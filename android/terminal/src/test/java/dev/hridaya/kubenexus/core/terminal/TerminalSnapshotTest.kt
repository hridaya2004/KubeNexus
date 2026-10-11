package dev.hridaya.kubenexus.core.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TerminalSnapshotTest {

    @Test
    fun testFromByteBuffer_decodesHeaderAndCellsCorrectly() {
        val cols = 2
        val rows = 2
        val cellCount = cols * rows
        val headerBytes = 14 * 4
        val cellBytes = cellCount * 11
        val totalSize = headerBytes + cellBytes

        val buffer = ByteBuffer.allocateDirect(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(cols)
        buffer.putInt(rows)
        buffer.putInt(1) // cursorX
        buffer.putInt(0) // cursorY
        buffer.putInt(1) // cursorVisible = true
        buffer.putInt(13) // defaultBgR
        buffer.putInt(17) // defaultBgG
        buffer.putInt(23) // defaultBgB
        buffer.putInt(201) // defaultFgR
        buffer.putInt(209) // defaultFgG
        buffer.putInt(217) // defaultFgB
        buffer.putInt(0) // extrasOffset
        buffer.putInt(0) // viewportScrollY
        buffer.putInt(0) // appHandlesSelectionDrag

        // Cell 0: 'K', fg=(255, 255, 255), bg=(0, 0, 0), flags=1 (bold)
        buffer.putInt('K'.code)
        buffer.put(255.toByte()); buffer.put(255.toByte()); buffer.put(255.toByte())
        buffer.put(0.toByte()); buffer.put(0.toByte()); buffer.put(0.toByte())
        buffer.put(TerminalSnapshot.CELL_FLAG_BOLD.toByte())

        // Cell 1: 'u', fg=(200, 200, 200), bg=(10, 10, 10), flags=0
        buffer.putInt('u'.code)
        buffer.put(200.toByte()); buffer.put(200.toByte()); buffer.put(200.toByte())
        buffer.put(10.toByte()); buffer.put(10.toByte()); buffer.put(10.toByte())
        buffer.put(0.toByte())

        // Cell 2: 'b', fg=(200, 200, 200), bg=(10, 10, 10), flags=0
        buffer.putInt('b'.code)
        buffer.put(200.toByte()); buffer.put(200.toByte()); buffer.put(200.toByte())
        buffer.put(10.toByte()); buffer.put(10.toByte()); buffer.put(10.toByte())
        buffer.put(0.toByte())

        // Cell 3: 'e', fg=(200, 200, 200), bg=(10, 10, 10), flags=0
        buffer.putInt('e'.code)
        buffer.put(200.toByte()); buffer.put(200.toByte()); buffer.put(200.toByte())
        buffer.put(10.toByte()); buffer.put(10.toByte()); buffer.put(10.toByte())
        buffer.put(0.toByte())

        val snapshot = TerminalSnapshot.fromByteBuffer(buffer)

        assertEquals(2, snapshot.cols)
        assertEquals(2, snapshot.rows)
        assertEquals(1, snapshot.cursorX)
        assertEquals(0, snapshot.cursorY)
        assertTrue(snapshot.cursorVisible)
        assertEquals('K'.code, snapshot.codepoints[0])
        assertEquals('u'.code, snapshot.codepoints[1])
        assertEquals('b'.code, snapshot.codepoints[2])
        assertEquals('e'.code, snapshot.codepoints[3])
        assertEquals(TerminalSnapshot.CELL_FLAG_BOLD.toByte(), snapshot.flags[0])
    }
}
