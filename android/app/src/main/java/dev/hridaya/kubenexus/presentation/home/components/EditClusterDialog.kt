package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun EditClusterDialog(
    cluster: Cluster,
    onDismiss: () -> Unit,
    onSaveName: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    var nameInput by remember { mutableStateOf(cluster.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Cluster Alias",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Update the friendly display name for this Kubernetes cluster.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Cluster Alias") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (nameInput.isNotBlank()) {
                        onSaveName(cluster.id, nameInput.trim())
                    }
                },
                enabled = nameInput.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier,
    )
}

@Preview(showBackground = true)
@Composable
private fun EditClusterDialogPreview() {
    KubeNexusTheme {
        EditClusterDialog(
            cluster = Cluster(
                id = "1",
                name = "k8s-production",
                serverUrl = "https://k8s.example.com:6443",
                contextName = "prod-context",
                userName = "admin",
                namespace = "default",
                rawKubeconfig = "",
            ),
            onDismiss = {},
            onSaveName = { _, _ -> },
        )
    }
}
