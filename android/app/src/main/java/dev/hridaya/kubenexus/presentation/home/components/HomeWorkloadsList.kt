package dev.hridaya.kubenexus.presentation.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.common.util.TimeFormatter
import dev.hridaya.kubenexus.presentation.resources.ResourceCatalog
import dev.hridaya.kubenexus.presentation.resources.ResourceGroup
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

/** The resource kinds of the active cluster, grouped as [ResourceCatalog.groups] lists them. */
@Composable
fun HomeWorkloadsList(
    isRefreshing: Boolean,
    lastRefreshedAt: Long?,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onNoopAction: (String) -> Unit,
    modifier: Modifier = Modifier,
    groups: List<ResourceGroup> = ResourceCatalog.groups,
    listState: LazyListState = rememberLazyListState(),
) {
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

        groups.forEachIndexed { groupIndex, group ->
            item(key = "header/${group.title}") {
                // The refresh time belongs to the whole list, so it is shown once.
                GroupHeader(title = group.title, lastRefreshedAt = lastRefreshedAt.takeIf { groupIndex == 0 })
            }
            item(key = "group/${group.title}") {
                GroupEntries(group = group, onOpenResources = onOpenResources, onNoopAction = onNoopAction)
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String, lastRefreshedAt: Long?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )

        if (lastRefreshedAt != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = TimeFormatter.formatLastRefreshed(lastRefreshedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupEntries(
    group: ResourceGroup,
    onOpenResources: (ResourcePresentation<*>) -> Unit,
    onNoopAction: (String) -> Unit,
) {
    val count = group.resources.size + group.upcoming.size
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        group.resources.forEachIndexed { index, presentation ->
            ResourcePreferenceCard(
                title = presentation.title,
                subtitle = presentation.description,
                icon = presentation.icon,
                onClick = { onOpenResources(presentation) },
                shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
            )
        }
        group.upcoming.forEachIndexed { index, upcoming ->
            ResourcePreferenceCard(
                title = upcoming.title,
                subtitle = upcoming.description,
                icon = upcoming.icon,
                onClick = { onNoopAction("${upcoming.title} management coming soon") },
                shapes = ListItemDefaults.segmentedShapes(index = group.resources.size + index, count = count),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeWorkloadsListPreview() {
    KubeNexusTheme {
        HomeWorkloadsList(
            isRefreshing = false,
            lastRefreshedAt = System.currentTimeMillis(),
            onOpenResources = {},
            onNoopAction = {},
        )
    }
}
