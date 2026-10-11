package dev.hridaya.kubenexus.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.hridaya.kubenexus.core.security.AesGcmKubeconfigEncryptor
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * Android Keystore-backed implementation of [KubeconfigEncryptor].
 *
 * Persists the master AES-256 key inside hardware-backed Android KeyStore (TEE / StrongBox when available).
 */
class AndroidKeystoreKubeconfigEncryptor @Inject constructor() : AesGcmKubeconfigEncryptor(
    keyProvider = {
        getOrCreateSecretKey(DEFAULT_KEY_ALIAS, ANDROID_KEYSTORE_PROVIDER)
    },
) {

    companion object {
        const val DEFAULT_KEY_ALIAS = "kubenexus_kubeconfig_master_key"
        const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"

        @Synchronized
        fun getOrCreateSecretKey(
            alias: String = DEFAULT_KEY_ALIAS,
            provider: String = ANDROID_KEYSTORE_PROVIDER
        ): SecretKey {
            val keyStore = KeyStore.getInstance(provider).apply {
                load(null)
            }

            if (keyStore.containsAlias(alias)) {
                val entry = keyStore.getEntry(alias, null) as? KeyStore.SecretKeyEntry
                if (entry != null) {
                    return entry.secretKey
                }
                val key = keyStore.getKey(alias, null) as? SecretKey
                if (key != null) {
                    return key
                }
            }

            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, provider)

            // Attempt StrongBox dedicated hardware security module on supported devices (API 28+)
            try {
                val strongBoxSpec = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .setIsStrongBoxBacked(true)
                    .build()

                keyGenerator.init(strongBoxSpec)
                return keyGenerator.generateKey()
            } catch (t: Throwable) {
                // StrongBox hardware not available on this device, fall back to standard TEE
            }

            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()

            keyGenerator.init(spec)
            return keyGenerator.generateKey()
        }
    }
}
