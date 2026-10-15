package dev.hridaya.kubenexus.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonFormatterTest {

    @Test
    fun `formatIfJson returns formatted string for simple json object`() {
        val raw = "{\"name\":\"nginx\",\"replicas\":3}"
        val formatted = JsonFormatter.formatIfJson(raw)
        assertNotNull(formatted)
        val expected = """
            {
              "name": "nginx",
              "replicas": 3
            }
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `formatIfJson returns formatted string for json array`() {
        val raw = "[\"worker-1\",\"worker-2\",\"worker-3\"]"
        val formatted = JsonFormatter.formatIfJson(raw)
        assertNotNull(formatted)
        val expected = """
            [
              "worker-1",
              "worker-2",
              "worker-3"
            ]
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `formatIfJson handles nested kubernetes annotation json`() {
        val raw =
            """{"apiVersion":"v1","kind":"Pod","metadata":{"name":"test-pod","labels":{"app":"demo"}}}"""
        val formatted = JsonFormatter.formatIfJson(raw)
        assertNotNull(formatted)
        val expected = """
            {
              "apiVersion": "v1",
              "kind": "Pod",
              "metadata": {
                "name": "test-pod",
                "labels": {
                  "app": "demo"
                }
              }
            }
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `formatIfJson ignores leading and trailing whitespace`() {
        val raw = "   {\"key\": \"value\"}   \n"
        val formatted = JsonFormatter.formatIfJson(raw)
        assertNotNull(formatted)
        val expected = """
            {
              "key": "value"
            }
        """.trimIndent()
        assertEquals(expected, formatted)
    }

    @Test
    fun `formatIfJson returns formatted string for empty object and array`() {
        assertEquals("{}", JsonFormatter.formatIfJson("{}"))
        assertEquals("[]", JsonFormatter.formatIfJson("[]"))
    }

    @Test
    fun `formatIfJson returns null for plain string values`() {
        assertNull(JsonFormatter.formatIfJson("nginx"))
        assertNull(JsonFormatter.formatIfJson("app.kubernetes.io/name"))
        assertNull(JsonFormatter.formatIfJson("https://github.com/hridaya2004/kubenexus"))
    }

    @Test
    fun `formatIfJson returns null for primitive types`() {
        assertNull(JsonFormatter.formatIfJson("12345"))
        assertNull(JsonFormatter.formatIfJson("true"))
        assertNull(JsonFormatter.formatIfJson("false"))
        assertNull(JsonFormatter.formatIfJson("\"string literal\""))
    }

    @Test
    fun `formatIfJson returns null for blank or too short inputs`() {
        assertNull(JsonFormatter.formatIfJson(""))
        assertNull(JsonFormatter.formatIfJson("   "))
        assertNull(JsonFormatter.formatIfJson("{"))
        assertNull(JsonFormatter.formatIfJson("}"))
        assertNull(JsonFormatter.formatIfJson("["))
    }

    @Test
    fun `formatIfJson returns null for malformed json`() {
        assertNull(JsonFormatter.formatIfJson("{not: valid, json}"))
        assertNull(JsonFormatter.formatIfJson("{\"unclosed\": \"bracket\""))
        assertNull(JsonFormatter.formatIfJson("[1, 2, 3"))
        assertNull(JsonFormatter.formatIfJson("[\"unterminated]"))
    }

    @Test
    fun `isJson returns true only for valid objects and arrays`() {
        assertTrue(JsonFormatter.isJson("{\"a\":1}"))
        assertTrue(JsonFormatter.isJson("[1,2]"))
        assertFalse(JsonFormatter.isJson("plain-string"))
        assertFalse(JsonFormatter.isJson("{bad json}"))
        assertFalse(JsonFormatter.isJson("42"))
    }
}
