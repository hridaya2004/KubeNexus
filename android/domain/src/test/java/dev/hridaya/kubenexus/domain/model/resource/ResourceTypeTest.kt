package dev.hridaya.kubenexus.domain.model.resource

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ResourceTypeTest {

    @Test
    fun `core types have a bare version as their apiVersion`() {
        assertEquals("v1", ResourceKinds.Pods.type.apiVersion)
        assertEquals("v1/pods", ResourceKinds.Pods.type.key)
    }

    @Test
    fun `grouped types prefix the group`() {
        assertEquals("apps/v1", ResourceKinds.Deployments.type.apiVersion)
        assertEquals("apps/v1/deployments", ResourceKinds.Deployments.type.key)
    }

    @Test
    fun `kinds are equal when their types are`() {
        val type = ResourceType(group = "", version = "v1", plural = "services", kind = "Service")

        assertEquals(ResourceKinds.Services, ResourceKind<Nothing, Any>(type))
        assertNotEquals(ResourceKinds.Services, ResourceKinds.Pods)
    }

    @Test
    fun `a blank namespace scopes to all namespaces`() {
        assertEquals(NamespaceScope.All, NamespaceScope.of(null))
        assertEquals(NamespaceScope.All, NamespaceScope.of("  "))
        assertEquals(NamespaceScope.Only("web"), NamespaceScope.of(" web "))
    }
}
