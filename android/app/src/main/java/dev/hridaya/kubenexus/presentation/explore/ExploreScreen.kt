package dev.hridaya.kubenexus.presentation.explore

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.presentation.common.share.ClipboardHelper
import dev.hridaya.kubenexus.presentation.explore.components.ExploreExplainDetailView
import dev.hridaya.kubenexus.presentation.explore.components.ExploreListView
import dev.hridaya.kubenexus.presentation.explore.components.ExploreSearchScreen
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun ExploreRoute(
    viewModel: ExploreViewModel,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is ExploreUiEvent.ShowMessage -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }

                is ExploreUiEvent.CopyToClipboard -> {
                    ClipboardHelper.copy(context, event.label, event.text, sensitive = false)
                }
            }
        }
    }

    if (uiState.selectedResource != null) {
        BackHandler {
            viewModel.onAction(ExploreUiAction.DismissExplain)
        }
    } else if (uiState.isSearchActive) {
        BackHandler {
            viewModel.onAction(ExploreUiAction.CloseSearch)
        }
    }

    ExploreScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
fun ExploreScreen(
    uiState: ExploreUiState,
    onAction: (ExploreUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        uiState.selectedResource != null -> {
            ExploreExplainDetailView(
                resource = uiState.selectedResource,
                uiState = uiState,
                onAction = onAction,
                onNavigateBack = { onAction(ExploreUiAction.DismissExplain) },
                modifier = modifier,
            )
        }

        uiState.isSearchActive -> {
            ExploreSearchScreen(
                uiState = uiState,
                onAction = onAction,
                modifier = modifier,
            )
        }

        else -> {
            ExploreListView(
                uiState = uiState,
                onAction = onAction,
                modifier = modifier,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ExploreScreenPreview() {
    KubeNexusTheme {
        ExploreScreen(
            uiState = ExploreUiState(
                activeCluster = Cluster(
                    id = "1",
                    name = "production-cluster",
                    serverUrl = "https://10.0.0.1:6443",
                    contextName = "prod",
                    userName = "admin",
                    namespace = "default",
                ),
                resources = listOf(
                    APIResource(
                        name = "pods",
                        singularName = "pod",
                        namespaced = true,
                        kind = "Pod",
                        verbs = listOf("get", "list", "watch", "create", "delete"),
                        group = "",
                        version = "v1",
                    ),
                    APIResource(
                        name = "deployments",
                        singularName = "deployment",
                        namespaced = true,
                        kind = "Deployment",
                        verbs = listOf("get", "list", "watch", "create", "delete", "update"),
                        group = "apps",
                        version = "v1",
                    ),
                ),
                filteredResources = listOf(
                    APIResource(
                        name = "pods",
                        singularName = "pod",
                        namespaced = true,
                        kind = "Pod",
                        verbs = listOf("get", "list", "watch", "create", "delete"),
                        group = "",
                        version = "v1",
                    ),
                ),
            ),
            onAction = {},
        )
    }
}
