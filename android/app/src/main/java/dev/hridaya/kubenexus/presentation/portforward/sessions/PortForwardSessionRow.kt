package dev.hridaya.kubenexus.presentation.portforward.sessions

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/**
 * One tunnel row: kind badge, "namespace/target" title with status chip,
 * monospace endpoint line, optional error message, and a trailing control
 * (Stop while live, Dismiss once stopped).
 */
@Composable
internal fun PortForwardSessionRow(
    session: ActivePortForwardSession,
    onAction: (PortForwardSessionsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            KindBadge(kind = session.kind)

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = session.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    SessionStatusChip(status = session.status)
                }

                Text(
                    text = session.endpointLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val errorMessage = session.message
                if (session.status == PortForwardSessionStatus.ERROR && errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            RowControl(session = session, onAction = onAction)
        }
    }
}

@Composable
private fun KindBadge(kind: PortForwardTargetKind, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
        modifier = modifier.size(40.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when (kind) {
                    PortForwardTargetKind.Pod -> Icons.Outlined.Widgets
                    PortForwardTargetKind.Service -> Icons.Outlined.Dns
                },
                contentDescription = kind.name,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SessionStatusChip(status: PortForwardSessionStatus, modifier: Modifier = Modifier) {
    val containerColor = when (status) {
        PortForwardSessionStatus.STARTING -> MaterialTheme.colorScheme.secondaryContainer
        PortForwardSessionStatus.READY -> MaterialTheme.colorScheme.primaryContainer
        PortForwardSessionStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
        PortForwardSessionStatus.STOPPED -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when (status) {
        PortForwardSessionStatus.STARTING -> MaterialTheme.colorScheme.onSecondaryContainer
        PortForwardSessionStatus.READY -> MaterialTheme.colorScheme.onPrimaryContainer
        PortForwardSessionStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
        PortForwardSessionStatus.STOPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(shape = CircleShape, color = containerColor, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            when (status) {
                PortForwardSessionStatus.STARTING ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(10.dp),
                        strokeWidth = 1.5.dp,
                        color = contentColor,
                    )

                PortForwardSessionStatus.READY, PortForwardSessionStatus.ERROR ->
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(color = contentColor, shape = CircleShape),
                    )

                PortForwardSessionStatus.STOPPED -> Unit
            }
            Text(
                text = status.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

@Composable
private fun RowControl(
    session: ActivePortForwardSession,
    onAction: (PortForwardSessionsUiAction) -> Unit,
) {
    if (session.isStopped) {
        TextButton(onClick = { onAction(PortForwardSessionsUiAction.DismissStopped(session.handleId)) }, shapes = ButtonDefaults.shapes()) {
            Text(text = "Dismiss", style = MaterialTheme.typography.labelMedium)
        }
    } else {
        IconButton(onClick = { onAction(PortForwardSessionsUiAction.Stop(session.handleId)) }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = "Stop forwarding ${session.title}",
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PortForwardSessionRowPreview() {
    KubeNexusTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                ActivePortForwardSession(
                    handleId = "pf-a1",
                    kind = PortForwardTargetKind.Pod,
                    namespace = "default",
                    targetName = "nginx-7d9f",
                    podName = "nginx-7d9f-x2k1",
                    localPort = 8080,
                    remotePort = 80,
                    status = PortForwardSessionStatus.READY,
                ),
                ActivePortForwardSession(
                    handleId = "pf-b2",
                    kind = PortForwardTargetKind.Service,
                    namespace = "prod",
                    targetName = "postgres",
                    localPort = 5432,
                    remotePort = 5432,
                    status = PortForwardSessionStatus.STARTING,
                ),
                ActivePortForwardSession(
                    handleId = "pf-c3",
                    kind = PortForwardTargetKind.Pod,
                    namespace = "dev",
                    targetName = "api-5c8f",
                    podName = "api-5c8f-9dpp",
                    localPort = 9090,
                    remotePort = 9090,
                    status = PortForwardSessionStatus.ERROR,
                    message = "local port already in use",
                ),
                ActivePortForwardSession(
                    handleId = "pf-d4",
                    namespace = "dev",
                    targetName = "redis",
                    localPort = 6379,
                    remotePort = 6379,
                    status = PortForwardSessionStatus.STOPPED,
                ),
            ).forEach { session ->
                PortForwardSessionRow(session = session, onAction = {})
            }
        }
    }
}
