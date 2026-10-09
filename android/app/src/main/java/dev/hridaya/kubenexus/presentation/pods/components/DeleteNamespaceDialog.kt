package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DeleteNamespaceDialog(
    namespace: String,
    clusterName: String?,
    onDismiss: () -> Unit,
    onConfirmDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = "Delete Namespace?",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            val clusterText =
                if (!clusterName.isNullOrBlank()) " from cluster '$clusterName'" else ""
            Text(
                text = "Are you sure you want to delete namespace '$namespace'$clusterText? All resources in this namespace will be permanently removed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmDelete(namespace) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Delete")
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
private fun DeleteNamespaceDialogPreview() {
    KubeNexusTheme {
        DeleteNamespaceDialog(
            namespace = "staging-feature-x",
            clusterName = "production-cluster",
            onDismiss = {},
            onConfirmDelete = {},
        )
    }
}
