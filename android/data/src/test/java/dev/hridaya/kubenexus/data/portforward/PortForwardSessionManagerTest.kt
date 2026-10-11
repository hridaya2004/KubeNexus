package dev.hridaya.kubenexus.data.portforward

import dev.hridaya.kubenexus.core.common.dispatcher.DispatcherProvider
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.PortForwardListener
import dev.hridaya.kubenexus.domain.model.PortForwardSessionStatus
import dev.hridaya.kubenexus.domain.model.PortForwardTargetKind
import dev.hridaya.kubenexus.domain.repository.PortForwardRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PortForwardSessionManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testDispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher = testDispatcher
        override val io: CoroutineDispatcher = testDispatcher
        override val default: CoroutineDispatcher = testDispatcher
        override val unconfined: CoroutineDispatcher = testDispatcher
    }

    private lateinit var fakeRepository: FakePortForwardRepository
    private lateinit var manager: PortForwardSessionManager

    @Before
    fun setUp() {
        fakeRepository = FakePortForwardRepository()
        manager = PortForwardSessionManager(
            repository = fakeRepository,
            externalScope = TestScope(testDispatcher),
            dispatcherProvider = testDispatcherProvider,
        )
    }

    @Test
    fun `startPodForward records a READY session, because the handle only arrives once listening`() =
        runTest(testDispatcher) {
            val result = manager.startPodForward(
                rawKubeconfig = "test-kubeconfig",
                namespace = "default",
                podName = "nginx",
                localPort = 8080,
                remotePort = 80,
                clusterId = "cluster-a",
            )

            assertTrue(result is Result.Success)
            val handleId = (result as Result.Success).data
            assertEquals("pf-1", handleId)

            val sessions = manager.sessions.value
            assertEquals(1, sessions.size)
            val session = sessions.first()
            assertEquals("pf-1", session.handleId)
            assertEquals("cluster-a", session.clusterId)
            assertEquals(PortForwardTargetKind.Pod, session.kind)
            assertEquals("default", session.namespace)
            assertEquals("nginx", session.targetName)
            assertEquals("nginx", session.podName)
            assertEquals(8080, session.localPort)
            assertEquals(80, session.remotePort)
            assertEquals(PortForwardSessionStatus.READY, session.status)
        }

    // Go reports readiness before StartPortForward returns the handle, and a tunnel that
    // dies at once can report its error before the session is recorded. Neither may be lost.
    @Test
    fun `events that arrive before the session is recorded are applied to it`() =
        runTest(testDispatcher) {
            fakeRepository.onStart = { listener ->
                listener.onPortForwardReady("pf-1", 8080)
                listener.onPortForwardError("pf-1", "pod is not running")
                listener.onPortForwardStopped("pf-1", "pod is not running")
            }

            manager.startPodForward("cfg", "default", "nginx", 8080, 80)

            val session = manager.sessions.value.single()
            assertEquals(PortForwardSessionStatus.STOPPED, session.status)
            assertEquals("pod is not running", session.message)
            advanceUntilIdle()
            assertFalse(manager.needsForegroundService.value)
        }

    @Test
    fun `events for a handle no start recorded are not kept`() = runTest(testDispatcher) {
        fakeRepository.onStart = { listener -> listener.onPortForwardStopped("pf-2", "gone") }
        manager.startPodForward("cfg", "default", "nginx", 8080, 80)

        fakeRepository.onStart = {}
        fakeRepository.nextHandleId = "pf-2"
        manager.startPodForward("cfg", "default", "nginx", 8081, 80)

        val second = manager.sessions.value.single { it.handleId == "pf-2" }
        assertEquals(PortForwardSessionStatus.READY, second.status)
    }

    @Test
    fun `the foreground service is needed while a forward is still dialing`() =
        runTest(testDispatcher) {
            var neededWhileDialing = false
            fakeRepository.onStart = {
                testDispatcher.scheduler.advanceUntilIdle()
                neededWhileDialing = manager.needsForegroundService.value
            }

            manager.startPodForward("cfg", "default", "nginx", 8080, 80)

            assertTrue(neededWhileDialing)
            advanceUntilIdle()
            assertTrue(manager.needsForegroundService.value)

            manager.stop("pf-1")
            advanceUntilIdle()
            assertFalse(manager.needsForegroundService.value)
        }

    @Test
    fun `a failed start leaves nothing active`() = runTest(testDispatcher) {
        fakeRepository.nextResult = Result.Error(AppError.Network("dial failed"))

        val result = manager.startPodForward("cfg", "default", "nginx", 8080, 80)

        assertTrue(result is Result.Error)
        assertTrue(manager.sessions.value.isEmpty())
        advanceUntilIdle()
        assertFalse(manager.needsForegroundService.value)
    }

    @Test
    fun `stopAll marks every active session stopped immediately`() = runTest(testDispatcher) {
        manager.startPodForward("cfg", "default", "nginx", 8080, 80)
        fakeRepository.nextHandleId = "pf-2"
        manager.startPodForward("cfg", "default", "redis", 6379, 6379)

        manager.stopAll()

        assertTrue(manager.sessions.value.all { it.status == PortForwardSessionStatus.STOPPED })
        advanceUntilIdle()
        assertEquals(listOf("pf-1", "pf-2"), fakeRepository.stopped.sorted())
    }

    @Test
    fun `startServiceForward registers active service session`() = runTest(testDispatcher) {
        val result = manager.startServiceForward(
            rawKubeconfig = "test-kubeconfig",
            namespace = "prod",
            serviceName = "web-svc",
            localPort = 3000,
            servicePort = 80,
            targetPodName = "web-pod-xyz",
            targetPodPort = 8080,
        )

        assertTrue(result is Result.Success)
        val session = manager.sessions.value.first()
        assertEquals(PortForwardTargetKind.Service, session.kind)
        assertEquals("prod", session.namespace)
        assertEquals("web-svc", session.targetName)
        assertEquals("web-pod-xyz", session.podName)
        assertEquals(3000, session.localPort)
        assertEquals(80, session.remotePort)
    }

    @Test
    fun `stop updates session status to STOPPED`() = runTest(testDispatcher) {
        manager.startPodForward(
            rawKubeconfig = "cfg",
            namespace = "default",
            podName = "nginx",
            localPort = 8080,
            remotePort = 80,
        )

        val stopResult = manager.stop("pf-1")
        assertTrue(stopResult is Result.Success)
        assertEquals(PortForwardSessionStatus.STOPPED, manager.sessions.value.first().status)
    }

    private class FakePortForwardRepository : PortForwardRepository {
        var nextHandleId = "pf-1"
        var nextResult: Result<String>? = null
        var onStart: (PortForwardListener) -> Unit = {}
        val stopped = mutableListOf<String>()

        override fun start(
            rawKubeconfig: String,
            namespace: String,
            podName: String,
            localPort: Int,
            remotePort: Int,
            listener: PortForwardListener,
        ): Result<String> {
            onStart(listener)
            return nextResult ?: Result.Success(nextHandleId)
        }

        override fun stop(handleId: String): Result<Unit> {
            stopped += handleId
            return Result.Success(Unit)
        }
    }
}
