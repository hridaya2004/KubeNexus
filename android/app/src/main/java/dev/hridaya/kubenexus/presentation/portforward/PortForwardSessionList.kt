package dev.hridaya.kubenexus.presentation.portforward

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlinx.coroutines.delay

private const val COPY_FEEDBACK_MILLIS = 1500L

/**
 * Active tunnels listed inside the port-forward dialog. Each row shows the
 * copyable local address plus a per-row stop control.
 */
@Composable
internal fun PortForwardSessionList(
    forwards: List<ActivePortForward>,
    onStopClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Active forwards",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )

        forwards.forEach { forward ->
            PortForwardSessionRow(
                forward = forward,
                onStopClick = { onStopClick(forward.handleId) },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PortForwardSessionRow(
    forward: ActivePortForward,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isCopied by remember(forward.handleId) { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(COPY_FEEDBACK_MILLIS)
            isCopied = false
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        SessionStatusDot(status = forward.status)

        Column(
            verticalArrangement = Arrangement.spacedBy(1.dp),
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = forward.localAddress,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Port forward", forward.localAddress),
                        )
                        isCopied = true
                    },
                    modifier = Modifier.size(28.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Outlined.Check else Icons.Outlined.ContentCopy,
                        contentDescription = "Copy ${forward.localAddress}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            Text(
                text = forward.subtitle,
                style = MaterialTheme.typography.labelSmall,
                color =
                    if (forward.status == PortForwardStatus.ERROR) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        TextButton(onClick = onStopClick, shapes = ButtonDefaults.shapes()) {
            Text("Stop", style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val ActivePortForward.subtitle: String
    get() = when (status) {
        PortForwardStatus.STARTING -> "$targetLabel - starting"
        PortForwardStatus.READY -> "$targetLabel - ready"
        PortForwardStatus.ERROR -> message?.let { "$targetLabel - $it" } ?: "$targetLabel - failed"
    }

@Composable
private fun SessionStatusDot(status: PortForwardStatus, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun PortForwardSessionListPreview() {
    KubeNexusTheme {
        PortForwardSessionList(
            forwards = listOf(
                ActivePortForward(
                    handleId = "pf-a1",
                    namespace = "default",
                    podName = "nginx-7d9f",
                    localPort = 8080,
                    remotePort = 80,
                    status = PortForwardStatus.READY,
                ),
                ActivePortForward(
                    handleId = "pf-b2",
                    namespace = "default",
                    podName = "nginx-7d9f",
                    localPort = 5432,
                    remotePort = 5432,
                    status = PortForwardStatus.ERROR,
                    message = "local port already in use",
                ),
            ),
            onStopClick = {},
        )
    }
}
