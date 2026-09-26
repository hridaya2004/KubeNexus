package dev.hridaya.kubenexus.presentation.pods.components.terminal

import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalCellColorsTest {

    private val white = 0xFFE6E6E6.toInt()
    private val black = 0xFF000000.toInt()
    private val yellow = 0xFFFFD700.toInt()

    @Test
    fun `reverse video swaps foreground and background`() {
        val colors = cellColors(fg = white, bg = black, cellFlags = TerminalSnapshot.CELL_FLAG_INVERSE, defaultFg = white)

        assertEquals(CellColors(foreground = black, background = white), colors)
    }

    @Test
    fun `black text on a coloured background stays black`() {
        val colors = cellColors(fg = black, bg = yellow, cellFlags = 0, defaultFg = white)

        assertEquals(CellColors(foreground = black, background = yellow), colors)
    }

    @Test
    fun `invisible or unset text falls back to the default foreground`() {
        assertEquals(white, cellColors(fg = black, bg = black, cellFlags = 0, defaultFg = white).foreground)
        assertEquals(white, cellColors(fg = 0, bg = black, cellFlags = 0, defaultFg = white).foreground)
    }

    @Test
    fun `without a usable default the fallback contrasts with the background`() {
        val onWhite = cellColors(fg = 0xFFFFFFFF.toInt(), bg = 0xFFFFFFFF.toInt(), cellFlags = 0, defaultFg = 0xFFFFFFFF.toInt())

        assertEquals(black, onWhite.foreground)
    }
}
