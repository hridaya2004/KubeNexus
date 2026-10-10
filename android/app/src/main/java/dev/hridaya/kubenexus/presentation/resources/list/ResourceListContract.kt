package dev.hridaya.kubenexus.presentation.resources.list

import dev.hridaya.kubenexus.presentation.common.ALL_NAMESPACES_FILTER
import dev.hridaya.kubenexus.presentation.common.isAllNamespaces
import dev.hridaya.kubenexus.presentation.resources.ResourceRow

data class ResourceListUiState(
    val rows: List<ResourceRow> = emptyList(),
    val namespaces: List<String> = listOf(ALL_NAMESPACES_FILTER),
    val selectedNamespace: String = ALL_NAMESPACES_FILTER,
    val lastSyncedAt: Long? = null,
    val isCacheLoaded: Boolean = false,
    val isSyncing: Boolean = false,
    val syncFailed: Boolean = false,
) {
    /** Nothing to show yet: the cache is still loading, or empty while a sync runs. */
    val isLoading: Boolean
        get() = rows.isEmpty() && (!isCacheLoaded || isSyncing)

    /** A sync is refreshing rows that are already on screen. */
    val isRefreshing: Boolean
        get() = isSyncing && rows.isNotEmpty()

    /** The last sync failed and nothing is cached to fall back on. */
    val showsSyncError: Boolean
        get() = syncFailed && rows.isEmpty() && !isSyncing

    /** Rows then come from several namespaces, so each shows its own. */
    val showsNamespaces: Boolean
        get() = isAllNamespaces(selectedNamespace)
}

sealed interface ResourceListUiAction {
    data object Refresh : ResourceListUiAction
    data class SelectNamespace(val namespace: String) : ResourceListUiAction
}
