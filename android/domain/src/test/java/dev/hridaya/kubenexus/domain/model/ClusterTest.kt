package dev.hridaya.kubenexus.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterTest {

    private val kubeconfig = "users:\n- name: admin\n  user:\n    token: secret-token-12345\n"

    @Test
    fun `cluster toString never includes the kubeconfig`() {
        val text = Cluster(
            id = "id-1",
            name = "prod",
            serverUrl = "https://10.0.0.1:6443",
            rawKubeconfig = kubeconfig,
            contextName = "prod",
        ).toString()

        assertFalse(text.contains("secret-token-12345"))
        assertTrue(text.contains("name=prod"))
        assertTrue(text.contains("rawKubeconfig=<redacted>"))
    }

    @Test
    fun `parsed kubeconfig toString never includes the kubeconfig or its CA`() {
        val text = ParsedKubeconfig(
            clusterName = "prod",
            serverUrl = "https://10.0.0.1:6443",
            contextName = "prod",
            userName = "admin",
            namespace = "default",
            rawKubeconfig = kubeconfig,
            certificateAuthorityData = "LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0t",
        ).toString()

        assertFalse(text.contains("secret-token-12345"))
        assertFalse(text.contains("LS0tLS1CRUdJTiBDRVJUSUZJQ0FURS0tLS0t"))
        assertTrue(text.contains("serverUrl=https://10.0.0.1:6443"))
    }
}
