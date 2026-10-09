package dev.hridaya.kubenexus.presentation.pods.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.presentation.common.components.ErrorBanner
import dev.hridaya.kubenexus.presentation.common.components.NamespacePicker
import dev.hridaya.kubenexus.presentation.common.components.StepHeader

@Composable
internal fun CreatePodFormFields(
    uiState: CreatePodUiState,
    onAction: (CreatePodUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHeader(stepLabel = "Step 1 of 2", description = "Configure the workload")

        uiState.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { onAction(CreatePodUiAction.DismissError) },
            )
        }

        val nameError = uiState.fieldErrors["name"]
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onAction(CreatePodUiAction.NameChanged(it)) },
            label = { Text(text = "Name") },
            placeholder = { Text(text = "my-pod") },
            isError = nameError != null,
            supportingText = nameError?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        NamespacePicker(
            selectedNamespace = uiState.namespace,
            namespaceError = uiState.fieldErrors["namespace"],
            availableNamespaces = uiState.availableNamespaces,
            onSelectNamespace = { onAction(CreatePodUiAction.NamespaceSelected(it)) },
            onCreateNamespaceClick = { onAction(CreatePodUiAction.CreateNamespaceClicked) },
        )

        val imageError = uiState.fieldErrors["image"]
        OutlinedTextField(
            value = uiState.image,
            onValueChange = { onAction(CreatePodUiAction.ImageChanged(it)) },
            label = { Text(text = "Image") },
            placeholder = { Text(text = "nginx:1.27") },
            isError = imageError != null,
            supportingText = imageError?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val portError = uiState.fieldErrors["containerPort"]
        OutlinedTextField(
            value = uiState.containerPort,
            onValueChange = { onAction(CreatePodUiAction.ContainerPortChanged(it)) },
            label = { Text(text = "Container port (optional)") },
            isError = portError != null,
            supportingText = {
                Text(
                    text = portError ?: "Leave empty to omit container ports",
                    color = if (portError != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = { onAction(CreatePodUiAction.PreviewSubmitted) },
            enabled = uiState.fieldErrors.isEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = "Preview manifest",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
