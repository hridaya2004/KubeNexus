package dev.hridaya.kubenexus.presentation.explore.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.core.common.util.TimeFormatter
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ResourceExplain
import dev.hridaya.kubenexus.domain.model.ResourceField
import dev.hridaya.kubenexus.presentation.common.components.RefreshableBox
import dev.hridaya.kubenexus.presentation.explore.ExploreUiAction
import dev.hridaya.kubenexus.presentation.explore.ExploreUiState
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreExplainDetailView(
    resource: APIResource,
    uiState: ExploreUiState,
    onAction: (ExploreUiAction) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            ExploreExplainTopBar(
                resource = resource,
                onNavigateBack = onNavigateBack,
            )
        },
    ) { innerPadding ->
        RefreshableBox(
            isRefreshing = uiState.isLoadingExplain,
            onRefresh = { onAction(ExploreUiAction.RetryExplain(resource)) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoadingExplain && uiState.explainDetails == null -> {
                        ExploreExplainLoadingState(
                            resourceKind = resource.kind,
                        )
                    }

                    uiState.explainError != null && uiState.explainDetails == null -> {
                        ExploreExplainErrorState(
                            errorMessage = uiState.explainError,
                            onRetry = { onAction(ExploreUiAction.RetryExplain(resource)) },
                        )
                    }

                    uiState.explainDetails != null -> {
                        val explainDetails = uiState.explainDetails
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 16.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            // Last Refreshed Above the Card
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Schedule,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = TimeFormatter.formatLastRefreshed(explainDetails.lastUpdated),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }

                            // Resource Overview Card with VERSION beneath KIND
                            item {
                                ExplainOverviewCard(explainDetails = explainDetails)
                            }

                            // Fields Header and Search
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = "FIELDS (${explainDetails.fields.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            // Field Search Bar
                            item {
                                FieldSearchBar(
                                    fieldSearchQuery = uiState.fieldSearchQuery,
                                    onQueryChange = { newFieldQuery ->
                                        onAction(
                                            ExploreUiAction.UpdateFieldSearchQuery(
                                                newFieldQuery
                                            )
                                        )
                                    },
                                    onClearQuery = {
                                        onAction(
                                            ExploreUiAction.UpdateFieldSearchQuery(
                                                ""
                                            )
                                        )
                                    },
                                )
                            }

                            if (uiState.filteredFields.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "No fields match '${uiState.fieldSearchQuery}'",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else {
                                items(uiState.filteredFields, key = { it.name }) { field ->
                                    ResourceFieldCard(
                                        field = field,
                                        resourceKind = resource.kind,
                                        onCopy = {
                                            onAction(
                                                ExploreUiAction.CopyText(
                                                    "${resource.kind}.${field.name}",
                                                    "Field"
                                                )
                                            )
                                        },
                                    )
                                }
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
private fun ExploreExplainDetailViewPreview() {
    KubeNexusTheme {
        ExploreExplainDetailView(
            resource = APIResource(
                name = "pods",
                singularName = "pod",
                namespaced = true,
                kind = "Pod",
                verbs = listOf("get", "list"),
                group = "",
                version = "v1",
            ),
            uiState = ExploreUiState(
                explainDetails = ResourceExplain(
                    kind = "Pod",
                    groupVersion = "v1",
                    description = "Pod is a collection of containers that can run on a host.",
                    fields = listOf(
                        ResourceField(
                            name = "spec",
                            type = "PodSpec",
                            description = "Specification of the desired behavior of the pod.",
                            required = true,
                        ),
                    ),
                    lastUpdated = System.currentTimeMillis(),
                ),
            ),
            onAction = {},
            onNavigateBack = {},
        )
    }
}
