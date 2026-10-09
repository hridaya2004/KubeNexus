package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.common.util.ClipboardHelper
import dev.hridaya.kubenexus.presentation.home.ErrorDialogData
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Suppress("DEPRECATION")
@Composable
fun ErrorDialog(
    data: ErrorDialogData,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier.fillMaxWidth(0.95f),
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = data.title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = data.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Diagnostic Error Log:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp, max = 220.dp)
                        .background(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(10.dp)
                        .verticalScroll(verticalScrollState)
                        .horizontalScroll(horizontalScrollState),
                ) {
                    Text(
                        text = data.rawErrorTrace,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ClipboardHelper.copy(context, "Error details", data.rawErrorTrace, sensitive = true)
                    onCopy(data.rawErrorTrace)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                ),
                shapes = ButtonDefaults.shapes(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy Error",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text("Close")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Preview(showBackground = true)
@Composable
private fun ErrorDialogPreview() {
    KubeNexusTheme {
        ErrorDialog(
            data = ErrorDialogData(
                title = "Connection Failed",
                errorMessage = "Could not establish TLS handshake with Kubernetes API.",
                rawErrorTrace = "HTTP 503 Service Unavailable: connection refused at https://192.168.1.1:6443/version",
            ),
            onDismiss = {},
            onCopy = {},
        )
    }
}
