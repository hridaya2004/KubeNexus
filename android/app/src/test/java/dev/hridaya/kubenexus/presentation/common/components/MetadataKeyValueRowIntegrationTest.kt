package dev.hridaya.kubenexus.presentation.common.components

import dev.hridaya.kubenexus.core.common.util.JsonFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataKeyValueRowIntegrationTest {

    @Test
    fun `last-applied-configuration annotation is recognized as JSON and formatted`() {
        val lastAppliedConfig =
            """{"apiVersion":"apps/v1","kind":"Deployment","metadata":{"name":"web-frontend"}}"""
        assertTrue(JsonFormatter.isJson(lastAppliedConfig))

        val formatted = JsonFormatter.formatIfJson(lastAppliedConfig)
        assertNotNull(formatted)
        val expected = """
            {
              "apiVersion": "apps/v1",
              "kind": "Deployment",
              "metadata": {
                "name": "web-frontend"
              }
            }
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `standard kubernetes labels and annotations are not detected as JSON`() {
        val labelValue = "web-frontend"
        val revisionAnnotation = "4"
        val cloudAnnotation = "External"

        assertFalse(JsonFormatter.isJson(labelValue))
        assertNull(JsonFormatter.formatIfJson(labelValue))

        assertFalse(JsonFormatter.isJson(revisionAnnotation))
        assertNull(JsonFormatter.formatIfJson(revisionAnnotation))

        assertFalse(JsonFormatter.isJson(cloudAnnotation))
        assertNull(JsonFormatter.formatIfJson(cloudAnnotation))
    }

    @Test
    fun `json array annotation is recognized as JSON`() {
        val arrayJson = """["us-central1-a", "us-central1-b"]"""
        assertTrue(JsonFormatter.isJson(arrayJson))

        val formatted = JsonFormatter.formatIfJson(arrayJson)
        assertNotNull(formatted)
        val expected = """
            [
              "us-central1-a",
              "us-central1-b"
            ]
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `malformed json does not crash and returns null`() {
        val malformed = "{unquoted_key: 123"
        assertFalse(JsonFormatter.isJson(malformed))
        assertNull(JsonFormatter.formatIfJson(malformed))
    }
}
