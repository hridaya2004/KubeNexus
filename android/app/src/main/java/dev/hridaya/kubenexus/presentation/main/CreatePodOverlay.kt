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
import dev.hridaya.kubenexus.presentation.pods.create.CreatePodRoute
import dev.hridaya.kubenexus.presentation.pods.create.CreatePodUiEffect
import dev.hridaya.kubenexus.presentation.pods.create.CreatePodViewModel

@Composable
internal fun CreatePodOverlay(
    homeViewModel: HomeViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val homeUiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val createPodViewModel: CreatePodViewModel = hiltViewModel(
        creationCallback = { factory: CreatePodViewModel.Factory ->
            factory.create(
                clusterId = homeUiState.activeCluster?.id,
                namespace = homeUiState.selectedNamespace
                    .takeIf { it.isNotBlank() && it != "All Namespaces" }
                    ?: "default",
                availableNamespaces = homeUiState.availableNamespaces,
            )
        },
    )
    LaunchedEffect(createPodViewModel.effects) {
        createPodViewModel.effects.collect { effect ->
            when (effect) {
                is CreatePodUiEffect.Created -> {
                    onDismiss()
                    Toast.makeText(
                        context,
                        "Pod '${effect.podName}' created successfully",
                        Toast.LENGTH_SHORT,
                    ).show()
                    homeViewModel.onAction(HomeUiAction.RefreshWorkloads)
                }

                is CreatePodUiEffect.NamespaceCreated -> {
                    Toast.makeText(context, "Namespace created", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    BackHandler { onDismiss() }
    CreatePodRoute(
        viewModel = createPodViewModel,
        onNavigateBack = onDismiss,
        modifier = modifier,
        targetClusterName = homeUiState.activeCluster?.name,
    )
}
