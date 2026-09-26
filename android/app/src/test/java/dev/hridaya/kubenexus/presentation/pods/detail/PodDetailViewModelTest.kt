package dev.hridaya.kubenexus.presentation.pods.detail

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.ContainerDetail
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodConditionDetail
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodEventDetail
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.PodRepository
import dev.hridaya.kubenexus.domain.usecase.CheckClusterHealthUseCase
import dev.hridaya.kubenexus.domain.usecase.DeletePodUseCase
import dev.hridaya.kubenexus.domain.usecase.DescribePodUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodLogsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodMetricsUseCase
import dev.hridaya.kubenexus.domain.usecase.StartExecSessionUseCase
import dev.hridaya.kubenexus.domain.usecase.StreamPodLogsUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Why not [kotlinx.coroutines.test.advanceUntilIdle]: open-ended work such as a
 * followed log stream (and metrics polling, once the screen starts it) never
 * finishes, so idle-based waiting would hang.
 *
 * Every test therefore:
 *  1. advances virtual time in fixed slices ([idleNow]) - bounded, deterministic;
 *  2. cancels viewModelScope in a finally INSIDE the test body, so runTest's own
 *     end-of-test quiescence check finds an empty scheduler instead of waiting
 *     on the immortal poller.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var fakePodRepository: FakePodRepository
    private lateinit var onlineFlow: MutableStateFlow<Boolean>
    private lateinit var viewModel: PodDetailViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClusterRepository = FakeClusterRepository()
        fakePodRepository = FakePodRepository()
        onlineFlow = MutableStateFlow(true)
        val fakeNetworkMonitor = object : NetworkMonitor {
            override val isOnline: Flow<Boolean> = onlineFlow
        }

        viewModel = PodDetailViewModel(
            podName = "test-pod-1",
            namespace = "default",
            getActiveClusterUseCase = GetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            describePodUseCase = DescribePodUseCase(fakePodRepository),
            getPodMetricsUseCase = GetPodMetricsUseCase(fakePodRepository),
            getPodLogsUseCase = GetPodLogsUseCase(fakePodRepository),
            streamPodLogsUseCase = StreamPodLogsUseCase(fakePodRepository),
            deletePodUseCase = DeletePodUseCase(fakePodRepository),
            startExecSessionUseCase = StartExecSessionUseCase(fakePodRepository),
            checkClusterHealthUseCase = CheckClusterHealthUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            networkMonitor = fakeNetworkMonitor,
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vmTest(block: suspend TestScope.() -> Unit) = runTest(testDispatcher) {
        try {
            block()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    /**
     * Runs everything scheduled at the current virtual time, then moves time
     * forward in 1s slices (30 steps = 30s virtual). Longest real wait in the
     * ViewModel is the 500ms shell-attach delay, so this always settles the
     * pending work while guaranteeing termination despite the metrics poller.
     */
    private fun TestScope.idleNow(steps: Int = 30) {
        repeat(steps) {
            runCurrent()
            advanceTimeBy(1_000)
        }
    }

    @Test
    fun `initial load fetches pod describe details and selects default container`() =
        vmTest {
            idleNow()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertNotNull(state.podDetails)
            assertNotNull(state.lastRefreshedAt)
            assertEquals("test-pod-1", state.podDetails?.name)
            assertEquals("container-app", state.selectedContainer)
            assertEquals(ClusterConnectionStatus.CONNECTED, state.clusterConnectionStatus)
            assertEquals(1, state.podDetails?.containers?.size)
            assertEquals(1, state.podDetails?.conditions?.size)
            assertEquals(1, state.podDetails?.events?.size)
        }

    @Test
    fun `switching tab to LOGS triggers log fetching`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
        idleNow()

        val state = viewModel.uiState.value
        assertEquals(PodDetailTab.LOGS, state.selectedTab)
        assertFalse(state.logs.isEmpty())
        assertTrue(state.logs.any { it.contains("Starting service") })
    }

    @Test
    fun `switching tab to TERMINAL updates selected tab`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.TERMINAL))
        idleNow()

        assertEquals(PodDetailTab.TERMINAL, viewModel.uiState.value.selectedTab)
    }

    @Test
    fun `interactive terminal attaches the first available shell and stops`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
        idleNow()
        assertTrue(viewModel.uiState.value.isTerminalActive)
        assertEquals("bash", viewModel.uiState.value.activeShellCommand)
        assertEquals(listOf("/bin/bash"), fakePodRepository.execCommands)

        viewModel.onAction(PodDetailUiAction.StopInteractiveTerminal)
        idleNow()
        assertFalse(viewModel.uiState.value.isTerminalActive)
        assertEquals("Terminal disconnected", viewModel.uiState.value.terminalNotice)
        assertTrue(fakePodRepository.sessions.single().closed)
    }

    // With direct exec, a missing shell is reported by the container runtime.
    @Test
    fun `a missing shell falls back to the next one and closes the failed session`() = vmTest {
        idleNow()
        fakePodRepository.missingShells += listOf("/bin/bash", "/bin/sh")

        viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
        idleNow()

        val state = viewModel.uiState.value
        assertTrue(state.isTerminalActive)
        assertEquals("sh", state.activeShellCommand)
        assertEquals(listOf("/bin/bash", "/bin/sh", "/busybox/sh"), fakePodRepository.execCommands)
        assertTrue(fakePodRepository.sessions.dropLast(1).all { it.closed })
        assertFalse(fakePodRepository.sessions.last().closed)
    }

    @Test
    fun `no available shell ends the attempt with a notice`() = vmTest {
        idleNow()
        fakePodRepository.missingShells += listOf("/bin/bash", "/bin/sh", "/busybox/sh")

        viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
        idleNow()

        val state = viewModel.uiState.value
        assertFalse(state.isTerminalActive)
        assertTrue(state.terminalNotice.orEmpty().startsWith("No shell found"))
        assertTrue(fakePodRepository.sessions.all { it.closed })
    }

    @Test
    fun `stopping while the shell is still attaching closes it and never attaches`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
        runCurrent() // the session is open, the attach probe is still waiting
        viewModel.onAction(PodDetailUiAction.StopInteractiveTerminal)
        idleNow()

        assertFalse(viewModel.uiState.value.isTerminalActive)
        assertTrue(fakePodRepository.sessions.single().closed)
    }

    @Test
    fun `a shell that exits by itself marks the terminal inactive`() = vmTest {
        idleNow()
        viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
        idleNow()
        assertTrue(viewModel.uiState.value.isTerminalActive)

        fakePodRepository.sessions.single().remoteExit()
        idleNow()

        assertFalse(viewModel.uiState.value.isTerminalActive)
        assertEquals("Session closed", viewModel.uiState.value.terminalNotice)
    }

    @Test
    fun `streaming logs clears existing logs first and then streams new lines`() = vmTest {
        idleNow()

        // Populate old logs first
        viewModel.onAction(PodDetailUiAction.FetchLogs)
        idleNow()
        assertTrue(viewModel.uiState.value.logs.any { it.contains("Starting service") })

        viewModel.onAction(PodDetailUiAction.StartStreamingLogs)
        idleNow()

        val state = viewModel.uiState.value
        assertTrue(state.isStreamingLogs)
        assertFalse(state.logs.any { it.contains("Starting service") })
        assertTrue(state.logs.any { it.contains("Log line 1") })

        viewModel.onAction(PodDetailUiAction.StopStreamingLogs)
        assertFalse(viewModel.uiState.value.isStreamingLogs)
    }

    @Test
    fun `a log stream that ends by itself is no longer shown as streaming`() = vmTest {
        idleNow()
        fakePodRepository.streamEnds = true

        viewModel.onAction(PodDetailUiAction.StartStreamingLogs)
        idleNow()

        val state = viewModel.uiState.value
        assertFalse(state.isStreamingLogs)
        assertEquals("[Log stream ended]", state.logs.last())
    }

    @Test
    fun `the log view keeps only the newest lines`() = vmTest {
        idleNow()
        fakePodRepository.streamLines = (0 until 6_000).map { "line $it" }

        viewModel.onAction(PodDetailUiAction.StartStreamingLogs)
        idleNow()

        val logs = viewModel.uiState.value.logs
        assertEquals(PodDetailViewModel.MAX_LOG_LINES, logs.size)
        assertEquals("line 5999", logs.last())
    }

    // "View logs" on a container card selects it while the Describe tab is still open.
    @Test
    fun `opening the logs tab refetches when the logs belong to another container`() = vmTest {
        idleNow()
        viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
        idleNow()
        assertEquals("container-app", fakePodRepository.lastGetPodLogsContainer)

        viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.DESCRIBE))
        viewModel.onAction(PodDetailUiAction.SelectContainer("sidecar"))
        viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
        idleNow()

        assertEquals("sidecar", fakePodRepository.lastGetPodLogsContainer)
    }

    @Test
    fun `setting tail lines updates state and refetches logs with the specified tail count`() =
        vmTest {
            idleNow()

            viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
            idleNow()
            assertEquals(250L, fakePodRepository.lastGetPodLogsTail)

            viewModel.onAction(PodDetailUiAction.SetTailLines(500L))
            idleNow()
            assertEquals(500L, viewModel.uiState.value.tailLines)
            assertEquals(500L, fakePodRepository.lastGetPodLogsTail)

            viewModel.onAction(PodDetailUiAction.SetTailLines(null))
            idleNow()
            assertNull(viewModel.uiState.value.tailLines)
            assertNull(fakePodRepository.lastGetPodLogsTail)
        }

    @Test
    fun `setting tail lines while streaming restarts stream with new tail limit`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.StartStreamingLogs)
        idleNow()
        assertEquals(250L, fakePodRepository.lastStreamPodLogsTail)

        viewModel.onAction(PodDetailUiAction.SetTailLines(500L))
        idleNow()
        assertEquals(500L, viewModel.uiState.value.tailLines)
        assertEquals(500L, fakePodRepository.lastStreamPodLogsTail)
        assertTrue(viewModel.uiState.value.isStreamingLogs)

        viewModel.onAction(PodDetailUiAction.StopStreamingLogs)
        assertFalse(viewModel.uiState.value.isStreamingLogs)
    }

    @Test
    fun `fetching all logs clears existing logs and requests un-tailed logs from repository`() =
        vmTest {
            idleNow()

            viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
            viewModel.onAction(PodDetailUiAction.SetTailLines(50L))
            idleNow()
            assertEquals(50L, fakePodRepository.lastGetPodLogsTail)
            assertTrue(viewModel.uiState.value.logs.isNotEmpty())

            viewModel.onAction(PodDetailUiAction.FetchAllLogs)
            idleNow()

            assertNull(fakePodRepository.lastGetPodLogsTail)
            assertTrue(viewModel.uiState.value.logs.isNotEmpty())
            assertFalse(viewModel.uiState.value.isLoadingLogs)
        }

    @Test
    fun `network offline updates isOnline and isContainerAttachable and closes active terminal`() =
        vmTest {
            idleNow()
            assertTrue(viewModel.uiState.value.isOnline)
            assertTrue(viewModel.uiState.value.isContainerAttachable)

            viewModel.onAction(PodDetailUiAction.StartInteractiveTerminal())
            idleNow()
            assertTrue(viewModel.uiState.value.isTerminalActive)

            onlineFlow.value = false
            idleNow()

            val state = viewModel.uiState.value
            assertFalse(state.isOnline)
            assertFalse(state.isContainerAttachable)
            assertFalse(state.isTerminalActive)
            assertTrue(state.terminalNotice.orEmpty().contains("Network disconnected"))
        }

    @Test
    fun `network offline stops streaming logs and reconnection refetches pod describe`() =
        vmTest {
            idleNow()

            viewModel.onAction(PodDetailUiAction.SelectTab(PodDetailTab.LOGS))
            viewModel.onAction(PodDetailUiAction.StartStreamingLogs)
            idleNow()
            assertTrue(viewModel.uiState.value.isStreamingLogs)

            onlineFlow.value = false
            idleNow()
            assertFalse(viewModel.uiState.value.isStreamingLogs)
            assertTrue(
                viewModel.uiState.value.logs.any { it.contains("Network disconnected") },
            )

            val describesBeforeReconnect = fakePodRepository.describeCalls
            onlineFlow.value = true
            idleNow()
            assertTrue(viewModel.uiState.value.isOnline)
            assertNotNull(viewModel.uiState.value.podDetails)
            assertTrue(
                "reconnecting must refetch the describe",
                fakePodRepository.describeCalls > describesBeforeReconnect,
            )
        }

    @Test
    fun `refreshDescribe when describe fails sets error and resets flags`() = vmTest {
        idleNow()

        fakePodRepository.describeError = "Connection refused: cannot reach cluster"
        viewModel.onAction(PodDetailUiAction.RefreshDescribe)
        idleNow()

        val state = viewModel.uiState.value
        assertFalse(state.isRefreshing)
        assertFalse(state.isLoading)
        assertEquals("Connection refused: cannot reach cluster", state.errorMessage)
    }

    @Test
    fun `unhealthy cluster health check disables container attachability`() = vmTest {
        fakeClusterRepository.healthResult = Result.Success(
            ClusterHealth(
                livez = false,
                readyz = false,
                serverVersion = "",
                statusMessage = "Not Ready",
            ),
        )
        // Health check runs as part of the init cluster load
        idleNow()

        val state = viewModel.uiState.value
        assertEquals(ClusterConnectionStatus.DISCONNECTED, state.clusterConnectionStatus)
        assertFalse(state.isContainerAttachable)
    }

    @Test
    fun `confirming delete pod succeeds and emits navigation effect`() = vmTest {
        idleNow()

        viewModel.onAction(PodDetailUiAction.ShowDeleteDialog(true))
        assertTrue(viewModel.uiState.value.showDeleteConfirmDialog)

        viewModel.onAction(PodDetailUiAction.ConfirmDeletePod)
        idleNow()

        val state = viewModel.uiState.value
        assertFalse(state.isDeletingPod)
        assertFalse(state.showDeleteConfirmDialog)
        assertEquals(
            listOf(
                PodDetailUiEffect.ShowToast("Pod 'test-pod-1' deleted successfully"),
                PodDetailUiEffect.NavigateBack,
            ),
            viewModel.effects.take(2).toList(),
        )
    }

    private class FakeClusterRepository : ClusterRepository {
        var healthResult: Result<ClusterHealth> = Result.Success(
            ClusterHealth(
                livez = true,
                readyz = true,
                serverVersion = "v1.30.0",
                statusMessage = "Ready",
            ),
        )

        private val activeCluster = Cluster(
            id = "c-1",
            name = "prod-cluster",
            serverUrl = "https://127.0.0.1:6443",
            rawKubeconfig = "...",
            contextName = "prod",
            isActive = true,
            status = ClusterStatus.CONNECTED,
        )

        override fun getClustersStream(): Flow<List<Cluster>> = flowOf(listOf(activeCluster))

        override fun getActiveClusterStream(): Flow<Cluster?> = flowOf(activeCluster)

        override suspend fun getClusterById(id: String): Cluster = activeCluster

        override suspend fun addCluster(
            kubeconfigRaw: String,
            customName: String?,
            setAsActive: Boolean,
        ): Result<Cluster> = Result.Success(activeCluster)

        override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun testConnection(kubeconfigRaw: String): Result<String> =
            Result.Success("OK")

        override suspend fun testClusterById(id: String): Result<String> = Result.Success("OK")

        override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> = healthResult

        override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
            healthResult

        override suspend fun updateClusterStatus(
            id: String,
            status: ClusterStatus,
            lastConnectedAt: Long?,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)


        override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)

        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
    }

    private class FakePodRepository : PodRepository {
        var describeError: String? = null
        var describeCalls = 0
        var lastGetPodLogsTail: Long? = null
        var lastGetPodLogsContainer: String? = null
        var lastStreamPodLogsTail: Long? = null
        var streamLines: List<String> = listOf("Log line 1", "Log line 2", "Log line 3")

        /** A followed stream stays open until cancelled, like the real one. */
        var streamEnds = false

        /** Shells the fake container does not have; exec reports them as missing. */
        val missingShells = mutableListOf<String>()
        val execCommands = mutableListOf<String>()
        val sessions = mutableListOf<FakeSession>()

        class FakeSession(private val onDone: () -> Unit) : TerminalSession {
            var closed = false
            override fun write(input: String) = Unit
            override fun writeBytes(bytes: ByteArray) = Unit
            override fun close() {
                if (!closed) {
                    closed = true
                    onDone()
                }
            }

            fun remoteExit() = onDone()
        }

        override fun getPodsStream(
            clusterId: String?,
            namespace: String?,
        ): Flow<List<Pod>> = flowOf(emptyList())

        override fun getNamespacesStream(clusterId: String?): Flow<List<String>> =
            flowOf(listOf("default"))

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)

        override suspend fun refreshWorkloads(
            clusterId: String?,
            namespace: String?,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun getPodMetrics(
            clusterId: String?,
            namespace: String?,
        ): Result<List<PodMetricSample>> = Result.Success(emptyList())

        override suspend fun getSinglePodMetrics(
            clusterId: String?,
            namespace: String,
            podName: String,
        ): Result<PodMetricSample?> = Result.Success(null)

        override suspend fun listPodsBySelector(
            rawKubeconfig: String,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(emptyList())

        override suspend fun getPodsBySelector(
            clusterId: String?,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(emptyList())

        override suspend fun describePod(
            clusterId: String?,
            namespace: String,
            podName: String,
        ): Result<PodDetails> {
            describeCalls++
            describeError?.let { return Result.Error(AppError.Network(it)) }
            return Result.Success(defaultDetails(podName, namespace))
        }

        override suspend fun deletePod(
            clusterId: String?,
            namespace: String,
            podName: String,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteNamespace(
            clusterId: String?,
            namespace: String,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun createNamespace(
            clusterId: String?,
            name: String,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun createPodFromManifest(
            clusterId: String?,
            manifestYaml: String,
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun getPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?,
        ): Result<String> {
            lastGetPodLogsTail = tailLines
            lastGetPodLogsContainer = containerName
            return Result.Success(
                "Starting service...\nListening on port 8080\nReady to accept connections.",
            )
        }

        override fun streamPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?,
        ): Flow<String> {
            lastStreamPodLogsTail = tailLines
            val lines = streamLines
            val ends = streamEnds
            return flow {
                lines.forEach { emit(it) }
                if (!ends) awaitCancellation()
            }
        }

        override suspend fun execCommand(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            stdin: String,
        ): Result<CommandExecResult> =
            Result.Success(CommandExecResult(stdout = "Linux k8s-node 5.15.0", stderr = ""))

        override suspend fun startTerminalSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit,
        ): Result<TerminalSession> = Result.Success(echoSession(onStdout, onDone))

        override suspend fun startExecSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            tty: Boolean,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit,
        ): Result<TerminalSession> {
            execCommands += command
            val session = FakeSession(onDone)
            sessions += session
            if (command in missingShells) {
                onError("OCI runtime exec failed: exec: \"$command\": stat $command: no such file or directory")
            }
            return Result.Success(session)
        }

        private fun echoSession(
            onStdout: (String) -> Unit,
            onDone: () -> Unit,
        ): TerminalSession = object : TerminalSession {
            override fun write(input: String) {
                onStdout("echo: ${input.trimEnd('\n')}")
            }

            override fun writeBytes(bytes: ByteArray) = Unit

            override fun close() {
                onDone()
            }
        }

        private fun defaultDetails(podName: String, namespace: String) = PodDetails(
            name = podName,
            namespace = namespace,
            status = PodStatus.RUNNING,
            node = "k8s-node-1",
            ip = "10.244.0.15",
            containers = listOf(
                ContainerDetail(
                    name = "container-app",
                    image = "kubenexus/api:v1",
                    ready = true,
                    restartCount = 0,
                ),
            ),
            conditions = listOf(
                PodConditionDetail(type = "Ready", status = "True"),
            ),
            events = listOf(
                PodEventDetail(
                    type = "Normal",
                    reason = "Started",
                    message = "Started container",
                    age = "5m",
                ),
            ),
        )
    }
}
