package dev.hridaya.kubenexus.presentation.pods.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Asks before pod logs leave the device for a third-party paste service.
 *
 * The upload is public and outlives the session, so the user is told where the logs go,
 * who can read them and for how long before anything is sent.
 */
@Composable
fun LogUploadConfirmDialog(
    lineCount: Int,
    serviceName: String,
    retention: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.CloudUpload, contentDescription = null) },
        title = { Text("Upload logs to $serviceName?") },
        text = {
            Text(
                "${if (lineCount == 1) "This log line" else "These $lineCount log lines"} will be " +
                    "sent to $serviceName, a public paste service that KubeNexus does not " +
                    "operate. Anyone with the link can read them for $retention.\n\n" +
                    "Tokens, passwords and keys that KubeNexus recognises are redacted first, " +
                    "but logs can still contain personal or confidential data from your " +
                    "workload. Only upload logs you are allowed to share.",
            )
        },
        confirmButton = { TextButton(onClick = onConfirm, shapes = ButtonDefaults.shapes()) { Text("Upload") } },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text("Cancel") } },
        modifier = modifier,
    )
}
