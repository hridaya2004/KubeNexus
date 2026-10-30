package dev.hridaya.kubenexus.data.testing

import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import dev.hridaya.kubenexus.data.security.TinkKubeconfigEncryptor
import javax.crypto.SecretKey

/** A [TinkKubeconfigEncryptor] with a fresh in-memory keyset in place of the Keystore. */
fun testKubeconfigEncryptor(legacyKey: SecretKey? = null): TinkKubeconfigEncryptor {
    val aead = newTestAead()
    return TinkKubeconfigEncryptor(aead = { aead }, legacyKey = { legacyKey })
}

fun newTestAead(): Aead {
    AeadConfig.register()
    return KeysetHandle.generateNew(PredefinedAeadParameters.AES256_GCM)
        .getPrimitive(RegistryConfiguration.get(), Aead::class.java)
}
