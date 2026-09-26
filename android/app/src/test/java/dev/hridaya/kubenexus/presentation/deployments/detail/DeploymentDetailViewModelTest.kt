package dev.hridaya.kubenexus.presentation.deployments.detail

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.DeploymentDetails
import dev.hridaya.kubenexus.domain.model.DeploymentSummary
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.DeploymentRepository
import dev.hridaya.kubenexus.domain.repository.PodRepository
import dev.hridaya.kubenexus.domain.usecase.DeleteDeploymentUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetDeploymentDetailsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetDeploymentsStreamUseCase
import dev.hridaya.kubenexus.domain.usecase.GetDeploymentsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetPodsBySelectorUseCase
import dev.hridaya.kubenexus.domain.usecase.RestartDeploymentUseCase
import dev.hridaya.kubenexus.domain.usecase.ScaleDeploymentUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DeploymentDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var fakeDeploymentRepository: FakeDeploymentRepository
    private lateinit var fakePodRepository: FakePodRepository

    private val sampleSummary = DeploymentSummary(
        id = "c1_default_nginx",
        name = "nginx",
        namespace = "default",
        desiredReplicas = 3,
        readyReplicas = 3,
        availableReplicas = 3,
        images = listOf("nginx:1.27"),
        creationTimestampMillis = 1000L,
    )

    private val sampleDetails = DeploymentDetails(
        name = "nginx",
        namespace = "default",
        creationTimestampMillis = 1000L,
        desiredReplicas = 3,
        readyReplicas = 3,
        availableReplicas = 3,
        updatedReplicas = 3,
        strategyType = "RollingUpdate",
        minReadySeconds = 0,
        selectorMatchLabels = mapOf("app" to "nginx"),
        labels = mapOf("app" to "nginx"),
        annotations = emptyMap(),
        conditions = emptyList(),
        images = listOf("nginx:1.27"),
        events = emptyList(),
    )

    private val samplePod = Pod(
        id = "pod-1",
        name = "nginx-78f56c879d-gqw87",
        namespace = "default",
        status = PodStatus.RUNNING,
        readyContainers = "1/1",
        restarts = 0,
        creationTimestampMillis = 1000L,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClusterRepository = FakeClusterRepository()
        fakeDeploymentRepository = FakeDeploymentRepository()
        fakePodRepository = FakePodRepository()

        fakeClusterRepository.activeClusterFlow.value = Cluster(
            id = "c1",
            name = "Test Cluster",
            serverUrl = "https://k8s.example.com",
            contextName = "default",
            userName = "admin",
            namespace = "default",
            rawKubeconfig = "",
            status = ClusterStatus.CONNECTED,
            isActive = true,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): DeploymentDetailViewModel {
        return DeploymentDetailViewModel(
            deploymentName = "nginx",
            namespace = "default",
            getActiveClusterUseCase = GetActiveClusterUseCase(fakeClusterRepository, testDispatcherProvider),
            getDeploymentDetailsUseCase = GetDeploymentDetailsUseCase(fakeDeploymentRepository),
            getDeploymentsUseCase = GetDeploymentsUseCase(fakeDeploymentRepository),
            getDeploymentsStreamUseCase = GetDeploymentsStreamUseCase(fakeDeploymentRepository),
            scaleDeploymentUseCase = ScaleDeploymentUseCase(fakeDeploymentRepository),
            restartDeploymentUseCase = RestartDeploymentUseCase(fakeDeploymentRepository),
            deleteDeploymentUseCase = DeleteDeploymentUseCase(fakeDeploymentRepository),
            getPodsBySelectorUseCase = GetPodsBySelectorUseCase(fakePodRepository),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `loads cached summary immediately from Room stream and fetches live details`() =
        runTest(testDispatcher) {
            fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)
            fakeDeploymentRepository.deploymentDetailsResult = Result.Success(sampleDetails)
            fakePodRepository.podsToReturn = listOf(samplePod)

            val viewModel = createViewModel()
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.isLoading)
            assertEquals(sampleSummary, state.deployment)
            assertFalse(state.isDetailsLoading)
            assertEquals(sampleDetails, state.details)
            assertEquals(listOf(samplePod), state.associatedPods)
            assertNull(state.errorMessage)
            assertNull(state.detailsErrorMessage)

            viewModel.viewModelScope.cancel()
        }

    @Test
    fun `performScale scales deployment and reloads on success`() = runTest(testDispatcher) {
        fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)
        fakeDeploymentRepository.deploymentDetailsResult = Result.Success(sampleDetails)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val effects = mutableListOf<DeploymentDetailUiEffect>()
        val job = launch { viewModel.effects.toList(effects) }

        viewModel.onAction(DeploymentDetailUiAction.OpenScaleDialog)
        assertTrue(viewModel.uiState.value.showScaleDialog)
        assertEquals(3, viewModel.uiState.value.scaleInput)

        viewModel.onAction(DeploymentDetailUiAction.ScaleInputChanged(5))
        assertEquals(5, viewModel.uiState.value.scaleInput)

        viewModel.onAction(DeploymentDetailUiAction.ConfirmScale)
        advanceUntilIdle()

        assertEquals(5, fakeDeploymentRepository.capturedScaleReplicas)
        assertEquals("c1", fakeDeploymentRepository.capturedScaleClusterId)
        assertEquals("default", fakeDeploymentRepository.capturedScaleNamespace)
        assertEquals("nginx", fakeDeploymentRepository.capturedScaleName)

        assertTrue(effects.any { it is DeploymentDetailUiEffect.ShowSnackbar && it.message.contains("5") })

        job.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `performScale surfaces error and emits snackbar when scale fails`() = runTest(testDispatcher) {
        fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)
        fakeDeploymentRepository.scaleResult = Result.Error(AppError.Network("RBAC denied scaling"))

        val viewModel = createViewModel()
        advanceUntilIdle()

        val effects = mutableListOf<DeploymentDetailUiEffect>()
        val job = launch { viewModel.effects.toList(effects) }

        viewModel.onAction(DeploymentDetailUiAction.OpenScaleDialog)
        viewModel.onAction(DeploymentDetailUiAction.ScaleInputChanged(10))
        viewModel.onAction(DeploymentDetailUiAction.ConfirmScale)
        advanceUntilIdle()

        assertEquals("RBAC denied scaling", viewModel.uiState.value.mutationErrorMessage)
        assertTrue(effects.any { it is DeploymentDetailUiEffect.ShowSnackbar && it.message.contains("RBAC denied") })

        job.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `performRestart triggers rollout restart and refreshes on success`() = runTest(testDispatcher) {
        fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)
        fakeDeploymentRepository.deploymentDetailsResult = Result.Success(sampleDetails)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val effects = mutableListOf<DeploymentDetailUiEffect>()
        val job = launch { viewModel.effects.toList(effects) }

        viewModel.onAction(DeploymentDetailUiAction.OpenRestartDialog)
        assertTrue(viewModel.uiState.value.showRestartDialog)

        viewModel.onAction(DeploymentDetailUiAction.ConfirmRestart)
        advanceUntilIdle()

        assertEquals("c1", fakeDeploymentRepository.capturedRestartClusterId)
        assertEquals("default", fakeDeploymentRepository.capturedRestartNamespace)
        assertEquals("nginx", fakeDeploymentRepository.capturedRestartName)

        assertTrue(effects.any { it is DeploymentDetailUiEffect.ShowSnackbar && (it.message.contains("Restarted") || it.message.contains("restart")) })

        job.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `performDelete deletes deployment and emits NavigateBack on success`() = runTest(testDispatcher) {
        fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val effects = mutableListOf<DeploymentDetailUiEffect>()
        val job = launch { viewModel.effects.toList(effects) }

        viewModel.onAction(DeploymentDetailUiAction.OpenDeleteDialog)
        assertTrue(viewModel.uiState.value.showDeleteDialog)

        viewModel.onAction(DeploymentDetailUiAction.ConfirmDelete)
        advanceUntilIdle()

        assertEquals("c1", fakeDeploymentRepository.capturedDeleteClusterId)
        assertEquals("default", fakeDeploymentRepository.capturedDeleteNamespace)
        assertEquals("nginx", fakeDeploymentRepository.capturedDeleteName)

        assertTrue(effects.contains(DeploymentDetailUiEffect.NavigateBack))

        job.cancel()
        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `dialog actions toggle show state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onAction(DeploymentDetailUiAction.OpenScaleDialog)
        assertTrue(viewModel.uiState.value.showScaleDialog)
        viewModel.onAction(DeploymentDetailUiAction.DismissScaleDialog)
        assertFalse(viewModel.uiState.value.showScaleDialog)

        viewModel.onAction(DeploymentDetailUiAction.OpenRestartDialog)
        assertTrue(viewModel.uiState.value.showRestartDialog)
        viewModel.onAction(DeploymentDetailUiAction.DismissRestartDialog)
        assertFalse(viewModel.uiState.value.showRestartDialog)

        viewModel.onAction(DeploymentDetailUiAction.OpenDeleteDialog)
        assertTrue(viewModel.uiState.value.showDeleteDialog)
        viewModel.onAction(DeploymentDetailUiAction.DismissDeleteDialog)
        assertFalse(viewModel.uiState.value.showDeleteDialog)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `refresh action triggers reload and updates lastRefreshedAt`() = runTest(testDispatcher) {
        fakeDeploymentRepository.cachedDeploymentsFlow.value = listOf(sampleSummary)
        fakeDeploymentRepository.deploymentDetailsResult = Result.Success(sampleDetails)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialRefreshedAt = viewModel.uiState.value.lastRefreshedAt
        assertTrue(initialRefreshedAt != null && initialRefreshedAt > 0)

        viewModel.onAction(DeploymentDetailUiAction.Refresh)
        advanceUntilIdle()

        val refreshedAt = viewModel.uiState.value.lastRefreshedAt
        assertTrue(refreshedAt != null && refreshedAt >= initialRefreshedAt!!)
        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.viewModelScope.cancel()
    }

    private class FakeClusterRepository : ClusterRepository {
        val activeClusterFlow = MutableStateFlow<Cluster?>(null)

        override fun getActiveClusterStream(): Flow<Cluster?> = activeClusterFlow
        override fun getClustersStream(): Flow<List<Cluster>> = flowOf(emptyList())
        override suspend fun addCluster(kubeconfigRaw: String, customName: String?, setAsActive: Boolean): Result<Cluster> =
            Result.Error(AppError.Unknown("not implemented"))
        override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)
        override suspend fun testConnection(kubeconfigRaw: String): Result<String> = Result.Success("v1.28.0")
        override suspend fun testClusterById(id: String): Result<String> = Result.Success("v1.28.0")
        override suspend fun checkClusterHealth(id: String): Result<ClusterHealth> =
            Result.Error(AppError.NotFound("not found"))
        override suspend fun checkClusterHealthByKubeconfig(kubeconfigRaw: String): Result<ClusterHealth> =
            Result.Error(AppError.NotFound("not found"))
        override suspend fun updateClusterStatus(id: String, status: ClusterStatus, lastConnectedAt: Long?): Result<Unit> = Result.Success(Unit)
        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
        override suspend fun getClusterById(id: String): Cluster? = activeClusterFlow.value
    }

    private class FakeDeploymentRepository : DeploymentRepository {
        val cachedDeploymentsFlow = MutableStateFlow<List<DeploymentSummary>>(emptyList())
        var deploymentDetailsResult: Result<DeploymentDetails> = Result.Error(AppError.NotFound("not found"))

        var capturedScaleClusterId: String? = null
        var capturedScaleNamespace: String? = null
        var capturedScaleName: String? = null
        var capturedScaleReplicas: Int? = null
        var scaleResult: Result<Unit> = Result.Success(Unit)

        var capturedRestartClusterId: String? = null
        var capturedRestartNamespace: String? = null
        var capturedRestartName: String? = null
        var restartResult: Result<Unit> = Result.Success(Unit)

        var capturedDeleteClusterId: String? = null
        var capturedDeleteNamespace: String? = null
        var capturedDeleteName: String? = null
        var deleteResult: Result<Unit> = Result.Success(Unit)

        override suspend fun createFromManifest(clusterId: String?, manifestYaml: String): Result<Unit> = Result.Success(Unit)

        override suspend fun getDeployments(clusterId: String?, namespace: String?): Result<List<DeploymentSummary>> =
            Result.Success(cachedDeploymentsFlow.value)

        override fun getDeploymentsStream(clusterId: String?, namespace: String?): Flow<List<DeploymentSummary>> =
            cachedDeploymentsFlow

        override suspend fun syncDeployments(clusterId: String?, namespace: String?): Result<Unit> = Result.Success(Unit)

        override suspend fun getDeploymentDetails(clusterId: String?, namespace: String, name: String): Result<DeploymentDetails> =
            deploymentDetailsResult

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)

        override suspend fun scaleDeployment(clusterId: String?, namespace: String, name: String, replicas: Int): Result<Unit> {
            capturedScaleClusterId = clusterId
            capturedScaleNamespace = namespace
            capturedScaleName = name
            capturedScaleReplicas = replicas
            return scaleResult
        }

        override suspend fun restartDeployment(clusterId: String?, namespace: String, name: String): Result<Unit> {
            capturedRestartClusterId = clusterId
            capturedRestartNamespace = namespace
            capturedRestartName = name
            return restartResult
        }

        override suspend fun deleteDeployment(clusterId: String?, namespace: String, name: String): Result<Unit> {
            capturedDeleteClusterId = clusterId
            capturedDeleteNamespace = namespace
            capturedDeleteName = name
            return deleteResult
        }
    }

    private class FakePodRepository : PodRepository {
        var podsToReturn: List<Pod> = emptyList()

        override suspend fun getPodsBySelector(
            clusterId: String?,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(podsToReturn)

        override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> = flowOf(emptyList())
        override fun getNamespacesStream(clusterId: String?): Flow<List<String>> = flowOf(emptyList())
        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)
        override suspend fun refreshWorkloads(clusterId: String?, namespace: String?): Result<Unit> = Result.Success(Unit)
        override suspend fun listPodsBySelector(rawKubeconfig: String, namespace: String?, labelSelector: String): Result<List<Pod>> = Result.Success(emptyList())
        override suspend fun describePod(clusterId: String?, namespace: String, podName: String): Result<PodDetails> = Result.Error(AppError.NotFound("not found"))
        override suspend fun getPodMetrics(clusterId: String?, namespace: String?): Result<List<PodMetricSample>> = Result.Success(emptyList())
        override suspend fun getSinglePodMetrics(clusterId: String?, namespace: String, podName: String): Result<PodMetricSample?> = Result.Success(null)
        override suspend fun deletePod(clusterId: String?, namespace: String, podName: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteNamespace(clusterId: String?, namespace: String): Result<Unit> = Result.Success(Unit)
        override suspend fun createNamespace(clusterId: String?, name: String): Result<Unit> = Result.Success(Unit)
        override suspend fun createPodFromManifest(clusterId: String?, manifestYaml: String): Result<Unit> = Result.Success(Unit)
        override suspend fun getPodLogs(clusterId: String?, namespace: String, podName: String, containerName: String?, tailLines: Long?): Result<String> = Result.Success("")
        override fun streamPodLogs(clusterId: String?, namespace: String, podName: String, containerName: String?, tailLines: Long?): Flow<String> = flowOf()
        override suspend fun execCommand(clusterId: String?, namespace: String, podName: String, containerName: String, command: String, stdin: String): Result<CommandExecResult> = Result.Error(AppError.Unknown("not implemented"))
        override suspend fun startTerminalSession(clusterId: String?, namespace: String, podName: String, containerName: String, onStdout: (String) -> Unit, onStderr: (String) -> Unit, onError: (String) -> Unit, onDone: () -> Unit): Result<TerminalSession> = Result.Error(AppError.Unknown("not implemented"))
        override suspend fun startExecSession(clusterId: String?, namespace: String, podName: String, containerName: String, command: String, tty: Boolean, onStdout: (String) -> Unit, onStderr: (String) -> Unit, onError: (String) -> Unit, onDone: () -> Unit): Result<TerminalSession> = Result.Error(AppError.Unknown("not implemented"))
    }
}
