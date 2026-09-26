package dev.hridaya.kubenexus.domain.usecase

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.CommandExecResult
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import dev.hridaya.kubenexus.domain.model.PodStatus
import dev.hridaya.kubenexus.domain.model.ServiceDetails
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.TerminalSession
import dev.hridaya.kubenexus.domain.repository.PodRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ResolveServiceForwardTargetUseCaseTest {

    private lateinit var fakePodRepository: FakePodRepository
    private lateinit var useCase: ResolveServiceForwardTargetUseCase

    @Before
    fun setUp() {
        fakePodRepository = FakePodRepository()
        useCase = ResolveServiceForwardTargetUseCase(fakePodRepository)
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

        fakePodRepository.podsToReturn = listOf(
            Pod(
                id = "1",
                name = "web-pod-abc",
                namespace = "default",
                status = PodStatus.RUNNING,
                readyContainers = "1/1",
            ),
        )

        val result = useCase("cfg", service, 80)
        assertTrue(result is Result.Success)
        val target = (result as Result.Success).data
        assertEquals("web-pod-abc", target.podName)
        assertEquals(8080, target.podPort)
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
        fakePodRepository.podsToReturn = listOf(
            Pod(id = "1", name = "web-1", namespace = "default", namedContainerPorts = mapOf("http" to 8080)),
        )

        val target = (useCase("cfg", service, 80) as Result.Success).data

        assertEquals("web-1", target.podName)
        assertEquals(8080, target.podPort)
    }

    @Test
    fun `a named targetPort no running pod defines is an error, not a guess`() = runTest {
        val service = serviceWithPort(
            ServicePortDetail(port = 80, targetPort = -1, nodePort = null, protocol = "TCP", name = "web", targetPortName = "http"),
        )
        fakePodRepository.podsToReturn = listOf(
            Pod(id = "1", name = "web-1", namespace = "default", namedContainerPorts = mapOf("metrics" to 9090)),
        )

        val result = useCase("cfg", service, 80)

        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error.message.contains("'http'"))
    }

    @Test
    fun `pods that are not running are never chosen`() = runTest {
        val service = serviceWithPort(
            ServicePortDetail(port = 80, targetPort = 8080, nodePort = null, protocol = "TCP", name = "web"),
        )
        fakePodRepository.podsToReturn = listOf(
            Pod(id = "1", name = "done", namespace = "default", status = PodStatus.COMPLETED, readyContainers = "0/1"),
            Pod(id = "2", name = "pending", namespace = "default", status = PodStatus.PENDING, readyContainers = "0/1"),
        )

        val result = useCase("cfg", service, 80)

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

        val result = useCase("cfg", service, 80)
        assertTrue(result is Result.Error)
        assertTrue((result as Result.Error).error is AppError.Validation)
    }

    private class FakePodRepository : PodRepository {
        var podsToReturn: List<Pod> = emptyList()

        override suspend fun listPodsBySelector(
            rawKubeconfig: String,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(podsToReturn)

        override suspend fun getPodsBySelector(
            clusterId: String?,
            namespace: String?,
            labelSelector: String,
        ): Result<List<Pod>> = Result.Success(podsToReturn)

        override fun getPodsStream(clusterId: String?, namespace: String?): Flow<List<Pod>> =
            flowOf(podsToReturn)

        override fun getNamespacesStream(clusterId: String?): Flow<List<String>> =
            flowOf(emptyList())

        override fun getLastRefreshedStream(clusterId: String?): Flow<Long?> = flowOf(null)
        override suspend fun refreshWorkloads(
            clusterId: String?,
            namespace: String?
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun describePod(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<PodDetails> = Result.Error(AppError.NotFound())

        override suspend fun getPodMetrics(
            clusterId: String?,
            namespace: String?
        ): Result<List<PodMetricSample>> = Result.Success(emptyList())

        override suspend fun getSinglePodMetrics(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<PodMetricSample?> = Result.Success(null)

        override suspend fun deletePod(
            clusterId: String?,
            namespace: String,
            podName: String
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun deleteNamespace(clusterId: String?, namespace: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun createNamespace(clusterId: String?, name: String): Result<Unit> =
            Result.Success(Unit)

        override suspend fun createPodFromManifest(
            clusterId: String?,
            manifestYaml: String
        ): Result<Unit> = Result.Success(Unit)

        override suspend fun getPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?
        ): Result<String> = Result.Success("")

        override fun streamPodLogs(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String?,
            tailLines: Long?
        ): Flow<String> = flowOf("")

        override suspend fun execCommand(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            stdin: String
        ): Result<CommandExecResult> = Result.Error(AppError.NotFound())

        override suspend fun startTerminalSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit
        ): Result<TerminalSession> = Result.Error(AppError.NotFound())

        override suspend fun startExecSession(
            clusterId: String?,
            namespace: String,
            podName: String,
            containerName: String,
            command: String,
            tty: Boolean,
            onStdout: (String) -> Unit,
            onStderr: (String) -> Unit,
            onError: (String) -> Unit,
            onDone: () -> Unit
        ): Result<TerminalSession> = Result.Error(AppError.NotFound())
    }
}
