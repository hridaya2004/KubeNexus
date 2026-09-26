package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.data.source.remote.dto.EventListDto
import dev.hridaya.kubenexus.data.source.remote.dto.K8sJson
import dev.hridaya.kubenexus.data.source.remote.dto.PodDto
import dev.hridaya.kubenexus.data.source.remote.dto.NamespaceListDto
import dev.hridaya.kubenexus.data.source.remote.dto.PodListDto
import dev.hridaya.kubenexus.domain.model.PodStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the mapper against real Kubernetes API payloads.
 *
 * The previous version of this file re-implemented the status mapping inside the
 * test and asserted against its own copy, so it could not have caught a mapper
 * bug. These tests decode the fixture the API server would actually return.
 */
class PodMapperTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "fixture $name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    private fun podList(): PodListDto =
        K8sJson.decodeFromString<PodListDto>(loadFixture("pod-list.json"))

    @Test
    fun `decodes a pod list containing fields the DTOs do not model`() {
        val list = podList()

        assertEquals(3, list.items.size)
        assertEquals("104729", list.metadata.resourceVersion)
        assertEquals("eyJ2IjoibWV0YS5rOHMuaW8vdjEifQ", list.metadata.continueToken)
    }

    @Test
    fun `maps identity and placement fields`() {
        val pod = podList().items[0].toDomain()

        assertEquals("nginx-deployment-78f56c879d-gqw87", pod.name)
        assertEquals("default", pod.namespace)
        assertEquals("default_nginx-deployment-78f56c879d-gqw87", pod.id)
        assertEquals("node-1", pod.node)
        assertEquals("10.244.0.15", pod.ip)
    }

    // The flattened Gomobile struct had no image field, so the list could never
    // display one.
    @Test
    fun `maps the first container image`() {
        assertEquals("nginx:1.27.1", podList().items[0].toDomain().image)
    }

    @Test
    fun `counts ready containers against the spec rather than the status list`() {
        // Two containers declared, one of them ready.
        assertEquals("1/2", podList().items[0].toDomain().readyContainers)
    }

    @Test
    fun `sums restarts across all containers`() {
        // nginx restarted twice, the sidecar once.
        assertEquals(3, podList().items[0].toDomain().restarts)
    }

    // A Running phase with a CrashLoopBackOff container must not be reported as
    // healthy. The old mapper keyed off the phase alone, which made
    // PodStatus.CRASH_LOOP unreachable.
    @Test
    fun `detects crash looping from container state despite a Running phase`() {
        val pod = podList().items[1].toDomain()

        assertEquals("Running", podList().items[1].status.phase)
        assertEquals(PodStatus.CRASH_LOOP, pod.status)
        assertEquals(17, pod.restarts)
    }

    @Test
    fun `maps pending pods and defaults a blank namespace`() {
        val pod = podList().items[2].toDomain()

        assertEquals(PodStatus.PENDING, pod.status)
        assertEquals("default", pod.namespace)
        assertNull(pod.node)
        assertNull(pod.ip)
        // A blank image string must become null rather than an empty string.
        assertNull(pod.image)
    }

    @Test
    fun `a non crash looping waiting reason does not become CRASH_LOOP`() {
        // The first pod has a sidecar in ImagePullBackOff, not CrashLoopBackOff.
        assertEquals(PodStatus.RUNNING, podList().items[0].toDomain().status)
    }

    @Test
    fun `details expose labels and annotations as maps`() {
        val details = podList().items[0].toDetails()

        assertEquals(3, details.labels.size)
        assertEquals("nginx", details.labels["app"])
        assertEquals("frontend", details.labels["tier"])
        // Annotations never crossed the old bridge at all and were always empty.
        assertEquals(2, details.annotations.size)
        assertEquals("true", details.annotations["prometheus.io/scrape"])
    }

    @Test
    fun `details expose volumes as a list rather than a csv string`() {
        val volumes = podList().items[0].toDetails().volumes

        assertEquals(listOf("config", "kube-api-access-2x9lk"), volumes)
    }

    @Test
    fun `details map containers and init containers with their state`() {
        val details = podList().items[0].toDetails()

        assertEquals(2, details.containers.size)
        val nginx = details.containers.first { it.name == "nginx" }
        assertEquals("nginx:1.27.1", nginx.image)
        assertTrue(nginx.ready)
        assertEquals(2, nginx.restartCount)
        assertEquals("Running", nginx.state)

        val sidecar = details.containers.first { it.name == "sidecar-logger" }
        assertFalse(sidecar.ready)
        assertEquals("Waiting (ImagePullBackOff)", sidecar.state)

        assertEquals(1, details.initContainers.size)
        assertEquals("Terminated (exit 0)", details.initContainers[0].state)
    }

    @Test
    fun `details map conditions`() {
        val conditions = podList().items[0].toDetails().conditions

        assertEquals(3, conditions.size)
        val ready = conditions.first { it.type == "Ready" }
        assertEquals("True", ready.status)
        assertEquals("2026-08-21T09:15:20Z", ready.lastTransitionTime)
        // Blank reason and message collapse to null.
        assertNull(ready.reason)
    }

    @Test
    fun `details carry the raw json so the ui can offer a raw view`() {
        val raw = """{"kind":"Pod"}"""
        val details = podList().items[0].toDetails(rawJson = raw)

        assertEquals(raw, details.rawDescribeText)
    }

    @Test
    fun `entity round trip preserves the creation timestamp instead of a rendered age`() {
        val pod = podList().items[0].toDomain()
        val restored = pod.toEntity(clusterId = "cluster-a").toDomain()

        assertNotNull(pod.creationTimestampMillis)
        assertEquals(pod.creationTimestampMillis, restored.creationTimestampMillis)
        assertEquals(pod.status, restored.status)
        assertEquals(pod.readyContainers, restored.readyContainers)
        assertEquals(pod.restarts, restored.restarts)
        assertEquals(pod.image, restored.image)
        assertEquals("cluster-a_default_${pod.name}", pod.toEntity("cluster-a").id)
    }

    @Test
    fun `crash loop status survives the entity round trip`() {
        val pod = podList().items[1].toDomain()
        val restored = pod.toEntity(clusterId = "cluster-a").toDomain()

        assertEquals(PodStatus.CRASH_LOOP, restored.status)
    }

    @Test
    fun `namespace phase is carried through rather than assumed Active`() {
        val json = """
            {
              "apiVersion": "v1",
              "kind": "NamespaceList",
              "items": [
                {
                  "metadata": { "name": "default", "creationTimestamp": "2026-08-01T00:00:00Z" },
                  "status": { "phase": "Active" }
                },
                {
                  "metadata": { "name": "doomed", "creationTimestamp": "2026-08-20T00:00:00Z" },
                  "status": { "phase": "Terminating" }
                }
              ]
            }
        """.trimIndent()

        val namespaces =
            K8sJson.decodeFromString<NamespaceListDto>(json).items.map { it.toDomain() }

        assertEquals(2, namespaces.size)
        assertEquals("Active", namespaces[0].status)
        assertEquals("Terminating", namespaces[1].status)
        assertEquals("doomed", namespaces[1].name)
    }

    @Test
    fun `namespace without a phase falls back to Active`() {
        val json = """{"items":[{"metadata":{"name":"legacy"}}]}"""

        val namespace = K8sJson.decodeFromString<NamespaceListDto>(json).items.single().toDomain()

        assertEquals("Active", namespace.status)
    }

    @Test
    fun `events prefer lastTimestamp then eventTime then firstTimestamp`() {
        val json = """
            {
              "items": [
                {
                  "type": "Warning",
                  "reason": "BackOff",
                  "message": "Back-off restarting failed container",
                  "count": 12,
                  "firstTimestamp": "2026-08-23T10:00:00Z",
                  "lastTimestamp": "2026-08-23T11:00:00Z"
                },
                {
                  "reason": "Scheduled",
                  "message": "Successfully assigned default/x to node-1",
                  "eventTime": "2026-08-23T09:00:00Z"
                }
              ]
            }
        """.trimIndent()

        val events = K8sJson.decodeFromString<EventListDto>(json).items.map { it.toDomain() }

        assertEquals(2, events.size)
        assertEquals("Warning", events[0].type)
        assertEquals("BackOff", events[0].reason)
        // Type defaults to Normal when the server omits it.
        assertEquals("Normal", events[1].type)
        assertEquals("Scheduled", events[1].reason)
    }

    @Test
    fun `details attach events when supplied`() {
        val eventsJson = """
            {"items":[{"type":"Normal","reason":"Pulled","message":"Image pulled",
            "lastTimestamp":"2026-08-23T10:00:00Z"}]}
        """.trimIndent()
        val events = K8sJson.decodeFromString<EventListDto>(eventsJson).items

        val details = podList().items[0].toDetails(events = events)

        assertEquals(1, details.events.size)
        assertEquals("Pulled", details.events[0].reason)
    }

    @Test
    fun `collects named container ports for resolving service targetPorts`() {
        val pod = K8sJson.decodeFromString<PodDto>(
            """
            {"metadata": {"name": "web-1", "namespace": "default"},
             "spec": {"containers": [
               {"name": "app", "image": "web:1", "ports": [
                 {"name": "http", "containerPort": 8080}, {"containerPort": 9000}]},
               {"name": "sidecar", "image": "proxy:1", "ports": [{"name": "metrics", "containerPort": 9090}]}
             ]},
             "status": {"phase": "Running"}}
            """.trimIndent(),
        )

        assertEquals(mapOf("http" to 8080, "metrics" to 9090), pod.toDomain().namedContainerPorts)
    }
}
