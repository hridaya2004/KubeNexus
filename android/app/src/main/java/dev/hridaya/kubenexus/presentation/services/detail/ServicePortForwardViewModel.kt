package dev.hridaya.kubenexus.presentation.services.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.di.ApplicationScope
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionManager
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ResolveServiceForwardTargetUseCase
import dev.hridaya.kubenexus.presentation.portforward.ActivePortForward
import dev.hridaya.kubenexus.presentation.portforward.PortForwardStatus
import dev.hridaya.kubenexus.presentation.portforward.PortForwardUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ServicePortForwardViewModel.Factory::class)
class ServicePortForwardViewModel @AssistedInject constructor(
    @Assisted("serviceName") private val serviceName: String,
    @Assisted("namespace") private val namespace: String,
    private val sessionManager: PortForwardSessionManager,
    private val resolveServiceForwardTargetUseCase: ResolveServiceForwardTargetUseCase,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    @param:ApplicationScope private val externalScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("serviceName") serviceName: String,
            @Assisted("namespace") namespace: String,
        ): ServicePortForwardViewModel
    }

    /**
     * The active cluster as one snapshot, so the kubeconfig and the cluster id recorded
     * with a forward always belong together. Written on IO, read on main.
     */
    @Volatile
    private var activeCluster: Cluster? = null
    private val activeClusterId = MutableStateFlow<String?>(null)

    private val _uiState = MutableStateFlow(PortForwardUiState())
    val uiState: StateFlow<PortForwardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(dispatcherProvider.io) {
            getActiveClusterUseCase().collect { cluster ->
                activeCluster = cluster
                activeClusterId.value = cluster?.id
            }
        }

        viewModelScope.launch {
            // Forwards are tracked process-wide; only show the ones opened on this cluster.
            combine(sessionManager.sessions, activeClusterId) { allSessions, clusterId ->
                allSessions.filter { it.clusterId == clusterId }
            }.collect { allSessions ->
                val serviceForwards = allSessions
                    .filter {
                        it.namespace == namespace &&
                                it.targetName == serviceName &&
                                it.kind == PortForwardTargetKind.Service &&
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
                _uiState.update { it.copy(activeForwards = serviceForwards) }
            }
        }
    }

    fun start(service: ServiceDetails, localPort: Int, servicePort: Int) {
        if (_uiState.value.isStarting) return
        val cluster = activeCluster
        val kubeconfig = cluster?.rawKubeconfig.orEmpty()
        if (kubeconfig.isBlank()) {
            _uiState.update { it.copy(error = NO_ACTIVE_CLUSTER_MESSAGE) }
            return
        }

        _uiState.update { it.copy(isStarting = true, error = null) }

        externalScope.launch(dispatcherProvider.io) {
            when (val targetResult =
                resolveServiceForwardTargetUseCase(cluster?.id, service, servicePort)) {
                is Result.Success -> {
                    val target = targetResult.data
                    when (
                        val startResult = sessionManager.startServiceForward(
                            rawKubeconfig = kubeconfig,
                            namespace = namespace,
                            serviceName = serviceName,
                            localPort = localPort,
                            servicePort = servicePort,
                            targetPodName = target.podName,
                            targetPodPort = target.podPort,
                            clusterId = cluster?.id,
                        )
                    ) {
                        is Result.Success -> _uiState.update { it.copy(isStarting = false) }
                        is Result.Error -> _uiState.update {
                            it.copy(isStarting = false, error = startResult.error.message)
                        }

                        is Result.Loading -> Unit
                    }
                }

                is Result.Error -> _uiState.update {
                    it.copy(isStarting = false, error = targetResult.error.message)
                }

                is Result.Loading -> Unit
            }
        }
    }

    fun stop(handleId: String) {
        externalScope.launch(dispatcherProvider.io) {
            sessionManager.stop(handleId)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private companion object {
        const val NO_ACTIVE_CLUSTER_MESSAGE = "No active Kubernetes cluster configured."
    }
}
