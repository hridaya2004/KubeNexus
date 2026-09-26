package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

internal fun parseAnsiToAnnotatedString(
    rawText: String,
    highlightQuery: String = ""
): AnnotatedString {
    val cleanText = rawText.replace("\r", "")
    val builder = AnnotatedString.Builder()

    // ANSI parser regex: \u001B\[[0-9;]*m
    val ansiRegex = Regex("""\u001B\[([0-9;]*)m""")
    var lastIndex = 0
    var currentColor = TerminalText
    var isBold = false

    val matches = ansiRegex.findAll(cleanText).toList()

    if (matches.isEmpty()) {
        appendWithHighlight(builder, cleanText, currentColor, isBold, highlightQuery)
        return builder.toAnnotatedString()
    }

    for (match in matches) {
        val plainChunk = cleanText.substring(lastIndex, match.range.first)
        if (plainChunk.isNotEmpty()) {
            appendWithHighlight(builder, plainChunk, currentColor, isBold, highlightQuery)
        }

        val styleCodesText = match.groupValues.getOrNull(1).orEmpty()
        val codes = styleCodesText.split(";").mapNotNull { it.toIntOrNull() }

        if (codes.isEmpty() || codes.contains(0)) {
            currentColor = TerminalText
            isBold = false
        }
        if (codes.contains(1)) isBold = true

        for (code in codes) {
            when (code) {
                30 -> currentColor = TerminalGutter
                31 -> currentColor = TerminalRed
                32 -> currentColor = TerminalGreen
                33 -> currentColor = TerminalYellow
                34 -> currentColor = TerminalCyan
                35 -> currentColor = TerminalPurple
                36 -> currentColor = TerminalCyan
                37, 39 -> currentColor = TerminalText
                90 -> currentColor = TerminalGutter
                91 -> currentColor = TerminalRed
                92 -> currentColor = TerminalGreen
                93 -> currentColor = TerminalYellow
                94 -> currentColor = TerminalCyan
                95 -> currentColor = TerminalPurple
                96 -> currentColor = TerminalCyan
                97 -> currentColor = Color.White
            }
        }

        lastIndex = match.range.last + 1
    }

    if (lastIndex < cleanText.length) {
        val remaining = cleanText.substring(lastIndex)
        appendWithHighlight(builder, remaining, currentColor, isBold, highlightQuery)
    }

    return builder.toAnnotatedString()
}

private fun appendWithHighlight(
    builder: AnnotatedString.Builder,
    text: String,
    baseColor: Color,
    isBold: Boolean,
    query: String
) {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        builder.withStyle(
            SpanStyle(
                color = baseColor,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            ),
        ) {
            append(text)
        }
        return
    }

    // Matched case-insensitively in place. Searching a lowercase() copy gave indices into a
    // string that can be longer than the original ("İ" lowercases to two chars), and
    // substring() then threw during composition.
    var start = 0

    while (start < text.length) {
        val index = text.indexOf(query, start, ignoreCase = true)
        if (index == -1) {
            builder.withStyle(
                SpanStyle(
                    color = baseColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                ),
            ) {
                append(text.substring(start))
            }
            break
        }

        if (index > start) {
            builder.withStyle(
                SpanStyle(
                    color = baseColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                ),
            ) {
                append(text.substring(start, index))
            }
        }

        val matchEnd = index + query.length
        builder.withStyle(
            SpanStyle(
                color = Color.Black,
                background = TerminalYellow,
                fontWeight = FontWeight.Bold,
            ),
        ) {
            append(text.substring(index, matchEnd))
        }

        start = matchEnd
    }
}
