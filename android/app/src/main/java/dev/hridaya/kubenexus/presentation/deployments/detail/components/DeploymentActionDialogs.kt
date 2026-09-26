package dev.hridaya.kubenexus.presentation.deployments.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Upper bound for the scale dialog, matching the Create Deployment form's limit. */
private const val MAX_SCALE_REPLICAS = 500

@Composable
internal fun ScaleDeploymentDialog(
    deploymentName: String,
    currentReplicas: Int,
    onReplicasChanged: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(text = "Scale Deployment")
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Update the desired replica count for \"$deploymentName\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilledIconButton(
                        onClick = { if (currentReplicas > 0) onReplicasChanged(currentReplicas - 1) },
                        enabled = currentReplicas > 0,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease replicas")
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    OutlinedTextField(
                        value = currentReplicas.toString(),
                        onValueChange = { input ->
                            val parsed = input.filter { it.isDigit() }.take(4).toIntOrNull()
                            if (parsed != null) {
                                onReplicasChanged(parsed.coerceAtMost(MAX_SCALE_REPLICAS))
                            } else if (input.isEmpty()) {
                                onReplicasChanged(0)
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.headlineSmall.copy(
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier.width(96.dp),
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledIconButton(
                        onClick = { onReplicasChanged((currentReplicas + 1).coerceAtMost(MAX_SCALE_REPLICAS)) },
                        enabled = currentReplicas < MAX_SCALE_REPLICAS,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Increase replicas")
                    }
                }

                if (currentReplicas == 0) {
                    Text(
                        text = "Scaling to 0 stops every pod of \"$deploymentName\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Scale")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun RestartDeploymentDialog(
    deploymentName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.RestartAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(text = "Rollout Restart")
        },
        text = {
            Text(
                text = "Are you sure you want to trigger a rolling restart for \"$deploymentName\"? " +
                        "A new rollout will be initiated immediately.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Restart")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
internal fun DeleteDeploymentDialog(
    deploymentName: String,
    namespace: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(text = "Delete Deployment")
        },
        text = {
            Text(
                text = "Are you sure you want to delete deployment \"$deploymentName\" in namespace \"$namespace\"? " +
                        "All associated ReplicaSets and Pods will be terminated. This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
