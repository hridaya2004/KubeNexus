package dev.hridaya.kubenexus.presentation.pods

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.common.components.segmentedShape
import dev.hridaya.kubenexus.presentation.home.HomeUiAction
import dev.hridaya.kubenexus.presentation.home.HomeUiState
import dev.hridaya.kubenexus.presentation.home.components.PodCard
import dev.hridaya.kubenexus.presentation.pods.components.DeleteNamespaceDialog
import dev.hridaya.kubenexus.presentation.pods.components.PodsEmptyState
import dev.hridaya.kubenexus.presentation.pods.components.PodsNamespaceFilterBar
import dev.hridaya.kubenexus.presentation.pods.components.PodsNoMatchState
import dev.hridaya.kubenexus.presentation.pods.components.PodsSearchBar
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlin.time.Duration.Companion.days

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodsScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToPodDetail: (Pod) -> Unit = {},
    onNavigateToCreatePod: () -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredPods = remember(uiState.pods, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.pods
        } else {
            val query = searchQuery.trim()
            uiState.pods.filter { pod ->
                pod.name.contains(query, ignoreCase = true) ||
                        (pod.ip?.contains(query, ignoreCase = true) == true) ||
                        (pod.node?.contains(query, ignoreCase = true) == true) ||
                        pod.status.title.contains(query, ignoreCase = true) ||
                        pod.namespace.contains(query, ignoreCase = true)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = "Pods",
                    )
                },
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
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = onNavigateToCreatePod,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Pod",
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                )
            }
        },
    ) { innerPadding ->
        RefreshableBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeUiAction.RefreshWorkloads) },
            appBarScrollConnection = scrollBehavior.nestedScrollConnection,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isDeletingNamespace) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                PodsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )

                PodsNamespaceFilterBar(
                    availableNamespaces = uiState.availableNamespaces,
                    selectedNamespace = uiState.selectedNamespace,
                    lastRefreshedAt = uiState.lastRefreshedAt,
                    onSelectNamespace = { onAction(HomeUiAction.SelectNamespace(it)) },
                )

                when {
                    uiState.pods.isEmpty() -> {
                        PodsEmptyState(
                            isRefreshing = uiState.isRefreshing,
                            selectedNamespace = uiState.selectedNamespace,
                        )
                    }

                    filteredPods.isEmpty() -> {
                        PodsNoMatchState(
                            searchQuery = searchQuery,
                            selectedNamespace = uiState.selectedNamespace,
                            onClearSearch = { searchQuery = "" },
                        )
                    }

                    else -> {
                        val isAllNamespaces = remember(uiState.selectedNamespace) {
                            uiState.selectedNamespace.equals("All Namespaces", ignoreCase = true) ||
                                uiState.selectedNamespace.isBlank()
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 32.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filteredPods, key = { _, pod -> pod.id }) { index, pod ->
                                PodCard(
                                    shape = segmentedShape(index, filteredPods.size),
                                    pod = pod,
                                    showNamespace = isAllNamespaces,
                                    onClick = { onNavigateToPodDetail(pod) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.namespaceToDelete?.let { ns ->
        DeleteNamespaceDialog(
            namespace = ns,
            clusterName = uiState.activeCluster?.name,
            onDismiss = { onAction(HomeUiAction.DismissDeleteNamespace) },
            onConfirmDelete = { onAction(HomeUiAction.ConfirmDeleteNamespace(it)) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PodsScreenPreview() {
    KubeNexusTheme {
        PodsScreen(
            uiState = HomeUiState(
                activeCluster = Cluster(
                    id = "1",
                    name = "production-cluster",
                    serverUrl = "https://10.0.0.1:6443",
                    contextName = "prod",
                    userName = "admin",
                    namespace = "default",
                    rawKubeconfig = "",
                ),
                availableNamespaces = listOf("default", "kube-system", "monitoring"),
                selectedNamespace = "default",
                pods = listOf(
                    Pod(
                        id = "1",
                        name = "coredns-559bb7b579-24r9k",
                        namespace = "kube-system",
                        status = PodStatus.RUNNING,
                        readyContainers = "1/1",
                        restarts = 0,
                        creationTimestampMillis = System.currentTimeMillis() -
                                12.days.inWholeMilliseconds,
                        ip = "10.244.0.2",
                    ),
                    Pod(
                        id = "2",
                        name = "nginx-deployment-78f56c879d-gqw87",
                        namespace = "default",
                        status = PodStatus.RUNNING,
                        readyContainers = "1/1",
                        restarts = 1,
                        creationTimestampMillis = System.currentTimeMillis() -
                                2.days.inWholeMilliseconds,
                        ip = "10.244.0.15",
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
            onNavigateToPodDetail = {},
        )
    }
}
