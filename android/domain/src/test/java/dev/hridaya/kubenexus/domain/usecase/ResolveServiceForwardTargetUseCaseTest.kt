package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.fake.FakeResourceRepository
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.resource.NamespaceScope
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveServiceForwardTargetUseCaseTest {

    private lateinit var fakeResourceRepository: FakeResourceRepository
    private lateinit var useCase: ResolveServiceForwardTargetUseCase

    @Before
    fun setUp() {
        fakeResourceRepository = FakeResourceRepository()
        useCase = ResolveServiceForwardTargetUseCase(fakeResourceRepository)
    }

    private fun givenPods(vararg pods: Pod) {
        fakeResourceRepository.givenList(ResourceKinds.Pods, Result.Success(pods.toList()))
    }

    @Test
    fun `resolves target pod and port matching service selector and port definition`() = runTest {
        val service = ServiceDetails(
            name = "web-service",
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

        givenPods(
            Pod(
                id = "1",
                name = "web-pod-abc",
                namespace = "default",
                status = PodStatus.RUNNING,
                readyContainers = "1/1",
            ),
        )

        val result = useCase("c1", service, 80)
        assertTrue(result is Result.Success)
        val target = (result as Result.Success).data
        assertEquals("web-pod-abc", target.podName)
        assertEquals(8080, target.podPort)
        assertEquals(
            FakeResourceRepository.ListCall("c1", ResourceKinds.Pods, NamespaceScope.Only("default"), "app=web"),
            fakeResourceRepository.listCalls.single(),
        )
    }

    private fun serviceWithPort(port: ServicePortDetail) = ServiceDetails(
        name = "web-service",
        namespace = "default",
        creationTimestampMillis = 1000L,
        type = "ClusterIP",
        clusterIP = "10.96.0.1",
        clusterIPs = listOf("10.96.0.1"),
        externalIPs = emptyList(),
        selector = mapOf("app" to "web"),
        ports = listOf(port),
        labels = emptyMap(),
        annotations = emptyMap(),
        events = emptyList(),
    )

    // targetPort: http with the pod exposing http on 8080 must forward to 8080, not to the
    // Service's own port 80.
    @Test
    fun `resolves a named targetPort to the pod's container port`() = runTest {
        val service = serviceWithPort(
            ServicePortDetail(port = 80, targetPort = -1, nodePort = null, protocol = "TCP", name = "web", targetPortName = "http"),
        )
        givenPods(
            Pod(id = "1", name = "web-1", namespace = "default", namedContainerPorts = mapOf("http" to 8080)),
        )

        val target = (useCase("c1", service, 80) as Result.Success).data

        assertEquals("web-1", target.podName)
        assertEquals(8080, target.podPort)
    }

    @Test
    fun `a named targetPort no running pod defines is an error, not a guess`() = runTest {
        val service = serviceWithPort(
            ServicePortDetail(port = 80, targetPort = -1, nodePort = null, protocol = "TCP", name = "web", targetPortName = "http"),
        )
        givenPods(
            Pod(id = "1", name = "web-1", namespace = "default", namedContainerPorts = mapOf("metrics" to 9090)),
        )

        val result = useCase("c1", service, 80)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error.message.contains("'http'"))
    }

    @Test
    fun `pods that are not running are never chosen`() = runTest {
        val service = serviceWithPort(
            ServicePortDetail(port = 80, targetPort = 8080, nodePort = null, protocol = "TCP", name = "web"),
        )
        givenPods(
            Pod(id = "1", name = "done", namespace = "default", status = PodStatus.COMPLETED, readyContainers = "0/1"),
            Pod(id = "2", name = "pending", namespace = "default", status = PodStatus.PENDING, readyContainers = "0/1"),
        )

        val result = useCase("c1", service, 80)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error.message.startsWith("No running pods"))
    }

    @Test
    fun `returns validation error when service has empty selector`() = runTest {
        val service = ServiceDetails(
            name = "external-service",
            namespace = "default",
            creationTimestampMillis = 1000L,
            type = "ClusterIP",
            clusterIP = "None",
            clusterIPs = emptyList(),
            externalIPs = emptyList(),
            selector = emptyMap(),
            ports = emptyList(),
            labels = emptyMap(),
            annotations = emptyMap(),
            events = emptyList(),
        )

        val result = useCase("c1", service, 80)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Validation)
    }
}
