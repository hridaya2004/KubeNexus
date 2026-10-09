package dev.hridaya.kubenexus.presentation.logcat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.LogLevel
import dev.hridaya.kubenexus.domain.model.LogcatEntry
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.logColors

@Composable
fun LogcatEntryRow(
    index: Int,
    entry: LogcatEntry,
    searchQuery: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    wrapLines: Boolean = true,
) {
    val levelColor = logColors.forLevel(entry.level)
    val highlightBg = MaterialTheme.colorScheme.primaryContainer
    val highlightColor = MaterialTheme.colorScheme.onPrimaryContainer

    val monoStyle = MaterialTheme.typography.labelSmall.copy(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    val rowModifier = if (wrapLines) {
        modifier.fillMaxWidth()
    } else {
        modifier.wrapContentWidth(align = Alignment.Start, unbounded = true)
    }

    Row(
        modifier = rowModifier
            .clickable(onClick = onCopy)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = index.toString().padStart(4, ' '),
            style = monoStyle.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.End,
            ),
            modifier = Modifier
                .width(32.dp)
                .alignByBaseline(),
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = if (entry.timestamp.isNotBlank()) entry.timestamp.takeLast(12) else "",
            style = monoStyle.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            ),
            maxLines = 1,
            modifier = Modifier
                .width(80.dp)
                .alignByBaseline(),
        )

        Spacer(modifier = Modifier.width(4.dp))

        Box(
            modifier = Modifier
                .width(18.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(levelColor.copy(alpha = 0.2f))
                .padding(vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.level.code,
                style = monoStyle.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = levelColor,
                    textAlign = TextAlign.Center,
                ),
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = entry.tag,
            style = monoStyle.copy(
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(125.dp)
                .alignByBaseline(),
        )

        Spacer(modifier = Modifier.width(8.dp))

        val annotatedMessage = remember(entry.message, searchQuery, highlightBg, highlightColor) {
            buildAnnotatedMessage(entry.message, searchQuery, highlightBg, highlightColor)
        }

        val textModifier = if (wrapLines) {
            Modifier.weight(1f, fill = false)
        } else {
            Modifier
        }

        Text(
            text = annotatedMessage,
            style = monoStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            softWrap = wrapLines,
            modifier = textModifier.alignByBaseline(),
        )
    }
}

private fun buildAnnotatedMessage(
    message: String,
    query: String,
    highlightBg: Color,
    highlightColor: Color,
): AnnotatedString {
    if (query.isBlank()) return AnnotatedString(message)
    val builder = AnnotatedString.Builder()
    var lastIndex = 0
    var index = message.indexOf(query, 0, ignoreCase = true)
    while (index >= 0) {
        builder.append(message.substring(lastIndex, index))
        builder.pushStyle(
            SpanStyle(
                background = highlightBg,
                color = highlightColor,
                fontWeight = FontWeight.Bold,
            ),
        )
        builder.append(message.substring(index, index + query.length))
        builder.pop()
        lastIndex = index + query.length
        index = message.indexOf(query, lastIndex, ignoreCase = true)
    }
    if (lastIndex < message.length) {
        builder.append(message.substring(lastIndex))
    }
    return builder.toAnnotatedString()
}

@Preview(showBackground = true)
@Composable
private fun LogcatEntryRowPreview() {
    KubeNexusTheme {
        LogcatEntryRow(
            index = 1,
            entry = LogcatEntry(
                id = 1L,
                timestamp = "16:42:05.123",
                pid = "1234",
                tid = "5678",
                level = LogLevel.INFO,
                tag = "KubeNexusNative",
                message = "Connected to Kubernetes API",
                raw = "08-19 16:42:05.123  1234  5678 I KubeNexusNative: Connected to Kubernetes API",
            ),
            searchQuery = "Kubernetes",
            onCopy = {},
        )
    }
}
