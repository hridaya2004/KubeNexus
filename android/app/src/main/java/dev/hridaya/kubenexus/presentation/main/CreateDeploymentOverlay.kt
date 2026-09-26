package dev.hridaya.kubenexus.presentation.main

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.presentation.deployments.CreateDeploymentRoute
import dev.hridaya.kubenexus.presentation.deployments.CreateDeploymentUiEffect
import dev.hridaya.kubenexus.presentation.deployments.CreateDeploymentViewModel
import dev.hridaya.kubenexus.presentation.home.HomeUiAction
import dev.hridaya.kubenexus.presentation.home.HomeViewModel

@Composable
internal fun CreateDeploymentOverlay(
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val createDeploymentViewModel: CreateDeploymentViewModel = hiltViewModel(
        creationCallback = { factory: CreateDeploymentViewModel.Factory ->
            factory.create(
                clusterId = homeUiState.activeCluster?.id,
                namespace = homeUiState.selectedNamespace
                    .takeIf { it.isNotBlank() && it != "All Namespaces" }
                    ?: "default",
                availableNamespaces = homeUiState.availableNamespaces,
            )
        },
    )
    LaunchedEffect(createDeploymentViewModel.effects) {
        createDeploymentViewModel.effects.collect { effect ->
            when (effect) {
                is CreateDeploymentUiEffect.Created -> {
                    onDismiss()
                    Toast.makeText(
                        context,
                        "Deployment '${effect.deploymentName}' created successfully",
                        Toast.LENGTH_SHORT,
                    ).show()
                    homeViewModel.onAction(HomeUiAction.RefreshWorkloads)
                }

                is CreateDeploymentUiEffect.NamespaceCreated -> {
                    Toast.makeText(context, "Namespace created", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    BackHandler { onDismiss() }
    CreateDeploymentRoute(
        viewModel = createDeploymentViewModel,
        onNavigateBack = onDismiss,
        modifier = modifier,
        targetClusterName = homeUiState.activeCluster?.name,
    )
}
