package dev.hridaya.kubenexus.presentation.home

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.network.NetworkMonitor
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterConnectionStatus
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.usecase.AddClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.CheckClusterHealthUseCase
import dev.hridaya.kubenexus.domain.usecase.ClearCachedDataUseCase
import dev.hridaya.kubenexus.domain.usecase.DeleteClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.DeleteNamespaceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.GetClustersUseCase
import dev.hridaya.kubenexus.domain.usecase.GetNamespacesUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveLastSyncedUseCase
import dev.hridaya.kubenexus.domain.usecase.RefreshWorkloadsUseCase
import dev.hridaya.kubenexus.domain.usecase.SetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.TestClusterConnectionUseCase
import dev.hridaya.kubenexus.domain.usecase.UpdateClusterNameUseCase
import dev.hridaya.kubenexus.presentation.testing.FakeNamespaceRepository
import dev.hridaya.kubenexus.presentation.testing.FakeResourceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
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
    private val resources = FakeResourceRepository()
    private val namespaces = FakeNamespaceRepository(names = listOf("default", "kube-system", "monitoring"))
    private val onlineFlow = MutableStateFlow(true)
    private val fakeNetworkMonitor =
        object : NetworkMonitor {
            override val isOnline: Flow<Boolean> = onlineFlow
        }
    private lateinit var viewModel: HomeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeClusterRepository = FakeClusterRepository()
        resources.lastSynced.value = 1_700_000_000_000L
        resources.onSync = {
            resources.lastSynced.value = System.currentTimeMillis()
            resources.syncResult
        }
        onlineFlow.value = true
        viewModel = HomeViewModel(
            getClustersUseCase = GetClustersUseCase(fakeClusterRepository, testDispatcherProvider),
            getActiveClusterUseCase = GetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider,
            ),
            getNamespacesUseCase = GetNamespacesUseCase(namespaces),
            observeLastSynced = ObserveLastSyncedUseCase(resources),
            refreshWorkloadsUseCase = RefreshWorkloadsUseCase(namespaces, resources),
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
            deleteNamespaceUseCase = DeleteNamespaceUseCase(namespaces),
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

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clusters are reported loaded only once they have been read`() = runTest(testDispatcher) {
        assertFalse(viewModel.clustersLoaded.value)

        advanceUntilIdle()

        assertTrue(viewModel.clustersLoaded.value)
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
    fun `OpenFabMenu and DismissFabMenu update state`() {
        viewModel.onAction(HomeUiAction.OpenFabMenu)
        assertTrue(viewModel.uiState.value.showFabMenu)

        viewModel.onAction(HomeUiAction.DismissFabMenu)
        assertFalse(viewModel.uiState.value.showFabMenu)
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
    fun `ConnectAndSaveSubmitted with valid kubeconfig saves, sets active and refreshes its pods`() =
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
            assertTrue(resources.syncCalls.any { it.clusterId == state.activeCluster?.id && it.kind == ResourceKinds.Pods })
            assertEquals(ClusterConnectionStatus.CONNECTED, state.clusterConnectionStatus)
        }

    @Test
    fun `SelectNamespace updates the selected namespace`() = runTest(testDispatcher) {
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
    fun `refreshing after selecting a namespace syncs that namespace's pods`() =
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
            viewModel.onAction(HomeUiAction.RefreshWorkloads)
            advanceUntilIdle()

            val lastPodSync = resources.syncCalls.last { it.kind == ResourceKinds.Pods }
            assertEquals(NamespaceScope.Only("default"), lastPodSync.scope)
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

    @Test
    fun `switching clusters resets the namespace filter`() = runTest(testDispatcher) {
        fakeClusterRepository.clustersFlow.value = listOf(
            Cluster(id = "a", name = "a", serverUrl = "https://a:6443", contextName = "a", isActive = true),
            Cluster(id = "b", name = "b", serverUrl = "https://b:6443", contextName = "b"),
        )
        advanceUntilIdle()
        viewModel.onAction(HomeUiAction.SelectNamespace("kube-system"))
        advanceUntilIdle()
        assertEquals("kube-system", viewModel.uiState.value.selectedNamespace)

        fakeClusterRepository.setActiveCluster("b")
        advanceUntilIdle()

        assertEquals("b", viewModel.uiState.value.activeCluster?.id)
        assertEquals("All Namespaces", viewModel.uiState.value.selectedNamespace)
        val lastSyncOfB = resources.syncCalls.last { it.clusterId == "b" && it.kind == ResourceKinds.Pods }
        assertEquals(NamespaceScope.All, lastSyncOfB.scope)
        assertTrue(resources.syncCalls.none { it.clusterId == "b" && it.scope == NamespaceScope.Only("kube-system") })
    }

    // Switch from an unreachable cluster to a healthy one: the old cluster's slow failure must
    // not mark the new one disconnected or raise a toast about it.
    @Test
    fun `a slow refresh of the previous cluster does not overwrite the new one`() = runTest(testDispatcher) {
        val toasts = mutableListOf<String>()
        val job = launch {
            viewModel.effects.collect { if (it is HomeUiEffect.ShowToast) toasts += it.message }
        }
        resources.onSync = { call ->
            if (call.clusterId == "a") {
                delay(10_000L)
                Result.Error(AppError.Network("timeout"))
            } else {
                Result.Success(Unit)
            }
        }
        fakeClusterRepository.clustersFlow.value = listOf(
            Cluster(id = "a", name = "a", serverUrl = "https://a:6443", contextName = "a", isActive = true),
            Cluster(id = "b", name = "b", serverUrl = "https://b:6443", contextName = "b"),
        )
        runCurrent()

        fakeClusterRepository.setActiveCluster("b")
        advanceUntilIdle()

        assertEquals("b", viewModel.uiState.value.activeCluster?.id)
        assertEquals(ClusterConnectionStatus.CONNECTED, viewModel.uiState.value.clusterConnectionStatus)
        assertTrue(toasts.none { it.startsWith("Couldn't refresh the cluster") })
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

    }
}
