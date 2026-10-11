package dev.hridaya.kubenexus.data.kubeconfig

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.data.nativebridge.FakeKubeNexusNativeBridge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ClusterConnectionTesterTest {

    private lateinit var fakeNativeBridge: FakeNativeBridge
    private lateinit var tester: ClusterConnectionTester

    private class FakeNativeBridge : FakeKubeNexusNativeBridge() {
        var pingResult: Result<String> =
            Result.Success("Cluster ready & healthy (Kubernetes v1.30.0)")

        override fun ping(rawKubeconfig: String): Result<String> = pingResult
    }

    @Before
    fun setUp() {
        fakeNativeBridge = FakeNativeBridge()
        tester = ClusterConnectionTester(fakeNativeBridge)
    }

    @Test
    fun `testConnection returns success message when native ping succeeds`() {
        val parsed = ParsedKubeconfig(
            clusterName = "test-cluster",
            serverUrl = "https://127.0.0.1:6443",
            contextName = "test-ctx",
            userName = "test-user",
            namespace = "default",
            rawKubeconfig = "clusters: []",
        )

        val result = tester.testConnection(parsed)
        assertEquals("Cluster ready & healthy (Kubernetes v1.30.0)", result)
    }

    @Test
    fun `testConnection throws exception with diagnostic information when native ping fails`() {
        fakeNativeBridge.pingResult =
            Result.Error(AppError.Network("TLS handshake failed: certificate untrusted"))

        val parsed = ParsedKubeconfig(
            clusterName = "test-cluster",
            serverUrl = "https://127.0.0.1:6443",
            contextName = "test-ctx",
            userName = "test-user",
            namespace = "default",
            rawKubeconfig = "clusters: []",
        )

        val exception = assertThrows(Exception::class.java) {
            tester.testConnection(parsed)
        }

        assertTrue(exception.message!!.contains("Failed to connect to Kubernetes Cluster: 'test-cluster'"))
        assertTrue(exception.message!!.contains("TLS handshake failed: certificate untrusted"))
    }
}
