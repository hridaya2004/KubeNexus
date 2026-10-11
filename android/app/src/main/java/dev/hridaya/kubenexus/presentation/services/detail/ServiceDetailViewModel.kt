package dev.hridaya.kubenexus.presentation.services.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.usecase.DescribeResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveResourcesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Offline-first detail screen for one Service.
 *
 * Reads cached service summary immediately from Room, while fetching live
 * describe details over the network in the background.
 * Automatically re-fetches when network connectivity is restored.
 */
@HiltViewModel(assistedFactory = ServiceDetailViewModel.Factory::class)
class ServiceDetailViewModel @AssistedInject constructor(
    @Assisted("serviceName") val serviceName: String,
    @Assisted("namespace") val namespace: String,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    private val describeResource: DescribeResourceUseCase,
    private val observeResources: ObserveResourcesUseCase,
    private val networkMonitor: NetworkMonitor? = null,
    private val dispatcherProvider: DispatcherProvider? = null,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("serviceName") serviceName: String,
            @Assisted("namespace") namespace: String,
        ): ServiceDetailViewModel
    }

    private val _uiState = MutableStateFlow(
        ServiceDetailUiState(
            serviceName = serviceName,
            namespace = namespace,
        ),
    )
    val uiState: StateFlow<ServiceDetailUiState> = _uiState.asStateFlow()

    private var wasOffline = false
    private var loadJob: Job? = null

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

    fun onAction(action: ServiceDetailUiAction) {
        when (action) {
            ServiceDetailUiAction.Refresh -> load()
            is ServiceDetailUiAction.ShowPortForwardDialog -> _uiState.update {
                it.copy(showPortForwardDialog = action.show)
            }
        }
    }

    /**
     * Reads cached summary from Room for instant offline display, then pulls
     * fresh describe details over the network.
     */
    private fun load() {
        val isExplicitRefresh = _uiState.value.service != null
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

            // Instant offline load from Room cache
            if (_uiState.value.service == null) {
                val cachedSummary = observeResources(clusterId, ResourceKinds.Services, NamespaceScope.Only(namespace))
                    .firstOrNull()
                    ?.firstOrNull { it.name == serviceName && it.namespace == namespace }

                if (cachedSummary != null) {
                    val initialDetails = ServiceDetails(
                        name = cachedSummary.name,
                        namespace = cachedSummary.namespace,
                        creationTimestampMillis = cachedSummary.creationTimestampMillis,
                        type = cachedSummary.type,
                        clusterIP = cachedSummary.clusterIP,
                        clusterIPs = if (cachedSummary.clusterIP.isNotBlank()) listOf(cachedSummary.clusterIP) else emptyList(),
                        externalIPs = emptyList(),
                        selector = emptyMap(),
                        ports = cachedSummary.ports,
                        labels = emptyMap(),
                        annotations = emptyMap(),
                        events = emptyList(),
                    )
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            service = state.service ?: initialDetails,
                            errorMessage = null,
                        )
                    }
                }
            }

            when (val result = getServiceDetails(clusterId, namespace, serviceName)) {
                is Result.Success -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        service = result.data,
                        lastRefreshedAt = System.currentTimeMillis(),
                        errorMessage = null,
                    )
                }

                is Result.Error -> _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = if (state.service == null) LOAD_ERROR_MESSAGE else null,
                    )
                }

                is Result.Loading -> Unit
            }
        }
    }

    /** Single integration point if the details use case signature drifts. */
    private suspend fun getServiceDetails(
        clusterId: String?,
        namespace: String,
        name: String,
    ): Result<ServiceDetails> = describeResource(clusterId, ResourceKinds.Services, namespace, name)

    private companion object {
        const val LOAD_ERROR_MESSAGE =
            "Couldn't load this service right now. Check that the cluster is reachable and try again."
    }
}
