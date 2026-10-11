package dev.hridaya.kubenexus.presentation.services.detail

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.portforward.PortForwardListener
import dev.hridaya.kubenexus.data.portforward.PortForwardRepository
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionManager
import dev.hridaya.kubenexus.data.portforward.PortForwardSessionRepositoryImpl
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import dev.hridaya.kubenexus.domain.usecase.GetActiveClusterUseCase
import dev.hridaya.kubenexus.domain.usecase.ObservePortForwardSessionsUseCase
import dev.hridaya.kubenexus.domain.usecase.ResolveServiceForwardTargetUseCase
import dev.hridaya.kubenexus.domain.usecase.StartServicePortForwardUseCase
import dev.hridaya.kubenexus.domain.usecase.StopPortForwardUseCase
import dev.hridaya.kubenexus.presentation.testing.FakeClusterRepository
import dev.hridaya.kubenexus.presentation.testing.FakeResourceRepository
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
    private val resources = FakeResourceRepository()
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
        fakeClusterRepository = FakeClusterRepository()

        val sessions = PortForwardSessionRepositoryImpl(sessionManager) { "fake-kubeconfig" }
        viewModel = ServicePortForwardViewModel(
            serviceName = "web-svc",
            namespace = "default",
            observePortForwardSessions = ObservePortForwardSessionsUseCase(sessions),
            startServicePortForward = StartServicePortForwardUseCase(sessions),
            stopPortForward = StopPortForwardUseCase(sessions),
            resolveServiceForwardTargetUseCase = ResolveServiceForwardTargetUseCase(resources),
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
        resources.givenList(ResourceKinds.Pods, Result.Success(listOf(
            Pod(
                id = "1",
                name = "web-pod-1",
                namespace = "default",
                status = PodStatus.RUNNING,
                readyContainers = "1/1",
            ),
        )))

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
