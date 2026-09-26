package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class AnsiTextParserTest {

    // "İ" (U+0130) lowercases to two chars, which used to shift the match indices past the end.
    @Test
    fun `highlighting survives characters whose lowercase form is longer`() {
        val line = "İ error"

        val rendered = parseAnsiToAnnotatedString(line, highlightQuery = "error")

        assertEquals(line, rendered.text)
    }

    @Test
    fun `highlights every case-insensitive match`() {
        val rendered = parseAnsiToAnnotatedString("Error then ERROR", highlightQuery = "error")

        assertEquals("Error then ERROR", rendered.text)
        assertEquals(2, rendered.spanStyles.count { it.item.background != Color.Unspecified })
    }
}
