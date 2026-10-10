package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.data.source.remote.dto.DeploymentDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventDto
import dev.hridaya.kubenexus.data.source.remote.dto.EventListDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * Exercises the Deployment mappers against real Kubernetes API payloads so a
 * DTO/mapper drift fails here rather than on a device.
 */
class DeploymentMapperTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "fixture $name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    private fun decodeDetails() =
        K8sJson.decodeFromString<DeploymentDto>(loadFixture("deployment-detail.json"))
            .toDeploymentDetails()

    @Test
    fun `maps identity and replica counters from a real payload`() {
        val details = decodeDetails()

        assertEquals("web-frontend", details.name)
        assertEquals("web", details.namespace)
        assertEquals(
            Instant.parse("2026-03-01T12:00:00Z").toEpochMilli(),
            details.creationTimestampMillis,
        )
        assertEquals(3, details.desiredReplicas)
        assertEquals(3, details.readyReplicas)
        assertEquals(3, details.availableReplicas)
        assertEquals(3, details.updatedReplicas)
    }

    @Test
    fun `maps strategy selector and images`() {
        val details = decodeDetails()

        assertEquals("RollingUpdate", details.strategyType)
        assertEquals(10, details.minReadySeconds)
        assertEquals(mapOf("app" to "web-frontend"), details.selectorMatchLabels)
        assertEquals(listOf("nginx:1.27.3", "busybox:1.36"), details.images)
    }

    @Test
    fun `maps metadata maps and condition timestamps to epoch millis`() {
        val details = decodeDetails()

        assertEquals(mapOf("app" to "web-frontend", "tier" to "frontend"), details.labels)
        assertEquals("4", details.annotations["deployment.kubernetes.io/revision"])

        assertEquals(2, details.conditions.size)
        val available = details.conditions.first { it.type == "Available" }
        assertEquals("True", available.status)
        assertEquals(
            Instant.parse("2026-03-01T12:05:30Z").toEpochMilli(),
            available.lastUpdateMillis,
        )
        assertEquals("MinimumReplicasAvailable", available.reason)
    }

    @Test
    fun `maps events with count and latest timestamp preferring lastTimestamp`() {
        val events = K8sJson.decodeFromString<EventListDto>(
            loadFixture("events-deployment.json"),
        ).items.map { it.toEventSummary() }

        assertEquals(2, events.size)

        val scaledUp = events[0]
        assertEquals("Normal", scaledUp.type)
        assertEquals("ScalingReplicaSet", scaledUp.reason)
        assertEquals(1, scaledUp.count)
        assertEquals(
            Instant.parse("2026-03-01T12:00:20Z").toEpochMilli(),
            scaledUp.lastTimestampMillis,
        )

        // This event carries eventTime plus a newer lastTimestamp; the mapper
        // must pick lastTimestamp when present.
        val scaledDown = events[1]
        assertEquals(3, scaledDown.count)
        assertEquals(
            Instant.parse("2026-03-02T08:13:59Z").toEpochMilli(),
            scaledDown.lastTimestampMillis,
        )
    }

    @Test
    fun `blank optional strings become null in events`() {
        val details = K8sJson.decodeFromString<DeploymentDto>(
            loadFixture("deployment-detail.json"),
        ).toDeploymentDetails(events = listOf(EventDto(type = "", reason = "", message = "")))

        assertNull(details.events.single().type)
        assertNull(details.events.single().reason)
        assertNull(details.events.single().message)
        assertNull(details.events.single().lastTimestampMillis)
    }
}
