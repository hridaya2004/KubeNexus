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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hridaya.kubenexus.domain.repository.FavouriteResourcesRepository.Companion.MAX_FAVOURITES
import dev.hridaya.kubenexus.presentation.home.HomeScreen
import dev.hridaya.kubenexus.presentation.home.HomeViewModel
import dev.hridaya.kubenexus.presentation.home.components.ResourceGroupHeader
import dev.hridaya.kubenexus.presentation.home.components.ResourcePreferenceCard
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun FavouritesRoute(
    homeViewModel: HomeViewModel,
    favouritesViewModel: FavouritesViewModel,
    onNavigateToManageClusters: () -> Unit,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onCreateResource: (ResourcePresentation<*>) -> Unit,
    onBrowseResources: () -> Unit,
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
            onToggleFavourite = { favouritesViewModel.onAction(FavouritesUiAction.ToggleFavourite(it)) },
            onBrowseResources = onBrowseResources,
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
    onToggleFavourite: (ResourcePresentation<*>) -> Unit,
    onBrowseResources: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    if (favourites == null) return
    if (favourites.isEmpty()) {
        EmptyFavouritesView(onBrowseResources = onBrowseResources, modifier = modifier)
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

        item(key = "header") {
            ResourceGroupHeader(
                title = "Favourites · ${favourites.size} of $MAX_FAVOURITES",
                lastRefreshedAt = lastRefreshedAt,
            )
        }
        item(key = "favourites") {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                favourites.forEachIndexed { index, presentation ->
                    ResourcePreferenceCard(
                        title = presentation.title,
                        subtitle = presentation.description,
                        icon = presentation.icon,
                        onClick = { onOpenResources(presentation) },
                        shapes = ListItemDefaults.segmentedShapes(index = index, count = favourites.size),
                        isFavourite = true,
                        onToggleFavourite = { onToggleFavourite(presentation) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFavouritesView(onBrowseResources: () -> Unit, modifier: Modifier = Modifier) {
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
                text = "Star the resources you use most to keep up to $MAX_FAVOURITES of them here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(onClick = onBrowseResources) {
                Text(text = "Browse resources")
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
            onToggleFavourite = {},
            onBrowseResources = {},
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
            onToggleFavourite = {},
            onBrowseResources = {},
        )
    }
}
