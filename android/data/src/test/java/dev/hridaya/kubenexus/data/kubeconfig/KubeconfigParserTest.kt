package dev.hridaya.kubenexus.data.kubeconfig

import dev.hridaya.kubenexus.domain.model.Cluster
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KubeconfigParserTest {

    private val sampleKubeconfig = """
        apiVersion: v1
        clusters:
        - cluster:
            certificate-authority-data: LS0tLS1CRUdJTi...
            server: https://192.168.49.2:8443
          name: minikube
        contexts:
        - context:
            cluster: minikube
            namespace: default
            user: minikube
          name: minikube
        current-context: minikube
        kind: Config
        preferences: {}
        users:
        - name: minikube
          user:
            client-certificate-data: LS0tLS1CRUdJTi...
            client-key-data: LS0tLS1CRUdJTi...
    """.trimIndent()

    @Test
    fun `parse valid kubeconfig extracts correct metadata`() {
        val parsed = KubeconfigParser.parse(sampleKubeconfig)

        assertEquals("minikube", parsed.clusterName)
        assertEquals("https://192.168.49.2:8443", parsed.serverUrl)
        assertEquals("minikube", parsed.contextName)
        assertEquals("minikube", parsed.userName)
        assertEquals("default", parsed.namespace)
        assertEquals("LS0tLS1CRUdJTi...", parsed.certificateAuthorityData)
        assertEquals(false, parsed.insecureSkipTlsVerify)
    }

    @Test
    fun `parse kubeconfig with insecure-skip-tls-verify extracts flag`() {
        val insecureKubeconfig = """
            apiVersion: v1
            clusters:
            - cluster:
                insecure-skip-tls-verify: true
                server: https://10.0.0.1:6443
              name: insecure-cluster
            contexts:
            - context:
                cluster: insecure-cluster
                user: admin
              name: insecure-ctx
            current-context: insecure-ctx
            kind: Config
        """.trimIndent()

        val parsed = KubeconfigParser.parse(insecureKubeconfig)
        assertEquals(true, parsed.insecureSkipTlsVerify)
        assertEquals(null, parsed.certificateAuthorityData)
    }

    @Test
    fun `parse multi-cluster kubeconfig extracts targeted cluster TLS config`() {
        val multiClusterKubeconfig = """
            apiVersion: v1
            clusters:
            - name: dev-cluster
              cluster:
                insecure-skip-tls-verify: true
                server: https://10.0.0.1:6443
            - name: prod-cluster
              cluster:
                certificate-authority-data: cHJvZC1jYS1kYXRh
                insecure-skip-tls-verify: false
                server: https://10.0.0.2:6443
            contexts:
            - name: dev-ctx
              context:
                cluster: dev-cluster
            - name: prod-ctx
              context:
                cluster: prod-cluster
            current-context: prod-ctx
            kind: Config
        """.trimIndent()

        val parsed = KubeconfigParser.parse(multiClusterKubeconfig)
        assertEquals("prod-cluster", parsed.clusterName)
        assertEquals("https://10.0.0.2:6443", parsed.serverUrl)
        assertEquals("cHJvZC1jYS1kYXRh", parsed.certificateAuthorityData)
        assertEquals(false, parsed.insecureSkipTlsVerify)
    }

    @Test
    fun `parse with custom name overrides extracted name`() {
        val parsed = KubeconfigParser.parse(sampleKubeconfig, customName = "My Minikube Lab")

        assertEquals("My Minikube Lab", parsed.clusterName)
        assertEquals("https://192.168.49.2:8443", parsed.serverUrl)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse empty string throws exception`() {
        KubeconfigParser.parse("")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse kubeconfig without server throws exception`() {
        val invalidKubeconfig = """
            apiVersion: v1
            kind: Config
            current-context: dev-cluster
        """.trimIndent()
        KubeconfigParser.parse(invalidKubeconfig)
    }

    // Cluster, context and user entries sharing names is the norm (kind, EKS, GKE). The
    // stored identity must be the one clientcmd connects to, whatever the entry order.
    private fun sharedNamesKubeconfig(order: List<String>, current: String): String {
        fun cluster(n: String) = "- cluster:\n    server: https://$n.example:6443\n  name: $n"
        fun context(n: String) = "- context:\n    cluster: $n\n    namespace: $n-ns\n    user: $n-user\n  name: $n"
        fun user(n: String) = "- name: $n-user\n  user:\n    token: $n-token"
        return buildString {
            appendLine("apiVersion: v1")
            appendLine("kind: Config")
            appendLine("clusters:"); order.forEach { appendLine(cluster(it)) }
            appendLine("contexts:"); order.forEach { appendLine(context(it)) }
            appendLine("users:"); order.forEach { appendLine(user(it)) }
            appendLine("current-context: $current")
        }
    }

    @Test
    fun `parse resolves the current context when names are shared, in either order`() {
        for (order in listOf(listOf("staging", "prod"), listOf("prod", "staging"))) {
            val parsed = KubeconfigParser.parse(sharedNamesKubeconfig(order, current = "prod"))

            assertEquals("order $order", "prod", parsed.clusterName)
            assertEquals("order $order", "https://prod.example:6443", parsed.serverUrl)
            assertEquals("order $order", "prod", parsed.contextName)
            assertEquals("order $order", "prod-user", parsed.userName)
            assertEquals("order $order", "prod-ns", parsed.namespace)
        }
    }

    @Test
    fun `parse does not confuse a name with another that it prefixes`() {
        val parsed = KubeconfigParser.parse(
            sharedNamesKubeconfig(listOf("production", "prod"), current = "prod"),
        )

        assertEquals("https://prod.example:6443", parsed.serverUrl)
        assertEquals("prod-user", parsed.userName)
    }

    @Test
    fun `parse reads json kubeconfigs`() {
        val json = """
            {"apiVersion": "v1", "kind": "Config",
             "clusters": [{"name": "c1", "cluster": {"server": "https://c1.example:6443", "insecure-skip-tls-verify": true}}],
             "contexts": [{"name": "ctx", "context": {"cluster": "c1", "user": "u1"}}],
             "users": [{"name": "u1", "user": {"token": "t"}}],
             "current-context": "ctx"}
        """.trimIndent()

        val parsed = KubeconfigParser.parse(json)

        assertEquals("c1", parsed.clusterName)
        assertEquals("https://c1.example:6443", parsed.serverUrl)
        assertEquals("ctx", parsed.contextName)
        assertEquals("u1", parsed.userName)
        assertEquals(true, parsed.insecureSkipTlsVerify)
    }

    @Test
    fun `parse handles comments and quoted values`() {
        val kubeconfig = """
            # exported from the dashboard
            apiVersion: v1
            kind: Config
            clusters:
            - name: "edge"   # the lab cluster
              cluster:
                server: 'https://edge.example:6443'
            contexts:
            - name: "edge-admin"
              context:
                cluster: "edge"
                user: 'admin'
            current-context: "edge-admin"
        """.trimIndent()

        val parsed = KubeconfigParser.parse(kubeconfig)

        assertEquals("edge", parsed.clusterName)
        assertEquals("https://edge.example:6443", parsed.serverUrl)
        assertEquals("edge-admin", parsed.contextName)
        assertEquals("admin", parsed.userName)
        assertEquals("default", parsed.namespace)
    }

    @Test
    fun `parse explains a missing or dangling current-context instead of guessing`() {
        val withoutCurrent = sharedNamesKubeconfig(listOf("a"), current = "a").replace("current-context: a\n", "")
        val noCurrent = runCatching { KubeconfigParser.parse(withoutCurrent) }.exceptionOrNull()
        assertTrue(noCurrent is IllegalArgumentException)
        assertTrue(noCurrent!!.message!!.contains("no current-context"))

        val dangling = runCatching {
            KubeconfigParser.parse(sharedNamesKubeconfig(listOf("a"), current = "missing"))
        }.exceptionOrNull()
        assertTrue(dangling is IllegalArgumentException)
        assertTrue(dangling!!.message!!.contains("\"missing\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parse rejects text that is not yaml`() {
        KubeconfigParser.parse("clusters: [unclosed")
    }

    @Test
    fun `parse rejects kubeconfigs over the size limit, counted in utf-8 bytes`() {
        // Pads with a comment, so only the size can make the kubeconfig invalid.
        fun padded(filler: String, bytes: Int): String {
            val prefix = sampleKubeconfig + "\n# "
            val count = (bytes - prefix.encodeToByteArray().size) / filler.encodeToByteArray().size
            return prefix + filler.repeat(count)
        }

        assertEquals("minikube", KubeconfigParser.parse(padded("x", Cluster.MAX_KUBECONFIG_BYTES)).clusterName)

        listOf(padded("x", Cluster.MAX_KUBECONFIG_BYTES + 1), padded("é", Cluster.MAX_KUBECONFIG_BYTES + 2)).forEach { oversized ->
            val error = runCatching { KubeconfigParser.parse(oversized) }.exceptionOrNull()

            assertTrue(error is IllegalArgumentException)
            assertTrue(error!!.message!!.contains("larger than 1 MB"))
        }
    }
}
