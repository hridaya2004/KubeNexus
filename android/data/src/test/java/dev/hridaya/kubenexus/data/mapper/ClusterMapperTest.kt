package dev.hridaya.kubenexus.data.mapper

import dev.hridaya.kubenexus.core.security.KubeconfigDecryptionException
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
import dev.hridaya.kubenexus.domain.model.Cluster
import dev.hridaya.kubenexus.domain.model.ClusterStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClusterMapperTest {

    @Test
    fun `toDomain converts entity to domain model correctly`() {
        val entity = ClusterEntity(
            id = "c-1",
            name = "Production",
            serverUrl = "https://k8s.example.com:6443",
            rawKubeconfig = "apiVersion: v1...",
            contextName = "prod-ctx",
            userName = "admin",
            namespace = "default",
            isActive = true,
            createdAt = 1000L,
            lastConnectedAt = 2000L,
            status = "CONNECTED",
        )

        val domain = entity.toDomain()

        assertEquals("c-1", domain.id)
        assertEquals("Production", domain.name)
        assertEquals("https://k8s.example.com:6443", domain.serverUrl)
        assertEquals(true, domain.isActive)
        assertEquals(ClusterStatus.CONNECTED, domain.status)
        assertFalse(domain.credentialsUnavailable)
    }

    @Test
    fun `toEntity converts domain to entity model correctly`() {
        val domain = Cluster(
            id = "c-2",
            name = "Staging",
            serverUrl = "https://staging.k8s.example.com",
            contextName = "staging-ctx",
            userName = "developer",
            namespace = "kube-system",
            isActive = false,
            createdAt = 3000L,
            lastConnectedAt = null,
            status = ClusterStatus.DISCONNECTED,
        )

        val entity = domain.toEntity("apiVersion: v1...")

        assertEquals("c-2", entity.id)
        assertEquals("Staging", entity.name)
        assertEquals(false, entity.isActive)
        assertEquals("DISCONNECTED", entity.status)
        assertEquals("apiVersion: v1...", entity.rawKubeconfig)
    }

    @Test
    fun `toEntity encrypts the kubeconfig and toDomain can still read it`() {
        val encryptor = testKubeconfigEncryptor()
        val plainKubeconfig =
            "apiVersion: v1\nkind: Config\nusers:\n- name: admin\n  user:\n    token: test-token"

        val domain = Cluster(
            id = "c-3",
            name = "Secure Cluster",
            serverUrl = "https://secure.k8s.example.com",
            contextName = "secure-ctx",
        )

        val entity = domain.toEntity(plainKubeconfig, encryptor)
        assertTrue(entity.rawKubeconfig.startsWith("enc:v2:"))
        assertEquals(plainKubeconfig, encryptor.decrypt(entity.rawKubeconfig, "c-3"))

        assertFalse(entity.toDomain(encryptor).credentialsUnavailable)
    }

    // One unreadable row must not break the cluster list: it maps with the flag set.
    @Test
    fun `an entity whose credentials cannot be decrypted maps with credentialsUnavailable`() {
        val unreadable = object : KubeconfigEncryptor {
            override fun encrypt(plainText: String, clusterId: String) = plainText
            override fun decrypt(cipherText: String, clusterId: String): String =
                throw KubeconfigDecryptionException("key lost")

            override fun needsReEncryption(stored: String) = false
        }
        val entity = ClusterEntity(
            id = "c1",
            name = "prod",
            serverUrl = "https://prod.example:6443",
            rawKubeconfig = "enc:v1:AAAA",
            contextName = "prod",
            userName = "admin",
            namespace = "default",
            isActive = true,
            createdAt = 0L,
            lastConnectedAt = null,
            status = "CONNECTED",
        )

        val cluster = entity.toDomain(unreadable)

        assertTrue(cluster.credentialsUnavailable)
        assertEquals("prod", cluster.name)
    }
}
