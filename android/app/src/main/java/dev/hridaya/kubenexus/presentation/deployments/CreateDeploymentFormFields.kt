package dev.hridaya.kubenexus.presentation.deployments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.DeploymentDraft
import dev.hridaya.kubenexus.presentation.common.components.ErrorBanner
import dev.hridaya.kubenexus.presentation.common.components.NamespacePicker
import dev.hridaya.kubenexus.presentation.common.components.StepHeader

@Composable
internal fun CreateDeploymentFormFields(
    uiState: CreateDeploymentUiState,
    onAction: (CreateDeploymentUiAction) -> Unit,
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
                onDismiss = { onAction(CreateDeploymentUiAction.DismissError) },
            )
        }

        val nameError = uiState.fieldErrors["name"]
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onAction(CreateDeploymentUiAction.NameChanged(it)) },
            label = { Text(text = "Name") },
            placeholder = { Text(text = "my-web-app") },
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
            onSelectNamespace = { onAction(CreateDeploymentUiAction.NamespaceSelected(it)) },
            onCreateNamespaceClick = { onAction(CreateDeploymentUiAction.CreateNamespaceClicked) },
        )

        val imageError = uiState.fieldErrors["image"]
        OutlinedTextField(
            value = uiState.image,
            onValueChange = { onAction(CreateDeploymentUiAction.ImageChanged(it)) },
            label = { Text(text = "Image") },
            placeholder = { Text(text = "nginx:1.27") },
            isError = imageError != null,
            supportingText = imageError?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val replicasError = uiState.fieldErrors["replicas"]
            OutlinedTextField(
                value = uiState.replicas,
                onValueChange = { onAction(CreateDeploymentUiAction.ReplicasChanged(it)) },
                label = { Text(text = "Replicas") },
                isError = replicasError != null,
                supportingText = replicasError?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            val portError = uiState.fieldErrors["containerPort"]
            OutlinedTextField(
                value = uiState.containerPort,
                onValueChange = { onAction(CreateDeploymentUiAction.ContainerPortChanged(it)) },
                label = { Text(text = "Container port") },
                isError = portError != null,
                supportingText = portError?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = "Expose with Service",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            DeploymentDraft.SERVICE_TYPES.forEach { type ->
                FilterChip(
                    selected = type == uiState.serviceType,
                    onClick = { onAction(CreateDeploymentUiAction.ServiceTypeSelected(type)) },
                    label = { Text(type) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
        }

        if (uiState.serviceType != DeploymentDraft.SERVICE_TYPE_NONE) {
            val servicePortError = uiState.fieldErrors["servicePort"]
            OutlinedTextField(
                value = uiState.servicePort,
                onValueChange = { onAction(CreateDeploymentUiAction.ServicePortChanged(it)) },
                label = { Text(text = "Service port") },
                isError = servicePortError != null,
                supportingText = servicePortError?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { onAction(CreateDeploymentUiAction.PreviewSubmitted) },
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
