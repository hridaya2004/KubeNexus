package dev.hridaya.kubenexus.presentation.services.detail

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.K8sEventSummary
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.ServiceRepository
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetServiceDetailsUseCase
import dev.hridaya.kubenexus.domain.usecase.GetServicesStreamUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

@OptIn(ExperimentalCoroutinesApi::class)
class ServiceDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var fakeServiceRepository: FakeServiceRepository

    private val sampleSummary = ServiceSummary(
        id = "c1_default_web",
        name = "web",
        namespace = "default",
        type = "ClusterIP",
        clusterIP = "10.96.0.1",
        ports = listOf(ServicePortDetail(port = 80, targetPort = 8080, nodePort = null, protocol = "TCP", name = "http")),
        creationTimestampMillis = 1000L,
    )

    private val sampleDetails = ServiceDetails(
        name = "web",
        namespace = "default",
        creationTimestampMillis = 1000L,
        type = "ClusterIP",
        clusterIP = "10.96.0.1",
        clusterIPs = listOf("10.96.0.1"),
        externalIPs = emptyList(),
        selector = mapOf("app" to "web"),
        ports = listOf(ServicePortDetail(port = 80, targetPort = 8080, nodePort = null, protocol = "TCP", name = "http")),
        labels = mapOf("app" to "web"),
        annotations = emptyMap(),
        events = listOf(
            K8sEventSummary(
                type = "Normal",
                reason = "Created",
                message = "Service created",
                count = 1,
                lastTimestampMillis = 1000L,
            ),
        ),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClusterRepository = FakeClusterRepository()
        fakeServiceRepository = FakeServiceRepository()

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

    private fun createViewModel(): ServiceDetailViewModel {
        return ServiceDetailViewModel(
            serviceName = "web",
            namespace = "default",
            getActiveClusterUseCase = GetActiveClusterUseCase(fakeClusterRepository, testDispatcherProvider),
            getServiceDetailsUseCase = GetServiceDetailsUseCase(fakeServiceRepository),
            getServicesStreamUseCase = GetServicesStreamUseCase(fakeServiceRepository),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `loads cached summary immediately and fetches live service details`() = runTest(testDispatcher) {
        fakeServiceRepository.cachedServicesFlow.value = listOf(sampleSummary)
        fakeServiceRepository.serviceDetailsResult = Result.Success(sampleDetails)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.isRefreshing)
        assertEquals(sampleDetails, state.service)
        assertNotNull(state.lastRefreshedAt)
        assertNull(state.errorMessage)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `refresh action reloads service details and updates lastRefreshedAt`() = runTest(testDispatcher) {
        fakeServiceRepository.cachedServicesFlow.value = listOf(sampleSummary)
        fakeServiceRepository.serviceDetailsResult = Result.Success(sampleDetails)

        val viewModel = createViewModel()
        advanceUntilIdle()

        val initialRefreshedAt = viewModel.uiState.value.lastRefreshedAt
        assertNotNull(initialRefreshedAt)
        val requestsBeforeRefresh = fakeServiceRepository.detailsRequests
        fakeServiceRepository.serviceDetailsResult = Result.Success(sampleDetails.copy(type = "NodePort"))

        viewModel.onAction(ServiceDetailUiAction.Refresh)
        advanceUntilIdle()

        // Refresh must fetch again and show what it fetched, not just keep the old state.
        assertEquals(requestsBeforeRefresh + 1, fakeServiceRepository.detailsRequests)
        assertEquals("NodePort", viewModel.uiState.value.service?.type)
        val newRefreshedAt = viewModel.uiState.value.lastRefreshedAt
        assertNotNull(newRefreshedAt)
        assertTrue(newRefreshedAt!! >= initialRefreshedAt!!)
        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.viewModelScope.cancel()
    }

    @Test
    fun `toggle port forward dialog updates showPortForwardDialog state`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onAction(ServiceDetailUiAction.ShowPortForwardDialog(true))
        assertTrue(viewModel.uiState.value.showPortForwardDialog)

        viewModel.onAction(ServiceDetailUiAction.ShowPortForwardDialog(false))
        assertFalse(viewModel.uiState.value.showPortForwardDialog)

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

    private class FakeServiceRepository : ServiceRepository {
        val cachedServicesFlow = MutableStateFlow<List<ServiceSummary>>(emptyList())
        var serviceDetailsResult: Result<ServiceDetails> = Result.Error(AppError.NotFound("not found"))

        override suspend fun createFromManifest(clusterId: String?, manifestYaml: String): Result<Unit> = Result.Success(Unit)
        override fun getServicesStream(clusterId: String?, namespace: String?): Flow<List<ServiceSummary>> = cachedServicesFlow
        override suspend fun syncServices(clusterId: String?, namespace: String?): Result<Unit> = Result.Success(Unit)
        var detailsRequests = 0

        override suspend fun getServiceDetails(clusterId: String?, namespace: String, name: String): Result<ServiceDetails> {
            detailsRequests++
            return serviceDetailsResult
        }
        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)
    }
}
