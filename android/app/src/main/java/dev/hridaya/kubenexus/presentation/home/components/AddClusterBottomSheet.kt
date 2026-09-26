package dev.hridaya.kubenexus.presentation.home.components

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddClusterBottomSheet(
    kubeconfigInput: String,
    customClusterName: String,
    kubeconfigError: String?,
    isConnecting: Boolean,
    onKubeconfigChanged: (String) -> Unit,
    onClusterNameChanged: (String) -> Unit,
    onFileImported: (content: String, fileName: String?) -> Unit,
    onFileImportFailed: (message: String) -> Unit,
    onConnectAndSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            // The provider may be slow (a cloud document) and the file arbitrarily large, so it is
            // read off the main thread and never beyond the size a kubeconfig can have.
            scope.launch {
                when (val file = withContext(Dispatchers.IO) { readKubeconfigFile(context, uri) }) {
                    is KubeconfigFile.Loaded -> onFileImported(file.content, file.name)
                    is KubeconfigFile.Failed -> onFileImportFailed(file.message)
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!isConnecting) {
                scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
            }
        },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .verticalScroll(scrollState),
        ) {
            Text(
                text = "Add Kubernetes Cluster",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Paste your kubeconfig YAML or import from a file to connect.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                enabled = !isConnecting,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Outlined.FileUpload,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Import Kubeconfig File (.yaml, .yml)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = customClusterName,
                onValueChange = onClusterNameChanged,
                label = { Text("Cluster Alias / Name (Optional)") },
                placeholder = { Text("e.g. Production US-East") },
                singleLine = true,
                enabled = !isConnecting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = kubeconfigInput,
                onValueChange = onKubeconfigChanged,
                label = { Text("Kubeconfig (YAML)") },
                placeholder = {
                    Text(
                        "apiVersion: v1\nkind: Config\nclusters:\n- cluster:\n    server: https://...\n...",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                    )
                },
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                isError = kubeconfigError != null,
                supportingText = kubeconfigError?.let {
                    {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                // Autocorrect and auto-capitalisation would rewrite YAML keys and tokens as they are typed.
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                minLines = 7,
                maxLines = 14,
                enabled = !isConnecting,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onConnectAndSave,
                enabled = !isConnecting && kubeconfigInput.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                if (isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Validating & Connecting")
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Link,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connect & Save",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private sealed interface KubeconfigFile {
    data class Loaded(val content: String, val name: String?) : KubeconfigFile
    data class Failed(val message: String) : KubeconfigFile
}

private fun readKubeconfigFile(context: Context, uri: Uri): KubeconfigFile = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
        input.readNBytes(Cluster.MAX_KUBECONFIG_BYTES + 1)
    }
    when {
        bytes == null -> KubeconfigFile.Failed("Couldn't open that file.")
        bytes.size > Cluster.MAX_KUBECONFIG_BYTES ->
            KubeconfigFile.Failed("That file is larger than 1 MB, which is more than any kubeconfig needs. Check that it is the right file.")
        else -> KubeconfigFile.Loaded(bytes.decodeToString(), getFileName(context, uri))
    }
} catch (e: Exception) {
    if (e is CancellationException) throw e
    KubeconfigFile.Failed("Couldn't read that file: ${e.message ?: e.javaClass.simpleName}")
}

private fun getFileName(context: Context, uri: Uri): String? {
    var result: String? = null
    if (uri.scheme == "content") {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    result = cursor.getString(index)
                }
            }
        }
    }
    return result ?: uri.lastPathSegment
}

@Preview(showBackground = true)
@Composable
private fun AddClusterBottomSheetPreview() {
    KubeNexusTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(24.dp),
        ) {
            Column {
                Text(
                    text = "Add Kubernetes Cluster",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = "Production US-East",
                    onValueChange = {},
                    label = { Text("Cluster Alias / Name (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = "apiVersion: v1\nkind: Config\nclusters: []",
                    onValueChange = {},
                    label = { Text("Kubeconfig (YAML)") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                ) {
                    Text("Connect & Save")
                }
            }
        }
    }
}
