package dev.hridaya.kubenexus.presentation.pods.components.terminal

import android.util.Log
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.core.terminal.GhosttyBridge
import dev.hridaya.kubenexus.core.terminal.GhosttyKeyAction
import dev.hridaya.kubenexus.core.terminal.KeyMapper
import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot
import dev.hridaya.kubenexus.domain.model.TerminalSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Owns one native Ghostty terminal and its link to a remote exec session.
 *
 * Threading: remote output arrives on a native (Go) thread, while resize, scroll, input
 * encoding, clear and destroy come from the main thread. The native terminal is not
 * thread-safe, so every native call, and every read or write of [handle], happens under
 * [lock]; an unsynchronised resize during output or a destroy during a write would corrupt
 * or free memory still in use.
 *
 * Writes to the remote session block until the other side reads them, so they are made on a
 * dedicated single-thread executor, which keeps keystrokes in order without ever blocking
 * the UI thread.
 */
class GhosttyTerminalEngine(
    private val maxScrollback: Int = 5000,
) {
    val bridge = GhosttyBridge()

    private val lock = Any()

    // Guarded by lock.
    private var handle: Long = 0L
    private var cols: Int = 80
    private var rows: Int = 24
    private var cellWidth: Int = 0
    private var cellHeight: Int = 0

    private val _snapshot = MutableStateFlow<TerminalSnapshot?>(null)
    val snapshot: StateFlow<TerminalSnapshot?> = _snapshot.asStateFlow()

    @Volatile
    private var terminalSession: TerminalSession? = null

    private val writer: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "terminal-writer").apply { isDaemon = true }
    }

    val isNativeLoaded: Boolean get() = bridge.isLoaded()
    val terminalHandle: Long get() = synchronized(lock) { handle }

    /**
     * Creates the native terminal, replacing any existing one. Without arguments the current
     * grid size is kept, so clearing the screen does not fall back to 80x24.
     */
    fun initialize(initialCols: Int? = null, initialRows: Int? = null) {
        if (!bridge.isLoaded()) return
        synchronized(lock) {
            if (handle != 0L) {
                bridge.nativeDestroy(handle)
            }
            cols = (initialCols ?: cols).coerceAtLeast(10)
            rows = (initialRows ?: rows).coerceAtLeast(4)
            handle = bridge.nativeCreate(cols, rows, maxScrollback)
            if (cellWidth > 0 && cellHeight > 0) {
                bridge.nativeResize(handle, cols, rows, cellWidth, cellHeight)
                applyMouseEncodingSizeLocked()
            }
            updateSnapshotLocked()
        }
    }

    fun attachSession(session: TerminalSession) {
        terminalSession = session
        val (c, r) = synchronized(lock) { cols to rows }
        submitWrite { session.resize(c, r) }
    }

    fun detachSession() {
        terminalSession = null
    }

    fun feedRemoteOutput(data: ByteArray) {
        if (data.isEmpty()) return
        val replies = synchronized(lock) {
            if (handle == 0L) return
            bridge.nativeWriteRemote(handle, data)
            updateSnapshotLocked()
            drainRepliesLocked()
        }
        sendBytes(replies)
    }

    fun feedRemoteOutput(text: String) {
        feedRemoteOutput(text.toByteArray(Charsets.UTF_8))
    }

    fun resize(newCols: Int, newRows: Int, cellW: Int, cellH: Int) {
        if (newCols <= 0 || newRows <= 0) return
        val replies = synchronized(lock) {
            if (handle == 0L) return
            cols = newCols
            rows = newRows
            cellWidth = cellW
            cellHeight = cellH
            bridge.nativeResize(handle, cols, rows, cellW, cellH)
            applyMouseEncodingSizeLocked()
            updateSnapshotLocked()
            drainRepliesLocked()
        }
        terminalSession?.let { session -> submitWrite { session.resize(newCols, newRows) } }
        sendBytes(replies)
    }

    fun scroll(delta: Int, x: Float, y: Float) {
        val replies = synchronized(lock) {
            if (handle == 0L) return
            bridge.nativeScroll(handle, delta, x, y)
            updateSnapshotLocked()
            // In mouse-reporting mode (less, vim, htop) scrolling is encoded as input for the
            // remote program rather than moving the local viewport.
            drainRepliesLocked()
        }
        sendBytes(replies)
    }

    fun scrollToActive() {
        synchronized(lock) {
            if (handle == 0L) return
            bridge.nativeScrollToActive(handle)
            updateSnapshotLocked()
        }
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        val session = terminalSession ?: return
        submitWrite { session.write(text) }
    }

    fun sendKey(
        keyCode: Int,
        codepoint: Int = 0,
        metaState: Int = 0,
        action: Int = GhosttyKeyAction.Press,
    ): Boolean {
        val mapped = KeyMapper.map(keyCode, codepoint, metaState) ?: return false
        val utf8 = if (mapped.charCode != 0) String(Character.toChars(mapped.charCode)) else null
        val encoded = synchronized(lock) {
            if (handle == 0L) return false
            bridge.nativeEncodeKey(handle, mapped.key, mapped.codepoint, mapped.mods, action, utf8)
        }
        if (encoded == null || encoded.isEmpty()) return false
        sendBytes(encoded)
        return true
    }

    fun sendPaste(text: String) {
        if (text.isEmpty()) return
        val encoded = synchronized(lock) {
            if (handle == 0L) return
            bridge.nativeEncodePaste(handle, text)
        }
        if (encoded != null && encoded.isNotEmpty()) {
            sendBytes(encoded)
        } else {
            sendText(text)
        }
    }

    fun updateSnapshot() {
        synchronized(lock) { updateSnapshotLocked() }
    }

    fun destroy() {
        synchronized(lock) {
            if (handle != 0L) {
                bridge.nativeDestroy(handle)
                handle = 0L
            }
        }
        _snapshot.value = null
        terminalSession = null
        writer.shutdown()
    }

    private fun updateSnapshotLocked() {
        if (handle == 0L) return
        try {
            val buf: ByteBuffer = bridge.nativeSnapshot(handle)
            // Copied out of the native buffer here, while the lock still guarantees it is valid.
            _snapshot.value = TerminalSnapshot.fromByteBuffer(buf)
        } catch (e: Exception) {
            Log.w(TAG, LogSanitizer.withStackTrace("Could not read the terminal snapshot", e))
        }
    }

    /**
     * Returns replies the terminal generated for the remote program: answers to device
     * attribute, cursor position and version queries, size reports, and encoded mouse
     * events. Programs such as prompt_toolkit REPLs and fish wait for these answers.
     */
    private fun drainRepliesLocked(): ByteArray =
        if (handle == 0L) EMPTY else bridge.nativeDrainPtyWrites(handle)

    private fun applyMouseEncodingSizeLocked() {
        if (cellWidth <= 0 || cellHeight <= 0) return
        bridge.nativeSetMouseEncodingSize(
            handle,
            cols * cellWidth,
            rows * cellHeight,
            cellWidth,
            cellHeight,
            0,
            0,
            0,
            0,
        )
    }

    private fun sendBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val session = terminalSession ?: return
        submitWrite { session.writeBytes(bytes) }
    }

    /**
     * Runs [write] on the writer thread. A session that has ended rejects writes with an
     * exception, which is expected once the shell exits and is not an error.
     */
    private fun submitWrite(write: () -> Unit) {
        try {
            writer.execute {
                try {
                    write()
                } catch (e: Exception) {
                    Log.d(TAG, "Terminal write dropped: ${LogSanitizer.sanitize(e.message)}")
                }
            }
        } catch (_: RejectedExecutionException) {
            // The engine has been destroyed.
        }
    }

    private companion object {
        const val TAG = "GhosttyTerminalEngine"
        val EMPTY = ByteArray(0)
    }
}
