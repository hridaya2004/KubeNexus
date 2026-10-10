package dev.hridaya.kubenexus.presentation.resources.list.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import dev.hridaya.kubenexus.presentation.resources.RowDetail
import dev.hridaya.kubenexus.presentation.resources.StatusTone
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.StatusColors
import dev.hridaya.kubenexus.ui.theme.statusColors

/** One list row of any resource kind. */
@Composable
fun ResourceCard(
    row: ResourceRow,
    showNamespace: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
) {
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            TitleLine(row = row, showNamespace = showNamespace)
            row.details.forEach { detail -> DetailLine(detail) }
            row.createdAtMillis?.let { createdAt ->
                Text(
                    text = DateUtils.getRelativeTimeSpanString(createdAt).toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TitleLine(row: ResourceRow, showNamespace: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        row.status?.let { tone ->
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color = statusColors.forTone(tone), shape = CircleShape),
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = row.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        row.badge?.let { badge ->
            Spacer(modifier = Modifier.width(8.dp))
            Tag(
                text = badge,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (showNamespace && row.namespace.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            Tag(
                text = row.namespace,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun DetailLine(detail: RowDetail) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        detail.label?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = detail.text,
            style = if (detail.monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (detail.label != null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Tag(text: String, containerColor: Color, contentColor: Color) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun StatusColors.forTone(tone: StatusTone): Color = when (tone) {
    StatusTone.Healthy -> running
    StatusTone.Pending -> pending
    StatusTone.Completed -> completed
    StatusTone.Failed -> failed
    StatusTone.Unknown -> offline
}

@Preview(showBackground = true)
@Composable
private fun ResourceCardPreview() {
    KubeNexusTheme {
        ResourceCard(
            row = ResourceRow(
                id = "default_web",
                name = "web-frontend",
                namespace = "default",
                status = StatusTone.Healthy,
                badge = "ClusterIP",
                details = listOf(
                    RowDetail("10.96.0.42", label = "ClusterIP:", monospace = true),
                    RowDetail("80:8080/TCP"),
                ),
                createdAtMillis = System.currentTimeMillis(),
            ),
            showNamespace = true,
            onClick = {},
        )
    }
}
