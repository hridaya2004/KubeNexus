package dev.hridaya.kubenexus.data.resource

import dev.hridaya.kubenexus.core.common.result.AppError
import dev.hridaya.kubenexus.core.common.result.Result
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestParserTest {

    @Test
    fun `reads the type of every document and keeps its text`() {
        val deployment = "apiVersion: apps/v1\nkind: Deployment\nmetadata:\n  name: web"
        val service = "apiVersion: v1\nkind: Service\nmetadata:\n  name: web"

        val documents =
            (ManifestParser.parse("$deployment\n---\n$service\n") as Result.Success).data

        assertEquals(
            listOf(
                ManifestDocument(
                    apiVersion = "apps/v1",
                    kind = "Deployment",
                    text = deployment,
                    name = "web"
                ),
                ManifestDocument(apiVersion = "v1", kind = "Service", text = service, name = "web"),
            ),
            documents,
        )
        assertEquals("Deployment 'web'", documents.first().label)
    }

    @Test
    fun `text the parser cannot handle is a validation error, not a crash`() {
        val deeplyNested = "a: " + "[".repeat(50_000)

        val result = ManifestParser.parse(deeplyNested)

        assertTrue(result is Result.Error)
    }

    @Test
    fun `accepts a json manifest`() {
        val json = """{"apiVersion":"v1","kind":"ConfigMap","metadata":{"name":"settings"}}"""

        val document = (ManifestParser.parse(json) as Result.Success).data.single()

        assertEquals("ConfigMap", document.kind)
    }

    @Test
    fun `skips documents that hold only comments`() {
        val manifest = "# leading notes\n---\napiVersion: v1\nkind: Pod\n---\n# trailing notes"

        val documents = (ManifestParser.parse(manifest) as Result.Success).data

        assertEquals(listOf("Pod"), documents.map { it.kind })
    }

    @Test
    fun `an empty manifest is invalid`() {
        assertValidationError(ManifestParser.parse("  \n---\n"))
    }

    @Test
    fun `a document that is not an object is invalid`() {
        assertValidationError(ManifestParser.parse("- just\n- a list"))
    }

    @Test
    fun `malformed yaml is invalid rather than thrown`() {
        assertValidationError(ManifestParser.parse("kind: Pod\nmetadata: [unclosed"))
    }

    private fun assertValidationError(result: Result<*>) {
        assertTrue(
            "expected a validation error, got $result",
            (result as? Result.Error)?.error is AppError.Validation
        )
    }
}
