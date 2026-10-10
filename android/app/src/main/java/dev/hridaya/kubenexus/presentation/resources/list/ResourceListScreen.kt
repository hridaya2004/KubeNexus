package dev.hridaya.kubenexus.presentation.resources.list

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.presentation.common.components.NamespaceFilterBar
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.common.components.SearchField
import dev.hridaya.kubenexus.presentation.common.components.segmentedShape
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import dev.hridaya.kubenexus.presentation.resources.RowDetail
import dev.hridaya.kubenexus.presentation.resources.kinds.ServicesPresentation
import dev.hridaya.kubenexus.presentation.resources.list.components.ResourceCard
import dev.hridaya.kubenexus.presentation.resources.list.components.ResourceListEmpty
import dev.hridaya.kubenexus.presentation.resources.list.components.ResourceListLoading
import dev.hridaya.kubenexus.presentation.resources.list.components.ResourceListNoMatch
import dev.hridaya.kubenexus.presentation.resources.list.components.ResourceListSyncError
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import dev.hridaya.kubenexus.ui.theme.appBarColors

@Composable
fun ResourceListRoute(
    presentation: ResourcePresentation<*>,
    viewModel: ResourceListViewModel,
    onNavigateBack: () -> Unit,
    onOpenResource: (ResourceRow) -> Unit,
    modifier: Modifier = Modifier,
    onCreateResource: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Runs each time the list is shown, including on the way back from a detail
    // or create screen, so it reflects what was just changed there.
    LaunchedEffect(viewModel) { viewModel.onAction(ResourceListUiAction.Refresh) }
    ResourceListScreen(
        presentation = presentation,
        uiState = uiState,
        onAction = viewModel::onAction,
        onNavigateBack = onNavigateBack,
        onOpenResource = onOpenResource,
        onCreateResource = onCreateResource,
        modifier = modifier,
    )
}

/** The searchable, namespace-filtered list of one resource kind. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResourceListScreen(
    presentation: ResourcePresentation<*>,
    uiState: ResourceListUiState,
    onAction: (ResourceListUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenResource: (ResourceRow) -> Unit,
    modifier: Modifier = Modifier,
    onCreateResource: (() -> Unit)? = null,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val visibleRows = remember(uiState.rows, searchQuery) {
        uiState.rows.filter { it.matches(searchQuery) }
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
                title = { Text(text = presentation.title) },
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
            if (onCreateResource != null) {
                MediumFloatingActionButton(
                    onClick = onCreateResource,
                    modifier = Modifier.animateFloatingActionButton(
                        visible = fabVisible,
                        alignment = Alignment.BottomEnd,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add ${presentation.singularTitle}",
                        modifier = Modifier.size(FloatingActionButtonDefaults.MediumIconSize),
                    )
                }
            }
        },
    ) { innerPadding ->
        RefreshableBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(ResourceListUiAction.Refresh) },
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

                SearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = presentation.searchHint,
                )

                NamespaceFilterBar(
                    namespaces = uiState.namespaces,
                    selectedNamespace = uiState.selectedNamespace,
                    lastSyncedAt = uiState.lastSyncedAt,
                    onSelectNamespace = { onAction(ResourceListUiAction.SelectNamespace(it)) },
                )

                when {
                    uiState.isLoading -> ResourceListLoading()

                    uiState.showsSyncError -> ResourceListSyncError(
                        title = presentation.title,
                        onRetry = { onAction(ResourceListUiAction.Refresh) },
                    )

                    uiState.rows.isEmpty() -> ResourceListEmpty(
                        title = presentation.title,
                        icon = presentation.icon,
                        selectedNamespace = uiState.selectedNamespace,
                    )

                    visibleRows.isEmpty() -> ResourceListNoMatch(
                        title = presentation.title,
                        searchQuery = searchQuery,
                        onClearSearch = { searchQuery = "" },
                    )

                    else -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        itemsIndexed(visibleRows, key = { _, row -> row.id }) { index, row ->
                            ResourceCard(
                                row = row,
                                showNamespace = uiState.showsNamespaces,
                                onClick = { onOpenResource(row) },
                                shape = segmentedShape(index, visibleRows.size),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResourceListScreenPreview() {
    KubeNexusTheme {
        ResourceListScreen(
            presentation = ServicesPresentation,
            uiState = ResourceListUiState(
                isCacheLoaded = true,
                rows = listOf(
                    ResourceRow(
                        id = "default_web",
                        name = "web-frontend",
                        namespace = "default",
                        badge = "ClusterIP",
                        details = listOf(RowDetail("10.96.0.42", label = "ClusterIP:", monospace = true)),
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
            onOpenResource = {},
            onCreateResource = {},
        )
    }
}
