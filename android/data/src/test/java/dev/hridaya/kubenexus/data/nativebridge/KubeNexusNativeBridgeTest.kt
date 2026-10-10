package dev.hridaya.kubenexus.data.nativebridge

import dev.hridaya.kubenexus.core.common.result.Result
import dev.hridaya.kubenexus.domain.model.APIResource
import dev.hridaya.kubenexus.domain.model.Namespace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KubeNexusNativeBridgeTest {

    @Test
    fun `fake bridge reports availability only after initialization`() {
        val fakeBridge = FakeKubeNexusNativeBridge()

        assertFalse(fakeBridge.isAvailable())
        fakeBridge.initialize()
        assertTrue(fakeBridge.isAvailable())
        assertTrue(fakeBridge.touch())
    }

    @Test
    fun `bridge carries namespace phase through instead of assuming Active`() {
        val fakeBridge = object : FakeKubeNexusNativeBridge() {
            override fun listNamespaces(rawKubeconfig: String): Result<List<Namespace>> =
                Result.Success(
                    listOf(
                        Namespace(name = "default", status = "Active"),
                        Namespace(name = "doomed", status = "Terminating"),
                    ),
                )
        }

        val namespaces = fakeBridge.listNamespaces("mock-kubeconfig").getOrThrow()

        assertEquals(2, namespaces.size)
        assertEquals("Terminating", namespaces[1].status)
    }

    @Test
    fun `health and version probes report success`() {
        val fakeBridge = FakeKubeNexusNativeBridge()

        assertTrue(fakeBridge.checkLivez("mock-kubeconfig").getOrThrow())
        assertTrue(fakeBridge.checkReadyz("mock-kubeconfig").getOrThrow())
        assertTrue(fakeBridge.checkHealthz("mock-kubeconfig").getOrThrow())
        assertEquals("v1.30.0", fakeBridge.serverVersion("mock-kubeconfig").getOrThrow())
        assertEquals(
            "Ready",
            fakeBridge.checkHealth("mock-kubeconfig").getOrThrow().statusMessage,
        )
    }

    @Test
    fun `api resources are returned as domain models`() {
        val fakeBridge = object : FakeKubeNexusNativeBridge() {
            override fun listAPIResources(rawKubeconfig: String): Result<List<APIResource>> =
                Result.Success(
                    listOf(APIResource(name = "pods", kind = "Pod", groupVersion = "v1")),
                )
        }

        val resources = fakeBridge.listAPIResources("mock-kubeconfig").getOrThrow()

        assertEquals(1, resources.size)
        assertEquals("Pod", resources[0].kind)
    }
}
