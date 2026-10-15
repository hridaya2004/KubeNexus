package dev.hridaya.kubenexus.presentation.resources.list

import androidx.lifecycle.viewModelScope
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.usecase.GetNamespacesUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveLastSyncedUseCase
import dev.hridaya.kubenexus.domain.usecase.ObserveResourcesUseCase
import dev.hridaya.kubenexus.domain.usecase.SyncResourcesUseCase
import dev.hridaya.kubenexus.presentation.common.ALL_NAMESPACES_FILTER
import dev.hridaya.kubenexus.presentation.resources.kinds.ServicesPresentation
import dev.hridaya.kubenexus.presentation.testing.FakeNamespaceRepository
import dev.hridaya.kubenexus.presentation.testing.FakeNetworkMonitor
import dev.hridaya.kubenexus.presentation.testing.FakeResourceRepository
import dev.hridaya.kubenexus.presentation.testing.TestDispatcherProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ResourceListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val resources = FakeResourceRepository()
    private val namespaces = FakeNamespaceRepository(names = listOf("team-a", "team-b"))
    private val network = FakeNetworkMonitor()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vmTest(
        initialNamespace: String? = "team-a",
        block: suspend TestScope.(ResourceListViewModel) -> Unit,
    ) = runTest(testDispatcher) {
        val viewModel = ResourceListViewModel(
            presentation = ServicesPresentation,
            clusterId = CLUSTER,
            initialNamespace = initialNamespace,
            observeResources = ObserveResourcesUseCase(resources),
            syncResources = SyncResourcesUseCase(resources),
            observeLastSynced = ObserveLastSyncedUseCase(resources),
            getNamespaces = GetNamespacesUseCase(namespaces),
            networkMonitor = network,
            dispatcherProvider = TestDispatcherProvider(testDispatcher),
        )
        try {
            advanceUntilIdle()
            block(viewModel)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun service(name: String, namespace: String = "team-a") = ServiceSummary(
        id = "${namespace}_$name",
        name = name,
        namespace = namespace,
        type = "ClusterIP",
        clusterIP = "10.96.0.1",
        ports = listOf(
            ServicePortDetail(
                port = 80,
                targetPort = 8080,
                nodePort = null,
                protocol = "TCP",
                name = "http"
            )
        ),
        creationTimestampMillis = 0L,
    )

    @Test
    fun `cached rows render immediately, mapped by the kind's presentation`() =
        vmTest { viewModel ->
            resources.givenCached(ResourceKinds.Services, listOf(service("web"), service("api")))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertEquals(listOf("web", "api"), state.rows.map { it.name })
            assertEquals("ClusterIP", state.rows.first().badge)
            assertFalse(state.isLoading)
        }

    @Test
    fun `nothing syncs until the screen asks`() = vmTest {
        assertTrue(resources.syncCalls.isEmpty())
    }

    @Test
    fun `refresh syncs the selected namespace of the cluster`() = vmTest { viewModel ->
        viewModel.onAction(ResourceListUiAction.Refresh)
        advanceUntilIdle()

        assertEquals(
            FakeResourceRepository.SyncCall(
                CLUSTER,
                ResourceKinds.Services,
                NamespaceScope.Only("team-a")
            ),
            resources.syncCalls.single(),
        )
    }

    @Test
    fun `the all namespaces filter syncs the whole cluster`() =
        vmTest(initialNamespace = null) { viewModel ->
            viewModel.onAction(ResourceListUiAction.Refresh)
            advanceUntilIdle()

            assertEquals(ALL_NAMESPACES_FILTER, viewModel.uiState.value.selectedNamespace)
            assertEquals(NamespaceScope.All, resources.syncCalls.single().scope)
        }

    @Test
    fun `selecting a namespace re-scopes the rows and syncs that namespace`() =
        vmTest { viewModel ->
            resources.givenCached(
                ResourceKinds.Services,
                listOf(service("web", "team-a"), service("api", "team-b"))
            )
            advanceUntilIdle()
            assertEquals(listOf("web"), viewModel.uiState.value.rows.map { it.name })

            viewModel.onAction(ResourceListUiAction.SelectNamespace("team-b"))
            advanceUntilIdle()

            assertEquals(listOf("api"), viewModel.uiState.value.rows.map { it.name })
            assertEquals(NamespaceScope.Only("team-b"), resources.syncCalls.single().scope)
        }

    @Test
    fun `re-selecting the visible namespace does not sync again`() = vmTest { viewModel ->
        viewModel.onAction(ResourceListUiAction.SelectNamespace("team-a"))
        advanceUntilIdle()

        assertTrue(resources.syncCalls.isEmpty())
    }

    @Test
    fun `namespace chips lead with all namespaces`() = vmTest { viewModel ->
        assertEquals(
            listOf(ALL_NAMESPACES_FILTER, "team-a", "team-b"),
            viewModel.uiState.value.namespaces
        )
    }

    @Test
    fun `a failed sync keeps cached rows without a blocking error`() = vmTest { viewModel ->
        resources.givenCached(ResourceKinds.Services, listOf(service("web")))
        resources.syncResult = Result.Error(AppError.Network("offline"))

        viewModel.onAction(ResourceListUiAction.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.rows.size)
        assertFalse(state.showsSyncError)
        assertFalse(state.isRefreshing)
    }

    @Test
    fun `a failed sync with nothing cached shows the error until a retry succeeds`() =
        vmTest { viewModel ->
            resources.syncResult = Result.Error(AppError.Network("offline"))
            viewModel.onAction(ResourceListUiAction.Refresh)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.showsSyncError)

            resources.syncResult = Result.Success(Unit)
            resources.givenCached(ResourceKinds.Services, listOf(service("web")))
            viewModel.onAction(ResourceListUiAction.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertFalse(state.showsSyncError)
            assertEquals(listOf("web"), state.rows.map { it.name })
        }

    @Test
    fun `an empty cache shows loading while the first sync runs`() = vmTest { viewModel ->
        val syncAnswer = CompletableDeferred<Result<Unit>>()
        resources.onSync = { syncAnswer.await() }

        viewModel.onAction(ResourceListUiAction.Refresh)
        runCurrent()
        assertTrue(viewModel.uiState.value.isLoading)

        syncAnswer.complete(Result.Success(Unit))
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `coming back online syncs again`() = vmTest {
        network.isOnline.value = false
        advanceUntilIdle()
        network.isOnline.value = true
        advanceUntilIdle()

        assertEquals(1, resources.syncCalls.size)
    }

    private companion object {
        const val CLUSTER = "c-1"
    }
}
