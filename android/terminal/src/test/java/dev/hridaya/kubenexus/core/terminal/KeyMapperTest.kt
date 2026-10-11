package dev.hridaya.kubenexus.core.terminal

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KeyMapperTest {

    @Test
    fun testNavigationKeys() {
        val up = KeyMapper.map(KeyEvent.KEYCODE_DPAD_UP, 0, 0)
        assertNotNull(up)
        assertEquals(GhosttyKey.arrowUp, up!!.key)

        val down = KeyMapper.map(KeyEvent.KEYCODE_DPAD_DOWN, 0, 0)
        assertNotNull(down)
        assertEquals(GhosttyKey.arrowDown, down!!.key)

        val enter = KeyMapper.map(KeyEvent.KEYCODE_ENTER, 0, 0)
        assertNotNull(enter)
        assertEquals(GhosttyKey.enter, enter!!.key)

        val tab = KeyMapper.map(KeyEvent.KEYCODE_TAB, 0, 0)
        assertNotNull(tab)
        assertEquals(GhosttyKey.tab, tab!!.key)

        val esc = KeyMapper.map(KeyEvent.KEYCODE_ESCAPE, 0, 0)
        assertNotNull(esc)
        assertEquals(GhosttyKey.escape, esc!!.key)
    }

    @Test
    fun testLetterKeysWithModifiers() {
        val ctrlC = KeyMapper.map(KeyEvent.KEYCODE_C, 0, KeyEvent.META_CTRL_ON)
        assertNotNull(ctrlC)
        assertEquals(GhosttyKey.keyC, ctrlC!!.key)
        assertEquals(2, ctrlC.mods) // bit 1 = ctrl

        val shiftA = KeyMapper.map(KeyEvent.KEYCODE_A, 'A'.code, KeyEvent.META_SHIFT_ON)
        assertNotNull(shiftA)
        assertEquals(GhosttyKey.keyA, shiftA!!.key)
        assertEquals(1, shiftA.mods) // bit 0 = shift
    }

    @Test
    fun testCharToGhosttyKey() {
        assertEquals(GhosttyKey.keyA, 'a'.toGhosttyKey())
        assertEquals(GhosttyKey.keyZ, 'Z'.toGhosttyKey())
        assertEquals(GhosttyKey.digit0, '0'.toGhosttyKey())
        assertEquals(GhosttyKey.digit9, '9'.toGhosttyKey())
        assertEquals(GhosttyKey.space, ' '.toGhosttyKey())
        assertNull('€'.toGhosttyKey())
    }
}
