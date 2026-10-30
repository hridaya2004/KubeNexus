package dev.hridaya.kubenexus.data.security

import android.content.Context
import androidx.core.content.edit
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.inject.Inject

/**
 * Android Keystore-backed implementation of [TinkKubeconfigEncryptor].
 *
 * The Tink keyset lives in private SharedPreferences, encrypted by a master key held in the
 * hardware-backed Android Keystore. Both are excluded from backups: restoring the keyset
 * without its master key would leave it unreadable.
 */
class AndroidKeystoreKubeconfigEncryptor private constructor(
    keyset: Lazy<Aead>,
) : TinkKubeconfigEncryptor(
    aead = { keyset.value },
    legacyKey = ::loadLegacyKey,
) {

    // Lazy: loading the keyset reads preferences and talks to the Keystore. A failed load is
    // retried on the next use.
    @Inject
    constructor(@ApplicationContext context: Context) : this(lazy { loadAead(context) })

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val MASTER_KEY_ALIAS = "kubenexus_kubeconfig_keyset_master_key"
        private const val MASTER_KEY_URI = "android-keystore://$MASTER_KEY_ALIAS"

        /** Must match the exclusions in `data_extraction_rules.xml`. */
        private const val KEYSET_PREFS_NAME = "kubenexus_kubeconfig_keyset"
        private const val KEYSET_NAME = "kubeconfig_keyset"

        /** Key of the `enc:v1:` format, read only to migrate those records. */
        private const val LEGACY_KEY_ALIAS = "kubenexus_kubeconfig_master_key"

        private fun loadAead(context: Context): Aead {
            AeadConfig.register()
            val prefs = context.getSharedPreferences(KEYSET_PREFS_NAME, Context.MODE_PRIVATE)

            // The Keystore lost the master key, so the keyset and everything it encrypted is
            // unreadable for good. Start over so new clusters can still be added.
            if (prefs.contains(KEYSET_NAME) && !keyStore().containsAlias(MASTER_KEY_ALIAS)) {
                prefs.edit(commit = true) { remove(KEYSET_NAME) }
            }

            val manager = AndroidKeysetManager.Builder()
                .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS_NAME)
                .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
                .withMasterKeyUri(MASTER_KEY_URI)
                .build()

            // Tink falls back to storing the keyset in plain text when the Keystore fails.
            // Credentials must not be protected by a key stored right next to them.
            if (!manager.isUsingKeystore) {
                prefs.edit(commit = true) { remove(KEYSET_NAME) }
                throw GeneralSecurityException("Android Keystore is unavailable.")
            }

            return manager.keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
        }

        private fun loadLegacyKey(): SecretKey? =
            keyStore().getKey(LEGACY_KEY_ALIAS, null) as? SecretKey

        private fun keyStore(): KeyStore =
            KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
    }
}
