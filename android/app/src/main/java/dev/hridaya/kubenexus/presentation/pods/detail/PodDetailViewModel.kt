package dev.hridaya.kubenexus.presentation.pods.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.usecase.CheckClusterHealthUseCase
import dev.hridaya.kubenexus.domain.usecase.DeletePodUseCase
import dev.hridaya.kubenexus.domain.usecase.DescribePodUseCase
import dev.hridaya.kubenexus.domain.usecase.ExecPodCommandUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodLogsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodMetricsUseCase
import dev.hridaya.kubenexus.domain.usecase.StartExecSessionUseCase
import dev.hridaya.kubenexus.domain.usecase.StartPodTerminalUseCase
import dev.hridaya.kubenexus.domain.usecase.StreamPodLogsUseCase
import dev.hridaya.kubenexus.presentation.pods.components.terminal.GhosttyTerminalEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val METRICS_POLL_INTERVAL_MS = 5_000L

/**
 * Backoff ceiling for a metrics endpoint that keeps failing. metrics-server is
 * optional, so a cluster without it returns 404 on every poll; retrying at the
 * normal cadence forever would waste battery and data for no possible benefit.
 */
private const val METRICS_MAX_BACKOFF_MS = 160_000L

@HiltViewModel(assistedFactory = PodDetailViewModel.Factory::class)
class PodDetailViewModel @AssistedInject constructor(
    @Assisted("podName") private val podName: String,
    @Assisted("namespace") private val namespace: String,
    private val getActiveClusterUseCase: GetActiveClusterUseCase,
    private val describePodUseCase: DescribePodUseCase,
    private val getPodMetricsUseCase: GetPodMetricsUseCase,
    private val getPodLogsUseCase: GetPodLogsUseCase,
    private val streamPodLogsUseCase: StreamPodLogsUseCase,
    private val deletePodUseCase: DeletePodUseCase,
    private val execPodCommandUseCase: ExecPodCommandUseCase,
    private val startPodTerminalUseCase: StartPodTerminalUseCase,
    private val startExecSessionUseCase: StartExecSessionUseCase,
    private val checkClusterHealthUseCase: CheckClusterHealthUseCase,
    private val networkMonitor: NetworkMonitor,
    private val dispatcherProvider: DispatcherProvider,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(
            @Assisted("podName") podName: String,
            @Assisted("namespace") namespace: String,
        ): PodDetailViewModel
    }

    val terminalEngine = GhosttyTerminalEngine()

    private val _uiState = MutableStateFlow(
        PodDetailUiState(
            podName = podName,
            namespace = namespace,
        ),
    )
    val uiState: StateFlow<PodDetailUiState> = _uiState.asStateFlow()

    private val _effects = Channel<PodDetailUiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    private var activeClusterId: String? = null
    private var streamJob: Job? = null
    private var metricsJob: Job? = null
    private var activeTerminalSession: TerminalSession? = null

    init {
        terminalEngine.initialize(80, 24)
        observeNetwork()
        loadClusterAndDescribe()
    }

    /**
     * Starts sampling pod usage into a rolling buffer covering the widest
     * selectable range. Poll cadence is fixed; the dropdown only changes how much
     * history the chart shows.
     *
     * Driven by the screen's lifecycle rather than started in `init`, because a
     * ViewModel outlives the UI being visible: polling from `init` continued
     * while the app sat in the background.
     */
    fun startMetricsPolling() {
        if (metricsJob?.isActive == true) return
        metricsJob = viewModelScope.launch(dispatcherProvider.io) {
            var backoffMs = METRICS_POLL_INTERVAL_MS
            while (isActive) {
                val shouldPoll = _uiState.value.isOnline && _uiState.value.podDetails != null
                if (shouldPoll) {
                    backoffMs = if (fetchMetricsSample()) {
                        METRICS_POLL_INTERVAL_MS
                    } else {
                        (backoffMs * 2).coerceAtMost(METRICS_MAX_BACKOFF_MS)
                    }
                }
                delay(if (shouldPoll) backoffMs else METRICS_POLL_INTERVAL_MS)
            }
        }
    }

    /** Suspends sampling while the screen is not visible. */
    fun stopMetricsPolling() {
        metricsJob?.cancel()
        metricsJob = null
    }

    /** Returns true when the endpoint responded, regardless of whether it had a sample. */
    private suspend fun fetchMetricsSample(): Boolean {
        val clusterId = activeClusterId ?: return false
        return when (val result = getPodMetricsUseCase.forPod(clusterId, namespace, podName)) {
            is Result.Success -> {
                result.data?.let { sample ->
                    val cutoff = System.currentTimeMillis() - MetricsRange.MINUTES_5.durationMs
                    _uiState.update { state ->
                        state.copy(
                            metricsSamples = (state.metricsSamples + sample)
                                .filter { it.timestampMillis >= cutoff }
                                .sortedBy { it.timestampMillis },
                        )
                    }
                }
                _uiState.update { it.copy(isLoadingMetrics = false) }
                true
            }

            is Result.Error -> {
                _uiState.update { it.copy(isLoadingMetrics = false) }
                false
            }

            is Result.Loading -> false
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch(dispatcherProvider.main) {
            networkMonitor.isOnline.collect { online ->
                val wasOffline = !_uiState.value.isOnline
                _uiState.update { it.copy(isOnline = online) }
                if (online) {
                    if (wasOffline) {
                        fetchDescribe()
                        if (_uiState.value.selectedTab == PodDetailTab.LOGS && !_uiState.value.isStreamingLogs) {
                            fetchLogs()
                        }
                    }
                } else {
                    if (_uiState.value.isTerminalActive) {
                        _uiState.update {
                            it.copy(
                                isTerminalActive = false,
                                terminalLines = it.terminalLines + TerminalLine(
                                    text = "[Network disconnected - terminal session closed]",
                                    type = TerminalLineType.SYSTEM,
                                ),
                            )
                        }
                        stopTerminal()
                    }
                    if (_uiState.value.isStreamingLogs) {
                        _uiState.update {
                            it.copy(
                                isStreamingLogs = false,
                                logs = it.logs + "[Network disconnected - log stream stopped]",
                            )
                        }
                        stopStreaming()
                    }
                }
            }
        }
    }

    private fun loadClusterAndDescribe() {
        viewModelScope.launch(dispatcherProvider.main) {
            getActiveClusterUseCase().collect { cluster ->
                activeClusterId = cluster?.id
                _uiState.update { it.copy(clusterId = cluster?.id) }
                if (cluster != null) {
                    checkClusterHealth(cluster.id)
                    fetchDescribe()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            clusterConnectionStatus = ClusterConnectionStatus.OFFLINE,
                            errorMessage = "No active Kubernetes cluster configured.",
                        )
                    }
                }
            }
        }
    }

    private fun checkClusterHealth(clusterId: String) {
        if (!_uiState.value.isOnline) {
            _uiState.update { it.copy(clusterConnectionStatus = ClusterConnectionStatus.DISCONNECTED) }
            return
        }
        viewModelScope.launch(dispatcherProvider.io) {
            val status = when (val result = checkClusterHealthUseCase.checkHealth(clusterId)) {
                is Result.Success -> {
                    if (result.data.livez && result.data.readyz) {
                        ClusterConnectionStatus.CONNECTED
                    } else {
                        ClusterConnectionStatus.DISCONNECTED
                    }
                }

                is Result.Error -> ClusterConnectionStatus.DISCONNECTED
                is Result.Loading -> ClusterConnectionStatus.CONNECTING
            }
            _uiState.update { it.copy(clusterConnectionStatus = status) }
        }
    }

    fun onAction(action: PodDetailUiAction) {
        when (action) {
            is PodDetailUiAction.SelectMetricsRange -> {
                _uiState.update { it.copy(metricsRange = action.range) }
            }

            is PodDetailUiAction.RefreshDescribe -> {
                if (activeClusterId == null) {
                    loadClusterAndDescribe()
                } else {
                    fetchDescribe(isRefresh = true)
                }
            }

            is PodDetailUiAction.SelectTab -> {
                _uiState.update { it.copy(selectedTab = action.tab) }
                if (action.tab == PodDetailTab.LOGS && _uiState.value.logs.isEmpty()) {
                    fetchLogs()
                }
            }

            is PodDetailUiAction.SelectContainer -> {
                _uiState.update { it.copy(selectedContainer = action.containerName) }
                if (_uiState.value.isStreamingLogs) {
                    startStreaming()
                } else if (_uiState.value.selectedTab == PodDetailTab.LOGS) {
                    fetchLogs()
                }
            }

            is PodDetailUiAction.SetTailLines -> {
                _uiState.update { it.copy(tailLines = action.tailLines) }
                if (_uiState.value.isStreamingLogs) {
                    startStreaming()
                } else if (_uiState.value.selectedTab == PodDetailTab.LOGS) {
                    fetchLogs()
                }
            }

            is PodDetailUiAction.FetchLogs -> {
                stopStreaming()
                fetchLogs()
            }

            is PodDetailUiAction.FetchAllLogs -> {
                stopStreaming()
                _uiState.update { it.copy(logs = emptyList()) }
                fetchLogs(overrideTail = null)
            }

            is PodDetailUiAction.StartStreamingLogs -> {
                startStreaming()
            }

            is PodDetailUiAction.StopStreamingLogs -> {
                stopStreaming()
            }

            is PodDetailUiAction.ClearLogs -> {
                _uiState.update { it.copy(logs = emptyList()) }
            }

            is PodDetailUiAction.UpdateExecInput -> {
                _uiState.update { it.copy(execInputText = action.input) }
            }

            is PodDetailUiAction.ExecuteCommand -> {
                handleExecuteCommand(action.command)
            }

            is PodDetailUiAction.StartInteractiveTerminal -> {
                startTerminal(action.shell)
            }

            is PodDetailUiAction.StopInteractiveTerminal -> {
                stopTerminal()
            }

            is PodDetailUiAction.SendTerminalInput -> {
                sendInputToTerminal(action.input)
            }

            is PodDetailUiAction.ClearTerminal -> {
                // Keeps the current grid size; the canvas only re-sends it when it changes.
                terminalEngine.initialize()
                _uiState.update { it.copy(terminalLines = emptyList()) }
            }

            is PodDetailUiAction.ShowDeleteDialog -> {
                _uiState.update { it.copy(showDeleteConfirmDialog = action.show) }
            }

            is PodDetailUiAction.ConfirmDeletePod -> {
                deletePod()
            }
        }
    }

    private fun fetchDescribe(isRefresh: Boolean = false) {
        val cid = activeClusterId
        if (cid == null) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = "No active Kubernetes cluster configured.",
                )
            }
            return
        }
        _uiState.update {
            if (isRefresh) {
                it.copy(isRefreshing = true, errorMessage = null)
            } else {
                it.copy(
                    isLoading = it.podDetails == null,
                    isRefreshing = false,
                    errorMessage = null,
                )
            }
        }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = describePodUseCase(cid, namespace, podName)) {
                is Result.Success -> {
                    val details = result.data
                    val defaultContainer = details.containers.firstOrNull()?.name
                        ?: details.initContainers.firstOrNull()?.name
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            lastRefreshedAt = System.currentTimeMillis(),
                            podDetails = details,
                            selectedContainer = it.selectedContainer ?: defaultContainer,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = result.error.message,
                        )
                    }
                    _effects.send(PodDetailUiEffect.ShowToast("Failed to describe pod: ${result.error.message}"))
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun fetchLogs(overrideTail: Long? = _uiState.value.tailLines) {
        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer
        _uiState.update { it.copy(isLoadingLogs = true) }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result =
                getPodLogsUseCase(cid, namespace, podName, container, overrideTail)) {
                is Result.Success -> {
                    val lines = result.data.lines()
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            logs = lines,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            logs = listOf("Error fetching logs: ${result.error.message}"),
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun startStreaming() {
        stopStreaming()
        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer
        val tail = _uiState.value.tailLines

        _uiState.update {
            it.copy(
                logs = emptyList(),
                isStreamingLogs = true,
                isLoadingLogs = false,
            )
        }

        streamJob = viewModelScope.launch(dispatcherProvider.main) {
            streamPodLogsUseCase(cid, namespace, podName, container, tail)
                .onStart {
                    val tailDesc = if (tail != null && tail > 0) " (tail $tail lines)" else ""
                    _uiState.update { it.copy(logs = listOf("[Streaming logs initiated for container '${container ?: "default"}'$tailDesc]...")) }
                }
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            isStreamingLogs = false,
                            logs = it.logs + "[Log stream closed: ${t.message}]",
                        )
                    }
                }
                .collect { line ->
                    _uiState.update {
                        it.copy(logs = it.logs + line)
                    }
                }
        }
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(isStreamingLogs = false) }
    }

    private fun handleExecuteCommand(cmd: String) {
        val command = cmd.trim()
        if (command.isBlank()) return

        _uiState.update { it.copy(execInputText = "") }

        if (_uiState.value.isTerminalActive && activeTerminalSession != null) {
            sendInputToTerminal(command)
            return
        }

        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer ?: "default"

        _uiState.update {
            it.copy(
                isExecutingCommand = true,
                terminalLines = it.terminalLines + TerminalLine(
                    text = "$ $command",
                    type = TerminalLineType.INPUT,
                ),
            )
        }

        viewModelScope.launch(dispatcherProvider.main) {
            when (
                val result =
                    execPodCommandUseCase(cid, namespace, podName, container, command, "")
            ) {
                is Result.Success -> {
                    val execResult = result.data
                    val newLines = mutableListOf<TerminalLine>()
                    val stdout = execResult.stdout
                    val stderr = execResult.stderr

                    if (stdout.isNotBlank()) {
                        stdout.lines().forEach { line ->
                            newLines.add(TerminalLine(text = line, type = TerminalLineType.STDOUT))
                        }
                    }
                    if (stderr.isNotBlank()) {
                        stderr.lines().forEach { line ->
                            newLines.add(TerminalLine(text = line, type = TerminalLineType.STDERR))
                        }
                    }
                    if (stdout.isBlank() && stderr.isBlank()) {
                        newLines.add(
                            TerminalLine(
                                text = "[Exit code 0]",
                                type = TerminalLineType.SYSTEM,
                            ),
                        )
                    }

                    _uiState.update {
                        it.copy(
                            isExecutingCommand = false,
                            terminalLines = it.terminalLines + newLines,
                        )
                    }
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isExecutingCommand = false,
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "Error: ${result.error.message}",
                                type = TerminalLineType.ERROR,
                            ),
                        )
                    }
                }

                is Result.Loading -> Unit
            }
        }
    }

    private fun startTerminal(preferredShell: String? = null) {
        stopTerminal()
        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer ?: "default"

        _uiState.update {
            it.copy(
                isTerminalActive = true,
                activeShellCommand = preferredShell ?: "bash",
                terminalLines = it.terminalLines + TerminalLine(
                    text = "[Attaching interactive shell on container '$container' ...]",
                    type = TerminalLineType.SYSTEM,
                ),
            )
        }

        viewModelScope.launch(dispatcherProvider.main) {
            if (preferredShell != null) {
                val success = tryAttachExec(cid, container, preferredShell)
                if (!success) {
                    tryAttachDefaultTerminal(cid, container)
                }
            } else {
                val bashSuccess = tryAttachExec(cid, container, "/bin/bash")
                if (!bashSuccess) {
                    _uiState.update {
                        it.copy(
                            activeShellCommand = "sh",
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[bash not available, falling back to /bin/sh ...]",
                                type = TerminalLineType.SYSTEM,
                            ),
                        )
                    }
                    val shSuccess = tryAttachExec(cid, container, "/bin/sh")
                    if (!shSuccess) {
                        tryAttachDefaultTerminal(cid, container)
                    }
                }
            }
        }
    }

    private suspend fun tryAttachExec(
        clusterId: String,
        container: String,
        command: String
    ): Boolean {
        var hadFatalError = false
        val sessionResult = startExecSessionUseCase(
            clusterId = clusterId,
            namespace = namespace,
            podName = podName,
            containerName = container,
            command = command,
            tty = true,
            onStdout = { output ->
                terminalEngine.feedRemoteOutput(output)
                viewModelScope.launch(dispatcherProvider.main) {
                    output.lines().forEach { line ->
                        _uiState.update {
                            it.copy(
                                terminalLines = it.terminalLines + TerminalLine(
                                    text = line,
                                    type = TerminalLineType.STDOUT,
                                ),
                            )
                        }
                    }
                }
            },
            onStderr = { output ->
                terminalEngine.feedRemoteOutput(output)
                if (output.contains("executable file not found", ignoreCase = true) ||
                    output.contains("no such file", ignoreCase = true) ||
                    output.contains("OCI runtime exec failed", ignoreCase = true)
                ) {
                    hadFatalError = true
                }
                viewModelScope.launch(dispatcherProvider.main) {
                    output.lines().forEach { line ->
                        _uiState.update {
                            it.copy(
                                terminalLines = it.terminalLines + TerminalLine(
                                    text = line,
                                    type = TerminalLineType.STDERR,
                                ),
                            )
                        }
                    }
                }
            },
            onError = { err ->
                if (err.contains("executable file not found", ignoreCase = true) ||
                    err.contains("no such file", ignoreCase = true) ||
                    err.contains("exit status 127", ignoreCase = true) ||
                    err.contains("OCI runtime exec failed", ignoreCase = true)
                ) {
                    hadFatalError = true
                }
                viewModelScope.launch(dispatcherProvider.main) {
                    _uiState.update {
                        it.copy(
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[Shell Error: $err]",
                                type = TerminalLineType.ERROR,
                            ),
                            isTerminalActive = false,
                        )
                    }
                }
            },
            onDone = {
                viewModelScope.launch(dispatcherProvider.main) {
                    _uiState.update {
                        it.copy(
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[Session closed]",
                                type = TerminalLineType.SYSTEM,
                            ),
                            isTerminalActive = false,
                        )
                    }
                }
            },
        )

        return when (sessionResult) {
            is Result.Success -> {
                // Brief delay to let error callbacks (e.g. "executable not found") fire
                delay(500)
                if (!hadFatalError) {
                    activeTerminalSession = sessionResult.data
                    terminalEngine.attachSession(sessionResult.data)
                    _uiState.update {
                        it.copy(
                            isTerminalActive = true,
                            activeShellCommand = command.substringAfterLast('/'),
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[Interactive session attached ($command)]",
                                type = TerminalLineType.SYSTEM,
                            ),
                        )
                    }
                    true
                } else {
                    terminalEngine.detachSession()
                    activeTerminalSession?.close()
                    activeTerminalSession = null
                    false
                }
            }

            is Result.Error -> false
            is Result.Loading -> false
        }
    }

    private suspend fun tryAttachDefaultTerminal(clusterId: String, container: String) {
        val defaultResult = startPodTerminalUseCase(
            clusterId = clusterId,
            namespace = namespace,
            podName = podName,
            containerName = container,
            onStdout = { output ->
                terminalEngine.feedRemoteOutput(output)
                viewModelScope.launch(dispatcherProvider.main) {
                    output.lines().forEach { line ->
                        _uiState.update {
                            it.copy(
                                terminalLines = it.terminalLines + TerminalLine(
                                    text = line,
                                    type = TerminalLineType.STDOUT,
                                ),
                            )
                        }
                    }
                }
            },
            onStderr = { output ->
                terminalEngine.feedRemoteOutput(output)
                viewModelScope.launch(dispatcherProvider.main) {
                    output.lines().forEach { line ->
                        _uiState.update {
                            it.copy(
                                terminalLines = it.terminalLines + TerminalLine(
                                    text = line,
                                    type = TerminalLineType.STDERR,
                                ),
                            )
                        }
                    }
                }
            },
            onError = { err ->
                viewModelScope.launch(dispatcherProvider.main) {
                    _uiState.update {
                        it.copy(
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[Shell Error: $err]",
                                type = TerminalLineType.ERROR,
                            ),
                            isTerminalActive = false,
                        )
                    }
                }
            },
            onDone = {
                viewModelScope.launch(dispatcherProvider.main) {
                    _uiState.update {
                        it.copy(
                            terminalLines = it.terminalLines + TerminalLine(
                                text = "[Session closed]",
                                type = TerminalLineType.SYSTEM,
                            ),
                            isTerminalActive = false,
                        )
                    }
                }
            },
        )

        when (defaultResult) {
            is Result.Success -> {
                activeTerminalSession = defaultResult.data
                terminalEngine.attachSession(defaultResult.data)
                _uiState.update {
                    it.copy(
                        isTerminalActive = true,
                        activeShellCommand = "default",
                        terminalLines = it.terminalLines + TerminalLine(
                            text = "[Interactive terminal attached]",
                            type = TerminalLineType.SYSTEM,
                        ),
                    )
                }
            }

            is Result.Error -> {
                _uiState.update {
                    it.copy(
                        isTerminalActive = false,
                        terminalLines = it.terminalLines + TerminalLine(
                            text = "[Failed to attach terminal: ${defaultResult.error.message}]",
                            type = TerminalLineType.ERROR,
                        ),
                    )
                }
            }

            is Result.Loading -> Unit
        }
    }

    private fun sendInputToTerminal(input: String) {
        val session = activeTerminalSession
        if (session != null) {
            try {
                session.write(input + "\n")
                _uiState.update {
                    it.copy(
                        terminalLines = it.terminalLines + TerminalLine(
                            text = input,
                            type = TerminalLineType.INPUT,
                        ),
                    )
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        terminalLines = it.terminalLines + TerminalLine(
                            text = "[Write error: ${t.message}]",
                            type = TerminalLineType.ERROR,
                        ),
                    )
                }
            }
        } else {
            handleExecuteCommand(input)
        }
    }

    private fun stopTerminal() {
        terminalEngine.detachSession()
        try {
            activeTerminalSession?.close()
        } catch (_: Throwable) {
        }
        activeTerminalSession = null
        _uiState.update {
            if (it.isTerminalActive) {
                it.copy(
                    isTerminalActive = false,
                    terminalLines = it.terminalLines + TerminalLine(
                        text = "[Terminal disconnected]",
                        type = TerminalLineType.SYSTEM,
                    ),
                )
            } else {
                it
            }
        }
    }

    private fun deletePod() {
        val cid = activeClusterId ?: return
        _uiState.update { it.copy(isDeletingPod = true) }

        viewModelScope.launch(dispatcherProvider.main) {
            when (val result = deletePodUseCase(cid, namespace, podName)) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isDeletingPod = false,
                            showDeleteConfirmDialog = false,
                        )
                    }
                    _effects.send(PodDetailUiEffect.ShowToast("Pod '$podName' deleted successfully"))
                    _effects.send(PodDetailUiEffect.NavigateBack)
                }

                is Result.Error -> {
                    _uiState.update {
                        it.copy(
                            isDeletingPod = false,
                            showDeleteConfirmDialog = false,
                        )
                    }
                    _effects.send(PodDetailUiEffect.ShowToast("Failed to delete pod: ${result.error.message}"))
                }

                is Result.Loading -> Unit
            }
        }
    }

    override fun onCleared() {
        stopStreaming()
        stopTerminal()
        terminalEngine.destroy()
    }

    companion object {
        fun provideFactory(
            factory: Factory,
            podName: String,
            namespace: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return factory.create(podName, namespace) as T
            }
        }
    }
}
