package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DeleteClusterDialog(
    cluster: Cluster,
    onDismiss: () -> Unit,
    onConfirmDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Remove Cluster?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "Are you sure you want to remove cluster '${cluster.name}'? This will delete the stored kubeconfig locally.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelete(cluster.id) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Remove")
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
private fun DeleteClusterDialogPreview() {
    KubeNexusTheme {
        DeleteClusterDialog(
            cluster = Cluster(
                id = "1",
                name = "production-cluster",
                serverUrl = "https://k8s.example.com:6443",
                contextName = "prod-context",
                userName = "admin",
                namespace = "default",
                rawKubeconfig = "",
            ),
            onDismiss = {},
            onConfirmDelete = {},
        )
    }
}
