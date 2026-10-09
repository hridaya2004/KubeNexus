package dev.hridaya.kubenexus.presentation.common.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Namespace chip picker shared by the create-deployment and create-pod flows.
 */
@Composable
fun NamespacePicker(
    selectedNamespace: String,
    namespaceError: String?,
    availableNamespaces: List<String>,
    onSelectNamespace: (String) -> Unit,
    onCreateNamespaceClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The workload must land in one concrete namespace; keep the current value
    // selectable even if it is not in the fetched list yet.
    val options = remember(availableNamespaces, selectedNamespace) {
        (availableNamespaces + selectedNamespace)
            .filter { it.isNotBlank() && it != "All Namespaces" }
            .distinct()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Namespace",
            style = MaterialTheme.typography.bodySmall,
            color = if (namespaceError != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(options) { namespace ->
                FilterChip(
                    selected = namespace == selectedNamespace,
                    onClick = { onSelectNamespace(namespace) },
                    label = { Text(namespace) },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            }
            item {
                FilterChip(
                    selected = false,
                    onClick = onCreateNamespaceClick,
                    label = { Text("Create new") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.primary,
                        iconColor = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }
        namespaceError?.let { error ->
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/**
 * Create-namespace dialog shared by the create-deployment and create-pod flows.
 */
@Composable
fun CreateNamespaceDialog(
    namespaceName: String,
    errorMessage: String?,
    isCreating: Boolean,
    onNameChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create namespace",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        },
        text = {
            OutlinedTextField(
                value = namespaceName,
                onValueChange = onNameChanged,
                label = { Text(text = "Namespace name") },
                placeholder = { Text(text = "my-team") },
                isError = errorMessage != null,
                supportingText = errorMessage?.let { error ->
                    { Text(text = error, color = MaterialTheme.colorScheme.error) }
                },
                singleLine = true,
                enabled = !isCreating,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isCreating && namespaceName.isNotBlank(),
                modifier = Modifier.height(48.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Creating",
                        style = MaterialTheme.typography.labelLarge,
                    )
                } else {
                    Text(
                        text = "Create",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isCreating,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = "Cancel")
            }
        },
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
