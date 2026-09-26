package dev.hridaya.kubenexus.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import javax.crypto.SecretKey

class KubeconfigEncryptorTest {

    private lateinit var secretKey: SecretKey
    private lateinit var encryptor: AesGcmKubeconfigEncryptor

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

    @Before
    fun setUp() {
        secretKey = AesGcmKubeconfigEncryptor.generateKey()
        encryptor = AesGcmKubeconfigEncryptor(secretKey)
    }

    @Test
    fun `encrypt produces output prefixed with enc v1`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig)

        assertTrue(encrypted.startsWith("enc:v1:"))
        assertNotEquals(sampleKubeconfig, encrypted)
        assertTrue(encryptor.isEncrypted(encrypted))
    }

    @Test
    fun `decrypt restores original plaintext kubeconfig exactly`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig)
        val decrypted = encryptor.decrypt(encrypted)

        assertEquals(sampleKubeconfig, decrypted)
    }

    @Test
    fun `isEncrypted returns false for plaintext and true for encrypted`() {
        assertFalse(encryptor.isEncrypted(sampleKubeconfig))
        assertFalse(encryptor.isEncrypted("apiVersion: v1"))
        assertFalse(encryptor.isEncrypted(""))

        val encrypted = encryptor.encrypt(sampleKubeconfig)
        assertTrue(encryptor.isEncrypted(encrypted))
    }

    @Test
    fun `decrypt on legacy plaintext string returns plaintext without failing`() {
        val plaintextResult = encryptor.decrypt(sampleKubeconfig)
        assertEquals(sampleKubeconfig, plaintextResult)
    }

    @Test
    fun `encrypt is idempotent when called on already encrypted text`() {
        val encrypted1 = encryptor.encrypt(sampleKubeconfig)
        val encrypted2 = encryptor.encrypt(encrypted1)

        assertEquals(encrypted1, encrypted2)
    }

    @Test
    fun `encrypt handles blank or empty text safely`() {
        assertEquals("", encryptor.encrypt(""))
        assertEquals("   ", encryptor.encrypt("   "))
    }

    @Test(expected = KubeconfigDecryptionException::class)
    fun `decrypt throws exception on corrupted payload length`() {
        encryptor.decrypt("enc:v1:dGVzdA==") // "test" base64 is too short for GCM IV + Tag
    }

    // The Keystore key is lost and regenerated: the stored ciphertext no longer authenticates.
    @Test(expected = KubeconfigDecryptionException::class)
    fun `decrypting with a different key throws KubeconfigDecryptionException`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig)
        val otherEncryptor = AesGcmKubeconfigEncryptor(AesGcmKubeconfigEncryptor.generateKey())

        otherEncryptor.decrypt(encrypted)
    }

    @Test
    fun `decryptOrEmpty returns empty for unreadable credentials instead of throwing`() {
        val encrypted = encryptor.encrypt(sampleKubeconfig)
        val otherEncryptor = AesGcmKubeconfigEncryptor(AesGcmKubeconfigEncryptor.generateKey())

        assertEquals("", otherEncryptor.decryptOrEmpty(encrypted))
        assertEquals("", encryptor.decryptOrEmpty("enc:v1:dGVzdA=="))
        assertEquals(sampleKubeconfig, encryptor.decryptOrEmpty(encrypted))
    }
}
