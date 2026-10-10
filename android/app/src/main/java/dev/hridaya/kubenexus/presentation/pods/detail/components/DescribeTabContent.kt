package dev.hridaya.kubenexus.presentation.pods.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.domain.model.PodConditionDetail
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.presentation.common.components.LoadingContent
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailUiAction
import dev.hridaya.kubenexus.presentation.pods.detail.PodDetailUiState
import dev.hridaya.kubenexus.ui.theme.KubeNexusTheme

@Composable
fun DescribeTabContent(
    uiState: PodDetailUiState,
    onAction: (PodDetailUiAction) -> Unit,
    onNavigateToLogs: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading && uiState.podDetails == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.Center,
        ) {
            LoadingContent(message = "Executing describe pod")
        }
        return
    }

    val details = uiState.podDetails
    if (details == null) {
        DescribeErrorContent(
            errorMessage = uiState.errorMessage,
            onRetry = { onAction(PodDetailUiAction.RefreshDescribe) },
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item {
            PodStatusCard(
                details = details,
                lastRefreshedAt = uiState.lastRefreshedAt,
            )
        }

        if (details.initContainers.isNotEmpty()) {
            item {
                SectionTitle(text = "Init Containers (${details.initContainers.size})")
            }

            val isConnected =
                uiState.isOnline && uiState.clusterConnectionStatus == ClusterConnectionStatus.CONNECTED

            items(details.initContainers, key = { "init/${it.name}" }) { container ->
                ContainerCard(
                    container = container,
                    isInitContainer = true,
                    isOnline = isConnected,
                    onViewLogsClick = { onNavigateToLogs(container.name) },
                    onOpenTerminalClick = { onNavigateToTerminal(container.name) },
                )
            }
        }

        item {
            SectionTitle(text = "Containers (${details.containers.size})")
        }

        val isConnected =
            uiState.isOnline && uiState.clusterConnectionStatus == ClusterConnectionStatus.CONNECTED

        items(details.containers, key = { "container/${it.name}" }) { container ->
            ContainerCard(
                container = container,
                isInitContainer = false,
                isOnline = isConnected,
                onViewLogsClick = { onNavigateToLogs(container.name) },
                onOpenTerminalClick = { onNavigateToTerminal(container.name) },
            )
        }

        if (details.conditions.isNotEmpty()) {
            item {
                SectionTitle(text = "Conditions")
            }

            item {
                ConditionsCard(conditions = details.conditions)
            }
        }

        if (uiState.isOnline && uiState.clusterConnectionStatus == ClusterConnectionStatus.CONNECTED) {
            item {
                PodMetricsSection(
                    samples = uiState.metricsSamples,
                    selectedRange = uiState.metricsRange,
                    isLoading = uiState.isLoadingMetrics,
                    onSelectRange = { onAction(PodDetailUiAction.SelectMetricsRange(it)) },
                )
            }
        }

        if (details.events.isNotEmpty()) {
            item {
                SectionTitle(text = "Events")
            }

            items(details.events) { event ->
                EventCard(event = event)
            }
        }

        if (details.volumes.isNotEmpty()) {
            item {
                SectionTitle(text = "Volumes (${details.volumes.size})")
            }

            item {
                VolumesCard(volumes = details.volumes)
            }
        }

        if (details.labels.isNotEmpty()) {
            item {
                SectionTitle(text = "Labels")
            }

            item {
                LabelsCard(labels = details.labels)
            }
        }

        if (details.annotations.isNotEmpty()) {
            item {
                SectionTitle(text = "Annotations")
            }

            item {
                LabelsCard(labels = details.annotations)
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            DeletePodButton(onClick = { onAction(PodDetailUiAction.ShowDeleteDialog(true)) })
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DescribeTabContentPreview() {
    KubeNexusTheme {
        DescribeTabContent(
            uiState = PodDetailUiState(
                podName = "nginx",
                namespace = "default",
                podDetails = PodDetails(
                    name = "nginx",
                    namespace = "default",
                    status = PodStatus.RUNNING,
                    node = "worker-1",
                    ip = "10.244.1.4",
                    hostIp = "192.168.1.10",
                    startTime = "2026-08-19T10:00:00Z",
                    labels = mapOf("app" to "nginx"),
                    annotations = emptyMap(),
                    containers = listOf(
                        ContainerDetail(
                            name = "nginx",
                            image = "nginx:alpine",
                            ready = true,
                            restartCount = 0,
                            state = "Running",
                        )
                    ),
                    conditions = listOf(
                        PodConditionDetail(type = "Ready", status = "True")
                    ),
                    events = emptyList(),
                ),
            ),
            onAction = {},
            onNavigateToLogs = {},
            onNavigateToTerminal = {},
        )
    }
}
