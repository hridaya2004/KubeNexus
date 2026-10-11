package dev.hridaya.kubenexus.presentation.portforward

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.portforward.PortForwardListener
import dev.hridaya.kubenexus.data.portforward.PortForwardRepository
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionManager
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionRepositoryImpl
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.presentation.testing.FakeClusterRepository
import dev.hridaya.kubenexus.presentation.testing.TestDispatcherProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dispatchers = TestDispatcherProvider(testDispatcher)
    private val tunnels = FakePortForwardRepository()
    private lateinit var viewModel: PortForwardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val clusters = FakeClusterRepository()
        val manager = PortForwardSessionManager(
            repository = tunnels,
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = dispatchers,
        )
        viewModel = PortForwardViewModel(
            podName = "web-0",
            namespace = "default",
            sessionRepository = PortForwardSessionRepositoryImpl(manager) { "fake-kubeconfig" },
            getActiveClusterUseCase = GetActiveClusterUseCase(clusters, dispatchers),
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = dispatchers,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `a forward opens on the active cluster and shows for this pod`() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.onAction(PortForwardUiAction.StartForward(localPort = 8080, remotePort = 80))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isStarting)
        assertEquals(listOf("fake-kubeconfig"), tunnels.startedWith)
        assertEquals(8080, state.activeForwards.single().localPort)
    }

    @Test
    fun `stopping a forward closes its tunnel`() = runTest(testDispatcher) {
        advanceUntilIdle()
        viewModel.onAction(PortForwardUiAction.StartForward(localPort = 8080, remotePort = 80))
        advanceUntilIdle()

        viewModel.onAction(PortForwardUiAction.StopForward("pf-1"))
        advanceUntilIdle()

        assertEquals(listOf("pf-1"), tunnels.stopped)
        assertTrue(viewModel.uiState.value.activeForwards.isEmpty())
    }

    private class FakePortForwardRepository : PortForwardRepository {
        val startedWith = mutableListOf<String>()
        val stopped = mutableListOf<String>()

        override fun start(
            rawKubeconfig: String,
            namespace: String,
            podName: String,
            localPort: Int,
            remotePort: Int,
            listener: PortForwardListener,
        ): Result<String> {
            startedWith += rawKubeconfig
            return Result.Success("pf-${startedWith.size}")
        }

        override fun stop(handleId: String): Result<Unit> {
            stopped += handleId
            return Result.Success(Unit)
        }
    }
}
