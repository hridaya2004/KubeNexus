package dev.hridaya.kubenexus.presentation.services

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.common.components.segmentedShape
import dev.hridaya.kubenexus.presentation.services.components.ServiceCard
import dev.hridaya.kubenexus.presentation.services.components.ServicesEmptyState
import dev.hridaya.kubenexus.presentation.services.components.ServicesNamespaceFilterBar
import dev.hridaya.kubenexus.presentation.services.components.ServicesNoMatchState
import dev.hridaya.kubenexus.presentation.services.components.ServicesSearchBar
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.appBarColors

@Composable
fun ServicesRoute(
    viewModel: ServicesViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onServiceClick: (ServiceSummary) -> Unit = {},
    onNavigateToCreateService: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ServicesScreen(
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        onServiceClick = onServiceClick,
        onNavigateToCreateService = onNavigateToCreateService,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    uiState: ServicesUiState,
    onAction: (ServicesUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onServiceClick: (ServiceSummary) -> Unit = {},
    onNavigateToCreateService: () -> Unit = {},
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val filteredServices = remember(uiState.services, searchQuery) {
        if (searchQuery.isBlank()) {
            uiState.services
        } else {
            val query = searchQuery.trim()
            uiState.services.filter { service ->
                service.name.contains(query, ignoreCase = true) ||
                        service.namespace.contains(query, ignoreCase = true) ||
                        service.type.contains(query, ignoreCase = true) ||
                        service.clusterIP.contains(query, ignoreCase = true)
            }
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val listState = rememberLazyListState()
    val fabVisible by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 || !listState.canScrollForward }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = "Services",
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
                colors = appBarColors(),
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            MediumFloatingActionButton(
                onClick = onNavigateToCreateService,
                modifier = Modifier.animateFloatingActionButton(
                    visible = fabVisible,
                    alignment = Alignment.BottomEnd,
                ),
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Service",
                    modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                )
            }
        },
    ) { innerPadding ->
        RefreshableBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(ServicesUiAction.Refresh) },
            appBarScrollConnection = scrollBehavior.nestedScrollConnection,
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

                ServicesSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )

                ServicesNamespaceFilterBar(
                    availableNamespaces = uiState.availableNamespaces,
                    selectedNamespace = uiState.selectedNamespace,
                    lastRefreshedAt = uiState.lastRefreshedAt,
                    onSelectNamespace = { onAction(ServicesUiAction.SelectNamespace(it)) },
                )

                when {
                    uiState.isLoading && uiState.services.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LoadingIndicator()
                        }
                    }

                    uiState.errorMessage != null && uiState.services.isEmpty() -> {
                        ServicesErrorState(
                            message = uiState.errorMessage.orEmpty(),
                            onRetry = { onAction(ServicesUiAction.Refresh) },
                        )
                    }

                    uiState.services.isEmpty() -> {
                        ServicesEmptyState(
                            isRefreshing = uiState.isRefreshing,
                            selectedNamespace = uiState.selectedNamespace,
                        )
                    }

                    filteredServices.isEmpty() -> {
                        ServicesNoMatchState(
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
                            state = listState,
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 32.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            itemsIndexed(filteredServices, key = { _, service -> service.id }) { index, service ->
                                ServiceCard(
                                    shape = segmentedShape(index, filteredServices.size),
                                    service = service,
                                    showNamespace = isAllNamespaces,
                                    onClick = { onServiceClick(service) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServicesErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text = "Retry",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServicesScreenPreview() {
    KubeNexusTheme {
        ServicesScreen(
            uiState = ServicesUiState(
                isLoading = false,
                services = listOf(
                    ServiceSummary(
                        id = "default/web",
                        name = "web-frontend",
                        namespace = "default",
                        creationTimestampMillis = System.currentTimeMillis(),
                        type = "ClusterIP",
                        clusterIP = "10.96.0.42",
                        ports = listOf(
                            ServicePortDetail(
                                port = 80,
                                targetPort = 8080,
                                nodePort = null,
                                protocol = "TCP",
                                name = "http",
                            ),
                        ),
                    ),
                    ServiceSummary(
                        id = "team-a/api",
                        name = "api-server",
                        namespace = "team-a",
                        creationTimestampMillis = System.currentTimeMillis(),
                        type = "NodePort",
                        clusterIP = "10.96.0.17",
                        ports = listOf(
                            ServicePortDetail(
                                port = 8080,
                                targetPort = 8080,
                                nodePort = 31580,
                                protocol = "TCP",
                                name = null,
                            ),
                        ),
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
            onServiceClick = {},
        )
    }
}
