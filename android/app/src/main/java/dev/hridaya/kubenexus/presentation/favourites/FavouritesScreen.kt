package dev.hridaya.kubenexus.presentation.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.presentation.home.HomeScreen
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.home.components.ResourceGroupHeader
import dev.hridaya.kubenexus.presentation.home.components.ResourcePreferenceCard
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourceGroup
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlinx.coroutines.launch

@Composable
fun FavouritesRoute(
    homeViewModel: HomeViewModel,
    favouritesViewModel: FavouritesViewModel,
    onNavigateToManageClusters: () -> Unit,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onCreateResource: (ResourcePresentation<*>) -> Unit,
    modifier: Modifier = Modifier,
    activePortForwardCount: Int = 0,
    onOpenPortForwardSessions: () -> Unit = {},
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val favourites by favouritesViewModel.favourites.collectAsStateWithLifecycle()

    HomeScreen(
        title = "Favourites",
        uiState = uiState,
        onAction = homeViewModel::onAction,
        onNavigateToManageClusters = onNavigateToManageClusters,
        creatableResources = favourites.orEmpty(),
        onCreateResource = onCreateResource,
        activePortForwardCount = activePortForwardCount,
        onOpenPortForwardSessions = onOpenPortForwardSessions,
        modifier = modifier,
    ) { listState ->
        FavouritesList(
            favourites = favourites,
            isRefreshing = uiState.isRefreshing,
            lastRefreshedAt = uiState.lastRefreshedAt,
            onOpenResources = onOpenResources,
            onAddFavourite = { favouritesViewModel.onAction(FavouritesUiAction.AddFavourite(it)) },
            onRemoveFavourite = { favouritesViewModel.onAction(FavouritesUiAction.RemoveFavourite(it)) },
            listState = listState,
        )
    }
}

/** Shows nothing while [favourites] is null (not loaded yet). */
@Composable
fun FavouritesList(
    favourites: List<ResourcePresentation<*>>?,
    isRefreshing: Boolean,
    lastRefreshedAt: Long?,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onAddFavourite: (ResourcePresentation<*>) -> Unit,
    onRemoveFavourite: (ResourcePresentation<*>) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    if (favourites == null) return
    var showAddSheet by rememberSaveable { mutableStateOf(false) }

    if (showAddSheet) {
        AddFavouriteSheet(
            favourites = favourites,
            onAddFavourite = onAddFavourite,
            onDismiss = { showAddSheet = false },
        )
    }

    if (favourites.isEmpty()) {
        EmptyFavouritesView(onAdd = { showAddSheet = true }, modifier = modifier)
        return
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        if (isRefreshing) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }

        if (lastRefreshedAt != null) {
            item(key = "header") {
                ResourceGroupHeader(title = null, lastRefreshedAt = lastRefreshedAt)
            }
        }
        item(key = "favourites") {
            val count = favourites.size + 1
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                favourites.forEachIndexed { index, presentation ->
                    key(presentation.kind.type.key) {
                        FavouriteEntry(
                            presentation = presentation,
                            shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
                            onOpen = { onOpenResources(presentation) },
                            onRemove = { onRemoveFavourite(presentation) },
                        )
                    }
                }
                ResourcePreferenceCard(
                    title = "Add",
                    subtitle = "Keep another resource here",
                    icon = Icons.Filled.Add,
                    onClick = { showAddSheet = true },
                    shapes = ListItemDefaults.segmentedShapes(index = favourites.size, count = count),
                )
            }
        }
    }
}

/** Long-pressing a favourite offers to remove it. */
@Composable
private fun FavouriteEntry(
    presentation: ResourcePresentation<*>,
    shapes: ListItemShapes,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    Box {
        ResourcePreferenceCard(
            title = presentation.title,
            subtitle = presentation.description,
            icon = presentation.icon,
            onClick = onOpen,
            shapes = shapes,
            onLongClick = { menuExpanded = true },
            onLongClickLabel = "Show options",
        )
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Remove from favourites") },
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                onClick = {
                    menuExpanded = false
                    onRemove()
                },
            )
        }
    }
}

/** Lists the resource kinds that are not favourites yet; picking one adds it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFavouriteSheet(
    favourites: List<ResourcePresentation<*>>,
    onAddFavourite: (ResourcePresentation<*>) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    val hideThen: (() -> Unit) -> Unit = { action ->
        scope.launch { sheetState.hide() }.invokeOnCompletion { action() }
    }
    val favouriteKeys = favourites.mapTo(HashSet()) { it.kind.type.key }
    val groups = ResourceCatalog.groups
        .map { group -> group.copy(resources = group.resources.filterNot { it.kind.type.key in favouriteKeys }) }
        .filter { it.resources.isNotEmpty() }

    ModalBottomSheet(
        onDismissRequest = { hideThen(onDismiss) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Add to favourites",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            when {
                favourites.size >= MAX_FAVOURITE_RESOURCES -> SheetMessage(
                    "You can keep up to $MAX_FAVOURITE_RESOURCES favourites. " +
                        "Long-press one to remove it before adding another.",
                )

                groups.isEmpty() -> SheetMessage("Every resource is already a favourite.")

                else -> groups.forEach { group ->
                    AddableGroup(
                        group = group,
                        onAdd = { presentation ->
                            hideThen {
                                onAddFavourite(presentation)
                                onDismiss()
                            }
                        },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AddableGroup(group: ResourceGroup, onAdd: (ResourcePresentation<*>) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        ResourceGroupHeader(title = group.title, lastRefreshedAt = null)
        group.resources.forEachIndexed { index, presentation ->
            ResourcePreferenceCard(
                title = presentation.title,
                subtitle = presentation.description,
                icon = presentation.icon,
                onClick = { onAdd(presentation) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = group.resources.size),
                trailingIcon = Icons.Filled.Add,
            )
        }
    }
}

@Composable
private fun SheetMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

@Composable
private fun EmptyFavouritesView(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.StarBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "No favourites yet",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Add the resources you use most to keep them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Add")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FavouritesListPreview() {
    KubeNexusTheme {
        FavouritesList(
            favourites = ResourceCatalog.all,
            isRefreshing = false,
            lastRefreshedAt = System.currentTimeMillis(),
            onOpenResources = {},
            onAddFavourite = {},
            onRemoveFavourite = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavouritesListEmptyPreview() {
    KubeNexusTheme {
        FavouritesList(
            favourites = emptyList(),
            isRefreshing = false,
            lastRefreshedAt = null,
            onOpenResources = {},
            onAddFavourite = {},
            onRemoveFavourite = {},
        )
    }
}
