package dev.hridaya.kubenexus.presentation.pods.components.terminal

import dev.hridaya.kubenexus.core.terminal.TerminalSnapshot

data class TerminalSelection(
    val anchorIndex: Int,
    val focusIndex: Int,
) {
    fun normalized(cellCount: Int): IntRange? {
        if (cellCount <= 0) return null
        val start = minOf(anchorIndex, focusIndex).coerceIn(0, cellCount - 1)
        val end = maxOf(anchorIndex, focusIndex).coerceIn(0, cellCount - 1)
        return start..end
    }

    fun withStart(newStartCell: Int, updateAnchor: Boolean): TerminalSelection =
        if (updateAnchor) copy(anchorIndex = newStartCell)
        else copy(focusIndex = newStartCell)

    fun withEnd(newEndCell: Int, updateAnchor: Boolean): TerminalSelection =
        if (updateAnchor) copy(anchorIndex = newEndCell)
        else copy(focusIndex = newEndCell)
}

internal fun TerminalSnapshot.isSpacerContinuation(cellIndex: Int): Boolean {
    return ((flags[cellIndex].toInt() and 0xFF) and TerminalSnapshot.CELL_FLAG_SPACER) != 0
}

/** Codepoints below this (ASCII, Latin, box drawing, CJK punctuation) have cached glyph strings. */
private const val GLYPH_CACHE_SIZE = 0x3000

/**
 * The canvas draws every non-blank cell on every frame, so single-codepoint glyphs are cached
 * instead of allocating a String per cell per frame. Written only from the UI thread.
 */
private val glyphCache = arrayOfNulls<String>(GLYPH_CACHE_SIZE)

private fun glyphString(codepoint: Int): String {
    if (!Character.isValidCodePoint(codepoint)) return "\uFFFD"
    if (codepoint < GLYPH_CACHE_SIZE) {
        return glyphCache[codepoint]
            ?: String(Character.toChars(codepoint)).also { glyphCache[codepoint] = it }
    }
    return String(Character.toChars(codepoint))
}

internal fun TerminalSnapshot.glyphAt(cellIndex: Int): String {
    val codepoint = codepoints[cellIndex]
    val extras = graphemeExtras[cellIndex]
    if (extras == null || extras.isEmpty()) return glyphString(codepoint)
    val builder = StringBuilder(1 + extras.size)
    builder.appendCodePoint(if (Character.isValidCodePoint(codepoint)) codepoint else 0xFFFD)
    for (cp in extras) if (Character.isValidCodePoint(cp)) builder.appendCodePoint(cp)
    return builder.toString()
}

internal fun TerminalSnapshot.wordAt(cellIndex: Int): IntRange? {
    if (cols <= 0 || cellIndex !in codepoints.indices) return null
    val row = cellIndex / cols
    val rowStart = row * cols
    val rowEnd = rowStart + cols - 1

    val cp = codepoints[cellIndex]
    if (cp == 0 || (cp == 32 && !isSpacerContinuation(cellIndex))) return null

    var start = cellIndex
    while (start > rowStart) {
        val prev = start - 1
        val prevCp = codepoints[prev]
        if (prevCp == 0 || (prevCp == 32 && !isSpacerContinuation(prev))) break
        start--
    }
    var end = cellIndex
    while (end < rowEnd) {
        val next = end + 1
        val nextCp = codepoints[next]
        if (nextCp == 0 || (nextCp == 32 && !isSpacerContinuation(next))) break
        end++
    }
    return start..end
}

internal fun extractSelectionText(snapshot: TerminalSnapshot, range: IntRange): String? {
    if (snapshot.cols <= 0 || snapshot.codepoints.isEmpty()) return null

    val normalizedRange = IntRange(
        start = range.first.coerceIn(0, snapshot.codepoints.lastIndex),
        endInclusive = range.last.coerceIn(0, snapshot.codepoints.lastIndex),
    )
    val startRow = normalizedRange.first / snapshot.cols
    val endRow = normalizedRange.last / snapshot.cols
    val builder =
        StringBuilder(normalizedRange.last - normalizedRange.first + 1 + (endRow - startRow))

    for (row in startRow..endRow) {
        val rowStart = row * snapshot.cols
        val from = maxOf(normalizedRange.first, rowStart)
        val until = minOf(normalizedRange.last, rowStart + snapshot.cols - 1)
        var lastContentIdx = until
        while (lastContentIdx >= from) {
            val cp = snapshot.codepoints[lastContentIdx]
            if (cp != 0 && (cp != 32 || snapshot.isSpacerContinuation(lastContentIdx))) break
            lastContentIdx--
        }
        for (index in from..lastContentIdx) {
            val codepoint = snapshot.codepoints[index]
            if (codepoint == 0 || (codepoint == 32 && !snapshot.isSpacerContinuation(index))) {
                builder.append(' ')
            } else if (codepoint != 32) {
                builder.append(snapshot.glyphAt(index))
            }
        }
        if (row != endRow) {
            builder.append('\n')
        }
    }

    return builder.toString()
}
