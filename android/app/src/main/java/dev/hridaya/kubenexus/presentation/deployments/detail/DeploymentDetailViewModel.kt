package dev.hridaya.kubenexus.presentation.deployments.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.usecase.DeleteResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.DescribeResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ListResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.RestartDeploymentUseCase
import dev.hridaya.kubenexus.domain.usecase.ScaleDeploymentUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Offline-first detail screen for one Deployment.
 *
 * Reads cached deployment summary immediately from Room, while fetching live
 * describe details and associated pods over the network in the background.
 * Supports scaling, rolling restart, and deletion with instant cache invalidation.
 */
@HiltViewModel(assistedFactory = DeploymentDetailViewModel.Factory::class)
class DeploymentDetailViewModel @AssistedInject constructor(
    @Assisted("deploymentName") val deploymentName: String,
    @Assisted("namespace") val namespace: String,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    private val describeResource: DescribeResourceUseCase,
    private val listResources: ListResourcesUseCase,
    private val observeResources: ObserveResourcesUseCase,
    private val deleteResource: DeleteResourceUseCase,
    private val scaleDeploymentUseCase: ScaleDeploymentUseCase,
    private val restartDeploymentUseCase: RestartDeploymentUseCase,
    private val networkMonitor: NetworkMonitor? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("deploymentName") deploymentName: String,
            @Assisted("namespace") namespace: String,
        ): DeploymentDetailViewModel
    }

    private val _uiState = MutableStateFlow(
        DeploymentDetailUiState(
            deploymentName = deploymentName,
            namespace = namespace,
        ),
    )
    val uiState: StateFlow<DeploymentDetailUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private val _effects = Channel<DeploymentDetailUiEffect>(Channel.BUFFERED)
    val effects: Flow<DeploymentDetailUiEffect> = _effects.receiveAsFlow()

    private var wasOffline = false

    init {
        observeNetwork()
        load()
    }

    private fun observeNetwork() {
        val monitor = networkMonitor ?: return
        val dispatcher = dispatcherProvider?.main ?: Dispatchers.Main.immediate
        viewModelScope.launch(dispatcher) {
            monitor.isOnline.collect { online ->
                if (online && wasOffline) {
                    load()
                }
                wasOffline = !online
            }
        }
    }

    fun onAction(action: DeploymentDetailUiAction) {
        when (action) {
            DeploymentDetailUiAction.Refresh -> load()
            DeploymentDetailUiAction.OpenScaleDialog -> {
                val currentDesired = _uiState.value.details?.desiredReplicas
                    ?: _uiState.value.deployment?.desiredReplicas
                    ?: 1
                _uiState.update { it.copy(showScaleDialog = true, scaleInput = currentDesired) }
            }

            DeploymentDetailUiAction.DismissScaleDialog -> {
                _uiState.update { it.copy(showScaleDialog = false) }
            }

            is DeploymentDetailUiAction.ScaleInputChanged -> {
                _uiState.update { it.copy(scaleInput = action.replicas) }
            }

            DeploymentDetailUiAction.ConfirmScale -> performScale()
            DeploymentDetailUiAction.OpenRestartDialog -> {
                _uiState.update { it.copy(showRestartDialog = true) }
            }

            DeploymentDetailUiAction.DismissRestartDialog -> {
                _uiState.update { it.copy(showRestartDialog = false) }
            }

            DeploymentDetailUiAction.ConfirmRestart -> performRestart()
            DeploymentDetailUiAction.OpenDeleteDialog -> {
                _uiState.update { it.copy(showDeleteDialog = true) }
            }

            DeploymentDetailUiAction.DismissDeleteDialog -> {
                _uiState.update { it.copy(showDeleteDialog = false) }
            }

            DeploymentDetailUiAction.ConfirmDelete -> performDelete()
            DeploymentDetailUiAction.DismissMutationError -> {
                _uiState.update { it.copy(mutationErrorMessage = null) }
            }
        }
    }

    private fun load() {
        val isExplicitRefresh = _uiState.value.deployment != null
        _uiState.update { state ->
            state.copy(
                isRefreshing = isExplicitRefresh,
                isLoading = !isExplicitRefresh && state.isLoading,
                errorMessage = null,
            )
        }
        val dispatcher = dispatcherProvider?.main ?: Dispatchers.Main.immediate
        loadJob?.cancel()
        loadJob = viewModelScope.launch(dispatcher) {
            val clusterId = getActiveClusterUseCase().firstOrNull()?.id
            val summaryJob = launch { loadSummary(clusterId) }
            val detailsJob = launch { loadDetails(clusterId) }
            summaryJob.join()
            detailsJob.join()
            _uiState.update { state ->
                state.copy(
                    isRefreshing = false,
                    lastRefreshedAt = if (state.deployment != null) System.currentTimeMillis() else state.lastRefreshedAt,
                )
            }
        }
    }

    private suspend fun loadSummary(clusterId: String?) {
        val cachedSummary =
            observeResources(clusterId, ResourceKinds.Deployments, NamespaceScope.Only(namespace))
                .firstOrNull()
                ?.firstOrNull { it.name == deploymentName && it.namespace == namespace }
        if (cachedSummary != null) {
            _uiState.update { state ->
                state.copy(isLoading = false, deployment = cachedSummary, errorMessage = null)
            }
        } else {
            _uiState.update { state ->
                state.copy(isLoading = state.deployment == null, errorMessage = null)
            }
        }

        when (val result =
            listResources(clusterId, ResourceKinds.Deployments, NamespaceScope.Only(namespace))) {
            is Result.Success -> {
                val match = result.data.firstOrNull { candidate ->
                    candidate.name == deploymentName && candidate.namespace == namespace
                }
                _uiState.update { state ->
                    if (match != null) {
                        state.copy(isLoading = false, deployment = match, errorMessage = null)
                    } else if (state.deployment == null) {
                        state.copy(
                            isLoading = false,
                            deployment = null,
                            errorMessage =
                                "Deployment \"$deploymentName\" was not found in namespace " +
                                        "\"$namespace\". It may have been deleted or renamed.",
                        )
                    } else {
                        state.copy(isLoading = false)
                    }
                }
            }

            is Result.Error -> {
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        errorMessage = if (state.deployment == null) LOAD_ERROR_MESSAGE else null,
                    )
                }
                if (_uiState.value.deployment != null) {
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar(STALE_REFRESH_MESSAGE))
                }
            }

            is Result.Loading -> Unit
        }
    }

    private suspend fun loadDetails(clusterId: String?) {
        _uiState.update { state ->
            state.copy(isDetailsLoading = state.details == null, detailsErrorMessage = null)
        }
        when (val result =
            describeResource(clusterId, ResourceKinds.Deployments, namespace, deploymentName)) {
            is Result.Success -> {
                val details = result.data
                _uiState.update { state ->
                    state.copy(
                        isDetailsLoading = false,
                        details = details,
                        detailsErrorMessage = null,
                    )
                }
                loadPodsForSelector(clusterId, details.selectorMatchLabels)
            }

            is Result.Error -> _uiState.update { state ->
                state.copy(
                    isDetailsLoading = false,
                    detailsErrorMessage = if (state.deployment == null) DETAILS_ERROR_MESSAGE else null,
                )
            }

            is Result.Loading -> Unit
        }
    }

    private suspend fun loadPodsForSelector(clusterId: String?, selectorMap: Map<String, String>) {
        if (selectorMap.isEmpty()) {
            return
        }
        _uiState.update { it.copy(isPodsLoading = true, podsErrorMessage = null) }
        val podsResult = listResources(
            clusterId = clusterId,
            kind = ResourceKinds.Pods,
            scope = NamespaceScope.Only(namespace),
            matchLabels = selectorMap,
        )
        when (podsResult) {
            is Result.Success -> _uiState.update {
                it.copy(
                    isPodsLoading = false,
                    associatedPods = podsResult.data,
                    podsErrorMessage = null
                )
            }

            is Result.Error -> _uiState.update {
                it.copy(isPodsLoading = false, podsErrorMessage = podsResult.error.message)
            }

            is Result.Loading -> Unit
        }
    }

    private fun performScale() {
        if (_uiState.value.isMutating) return
        val targetReplicas = _uiState.value.scaleInput
        _uiState.update {
            it.copy(
                showScaleDialog = false,
                isMutating = true,
                mutationErrorMessage = null,
            )
        }
        viewModelScope.launch {
            val clusterId = getActiveClusterUseCase().firstOrNull()?.id
            val result =
                scaleDeploymentUseCase(clusterId, namespace, deploymentName, targetReplicas)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar("Scaled \"$deploymentName\" to $targetReplicas replicas"))
                    load()
                }

                is Result.Error -> {
                    val message = result.error.message
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            mutationErrorMessage = message,
                        )
                    }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar(message))
                }

                else -> {
                    _uiState.update { it.copy(isMutating = false) }
                }
            }
        }
    }

    private fun performRestart() {
        if (_uiState.value.isMutating) return
        _uiState.update {
            it.copy(
                showRestartDialog = false,
                isMutating = true,
                mutationErrorMessage = null,
            )
        }
        viewModelScope.launch {
            val clusterId = getActiveClusterUseCase().firstOrNull()?.id
            val result = restartDeploymentUseCase(clusterId, namespace, deploymentName)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar("Initiated rollout restart for \"$deploymentName\""))
                    load()
                }

                is Result.Error -> {
                    val message = result.error.message
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            mutationErrorMessage = message,
                        )
                    }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar(message))
                }

                else -> {
                    _uiState.update { it.copy(isMutating = false) }
                }
            }
        }
    }

    private fun performDelete() {
        if (_uiState.value.isMutating) return
        _uiState.update {
            it.copy(
                showDeleteDialog = false,
                isMutating = true,
                mutationErrorMessage = null,
            )
        }
        viewModelScope.launch {
            val clusterId = getActiveClusterUseCase().firstOrNull()?.id
            val result =
                deleteResource(clusterId, ResourceKinds.Deployments, namespace, deploymentName)
            when (result) {
                is Result.Success -> {
                    _uiState.update { it.copy(isMutating = false) }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar("Deleted deployment \"$deploymentName\""))
                    _effects.send(DeploymentDetailUiEffect.NavigateBack)
                }

                is Result.Error -> {
                    val message = result.error.message
                    _uiState.update {
                        it.copy(
                            isMutating = false,
                            mutationErrorMessage = message,
                        )
                    }
                    _effects.send(DeploymentDetailUiEffect.ShowSnackbar(message))
                }

                else -> {
                    _uiState.update { it.copy(isMutating = false) }
                }
            }
        }
    }

    private companion object {
        const val LOAD_ERROR_MESSAGE =
            "Couldn't load this deployment right now. Check that the cluster is reachable and try again."
        const val STALE_REFRESH_MESSAGE = "Couldn't refresh. Showing the last known state."

        const val DETAILS_ERROR_MESSAGE =
            "Couldn't load the describe details for this deployment."
    }
}
