#include <jni.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <ghostty/vt.h>

// Snapshot header layout (LE i32 fields):
//   0  cols
//   1  rows
//   2  cursor_x
//   3  cursor_y
//   4  cursor_visible
//   5  default_bg_r
//   6  default_bg_g
//   7  default_bg_b
//   8  default_fg_r
//   9  default_fg_g
//  10  default_fg_b
//  11  extras_offset (byte offset to grapheme extras section, 0 if none)
//  12  viewport_scroll_y (screen.y of the content at viewport row 0; stable
//      content coordinate that changes as the viewport scrolls, so the host
//      can remap a content-tracking selection anchor across scrolls)
//  13  app_handles_selection_drag (1 when the running app has enabled a
//      drag-reporting mouse mode — DECSET 1002/1003 — so the host forwards
//      long-press drag gestures to the app instead of starting a client-side
//      grid selection. This is what lets tmux/zellij perform their own
//      pane-scoped selection.)
#define SNAPSHOT_HEADER_I32_COUNT 14
#define SNAPSHOT_CELL_SIZE_BYTES 11
// Flag bit set on a cell whose codepoint has additional grapheme codepoints
// in the extras section.
#define CELL_FLAG_HAS_GRAPHEME (1u << 6)
// Flag bit set on spacer cells that belong to a wide glyph cluster.
// These are serialized with codepoint=32 but should not force a shaping
// run break between neighboring glyph cells.
#define CELL_FLAG_SPACER (1u << 7)
#define MAX_OSC52_CLIPBOARD_BYTES (1024 * 1024)
#define MAX_OSC7_PWD_BYTES 4096
// Kitty Unicode graphics placeholder (U+10EEEE). These cells only mark where an
// image is composited; we blank them so the raw glyph never shows through a gap
// the image doesn't fully cover.
#define KITTY_PLACEHOLDER_CP 0x10EEEEu
// Upper bound for queued replies to the remote side (device attributes, cursor position,
// size reports, encoded mouse events). Kotlin drains the queue after every write, so this
// only matters if draining stalls, e.g. output full of query sequences with no session.
#define MAX_PTY_WRITE_BYTES (64 * 1024)
#define MAX_GRAPHEME_CODEPOINTS 64

typedef struct {
    uint8_t *data;
    size_t len;
    size_t cap;
} Bytes;

typedef struct {
    char *value;
    size_t len;
    bool dirty;
} PolledString;

typedef struct {
    GhosttyTerminal terminal;
    GhosttyRenderState render_state;
    GhosttyRenderStateRowIterator row_iterator;
    GhosttyRenderStateRowCells row_cells;
    GhosttyKeyEncoder key_encoder;
    GhosttyKeyEvent key_event;
    GhosttyMouseEncoder mouse_tracked;
    GhosttyMouseEncoder mouse_untracked;
    GhosttyMouseEvent mouse_event;
    uint32_t mouse_modes;
    bool mouse_size_set;
    uint16_t cols;
    uint16_t rows;
    uint32_t cell_width;
    uint32_t cell_height;
    GhosttyColorScheme color_scheme;
    bool color_scheme_set;
    PolledString title;
    PolledString pwd;
    Bytes clipboard;
    bool clipboard_set;
    bool clipboard_dirty;
    uint32_t bell_count;
    Bytes snapshot;
    Bytes snapshot_extras;
    Bytes pty_writes;
} Terminal;

static const uint32_t default_word_boundaries[] = {
    0,   ' ', '\t', '\'', '"', 0x2502 /* │ */,
    '`', '|', ':',  ';',  ',', '(',
    ')', '[', ']',  '{',  '}', '<',
    '>', '$',
};

static bool bytesReserve(Bytes *bytes, size_t cap) {
    if (cap <= bytes->cap) return true;
    size_t new_cap = bytes->cap ? bytes->cap : 256;
    while (new_cap < cap) new_cap *= 2;
    uint8_t *data = realloc(bytes->data, new_cap);
    if (!data) return false;
    bytes->data = data;
    bytes->cap = new_cap;
    return true;
}

static bool bytesAppend(Bytes *bytes, const void *src, size_t len) {
    if (!bytesReserve(bytes, bytes->len + len)) return false;
    if (len > 0) memcpy(bytes->data + bytes->len, src, len);
    bytes->len += len;
    return true;
}

static void bytesFree(Bytes *bytes) {
    free(bytes->data);
    *bytes = (Bytes){0};
}

static void putI32(uint8_t *dst, int32_t value) {
    uint32_t v = (uint32_t)value;
    dst[0] = (uint8_t)v;
    dst[1] = (uint8_t)(v >> 8);
    dst[2] = (uint8_t)(v >> 16);
    dst[3] = (uint8_t)(v >> 24);
}

static bool bytesAppendU32(Bytes *bytes, uint32_t value) {
    uint8_t le[4];
    putI32(le, (int32_t)value);
    return bytesAppend(bytes, le, sizeof(le));
}

static int32_t clampI32(jint value, int32_t minimum, int32_t fallback) {
    return value < minimum ? fallback : (int32_t)value;
}

static Terminal *fromHandle(jlong handle) {
    return (Terminal *)(uintptr_t)handle;
}

static bool modeGet(GhosttyTerminal terminal, GhosttyMode mode) {
    GhosttyTerminalModeConfig config = {.mode = mode, .value = false};
    return ghostty_terminal_get(terminal, GHOSTTY_TERMINAL_DATA_MODE, &config) == GHOSTTY_SUCCESS &&
           config.value;
}

static void appendPtyWrite(Terminal *term, const void *data, size_t len) {
    if (len == 0 || len > MAX_PTY_WRITE_BYTES - term->pty_writes.len) return;
    bytesAppend(&term->pty_writes, data, len);
}

static jbyteArray jniBytes(JNIEnv *env, const void *data, size_t len) {
    jbyteArray out = (*env)->NewByteArray(env, (jsize)len);
    if (out && len > 0) (*env)->SetByteArrayRegion(env, out, 0, (jsize)len, (const jbyte *)data);
    return out;
}

static jbyteArray jniEmptyBytes(JNIEnv *env) {
    return (*env)->NewByteArray(env, 0);
}

/// Returns the contents of a Java string as standard UTF-8 in a malloc'd, NUL-terminated buffer.
///
/// GetStringUTFChars yields Java's *modified* UTF-8, which encodes characters outside the
/// BMP (emoji, many CJK extensions) as two 3-byte surrogate halves and U+0000 as C0 80, so
/// sending it to a remote shell garbles those characters. This reads the UTF-16 contents and
/// converts them properly. Returns NULL for a null string or one with unpaired surrogates.
static char *jstringToUtf8(JNIEnv *env, jstring str, size_t *out_len) {
    if (!str) return NULL;
    jsize len = (*env)->GetStringLength(env, str);
    char *out = malloc((size_t)len * 3 + 1);
    if (!out) return NULL;
    const jchar *units = len > 0 ? (*env)->GetStringChars(env, str, NULL) : NULL;
    if (len > 0 && !units) {
        free(out);
        return NULL;
    }

    size_t n = 0;
    bool ok = true;
    for (jsize i = 0; i < len; i++) {
        uint32_t cp = units[i];
        if (cp >= 0xD800 && cp <= 0xDBFF) {
            if (i + 1 >= len || units[i + 1] < 0xDC00 || units[i + 1] > 0xDFFF) {
                ok = false;
                break;
            }
            cp = 0x10000 + ((cp - 0xD800) << 10) + (units[++i] - 0xDC00);
        } else if (cp >= 0xDC00 && cp <= 0xDFFF) {
            ok = false;
            break;
        }

        if (cp < 0x80) {
            out[n++] = (char)cp;
        } else if (cp < 0x800) {
            out[n++] = (char)(0xC0 | (cp >> 6));
            out[n++] = (char)(0x80 | (cp & 0x3F));
        } else if (cp < 0x10000) {
            out[n++] = (char)(0xE0 | (cp >> 12));
            out[n++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            out[n++] = (char)(0x80 | (cp & 0x3F));
        } else {
            out[n++] = (char)(0xF0 | (cp >> 18));
            out[n++] = (char)(0x80 | ((cp >> 12) & 0x3F));
            out[n++] = (char)(0x80 | ((cp >> 6) & 0x3F));
            out[n++] = (char)(0x80 | (cp & 0x3F));
        }
    }
    if (units) (*env)->ReleaseStringChars(env, str, units);
    if (!ok) {
        free(out);
        return NULL;
    }
    out[n] = 0;
    *out_len = n;
    return out;
}

static void pollStringUpdate(PolledString *polled, const uint8_t *data, size_t len) {
    if (polled->value && polled->len == len && (len == 0 || memcmp(polled->value, data, len) == 0)) return;
    char *copy = malloc(len + 1);
    if (!copy) return;
    if (len > 0) memcpy(copy, data, len);
    copy[len] = 0;
    free(polled->value);
    polled->value = copy;
    polled->len = len;
    polled->dirty = true;
}

static int hexValue(uint8_t byte) {
    if (byte >= '0' && byte <= '9') return byte - '0';
    if (byte >= 'a' && byte <= 'f') return byte - 'a' + 10;
    if (byte >= 'A' && byte <= 'F') return byte - 'A' + 10;
    return -1;
}

static bool decodePwd(const uint8_t *raw, size_t raw_len, uint8_t *out, size_t *out_len) {
    static const char prefix[] = "file://";
    const size_t prefix_len = sizeof(prefix) - 1;
    if (raw_len > MAX_OSC7_PWD_BYTES) return false;

    if (raw_len < prefix_len || memcmp(raw, prefix, prefix_len) != 0) {
        if (raw_len == 0 || raw[0] != '/') return false;
        memcpy(out, raw, raw_len);
        *out_len = raw_len;
        return true;
    }

    const uint8_t *host_and_path = raw + prefix_len;
    size_t rest = raw_len - prefix_len;
    const uint8_t *path = memchr(host_and_path, '/', rest);
    if (!path) return false;
    size_t path_len = rest - (size_t)(path - host_and_path);

    size_t n = 0;
    for (size_t i = 0; i < path_len;) {
        if (path[i] == '%' && i + 2 < path_len) {
            int high = hexValue(path[i + 1]);
            int low = hexValue(path[i + 2]);
            if (high >= 0 && low >= 0) {
                out[n++] = (uint8_t)(high * 16 + low);
                i += 3;
                continue;
            }
        }
        out[n++] = path[i++];
    }
    if (n == 0 || out[0] != '/') return false;
    *out_len = n;
    return true;
}

static void updateMetadata(Terminal *term) {
    GhosttyString title = {0};
    if (ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_TITLE, &title) == GHOSTTY_SUCCESS) {
        pollStringUpdate(&term->title, title.ptr, title.len);
    }

    GhosttyString pwd = {0};
    if (ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_PWD, &pwd) == GHOSTTY_SUCCESS) {
        if (pwd.len == 0) {
            pollStringUpdate(&term->pwd, NULL, 0);
        } else {
            uint8_t decoded[MAX_OSC7_PWD_BYTES];
            size_t decoded_len = 0;
            if (decodePwd(pwd.ptr, pwd.len, decoded, &decoded_len)) {
                pollStringUpdate(&term->pwd, decoded, decoded_len);
            }
        }
    }
}

static bool mimeIsPlainText(GhosttyString mime) {
    static const char plain[] = "text/plain";
    return mime.len >= sizeof(plain) - 1 && memcmp(mime.ptr, plain, sizeof(plain) - 1) == 0;
}

static void onClipboardWrite(GhosttyTerminal terminal, void *userdata, const GhosttyClipboardWrite *write) {
    (void)terminal;
    Terminal *term = userdata;
    GhosttyClipboardWriteReply reply = GHOSTTY_INIT_SIZED(GhosttyClipboardWriteReply);
    reply.result = GHOSTTY_CLIPBOARD_WRITE_RESULT_DENIED;

    const GhosttyClipboardContent *content = NULL;
    for (size_t i = 0; i < write->contents_len; i++) {
        if (mimeIsPlainText(write->contents[i].mime)) {
            content = &write->contents[i];
            break;
        }
    }
    if (!content && write->contents_len > 0) content = &write->contents[0];

    const uint8_t *data = content ? content->data.ptr : NULL;
    size_t len = content ? content->data.len : 0;
    if (len <= MAX_OSC52_CLIPBOARD_BYTES) {
        if (!(term->clipboard_set && term->clipboard.len == 0 && len == 0)) {
            term->clipboard.len = 0;
            if (bytesAppend(&term->clipboard, data, len)) {
                term->clipboard_set = true;
                term->clipboard_dirty = true;
            }
        }
        reply.result = GHOSTTY_CLIPBOARD_WRITE_RESULT_SUCCESS;
    }
    write->reply(write, &reply);
}

static void onWritePty(GhosttyTerminal terminal, void *userdata, const uint8_t *data, size_t len) {
    (void)terminal;
    appendPtyWrite(userdata, data, len);
}

static void onBell(GhosttyTerminal terminal, void *userdata) {
    (void)terminal;
    Terminal *term = userdata;
    if (term->bell_count < UINT32_MAX) term->bell_count++;
}

static bool onColorScheme(GhosttyTerminal terminal, void *userdata, GhosttyColorScheme *out) {
    (void)terminal;
    Terminal *term = userdata;
    if (!term->color_scheme_set) return false;
    *out = term->color_scheme;
    return true;
}

static bool onDeviceAttributes(GhosttyTerminal terminal, void *userdata, GhosttyDeviceAttributes *out) {
    (void)terminal;
    (void)userdata;
    memset(out, 0, sizeof(*out));
    out->primary.conformance_level = GHOSTTY_DA_CONFORMANCE_VT220;
    out->primary.features[0] = GHOSTTY_DA_FEATURE_ANSI_COLOR;
    out->primary.num_features = 1;
    out->secondary.device_type = GHOSTTY_DA_DEVICE_TYPE_VT220;
    return true;
}

static bool onSize(GhosttyTerminal terminal, void *userdata, GhosttySizeReportSize *out) {
    (void)terminal;
    Terminal *term = userdata;
    out->rows = term->rows;
    out->columns = term->cols;
    out->cell_width = term->cell_width > 0 ? term->cell_width : 1;
    out->cell_height = term->cell_height > 0 ? term->cell_height : 1;
    return true;
}

static GhosttyString onXtversion(GhosttyTerminal terminal, void *userdata) {
    (void)terminal;
    (void)userdata;
    static const char version[] = "ghostty 1";
    return (GhosttyString){.ptr = (const uint8_t *)version, .len = sizeof(version) - 1};
}

static void updateRenderState(Terminal *term) {
    ghostty_render_state_update(term->render_state, term->terminal);
    updateMetadata(term);
}

static void syncMouseEncoders(Terminal *term) {
    const GhosttyMode mouse_modes[] = {
        GHOSTTY_MODE_X10_MOUSE,   GHOSTTY_MODE_NORMAL_MOUSE, GHOSTTY_MODE_BUTTON_MOUSE,
        GHOSTTY_MODE_ANY_MOUSE,   GHOSTTY_MODE_UTF8_MOUSE,   GHOSTTY_MODE_SGR_MOUSE,
        GHOSTTY_MODE_URXVT_MOUSE, GHOSTTY_MODE_SGR_PIXELS_MOUSE,
    };
    uint32_t modes = 0;
    for (size_t i = 0; i < sizeof(mouse_modes) / sizeof(mouse_modes[0]); i++) {
        if (modeGet(term->terminal, mouse_modes[i])) modes |= 1u << i;
    }
    modes |= 1u << 31;
    if (modes == term->mouse_modes) return;
    term->mouse_modes = modes;
    ghostty_mouse_encoder_setopt_from_terminal(term->mouse_tracked, term->terminal);
    ghostty_mouse_encoder_setopt_from_terminal(term->mouse_untracked, term->terminal);
}

static void setMouseSize(Terminal *term, const GhosttyMouseEncoderSize *size) {
    ghostty_mouse_encoder_setopt(term->mouse_tracked, GHOSTTY_MOUSE_ENCODER_OPT_SIZE, size);
    ghostty_mouse_encoder_setopt(term->mouse_untracked, GHOSTTY_MOUSE_ENCODER_OPT_SIZE, size);
}

static size_t encodeMouse(GhosttyMouseEncoder encoder, GhosttyMouseEvent event, char *buf, size_t buf_len) {
    size_t written = 0;
    if (ghostty_mouse_encoder_encode(encoder, event, buf, buf_len, &written) != GHOSTTY_SUCCESS) return 0;
    return written;
}

static void destroyTerminal(Terminal *term) {
    if (!term) return;
    if (term->mouse_event) ghostty_mouse_event_free(term->mouse_event);
    if (term->mouse_untracked) ghostty_mouse_encoder_free(term->mouse_untracked);
    if (term->mouse_tracked) ghostty_mouse_encoder_free(term->mouse_tracked);
    if (term->key_event) ghostty_key_event_free(term->key_event);
    if (term->key_encoder) ghostty_key_encoder_free(term->key_encoder);
    if (term->row_cells) ghostty_render_state_row_cells_free(term->row_cells);
    if (term->row_iterator) ghostty_render_state_row_iterator_free(term->row_iterator);
    if (term->render_state) ghostty_render_state_free(term->render_state);
    if (term->terminal) ghostty_terminal_free(term->terminal);
    free(term->title.value);
    free(term->pwd.value);
    bytesFree(&term->clipboard);
    bytesFree(&term->snapshot);
    bytesFree(&term->snapshot_extras);
    bytesFree(&term->pty_writes);
    free(term);
}

static Terminal *createTerminal(jint cols, jint rows, jint max_scrollback) {
    Terminal *term = calloc(1, sizeof(Terminal));
    if (!term) return NULL;
    term->cols = (uint16_t)clampI32(cols, 1, 80);
    term->rows = (uint16_t)clampI32(rows, 1, 24);
    term->cell_width = 1;
    term->cell_height = 1;
    term->color_scheme = GHOSTTY_COLOR_SCHEME_DARK;

    if (ghostty_terminal_new(NULL, &term->terminal, term->cols, term->rows) != GHOSTTY_SUCCESS ||
        ghostty_render_state_new(NULL, &term->render_state) != GHOSTTY_SUCCESS ||
        ghostty_render_state_row_iterator_new(NULL, &term->row_iterator) != GHOSTTY_SUCCESS ||
        ghostty_render_state_row_cells_new(NULL, &term->row_cells) != GHOSTTY_SUCCESS ||
        ghostty_key_encoder_new(NULL, &term->key_encoder) != GHOSTTY_SUCCESS ||
        ghostty_key_event_new(NULL, &term->key_event) != GHOSTTY_SUCCESS ||
        ghostty_mouse_encoder_new(NULL, &term->mouse_tracked) != GHOSTTY_SUCCESS ||
        ghostty_mouse_encoder_new(NULL, &term->mouse_untracked) != GHOSTTY_SUCCESS ||
        ghostty_mouse_event_new(NULL, &term->mouse_event) != GHOSTTY_SUCCESS) {
        destroyTerminal(term);
        return NULL;
    }

    GhosttyTerminal t = term->terminal;
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_USERDATA, term);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_WRITE_PTY, (const void *)onWritePty);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_BELL, (const void *)onBell);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_COLOR_SCHEME, (const void *)onColorScheme);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_DEVICE_ATTRIBUTES, (const void *)onDeviceAttributes);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_SIZE, (const void *)onSize);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_XTVERSION, (const void *)onXtversion);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_CLIPBOARD_WRITE, (const void *)onClipboardWrite);

    size_t scrollback = (size_t)clampI32(max_scrollback, 0, 10000);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_SCROLLBACK_MAX_LINES, &scrollback);

    uint64_t image_limit = 64ull * 1024 * 1024;
    bool off = false;
    bool on = true;
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_STORAGE_LIMIT, &image_limit);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_MEDIUM_FILE, &off);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_MEDIUM_TEMP_FILE, &off);
    ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_KITTY_IMAGE_MEDIUM_SHARED_MEM, &on);

    // DEC private mode 2027 makes the VT layer width-collapse grapheme clusters
    // instead of summing per-codepoint wcwidth values.
    GhosttyTerminalModeConfig grapheme_mode = {.mode = GHOSTTY_MODE_GRAPHEME_CLUSTER, .value = true};
    if (ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_MODE_DEFAULT, &grapheme_mode) != GHOSTTY_SUCCESS) {
        ghostty_terminal_set(t, GHOSTTY_TERMINAL_OPT_MODE, &grapheme_mode);
    }

    bool track = true;
    ghostty_mouse_encoder_setopt(term->mouse_tracked, GHOSTTY_MOUSE_ENCODER_OPT_TRACK_LAST_CELL, &track);
    GhosttyMouseEncoderSize size = GHOSTTY_INIT_SIZED(GhosttyMouseEncoderSize);
    size.screen_width = 1;
    size.screen_height = 1;
    size.cell_width = 1;
    size.cell_height = 1;
    setMouseSize(term, &size);

    updateRenderState(term);
    return term;
}

static void putRgb(uint8_t *dst, GhosttyColorRgb rgb) {
    dst[0] = rgb.r;
    dst[1] = rgb.g;
    dst[2] = rgb.b;
}

static int32_t viewportScrollY(GhosttyTerminal terminal) {
    GhosttyPoint top = {.tag = GHOSTTY_POINT_TAG_VIEWPORT, .value = {.coordinate = {.x = 0, .y = 0}}};
    GhosttyGridRef ref = GHOSTTY_INIT_SIZED(GhosttyGridRef);
    if (ghostty_terminal_grid_ref(terminal, top, &ref) != GHOSTTY_SUCCESS) return 0;
    GhosttyPointCoordinate screen = {0};
    if (ghostty_terminal_point_from_grid_ref(terminal, &ref, GHOSTTY_POINT_TAG_SCREEN, &screen) != GHOSTTY_SUCCESS) {
        return 0;
    }
    return (int32_t)screen.y;
}

static uint8_t *buildTextSnapshot(Terminal *term, size_t *out_size) {
    updateRenderState(term);

    uint16_t cols = 0;
    uint16_t rows = 0;
    ghostty_render_state_get(term->render_state, GHOSTTY_RENDER_STATE_DATA_COLS, &cols);
    ghostty_render_state_get(term->render_state, GHOSTTY_RENDER_STATE_DATA_ROWS, &rows);
    GhosttyRenderStateCursor cursor = GHOSTTY_INIT_SIZED(GhosttyRenderStateCursor);
    ghostty_render_state_get(term->render_state, GHOSTTY_RENDER_STATE_DATA_CURSOR, &cursor);
    GhosttyRenderStateColors colors = GHOSTTY_INIT_SIZED(GhosttyRenderStateColors);
    ghostty_render_state_get(term->render_state, GHOSTTY_RENDER_STATE_DATA_COLORS, &colors);

    const size_t header_size = SNAPSHOT_HEADER_I32_COUNT * sizeof(int32_t);
    const size_t base_size = header_size + (size_t)cols * rows * SNAPSHOT_CELL_SIZE_BYTES;
    Bytes *snapshot = &term->snapshot;
    snapshot->len = 0;
    if (!bytesReserve(snapshot, base_size)) return NULL;
    snapshot->len = base_size;
    memset(snapshot->data, 0, base_size);
    uint8_t *buf = snapshot->data;

    putI32(buf + 0, cols);
    putI32(buf + 4, rows);
    putI32(buf + 8, cursor.viewport_has_value ? cursor.viewport_x : -1);
    putI32(buf + 12, cursor.viewport_has_value ? cursor.viewport_y : -1);
    putI32(buf + 16, cursor.visible && cursor.viewport_has_value ? 1 : 0);
    putI32(buf + 20, colors.background.r);
    putI32(buf + 24, colors.background.g);
    putI32(buf + 28, colors.background.b);
    putI32(buf + 32, colors.foreground.r);
    putI32(buf + 36, colors.foreground.g);
    putI32(buf + 40, colors.foreground.b);
    // extras_offset (44) gets patched once we know whether extras exist.
    putI32(buf + 48, viewportScrollY(term->terminal));
    // app_handles_selection_drag: 1 only for drag-reporting mouse modes
    // (DECSET 1002/1003). Click-only mouse modes still allow the host to keep
    // its own long-press selection behavior.
    bool drag_reporting = modeGet(term->terminal, GHOSTTY_MODE_BUTTON_MOUSE) ||
                          modeGet(term->terminal, GHOSTTY_MODE_ANY_MOUSE);
    putI32(buf + 52, drag_reporting ? 1 : 0);

    Bytes *extras = &term->snapshot_extras;
    extras->len = 0;
    uint32_t extras_records = 0;
    uint32_t codepoints[MAX_GRAPHEME_CODEPOINTS];

    if (ghostty_render_state_get(term->render_state, GHOSTTY_RENDER_STATE_DATA_ROW_ITERATOR, &term->row_iterator) !=
        GHOSTTY_SUCCESS) {
        return NULL;
    }
    for (uint16_t y = 0; y < rows && ghostty_render_state_row_iterator_next(term->row_iterator); y++) {
        if (ghostty_render_state_row_get(term->row_iterator, GHOSTTY_RENDER_STATE_ROW_DATA_CELLS, &term->row_cells) !=
            GHOSTTY_SUCCESS) {
            continue;
        }
        for (uint16_t x = 0; x < cols && ghostty_render_state_row_cells_next(term->row_cells); x++) {
            const uint32_t cell_index = (uint32_t)y * cols + x;
            uint8_t *out = buf + header_size + (size_t)cell_index * SNAPSHOT_CELL_SIZE_BYTES;

            GhosttyCell raw = 0;
            ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_RAW, &raw);
            uint32_t cp = 0;
            GhosttyCellWide wide = GHOSTTY_CELL_WIDE_NARROW;
            ghostty_cell_get(raw, GHOSTTY_CELL_DATA_CODEPOINT, &cp);
            ghostty_cell_get(raw, GHOSTTY_CELL_DATA_WIDE, &wide);
            const bool spacer = wide == GHOSTTY_CELL_WIDE_SPACER_TAIL || wide == GHOSTTY_CELL_WIDE_SPACER_HEAD;

            uint8_t flags = 0;
            GhosttyColorRgb fg = colors.foreground;
            GhosttyColorRgb bg = colors.background;
            bool has_styling = false;
            ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_HAS_STYLING,
                                               &has_styling);
            if (has_styling) {
                GhosttyStyle style = GHOSTTY_INIT_SIZED(GhosttyStyle);
                ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_STYLE, &style);
                if (style.bold) flags |= 1u << 0;
                if (style.italic) flags |= 1u << 1;
                if (style.underline != 0) flags |= 1u << 2;
                if (style.inverse) flags |= 1u << 3;
                if (style.blink) flags |= 1u << 4;
                if (style.faint) flags |= 1u << 5;
                ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_FG_COLOR, &fg);
            }
            ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_BG_COLOR, &bg);
            if (spacer) flags |= CELL_FLAG_SPACER;

            // Placeholder cells carry row/column diacritics as grapheme extras; drop
            // them so the combining marks aren't painted (see KITTY_PLACEHOLDER_CP).
            if (!spacer && cp != KITTY_PLACEHOLDER_CP) {
                uint32_t len = 0;
                ghostty_render_state_row_cells_get(term->row_cells, GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_LEN,
                                                   &len);
                if (len > 1 && len <= MAX_GRAPHEME_CODEPOINTS &&
                    ghostty_render_state_row_cells_get(term->row_cells,
                                                       GHOSTTY_RENDER_STATE_ROW_CELLS_DATA_GRAPHEMES_BUF,
                                                       codepoints) == GHOSTTY_SUCCESS) {
                    flags |= CELL_FLAG_HAS_GRAPHEME;
                    if (!bytesAppendU32(extras, cell_index) || !bytesAppendU32(extras, len - 1)) return NULL;
                    for (uint32_t i = 1; i < len; i++) {
                        if (!bytesAppendU32(extras, codepoints[i])) return NULL;
                    }
                    extras_records++;
                }
            }

            const uint32_t serialized_cp = (spacer || cp == 0 || cp == KITTY_PLACEHOLDER_CP) ? 32 : cp;
            putI32(out, (int32_t)serialized_cp);
            putRgb(out + 4, fg);
            putRgb(out + 7, bg);
            out[10] = flags;
        }
    }

    if (extras_records > 0) {
        // Extras section layout:
        //   u32 record_count
        //   For each record:
        //     u32 cell_index
        //     u32 count
        //     u32[count] codepoints
        if (!bytesAppendU32(snapshot, extras_records) || !bytesAppend(snapshot, extras->data, extras->len)) {
            return NULL;
        }
        putI32(snapshot->data + 44, (int32_t)base_size);
    }

    *out_size = snapshot->len;
    return snapshot->data;
}

static bool gridRef(GhosttyTerminal terminal, GhosttyPointTag tag, uint32_t x, uint32_t y, GhosttyGridRef *out) {
    GhosttyPoint point = {.tag = tag, .value = {.coordinate = {.x = (uint16_t)x, .y = y}}};
    *out = GHOSTTY_INIT_SIZED(GhosttyGridRef);
    return ghostty_terminal_grid_ref(terminal, point, out) == GHOSTTY_SUCCESS;
}

static jstring selectionText(JNIEnv *env, Terminal *term, const GhosttySelection *selection) {
    GhosttyTerminalSelectionFormatOptions options = GHOSTTY_INIT_SIZED(GhosttyTerminalSelectionFormatOptions);
    options.emit = GHOSTTY_FORMATTER_FORMAT_PLAIN;
    options.unwrap = true;
    options.trim = true;
    options.selection = selection;

    uint8_t *text = NULL;
    size_t len = 0;
    if (ghostty_terminal_selection_format_alloc(term->terminal, NULL, options, &text, &len) != GHOSTTY_SUCCESS) {
        return NULL;
    }
    char *terminated = malloc(len + 1);
    if (terminated) {
        if (len > 0) memcpy(terminated, text, len);
        terminated[len] = 0;
    }
    ghostty_free(NULL, text, len);
    if (!terminated) return NULL;
    jstring out = (*env)->NewStringUTF(env, terminated);
    free(terminated);
    return out;
}

static jstring selectionBetween(JNIEnv *env, Terminal *term, const GhosttyGridRef *start, const GhosttyGridRef *end) {
    GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
    selection.start = *start;
    selection.end = *end;
    selection.rectangle = false;
    return selectionText(env, term, &selection);
}

#define JNI(name) Java_dev_hridaya_kubenexus_core_terminal_GhosttyBridge_##name

JNIEXPORT jstring JNICALL JNI(nativeVersion)(JNIEnv *env, jclass clazz) {
    (void)clazz;
    return (*env)->NewStringUTF(env, "1");
}

JNIEXPORT jlong JNICALL JNI(nativeCreate)(JNIEnv *env, jobject thiz, jint cols, jint rows, jint max_scrollback) {
    (void)env;
    (void)thiz;
    return (jlong)(uintptr_t)createTerminal(cols, rows, max_scrollback);
}

JNIEXPORT void JNICALL JNI(nativeDestroy)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    destroyTerminal(fromHandle(handle));
}

JNIEXPORT void JNICALL JNI(nativeWriteRemote)(JNIEnv *env, jobject thiz, jlong handle, jbyteArray data) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || !data) return;
    jsize len = (*env)->GetArrayLength(env, data);
    if (len <= 0) return;
    jbyte *bytes = (*env)->GetByteArrayElements(env, data, NULL);
    if (!bytes) return;
    ghostty_terminal_vt_write(term->terminal, (const uint8_t *)bytes, (size_t)len);
    (*env)->ReleaseByteArrayElements(env, data, bytes, JNI_ABORT);
    updateRenderState(term);
}

JNIEXPORT void JNICALL JNI(nativeResize)(JNIEnv *env, jobject thiz, jlong handle, jint cols, jint rows,
                                         jint cell_width, jint cell_height) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;
    uint16_t new_cols = (uint16_t)clampI32(cols, 1, term->cols);
    uint16_t new_rows = (uint16_t)clampI32(rows, 1, term->rows);
    uint32_t new_cell_width = (uint32_t)clampI32(cell_width, 1, 1);
    uint32_t new_cell_height = (uint32_t)clampI32(cell_height, 1, 1);
    // Also turns off synchronized output and sends the DEC 2048 in-band size
    // report that TUIs like Textual rely on once they disable SIGWINCH.
    if (ghostty_terminal_resize(term->terminal, new_cols, new_rows, new_cell_width, new_cell_height) !=
        GHOSTTY_SUCCESS) {
        return;
    }
    term->cols = new_cols;
    term->rows = new_rows;
    term->cell_width = new_cell_width;
    term->cell_height = new_cell_height;
    updateRenderState(term);
}

JNIEXPORT void JNICALL JNI(nativeScroll)(JNIEnv *env, jobject thiz, jlong handle, jint delta, jfloat x, jfloat y) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;

    // Alternate screen (vim, tmux, less, ...) or any mouse reporting: send wheel
    // events to the application instead of scrolling our own scrollback.
    GhosttyTerminalScreen screen = GHOSTTY_TERMINAL_SCREEN_PRIMARY;
    bool mouse_tracking = false;
    ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_ACTIVE_SCREEN, &screen);
    ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_MOUSE_TRACKING, &mouse_tracking);

    if (screen == GHOSTTY_TERMINAL_SCREEN_ALTERNATE || mouse_tracking) {
        // Negative delta = scroll up (older content) → button 4.
        // Positive delta = scroll down (newer content) → button 5.
        GhosttyMouseButton button = delta < 0 ? GHOSTTY_MOUSE_BUTTON_FOUR : GHOSTTY_MOUSE_BUTTON_FIVE;
        uint32_t count = delta == 0 ? 1 : (uint32_t)(delta < 0 ? -(int64_t)delta : delta);

        // Use the gesture position so mouse-aware programs like tmux route the
        // wheel event to the pane under the user's finger.
        uint32_t width_px = 1;
        uint32_t height_px = 1;
        ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_WIDTH_PX, &width_px);
        ghostty_terminal_get(term->terminal, GHOSTTY_TERMINAL_DATA_HEIGHT_PX, &height_px);
        float max_x = (float)((width_px > 1 ? width_px : 1) - 1);
        float max_y = (float)((height_px > 1 ? height_px : 1) - 1);
        GhosttyMousePosition pos = {
            .x = x < 0 ? 0 : (x > max_x ? max_x : x),
            .y = y < 0 ? 0 : (y > max_y ? max_y : y),
        };

        syncMouseEncoders(term);
        bool released = false;
        ghostty_mouse_encoder_setopt(term->mouse_untracked, GHOSTTY_MOUSE_ENCODER_OPT_ANY_BUTTON_PRESSED, &released);
        ghostty_mouse_event_set_button(term->mouse_event, button);
        ghostty_mouse_event_set_mods(term->mouse_event, 0);
        ghostty_mouse_event_set_position(term->mouse_event, pos);

        // One press/release pair per step so the app sees the scroll magnitude.
        char buf[64];
        for (uint32_t i = 0; i < count; i++) {
            ghostty_mouse_event_set_action(term->mouse_event, GHOSTTY_MOUSE_ACTION_PRESS);
            appendPtyWrite(term, buf, encodeMouse(term->mouse_untracked, term->mouse_event, buf, sizeof(buf)));
            ghostty_mouse_event_set_action(term->mouse_event, GHOSTTY_MOUSE_ACTION_RELEASE);
            appendPtyWrite(term, buf, encodeMouse(term->mouse_untracked, term->mouse_event, buf, sizeof(buf)));
        }
    } else {
        GhosttyTerminalScrollViewport scroll = {.tag = GHOSTTY_SCROLL_VIEWPORT_DELTA, .value = {.delta = delta}};
        ghostty_terminal_scroll_viewport(term->terminal, scroll);
    }

    updateRenderState(term);
}

JNIEXPORT void JNICALL JNI(nativeScrollToActive)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;
    GhosttyTerminalScrollViewport scroll = {.tag = GHOSTTY_SCROLL_VIEWPORT_BOTTOM};
    ghostty_terminal_scroll_viewport(term->terminal, scroll);
    updateRenderState(term);
}

JNIEXPORT jobject JNICALL JNI(nativeSnapshot)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    size_t size = 0;
    uint8_t *buffer = term ? buildTextSnapshot(term, &size) : NULL;
    if (!buffer || size == 0) return (*env)->NewDirectByteBuffer(env, NULL, 0);
    return (*env)->NewDirectByteBuffer(env, buffer, (jlong)size);
}

JNIEXPORT jobject JNICALL JNI(nativeSnapshotImages)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    (void)handle;
    return (*env)->NewDirectByteBuffer(env, NULL, 0);
}

JNIEXPORT jboolean JNICALL JNI(nativeIsImageLoading)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    (void)handle;
    return JNI_FALSE;
}

static jstring pollString(JNIEnv *env, PolledString *polled) {
    if (!polled->dirty) return NULL;
    polled->dirty = false;
    return (*env)->NewStringUTF(env, polled->value ? polled->value : "");
}

JNIEXPORT jstring JNICALL JNI(nativePollTitle)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    return term ? pollString(env, &term->title) : NULL;
}

JNIEXPORT jstring JNICALL JNI(nativePollPwd)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    return term ? pollString(env, &term->pwd) : NULL;
}

JNIEXPORT jbyteArray JNICALL JNI(nativePollClipboard)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || !term->clipboard_dirty) return NULL;
    term->clipboard_dirty = false;
    return jniBytes(env, term->clipboard.data, term->clipboard.len);
}

JNIEXPORT jint JNICALL JNI(nativeDrainBellCount)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return 0;
    uint32_t count = term->bell_count;
    term->bell_count = 0;
    return (jint)(count > INT32_MAX ? INT32_MAX : count);
}

JNIEXPORT void JNICALL JNI(nativeSetColorScheme)(JNIEnv *env, jobject thiz, jlong handle, jint scheme) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;
    term->color_scheme = scheme == 0 ? GHOSTTY_COLOR_SCHEME_LIGHT : GHOSTTY_COLOR_SCHEME_DARK;
    term->color_scheme_set = true;
}

static bool readRgb(JNIEnv *env, jintArray array, GhosttyColorRgb *out) {
    if (!array || (*env)->GetArrayLength(env, array) < 3) return false;
    jint values[3];
    (*env)->GetIntArrayRegion(env, array, 0, 3, values);
    uint8_t channels[3];
    for (int i = 0; i < 3; i++) channels[i] = (uint8_t)(values[i] < 0 ? 0 : (values[i] > 255 ? 255 : values[i]));
    *out = (GhosttyColorRgb){.r = channels[0], .g = channels[1], .b = channels[2]};
    return true;
}

JNIEXPORT void JNICALL JNI(nativeSetDefaultColors)(JNIEnv *env, jobject thiz, jlong handle, jintArray fg_rgb,
                                                   jintArray bg_rgb, jintArray cursor_rgb, jbyteArray palette_rgb) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;

    GhosttyColorRgb fg, bg, cursor;
    ghostty_terminal_set(term->terminal, GHOSTTY_TERMINAL_OPT_COLOR_FOREGROUND, readRgb(env, fg_rgb, &fg) ? &fg : NULL);
    ghostty_terminal_set(term->terminal, GHOSTTY_TERMINAL_OPT_COLOR_BACKGROUND, readRgb(env, bg_rgb, &bg) ? &bg : NULL);
    ghostty_terminal_set(term->terminal, GHOSTTY_TERMINAL_OPT_COLOR_CURSOR,
                         readRgb(env, cursor_rgb, &cursor) ? &cursor : NULL);

    GhosttyColorRgb palette[256];
    bool has_palette = palette_rgb && (*env)->GetArrayLength(env, palette_rgb) >= 256 * 3;
    if (has_palette) {
        uint8_t raw[256 * 3];
        (*env)->GetByteArrayRegion(env, palette_rgb, 0, 256 * 3, (jbyte *)raw);
        for (int i = 0; i < 256; i++) {
            palette[i] = (GhosttyColorRgb){.r = raw[i * 3], .g = raw[i * 3 + 1], .b = raw[i * 3 + 2]};
        }
    }
    ghostty_terminal_set(term->terminal, GHOSTTY_TERMINAL_OPT_COLOR_PALETTE, has_palette ? palette : NULL);

    updateRenderState(term);
}

JNIEXPORT jbyteArray JNICALL JNI(nativeEncodeKey)(JNIEnv *env, jobject thiz, jlong handle, jint key, jint codepoint,
                                                  jint mods, jint action, jstring utf8_jstring) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return jniEmptyBytes(env);
    if (action < GHOSTTY_KEY_ACTION_RELEASE || action > GHOSTTY_KEY_ACTION_REPEAT) return jniEmptyBytes(env);
    if (key < GHOSTTY_KEY_UNIDENTIFIED || key > GHOSTTY_KEY_PASTE) return jniEmptyBytes(env);

    // For printable characters the text lets the legacy encoding emit raw text
    // when the remote side doesn't speak the Kitty keyboard protocol.
    size_t utf8_len = 0;
    char *utf8 = jstringToUtf8(env, utf8_jstring, &utf8_len);
    if (utf8 && utf8_len > 256) utf8_len = 0;

    GhosttyKeyEvent event = term->key_event;
    ghostty_key_event_set_action(event, (GhosttyKeyAction)action);
    ghostty_key_event_set_key(event, (GhosttyKey)key);
    ghostty_key_event_set_mods(event, (GhosttyMods)mods);
    ghostty_key_event_set_consumed_mods(event, 0);
    ghostty_key_event_set_composing(event, false);
    ghostty_key_event_set_unshifted_codepoint(event, codepoint > 0 ? (uint32_t)codepoint : 0);
    ghostty_key_event_set_utf8(event, utf8 ? utf8 : "", utf8 ? utf8_len : 0);

    ghostty_key_encoder_setopt_from_terminal(term->key_encoder, term->terminal);
    char buf[128];
    size_t written = 0;
    GhosttyResult result = ghostty_key_encoder_encode(term->key_encoder, event, buf, sizeof(buf), &written);
    ghostty_key_event_set_utf8(event, "", 0);
    free(utf8);
    if (result != GHOSTTY_SUCCESS) return jniEmptyBytes(env);
    return jniBytes(env, buf, written);
}

JNIEXPORT jbyteArray JNICALL JNI(nativeEncodePaste)(JNIEnv *env, jobject thiz, jlong handle, jstring data_jstring) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || !data_jstring) return jniEmptyBytes(env);

    // Encoding mutates the data in place (stripping unsafe bytes and, for
    // non-bracketed pastes, converting newlines to \r), so it gets its own copy.
    size_t len = 0;
    char *data = jstringToUtf8(env, data_jstring, &len);
    if (!data) return jniEmptyBytes(env);
    if (len == 0) {
        free(data);
        return jniEmptyBytes(env);
    }

    bool bracketed = modeGet(term->terminal, GHOSTTY_MODE_BRACKETED_PASTE);
    size_t cap = len + 32;
    char *out = malloc(cap);
    size_t written = 0;
    GhosttyResult result = out ? ghostty_paste_encode(data, len, bracketed, out, cap, &written) : GHOSTTY_OUT_OF_MEMORY;
    if (result == GHOSTTY_OUT_OF_SPACE) {
        char *bigger = realloc(out, written);
        if (bigger) {
            out = bigger;
            cap = written;
            result = ghostty_paste_encode(data, len, bracketed, out, cap, &written);
        }
    }
    jbyteArray array = result == GHOSTTY_SUCCESS ? jniBytes(env, out, written) : jniEmptyBytes(env);
    free(out);
    free(data);
    return array;
}

JNIEXPORT void JNICALL JNI(nativeSetMouseEncodingSize)(JNIEnv *env, jobject thiz, jlong handle, jint screen_width,
                                                       jint screen_height, jint cell_width, jint cell_height,
                                                       jint padding_top, jint padding_bottom, jint padding_left,
                                                       jint padding_right) {
    (void)env;
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return;
    GhosttyMouseEncoderSize size = GHOSTTY_INIT_SIZED(GhosttyMouseEncoderSize);
    size.screen_width = (uint32_t)clampI32(screen_width, 1, 1);
    size.screen_height = (uint32_t)clampI32(screen_height, 1, 1);
    size.cell_width = (uint32_t)clampI32(cell_width, 1, 1);
    size.cell_height = (uint32_t)clampI32(cell_height, 1, 1);
    size.padding_top = (uint32_t)clampI32(padding_top, 0, 0);
    size.padding_bottom = (uint32_t)clampI32(padding_bottom, 0, 0);
    size.padding_left = (uint32_t)clampI32(padding_left, 0, 0);
    size.padding_right = (uint32_t)clampI32(padding_right, 0, 0);
    setMouseSize(term, &size);
    term->mouse_size_set = true;
}

JNIEXPORT jbyteArray JNICALL JNI(nativeEncodeMouse)(JNIEnv *env, jobject thiz, jlong handle, jint action, jint button,
                                                    jint mods, jfloat x, jfloat y, jboolean any_button_pressed,
                                                    jboolean track_last_cell) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || !term->mouse_size_set) return jniEmptyBytes(env);
    if (action < GHOSTTY_MOUSE_ACTION_PRESS || action > GHOSTTY_MOUSE_ACTION_MOTION) return jniEmptyBytes(env);
    if (button > GHOSTTY_MOUSE_BUTTON_ELEVEN) return jniEmptyBytes(env);

    syncMouseEncoders(term);
    GhosttyMouseEncoder encoder = track_last_cell ? term->mouse_tracked : term->mouse_untracked;
    bool pressed = any_button_pressed == JNI_TRUE;
    ghostty_mouse_encoder_setopt(encoder, GHOSTTY_MOUSE_ENCODER_OPT_ANY_BUTTON_PRESSED, &pressed);

    GhosttyMouseEvent event = term->mouse_event;
    ghostty_mouse_event_set_action(event, (GhosttyMouseAction)action);
    if (button <= 0) {
        ghostty_mouse_event_clear_button(event);
    } else {
        ghostty_mouse_event_set_button(event, (GhosttyMouseButton)button);
    }
    ghostty_mouse_event_set_mods(event, (GhosttyMods)mods);
    ghostty_mouse_event_set_position(event, (GhosttyMousePosition){.x = x, .y = y});

    char buf[64];
    return jniBytes(env, buf, encodeMouse(encoder, event, buf, sizeof(buf)));
}

JNIEXPORT jbyteArray JNICALL JNI(nativeEncodeFocus)(JNIEnv *env, jobject thiz, jlong handle, jboolean focused) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || !modeGet(term->terminal, GHOSTTY_MODE_FOCUS_EVENT)) return jniEmptyBytes(env);
    char buf[16];
    size_t written = 0;
    if (ghostty_focus_encode(focused == JNI_TRUE ? GHOSTTY_FOCUS_GAINED : GHOSTTY_FOCUS_LOST, buf, sizeof(buf),
                             &written) != GHOSTTY_SUCCESS) {
        return jniEmptyBytes(env);
    }
    return jniBytes(env, buf, written);
}

JNIEXPORT jbyteArray JNICALL JNI(nativeDrainPtyWrites)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || term->pty_writes.len == 0) return jniEmptyBytes(env);
    jbyteArray out = jniBytes(env, term->pty_writes.data, term->pty_writes.len);
    term->pty_writes.len = 0;
    return out;
}

JNIEXPORT jstring JNICALL JNI(nativeFormatSelectionRange)(JNIEnv *env, jobject thiz, jlong handle, jint start_cell,
                                                          jint end_cell) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || term->cols == 0) return NULL;
    const jint cols = term->cols;
    GhosttyGridRef start, end;
    if (!gridRef(term->terminal, GHOSTTY_POINT_TAG_VIEWPORT, (uint32_t)(start_cell < 0 ? 0 : start_cell % cols),
                 (uint32_t)(start_cell < 0 ? 0 : start_cell / cols), &start) ||
        !gridRef(term->terminal, GHOSTTY_POINT_TAG_VIEWPORT, (uint32_t)(end_cell < 0 ? 0 : end_cell % cols),
                 (uint32_t)(end_cell < 0 ? 0 : end_cell / cols), &end)) {
        return NULL;
    }
    return selectionBetween(env, term, &start, &end);
}

/// Like nativeFormatSelectionRange but takes absolute screen-space cell
/// indices (screen.y * cols + screen.x) instead of viewport-space ones.
/// Used for content-tracking selections that span off-screen scrollback —
/// the host converts viewport-space to screen-space via viewportScrollY so
/// the full selected range (including scrolled-away content) is extracted.
JNIEXPORT jstring JNICALL JNI(nativeFormatSelectionScreenRange)(JNIEnv *env, jobject thiz, jlong handle,
                                                                jint start_screen_cell, jint end_screen_cell) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term || term->cols == 0 || start_screen_cell < 0 || end_screen_cell < 0) return NULL;
    const jint cols = term->cols;
    GhosttyGridRef start, end;
    if (!gridRef(term->terminal, GHOSTTY_POINT_TAG_SCREEN, (uint32_t)(start_screen_cell % cols),
                 (uint32_t)(start_screen_cell / cols), &start) ||
        !gridRef(term->terminal, GHOSTTY_POINT_TAG_SCREEN, (uint32_t)(end_screen_cell % cols),
                 (uint32_t)(end_screen_cell / cols), &end)) {
        return NULL;
    }
    return selectionBetween(env, term, &start, &end);
}

JNIEXPORT jstring JNICALL JNI(nativeSelectWordAt)(JNIEnv *env, jobject thiz, jlong handle, jint cell_x, jint cell_y) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return NULL;
    GhosttyTerminalSelectWordOptions options = GHOSTTY_INIT_SIZED(GhosttyTerminalSelectWordOptions);
    if (!gridRef(term->terminal, GHOSTTY_POINT_TAG_VIEWPORT, (uint32_t)(cell_x < 0 ? 0 : cell_x),
                 (uint32_t)(cell_y < 0 ? 0 : cell_y), &options.ref)) {
        return NULL;
    }
    options.boundary_codepoints = default_word_boundaries;
    options.boundary_codepoints_len = sizeof(default_word_boundaries) / sizeof(default_word_boundaries[0]);
    GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
    if (ghostty_terminal_select_word(term->terminal, &options, &selection) != GHOSTTY_SUCCESS) return NULL;
    return selectionText(env, term, &selection);
}

JNIEXPORT jstring JNICALL JNI(nativeSelectLineAt)(JNIEnv *env, jobject thiz, jlong handle, jint cell_x, jint cell_y) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return NULL;
    GhosttyTerminalSelectLineOptions options = GHOSTTY_INIT_SIZED(GhosttyTerminalSelectLineOptions);
    if (!gridRef(term->terminal, GHOSTTY_POINT_TAG_VIEWPORT, (uint32_t)(cell_x < 0 ? 0 : cell_x),
                 (uint32_t)(cell_y < 0 ? 0 : cell_y), &options.ref)) {
        return NULL;
    }
    options.semantic_prompt_boundary = true;
    GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
    if (ghostty_terminal_select_line(term->terminal, &options, &selection) != GHOSTTY_SUCCESS) return NULL;
    return selectionText(env, term, &selection);
}

JNIEXPORT jstring JNICALL JNI(nativeSelectAll)(JNIEnv *env, jobject thiz, jlong handle) {
    (void)thiz;
    Terminal *term = fromHandle(handle);
    if (!term) return NULL;
    GhosttySelection selection = GHOSTTY_INIT_SIZED(GhosttySelection);
    if (ghostty_terminal_select_all(term->terminal, &selection) != GHOSTTY_SUCCESS) return NULL;
    return selectionText(env, term, &selection);
}

int shm_open(const char *name, int oflag, unsigned int mode) {
    (void)name;
    (void)oflag;
    (void)mode;
    return -1;
}

int shm_unlink(const char *name) {
    (void)name;
    return -1;
}
