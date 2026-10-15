package dev.hridaya.kubenexus.data.repository

import client.LogCallback
import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.nativebridge.LogStreamHandle
import dev.hridaya.kubenexus.data.testing.InMemoryClusterDao
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import dev.hridaya.kubenexus.domain.model.PodMetricSample
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PodRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val encryptor = AesGcmKubeconfigEncryptor(AesGcmKubeconfigEncryptor.generateKey())
    private val clusterDao = InMemoryClusterDao()
    private val bridge = RecordingPodBridge()

    private val repository = PodRepositoryImpl(
        clusterCalls = ClusterCallRunner(
            clusterDao,
            encryptor,
            TestDispatcherProvider(testDispatcher)
        ),
        nativeBridge = bridge,
        dispatcherProvider = TestDispatcherProvider(testDispatcher),
    )

    private fun givenCluster() =
        clusterDao.seed(id = CLUSTER, rawKubeconfig = encryptor.encrypt(KUBECONFIG))

    @Test
    fun `logs are fetched with the decrypted kubeconfig and the requested container and tail`() =
        runTest(testDispatcher) {
            givenCluster()

            val result = repository.getPodLogs(
                CLUSTER,
                "web",
                "web-1",
                containerName = "nginx",
                tailLines = 50
            )

            assertEquals(Result.Success("log line"), result)
            assertEquals(listOf(KUBECONFIG, "web", "web-1", "nginx", "50"), bridge.lastLogsRequest)
        }

    @Test
    fun `single pod metrics pass through a missing sample`() = runTest(testDispatcher) {
        givenCluster()

        val result = repository.getSinglePodMetrics(CLUSTER, "web", "web-1")

        assertEquals(Result.Success(null), result)
    }

    @Test
    fun `metrics failures are reported as sanitized network errors`() = runTest(testDispatcher) {
        givenCluster()
        bridge.metricsResult =
            Result.Error(AppError.Unknown("metrics denied: token: secret-token-12345"))

        val result = repository.getPodMetrics(CLUSTER, "web")

        assertEquals(Result.Error(AppError.Network("metrics denied: token: [REDACTED]")), result)
    }

    @Test
    fun `a log stream without a cluster reports why instead of hanging`() =
        runTest(testDispatcher) {
            val lines = repository.streamPodLogs(null, "web", "web-1").toList()

            assertEquals(listOf("Error: No cluster selected"), lines)
        }

    @Test
    fun `a log stream that fails to start reports a sanitized error`() = runTest(testDispatcher) {
        givenCluster()
        bridge.streamResult =
            Result.Error(AppError.Unknown("stream denied: token: secret-token-12345"))

        val lines = repository.streamPodLogs(CLUSTER, "web", "web-1").toList()

        assertEquals(1, lines.size)
        assertTrue(lines.single().startsWith("[Stream error]"))
        assertTrue("token" in lines.single() && "secret-token-12345" !in lines.single())
    }

    private class RecordingPodBridge : FakeKubeNexusNativeBridge() {
        var lastLogsRequest: List<String?>? = null
        var metricsResult: Result<List<PodMetricSample>> = Result.Success(emptyList())
        var streamResult: Result<LogStreamHandle> = Result.Success(LogStreamHandle {})

        override fun getPodLogs(
            rawKubeconfig: String,
            namespace: String,
            podName: String,
            container: String?,
            tailLines: Long?,
        ): Result<String> {
            lastLogsRequest =
                listOf(rawKubeconfig, namespace, podName, container, tailLines?.toString())
            return Result.Success("log line")
        }

        override fun topPods(
            rawKubeconfig: String,
            namespace: String?
        ): Result<List<PodMetricSample>> = metricsResult

        override fun streamPodLogs(
            rawKubeconfig: String,
            namespace: String,
            podName: String,
            container: String?,
            tailLines: Long?,
            callback: LogCallback,
        ): Result<LogStreamHandle> = streamResult
    }

    private companion object {
        const val CLUSTER = "c-1"
        const val KUBECONFIG =
            "apiVersion: v1\nkind: Config\nusers:\n- name: u\n  user:\n    token: secret-token-12345"
    }
}
