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
import dev.hridaya.kubenexus.presentation.home.HomeUiAction
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.services.create.CreateServiceRoute
import dev.hridaya.kubenexus.presentation.services.create.CreateServiceUiEffect
import dev.hridaya.kubenexus.presentation.services.create.CreateServiceViewModel

@Composable
internal fun CreateServiceOverlay(
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val createServiceViewModel: CreateServiceViewModel = hiltViewModel(
        creationCallback = { factory: CreateServiceViewModel.Factory ->
            factory.create(
                clusterId = homeUiState.activeCluster?.id,
                namespace = homeUiState.selectedNamespace
                    .takeIf { it.isNotBlank() && it != "All Namespaces" }
                    ?: "default",
                availableNamespaces = homeUiState.availableNamespaces,
            )
        },
    )
    LaunchedEffect(createServiceViewModel.effects) {
        createServiceViewModel.effects.collect { effect ->
            when (effect) {
                is CreateServiceUiEffect.Created -> {
                    onDismiss()
                    Toast.makeText(
                        context,
                        "Service '${effect.serviceName}' created successfully",
                        Toast.LENGTH_SHORT,
                    ).show()
                    homeViewModel.onAction(HomeUiAction.RefreshWorkloads)
                }

                is CreateServiceUiEffect.NamespaceCreated -> {
                    Toast.makeText(context, "Namespace created", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    BackHandler { onDismiss() }
    CreateServiceRoute(
        viewModel = createServiceViewModel,
        onNavigateBack = onDismiss,
        modifier = modifier,
        targetClusterName = homeUiState.activeCluster?.name,
    )
}
