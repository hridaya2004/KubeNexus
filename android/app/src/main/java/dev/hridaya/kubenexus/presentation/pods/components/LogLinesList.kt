package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun LogLinesList(
    lines: VisibleLogLines,
    wrapLines: Boolean,
    highlightQuery: String,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val horizontalScrollState = rememberScrollState()
    val lineModifier =
        if (wrapLines) {
            Modifier.fillMaxWidth()
        } else {
            Modifier.horizontalScroll(horizontalScrollState)
        }

    SelectionContainer(modifier = lineModifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = modifier.fillMaxSize(),
        ) {
            items(count = lines.size, key = lines::numberAt) { position ->
                val line = lines.lineAt(position)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.dp),
                ) {
                    Text(
                        text = lines.numberAt(position).toString().padStart(4, ' '),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = TerminalGutter,
                        modifier = Modifier.width(36.dp),
                    )

                    val parsedLine = remember(line, highlightQuery) {
                        parseAnsiToAnnotatedString(line, highlightQuery)
                    }

                    // Unwrapped lines sit in a horizontal scroll, where the row's width is
                    // unbounded: a weight there measures the text at zero width, so it only
                    // applies when wrapping, as in the Logcat list.
                    Text(
                        text = parsedLine,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = TerminalText,
                        softWrap = wrapLines,
                        modifier = if (wrapLines) Modifier.weight(1f) else Modifier,
                    )
                }
            }
        }
    }
}

/** The log lines on screen: all of [lines], or only those at [indices] while searching. */
internal class VisibleLogLines(
    private val lines: List<String>,
    private val indices: List<Int>?,
    private val firstLineNumber: Int,
) {
    val size: Int get() = indices?.size ?: lines.size

    fun isEmpty(): Boolean = size == 0

    fun isNotEmpty(): Boolean = size > 0

    fun lineAt(position: Int): String = lines.getOrElse(indexAt(position)) { "" }

    fun numberAt(position: Int): Int = firstLineNumber + indexAt(position)

    fun lastOrNull(): String? = if (isEmpty()) null else lineAt(size - 1)

    private fun indexAt(position: Int): Int = indices?.getOrNull(position) ?: position
}
