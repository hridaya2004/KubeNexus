package dev.hridaya.kubenexus.domain.util

import dev.hridaya.kubenexus.domain.model.DeploymentDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeploymentYamlGeneratorTest {

    // The renderer's output is pinned exactly, apart from the single trailing newline
    // asserted separately.
    private fun normalizeRenderedManifest(renderedManifest: String): String =
        renderedManifest.trimEnd('\n')

    @Test
    fun `renders the full manifest deterministically`() {
        val renderedManifest = DeploymentYamlGenerator.generate(
            DeploymentDraft(
                name = "nginx",
                namespace = "web",
                image = "nginx:1.27",
                replicas = 3,
                containerPort = 8080,
                serviceType = "None",
            ),
        )

        val expectedManifest = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: nginx
              namespace: web
              labels:
                app: nginx
            spec:
              replicas: 3
              selector:
                matchLabels:
                  app: nginx
              template:
                metadata:
                  labels:
                    app: nginx
                spec:
                  containers:
                    - name: nginx
                      image: nginx:1.27
                      ports:
                        - containerPort: 8080
        """.trimIndent()

        assertEquals(expectedManifest, normalizeRenderedManifest(renderedManifest))
        assertTrue(renderedManifest.endsWith("\n"))
    }

    // Issue #5 acceptance: the Deployment's labels and selector must match so a
    // Service added later can target the workload.
    @Test
    fun `pod labels match the deployment selector`() {
        val renderedManifest = DeploymentYamlGenerator.generate(
            DeploymentDraft(name = "api", namespace = "default", image = "api:v1"),
        )

        // One app label at each level: deployment metadata, selector matchLabels,
        // and the pod template.
        assertEquals(
            1,
            Regex("^    app: api$", RegexOption.MULTILINE).findAll(renderedManifest).count()
        )
        assertEquals(
            1,
            Regex("^      app: api$", RegexOption.MULTILINE).findAll(renderedManifest).count()
        )
        assertEquals(
            1,
            Regex("^        app: api$", RegexOption.MULTILINE).findAll(renderedManifest).count()
        )
    }

    @Test
    fun `values are interpolated verbatim`() {
        val renderedManifest = DeploymentYamlGenerator.generate(
            DeploymentDraft(
                name = "cache",
                namespace = "team-42",
                image = "registry.example.com/redis:7-alpine",
                replicas = 5,
                containerPort = 6379,
            ),
        )

        val normalizedManifest = normalizeRenderedManifest(renderedManifest)

        assertEquals("cache", Regex("name: (\\S+)").find(normalizedManifest)!!.groupValues[1])
        assertTrue(normalizedManifest.contains("image: registry.example.com/redis:7-alpine"))
        assertTrue(normalizedManifest.contains("replicas: 5"))
        assertTrue(normalizedManifest.contains("containerPort: 6379"))
    }

    @Test
    fun `renders combined deployment and service with matching labels and selectors when service is requested`() {
        val renderedManifest = DeploymentYamlGenerator.generate(
            DeploymentDraft(
                name = "web",
                namespace = "production",
                image = "nginx:alpine",
                replicas = 2,
                containerPort = 8080,
                serviceType = "ClusterIP",
                servicePort = 80,
            ),
        )

        val normalizedManifest = normalizeRenderedManifest(renderedManifest)
        assertTrue(normalizedManifest.contains("kind: Deployment"))
        assertTrue(normalizedManifest.contains("kind: Service"))
        assertTrue(normalizedManifest.contains("---"))
        assertTrue(normalizedManifest.contains("type: ClusterIP"))
        assertTrue(normalizedManifest.contains("port: 80"))
        assertTrue(normalizedManifest.contains("targetPort: 8080"))

        // Verify selector match
        assertTrue(normalizedManifest.contains("selector:\n    app: web"))
    }

    // YAML 1.1 reads plain `yes` as a boolean and `123` as a number, which the API server
    // then rejects ("missing metadata.name") or misroutes. They must be emitted as strings.
    @Test
    fun `names YAML would coerce are emitted as strings`() {
        val renderedManifest = DeploymentYamlGenerator.generate(
            DeploymentDraft(
                name = "yes",
                namespace = "123",
                image = "nginx:1.27",
                replicas = 1,
                containerPort = 80,
                serviceType = "None",
            ),
        )

        assertTrue(renderedManifest.contains("  name: \"yes\"\n"))
        assertTrue(renderedManifest.contains("  namespace: \"123\"\n"))
        assertTrue(renderedManifest.contains("app: \"yes\"\n"))
    }
}
