package dev.hridaya.kubenexus.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64 as AndroidBase64
import java.util.Base64 as JavaBase64

/**
 * AES-256-GCM implementation of [KubeconfigEncryptor].
 *
 * Formats encrypted output as `enc:v1:<base64(12_byte_iv + ciphertext_with_tag)>`.
 * Provides authenticated encryption with Associated Data (AEAD) to protect confidentiality
 * and prevent tampering.
 */
open class AesGcmKubeconfigEncryptor(private val keyProvider: () -> SecretKey) :
    KubeconfigEncryptor {

    constructor(secretKey: SecretKey) : this({ secretKey })

    companion object {
        const val ENCRYPTION_PREFIX = "enc:v1:"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MIN_PAYLOAD_BYTES = GCM_IV_LENGTH_BYTES + (GCM_TAG_LENGTH_BITS / 8)

        /**
         * Utility to generate a new 256-bit AES secret key in memory (e.g. for testing).
         */
        fun generateKey(): SecretKey {
            val keyGen = KeyGenerator.getInstance("AES")
            keyGen.init(256, SecureRandom())
            return keyGen.generateKey()
        }

        private fun encodeBase64(bytes: ByteArray): String {
            return try {
                JavaBase64.getEncoder().encodeToString(bytes)
            } catch (_: Throwable) {
                AndroidBase64.encodeToString(bytes, AndroidBase64.NO_WRAP)
            }
        }

        private fun decodeBase64(input: String): ByteArray {
            val clean = input.replace("\\s".toRegex(), "")
            return try {
                JavaBase64.getDecoder().decode(clean)
            } catch (_: Throwable) {
                AndroidBase64.decode(clean, AndroidBase64.DEFAULT)
            }
        }
    }

    override fun isEncrypted(text: String): Boolean {
        return text.startsWith(ENCRYPTION_PREFIX)
    }

    override fun encrypt(plainText: String): String {
        if (isEncrypted(plainText)) {
            return plainText
        }
        if (plainText.isBlank()) {
            return plainText
        }

        val secretKey = keyProvider()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val iv = cipher.iv ?: ByteArray(GCM_IV_LENGTH_BYTES).also {
            SecureRandom().nextBytes(it)
        }

        val plainBytes = plainText.toByteArray(Charsets.UTF_8)
        val encryptedBytes = cipher.doFinal(plainBytes)

        val combined = ByteArray(iv.size + encryptedBytes.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

        return ENCRYPTION_PREFIX + encodeBase64(combined)
    }

    override fun decrypt(cipherText: String): String {
        if (!isEncrypted(cipherText)) {
            // Legacy plaintext record; return directly without failing
            return cipherText
        }

        val base64Payload = cipherText.removePrefix(ENCRYPTION_PREFIX).trim()
        if (base64Payload.isEmpty()) {
            return ""
        }

        val combinedBytes = try {
            decodeBase64(base64Payload)
        } catch (e: Exception) {
            throw KubeconfigDecryptionException("Corrupted base64 encrypted payload.", e)
        }

        if (combinedBytes.size < MIN_PAYLOAD_BYTES) {
            throw KubeconfigDecryptionException("Malformed ciphertext payload: insufficient length.")
        }

        val iv = combinedBytes.copyOfRange(0, GCM_IV_LENGTH_BYTES)
        val encryptedData = combinedBytes.copyOfRange(GCM_IV_LENGTH_BYTES, combinedBytes.size)

        return try {
            val secretKey = keyProvider()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            String(cipher.doFinal(encryptedData), Charsets.UTF_8)
        } catch (e: GeneralSecurityException) {
            // AEADBadTagException when the key was replaced (the Keystore entry was lost and
            // regenerated) or the payload was altered.
            throw KubeconfigDecryptionException("Stored credentials cannot be decrypted with this device's key.", e)
        }
    }
}
