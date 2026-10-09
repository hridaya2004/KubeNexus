package dev.hridaya.kubenexus.presentation.portforward

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/**
 * Compact persistent indicator for one active tunnel:
 * "127.0.0.1:8080 -> pod:80 [x]".
 */
@Composable
fun ActivePortForwardChip(
    forward: ActivePortForward,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 2.dp),
        ) {
            StatusDot(status = forward.status)

            Text(
                text = "${forward.localAddress} -> ${forward.targetLabel}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 6.dp),
            )

            IconButton(
                onClick = onStopClick,
                modifier = Modifier.size(24.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Stop forward on port ${forward.localPort}",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Small colored dot reflecting tunnel status inside the chip. */
@Composable
private fun StatusDot(status: PortForwardStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        PortForwardStatus.READY -> MaterialTheme.colorScheme.primary
        PortForwardStatus.STARTING -> MaterialTheme.colorScheme.tertiary
        PortForwardStatus.ERROR -> MaterialTheme.colorScheme.error
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .background(color = color, shape = CircleShape),
    )
}

/**
 * Horizontally scrolling row of chips shown beneath the top bar while any
 * tunnel from this pod is alive.
 */
@Composable
fun ActivePortForwardChipsRow(
    forwards: List<ActivePortForward>,
    onStopClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.defaultMinSize(minHeight = 32.dp),
    ) {
        items(forwards.size, key = { forwards[it].handleId }) { index ->
            val forward = forwards[index]
            ActivePortForwardChip(
                forward = forward,
                onStopClick = { onStopClick(forward.handleId) },
            )
        }
    }
}

private val previewForwards = listOf(
    ActivePortForward(
        handleId = "pf-a1",
        namespace = "default",
        podName = "nginx",
        localPort = 8080,
        remotePort = 80,
        status = PortForwardStatus.READY,
    ),
    ActivePortForward(
        handleId = "pf-b2",
        namespace = "default",
        podName = "nginx",
        localPort = 5432,
        remotePort = 5432,
        status = PortForwardStatus.ERROR,
        message = "connection refused",
    ),
)

@Preview(showBackground = true)
@Composable
private fun ActivePortForwardChipsRowPreview() {
    KubeNexusTheme {
        ActivePortForwardChipsRow(
            forwards = previewForwards,
            onStopClick = {},
        )
    }
}
