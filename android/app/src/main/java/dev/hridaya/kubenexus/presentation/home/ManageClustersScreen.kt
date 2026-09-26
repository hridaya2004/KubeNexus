package dev.hridaya.kubenexus.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.presentation.home.components.AddClusterBottomSheet
import dev.hridaya.kubenexus.presentation.home.components.DeleteClusterDialog
import dev.hridaya.kubenexus.presentation.home.components.EditClusterDialog
import dev.hridaya.kubenexus.presentation.home.components.EmptyClustersView
import dev.hridaya.kubenexus.presentation.home.components.ErrorDialog
import dev.hridaya.kubenexus.presentation.home.components.ManageClusterCard
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageClustersScreen(
    uiState: HomeUiState,
    onAction: (HomeUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Manage Clusters",
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAction(HomeUiAction.OpenAddClusterSheet) },
                icon = { Icon(imageVector = Icons.Outlined.Add, contentDescription = null) },
                text = { Text("Add Cluster") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        },
    ) { innerPadding ->
        if (uiState.clusters.isEmpty()) {
            EmptyClustersView(
                onAddClusterClick = { onAction(HomeUiAction.OpenAddClusterSheet) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(uiState.clusters, key = { it.id }) { cluster ->
                    ManageClusterCard(
                        cluster = cluster,
                        testStatus = uiState.clusterTestStatuses[cluster.id]
                            ?: ClusterTestStatus.IDLE,
                        onSelectActive = { onAction(HomeUiAction.SelectClusterClicked(cluster.id)) },
                        onTestConnection = {
                            onAction(
                                HomeUiAction.TestClusterConnectionClicked(
                                    cluster.id,
                                ),
                            )
                        },
                        onEdit = { onAction(HomeUiAction.RequestEditCluster(cluster)) },
                        onDelete = { onAction(HomeUiAction.RequestDeleteCluster(cluster)) },
                    )
                }
            }
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

    uiState.editingCluster?.let { cluster ->
        EditClusterDialog(
            cluster = cluster,
            onDismiss = { onAction(HomeUiAction.DismissEditCluster) },
            onSaveName = { id, newName -> onAction(HomeUiAction.SaveClusterName(id, newName)) },
        )
    }

    uiState.clusterToDelete?.let { cluster ->
        DeleteClusterDialog(
            cluster = cluster,
            onDismiss = { onAction(HomeUiAction.DismissDeleteCluster) },
            onConfirmDelete = { id -> onAction(HomeUiAction.ConfirmDeleteCluster(id)) },
        )
    }

    uiState.errorDialogData?.let { data ->
        ErrorDialog(
            data = data,
            onDismiss = { onAction(HomeUiAction.DismissErrorDialog) },
            onCopy = { onAction(HomeUiAction.CopyErrorClicked(it)) },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ManageClustersScreenPreview() {
    KubeNexusTheme {
        ManageClustersScreen(
            uiState = HomeUiState(
                clusters = listOf(
                    Cluster(
                        id = "1",
                        name = "production-cluster",
                        serverUrl = "https://k8s.example.com:6443",
                        contextName = "prod-context",
                        userName = "admin",
                        namespace = "default",
                        rawKubeconfig = "",
                        isActive = true,
                    ),
                    Cluster(
                        id = "2",
                        name = "staging-cluster",
                        serverUrl = "https://192.168.1.100:6443",
                        contextName = "staging-context",
                        userName = "developer",
                        namespace = "staging",
                        rawKubeconfig = "",
                        isActive = false,
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
