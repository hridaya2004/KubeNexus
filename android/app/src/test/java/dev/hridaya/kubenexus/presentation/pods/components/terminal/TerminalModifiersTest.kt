package dev.hridaya.kubenexus.presentation.pods.components.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TerminalModifiersTest {

    @Test
    fun `ctrl maps letters and punctuation to their control codes`() {
        val ctrl = TerminalModifiers(ctrl = true)

        assertEquals("\u0003", ctrl.apply('c'))
        assertEquals("\u0003", ctrl.apply('C'))
        assertEquals("\u001A", ctrl.apply('z'))
        assertEquals("\u001B", ctrl.apply('['))
        assertEquals("\u0000", ctrl.apply(' '))
        assertEquals("\u007F", ctrl.apply('?'))
    }

    @Test
    fun `alt prefixes escape, combined with ctrl when both are armed`() {
        assertEquals("\u001Bx", TerminalModifiers(alt = true).apply('x'))
        assertEquals("\u001B\u0002", TerminalModifiers(ctrl = true, alt = true).apply('b'))
    }

    @Test
    fun `characters without a control code are sent as typed`() {
        assertEquals("1", TerminalModifiers(ctrl = true).apply('1'))
        assertNull(TerminalModifiers.controlCode('é'))
    }

    @Test
    fun `appendedChar detects a single typed character only`() {
        assertEquals('s', appendedChar("l", "ls"))
        assertNull(appendedChar("ls", "l"))
        assertNull(appendedChar("ls", "ls -la"))
        assertNull(appendedChar("ls", "xls"))
    }
}
