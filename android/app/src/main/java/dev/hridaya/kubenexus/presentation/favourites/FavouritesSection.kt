package dev.hridaya.kubenexus.presentation.favourites

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.MAX_FAVOURITE_RESOURCES
import dev.hridaya.kubenexus.presentation.home.components.ResourceGroupHeader
import dev.hridaya.kubenexus.presentation.home.components.ResourcePreferenceCard
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourceGroup
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme
import kotlinx.coroutines.launch

/** Home category of favourite resources. Shows nothing while [favourites] is null (not loaded yet). */
@Composable
fun FavouritesSection(
    favourites: List<ResourcePresentation<*>>?,
    lastRefreshedAt: Long?,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onAddFavourite: (ResourcePresentation<*>) -> Unit,
    onRemoveFavourite: (ResourcePresentation<*>) -> Unit,
    modifier: Modifier = Modifier,
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResourceGroupHeader(title = "Favourite", lastRefreshedAt = lastRefreshedAt)
        if (favourites.isEmpty()) {
            EmptyFavouritesView(onAdd = { showAddSheet = true })
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                favourites.forEach { presentation ->
                    key(presentation.kind.type.key) {
                        FavouriteRoundButton(
                            presentation = presentation,
                            onOpen = { onOpenResources(presentation) },
                            onRemove = { onRemoveFavourite(presentation) },
                        )
                    }
                }
                AddRoundButton(onClick = { showAddSheet = true })
            }
        }
    }
}

/** Round button with icon inside and title label below. Long-pressing offers to remove it. */
@Composable
private fun FavouriteRoundButton(
    presentation: ResourcePresentation<*>,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(72.dp)
                .combinedClickable(
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuExpanded = true
                    },
                    onLongClickLabel = "Show options",
                    onClick = {
                        //noop
                    },
                )
                .padding(vertical = 4.dp),
        ) {
            FilledTonalIconButton(
                modifier = Modifier.size(56.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                onClick = onOpen,
            ) {
                Icon(
                    imageVector = presentation.icon,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

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

/** Round Add button with plus icon inside, matching the user sketch. */
@Composable
private fun AddRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.width(72.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .width(72.dp)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FilledTonalIconButton(
                modifier = Modifier.size(56.dp),
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
                onClick = onClick,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Add favourite",
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                shapes = ListItemDefaults.segmentedShapes(
                    index = index,
                    count = group.resources.size
                ),
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
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp),
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
private fun FavouritesSectionPreview() {
    KubeNexusTheme {
        FavouritesSection(
            favourites = ResourceCatalog.all,
            lastRefreshedAt = System.currentTimeMillis(),
            onOpenResources = {},
            onAddFavourite = {},
            onRemoveFavourite = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FavouritesSectionEmptyPreview() {
    KubeNexusTheme {
        FavouritesSection(
            favourites = emptyList(),
            lastRefreshedAt = null,
            onOpenResources = {},
            onAddFavourite = {},
            onRemoveFavourite = {},
        )
    }
}
