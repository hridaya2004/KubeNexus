package dev.hridaya.kubenexus.presentation.portforward

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/**
 * Dialog for starting and managing port-forward tunnels to the pod's declared
 * container ports.
 */
@Composable
fun PortForwardDialog(
    podDetails: PodDetails,
    uiState: PortForwardUiState,
    onAction: (PortForwardUiAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = podPortTargets(podDetails.containers)
    var selectedRemote by remember(targets) {
        mutableStateOf(targets.firstOrNull()?.remotePort)
    }
    var localPortInput by remember(targets) { mutableStateOf("") }

    LaunchedEffect(selectedRemote) {
        selectedRemote?.let { remote ->
            localPortInput = defaultLocalPort(remote).toString()
        }
    }

    val takenLocalPorts = uiState.activeForwards.map { it.localPort }.toSet()
    val localPortError = validateLocalPort(localPortInput, takenLocalPorts)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Port Forward",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                RemotePortSection(
                    targets = targets,
                    selectedRemote = selectedRemote,
                    onSelect = { remote -> selectedRemote = remote },
                )

                Spacer(modifier = Modifier.height(12.dp))
                LocalPortField(
                    input = localPortInput,
                    errorMessage = localPortError,
                    onChange = { localPortInput = it },
                )

                uiState.error?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (uiState.activeForwards.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    PortForwardSessionList(
                        forwards = uiState.activeForwards,
                        onStopClick = { handleId ->
                            onAction(
                                PortForwardUiAction.StopForward(
                                    handleId
                                )
                            )
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val remote = selectedRemote ?: return@Button
                    val local = localPortInput.trim().toIntOrNull() ?: return@Button
                    onAction(
                        PortForwardUiAction.StartForward(
                            localPort = local,
                            remotePort = remote
                        )
                    )
                },
                enabled = selectedRemote != null && localPortError == null && !uiState.isStarting,
                shapes = ButtonDefaults.shapes(),
            ) {
                if (uiState.isStarting) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isStarting) "Starting" else "Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("Close")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun RemotePortSection(
    targets: List<PodPortTarget>,
    selectedRemote: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Container ports",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )

        if (targets.isEmpty()) {
            Text(
                text = "This pod declares no container ports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }

        targets.forEach { target ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(target.remotePort) }
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = target.remotePort == selectedRemote,
                    onClick = { onSelect(target.remotePort) },
                )
                Column {
                    Text(
                        text = "${target.containerName}: ${target.remoteLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    target.hostHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalPortField(
    input: String,
    errorMessage: String?,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = input,
        onValueChange = onChange,
        label = { Text("Local port") },
        prefix = { Text("127.0.0.1:") },
        singleLine = true,
        isError = errorMessage != null,
        supportingText = {
            Text(
                text = errorMessage ?: "Defaults to the remote port plus 2000",
                color =
                    if (errorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun PortForwardDialogPreview() {
    KubeNexusTheme {
        PortForwardDialog(
            podDetails = PodDetails(
                name = "nginx-deployment-78f56c879d-gqw87",
                namespace = "default",
                status = PodStatus.RUNNING,
                containers = listOf(
                    ContainerDetail(
                        name = "nginx",
                        image = "nginx:1.25-alpine",
                        ready = true,
                        restartCount = 0,
                        ports = listOf("80/TCP"),
                    ),
                    ContainerDetail(
                        name = "sidecar",
                        image = "envoy:1.30",
                        ready = true,
                        restartCount = 2,
                        ports = listOf("9901/TCP", "8080:8080/TCP"),
                    ),
                ),
            ),
            uiState = PortForwardUiState(
                activeForwards = listOf(
                    ActivePortForward(
                        handleId = "pf-a1",
                        namespace = "default",
                        podName = "nginx-deployment-78f56c879d-gqw87",
                        localPort = 2080,
                        remotePort = 80,
                        status = PortForwardStatus.READY,
                    ),
                ),
            ),
            onAction = {},
            onDismiss = {},
        )
    }
}
