package dev.hridaya.kubenexus.domain.util

import dev.hridaya.kubenexus.domain.model.ServiceDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceYamlGeneratorTest {

    // The renderer's output is pinned exactly, apart from the single trailing newline
    // asserted separately.
    private fun normalizeRenderedManifest(renderedManifest: String): String =
        renderedManifest.trimEnd('\n')

    @Test
    fun `renders the full manifest deterministically`() {
        val renderedManifest = ServiceYamlGenerator.generate(
            ServiceDraft(
                name = "nginx",
                namespace = "web",
                selectorApp = "nginx",
                port = 80,
                targetPort = 8080,
                serviceType = "NodePort",
            ),
        )

        val expectedManifest = """
            apiVersion: v1
            kind: Service
            metadata:
              name: nginx
              namespace: web
              labels:
                app: nginx
            spec:
              selector:
                app: nginx
              type: NodePort
              ports:
                - port: 80
                  targetPort: 8080
        """.trimIndent()

        assertEquals(expectedManifest, normalizeRenderedManifest(renderedManifest))
        assertTrue(renderedManifest.endsWith("\n"))
    }

    // Issue #5 acceptance: the selector intentionally targets the app label the
    // Deployment generator stamps onto pods, independent of the Service's name.
    @Test
    fun `labels carry the service name while the selector carries the target app`() {
        val renderedManifest = ServiceYamlGenerator.generate(
            ServiceDraft(
                name = "web-svc",
                namespace = "default",
                selectorApp = "web",
                port = 80,
                targetPort = 8080,
            ),
        )

        val normalizedManifest = normalizeRenderedManifest(renderedManifest)
        val appValues = Regex("^    app: (.+)$", RegexOption.MULTILINE)
            .findAll(normalizedManifest)
            .map { it.groupValues[1] }
            .toList()

        assertEquals(listOf("web-svc", "web"), appValues)
    }

    @Test
    fun `default type is emitted verbatim`() {
        val renderedManifest = ServiceYamlGenerator.generate(
            ServiceDraft(
                name = "cache",
                namespace = "team-42",
                selectorApp = "cache",
                port = 6379,
                targetPort = 6379,
            ),
        )

        assertTrue(renderedManifest.contains("  type: ${ServiceDraft.DEFAULT_TYPE}"))
        assertTrue(
            Regex("^    - port: 6379$", RegexOption.MULTILINE).containsMatchIn(
                renderedManifest
            )
        )
        assertTrue(renderedManifest.contains("targetPort: 6379"))
    }

    // Traffic arriving on the service port is forwarded to targetPort on the
    // pods; both keys must carry their own distinct value in the applied text.
    @Test
    fun `target port is emitted separately from the service port`() {
        val renderedManifest = ServiceYamlGenerator.generate(
            ServiceDraft(
                name = "web-svc",
                namespace = "default",
                selectorApp = "web",
                port = 80,
                targetPort = 8080,
            ),
        )

        val emittedPorts = Regex("(port|targetPort): (\\d+)")
            .findAll(normalizeRenderedManifest(renderedManifest))
            .map { "${it.groupValues[1]}=${it.groupValues[2]}" }
            .toList()

        assertEquals(listOf("port=80", "targetPort=8080"), emittedPorts)
    }
}
