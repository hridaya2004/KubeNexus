package dev.hridaya.kubenexus.presentation.portforward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.di.ApplicationScope
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ObservePortForwardSessionsUseCase
import dev.hridaya.kubenexus.domain.usecase.StartPodPortForwardUseCase
import dev.hridaya.kubenexus.domain.usecase.StopPortForwardUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = PortForwardViewModel.Factory::class)
class PortForwardViewModel @AssistedInject constructor(
    @Assisted("podName") private val podName: String,
    @Assisted("namespace") private val namespace: String,
    observePortForwardSessions: ObservePortForwardSessionsUseCase,
    private val startPodPortForward: StartPodPortForwardUseCase,
    private val stopPortForward: StopPortForwardUseCase,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    @param:ApplicationScope private val externalScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("podName") podName: String,
            @Assisted("namespace") namespace: String,
        ): PortForwardViewModel
    }

    private val activeClusterId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(PortForwardUiState())
    val uiState: StateFlow<PortForwardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            getActiveClusterUseCase().collect { cluster -> activeClusterId.value = cluster?.id }
        }

        viewModelScope.launch {
            // Forwards are tracked process-wide; only show the ones opened on this cluster.
            combine(observePortForwardSessions(), activeClusterId) { allSessions, clusterId ->
                allSessions.filter { it.clusterId == clusterId }
            }.collect { allSessions ->
                val podForwards = allSessions
                    .filter {
                        it.namespace == namespace &&
                                (it.targetName == podName || it.podName == podName) &&
                                it.kind == PortForwardTargetKind.Pod &&
                                it.isActive
                    }
                    .map { session ->
                        ActivePortForward(
                            handleId = session.handleId,
                            namespace = session.namespace,
                            podName = session.podName ?: session.targetName,
                            localPort = session.localPort,
                            remotePort = session.remotePort,
                            status = when (session.status) {
                                PortForwardSessionStatus.STARTING -> PortForwardStatus.STARTING
                                PortForwardSessionStatus.READY -> PortForwardStatus.READY
                                PortForwardSessionStatus.ERROR -> PortForwardStatus.ERROR
                                PortForwardSessionStatus.STOPPED -> PortForwardStatus.ERROR
                            },
                            message = session.message,
                        )
                    }
                _uiState.update { it.copy(activeForwards = podForwards) }
            }
        }
    }

    fun onAction(action: PortForwardUiAction) {
        when (action) {
            is PortForwardUiAction.StartForward -> start(action.localPort, action.remotePort)
            is PortForwardUiAction.StopForward -> stop(action.handleId)
            PortForwardUiAction.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    /** Opens a tunnel 127.0.0.1:[localPort] -> pod:[remotePort]. */
    fun start(localPort: Int, remotePort: Int) {
        if (_uiState.value.isStarting) return
        val clusterId = activeClusterId.value
        if (clusterId == null) {
            _uiState.update { it.copy(error = NO_ACTIVE_CLUSTER_MESSAGE) }
            return
        }

        _uiState.update { it.copy(isStarting = true, error = null) }

        // Not viewModelScope: leaving the screen mid-start would leave a tunnel nothing tracks.
        externalScope.launch(dispatcherProvider.io) {
            when (
                val result = startPodPortForward(
                    clusterId = clusterId,
                    namespace = namespace,
                    podName = podName,
                    localPort = localPort,
                    remotePort = remotePort,
                )
            ) {
                is Result.Success -> _uiState.update { it.copy(isStarting = false) }
                is Result.Error -> _uiState.update {
                    it.copy(isStarting = false, error = result.error.message)
                }

                is Result.Loading -> Unit
            }
        }
    }

    /** Closes one tunnel. */
    fun stop(handleId: String) {
        externalScope.launch(dispatcherProvider.io) {
            stopPortForward(handleId)
        }
    }

    private companion object {
        const val NO_ACTIVE_CLUSTER_MESSAGE = "No active Kubernetes cluster configured."
    }
}
