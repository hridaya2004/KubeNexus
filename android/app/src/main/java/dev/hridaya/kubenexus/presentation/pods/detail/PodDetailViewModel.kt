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
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodLogsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodMetricsUseCase
import dev.hridaya.kubenexus.domain.usecase.StartExecSessionUseCase
import dev.hridaya.kubenexus.domain.usecase.StreamPodLogsUseCase
import dev.hridaya.kubenexus.presentation.pods.components.terminal.GhosttyTerminalEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
    private var logsJob: Job? = null
    private var metricsJob: Job? = null

    /** Container the current log lines belong to, so the Logs tab never shows another's. */
    private var logsContainer: String? = null

    private var terminalJob: Job? = null
    private var activeTerminalSession: TerminalSession? = null

    /**
     * Incremented for every attach attempt and on stop. Session callbacks arrive on native
     * threads, possibly after a newer attempt began, and only act if their attempt is current.
     */
    @Volatile
    private var terminalAttempt = 0

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
                    _uiState.update { state ->
                        // metrics-server refreshes every ~15 s, so most polls return the sample
                        // already held. The window is measured in server time, from the newest
                        // sample, so a device clock that disagrees with the cluster's cannot
                        // empty the chart.
                        val samples = (state.metricsSamples + sample)
                            .distinctBy { it.timestampMillis }
                            .sortedBy { it.timestampMillis }
                        val cutoff = samples.last().timestampMillis - MetricsRange.MINUTES_5.durationMs
                        state.copy(metricsSamples = samples.filter { it.timestampMillis >= cutoff })
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
                        stopTerminal(notice = "Network disconnected - terminal session closed")
                    }
                    if (_uiState.value.isStreamingLogs) {
                        stopStreaming()
                        _uiState.update {
                            it.copy(logs = it.logs.appendCapped("[Network disconnected - log stream stopped]"))
                        }
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
                val state = _uiState.value
                val logsAreForAnotherContainer = logsContainer != state.selectedContainer
                if (action.tab == PodDetailTab.LOGS && !state.isStreamingLogs &&
                    (state.logs.isEmpty() || logsAreForAnotherContainer)
                ) {
                    fetchLogs()
                }
            }

            is PodDetailUiAction.SelectContainer -> {
                val previous = _uiState.value.selectedContainer
                _uiState.update { it.copy(selectedContainer = action.containerName) }
                // A shell stays attached to the container it was opened in; switching the
                // selected container ends it rather than silently typing into the old one.
                if (previous != action.containerName && _uiState.value.isTerminalActive) {
                    stopTerminal(notice = "Switched to container '${action.containerName}'. Start a shell to attach.")
                }
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

            is PodDetailUiAction.StartInteractiveTerminal -> {
                startTerminal(action.shell)
            }

            is PodDetailUiAction.StopInteractiveTerminal -> {
                stopTerminal()
            }

            is PodDetailUiAction.ClearTerminal -> {
                // Keeps the current grid size; the canvas only re-sends it when it changes.
                terminalEngine.initialize()
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

        // A newer fetch (another container or tail size) supersedes an older one still running.
        logsJob?.cancel()
        logsJob = viewModelScope.launch(dispatcherProvider.main) {
            when (val result =
                getPodLogsUseCase(cid, namespace, podName, container, overrideTail)) {
                is Result.Success -> {
                    logsContainer = container
                    val lines = result.data.lines().takeLast(MAX_LOG_LINES)
                    _uiState.update {
                        it.copy(
                            isLoadingLogs = false,
                            logs = lines,
                        )
                    }
                }

                is Result.Error -> {
                    logsContainer = container
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
        logsJob?.cancel()
        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer
        val tail = _uiState.value.tailLines
        logsContainer = container

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
                .onCompletion { cause ->
                    // The stream ends by itself when the container stops or the connection
                    // drops; say so, and stop showing it as live. Cancellation is a user stop.
                    if (cause == null) {
                        _uiState.update {
                            it.copy(isStreamingLogs = false, logs = it.logs.appendCapped("[Log stream ended]"))
                        }
                    }
                }
                .catch { t ->
                    _uiState.update {
                        it.copy(
                            isStreamingLogs = false,
                            logs = it.logs.appendCapped("[Log stream closed: ${t.message}]"),
                        )
                    }
                }
                .collect { line ->
                    _uiState.update {
                        it.copy(logs = it.logs.appendCapped(line))
                    }
                }
        }
    }

    private fun stopStreaming() {
        streamJob?.cancel()
        streamJob = null
        _uiState.update { it.copy(isStreamingLogs = false) }
    }

    private fun startTerminal(preferredShell: String? = null) {
        stopTerminal()
        val cid = activeClusterId ?: return
        val container = _uiState.value.selectedContainer ?: "default"

        _uiState.update { it.copy(isTerminalActive = true, activeShellCommand = preferredShell ?: "bash") }
        terminalNotice("Attaching to container '$container'...")

        terminalJob = viewModelScope.launch(dispatcherProvider.main) {
            val shells = (listOfNotNull(preferredShell) + DEFAULT_SHELLS).distinct()
            for ((index, shell) in shells.withIndex()) {
                if (tryAttachExec(cid, container, shell)) return@launch
                shells.getOrNull(index + 1)?.let { next ->
                    terminalNotice("$shell is not available, trying $next...")
                }
            }
            _uiState.update { it.copy(isTerminalActive = false) }
            terminalNotice("No shell found in container '$container' (tried ${shells.joinToString()}).")
        }
    }

    /**
     * Opens [command] as the terminal's shell. Returns false if the container cannot run it,
     * which the runtime reports as an exec error shortly after the stream opens.
     */
    private suspend fun tryAttachExec(
        clusterId: String,
        container: String,
        command: String,
    ): Boolean {
        val attempt = ++terminalAttempt
        // Set from native callback threads, read here on the main thread.
        val shellMissing = AtomicBoolean(false)
        var attached = false

        val sessionResult = startExecSessionUseCase(
            clusterId = clusterId,
            namespace = namespace,
            podName = podName,
            containerName = container,
            command = command,
            tty = true,
            onStdout = { output -> terminalEngine.feedRemoteOutput(output) },
            onStderr = { output ->
                if (isMissingExecutable(output)) shellMissing.set(true)
                terminalEngine.feedRemoteOutput(output)
            },
            onError = { err ->
                if (isMissingExecutable(err)) {
                    shellMissing.set(true)
                } else {
                    viewModelScope.launch(dispatcherProvider.main) {
                        if (attempt == terminalAttempt && attached) endTerminalSession("Shell error: $err")
                    }
                }
            },
            onDone = {
                viewModelScope.launch(dispatcherProvider.main) {
                    if (attempt == terminalAttempt && attached) endTerminalSession("Session closed")
                }
            },
        )

        val session = when (sessionResult) {
            is Result.Success -> sessionResult.data
            is Result.Error -> {
                terminalNotice("Failed to start $command: ${sessionResult.error.message}")
                return false
            }
            is Result.Loading -> return false
        }

        try {
            // A missing shell is reported by the runtime just after the stream opens.
            delay(SHELL_PROBE_MS)
        } catch (e: CancellationException) {
            // Stopped while attaching: never leave the session open.
            closeQuietly(session)
            throw e
        }
        if (shellMissing.get() || attempt != terminalAttempt) {
            closeQuietly(session)
            return false
        }

        attached = true
        activeTerminalSession = session
        terminalEngine.attachSession(session)
        _uiState.update {
            it.copy(isTerminalActive = true, activeShellCommand = command.substringAfterLast('/'))
        }
        return true
    }

    /** Called when the attached shell ends on its own. */
    private fun endTerminalSession(reason: String) {
        terminalAttempt++
        terminalEngine.detachSession()
        activeTerminalSession = null
        _uiState.update { it.copy(isTerminalActive = false) }
        terminalNotice(reason)
    }

    private fun stopTerminal(notice: String? = "Terminal disconnected") {
        val wasActive = _uiState.value.isTerminalActive
        terminalJob?.cancel()
        terminalJob = null
        terminalAttempt++
        terminalEngine.detachSession()
        activeTerminalSession?.let(::closeQuietly)
        activeTerminalSession = null
        _uiState.update { it.copy(isTerminalActive = false) }
        if (wasActive && notice != null) terminalNotice(notice)
    }

    /**
     * Shows a status line in the terminal itself, dimmed like a shell's own notices, and
     * keeps it in the UI state for the header.
     */
    private fun terminalNotice(message: String) {
        terminalEngine.feedRemoteOutput("\r\n\u001B[2m[$message]\u001B[0m\r\n")
        _uiState.update { it.copy(terminalNotice = message) }
    }

    private fun closeQuietly(session: TerminalSession) {
        try {
            session.close()
        } catch (_: Exception) {
            // Already closed on the remote side.
        }
    }

    private fun isMissingExecutable(message: String): Boolean =
        MISSING_EXECUTABLE_MARKERS.any { message.contains(it, ignoreCase = true) }

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
        stopTerminal(notice = null)
        terminalEngine.destroy()
    }

    companion object {
        /** Shells tried in order when the user does not pick one. */
        private val DEFAULT_SHELLS = listOf("/bin/bash", "/bin/sh", "/busybox/sh")

        /** How long to wait for the runtime to report that a shell does not exist. */
        private const val SHELL_PROBE_MS = 500L

        /** Upper bound for the log view, so a long stream cannot grow without limit. */
        const val MAX_LOG_LINES = 5_000

        private val MISSING_EXECUTABLE_MARKERS = listOf(
            "executable file not found",
            "no such file",
            "exit status 127",
            "OCI runtime exec failed",
        )

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

/** Appends [line], dropping the oldest lines beyond [PodDetailViewModel.MAX_LOG_LINES]. */
private fun List<String>.appendCapped(line: String): List<String> =
    if (size < PodDetailViewModel.MAX_LOG_LINES) this + line else drop(size - PodDetailViewModel.MAX_LOG_LINES + 1) + line
