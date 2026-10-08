package dev.hridaya.kubenexus.presentation.deployments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DeploymentsRoute(
    viewModel: DeploymentsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDeploymentClick: (DeploymentSummary) -> Unit = {},
    onNavigateToCreateDeployment: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DeploymentsScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onDeploymentClick = onDeploymentClick,
        onNavigateToCreateDeployment = onNavigateToCreateDeployment,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeploymentsScreen(
    uiState: DeploymentsUiState,
    onAction: (DeploymentsUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onDeploymentClick: (DeploymentSummary) -> Unit = {},
    onNavigateToCreateDeployment: () -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredDeployments = remember(uiState.deployments, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.deployments
        } else {
            val query = searchQuery.trim()
            uiState.deployments.filter { deployment ->
                deployment.name.contains(query, ignoreCase = true) ||
                        deployment.namespace.contains(query, ignoreCase = true) ||
                        deployment.images.any { image ->
                            image.contains(
                                query,
                                ignoreCase = true
                            )
                        } ||
                        "${deployment.readyReplicas}/${deployment.desiredReplicas}"
                            .contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Deployments",
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
            MediumFloatingActionButton(
                onClick = onNavigateToCreateDeployment,
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Deployment",
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                )
            }
        },
    ) { innerPadding ->
        RefreshableBox(
            isRefreshing = uiState.isSyncing,
            onRefresh = { onAction(DeploymentsUiAction.Refresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }

                DeploymentsSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )

                DeploymentsNamespaceFilterBar(
                    availableNamespaces = uiState.namespaces,
                    selectedNamespace = uiState.selectedNamespace,
                    lastSyncedAt = uiState.lastSyncedAt,
                    onSelectNamespace = { onAction(DeploymentsUiAction.SelectNamespace(it)) },
                )

                when {
                    uiState.isLoading && uiState.deployments.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }

                    uiState.errorMessage != null && uiState.deployments.isEmpty() -> {
                        DeploymentsErrorState(
                            message = uiState.errorMessage.orEmpty(),
                            onRetry = { onAction(DeploymentsUiAction.Refresh) },
                        )
                    }

                    uiState.deployments.isEmpty() -> {
                        DeploymentsEmptyState(
                            isSyncing = uiState.isSyncing,
                            selectedNamespace = uiState.selectedNamespace,
                        )
                    }

                    filteredDeployments.isEmpty() -> {
                        DeploymentsNoMatchState(
                            searchQuery = searchQuery,
                            selectedNamespace = uiState.selectedNamespace,
                            onClearSearch = { searchQuery = "" },
                        )
                    }

                    else -> {
                        val isAllNamespaces = remember(uiState.selectedNamespace) {
                            uiState.selectedNamespace.equals(ALL_NAMESPACES_FILTER, ignoreCase = true) ||
                                uiState.selectedNamespace.isBlank()
                        }
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 32.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(filteredDeployments, key = { it.id }) { deployment ->
                                DeploymentCard(
                                    deployment = deployment,
                                    showNamespace = isAllNamespaces,
                                    onClick = { onDeploymentClick(deployment) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DeploymentsScreenPreview() {
    KubeNexusTheme {
        DeploymentsScreen(
            uiState = DeploymentsUiState(
                isLoading = false,
                deployments = listOf(
                    DeploymentSummary(
                        id = "default/web",
                        name = "web-frontend",
                        namespace = "default",
                        desiredReplicas = 3,
                        readyReplicas = 3,
                        availableReplicas = 3,
                        images = listOf("nginx:1.27"),
                        creationTimestampMillis = System.currentTimeMillis(),
                    ),
                    DeploymentSummary(
                        id = "team-a/api",
                        name = "api-server",
                        namespace = "team-a",
                        desiredReplicas = 2,
                        readyReplicas = 1,
                        availableReplicas = 1,
                        images = listOf("registry.example.com/api:v2.4.1"),
                        creationTimestampMillis = System.currentTimeMillis(),
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
            onDeploymentClick = {},
        )
    }
}
