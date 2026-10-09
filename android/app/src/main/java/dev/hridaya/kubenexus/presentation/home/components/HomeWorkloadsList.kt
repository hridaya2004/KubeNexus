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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Widgets
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
import dev.hridaya.kubenexus.presentation.common.components.segmentedShape
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun HomeWorkloadsList(
    isRefreshing: Boolean,
    lastRefreshedAt: Long?,
    onNavigateToPods: () -> Unit,
    onNavigateToDeployments: () -> Unit,
    onNavigateToServices: () -> Unit,
    onNoopAction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
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

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Workloads",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

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

        item {
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                ResourcePreferenceCard(
                    title = "Pods",
                    subtitle = "Container instances and workload state",
                    icon = Icons.Outlined.Layers,
                    onClick = onNavigateToPods,
                    shape = segmentedShape(0, 4),
                )
                ResourcePreferenceCard(
                    title = "Deployments",
                    subtitle = "Declarative updates for Pods and ReplicaSets",
                    icon = Icons.Outlined.Apps,
                    onClick = onNavigateToDeployments,
                    shape = segmentedShape(1, 4),
                )
                ResourcePreferenceCard(
                    title = "Services",
                    subtitle = "Stable network endpoints for Pods",
                    icon = Icons.Outlined.Dns,
                    onClick = onNavigateToServices,
                    shape = segmentedShape(2, 4),
                )
                ResourcePreferenceCard(
                    title = "ReplicaSets",
                    subtitle = "Maintain stable set of replica Pods",
                    icon = Icons.Outlined.Widgets,
                    onClick = {
                        onNoopAction("ReplicaSet management coming soon")
                    },
                    shape = segmentedShape(3, 4),
                )
            }
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
            onNavigateToPods = {},
            onNavigateToDeployments = {},
            onNavigateToServices = {},
            onNoopAction = {},
        )
    }
}
