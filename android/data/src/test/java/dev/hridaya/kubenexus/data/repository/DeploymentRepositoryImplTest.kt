package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import dev.hridaya.kubenexus.data.testing.InMemoryClusterDao
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeploymentRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val encryptor = AesGcmKubeconfigEncryptor(AesGcmKubeconfigEncryptor.generateKey())
    private val clusterDao = InMemoryClusterDao().apply {
        seed(
            id = CLUSTER,
            rawKubeconfig = encryptor.encrypt(KUBECONFIG)
        )
    }
    private val bridge = RecordingRolloutBridge()

    private val repository = DeploymentRepositoryImpl(
        clusterCalls = ClusterCallRunner(
            clusterDao,
            encryptor,
            TestDispatcherProvider(testDispatcher)
        ),
        nativeBridge = bridge,
    )

    @Test
    fun `scale passes the decrypted kubeconfig and replica count`() = runTest(testDispatcher) {
        val result = repository.scaleDeployment(CLUSTER, "prod", "api", replicas = 4)

        assertEquals(Result.Success(Unit), result)
        assertEquals(listOf("scale", KUBECONFIG, "prod", "api", "4"), bridge.lastCall)
    }

    @Test
    fun `restart passes the decrypted kubeconfig`() = runTest(testDispatcher) {
        val result = repository.restartDeployment(CLUSTER, "prod", "api")

        assertEquals(Result.Success(Unit), result)
        assertEquals(listOf("restart", KUBECONFIG, "prod", "api"), bridge.lastCall)
    }

    @Test
    fun `rollout failures are reported as sanitized network errors`() = runTest(testDispatcher) {
        bridge.result = Result.Error(AppError.Unknown("scale denied: token: secret-token-12345"))

        val result = repository.scaleDeployment(CLUSTER, "prod", "api", replicas = 4)

        assertEquals(Result.Error(AppError.Network("scale denied: token: [REDACTED]")), result)
    }

    private class RecordingRolloutBridge : FakeKubeNexusNativeBridge() {
        var lastCall: List<String>? = null
        var result: Result<Unit> = Result.Success(Unit)

        override fun scaleDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String,
            replicas: Int
        ): Result<Unit> {
            lastCall = listOf("scale", rawKubeconfig, namespace, name, replicas.toString())
            return result
        }

        override fun restartDeployment(
            rawKubeconfig: String,
            namespace: String,
            name: String
        ): Result<Unit> {
            lastCall = listOf("restart", rawKubeconfig, namespace, name)
            return result
        }
    }

    private companion object {
        const val CLUSTER = "c-1"
        const val KUBECONFIG = "apiVersion: v1\nkind: Config"
    }
}
