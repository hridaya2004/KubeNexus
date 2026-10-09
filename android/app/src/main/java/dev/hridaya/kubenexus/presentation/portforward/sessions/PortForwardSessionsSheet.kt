package dev.hridaya.kubenexus.presentation.portforward.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.ActivePortForwardSession
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

private val SESSION_LIST_MAX_HEIGHT = 420.dp

/**
 * Global view of every port-forward tunnel tracked by the manager. Fully
 * stateless: hosts pass the hoisted [PortForwardSessionsUiState] plus a single
 * action sink and own sheet visibility.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PortForwardSessionsSheet(
    uiState: PortForwardSessionsUiState,
    onAction: (PortForwardSessionsUiAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        PortForwardSessionsSheetContent(
            uiState = uiState,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
    }
}

@Composable
internal fun PortForwardSessionsSheetContent(
    uiState: PortForwardSessionsUiState,
    onAction: (PortForwardSessionsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        SheetHeader(uiState = uiState, onAction = onAction)

        Spacer(modifier = Modifier.height(12.dp))

        val rows = uiState.visibleSessions
        if (rows.isEmpty()) {
            EmptySessionsView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = SESSION_LIST_MAX_HEIGHT),
            ) {
                items(items = rows, key = { it.handleId }) { session ->
                    PortForwardSessionRow(session = session, onAction = onAction)
                }
            }
        }

        if (uiState.canStopAll) {
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { onAction(PortForwardSessionsUiAction.StopAllActive) },
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Stop all (${uiState.activeCount})")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun SheetHeader(
    uiState: PortForwardSessionsUiState,
    onAction: (PortForwardSessionsUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Port forwards",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            val activeLabel =
                if (uiState.activeCount == 1) "1 active tunnel" else "${uiState.activeCount} active tunnels"
            Text(
                text = activeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (uiState.hasStoppedRows) {
            FilterChip(
                selected = uiState.includeStopped,
                onClick = { onAction(PortForwardSessionsUiAction.ToggleIncludeStopped) },
                label = { Text(text = "Show stopped") },
                leadingIcon = if (uiState.includeStopped) {
                    {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun EmptySessionsView(modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Outlined.SettingsEthernet,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No active port forwards",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Start a tunnel from any pod or service and it will show up here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun PortForwardSessionsSheetContentPreview() {
    KubeNexusTheme {
        PortForwardSessionsSheetContent(
            uiState = PortForwardSessionsUiState(
                sessions = listOf(
                    ActivePortForwardSession(
                        handleId = "pf-a1",
                        namespace = "default",
                        targetName = "nginx",
                        podName = "nginx-7d9f",
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
                        namespace = "dev",
                        targetName = "redis",
                        localPort = 6379,
                        remotePort = 6379,
                        status = PortForwardSessionStatus.STOPPED,
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
