package dev.hridaya.kubenexus.presentation.portforward.sessions

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.portforward.PortForwardListener
import dev.hridaya.kubenexus.data.portforward.PortForwardRepository
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionManager
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionRepositoryImpl
import dev.hridaya.kubenexus.domain.usecase.ObservePortForwardSessionsUseCase
import dev.hridaya.kubenexus.domain.usecase.StopPortForwardUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardSessionsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepository: FakePortForwardRepository
    private lateinit var sessionManager: PortForwardSessionManager
    private lateinit var viewModel: PortForwardSessionsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeRepository = FakePortForwardRepository()
        sessionManager = PortForwardSessionManager(
            repository = fakeRepository,
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = testDispatcherProvider,
        )
        val sessions = PortForwardSessionRepositoryImpl(sessionManager) { "fake-kubeconfig" }
        viewModel = PortForwardSessionsViewModel(ObservePortForwardSessionsUseCase(sessions), StopPortForwardUseCase(sessions))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observes sessions and updates visible sessions and active count`() =
        runTest(testDispatcher) {
            sessionManager.startPodForward("cfg", "default", "pod-1", 8080, 80)
            sessionManager.startServiceForward("cfg", "default", "svc-1", 9090, 80, "pod-1", 8080)
            runCurrent()

            val state = viewModel.uiState.value
            assertEquals(2, state.activeCount)
            assertEquals(2, state.visibleSessions.size)
            assertTrue(state.canStopAll)
        }

    @Test
    fun `dismissStopped removes stopped row from visible state`() = runTest(testDispatcher) {
        sessionManager.startPodForward("cfg", "default", "pod-1", 8080, 80)
        runCurrent()
        sessionManager.stop("pf-1")
        runCurrent()

        viewModel.onAction(PortForwardSessionsUiAction.ToggleIncludeStopped)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.visibleSessions.size)

        viewModel.onAction(PortForwardSessionsUiAction.DismissStopped("pf-1"))
        runCurrent()
        assertEquals(0, viewModel.uiState.value.visibleSessions.size)
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
}
