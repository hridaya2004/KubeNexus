package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ContainerCard(
    container: ContainerDetail,
    onViewLogsClick: () -> Unit,
    onOpenTerminalClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInitContainer: Boolean = false,
    isOnline: Boolean = true,
) {
    val isAttachable =
        isOnline && (container.ready || container.state.equals("Running", ignoreCase = true))

    ElevatedCard(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                    if (isInitContainer) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                        ) {
                            Text(
                                text = "Init",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            )
                        }
                    }
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (container.ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = if (container.ready) "Ready" else "Not Ready",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Image: ${container.image}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            DetailItem("State", container.state)
            DetailItem("Restarts", "${container.restartCount}")
            if (container.ports.isNotEmpty()) {
                DetailItem("Ports", container.ports.joinToString(", "))
            }

            Spacer(modifier = Modifier.height(8.dp))
            ButtonGroup(
                overflowIndicator = {},
                modifier = Modifier.fillMaxWidth(),
            ) {
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        OutlinedButton(
                            onClick = onViewLogsClick,
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interactionSource),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Description,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Logs", style = MaterialTheme.typography.labelMedium)
                        }
                    },
                    menuContent = { state ->
                        DropdownMenuItem(
                            text = { Text("Logs") },
                            onClick = {
                                state.dismiss()
                                onViewLogsClick()
                            },
                        )
                    },
                )
                customItem(
                    buttonGroupContent = {
                        val interactionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = onOpenTerminalClick,
                            enabled = isAttachable,
                            interactionSource = interactionSource,
                            modifier = Modifier
                                .weight(1f)
                                .animateWidth(interactionSource),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                if (isAttachable) "Terminal" else "Detached",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    },
                    menuContent = { state ->
                        DropdownMenuItem(
                            text = { Text("Terminal") },
                            enabled = isAttachable,
                            onClick = {
                                state.dismiss()
                                onOpenTerminalClick()
                            },
                        )
                    },
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContainerCardPreview() {
    KubeNexusTheme {
        ContainerCard(
            container = ContainerDetail(
                name = "nginx",
                image = "nginx:alpine",
                ready = true,
                restartCount = 0,
                state = "Running",
                ports = listOf("80/TCP"),
            ),
            onViewLogsClick = {},
            onOpenTerminalClick = {},
            isInitContainer = false,
            isOnline = true,
        )
    }
}
