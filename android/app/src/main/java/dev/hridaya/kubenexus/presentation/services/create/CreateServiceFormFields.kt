package dev.hridaya.kubenexus.presentation.services.create

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
import dev.hridaya.kubenexus.domain.model.ServiceDraft
import dev.hridaya.kubenexus.presentation.common.components.ErrorBanner
import dev.hridaya.kubenexus.presentation.common.components.NamespacePicker
import dev.hridaya.kubenexus.presentation.common.components.StepHeader

@Composable
internal fun CreateServiceFormFields(
    uiState: CreateServiceUiState,
    onAction: (CreateServiceUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        StepHeader(stepLabel = "Step 1 of 2", description = "Configure the service")

        uiState.errorMessage?.let { message ->
            ErrorBanner(
                message = message,
                onDismiss = { onAction(CreateServiceUiAction.DismissError) },
            )
        }

        val nameError = uiState.fieldErrors["name"]
        OutlinedTextField(
            value = uiState.name,
            onValueChange = { onAction(CreateServiceUiAction.NameChanged(it)) },
            label = { Text(text = "Name") },
            placeholder = { Text(text = "my-service") },
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
            onSelectNamespace = { onAction(CreateServiceUiAction.NamespaceSelected(it)) },
            onCreateNamespaceClick = { onAction(CreateServiceUiAction.CreateNamespaceClicked) },
        )

        val selectorAppError = uiState.fieldErrors["selectorApp"]
        OutlinedTextField(
            value = uiState.selectorApp,
            onValueChange = { onAction(CreateServiceUiAction.SelectorAppChanged(it)) },
            label = { Text(text = "Selector (app label)") },
            placeholder = { Text(text = "my-web-app") },
            isError = selectorAppError != null,
            supportingText = selectorAppError?.let { error ->
                { Text(text = error, color = MaterialTheme.colorScheme.error) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Type",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            ServiceDraft.TYPES.forEach { type ->
                FilterChip(
                    selected = type == uiState.serviceType,
                    onClick = { onAction(CreateServiceUiAction.ServiceTypeSelected(type)) },
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

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val portError = uiState.fieldErrors["port"]
            OutlinedTextField(
                value = uiState.port,
                onValueChange = { onAction(CreateServiceUiAction.PortChanged(it)) },
                label = { Text(text = "Port") },
                isError = portError != null,
                supportingText = portError?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )

            val targetPortError = uiState.fieldErrors["targetPort"]
            OutlinedTextField(
                value = uiState.targetPort,
                onValueChange = { onAction(CreateServiceUiAction.TargetPortChanged(it)) },
                label = { Text(text = "Target port") },
                isError = targetPortError != null,
                supportingText = targetPortError?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        Button(
            onClick = { onAction(CreateServiceUiAction.PreviewSubmitted) },
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
