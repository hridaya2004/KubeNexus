package dev.hridaya.kubenexus.presentation.deployments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.presentation.common.components.CreateNamespaceDialog
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun CreateDeploymentRoute(
    viewModel: CreateDeploymentViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetClusterName: String? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    CreateDeploymentScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        targetClusterName = targetClusterName,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateDeploymentScreen(
    uiState: CreateDeploymentUiState,
    onAction: (CreateDeploymentUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    targetClusterName: String? = null,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Create Deployment",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        // Always visible, so the cluster the object lands on is never a guess.
                        if (targetClusterName != null) {
                            Text(
                                text = "on $targetClusterName",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (uiState.step) {
                CreateDeploymentStep.FORM -> CreateDeploymentFormFields(
                    uiState = uiState,
                    onAction = onAction,
                )

                CreateDeploymentStep.REVIEW -> CreateDeploymentReviewFields(
                    uiState = uiState,
                    onAction = onAction,
                )
            }
        }
    }

    if (uiState.showCreateNamespaceDialog) {
        CreateNamespaceDialog(
            namespaceName = uiState.newNamespaceName,
            errorMessage = uiState.newNamespaceError,
            isCreating = uiState.isCreatingNamespace,
            onNameChanged = { onAction(CreateDeploymentUiAction.NewNamespaceNameChanged(it)) },
            onConfirm = { onAction(CreateDeploymentUiAction.CreateNamespaceSubmitted) },
            onDismiss = { onAction(CreateDeploymentUiAction.DismissCreateNamespaceClicked) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateDeploymentFormPreview() {
    KubeNexusTheme {
        CreateDeploymentScreen(
            uiState = CreateDeploymentUiState(
                name = "my-web-app",
                namespace = "default",
                image = "nginx:1.27",
                availableNamespaces = listOf("default", "kube-system", "monitoring"),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CreateDeploymentReviewPreview() {
    KubeNexusTheme {
        CreateDeploymentScreen(
            uiState = CreateDeploymentUiState(
                name = "my-web-app",
                namespace = "default",
                image = "nginx:1.27",
                step = CreateDeploymentStep.REVIEW,
                generatedYaml = DeploymentYamlPreviewSample,
                reviewedYaml = DeploymentYamlPreviewSample,
                availableNamespaces = listOf("default", "kube-system"),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}

private val DeploymentYamlPreviewSample = """
    apiVersion: apps/v1
    kind: Deployment
    metadata:
      name: my-web-app
      namespace: default
""".trimIndent()
