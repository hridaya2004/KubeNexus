package dev.hridaya.kubenexus.core.security

/**
 * Contract for encrypting and decrypting sensitive kubeconfig credentials stored at rest.
 */
interface KubeconfigEncryptor {

    /**
     * Encrypts the provided plaintext kubeconfig string into an authenticated ciphertext string.
     * If the content is already encrypted, returns the ciphertext as-is (idempotent).
     */
    fun encrypt(plainText: String): String

    /**
     * Decrypts the provided ciphertext string back into the original plaintext kubeconfig.
     * If the input is not encrypted (e.g. legacy plaintext records), returns it as-is for safe backward compatibility.
     *
     * @throws KubeconfigDecryptionException if the ciphertext is corrupt or the key that
     * encrypted it no longer exists.
     */
    fun decrypt(cipherText: String): String

    /**
     * Like [decrypt], but returns an empty string when the credentials cannot be decrypted.
     *
     * One unreadable cluster must not take the app down: its row is still listed, so the user
     * can see it and delete it, and every operation on it fails with an explanation (an empty
     * kubeconfig is reported as "credentials unavailable" by the native bridge).
     */
    fun decryptOrEmpty(cipherText: String): String = try {
        decrypt(cipherText)
    } catch (_: KubeconfigDecryptionException) {
        ""
    }

    /**
     * Returns true if the given string is encrypted by this encryptor format.
     */
    fun isEncrypted(text: String): Boolean
}

/** The stored credentials cannot be decrypted on this device. */
class KubeconfigDecryptionException(message: String, cause: Throwable? = null) :
    IllegalStateException(message, cause)

/**
 * A pass-through no-op encryptor primarily useful as a default fallback or for raw tests.
 */
object NoOpKubeconfigEncryptor : KubeconfigEncryptor {
    override fun encrypt(plainText: String): String = plainText
    override fun decrypt(cipherText: String): String = cipherText
    override fun isEncrypted(text: String): Boolean = false
}
