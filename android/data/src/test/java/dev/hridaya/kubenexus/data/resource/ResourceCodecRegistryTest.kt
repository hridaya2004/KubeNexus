package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.data.resource.codec.DeploymentCodec
import dev.hridaya.kubenexus.data.resource.codec.PodCodec
import dev.hridaya.kubenexus.data.resource.codec.ServiceCodec
import dev.hridaya.kubenexus.domain.model.Pod
import dev.hridaya.kubenexus.domain.model.PodDetails
import dev.hridaya.kubenexus.domain.model.resource.ResourceKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ResourceCodecRegistryTest {

    private val registry = ResourceCodecRegistry(setOf(PodCodec, DeploymentCodec, ServiceCodec))

    @Test
    fun `finds the codec registered for a kind`() {
        assertSame(DeploymentCodec, registry.codecFor(ResourceKinds.Deployments))
    }

    @Test
    fun `resolves a manifest's apiVersion and kind to the resource type`() {
        assertEquals(ResourceKinds.Deployments.type, registry.typeOf("apps/v1", "Deployment"))
        assertEquals(ResourceKinds.Services.type, registry.typeOf("v1", "Service"))
    }

    @Test
    fun `does not resolve a kind under the wrong api group`() {
        assertNull(registry.typeOf("v1", "Deployment"))
    }

    @Test(expected = IllegalStateException::class)
    fun `asking for an unregistered kind fails loudly`() {
        ResourceCodecRegistry(setOf(PodCodec)).codecFor(ResourceKinds.Services)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `two codecs for the same type are rejected`() {
        val impostor = object : ResourceCodec<Pod, PodDetails> by PodCodec {}
        ResourceCodecRegistry(setOf(PodCodec, impostor))
    }
}
