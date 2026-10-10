package dev.hridaya.kubenexus.presentation.deployments.detail

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.deployments.detail.components.DeleteDeploymentDialog
import dev.hridaya.kubenexus.presentation.deployments.detail.components.DeploymentDetailsSection
import dev.hridaya.kubenexus.presentation.deployments.detail.components.DeploymentImagesCard
import dev.hridaya.kubenexus.presentation.deployments.detail.components.DeploymentPodsSection
import dev.hridaya.kubenexus.presentation.deployments.detail.components.DeploymentStatusCard
import dev.hridaya.kubenexus.presentation.deployments.detail.components.RestartDeploymentDialog
import dev.hridaya.kubenexus.presentation.deployments.detail.components.ScaleDeploymentDialog
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DeploymentDetailRoute(
    viewModel: DeploymentDetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPodDetail: (podName: String, namespace: String) -> Unit = { _, _ -> },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel.effects) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DeploymentDetailUiEffect.NavigateBack -> onNavigateBack()
                is DeploymentDetailUiEffect.ShowSnackbar -> {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    DeploymentDetailScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onPodClick = { pod -> onNavigateToPodDetail(pod.name, pod.namespace) },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploymentDetailScreen(
    uiState: DeploymentDetailUiState,
    onAction: (DeploymentDetailUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onPodClick: (Pod) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.deploymentName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                subtitle = { Text(text = uiState.namespace) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            }

            uiState.errorMessage != null && uiState.deployment == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = uiState.errorMessage.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onAction(DeploymentDetailUiAction.Refresh) },
                            modifier = Modifier
                                .height(48.dp)
                                .padding(horizontal = 16.dp),
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Text(text = "Retry", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            else -> uiState.deployment?.let { deployment ->
                RefreshableBox(
                    isRefreshing = uiState.isRefreshing,
                    onRefresh = { onAction(DeploymentDetailUiAction.Refresh) },
                    appBarScrollConnection = scrollBehavior.nestedScrollConnection,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (uiState.isMutating) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }

                        DeploymentStatusCard(
                            deployment = deployment,
                            details = uiState.details,
                            lastRefreshedAt = uiState.lastRefreshedAt,
                            modifier = Modifier.fillMaxWidth(),
                        )

                    ButtonGroup(
                        overflowIndicator = {},
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                FilledTonalButton(
                                    onClick = { onAction(DeploymentDetailUiAction.OpenScaleDialog) },
                                    enabled = !uiState.isMutating,
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(interactionSource),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Scale")
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text("Scale") },
                                    onClick = {
                                        state.dismiss()
                                        onAction(DeploymentDetailUiAction.OpenScaleDialog)
                                    },
                                )
                            },
                        )
                        customItem(
                            buttonGroupContent = {
                                val interactionSource = remember { MutableInteractionSource() }
                                FilledTonalButton(
                                    onClick = { onAction(DeploymentDetailUiAction.OpenRestartDialog) },
                                    enabled = !uiState.isMutating,
                                    interactionSource = interactionSource,
                                    modifier = Modifier
                                        .weight(1f)
                                        .animateWidth(interactionSource),
                                    shapes = ButtonDefaults.shapes(),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.RestartAlt,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.size(6.dp))
                                    Text("Restart")
                                }
                            },
                            menuContent = { state ->
                                DropdownMenuItem(
                                    text = { Text("Restart") },
                                    onClick = {
                                        state.dismiss()
                                        onAction(DeploymentDetailUiAction.OpenRestartDialog)
                                    },
                                )
                            },
                        )
                    }

                    DeploymentImagesCard(
                        images = deployment.images,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    DeploymentPodsSection(
                        pods = uiState.associatedPods,
                        isLoading = uiState.isPodsLoading,
                        errorMessage = uiState.podsErrorMessage,
                        onPodClick = onPodClick,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Describe sections load independently; the overview above
                    // keeps rendering while they stream in or fail.
                    DeploymentDetailsSection(
                        isDetailsLoading = uiState.isDetailsLoading,
                        details = uiState.details,
                        detailsErrorMessage = uiState.detailsErrorMessage,
                        onRetry = { onAction(DeploymentDetailUiAction.Refresh) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }

        if (uiState.showScaleDialog) {
            ScaleDeploymentDialog(
                deploymentName = uiState.deploymentName,
                currentReplicas = uiState.scaleInput,
                onReplicasChanged = { onAction(DeploymentDetailUiAction.ScaleInputChanged(it)) },
                onConfirm = { onAction(DeploymentDetailUiAction.ConfirmScale) },
                onDismiss = { onAction(DeploymentDetailUiAction.DismissScaleDialog) },
            )
        }

        if (uiState.showRestartDialog) {
            RestartDeploymentDialog(
                deploymentName = uiState.deploymentName,
                onConfirm = { onAction(DeploymentDetailUiAction.ConfirmRestart) },
                onDismiss = { onAction(DeploymentDetailUiAction.DismissRestartDialog) },
            )
        }

        if (uiState.showDeleteDialog) {
            DeleteDeploymentDialog(
                deploymentName = uiState.deploymentName,
                namespace = uiState.namespace,
                onConfirm = { onAction(DeploymentDetailUiAction.ConfirmDelete) },
                onDismiss = { onAction(DeploymentDetailUiAction.DismissDeleteDialog) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeploymentDetailScreenPreview() {
    KubeNexusTheme {
        DeploymentDetailScreen(
            uiState = DeploymentDetailUiState(
                deploymentName = "web-frontend",
                namespace = "default",
                isLoading = false,
                deployment = DeploymentSummary(
                    id = "default/web-frontend",
                    name = "web-frontend",
                    namespace = "default",
                    desiredReplicas = 3,
                    readyReplicas = 2,
                    availableReplicas = 2,
                    images = listOf("nginx:1.27", "registry.example.com/sidecar:v0.9"),
                    creationTimestampMillis = System.currentTimeMillis(),
                ),
                isDetailsLoading = false,
                detailsErrorMessage = "Couldn't load the describe details for this deployment.",
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
