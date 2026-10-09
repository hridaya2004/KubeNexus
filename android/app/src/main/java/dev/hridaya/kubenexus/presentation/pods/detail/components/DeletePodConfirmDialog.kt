package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DeletePodConfirmDialog(
    podName: String,
    namespace: String,
    isDeletingPod: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isDeletingPod) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "Delete Pod",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete pod '$podName' in namespace '$namespace'? This action will terminate running containers.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeletingPod,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(if (isDeletingPod) "Deleting" else "Delete")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isDeletingPod,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text("Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Preview(showBackground = true)
@Composable
private fun DeletePodConfirmDialogPreview() {
    KubeNexusTheme {
        DeletePodConfirmDialog(
            podName = "nginx-pod",
            namespace = "default",
            isDeletingPod = false,
            onConfirm = {},
            onDismiss = {},
        )
    }
}
