package dev.hridaya.kubenexus.presentation.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.LogSanitizer
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.usecase.AddClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.CheckClusterHealthUseCase
import dev.hridaya.kubenexus.domain.usecase.ClearCachedDataUseCase
import dev.hridaya.kubenexus.domain.usecase.DeleteClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.DeleteNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetClustersUseCase
import dev.hridaya.kubenexus.domain.usecase.GetLastRefreshedUseCase
import dev.hridaya.kubenexus.domain.usecase.GetNamespacesUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodsUseCase
import dev.hridaya.kubenexus.domain.usecase.RefreshWorkloadsUseCase
import dev.hridaya.kubenexus.domain.usecase.SetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.TestClusterConnectionUseCase
import dev.hridaya.kubenexus.domain.usecase.UpdateClusterNameUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getClustersUseCase: GetClustersUseCase,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    private val getPodsUseCase: GetPodsUseCase,
    private val getNamespacesUseCase: GetNamespacesUseCase,
    private val getLastRefreshedUseCase: GetLastRefreshedUseCase,
    private val refreshWorkloadsUseCase: RefreshWorkloadsUseCase,
    private val addClusterUseCase: AddClusterUseCase,
    private val setActiveClusterUseCase: SetActiveClusterUseCase,
    private val deleteClusterUseCase: DeleteClusterUseCase,
    private val clearCachedDataUseCase: ClearCachedDataUseCase,
    private val deleteNamespaceUseCase: DeleteNamespaceUseCase,
    private val updateClusterNameUseCase: UpdateClusterNameUseCase,
    private val testClusterConnectionUseCase: TestClusterConnectionUseCase,
    private val checkClusterHealthUseCase: CheckClusterHealthUseCase,
    private val networkMonitor: NetworkMonitor,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** Only the active cluster's id, for screens that need to follow cluster switches. */
    val activeClusterId: StateFlow<String?> = _uiState
        .map { it.activeCluster?.id }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _selectedNamespace = MutableStateFlow("All Namespaces")
    private var lastSyncedClusterId: String? = null

    // At most one refresh and one health check run at a time. A newer one cancels the
    // older, and results are applied only while their cluster is still the active one, so a
    // slow answer from the previous cluster cannot overwrite the new cluster's state.
    private var refreshJob: Job? = null
    private var healthJob: Job? = null

    private fun isActiveCluster(clusterId: String) = _uiState.value.activeCluster?.id == clusterId

    private val _effects = Channel<HomeUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    init {
        observeNetworkConnectivity()
        observeLocalDatabase()
    }

    private fun observeNetworkConnectivity() {
        viewModelScope.launch(dispatcherProvider.main) {
            networkMonitor.isOnline.collect { online ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (online) {
                    val activeCluster = _uiState.value.activeCluster
                    if (activeCluster != null && wasOffline) {
                        performRefresh(
                            clusterId = activeCluster.id,
                            namespace = _selectedNamespace.value,
                            showLoading = false,
                        )
                    }
                } else {
                    _uiState.update {
                        if (it.activeCluster != null) {
                            it.copy(clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED)
                        } else {
                            it.copy(clusterConnectionStatus = ClusterConnectionStatus.OFFLINE)
                        }
                    }
                }
            }
        }
    }

    private fun observeLocalDatabase() {
        viewModelScope.launch(dispatcherProvider.main) {
            combine(
                getClustersUseCase(),
                getActiveClusterUseCase(),
                _selectedNamespace,
            ) { clusters, activeCluster, ns ->
                Triple(clusters, activeCluster, ns)
            }.flatMapLatest { (clusters, activeCluster, ns) ->
                val clusterId = activeCluster?.id
                combine(
                    getPodsUseCase(clusterId, ns),
                    getNamespacesUseCase(clusterId),
                    getLastRefreshedUseCase(clusterId),
                ) { pods, namespaces, lastRefreshed ->
                    LocalWorkloadData(
                        clusters = clusters,
                        activeCluster = activeCluster,
                        pods = pods,
                        namespaces = namespaces,
                        selectedNamespace = ns,
                        lastRefreshed = lastRefreshed,
                    )
                }.catch { t ->
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED,
                        )
                    }
                }
            }.catch { t ->
                // The cluster list itself failed (e.g. the database). Keep the app usable.
                Log.w(TAG, LogSanitizer.withStackTrace("Could not load clusters", t))
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            }.collect { data ->
                val status = when {
                    data.activeCluster == null -> ClusterConnectionStatus.OFFLINE
                    _uiState.value.isRefreshing || _uiState.value.isConnecting -> ClusterConnectionStatus.CONNECTING
                    _uiState.value.clusterConnectionStatus == ClusterConnectionStatus.DISCONNECTED -> ClusterConnectionStatus.DISCONNECTED
                    else -> ClusterConnectionStatus.CONNECTED
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        clusters = data.clusters,
                        activeCluster = data.activeCluster,
                        pods = data.pods,
                        availableNamespaces = if (data.namespaces.isNotEmpty()) data.namespaces else state.availableNamespaces,
                        selectedNamespace = data.selectedNamespace,
                        lastRefreshedAt = data.lastRefreshed ?: state.lastRefreshedAt,
                        clusterConnectionStatus = status,
                    )
                }

                if (data.activeCluster != null && lastSyncedClusterId != data.activeCluster.id) {
                    lastSyncedClusterId = data.activeCluster.id
                    checkActiveClusterHealth(data.activeCluster.id)
                    performRefresh(
                        data.activeCluster.id,
                        data.selectedNamespace,
                        showLoading = data.pods.isEmpty(),
                    )
                }
            }
        }
    }

    private fun checkActiveClusterHealth(clusterId: String) {
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED) }
            return
        }
        healthJob?.cancel()
        healthJob = viewModelScope.launch(dispatcherProvider.io) {
            val result = checkClusterHealthUseCase.checkHealth(clusterId)
            if (!isActiveCluster(clusterId)) return@launch
            when (result) {
                is Result.Success -> {
                    val status = if (result.data.livez && result.data.readyz) {
                        ClusterConnectionStatus.CONNECTED
                    } else {
                        ClusterConnectionStatus.DISCONNECTED
                    }
                    _uiState.update { it.copy(clusterConnectionStatus = status) }
                }

                is Result.Error -> {
                    _uiState.update { it.copy(clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED) }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private data class LocalWorkloadData(
        val clusters: List<Cluster>,
        val activeCluster: Cluster?,
        val pods: List<Pod>,
        val namespaces: List<String>,
        val selectedNamespace: String,
        val lastRefreshed: Long?,
    )

    fun onAction(action: HomeUiAction) {
        when (action) {
            is HomeUiAction.RefreshWorkloads -> {
                val activeClusterId = _uiState.value.activeCluster?.id
                performRefresh(
                    activeClusterId,
                    _uiState.value.selectedNamespace,
                    showLoading = true,
                )
            }

            is HomeUiAction.OpenClusterDrawer -> {
                _uiState.update { it.copy(showClusterDrawer = true) }
            }

            is HomeUiAction.DismissClusterDrawer -> {
                _uiState.update { it.copy(showClusterDrawer = false) }
            }

            is HomeUiAction.OpenFabActionSheet -> {
                _uiState.update { it.copy(showFabActionSheet = true) }
            }

            is HomeUiAction.DismissFabActionSheet -> {
                _uiState.update { it.copy(showFabActionSheet = false) }
            }

            is HomeUiAction.OpenAddClusterSheet -> {
                _uiState.update {
                    it.copy(
                        showAddClusterSheet = true,
                        showFabActionSheet = false,
                        showClusterDrawer = false,
                        kubeconfigInput = "",
                        customClusterName = "",
                        kubeconfigError = null,
                    )
                }
            }

            is HomeUiAction.DismissAddClusterSheet -> {
                if (!_uiState.value.isConnecting) {
                    _uiState.update { it.copy(showAddClusterSheet = false, kubeconfigError = null) }
                }
            }

            is HomeUiAction.KubeconfigInputChanged -> {
                _uiState.update { it.copy(kubeconfigInput = action.text, kubeconfigError = null) }
            }

            is HomeUiAction.ClusterNameChanged -> {
                _uiState.update { it.copy(customClusterName = action.name) }
            }

            is HomeUiAction.FileImported -> {
                val inferredName = action.fileName?.substringBeforeLast(".") ?: ""
                _uiState.update {
                    it.copy(
                        kubeconfigInput = action.content,
                        customClusterName = it.customClusterName.ifBlank { inferredName },
                        kubeconfigError = null,
                    )
                }
            }

            is HomeUiAction.FileImportFailed -> {
                _uiState.update { it.copy(kubeconfigError = action.message) }
            }

            is HomeUiAction.ConnectAndSaveSubmitted -> {
                connectAndSaveCluster()
            }

            is HomeUiAction.SelectClusterClicked -> {
                selectActiveCluster(action.clusterId)
            }

            is HomeUiAction.TestClusterConnectionClicked -> {
                testClusterConnection(action.clusterId)
            }

            is HomeUiAction.RequestEditCluster -> {
                _uiState.update { it.copy(editingCluster = action.cluster) }
            }

            is HomeUiAction.DismissEditCluster -> {
                _uiState.update { it.copy(editingCluster = null) }
            }

            is HomeUiAction.SaveClusterName -> {
                saveClusterName(action.clusterId, action.newName)
            }

            is HomeUiAction.RequestDeleteCluster -> {
                _uiState.update { it.copy(clusterToDelete = action.cluster) }
            }

            is HomeUiAction.DismissDeleteCluster -> {
                _uiState.update { it.copy(clusterToDelete = null) }
            }

            is HomeUiAction.ConfirmDeleteCluster -> {
                _uiState.update { it.copy(clusterToDelete = null) }
                deleteCluster(action.clusterId)
            }

            is HomeUiAction.ClearCachedData -> clearCachedData()

            is HomeUiAction.RequestDeleteNamespace -> {
                _uiState.update { it.copy(namespaceToDelete = action.namespace) }
            }

            is HomeUiAction.DismissDeleteNamespace -> {
                _uiState.update { it.copy(namespaceToDelete = null) }
            }

            is HomeUiAction.ConfirmDeleteNamespace -> {
                _uiState.update { it.copy(namespaceToDelete = null) }
                deleteNamespace(action.namespace)
            }

            is HomeUiAction.SelectPod -> {
                _uiState.update { it.copy(selectedPod = action.pod) }
            }

            is HomeUiAction.DismissPodDetails -> {
                _uiState.update { it.copy(selectedPod = null) }
            }

            is HomeUiAction.OpenNamespacePicker -> {
                _uiState.update { it.copy(showNamespacePicker = true) }
            }

            is HomeUiAction.DismissNamespacePicker -> {
                _uiState.update { it.copy(showNamespacePicker = false) }
            }

            is HomeUiAction.SelectNamespace -> {
                _selectedNamespace.value = action.namespace
                _uiState.update {
                    it.copy(
                        selectedNamespace = action.namespace,
                        showNamespacePicker = false,
                    )
                }
            }

            is HomeUiAction.DismissErrorDialog -> {
                _uiState.update { it.copy(errorDialogData = null) }
            }

            is HomeUiAction.CopyErrorClicked -> {
                viewModelScope.launch {
                    _effects.send(HomeUiEffect.ShowToast("Error details copied to clipboard"))
                }
            }

            is HomeUiAction.TriggerNoopAction -> {
                viewModelScope.launch {
                    _effects.send(HomeUiEffect.ShowToast(action.message))
                }
            }
        }
    }

    private fun performRefresh(clusterId: String?, namespace: String?, showLoading: Boolean) {
        if (clusterId == null) return

        _uiState.update {
            it.copy(
                isRefreshing = showLoading,
                clusterConnectionStatus = ClusterConnectionStatus.CONNECTING,
            )
        }

        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(dispatcherProvider.main) {
            val result = refreshWorkloadsUseCase(clusterId, namespace)
            if (!isActiveCluster(clusterId)) return@launch
            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            clusterConnectionStatus = ClusterConnectionStatus.CONNECTED,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED,
                        )
                    }
                    _effects.send(HomeUiEffect.ShowToast("Couldn't load your pods right now. Check that the cluster is reachable and try again."))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun connectAndSaveCluster() {
        val input = _uiState.value.kubeconfigInput.trim()
        if (input.isBlank()) {
            _uiState.update { it.copy(kubeconfigError = "Please paste or import a valid Kubeconfig.") }
            return
        }

        _uiState.update {
            it.copy(
                isConnecting = true,
                clusterConnectionStatus = ClusterConnectionStatus.CONNECTING,
                kubeconfigError = null,
            )
        }

        viewModelScope.launch(dispatcherProvider.main) {
            val result = addClusterUseCase(
                kubeconfigRaw = input,
                customName = _uiState.value.customClusterName.ifBlank { null },
                setAsActive = true,
            )

            when (result) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            clusterConnectionStatus = ClusterConnectionStatus.CONNECTED,
                            showAddClusterSheet = false,
                            kubeconfigInput = "",
                            customClusterName = "",
                        )
                    }
                    _effects.send(HomeUiEffect.ShowToast("Connected to cluster '${result.data.name}' successfully!"))
                    _effects.send(HomeUiEffect.NavigateToHome)
                }

                is Result.Error -> {
                    val rawMsg = result.error.message
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED,
                            errorDialogData = ErrorDialogData(
                                title = "Cluster Connection Failed",
                                errorMessage = "Unable to connect to Kubernetes cluster. Please verify the kubeconfig credentials and network connectivity.",
                                rawErrorTrace = rawMsg,
                            ),
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun selectActiveCluster(clusterId: String) {
        _uiState.update {
            it.copy(
                isConnecting = true,
                clusterConnectionStatus = ClusterConnectionStatus.CONNECTING,
            )
        }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = setActiveClusterUseCase(clusterId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            clusterConnectionStatus = ClusterConnectionStatus.CONNECTED,
                        )
                    }
                    _effects.send(HomeUiEffect.ShowToast("Active cluster updated successfully!"))
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isConnecting = false,
                            clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED,
                            errorDialogData = ErrorDialogData(
                                title = "Failed to Activate Cluster",
                                errorMessage = "Could not establish connection to the selected cluster.",
                                rawErrorTrace = result.error.message,
                            ),
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun testClusterConnection(clusterId: String) {
        _uiState.update {
            it.copy(
                testingClusterId = clusterId,
                clusterTestStatuses = it.clusterTestStatuses + (clusterId to ClusterTestStatus.TESTING),
            )
        }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = testClusterConnectionUseCase.testCluster(clusterId)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            testingClusterId = null,
                            clusterTestStatuses = it.clusterTestStatuses + (clusterId to ClusterTestStatus.HEALTHY),
                            clusterConnectionStatus = if (it.activeCluster?.id == clusterId) ClusterConnectionStatus.CONNECTED else it.clusterConnectionStatus,
                        )
                    }
                    _effects.send(HomeUiEffect.ShowToast("Connection successful: ${result.data}"))
                    delay(2500)
                    _uiState.update {
                        it.copy(
                            clusterTestStatuses = it.clusterTestStatuses - clusterId,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            testingClusterId = null,
                            clusterTestStatuses = it.clusterTestStatuses + (clusterId to ClusterTestStatus.UNHEALTHY),
                            clusterConnectionStatus = if (it.activeCluster?.id == clusterId) ClusterConnectionStatus.DISCONNECTED else it.clusterConnectionStatus,
                            errorDialogData = ErrorDialogData(
                                title = "Connection Check Failed",
                                errorMessage = "Failed to connect to cluster.",
                                rawErrorTrace = result.error.message,
                            ),
                        )
                    }
                    delay(2500)
                    _uiState.update {
                        it.copy(
                            clusterTestStatuses = it.clusterTestStatuses - clusterId,
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun saveClusterName(clusterId: String, newName: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = updateClusterNameUseCase(clusterId, newName)) {
                is Result.Success -> {
                    _uiState.update { it.copy(editingCluster = null) }
                    _effects.send(HomeUiEffect.ShowToast("Cluster renamed successfully!"))
                }

                is Result.Error -> {
                    _effects.send(HomeUiEffect.ShowToast("Couldn't rename the cluster. Please try again."))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun deleteCluster(clusterId: String) {
        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deleteClusterUseCase(clusterId)) {
                is Result.Success -> {
                    _effects.send(HomeUiEffect.ShowToast("Cluster removed"))
                }

                is Result.Error -> {
                    _effects.send(HomeUiEffect.ShowToast("Couldn't remove the cluster. Check your connection and try again."))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun clearCachedData() {
        viewModelScope.launch(dispatcherProvider.main) {
            when (clearCachedDataUseCase()) {
                is Result.Success -> _effects.send(
                    HomeUiEffect.ShowToast("Cached cluster data cleared. Your clusters are kept."),
                )

                is Result.Error -> _effects.send(
                    HomeUiEffect.ShowToast("Couldn't clear cached data. Please try again."),
                )

                is Result.Loading -> Unit
            }
        }
    }

    private fun deleteNamespace(namespace: String) {
        val activeClusterId = _uiState.value.activeCluster?.id ?: return
        _uiState.update { it.copy(isDeletingNamespace = true) }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deleteNamespaceUseCase(activeClusterId, namespace)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeletingNamespace = false,
                            selectedNamespace = if (it.selectedNamespace == namespace) "All Namespaces" else it.selectedNamespace,
                        )
                    }
                    if (_selectedNamespace.value == namespace) {
                        _selectedNamespace.value = "All Namespaces"
                    }
                    _effects.send(HomeUiEffect.ShowToast("Namespace deleted"))
                    performRefresh(
                        activeClusterId,
                        _selectedNamespace.value,
                        showLoading = false,
                    )
                }

                is Result.Error -> {
                    _uiState.update { it.copy(isDeletingNamespace = false) }
                    _effects.send(HomeUiEffect.ShowToast("Couldn't delete this namespace. It may still contain workloads, or the cluster is unreachable."))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}
