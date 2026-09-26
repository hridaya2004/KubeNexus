package dev.hridaya.kubenexus.presentation.pods.detail

import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample

enum class PodDetailTab(val title: String) {
    DESCRIBE("Describe"),
    LOGS("Logs"),
    TERMINAL("Terminal"),
}

/**
 * How much usage history the chart shows. metrics-server refreshes a pod's usage about every
 * 15 seconds, so no range is shorter than two of its samples.
 */
enum class MetricsRange(val label: String, val durationMs: Long) {
    SECONDS_30("30s", 30_000L),
    MINUTES_1("1 min", 60_000L),
    MINUTES_5("5 min", 300_000L),
    ;

    /**
     * The samples within this range of the newest one, and never fewer than the newest two, so
     * a cluster whose metrics refresh more slowly than the range still charts its latest change.
     * Like the buffer, the window is measured in the cluster's time, not the device's, so a
     * device clock that disagrees with the cluster's cannot empty the chart.
     */
    fun window(samples: List<PodMetricSample>): List<PodMetricSample> {
        val sorted = samples.sortedBy { it.timestampMillis }
        val newest = sorted.lastOrNull()?.timestampMillis ?: return emptyList()
        val inRange = sorted.filter { it.timestampMillis >= newest - durationMs }
        return if (inRange.size >= 2) inRange else sorted.takeLast(2)
    }
}

data class PodDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val lastRefreshedAt: Long? = null,
    val podName: String = "",
    val namespace: String = "",
    val clusterId: String? = null,
    val podDetails: PodDetails? = null,
    val selectedTab: PodDetailTab = PodDetailTab.DESCRIBE,
    val selectedContainer: String? = null,
    val logs: List<String> = emptyList(),
    val isStreamingLogs: Boolean = false,
    val isLoadingLogs: Boolean = false,
    val tailLines: Long? = 250L,
    val errorMessage: String? = null,
    val isTerminalActive: Boolean = false,
    /** Latest terminal status notice, e.g. "Session closed"; also shown in the terminal. */
    val terminalNotice: String? = null,
    val activeShellCommand: String = "/bin/sh",
    val showDeleteConfirmDialog: Boolean = false,
    val isDeletingPod: Boolean = false,
    val isOnline: Boolean = true,
    val clusterConnectionStatus: ClusterConnectionStatus = ClusterConnectionStatus.CONNECTED,
    val metricsSamples: List<PodMetricSample> = emptyList(),
    val metricsRange: MetricsRange = MetricsRange.MINUTES_5,
    val isLoadingMetrics: Boolean = true,
) {
    val isContainerAttachable: Boolean
        get() {
            if (!isOnline || clusterConnectionStatus != ClusterConnectionStatus.CONNECTED) return false
            val currentContainer =
                (podDetails?.containers.orEmpty() + podDetails?.initContainers.orEmpty())
                    .find { it.name == selectedContainer }
            return currentContainer == null ||
                    currentContainer.state.equals(
                        "Running",
                        ignoreCase = true,
                    ) ||
                    currentContainer.ready
        }
}

sealed interface PodDetailUiAction {
    data object RefreshDescribe : PodDetailUiAction
    data class SelectTab(val tab: PodDetailTab) : PodDetailUiAction
    data class SelectContainer(val containerName: String) : PodDetailUiAction
    data class SetTailLines(val tailLines: Long?) : PodDetailUiAction
    data object FetchLogs : PodDetailUiAction
    data object FetchAllLogs : PodDetailUiAction
    data object StartStreamingLogs : PodDetailUiAction
    data object StopStreamingLogs : PodDetailUiAction
    data object ClearLogs : PodDetailUiAction

    data class StartInteractiveTerminal(val shell: String? = null) : PodDetailUiAction
    data object StopInteractiveTerminal : PodDetailUiAction
    data class SelectMetricsRange(val range: MetricsRange) : PodDetailUiAction
    data object ClearTerminal : PodDetailUiAction

    data class ShowDeleteDialog(val show: Boolean) : PodDetailUiAction
    data object ConfirmDeletePod : PodDetailUiAction
}

sealed interface PodDetailUiEffect {
    data class ShowToast(val message: String) : PodDetailUiEffect
    data object NavigateBack : PodDetailUiEffect
}
