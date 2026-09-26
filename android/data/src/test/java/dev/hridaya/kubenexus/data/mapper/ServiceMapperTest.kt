package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.NAMED_PORT_UNRESOLVED
import dev.hridaya.kubenexus.data.source.remote.dto.ServiceDto
import dev.hridaya.kubenexus.domain.model.ServicePortDetail
import dev.hridaya.kubenexus.domain.model.ServiceSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Exercises the Service mappers against real Kubernetes API payloads,
 * including the int-or-string targetPort shapes that motivated the custom
 * serializer.
 */
class ServiceMapperTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "fixture $name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    @Test
    fun `decodes ports where targetPort is numeric and named`() {
        val service = K8sJson.decodeFromString<ServiceDto>(loadFixture("service-detail.json"))

        assertEquals(2, service.spec.ports.size)

        val http = service.spec.ports[0].toDomain()
        assertEquals(
            ServicePortDetail(
                port = 80,
                targetPort = 8080,
                nodePort = 30080,
                protocol = "TCP",
                name = "http"
            ), http
        )

        // A named targetPort cannot be an Int; it must not fail the decode and
        // must surface as NAMED_PORT_UNRESOLVED so the row still renders.
        val https = service.spec.ports[1].toDomain()
        assertEquals(NAMED_PORT_UNRESOLVED, https.targetPort)
        assertEquals(443, https.port)
        assertEquals("https", https.name)
        // The name is kept so port-forwarding can resolve it against the pod's ports.
        assertEquals("https-port", https.targetPortName)
        assertEquals(null, http.targetPortName)
    }

    @Test
    fun `maps identity type clusterIPs and selector`() {
        val details = K8sJson.decodeFromString<ServiceDto>(loadFixture("service-detail.json"))
            .toServiceDetails()

        assertEquals("web-frontend", details.name)
        assertEquals("web", details.namespace)
        assertEquals(
            Instant.parse("2026-03-01T12:01:10Z").toEpochMilli(),
            details.creationTimestampMillis,
        )
        assertEquals("LoadBalancer", details.type)
        assertEquals("10.96.44.17", details.clusterIP)
        assertEquals(listOf("10.96.44.17"), details.clusterIPs)
        assertEquals(mapOf("app" to "web-frontend", "tier" to "frontend"), details.selector)
    }

    @Test
    fun `external IPs merge spec entries with load balancer ingress without duplicates`() {
        val service = K8sJson.decodeFromString<ServiceDto>(loadFixture("service-detail.json"))

        // spec.externalIPs first, then LB ingress addresses.
        assertEquals(
            listOf("203.0.113.7", "198.51.100.4"),
            service.toServiceDetails().externalIPs,
        )

        val overlapping = service.copy(
            spec = service.spec.copy(externalIPs = listOf("198.51.100.4")),
        )
        assertEquals(listOf("198.51.100.4"), overlapping.toServiceDetails().externalIPs)
    }

    @Test
    fun `summary survives the entity round trip including port rows`() {
        val original = ServiceSummary(
            id = "ignored-here",
            name = "web-frontend",
            namespace = "web",
            type = "LoadBalancer",
            clusterIP = "10.96.44.17",
            ports = listOf(
                ServicePortDetail(
                    port = 80,
                    targetPort = 8080,
                    nodePort = 30080,
                    protocol = "TCP",
                    name = "http"
                ),
                ServicePortDetail(
                    port = 443,
                    targetPort = NAMED_PORT_UNRESOLVED,
                    nodePort = null,
                    protocol = "TCP",
                    name = null
                ),
            ),
            creationTimestampMillis = 1_772_000_000_000L,
        )

        val restored = original.toEntity(clusterId = "c-1").toDomain()

        assertEquals("c-1_web_web-frontend", restored.id)
        assertEquals(original.name, restored.name)
        assertEquals(original.namespace, restored.namespace)
        assertEquals(original.type, restored.type)
        assertEquals(original.clusterIP, restored.clusterIP)
        assertEquals(original.ports, restored.ports)
        assertEquals(original.creationTimestampMillis, restored.creationTimestampMillis)
    }

    @Test
    fun `blank event fields become null in detail events`() {
        val details = K8sJson.decodeFromString<ServiceDto>(loadFixture("service-detail.json"))
            .toServiceDetails(
                events = listOf(
                    EventDto(type = "", reason = "", message = "", count = 2),
                ),
            )

        val single = details.events.single()
        assertNull(single.type)
        assertNull(single.reason)
        assertNull(single.message)
        assertEquals(2, single.count)
    }
}
