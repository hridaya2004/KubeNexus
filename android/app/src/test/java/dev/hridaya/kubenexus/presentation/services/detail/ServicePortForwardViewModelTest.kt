package dev.hridaya.kubenexus.presentation.services.detail

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionManager
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.PortForwardListener
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.PodRepository
import dev.hridaya.kubenexus.domain.repository.PortForwardRepository
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ResolveServiceForwardTargetUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServicePortForwardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepository: FakePortForwardRepository
    private lateinit var sessionManager: PortForwardSessionManager
    private lateinit var fakePodRepository: FakePodRepository
    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var viewModel: ServicePortForwardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePortForwardRepository()
        sessionManager = PortForwardSessionManager(
            repository = fakeRepository,
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = testDispatcherProvider,
        )
        fakePodRepository = FakePodRepository()
        fakeClusterRepository = FakeClusterRepository()

        viewModel = ServicePortForwardViewModel(
            serviceName = "web-svc",
            namespace = "default",
            sessionManager = sessionManager,
            resolveServiceForwardTargetUseCase = ResolveServiceForwardTargetUseCase(
                fakePodRepository
            ),
            getActiveClusterUseCase = GetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider
            ),
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `start resolves target pod and opens tunnel`() = runTest(testDispatcher) {
        runCurrent()
        val service = ServiceDetails(
            name = "web-svc",
            namespace = "default",
            creationTimestampMillis = 1000L,
            type = "ClusterIP",
            clusterIP = "10.96.0.1",
            clusterIPs = listOf("10.96.0.1"),
            externalIPs = emptyList(),
            selector = mapOf("app" to "web"),
            ports = listOf(
                ServicePortDetail(
                    port = 80,
                    targetPort = 8080,
                    nodePort = null,
                    protocol = "TCP",
                    name = "http",
                ),
            ),
            labels = emptyMap(),
            annotations = emptyMap(),
            events = emptyList(),
        )
        fakePodRepository.podsToReturn = listOf(
            Pod(
                id = "1",
                name = "web-pod-1",
                namespace = "default",
                status = PodStatus.RUNNING,
                readyContainers = "1/1",
            ),
        )

        viewModel.start(service, localPort = 3000, servicePort = 80)
        runCurrent()

        val state = viewModel.uiState.value
        assertFalse(state.isStarting)
        assertEquals(1, state.activeForwards.size)
        val forward = state.activeForwards.first()
        assertEquals("web-pod-1", forward.podName)
        assertEquals(3000, forward.localPort)
        assertEquals(80, forward.remotePort)
    }

    private class FakeClusterRepository : ClusterRepository {
        private val activeCluster = Cluster(
            id = "c-1",
            name = "prod-cluster",
            serverUrl = "https://127.0.0.1:6443",
            rawKubeconfig = "fake-kubeconfig",
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
            setAsActive: Boolean
        ): Result<Cluster> = Result.Success(activeCluster)

        override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun testConnection(kubeconfigRaw: String): Result<String> =
            Result.Success("OK")

        override suspend fun testClusterById(id: String): Result<String> = Result.Success("OK")
        override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    healthz = true,
                    serverVersion = "1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
            Result.Success(
                ClusterHealth(
                    livez = true,
                    readyz = true,
                    healthz = true,
                    serverVersion = "1.30.0",
                    statusMessage = "Ready"
                )
            )

        override suspend fun updateClusterStatus(
            id: String,
            status: ClusterStatus,
            lastConnectedAt: Long?
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)


        override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)
        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
    }

    private class FakePortForwardRepository : PortForwardRepository {
        var nextId = 1
        override fun start(
            rawKubeconfig: String,
            namespace: String,
            podName: String,
            localPort: Int,
            remotePort: Int,
            listener: PortForwardListener,
        ): Result<String> = Result.Success("pf-${nextId++}")

        override fun stop(handleId: String): Result<Unit> = Result.Success(Unit)
    }

    private class FakePodRepository : PodRepository {
        var podsToReturn: List<Pod> = emptyList()

        override suspend fun listPodsBySelector(
            rawKubeconfig: String,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(podsToReturn)

        override suspend fun getPodsBySelector(
            clusterId: String?,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(podsToReturn)

        override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> =
            flowOf(podsToReturn)

        override fun getNamespacesStream(clusterId: String?): Flow<List<String>> =
            flowOf(emptyList())

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)
        override suspend fun refreshWorkloads(
            clusterId: String?,
            namespace: String?
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun describePod(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<PodDetails> = Result.Error(AppError.NotFound())

        override suspend fun getPodMetrics(
            clusterId: String?,
            namespace: String?
        ): Result<List<PodMetricSample>> = Result.Success(emptyList())

        override suspend fun getSinglePodMetrics(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<PodMetricSample?> = Result.Success(null)

        override suspend fun deletePod(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteNamespace(clusterId: String?, namespace: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun createNamespace(clusterId: String?, name: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun createPodFromManifest(
            clusterId: String?,
            manifestYaml: String
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun getPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?
        ): Result<String> = Result.Success("")

        override fun streamPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?
        ): Flow<String> = flowOf("")

        override suspend fun execCommand(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            stdin: String
        ): Result<CommandExecResult> = Result.Error(AppError.NotFound())

        override suspend fun startTerminalSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit
        ): Result<TerminalSession> = Result.Error(AppError.NotFound())

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
            onDone: () -> Unit
        ): Result<TerminalSession> = Result.Error(AppError.NotFound())
    }
}
