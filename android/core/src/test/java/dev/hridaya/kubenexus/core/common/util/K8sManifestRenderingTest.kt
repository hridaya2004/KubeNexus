package dev.hridaya.kubenexus.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class K8sManifestRenderingTest {

    @Test
    fun `renders nested maps and lists in block style`() {
        val manifest = linkedMapOf<String, Any>(
            "apiVersion" to "apps/v1",
            "kind" to "Deployment",
            "metadata" to linkedMapOf("name" to "nginx", "labels" to linkedMapOf("app" to "nginx")),
            "spec" to linkedMapOf(
                "replicas" to 3,
                "containers" to listOf(
                    linkedMapOf(
                        "name" to "nginx",
                        "image" to "nginx:1.27",
                        "ports" to listOf(linkedMapOf("containerPort" to 80)),
                    ),
                ),
            ),
        )

        assertEquals(
            """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: nginx
              labels:
                app: nginx
            spec:
              replicas: 3
              containers:
                - name: nginx
                  image: nginx:1.27
                  ports:
                    - containerPort: 80

            """.trimIndent(),
            renderK8sManifest(manifest),
        )
    }

    // The API server parses YAML 1.1, where these plain scalars are not strings.
    @Test
    fun `strings YAML 1_1 would read as booleans, nulls or numbers are quoted`() {
        for (text in listOf("yes", "No", "ON", "off", "true", "null", "~", "y", "123", "1:20", "0x1F", "1e3", "2001-12-14", "-dash", ".5")) {
            assertEquals(text, "\"$text\"", yamlScalar(text))
        }
    }

    @Test
    fun `ordinary names, images and paths stay plain`() {
        for (text in listOf("nginx", "my-app", "web.v2", "registry.example.com/team/api:1.2.3", "apps/v1", "ClusterIP", "app_name")) {
            assertEquals(text, yamlScalar(text))
        }
    }

    @Test
    fun `quoted strings escape what would break the document`() {
        assertEquals("\"a b\"", yamlScalar("a b"))
        assertEquals("\"say \\\"hi\\\"\"", yamlScalar("say \"hi\""))
        assertEquals("\"line\\nbreak\"", yamlScalar("line\nbreak"))
        assertEquals("\"trailing:\"", yamlScalar("trailing:"))
        assertEquals("\"# comment\"", yamlScalar("# comment"))
    }

    @Test
    fun `empty collections render inline`() {
        assertEquals("a: {}\nb: []\n", renderK8sManifest(linkedMapOf("a" to emptyMap<String, Any>(), "b" to emptyList<Any>())))
    }
}
