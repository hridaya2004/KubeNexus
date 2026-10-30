package dev.hridaya.kubenexus.data.security

import dev.hridaya.kubenexus.core.security.KubeconfigDecryptionException
import dev.hridaya.kubenexus.data.testing.newTestAead
import dev.hridaya.kubenexus.data.testing.testKubeconfigEncryptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

class TinkKubeconfigEncryptorTest {

    private val encryptor = testKubeconfigEncryptor()

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
            token: eyJhbGciOiJSUzI1NiIsImtpZCI6IiJ9...
    """.trimIndent()

    @Test
    fun `encrypt produces output prefixed with enc v2`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig, CLUSTER)

        assertTrue(encrypted.startsWith("enc:v2:"))
        assertNotEquals(sampleKubeconfig, encrypted)
        assertFalse(encryptor.needsReEncryption(encrypted))
    }

    @Test
    fun `decrypt restores original plaintext kubeconfig exactly`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig, CLUSTER)

        assertEquals(sampleKubeconfig, encryptor.decrypt(encrypted, CLUSTER))
    }

    // A ciphertext copied into another cluster's row must not decrypt there.
    @Test(expected = KubeconfigDecryptionException::class)
    fun `decrypting with another cluster's id throws KubeconfigDecryptionException`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig, CLUSTER)

        encryptor.decrypt(encrypted, "c-other")
    }

    @Test
    fun `decrypt on legacy plaintext string returns plaintext without failing`() {
        assertEquals(sampleKubeconfig, encryptor.decrypt(sampleKubeconfig, CLUSTER))
    }

    @Test
    fun `encrypt handles blank or empty text safely`() {
        assertEquals("", encryptor.encrypt("", CLUSTER))
        assertEquals("   ", encryptor.encrypt("   ", CLUSTER))
    }

    @Test
    fun `plaintext and enc v1 records need re-encryption, blank ones do not`() {
        assertTrue(encryptor.needsReEncryption(sampleKubeconfig))
        assertTrue(encryptor.needsReEncryption("enc:v1:AAAA"))
        assertFalse(encryptor.needsReEncryption(""))
    }

    @Test
    fun `enc v1 records still decrypt with the legacy key`() {
        val legacyKey = newLegacyKey()
        val withLegacyKey = testKubeconfigEncryptor(legacyKey)

        val decrypted = withLegacyKey.decrypt(legacyEncrypt(sampleKubeconfig, legacyKey), CLUSTER)

        assertEquals(sampleKubeconfig, decrypted)
    }

    @Test
    fun `enc v1 records are unreadable once the legacy key is gone`() {
        val stored = legacyEncrypt(sampleKubeconfig, newLegacyKey())

        assertEquals("", encryptor.decryptOrEmpty(stored, CLUSTER))
        assertEquals("", testKubeconfigEncryptor(newLegacyKey()).decryptOrEmpty(stored, CLUSTER))
    }

    @Test(expected = KubeconfigDecryptionException::class)
    fun `decrypt throws exception on corrupted payload`() {
        encryptor.decrypt("enc:v2:dGVzdA==", CLUSTER)
    }

    // The keyset is lost and regenerated: the stored ciphertext no longer authenticates.
    @Test
    fun `decryptOrEmpty returns empty for unreadable credentials instead of throwing`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig, CLUSTER)
        val otherEncryptor = testKubeconfigEncryptor()

        assertEquals("", otherEncryptor.decryptOrEmpty(encrypted, CLUSTER))
        assertEquals("", encryptor.decryptOrEmpty("enc:v2:dGVzdA==", CLUSTER))
        assertEquals(sampleKubeconfig, encryptor.decryptOrEmpty(encrypted, CLUSTER))
    }

    @Test
    fun `a keyset load failure is reported as unreadable credentials`() {
        val aead = newTestAead()
        val encrypted = TinkKubeconfigEncryptor({ aead }, { null }).encrypt(sampleKubeconfig, CLUSTER)
        val failingKeyset = TinkKubeconfigEncryptor(
            aead = { throw java.security.ProviderException("Keystore operation failed") },
            legacyKey = { null },
        )

        val thrown = runCatching { failingKeyset.decrypt(encrypted, CLUSTER) }.exceptionOrNull()

        assertTrue(thrown is KubeconfigDecryptionException)
        assertEquals("", failingKeyset.decryptOrEmpty(encrypted, CLUSTER))
    }

    private fun newLegacyKey(): SecretKey =
        KeyGenerator.getInstance("AES").apply { init(256, SecureRandom()) }.generateKey()

    /** The `enc:v1:` format written by earlier versions. */
    private fun legacyEncrypt(plainText: String, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return "enc:v1:" + Base64.getEncoder().encodeToString(payload)
    }

    private companion object {
        const val CLUSTER = "c-1"
    }
}
