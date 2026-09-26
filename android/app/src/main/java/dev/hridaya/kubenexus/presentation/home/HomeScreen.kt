package dev.hridaya.kubenexus.presentation.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.presentation.common.components.LoadingContent
import dev.hridaya.kubenexus.presentation.home.components.AddClusterBottomSheet
import dev.hridaya.kubenexus.presentation.home.components.ClusterPill
import dev.hridaya.kubenexus.presentation.home.components.ClusterSwitcherDrawer
import dev.hridaya.kubenexus.presentation.home.components.EmptyClustersView
import dev.hridaya.kubenexus.presentation.home.components.ErrorDialog
import dev.hridaya.kubenexus.presentation.home.components.FabActionBottomSheet
import dev.hridaya.kubenexus.presentation.home.components.HomeWorkloadsList
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onNavigateToManageClusters: () -> Unit,
    onNavigateToPods: () -> Unit,
    onNavigateToDeployments: () -> Unit,
    onNavigateToServices: () -> Unit,
    onNavigateToCreatePod: () -> Unit,
    onNavigateToCreateDeployment: () -> Unit,
    onNavigateToCreateService: () -> Unit,
    modifier: Modifier = Modifier,
    activePortForwardCount: Int = 0,
    onOpenPortForwardSessions: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Toasts are shown by MainScreen, the single collector of viewModel.effects.

    HomeScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateToManageClusters = onNavigateToManageClusters,
        onNavigateToPods = onNavigateToPods,
        onNavigateToDeployments = onNavigateToDeployments,
        onNavigateToServices = onNavigateToServices,
        onNavigateToCreatePod = onNavigateToCreatePod,
        onNavigateToCreateDeployment = onNavigateToCreateDeployment,
        onNavigateToCreateService = onNavigateToCreateService,
        activePortForwardCount = activePortForwardCount,
        onOpenPortForwardSessions = onOpenPortForwardSessions,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateToManageClusters: () -> Unit,
    onNavigateToPods: () -> Unit,
    onNavigateToDeployments: () -> Unit,
    onNavigateToServices: () -> Unit,
    onNavigateToCreatePod: () -> Unit,
    onNavigateToCreateDeployment: () -> Unit,
    onNavigateToCreateService: () -> Unit,
    modifier: Modifier = Modifier,
    activePortForwardCount: Int = 0,
    onOpenPortForwardSessions: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "KubeNexus",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        ClusterPill(
                            activeCluster = uiState.activeCluster,
                            totalClusters = uiState.clusters.size,
                            connectionStatus = uiState.clusterConnectionStatus,
                            onClick = { onAction(HomeUiAction.OpenClusterDrawer) },
                        )
                    }
                },
                actions = {
                    if (activePortForwardCount > 0) {
                        IconButton(onClick = onOpenPortForwardSessions) {
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
            )
        },

        floatingActionButton = {
            FloatingActionButton(
                onClick = { onAction(HomeUiAction.OpenFabActionSheet) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Actions",
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeUiAction.RefreshWorkloads) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
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

                else -> {
                    HomeWorkloadsList(
                        isRefreshing = uiState.isRefreshing,
                        lastRefreshedAt = uiState.lastRefreshedAt,
                        onNavigateToPods = onNavigateToPods,
                        onNavigateToDeployments = onNavigateToDeployments,
                        onNavigateToServices = onNavigateToServices,
                        onNoopAction = { onAction(HomeUiAction.TriggerNoopAction(it)) },
                    )
                }
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

        if (uiState.showFabActionSheet) {
            FabActionBottomSheet(
                hasClustersConfigured = uiState.clusters.isNotEmpty(),
                onAddClusterClick = { onAction(HomeUiAction.OpenAddClusterSheet) },
                onAddPodClick = onNavigateToCreatePod,
                onAddDeploymentClick = onNavigateToCreateDeployment,
                onAddServiceClick = onNavigateToCreateService,
                onDismiss = { onAction(HomeUiAction.DismissFabActionSheet) },
            )
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
            onNavigateToPods = {},
            onNavigateToDeployments = {},
            onNavigateToServices = {},
            onNavigateToCreatePod = {},
            onNavigateToCreateDeployment = {},
            onNavigateToCreateService = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenEmptyPreview() {
    KubeNexusTheme {
        HomeScreen(
            uiState = HomeUiState(
                clusters = emptyList(),
                activeCluster = null,
            ),
            onAction = {},
            onNavigateToManageClusters = {},
            onNavigateToPods = {},
            onNavigateToDeployments = {},
            onNavigateToServices = {},
            onNavigateToCreatePod = {},
            onNavigateToCreateDeployment = {},
            onNavigateToCreateService = {},
        )
    }
}
