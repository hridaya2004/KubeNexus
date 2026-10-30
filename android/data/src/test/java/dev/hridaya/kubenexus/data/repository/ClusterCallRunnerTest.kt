package dev.hridaya.kubenexus.data.repository

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.testing.InMemoryClusterDao
import dev.hridaya.kubenexus.data.testing.TestDispatcherProvider
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterCallRunnerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val encryptor = testKubeconfigEncryptor()
    private val clusterDao = InMemoryClusterDao()
    private val runner =
        ClusterCallRunner(clusterDao, encryptor, TestDispatcherProvider(testDispatcher))

    @Test
    fun `no cluster selected fails without running the call`() = runTest(testDispatcher) {
        var ran = false

        val result = runner.run(null, "Failed") { ran = true; Result.Success(Unit) }

        assertEquals(Result.Error(AppError.NotFound("No cluster selected")), result)
        assertFalse(ran)
    }

    @Test
    fun `an unknown cluster fails without running the call`() = runTest(testDispatcher) {
        val result = runner.run("missing", "Failed") { Result.Success(Unit) }

        assertTrue((result as Result.Error).error is AppError.NotFound)
    }

    @Test
    fun `the call receives the cluster id and its decrypted kubeconfig`() =
        runTest(testDispatcher) {
            clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

            val result = runner.run(
                "c-1",
                "Failed"
            ) { cluster -> Result.Success(cluster.id to cluster.kubeconfig) }

            assertEquals(Result.Success("c-1" to "kubeconfig-text"), result)
        }

    @Test
    fun `native failures become sanitized network errors`() = runTest(testDispatcher) {
        clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

        val result = runner.run("c-1", "Failed") {
            Result.Error(AppError.Unknown("denied: token: secret-token-12345"))
        }

        assertEquals(Result.Error(AppError.Network("denied: token: [REDACTED]")), result)
    }

    @Test
    fun `a native failure without a message falls back to the failure message`() =
        runTest(testDispatcher) {
            clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

            val result =
                runner.run("c-1", "Failed to list pods") { Result.Error(AppError.Unknown("")) }

            assertEquals(Result.Error(AppError.Network("Failed to list pods")), result)
        }

    @Test
    fun `deliberate errors pass through unchanged`() = runTest(testDispatcher) {
        clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

        val result = runner.run("c-1", "Failed") { Result.Error(AppError.Validation("bad name")) }

        assertEquals(Result.Error(AppError.Validation("bad name")), result)
    }

    @Test
    fun `thrown exceptions become network errors`() = runTest(testDispatcher) {
        clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

        val result =
            runner.run<Unit>("c-1", "Failed") { throw IllegalStateException("connection reset") }

        assertEquals(Result.Error(AppError.Network("connection reset")), result)
    }

    @Test
    fun `cancellation is rethrown, not reported as a failure`() = runTest(testDispatcher) {
        clusterDao.seed(id = "c-1", rawKubeconfig = encryptor.encrypt("kubeconfig-text", "c-1"))

        val thrown = runCatching {
            runner.run<Unit>("c-1", "Failed") { throw CancellationException("screen closed") }
        }.exceptionOrNull()

        assertTrue(thrown is CancellationException)
    }
}
