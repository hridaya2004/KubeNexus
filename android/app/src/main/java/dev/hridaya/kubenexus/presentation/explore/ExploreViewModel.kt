package dev.hridaya.kubenexus.presentation.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.ResourceField
import dev.hridaya.kubenexus.domain.usecase.ExplainResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetAPIResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    private val getAPIResourcesUseCase: GetAPIResourcesUseCase,
    private val explainResourceUseCase: ExplainResourceUseCase,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    private val _events = Channel<ExploreUiEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var streamJob: Job? = null
    private var lastRefreshedJob: Job? = null
    private var explainJob: Job? = null
    private var refreshJob: Job? = null
    private var lastObservedClusterId: String? = null
    private var hasCheckedEmptyForCluster = false

    init {
        observeActiveCluster()
    }

    private fun observeActiveCluster() {
        viewModelScope.launch(dispatcherProvider.main) {
            getActiveClusterUseCase().collectLatest { cluster ->
                _uiState.update { it.copy(activeCluster = cluster) }
                subscribeToResources(cluster?.id)
                subscribeToLastRefreshed(cluster?.id)
            }
        }
    }

    private fun subscribeToResources(clusterId: String?) {
        streamJob?.cancel()
        if (lastObservedClusterId != clusterId) {
            lastObservedClusterId = clusterId
            hasCheckedEmptyForCluster = false
            // A refresh still running for the previous cluster must not land on this one.
            refreshJob?.cancel()
            _uiState.update { it.copy(isRefreshing = false, isLoading = false, errorMessage = null) }
        }
        streamJob = viewModelScope.launch(dispatcherProvider.main) {
            getAPIResourcesUseCase.getStream(clusterId).collectLatest { list ->
                _uiState.update { state ->
                    val (filtered, paged, hasMore) = updateFilterAndPaging(
                        resources = list,
                        query = state.searchQuery,
                        category = state.selectedCategory,
                        page = 1,
                        pageSize = state.pageSize,
                    )
                    state.copy(
                        resources = list,
                        filteredResources = filtered,
                        pagedResources = paged,
                        currentPage = 1,
                        hasMorePages = hasMore,
                    )
                }

                // Cache-first: only fetch from remote cluster if local cache is empty on initial observation
                if (!hasCheckedEmptyForCluster) {
                    hasCheckedEmptyForCluster = true
                    if (list.isEmpty()) {
                        refreshResources(clusterId)
                    }
                }
            }
        }
    }

    private fun subscribeToLastRefreshed(clusterId: String?) {
        lastRefreshedJob?.cancel()
        lastRefreshedJob = viewModelScope.launch(dispatcherProvider.main) {
            getAPIResourcesUseCase.getLastRefreshedStream(clusterId).collectLatest { timestamp ->
                _uiState.update { it.copy(lastRefreshedAt = timestamp) }
            }
        }
    }

    fun onAction(action: ExploreUiAction) {
        when (action) {
            is ExploreUiAction.OpenSearch -> {
                _uiState.update { it.copy(isSearchActive = true) }
            }

            is ExploreUiAction.CloseSearch -> {
                _uiState.update { state ->
                    val (filtered, paged, hasMore) = updateFilterAndPaging(
                        resources = state.resources,
                        query = "",
                        category = state.selectedCategory,
                        page = 1,
                        pageSize = state.pageSize,
                    )
                    state.copy(
                        isSearchActive = false,
                        searchQuery = "",
                        filteredResources = filtered,
                        pagedResources = paged,
                        currentPage = 1,
                        hasMorePages = hasMore,
                    )
                }
            }

            is ExploreUiAction.UpdateSearchQuery -> {
                _uiState.update { state ->
                    val (filtered, paged, hasMore) = updateFilterAndPaging(
                        resources = state.resources,
                        query = action.query,
                        category = state.selectedCategory,
                        page = 1,
                        pageSize = state.pageSize,
                    )
                    state.copy(
                        searchQuery = action.query,
                        filteredResources = filtered,
                        pagedResources = paged,
                        currentPage = 1,
                        hasMorePages = hasMore,
                    )
                }
            }

            is ExploreUiAction.SelectCategory -> {
                _uiState.update { state ->
                    val (filtered, paged, hasMore) = updateFilterAndPaging(
                        resources = state.resources,
                        query = state.searchQuery,
                        category = action.category,
                        page = 1,
                        pageSize = state.pageSize,
                    )
                    state.copy(
                        selectedCategory = action.category,
                        filteredResources = filtered,
                        pagedResources = paged,
                        currentPage = 1,
                        hasMorePages = hasMore,
                    )
                }
            }

            is ExploreUiAction.LoadNextPage -> {
                _uiState.update { state ->
                    if (!state.hasMorePages) return@update state
                    val nextPage = state.currentPage + 1
                    val paged = state.filteredResources.take(nextPage * state.pageSize)
                    state.copy(
                        currentPage = nextPage,
                        pagedResources = paged,
                        hasMorePages = paged.size < state.filteredResources.size,
                    )
                }
            }

            is ExploreUiAction.Refresh -> {
                refreshResources(_uiState.value.activeCluster?.id)
            }

            is ExploreUiAction.SelectResource -> {
                loadExplain(action.resource)
            }

            is ExploreUiAction.DismissExplain -> {
                explainJob?.cancel()
                _uiState.update {
                    it.copy(
                        selectedResource = null,
                        explainDetails = null,
                        fieldSearchQuery = "",
                        filteredFields = emptyList(),
                        explainError = null,
                    )
                }
            }

            is ExploreUiAction.UpdateFieldSearchQuery -> {
                _uiState.update { state ->
                    val fields = state.explainDetails?.fields ?: emptyList()
                    val filtered = filterFields(fields, action.query)
                    state.copy(
                        fieldSearchQuery = action.query,
                        filteredFields = filtered,
                    )
                }
            }

            is ExploreUiAction.RetryExplain -> {
                loadExplain(action.resource, forceRefresh = true)
            }

            is ExploreUiAction.CopyText -> {
                viewModelScope.launch {
                    _events.send(ExploreUiEvent.CopyToClipboard(action.text, action.label))
                    _events.send(ExploreUiEvent.ShowMessage("Copied ${action.label} to clipboard"))
                }
            }
        }
    }

    private fun refreshResources(clusterId: String?) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(dispatcherProvider.main) {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    isLoading = it.resources.isEmpty(),
                    errorMessage = null
                )
            }
            when (val result = getAPIResourcesUseCase.refresh(clusterId)) {
                is Result.Success -> {
                    _uiState.update { state ->
                        val (filtered, paged, hasMore) = updateFilterAndPaging(
                            resources = result.data,
                            query = state.searchQuery,
                            category = state.selectedCategory,
                            page = 1,
                            pageSize = state.pageSize,
                        )
                        state.copy(
                            resources = result.data,
                            filteredResources = filtered,
                            pagedResources = paged,
                            currentPage = 1,
                            hasMorePages = hasMore,
                            isRefreshing = false,
                            isLoading = false,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update { state ->
                        state.copy(
                            isRefreshing = false,
                            isLoading = false,
                            errorMessage = result.error.message,
                        )
                    }
                    _events.send(
                        ExploreUiEvent.ShowMessage(
                            "Couldn't refresh your resources. Check your connection and try again."
                        )
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun updateFilterAndPaging(
        resources: List<APIResource>,
        query: String,
        category: String,
        page: Int = 1,
        pageSize: Int = 30,
    ): Triple<List<APIResource>, List<APIResource>, Boolean> {
        val filtered = applyFilter(resources, query, category)
        val paged = filtered.take(page * pageSize)
        val hasMore = paged.size < filtered.size
        return Triple(filtered, paged, hasMore)
    }

    private fun loadExplain(resource: APIResource, forceRefresh: Boolean = false) {
        explainJob?.cancel()
        val clusterId = _uiState.value.activeCluster?.id

        explainJob = viewModelScope.launch(dispatcherProvider.main) {
            // Check cached schema from Room database
            val cached =
                explainResourceUseCase.getCached(clusterId, resource.name, resource.groupVersion)
            if (cached != null && !forceRefresh) {
                _uiState.update { state ->
                    state.copy(
                        selectedResource = resource,
                        explainDetails = cached,
                        filteredFields = filterFields(cached.fields, state.fieldSearchQuery),
                        isLoadingExplain = false,
                        explainError = null,
                        fieldSearchQuery = "",
                    )
                }
                return@launch
            }

            _uiState.update { state ->
                state.copy(
                    selectedResource = resource,
                    explainDetails = cached,
                    isLoadingExplain = true,
                    explainError = null,
                    fieldSearchQuery = "",
                    filteredFields = cached?.fields ?: emptyList(),
                )
            }

            // Fetch fresh from native / cluster and sync to Room database
            val result = explainResourceUseCase(
                clusterId,
                resource.name,
                resource.groupVersion,
                forceRefresh
            )
            when (result) {
                is Result.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            selectedResource = resource,
                            explainDetails = result.data,
                            filteredFields = filterFields(
                                result.data.fields,
                                state.fieldSearchQuery
                            ),
                            isLoadingExplain = false,
                            explainError = null,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update { state ->
                        if (state.explainDetails == null) {
                            state.copy(
                                isLoadingExplain = false,
                                explainError = result.error.message ?: "Failed to load explanation",
                            )
                        } else {
                            state.copy(isLoadingExplain = false)
                        }
                    }
                    _events.send(
                        ExploreUiEvent.ShowMessage(
                            "Couldn't load details for ${resource.kind}. Please try again in a moment.",
                        ),
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }


    private fun applyFilter(
        resources: List<APIResource>,
        query: String,
        category: String,
    ): List<APIResource> {
        val trimmed = query.trim().lowercase()
        return resources.filter { r ->
            val matchesCategory = when (category) {
                "All" -> true
                "Namespaced" -> r.namespaced
                "Cluster" -> !r.namespaced
                "Core (v1)" -> r.group.isEmpty() || r.groupVersion == "v1"
                "Apps" -> r.group.equals("apps", ignoreCase = true)
                "Batch" -> r.group.equals("batch", ignoreCase = true)
                "Networking" -> r.group.contains("networking", ignoreCase = true)
                else -> true
            }

            val matchesQuery = if (trimmed.isEmpty()) {
                true
            } else {
                r.name.lowercase().contains(trimmed) ||
                        r.kind.lowercase().contains(trimmed) ||
                        r.groupVersion.lowercase().contains(trimmed) ||
                        r.singularName.lowercase().contains(trimmed) ||
                        r.shortNames.any { it.lowercase().contains(trimmed) } ||
                        r.categories.any { it.lowercase().contains(trimmed) }
            }

            matchesCategory && matchesQuery
        }
    }

    private fun filterFields(fields: List<ResourceField>, query: String): List<ResourceField> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) return fields
        return fields.filter { field ->
            field.name.lowercase().contains(trimmed) ||
                    field.type.lowercase().contains(trimmed) ||
                    field.description.lowercase().contains(trimmed)
        }
    }
}
