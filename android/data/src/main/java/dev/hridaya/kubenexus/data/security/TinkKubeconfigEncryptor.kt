package dev.hridaya.kubenexus.data.security

import com.google.crypto.tink.Aead
import dev.hridaya.kubenexus.core.security.KubeconfigDecryptionException
import dev.hridaya.kubenexus.core.security.KubeconfigEncryptor
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * [KubeconfigEncryptor] backed by a Tink [Aead], with the cluster ID as associated data.
 *
 * Writes `enc:v2:<base64(tink ciphertext)>`. Still reads what earlier versions stored, so the
 * startup migration can rewrite it: plaintext, and `enc:v1:<base64(12_byte_iv + ciphertext)>`
 * (AES-256-GCM under [legacyKey], no associated data).
 */
open class TinkKubeconfigEncryptor(
    private val aead: () -> Aead,
    private val legacyKey: () -> SecretKey?,
) : KubeconfigEncryptor {

    companion object {
        const val ENCRYPTION_PREFIX = "enc:v2:"
        const val LEGACY_ENCRYPTION_PREFIX = "enc:v1:"

        private const val LEGACY_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val LEGACY_IV_LENGTH_BYTES = 12
        private const val LEGACY_TAG_LENGTH_BITS = 128
    }

    override fun encrypt(plainText: String, clusterId: String): String {
        if (plainText.isBlank()) return plainText
        val ciphertext = aead().encrypt(
            plainText.toByteArray(Charsets.UTF_8),
            clusterId.toByteArray(Charsets.UTF_8),
        )
        return ENCRYPTION_PREFIX + Base64.getEncoder().encodeToString(ciphertext)
    }

    override fun decrypt(cipherText: String, clusterId: String): String {
        val prefix = when {
            cipherText.startsWith(ENCRYPTION_PREFIX) -> ENCRYPTION_PREFIX
            cipherText.startsWith(LEGACY_ENCRYPTION_PREFIX) -> LEGACY_ENCRYPTION_PREFIX
            // Legacy plaintext record; return directly without failing
            else -> return cipherText
        }
        val payload = cipherText.removePrefix(prefix).trim()
        if (payload.isEmpty()) return ""

        val bytes = try {
            Base64.getDecoder().decode(payload.replace("\\s".toRegex(), ""))
        } catch (e: IllegalArgumentException) {
            throw KubeconfigDecryptionException("Corrupted base64 encrypted payload.", e)
        }

        val plainBytes = try {
            if (prefix == ENCRYPTION_PREFIX) {
                aead().decrypt(bytes, clusterId.toByteArray(Charsets.UTF_8))
            } else {
                decryptLegacy(bytes)
            }
        } catch (e: KubeconfigDecryptionException) {
            throw e
        } catch (e: Exception) {
            // GeneralSecurityException when the key was lost and regenerated, the payload was
            // altered or it belongs to another cluster; IOException or ProviderException when the
            // keyset or the Keystore cannot be read.
            throw KubeconfigDecryptionException(
                "Stored credentials cannot be decrypted with this device's key.",
                e,
            )
        }
        return String(plainBytes, Charsets.UTF_8)
    }

    override fun needsReEncryption(stored: String): Boolean =
        stored.isNotBlank() && !stored.startsWith(ENCRYPTION_PREFIX)

    private fun decryptLegacy(bytes: ByteArray): ByteArray {
        if (bytes.size < LEGACY_IV_LENGTH_BYTES + LEGACY_TAG_LENGTH_BITS / 8) {
            throw KubeconfigDecryptionException("Malformed ciphertext payload: insufficient length.")
        }
        val key = legacyKey()
            ?: throw KubeconfigDecryptionException("The key of these stored credentials no longer exists.")
        val cipher = Cipher.getInstance(LEGACY_TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(LEGACY_TAG_LENGTH_BITS, bytes, 0, LEGACY_IV_LENGTH_BYTES),
        )
        return cipher.doFinal(bytes, LEGACY_IV_LENGTH_BYTES, bytes.size - LEGACY_IV_LENGTH_BYTES)
    }
}
