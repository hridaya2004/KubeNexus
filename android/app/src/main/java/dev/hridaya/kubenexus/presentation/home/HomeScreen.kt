package dev.hridaya.kubenexus.presentation.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.presentation.common.components.LoadingContent
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.favourites.FavouritesUiAction
import dev.hridaya.kubenexus.presentation.favourites.FavouritesViewModel
import dev.hridaya.kubenexus.presentation.home.components.AddClusterBottomSheet
import dev.hridaya.kubenexus.presentation.home.components.ClusterPill
import dev.hridaya.kubenexus.presentation.home.components.ClusterSwitcherDrawer
import dev.hridaya.kubenexus.presentation.home.components.CreateFabMenu
import dev.hridaya.kubenexus.presentation.home.components.EmptyClustersView
import dev.hridaya.kubenexus.presentation.home.components.ErrorDialog
import dev.hridaya.kubenexus.presentation.home.components.HomeWorkloadsList
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.appBarColors

@Composable
fun ResourcesRoute(
    viewModel: HomeViewModel,
    favouritesViewModel: FavouritesViewModel,
    onNavigateToManageClusters: () -> Unit,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onCreateResource: (ResourcePresentation<*>) -> Unit,
    modifier: Modifier = Modifier,
    activePortForwardCount: Int = 0,
    onOpenPortForwardSessions: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val favourites by favouritesViewModel.favourites.collectAsStateWithLifecycle()
    val favouriteKeys = remember(favourites) { favourites.orEmpty().mapTo(HashSet()) { it.kind.type.key } }

    // Toasts are shown by MainScreen, the single collector of viewModel.effects.

    HomeScreen(
        title = "Resources",
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToManageClusters = onNavigateToManageClusters,
        creatableResources = ResourceCatalog.all,
        onCreateResource = onCreateResource,
        activePortForwardCount = activePortForwardCount,
        onOpenPortForwardSessions = onOpenPortForwardSessions,
        modifier = modifier,
    ) { listState ->
        HomeWorkloadsList(
            isRefreshing = uiState.isRefreshing,
            lastRefreshedAt = uiState.lastRefreshedAt,
            favouriteKeys = favouriteKeys,
            onOpenResources = onOpenResources,
            onToggleFavourite = { favouritesViewModel.onAction(FavouritesUiAction.ToggleFavourite(it)) },
            onNoopAction = { viewModel.onAction(HomeUiAction.TriggerNoopAction(it)) },
            listState = listState,
        )
    }
}

/** The frame shared by Favourites and Resources. [content] is shown once a cluster exists. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    title: String,
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateToManageClusters: () -> Unit,
    creatableResources: List<ResourcePresentation<*>>,
    onCreateResource: (ResourcePresentation<*>) -> Unit,
    modifier: Modifier = Modifier,
    activePortForwardCount: Int = 0,
    onOpenPortForwardSessions: () -> Unit = {},
    content: @Composable (LazyListState) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 || !listState.canScrollForward }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(text = title) },
                subtitle = {
                    ClusterPill(
                        activeCluster = uiState.activeCluster,
                        totalClusters = uiState.clusters.size,
                        connectionStatus = uiState.clusterConnectionStatus,
                        onClick = { onAction(HomeUiAction.OpenClusterDrawer) },
                    )
                },
                actions = {
                    if (activePortForwardCount > 0) {
                        IconButton(onClick = onOpenPortForwardSessions, shapes = IconButtonDefaults.shapes()) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(text = "$activePortForwardCount")
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.SettingsEthernet,
                                    contentDescription = "Active port forward sessions ($activePortForwardCount)",
                                )
                            }
                        }
                    }
                },
                colors = appBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },

        floatingActionButton = {
            if (uiState.clusters.isEmpty()) {
                MediumFloatingActionButton(onClick = { onAction(HomeUiAction.OpenAddClusterSheet) }) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add cluster",
                        modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                    )
                }
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
            showIndicatorWhileRefreshing = false,
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent(message = "Connecting to cluster")
                }

                uiState.clusters.isEmpty() -> {
                    EmptyClustersView(
                        onAddClusterClick = { onAction(HomeUiAction.OpenAddClusterSheet) },
                    )
                }

                else -> content(listState)
            }
        }

        if (uiState.showClusterDrawer) {
            ClusterSwitcherDrawer(
                clusters = uiState.clusters,
                activeCluster = uiState.activeCluster,
                onSelectCluster = { onAction(HomeUiAction.SelectClusterClicked(it)) },
                onManageClustersClick = onNavigateToManageClusters,
                onDismiss = { onAction(HomeUiAction.DismissClusterDrawer) },
            )
        }

        if (uiState.clusters.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
                contentAlignment = Alignment.BottomEnd,
            ) {
                CreateFabMenu(
                    expanded = uiState.showFabMenu,
                    visible = fabVisible,
                    onExpandedChange = { expanded ->
                        onAction(if (expanded) HomeUiAction.OpenFabMenu else HomeUiAction.DismissFabMenu)
                    },
                    creatableResources = creatableResources,
                    onAddResourceClick = onCreateResource,
                    onAddClusterClick = { onAction(HomeUiAction.OpenAddClusterSheet) },
                )
            }
        }

        if (uiState.showAddClusterSheet) {
            AddClusterBottomSheet(
                kubeconfigInput = uiState.kubeconfigInput,
                customClusterName = uiState.customClusterName,
                kubeconfigError = uiState.kubeconfigError,
                isConnecting = uiState.isConnecting,
                onKubeconfigChanged = { onAction(HomeUiAction.KubeconfigInputChanged(it)) },
                onClusterNameChanged = { onAction(HomeUiAction.ClusterNameChanged(it)) },
                onFileImported = { content, name ->
                    onAction(
                        HomeUiAction.FileImported(
                            content,
                            name,
                        ),
                    )
                },
                onFileImportFailed = { onAction(HomeUiAction.FileImportFailed(it)) },
                onConnectAndSave = { onAction(HomeUiAction.ConnectAndSaveSubmitted) },
                onDismiss = { onAction(HomeUiAction.DismissAddClusterSheet) },
            )
        }

        uiState.errorDialogData?.let { errorData ->
            ErrorDialog(
                data = errorData,
                onDismiss = { onAction(HomeUiAction.DismissErrorDialog) },
                onCopy = { text -> onAction(HomeUiAction.CopyErrorClicked(text)) },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenPreview() {
    KubeNexusTheme {
        HomeScreen(
            title = "Resources",
            uiState = HomeUiState(
                clusters = listOf(
                    Cluster(
                        id = "1",
                        name = "production-cluster",
                        serverUrl = "https://10.0.0.1:6443",
                        contextName = "prod",
                        userName = "admin",
                        namespace = "default",
                        rawKubeconfig = "",
                        isActive = true,
                    )
                ),
                activeCluster = Cluster(
                    id = "1",
                    name = "production-cluster",
                    serverUrl = "https://10.0.0.1:6443",
                    contextName = "prod",
                    userName = "admin",
                    namespace = "default",
                    rawKubeconfig = "",
                    isActive = true,
                ),
                clusterConnectionStatus = ClusterConnectionStatus.CONNECTED,
            ),
            onAction = {},
            onNavigateToManageClusters = {},
            creatableResources = ResourceCatalog.all,
            onCreateResource = {},
        ) { listState ->
            HomeWorkloadsList(
                isRefreshing = false,
                lastRefreshedAt = null,
                favouriteKeys = emptySet(),
                onOpenResources = {},
                onToggleFavourite = {},
                onNoopAction = {},
                listState = listState,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    KubeNexusTheme {
        HomeScreen(
            title = "Favourites",
            uiState = HomeUiState(
                clusters = emptyList(),
                activeCluster = null,
            ),
            onAction = {},
            onNavigateToManageClusters = {},
            creatableResources = ResourceCatalog.all,
            onCreateResource = {},
        ) { listState ->
            HomeWorkloadsList(
                isRefreshing = false,
                lastRefreshedAt = null,
                favouriteKeys = emptySet(),
                onOpenResources = {},
                onToggleFavourite = {},
                onNoopAction = {},
                listState = listState,
            )
        }
    }
}
