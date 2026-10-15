package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.data.resource.codec.DeploymentCodec
import dev.hridaya.kubenexus.data.resource.codec.PodCodec
import dev.hridaya.kubenexus.data.resource.codec.ServiceCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache stores each kind's list projection rather than the API object, so
 * every codec must rebuild exactly the summary a live list would have produced.
 */
class ResourceCodecsTest {

    private fun loadFixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "fixture $name not found on the test classpath"
        }.bufferedReader().use { it.readText() }

    private fun listOf(objectFixture: String): String =
        """{"items":[${loadFixture(objectFixture)}]}"""

    private fun assertSurvivesCache(codec: ResourceCodec<*, *>, listJson: String) {
        val live = codec.decodeSummaries(listJson)
        val cached = codec.decodeForCache(listJson)

        assertTrue("fixture decoded to nothing", live.isNotEmpty())
        assertEquals(live, cached.map { codec.summaryFromCache(it.payload) })
        assertEquals(live.map { it.name }, cached.map { it.name })
        assertEquals(live.map { it.namespace }, cached.map { it.namespace })
    }

    @Test
    fun `pods survive the cache round trip`() =
        assertSurvivesCache(PodCodec, loadFixture("pod-list.json"))

    @Test
    fun `deployments survive the cache round trip`() =
        assertSurvivesCache(DeploymentCodec, listOf("deployment-detail.json"))

    @Test
    fun `services survive the cache round trip`() =
        assertSurvivesCache(ServiceCodec, listOf("service-detail.json"))

    @Test
    fun `the cached payload keeps only the projection`() {
        val fixture = loadFixture("pod-list.json")
        val payloads = PodCodec.decodeForCache(fixture).map { it.payload }

        // Present in the fixture, but not something the pod projection models.
        for (unmodelled in listOf("ownerReferences", "tolerations", "dnsPolicy")) {
            assertTrue("fixture lacks $unmodelled", "\"$unmodelled\"" in fixture)
            assertFalse("$unmodelled was cached", payloads.any { "\"$unmodelled\"" in it })
        }
    }

    @Test
    fun `annotations never reach the cache`() {
        val lastApplied = """{\"env\":[{\"name\":\"DB_PASSWORD\",\"value\":\"hunter2\"}]}"""
        val listJson = """
            {"items":[{
              "metadata":{
                "name":"api","namespace":"prod","labels":{"app":"api"},
                "annotations":{"kubectl.kubernetes.io/last-applied-configuration":"$lastApplied"}
              }
            }]}
        """.trimIndent()

        for (codec in listOf(PodCodec, DeploymentCodec, ServiceCodec)) {
            val payload = codec.decodeForCache(listJson).single().payload

            assertFalse("${codec.kind} cached annotations", "annotations" in payload)
            assertFalse("${codec.kind} cached the applied manifest", "hunter2" in payload)
            assertTrue("${codec.kind} dropped labels too", "\"app\"" in payload)
            assertEquals(codec.decodeSummaries(listJson), listOf(codec.summaryFromCache(payload)))
        }
    }
}
