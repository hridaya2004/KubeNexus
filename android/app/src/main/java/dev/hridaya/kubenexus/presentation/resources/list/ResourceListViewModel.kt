package dev.hridaya.kubenexus.presentation.resources.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.KubeObjectSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.usecase.GetNamespacesUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveLastSyncedUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.SyncResourcesUseCase
import dev.hridaya.kubenexus.presentation.common.ALL_NAMESPACES_FILTER
import dev.hridaya.kubenexus.presentation.common.namespaceFilterOptions
import dev.hridaya.kubenexus.presentation.common.namespaceScopeOf
import dev.hridaya.kubenexus.presentation.resources.ResourcePresentation
import dev.hridaya.kubenexus.presentation.resources.ResourceRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The offline-first list behind every resource kind's list screen.
 *
 * The cache is the single source of truth: rows render from it immediately,
 * offline included, and a sync only ever writes to it. The visible namespace is
 * synced whenever the screen is shown ([ResourceListUiAction.Refresh], sent by
 * the route), on every filter change, and when the device comes back online.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = ResourceListViewModel.Factory::class)
class ResourceListViewModel @AssistedInject constructor(
    @Assisted private val presentation: ResourcePresentation<*>,
    @Assisted("clusterId") private val clusterId: String?,
    @Assisted("namespace") initialNamespace: String?,
    private val observeResources: ObserveResourcesUseCase,
    private val syncResources: SyncResourcesUseCase,
    private val observeLastSynced: ObserveLastSyncedUseCase,
    private val getNamespaces: GetNamespacesUseCase,
    private val networkMonitor: NetworkMonitor,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            presentation: ResourcePresentation<*>,
            @Assisted("clusterId") clusterId: String?,
            @Assisted("namespace") namespace: String?,
        ): ResourceListViewModel
    }

    private val selectedNamespace = MutableStateFlow(
        initialNamespace?.takeIf { it.isNotBlank() } ?: ALL_NAMESPACES_FILTER,
    )

    private val _uiState = MutableStateFlow(ResourceListUiState(selectedNamespace = selectedNamespace.value))
    val uiState: StateFlow<ResourceListUiState> = _uiState.asStateFlow()

    private var syncJob: Job? = null

    init {
        observeCachedRows()
        observeNamespaces()
        syncOnNamespaceChange()
        resyncOnReconnect()
    }

    fun onAction(action: ResourceListUiAction) {
        when (action) {
            ResourceListUiAction.Refresh -> sync()
            is ResourceListUiAction.SelectNamespace -> {
                selectedNamespace.value = action.namespace
                _uiState.update { it.copy(selectedNamespace = action.namespace) }
            }
        }
    }

    private fun observeCachedRows() {
        viewModelScope.launch(dispatcherProvider.main) {
            selectedNamespace
                .flatMapLatest { filter ->
                    combine(
                        rowsIn(presentation, namespaceScopeOf(filter)),
                        observeLastSynced(clusterId, presentation.kind),
                    ) { rows, lastSyncedAt -> CachedRows(rows, lastSyncedAt) }
                }
                .collect { cached ->
                    _uiState.update { state ->
                        state.copy(
                            rows = cached.rows,
                            lastSyncedAt = cached.lastSyncedAt,
                            isCacheLoaded = true,
                        )
                    }
                }
        }
    }

    private fun observeNamespaces() {
        viewModelScope.launch(dispatcherProvider.main) {
            getNamespaces(clusterId).collect { namespaces ->
                _uiState.update { it.copy(namespaces = namespaceFilterOptions(namespaces)) }
            }
        }
    }

    /**
     * The initial namespace is synced when the screen is shown, so only later
     * selections are synced here. A StateFlow only re-emits on change, so
     * re-selecting the same namespace does not sync again.
     */
    private fun syncOnNamespaceChange() {
        viewModelScope.launch(dispatcherProvider.main) {
            selectedNamespace.drop(1).collect { sync() }
        }
    }

    private fun resyncOnReconnect() {
        viewModelScope.launch(dispatcherProvider.main) {
            var wasOffline = false
            networkMonitor.isOnline.collect { online ->
                if (online && wasOffline) sync()
                wasOffline = !online
            }
        }
    }

    /** Replaces any sync still running, whose namespace may no longer be the visible one. */
    private fun sync() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update { it.copy(isSyncing = true, syncFailed = false) }
            val result = syncResources(clusterId, presentation.kind, namespaceScopeOf(selectedNamespace.value))
            _uiState.update { it.copy(isSyncing = false, syncFailed = result is Result.Error) }
        }
    }

    private fun <S : KubeObjectSummary> rowsIn(
        presentation: ResourcePresentation<S>,
        scope: NamespaceScope,
    ): Flow<List<ResourceRow>> =
        observeResources(clusterId, presentation.kind, scope).map { items -> items.map(presentation::row) }

    private data class CachedRows(val rows: List<ResourceRow>, val lastSyncedAt: Long?)
}
