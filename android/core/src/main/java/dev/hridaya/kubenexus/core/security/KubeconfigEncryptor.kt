package dev.hridaya.kubenexus.core.security

/**
 * Contract for encrypting and decrypting sensitive kubeconfig credentials stored at rest.
 *
 * Every ciphertext is bound to the cluster it belongs to: it only decrypts with the same
 * `clusterId`, so a ciphertext copied into another cluster's row is rejected.
 */
interface KubeconfigEncryptor {

    /** Encrypts the plaintext kubeconfig of [clusterId]. Blank text is returned as-is. */
    fun encrypt(plainText: String, clusterId: String): String

    /**
     * Decrypts the stored kubeconfig of [clusterId] back into plaintext.
     * Text stored in plain by earlier versions is returned as-is.
     *
     * @throws KubeconfigDecryptionException if the ciphertext is corrupt, belongs to another
     * cluster, or the key that encrypted it no longer exists.
     */
    fun decrypt(cipherText: String, clusterId: String): String

    /**
     * Like [decrypt], but returns an empty string when the credentials cannot be decrypted.
     *
     * One unreadable cluster must not take the app down: its row is still listed, so the user
     * can see it and delete it, and every operation on it fails with an explanation (an empty
     * kubeconfig is reported as "credentials unavailable" by the native bridge).
     */
    fun decryptOrEmpty(cipherText: String, clusterId: String): String = try {
        decrypt(cipherText, clusterId)
    } catch (_: KubeconfigDecryptionException) {
        ""
    }

    /**
     * Returns true if [stored] is plaintext or an older format, which should be rewritten
     * with [encrypt] after decrypting it.
     */
    fun needsReEncryption(stored: String): Boolean
}

/** The stored credentials cannot be decrypted on this device. */
class KubeconfigDecryptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * A pass-through no-op encryptor primarily useful as a default fallback or for raw tests.
 */
object NoOpKubeconfigEncryptor : KubeconfigEncryptor {
    override fun encrypt(plainText: String, clusterId: String): String = plainText
    override fun decrypt(cipherText: String, clusterId: String): String = cipherText
    override fun needsReEncryption(stored: String): Boolean = false
}
