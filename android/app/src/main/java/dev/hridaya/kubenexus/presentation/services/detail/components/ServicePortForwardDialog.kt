package dev.hridaya.kubenexus.presentation.services.detail.components

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
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.presentation.portforward.ActivePortForward
import dev.hridaya.kubenexus.presentation.portforward.PortForwardSessionList
import dev.hridaya.kubenexus.presentation.portforward.PortForwardStatus
import dev.hridaya.kubenexus.presentation.portforward.PortForwardUiState
import dev.hridaya.kubenexus.presentation.portforward.defaultLocalPort
import dev.hridaya.kubenexus.presentation.portforward.validateLocalPort
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ServicePortForwardDialog(
    service: ServiceDetails,
    uiState: PortForwardUiState,
    onStart: (localPort: Int, servicePort: Int) -> Unit,
    onStop: (handleId: String) -> Unit,
    onDismissError: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ports = service.ports
    val takenLocalPorts = uiState.activeForwards.map { it.localPort }.toSet()
    var selectedPort by remember(ports) {
        mutableStateOf(ports.firstOrNull()?.port)
    }
    var localPortInput by remember(ports) {
        mutableStateOf(
            ports.firstOrNull()?.port?.let { defaultLocalPort(it, takenLocalPorts).toString() }
                .orEmpty(),
        )
    }

    var wasStarting by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isStarting, uiState.error, uiState.activeForwards.size) {
        if (wasStarting && !uiState.isStarting && uiState.error == null) {
            selectedPort?.let { port ->
                localPortInput = defaultLocalPort(port, takenLocalPorts).toString()
            }
        }
        wasStarting = uiState.isStarting
    }

    LaunchedEffect(selectedPort) {
        selectedPort?.let { port ->
            localPortInput = defaultLocalPort(port, takenLocalPorts).toString()
        }
    }

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
                ServicePortsSection(
                    ports = ports,
                    selectedPort = selectedPort,
                    onSelect = { port ->
                        selectedPort = port
                        onDismissError()
                    },
                )

                Spacer(modifier = Modifier.height(12.dp))
                LocalPortField(
                    input = localPortInput,
                    errorMessage = localPortError,
                    onChange = {
                        localPortInput = it
                        onDismissError()
                    },
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
                        onStopClick = onStop,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val port = selectedPort ?: return@Button
                    val local = localPortInput.trim().toIntOrNull() ?: return@Button
                    onStart(local, port)
                },
                enabled = selectedPort != null && localPortError == null && !uiState.isStarting,
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
private fun ServicePortsSection(
    ports: List<ServicePortDetail>,
    selectedPort: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Service ports",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )

        if (ports.isEmpty()) {
            Text(
                text = "This service declares no ports.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            return@Column
        }

        ports.forEach { port ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(port.port) }
                    .padding(vertical = 2.dp),
            ) {
                RadioButton(
                    selected = port.port == selectedPort,
                    onClick = { onSelect(port.port) },
                )
                Column {
                    val targetText = if (port.targetPort > 0 && port.targetPort != port.port) {
                        "${port.port} \u2192 ${port.targetPort}/${port.protocol}"
                    } else {
                        "${port.port}/${port.protocol}"
                    }
                    Text(
                        text = targetText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                    port.name?.let { name ->
                        Text(
                            text = name,
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
private fun ServicePortForwardDialogPreview() {
    KubeNexusTheme {
        ServicePortForwardDialog(
            service = ServiceDetails(
                name = "web-frontend",
                namespace = "default",
                creationTimestampMillis = System.currentTimeMillis(),
                type = "ClusterIP",
                clusterIP = "10.96.0.42",
                clusterIPs = listOf("10.96.0.42"),
                externalIPs = emptyList(),
                selector = mapOf("app" to "web"),
                ports = listOf(
                    ServicePortDetail(
                        port = 80,
                        targetPort = 8080,
                        nodePort = null,
                        protocol = "TCP",
                        name = "http",
                    ),
                    ServicePortDetail(
                        port = 443,
                        targetPort = 8443,
                        nodePort = null,
                        protocol = "TCP",
                        name = "https",
                    ),
                ),
                labels = mapOf("app" to "web"),
                annotations = emptyMap(),
                events = emptyList(),
            ),
            uiState = PortForwardUiState(
                activeForwards = listOf(
                    ActivePortForward(
                        handleId = "pf-s1",
                        namespace = "default",
                        podName = "web-frontend-7d9f-x1",
                        localPort = 2080,
                        remotePort = 80,
                        status = PortForwardStatus.READY,
                    ),
                ),
            ),
            onStart = { _, _ -> },
            onStop = {},
            onDismissError = {},
            onDismiss = {},
        )
    }
}
