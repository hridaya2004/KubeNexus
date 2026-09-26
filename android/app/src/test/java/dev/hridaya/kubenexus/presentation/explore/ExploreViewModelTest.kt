package dev.hridaya.kubenexus.presentation.explore

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterHealth
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import dev.hridaya.kubenexus.domain.model.ResourceExplain
import dev.hridaya.kubenexus.domain.model.ResourceField
import dev.hridaya.kubenexus.domain.repository.ClusterRepository
import dev.hridaya.kubenexus.domain.repository.ExploreRepository
import dev.hridaya.kubenexus.domain.usecase.ExplainResourceUseCase
import dev.hridaya.kubenexus.domain.usecase.GetAPIResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeClusterRepository: FakeClusterRepository
    private lateinit var fakeExploreRepository: FakeExploreRepository
    private lateinit var viewModel: ExploreViewModel

    @Before
    fun setUp() {
        fakeClusterRepository = FakeClusterRepository()
        fakeExploreRepository = FakeExploreRepository()

        viewModel = ExploreViewModel(
            getActiveClusterUseCase = GetActiveClusterUseCase(
                fakeClusterRepository,
                testDispatcherProvider
            ),
            getAPIResourcesUseCase = GetAPIResourcesUseCase(fakeExploreRepository),
            explainResourceUseCase = ExplainResourceUseCase(fakeExploreRepository),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `initial state loads resources and observes active cluster`() = runTest(testDispatcher) {
        val testCluster = Cluster(
            id = "c1",
            name = "prod-cluster",
            serverUrl = "https://127.0.0.1:6443",
            contextName = "prod",
            namespace = "default",
            rawKubeconfig = "yaml",
            isActive = true,
            status = ClusterStatus.CONNECTED,
        )
        fakeClusterRepository.setClusters(listOf(testCluster))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("prod-cluster", state.activeCluster?.name)
        assertEquals(3, state.resources.size)
        assertEquals(3, state.filteredResources.size)
        assertNotNull(state.lastRefreshedAt)
        assertFalse(state.isLoading)
    }

    @Test
    fun `search query filters resources by name, kind, and short names`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            viewModel.onAction(ExploreUiAction.UpdateSearchQuery("pod"))
            var state = viewModel.uiState.value
            assertEquals(1, state.filteredResources.size)
            assertEquals("pods", state.filteredResources[0].name)

            viewModel.onAction(ExploreUiAction.UpdateSearchQuery("deploy"))
            state = viewModel.uiState.value
            assertEquals(1, state.filteredResources.size)
            assertEquals("deployments", state.filteredResources[0].name)

            viewModel.onAction(ExploreUiAction.UpdateSearchQuery(""))
            state = viewModel.uiState.value
            assertEquals(3, state.filteredResources.size)
        }

    @Test
    fun `open and close search updates search state and resets query`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(ExploreUiAction.OpenSearch)
        assertTrue(viewModel.uiState.value.isSearchActive)

        viewModel.onAction(ExploreUiAction.UpdateSearchQuery("pod"))
        assertEquals("pod", viewModel.uiState.value.searchQuery)
        assertEquals(1, viewModel.uiState.value.filteredResources.size)

        viewModel.onAction(ExploreUiAction.CloseSearch)
        assertFalse(viewModel.uiState.value.isSearchActive)
        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(3, viewModel.uiState.value.filteredResources.size)
    }


    @Test
    fun `category selection filters namespaced vs cluster resources`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(ExploreUiAction.SelectCategory("Namespaced"))
        var state = viewModel.uiState.value
        assertEquals(2, state.filteredResources.size)
        assertTrue(state.filteredResources.all { it.namespaced })

        viewModel.onAction(ExploreUiAction.SelectCategory("Cluster"))
        state = viewModel.uiState.value
        assertEquals(1, state.filteredResources.size)
        assertEquals("nodes", state.filteredResources[0].name)
    }

    @Test
    fun `select resource loads explain schema details and dismiss clears it`() =
        runTest(testDispatcher) {
            advanceUntilIdle()

            val podResource = APIResource(name = "pods", kind = "Pod", groupVersion = "v1")
            viewModel.onAction(ExploreUiAction.SelectResource(podResource))
            advanceUntilIdle()

            var state = viewModel.uiState.value
            assertEquals(podResource, state.selectedResource)
            assertNotNull(state.explainDetails)
            assertEquals("Pod", state.explainDetails?.kind)
            assertEquals(2, state.filteredFields.size)

            // Filter fields
            viewModel.onAction(ExploreUiAction.UpdateFieldSearchQuery("spec"))
            state = viewModel.uiState.value
            assertEquals(1, state.filteredFields.size)
            assertEquals("spec", state.filteredFields[0].name)

            // Dismiss
            viewModel.onAction(ExploreUiAction.DismissExplain)
            state = viewModel.uiState.value
            assertNull(state.selectedResource)
            assertNull(state.explainDetails)
            assertEquals(0, state.filteredFields.size)
        }

    @Test
    fun `empty room cache fetches api resources exactly once on first observation`() =
        runTest(testDispatcher) {
            val testCluster = Cluster(
                id = "c1",
                name = "prod-cluster",
                serverUrl = "https://127.0.0.1:6443",
                contextName = "prod",
                namespace = "default",
                rawKubeconfig = "yaml",
                isActive = true,
                status = ClusterStatus.CONNECTED,
            )
            fakeExploreRepository.setResources(emptyList())
            fakeClusterRepository.setClusters(listOf(testCluster))
            advanceUntilIdle()

            // Nothing cached yet: one network fill-in is allowed.
            assertEquals(1, fakeExploreRepository.fetchCount)

            // Re-emissions of the same cluster must stay on Room only.
            advanceUntilIdle()
            assertEquals(1, fakeExploreRepository.fetchCount)
        }

    @Test
    fun `cached api resources render from room without any remote fetch`() =
        runTest(testDispatcher) {
            val testCluster = Cluster(
                id = "c1",
                name = "prod-cluster",
                serverUrl = "https://127.0.0.1:6443",
                contextName = "prod",
                namespace = "default",
                rawKubeconfig = "yaml",
                isActive = true,
                status = ClusterStatus.CONNECTED,
            )
            fakeClusterRepository.setClusters(listOf(testCluster))
            advanceUntilIdle()

            // Cache already holds the resource list: zero bridge calls.
            assertEquals(0, fakeExploreRepository.fetchCount)
            assertEquals(3, viewModel.uiState.value.resources.size)
        }

    @Test
    fun `user refresh explicitly triggers fetch`() =
        runTest(testDispatcher) {
            val testCluster = Cluster(
                id = "c1",
                name = "prod-cluster",
                serverUrl = "https://127.0.0.1:6443",
                contextName = "prod",
                namespace = "default",
                rawKubeconfig = "yaml",
                isActive = true,
                status = ClusterStatus.CONNECTED,
            )
            fakeClusterRepository.setClusters(listOf(testCluster))
            advanceUntilIdle()

            // Cached start: observing never touched the network.
            assertEquals(0, fakeExploreRepository.fetchCount)

            viewModel.onAction(ExploreUiAction.Refresh)
            advanceUntilIdle()

            // Only the explicit user action goes remote.
            assertEquals(1, fakeExploreRepository.fetchCount)
        }

    @Test
    fun `select resource uses cached explain without remote call`() =
        runTest(testDispatcher) {
            val cachedExplain = ResourceExplain(
                kind = "Pod",
                groupVersion = "v1",
                description = "Cached pod explain",
                fields = listOf(ResourceField("metadata", "ObjectMeta", "desc", false))
            )
            fakeExploreRepository.cachedExplain = cachedExplain

            val podResource = APIResource(name = "pods", kind = "Pod", groupVersion = "v1")
            viewModel.onAction(ExploreUiAction.SelectResource(podResource))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("Cached pod explain", state.explainDetails?.description)
            assertEquals(0, fakeExploreRepository.explainCount)

            // When user triggers retry / force refresh, explainResource is called
            viewModel.onAction(ExploreUiAction.RetryExplain(podResource))
            advanceUntilIdle()
            assertEquals(1, fakeExploreRepository.explainCount)
        }

    @Test
    fun `paged resources chunks items and loads next page on action`() =
        runTest(testDispatcher) {
            val manyResources = (1..65).map { i ->
                APIResource(
                    name = "resource-$i",
                    singularName = "resource-$i",
                    namespaced = true,
                    kind = "Resource$i",
                    groupVersion = "v1",
                )
            }
            fakeExploreRepository.setResources(manyResources)
            val testCluster = Cluster(
                id = "c1",
                name = "prod-cluster",
                serverUrl = "https://127.0.0.1:6443",
                contextName = "prod",
                namespace = "default",
                rawKubeconfig = "yaml",
                isActive = true,
                status = ClusterStatus.CONNECTED,
            )
            fakeClusterRepository.setClusters(listOf(testCluster))
            advanceUntilIdle()

            val state1 = viewModel.uiState.value
            assertEquals(65, state1.resources.size)
            assertEquals(65, state1.filteredResources.size)
            // Page size is 30 by default
            assertEquals(30, state1.pagedResources.size)
            assertEquals(1, state1.currentPage)
            assertTrue(state1.hasMorePages)

            // Load next page
            viewModel.onAction(ExploreUiAction.LoadNextPage)
            val state2 = viewModel.uiState.value
            assertEquals(60, state2.pagedResources.size)
            assertEquals(2, state2.currentPage)
            assertTrue(state2.hasMorePages)

            // Load third page
            viewModel.onAction(ExploreUiAction.LoadNextPage)
            val state3 = viewModel.uiState.value
            assertEquals(65, state3.pagedResources.size)
            assertEquals(3, state3.currentPage)
            assertFalse(state3.hasMorePages)

            // Filtering resets page back to 1
            viewModel.onAction(ExploreUiAction.UpdateSearchQuery("resource-1"))
            val state4 = viewModel.uiState.value
            assertEquals(1, state4.currentPage)
            assertEquals(11, state4.filteredResources.size) // resource-1, resource-10..resource-19
            assertEquals(11, state4.pagedResources.size)
            assertFalse(state4.hasMorePages)
        }

    @Test
    fun `a refresh still running when the cluster changes does not land on the new cluster`() =
        runTest(testDispatcher) {
            val first = Cluster(
                id = "c1",
                name = "prod-cluster",
                serverUrl = "https://127.0.0.1:6443",
                contextName = "prod",
                rawKubeconfig = "yaml",
                isActive = true,
            )
            val second = first.copy(id = "c2", name = "staging-cluster", contextName = "staging")
            fakeClusterRepository.setClusters(listOf(first, second.copy(isActive = false)))
            advanceUntilIdle()

            val gate = CompletableDeferred<Unit>()
            fakeExploreRepository.fetchGate = gate
            fakeExploreRepository.fetchResultOverride = listOf(
                APIResource(name = "prodonlies", singularName = "prodonly", namespaced = true, kind = "ProdOnly", groupVersion = "v1"),
            )
            viewModel.onAction(ExploreUiAction.Refresh)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.isRefreshing)

            fakeClusterRepository.setClusters(listOf(first.copy(isActive = false), second))
            advanceUntilIdle()
            gate.complete(Unit)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals("staging-cluster", state.activeCluster?.name)
            assertFalse(state.resources.any { it.kind == "ProdOnly" })
            assertFalse(state.isRefreshing)
            assertFalse(state.isLoading)
        }

    private class FakeClusterRepository : ClusterRepository {
        private val clustersFlow = MutableStateFlow<List<Cluster>>(emptyList())

        fun setClusters(list: List<Cluster>) {
            clustersFlow.value = list
        }

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
                id = "c1",
                name = customName ?: "test",
                serverUrl = "url",
                contextName = "ctx",
                namespace = "ns",
                rawKubeconfig = kubeconfigRaw,
                isActive = setAsActive,
                status = ClusterStatus.CONNECTED,
            )
            return Result.Success(cluster)
        }

        override suspend fun setActiveCluster(id: String): Result<Unit> = Result.Success(Unit)
        override suspend fun deleteCluster(id: String): Result<Unit> = Result.Success(Unit)

        override suspend fun clearCachedData(): Result<Unit> = Result.Success(Unit)
        override suspend fun updateClusterName(id: String, newName: String): Result<Unit> =
            Result.Success(Unit)

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
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun migratePlaintextClusters(): Result<Int> = Result.Success(0)
    }

    private class FakeExploreRepository : ExploreRepository {
        var fetchCount = 0
        var explainCount = 0
        var cachedExplain: ResourceExplain? = null

        private val defaultResources = listOf(
            APIResource(
                name = "pods",
                singularName = "pod",
                namespaced = true,
                kind = "Pod",
                groupVersion = "v1",
                shortNames = listOf("po")
            ),
            APIResource(
                name = "deployments",
                singularName = "deployment",
                namespaced = true,
                kind = "Deployment",
                group = "apps",
                groupVersion = "apps/v1",
                shortNames = listOf("deploy")
            ),
            APIResource(
                name = "nodes",
                singularName = "node",
                namespaced = false,
                kind = "Node",
                groupVersion = "v1",
                shortNames = listOf("no")
            ),
        )
        private var currentResources: List<APIResource> = defaultResources
        private val flow = MutableStateFlow(defaultResources)
        private val lastRefreshedFlow = MutableStateFlow<Long?>(1700000000000L)

        fun setResources(list: List<APIResource>) {
            currentResources = list
            flow.value = list
        }

        override fun getAPIResourcesStream(clusterId: String?): Flow<List<APIResource>> =
            flow.asStateFlow()

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> =
            lastRefreshedFlow.asStateFlow()

        /** When set, fetches wait for it, like a slow API server. */
        var fetchGate: CompletableDeferred<Unit>? = null

        /** When set, fetches return this list without touching the cache stream. */
        var fetchResultOverride: List<APIResource>? = null

        override suspend fun fetchAPIResources(clusterId: String?): Result<List<APIResource>> {
            fetchCount++
            fetchGate?.await()
            fetchResultOverride?.let { return Result.Success(it) }
            flow.value = currentResources
            lastRefreshedFlow.value = System.currentTimeMillis()
            return Result.Success(currentResources)
        }

        override fun getExplainedResourceStream(
            clusterId: String?,
            resourceOrKind: String,
            groupVersion: String,
        ): Flow<ResourceExplain?> = MutableStateFlow(null).asStateFlow()

        override suspend fun getCachedExplainedResource(
            clusterId: String?,
            resourceOrKind: String,
            groupVersion: String,
        ): ResourceExplain? = cachedExplain

        override suspend fun explainResource(
            clusterId: String?,
            resourceOrKind: String,
            groupVersion: String,
            forceRefresh: Boolean,
        ): Result<ResourceExplain> {
            explainCount++
            val matchedKind =
                defaultResources.find { it.name.equals(resourceOrKind, ignoreCase = true) }?.kind
                    ?: resourceOrKind.replaceFirstChar { it.uppercase() }
            return Result.Success(
                ResourceExplain(
                    kind = matchedKind,
                    groupVersion = groupVersion.ifEmpty { "v1" },
                    description = "Mock explanation for $resourceOrKind",
                    fields = listOf(
                        ResourceField(
                            name = "metadata",
                            type = "ObjectMeta",
                            description = "Standard metadata",
                            required = false
                        ),
                        ResourceField(
                            name = "spec",
                            type = "object",
                            description = "Specification of behavior",
                            required = true
                        ),
                    ),
                ),
            )
        }
    }

}
