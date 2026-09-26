package dev.hridaya.kubenexus.presentation.pods.components.terminal

import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot

/** Resolved ARGB colours for drawing one terminal cell. */
internal data class CellColors(val foreground: Int, val background: Int)

private const val OPAQUE_WHITE = 0xFFFFFFFF.toInt()
private const val OPAQUE_BLACK = 0xFF000000.toInt()

/**
 * Colours a cell is drawn with. SGR 7 (reverse video) swaps foreground and background; the
 * native snapshot records the flag but reports the unswapped colours, so the swap happens
 * here. That is what makes the less/man status line, vim's statusline and nano/htop bars
 * visible.
 *
 * Text that would be invisible, because its colour is unset or equal to its background,
 * falls back to the default foreground, or to whichever of black and white contrasts. A
 * genuinely black foreground on a coloured background is kept as it is.
 */
internal fun cellColors(fg: Int, bg: Int, cellFlags: Int, defaultFg: Int): CellColors {
    val inverse = (cellFlags and TerminalSnapshot.CELL_FLAG_INVERSE) != 0
    val background = if (inverse) fg else bg
    var foreground = if (inverse) bg else fg
    if (foreground == 0 || foreground == background) {
        foreground = when {
            defaultFg != 0 && defaultFg != background -> defaultFg
            background == OPAQUE_WHITE -> OPAQUE_BLACK
            else -> OPAQUE_WHITE
        }
    }
    return CellColors(foreground, background)
}
