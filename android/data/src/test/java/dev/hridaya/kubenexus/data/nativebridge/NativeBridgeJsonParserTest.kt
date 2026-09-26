package dev.hridaya.kubenexus.data.nativebridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NativeBridgeJsonParserTest {

    private lateinit var parser: NativeBridgeJsonParser

    @Before
    fun setUp() {
        parser = NativeBridgeJsonParser()
    }

    private fun loadFixture(name: String): String =
        javaClass.classLoader?.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun `parseAPIResources returns empty list for blank input`() {
        assertTrue(parser.parseAPIResources("").isEmpty())
        assertTrue(parser.parseAPIResources("   ").isEmpty())
    }

    @Test
    fun `parseAPIResources splits groupVersion, dedupes and sorts by name`() {
        val resources = parser.parseAPIResources(loadFixture("api-resources.json"))

        assertEquals(4, resources.size)

        // Sorted by name, then group version: apps/v1 pods before core v1 pods.
        assertEquals("deployments", resources[0].name)
        assertEquals("apps/v1", resources[0].groupVersion)
        assertEquals("apps", resources[0].group)
        assertEquals("v1", resources[0].version)

        val corePods = resources.last { it.name == "pods" }
        assertEquals("", corePods.group)
        assertEquals("v1", corePods.version)
        assertEquals("pod", corePods.singularName)
        assertTrue(corePods.namespaced)
        assertEquals(listOf("po"), corePods.shortNames)
        assertEquals(listOf("all"), corePods.categories)

        val nodes = resources.first { it.name == "nodes" }
        assertFalse(nodes.namespaced)
        assertEquals("Node", nodes.kind)
    }

    @Test
    fun `parseAPIResources drops repeated entries within a group version`() {
        val payload = loadFixture("api-resources-duplicates.json")

        assertEquals(1, parser.parseAPIResources(payload).size)
    }

    @Test
    fun `resolveResourceExplain falls back to title-cased stub without schema`() {
        val explain = parser.resolveResourceExplain("", "pod", "")

        assertEquals("Pod", explain.kind)
        assertEquals("", explain.groupVersion)
        assertTrue(explain.description.contains("schema unavailable"))
        assertTrue(explain.fields.isNotEmpty())
        assertTrue(explain.fields.any { it.name == "spec" && it.type == "object" })
    }

    @Test
    fun `resolveResourceExplain leaves versioned custom resources untouched`() {
        val explain = parser.resolveResourceExplain("", "mycustomresource", "custom.io/v1alpha1")

        assertEquals("mycustomresource", explain.kind)
        assertEquals("custom.io/v1alpha1", explain.groupVersion)
    }

    @Test
    fun `resolveResourceExplain matches singular and plural against schema GVKs`() {
        val schema = loadFixture("openapi-pod-schema.json")

        val pod = parser.resolveResourceExplain(schema, "pod", "")
        assertEquals("Pod", pod.kind)
        assertEquals("", pod.group)
        assertEquals("v1", pod.version)
        assertEquals("v1", pod.groupVersion)
        assertEquals("Pod is a collection of containers.", pod.description)

        // Fields come out sorted by name; $ref maps to its last path segment;
        // format rides along in parentheses; required comes from the schema.
        assertEquals(
            listOf("apiVersion", "restartPolicy", "status", "terminationGracePeriodSeconds"),
            pod.fields.map { it.name },
        )
        val status = pod.fields.first { it.name == "status" }
        assertEquals("PodStatus", status.type)
        assertFalse(status.required)
        assertTrue(pod.fields.first { it.name == "apiVersion" }.required)
        assertEquals(
            "integer (int64)",
            pod.fields.first { it.name == "terminationGracePeriodSeconds" }.type
        )

        val podsPlural = parser.resolveResourceExplain(schema, "pods", "v1")
        assertEquals("Pod", podsPlural.kind)
    }

    @Test
    fun `findDefinitionByGVK matches exact GVK regardless of pluralization`() {
        val schema = loadFixture("openapi-schema-test.json")

        val policy = parser.findDefinitionByGVK(schema, "kyverno.io", "v1", "Policy")
        assertEquals("Policy", policy?.kind)
        assertEquals("Kyverno policy rule set.", policy?.description)

        // The heuristic finder only knows singular+s, so irregular plurals
        // require the discovery-driven GVK path.
        assertNull(parser.findDefinition(schema, "policies", "kyverno.io/v1"))
    }

    @Test
    fun `resolveResourceExplain uses groupVersion to disambiguate`() {
        val schema = loadFixture("openapi-pod-multiversion.json")

        assertEquals(
            "Something pod.",
            parser.resolveResourceExplain(schema, "pod", "something/v2").description,
        )
        assertEquals(
            "Core pod.",
            parser.resolveResourceExplain(schema, "pod", "v1").description,
        )
    }

    // The repository parses the document once and reuses the definitions map for
    // both the GVK lookup and the name-based fallback. These pin that the
    // pre-parsed overloads behave identically to the String ones, since a
    // multi-megabyte document was previously parsed up to four times per call.
    @Test
    fun `parseDefinitions returns the definitions map or null`() {
        val schema = loadFixture("openapi-pod-schema.json")

        val definitions = parser.parseDefinitions(schema)
        assertNotNull(definitions)
        assertTrue(definitions!!.has("io.k8s.api.core.v1.Pod"))

        assertNull(parser.parseDefinitions(""))
        assertNull(parser.parseDefinitions("   "))
        assertNull(parser.parseDefinitions("""{"paths":{}}"""))
    }

    @Test
    fun `pre-parsed findDefinitionByGVK matches the string overload`() {
        val schema = loadFixture("openapi-pod-schema.json")
        val definitions = parser.parseDefinitions(schema)!!

        val fromString = parser.findDefinitionByGVK(schema, "", "v1", "Pod")
        val fromParsed = parser.findDefinitionByGVK(definitions, "", "v1", "Pod")

        assertNotNull(fromString)
        // lastUpdated is stamped at construction, so the two calls can differ by a millisecond.
        assertEquals(fromString!!.copy(lastUpdated = 0), fromParsed!!.copy(lastUpdated = 0))
        assertEquals("Pod", fromParsed.kind)
    }

    @Test
    fun `pre-parsed findDefinition matches the string overload`() {
        val schema = loadFixture("openapi-pod-schema.json")
        val definitions = parser.parseDefinitions(schema)!!

        val fromString = parser.findDefinition(schema, "pods", "v1")
        val fromParsed = parser.findDefinition(definitions, "pods", "v1")

        assertNotNull(fromString)
        // lastUpdated is stamped at construction, so the two calls can differ by a millisecond.
        assertEquals(fromString!!.copy(lastUpdated = 0), fromParsed!!.copy(lastUpdated = 0))
    }

    @Test
    fun `pre-parsed lookups agree on a miss`() {
        val definitions = parser.parseDefinitions(loadFixture("openapi-pod-schema.json"))!!

        assertNull(parser.findDefinitionByGVK(definitions, "acme.io", "v1", "Widget"))
        assertNull(parser.findDefinition(definitions, "widgets", "acme.io/v1"))
        // A blank kind cannot be resolved by GVK.
        assertNull(parser.findDefinitionByGVK(definitions, "", "v1", ""))
    }
}
