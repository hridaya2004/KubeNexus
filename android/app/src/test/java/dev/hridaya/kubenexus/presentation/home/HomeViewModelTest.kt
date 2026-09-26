package dev.hridaya.kubenexus.presentation.home

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.PodRepository
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var fakePodRepository: FakePodRepository
    private val onlineFlow = MutableStateFlow(true)
    private val fakeNetworkMonitor =
        object : NetworkMonitor {
            override val isOnline: Flow<Boolean> = onlineFlow
        }
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        fakeClusterRepository = FakeClusterRepository()
        fakePodRepository = FakePodRepository()
        onlineFlow.value = true
        viewModel = HomeViewModel(
            getClustersUseCase = GetClustersUseCase(fakeClusterRepository, testDispatcherProvider),
            getActiveClusterUseCase = GetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            getPodsUseCase = GetPodsUseCase(fakePodRepository),
            getNamespacesUseCase = GetNamespacesUseCase(fakePodRepository),
            getLastRefreshedUseCase = GetLastRefreshedUseCase(fakePodRepository),
            refreshWorkloadsUseCase = RefreshWorkloadsUseCase(fakePodRepository),
            addClusterUseCase = AddClusterUseCase(fakeClusterRepository, testDispatcherProvider),
            setActiveClusterUseCase = SetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            deleteClusterUseCase = DeleteClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            clearCachedDataUseCase = ClearCachedDataUseCase(fakeClusterRepository),
            deleteNamespaceUseCase = DeleteNamespaceUseCase(fakePodRepository),
            updateClusterNameUseCase = UpdateClusterNameUseCase(fakeClusterRepository),
            testClusterConnectionUseCase = TestClusterConnectionUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            checkClusterHealthUseCase = CheckClusterHealthUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            networkMonitor = fakeNetworkMonitor,
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `initial state starts with loading false and empty clusters`() = runTest(testDispatcher) {
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.clusters.isEmpty())
        assertEquals(null, state.activeCluster)
        assertEquals(ClusterConnectionStatus.OFFLINE, state.clusterConnectionStatus)
    }

    @Test
    fun `OpenClusterDrawer and DismissClusterDrawer update state`() {
        viewModel.onAction(HomeUiAction.OpenClusterDrawer)
        assertTrue(viewModel.uiState.value.showClusterDrawer)

        viewModel.onAction(HomeUiAction.DismissClusterDrawer)
        assertFalse(viewModel.uiState.value.showClusterDrawer)
    }

    @Test
    fun `OpenFabActionSheet and DismissFabActionSheet update state`() {
        viewModel.onAction(HomeUiAction.OpenFabActionSheet)
        assertTrue(viewModel.uiState.value.showFabActionSheet)

        viewModel.onAction(HomeUiAction.DismissFabActionSheet)
        assertFalse(viewModel.uiState.value.showFabActionSheet)
    }

    @Test
    fun `a failed file import is shown on the kubeconfig field until the input changes`() {
        viewModel.onAction(HomeUiAction.OpenAddClusterSheet)
        viewModel.onAction(HomeUiAction.FileImportFailed("That file is larger than 1 MB"))

        assertEquals("That file is larger than 1 MB", viewModel.uiState.value.kubeconfigError)

        viewModel.onAction(HomeUiAction.KubeconfigInputChanged("apiVersion: v1"))

        assertEquals(null, viewModel.uiState.value.kubeconfigError)
    }

    @Test
    fun `ConnectAndSaveSubmitted with blank input sets error`() = runTest(testDispatcher) {
        viewModel.onAction(HomeUiAction.OpenAddClusterSheet)
        viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.kubeconfigError)
    }

    @Test
    fun `ConnectAndSaveSubmitted with valid kubeconfig saves, sets active and loads pods`() =
        runTest(testDispatcher) {
            val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.showAddClusterSheet)
            assertEquals(1, state.clusters.size)
            assertEquals("test-cluster", state.activeCluster?.name)
            assertEquals(2, state.pods.size)
            assertEquals(ClusterConnectionStatus.CONNECTED, state.clusterConnectionStatus)
        }

    @Test
    fun `SelectNamespace updates selected namespace and filters pods`() = runTest(testDispatcher) {
        val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

        viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
        viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
        advanceUntilIdle()

        viewModel.onAction(HomeUiAction.SelectNamespace("kube-system"))
        advanceUntilIdle()

        assertEquals("kube-system", viewModel.uiState.value.selectedNamespace)
        assertEquals(1, viewModel.uiState.value.pods.size)
        assertEquals("coredns-1", viewModel.uiState.value.pods.first().name)
    }

    @Test
    fun `RefreshWorkloads triggers refresh and updates lastRefreshedAt`() =
        runTest(testDispatcher) {
            val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            viewModel.onAction(HomeUiAction.RefreshWorkloads)
            advanceUntilIdle()

            val afterRefresh = viewModel.uiState.value.lastRefreshedAt
            assertFalse(viewModel.uiState.value.isRefreshing)
            assertNotNull(afterRefresh)
        }

    @Test
    fun `network reconnection triggers auto-refresh when active cluster exists`() =
        runTest(testDispatcher) {
            val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            // Network goes offline
            onlineFlow.value = false
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isOnline)
            assertEquals(
                ClusterConnectionStatus.DISCONNECTED,
                viewModel.uiState.value.clusterConnectionStatus,
            )

            // Network comes back online
            onlineFlow.value = true
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isOnline)
            assertNotNull(viewModel.uiState.value.activeCluster)
        }

    @Test
    fun `selecting specific namespace updates selected namespace and filters pods`() =
        runTest(testDispatcher) {
            val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            viewModel.onAction(HomeUiAction.SelectNamespace("default"))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("default", state.selectedNamespace)
            assertEquals(1, state.pods.size)
        }

    @Test
    fun `request delete namespace updates state and dismiss clears state`() =
        runTest(testDispatcher) {
            viewModel.onAction(HomeUiAction.RequestDeleteNamespace("test-namespace"))
            assertEquals("test-namespace", viewModel.uiState.value.namespaceToDelete)

            viewModel.onAction(HomeUiAction.DismissDeleteNamespace)
            assertEquals(null, viewModel.uiState.value.namespaceToDelete)
        }

    @Test
    fun `confirm delete namespace clears delete state and deletes namespace`() =
        runTest(testDispatcher) {
            val validYaml = """
            apiVersion: v1
            kind: Config
            clusters:
            - cluster:
                server: https://127.0.0.1:6443
              name: test-cluster
            contexts:
            - context:
                cluster: test-cluster
                user: test-user
              name: test-cluster
            current-context: test-cluster
        """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            viewModel.onAction(HomeUiAction.SelectNamespace("test-namespace"))
            advanceUntilIdle()

            viewModel.onAction(HomeUiAction.RequestDeleteNamespace("test-namespace"))
            assertEquals("test-namespace", viewModel.uiState.value.namespaceToDelete)

            viewModel.onAction(HomeUiAction.ConfirmDeleteNamespace("test-namespace"))
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.namespaceToDelete)
            assertEquals("All Namespaces", viewModel.uiState.value.selectedNamespace)
        }

    @Test
    fun `test cluster connection sets testingClusterId during execution and clears upon completion`() =
        runTest(testDispatcher) {
            val validYaml = """
                apiVersion: v1
                kind: Config
                clusters:
                - cluster:
                    server: https://127.0.0.1:6443
                  name: test-cluster
            """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            val clusterId = viewModel.uiState.value.activeCluster?.id ?: ""
            viewModel.onAction(HomeUiAction.TestClusterConnectionClicked(clusterId))

            // After execution and delay
            advanceUntilIdle()

            assertEquals(null, viewModel.uiState.value.testingClusterId)
            assertEquals(null, viewModel.uiState.value.clusterTestStatuses[clusterId])
            assertEquals(
                ClusterConnectionStatus.CONNECTED,
                viewModel.uiState.value.clusterConnectionStatus
            )
        }

    @Test
    fun `observing active cluster triggers health check and updates status`() =
        runTest(testDispatcher) {
            val validYaml = """
                apiVersion: v1
                kind: Config
                clusters:
                - cluster:
                    server: https://127.0.0.1:6443
                  name: test-cluster
            """.trimIndent()

            viewModel.onAction(HomeUiAction.KubeconfigInputChanged(validYaml))
            viewModel.onAction(HomeUiAction.ConnectAndSaveSubmitted)
            advanceUntilIdle()

            assertEquals(
                ClusterConnectionStatus.CONNECTED,
                viewModel.uiState.value.clusterConnectionStatus
            )
        }

    @Test
    fun `clearing cached data keeps clusters and confirms`() = runTest(testDispatcher) {
        val toasts = mutableListOf<String>()
        val job = launch {
            viewModel.effects.collect { if (it is HomeUiEffect.ShowToast) toasts += it.message }
        }
        advanceUntilIdle()
        val clustersBefore = fakeClusterRepository.clustersFlow.value

        viewModel.onAction(HomeUiAction.ClearCachedData)
        advanceUntilIdle()

        assertTrue(fakeClusterRepository.cacheCleared)
        assertEquals(clustersBefore, fakeClusterRepository.clustersFlow.value)
        assertTrue(toasts.any { it.startsWith("Cached cluster data cleared") })
        job.cancel()
    }

    // Switch from an unreachable cluster to a healthy one: the old cluster's slow failure must
    // not mark the new one disconnected or raise a toast about it.
    @Test
    fun `a slow refresh of the previous cluster does not overwrite the new one`() = runTest(testDispatcher) {
        val toasts = mutableListOf<String>()
        val job = launch {
            viewModel.effects.collect { if (it is HomeUiEffect.ShowToast) toasts += it.message }
        }
        fakePodRepository.refreshBehaviour["a"] = 10_000L to Result.Error(AppError.Network("timeout"))
        fakeClusterRepository.clustersFlow.value = listOf(
            Cluster(id = "a", name = "a", serverUrl = "https://a:6443", rawKubeconfig = "x", contextName = "a", isActive = true),
            Cluster(id = "b", name = "b", serverUrl = "https://b:6443", rawKubeconfig = "x", contextName = "b"),
        )
        runCurrent()

        fakeClusterRepository.setActiveCluster("b")
        advanceUntilIdle()

        assertEquals("b", viewModel.uiState.value.activeCluster?.id)
        assertEquals(ClusterConnectionStatus.CONNECTED, viewModel.uiState.value.clusterConnectionStatus)
        assertTrue(toasts.none { it.startsWith("Couldn't load your pods") })
        job.cancel()
    }

    private class FakeClusterRepository : ClusterRepository {
        val clustersFlow = MutableStateFlow<List<Cluster>>(emptyList())

        override fun getClustersStream(): Flow<List<Cluster>> = clustersFlow.asStateFlow()

        override fun getActiveClusterStream(): Flow<Cluster?> =
            clustersFlow.map { list -> list.firstOrNull { it.isActive } }

        override suspend fun getClusterById(id: String): Cluster? =
            clustersFlow.value.firstOrNull { it.id == id }

        override suspend fun addCluster(
            kubeconfigRaw: String,
            customName: String?,
            setAsActive: Boolean
        ): Result<Cluster> {
            val cluster = Cluster(
                id = "cluster-${System.currentTimeMillis()}",
                name = customName ?: "test-cluster",
                serverUrl = "https://127.0.0.1:6443",
                rawKubeconfig = kubeconfigRaw,
                contextName = "test-cluster",
                isActive = setAsActive,
                status = ClusterStatus.CONNECTED,
            )
            val current =
                clustersFlow.value.map { if (setAsActive) it.copy(isActive = false) else it }
            clustersFlow.value = current + cluster
            return Result.Success(cluster)
        }

        override suspend fun setActiveCluster(id: String): Result<Unit> {
            clustersFlow.value = clustersFlow.value.map { it.copy(isActive = it.id == id) }
            return Result.Success(Unit)
        }

        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> {
            clustersFlow.value = clustersFlow.value.map {
                if (it.id == id) it.copy(name = newName) else it
            }
            return Result.Success(Unit)
        }

        override suspend fun deleteCluster(id: String): Result<Unit> {
            clustersFlow.value = clustersFlow.value.filterNot { it.id == id }
            return Result.Success(Unit)
        }

        var cacheCleared = false

        override suspend fun clearCachedData(): Result<Unit> {
            cacheCleared = true
            return Result.Success(Unit)
        }

        override suspend fun testConnection(kubeconfigRaw: String): Result<String> =
            Result.Success("Reachable")

        override suspend fun testClusterById(id: String): Result<String> =
            Result.Success("Reachable")

        override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    serverVersion = "v1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    serverVersion = "v1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun updateClusterStatus(
            id: String,
            status: ClusterStatus,
            lastConnectedAt: Long?
        ): Result<Unit> {
            clustersFlow.value = clustersFlow.value.map {
                if (it.id == id) it.copy(status = status, lastConnectedAt = lastConnectedAt) else it
            }
            return Result.Success(Unit)
        }

        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
    }

    private class FakePodRepository : PodRepository {
        private val lastRefreshedFlow = MutableStateFlow<Long?>(1700000000000L)

        override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> {
            if (clusterId == null) return flowOf(emptyList())
            val all = listOf(
                Pod(
                    id = "p-1",
                    name = "coredns-1",
                    namespace = "kube-system",
                    status = PodStatus.RUNNING,
                ),
                Pod(
                    id = "p-2",
                    name = "nginx-web",
                    namespace = "default",
                    status = PodStatus.RUNNING,
                ),
            )
            val filtered = if (!namespace.isNullOrBlank() && namespace != "All Namespaces") {
                all.filter { it.namespace == namespace }
            } else {
                all
            }
            return flowOf(filtered)
        }

        override fun getNamespacesStream(clusterId: String?): Flow<List<String>> {
            return flowOf(listOf("All Namespaces", "default", "kube-system", "monitoring"))
        }

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> {
            return lastRefreshedFlow.asStateFlow()
        }

        /** Per-cluster refresh behaviour: how long it takes and what it returns. */
        val refreshBehaviour = mutableMapOf<String, Pair<Long, Result<Unit>>>()

        override suspend fun refreshWorkloads(
            clusterId: String?,
            namespace: String?
        ): Result<Unit> {
            refreshBehaviour[clusterId]?.let { (delayMs, result) ->
                delay(delayMs)
                return result
            }
            lastRefreshedFlow.value = System.currentTimeMillis()
            return Result.Success(Unit)
        }

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
            return Result.Success(
                PodDetails(
                    name = podName,
                    namespace = namespace,
                    status = PodStatus.RUNNING,
                ),
            )
        }

        override suspend fun deletePod(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<Unit> {
            return Result.Success(Unit)
        }

        override suspend fun deleteNamespace(
            clusterId: String?,
            namespace: String
        ): Result<Unit> {
            return Result.Success(Unit)
        }

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
            return Result.Success("Fake logs output")
        }

        override fun streamPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?,
        ): Flow<String> {
            return flowOf("Fake streamed log line")
        }

        override suspend fun execCommand(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            stdin: String,
        ): Result<CommandExecResult> {
            return Result.Success(CommandExecResult())
        }

        override suspend fun startTerminalSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit,
        ): Result<TerminalSession> {
            val session = object : TerminalSession {
                override fun write(input: String) {}
                override fun writeBytes(bytes: ByteArray) {}
                override fun close() {}
            }
            return Result.Success(session)
        }

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
            val session = object : TerminalSession {
                override fun write(input: String) {}
                override fun writeBytes(bytes: ByteArray) {}
                override fun close() {}
            }
            return Result.Success(session)
        }
    }
}
