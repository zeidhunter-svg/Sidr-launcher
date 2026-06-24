package com.sidr.launcher.data.repository.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject

/**
 * The single Keystore-touching class (Fork 7). Encrypts secret values with an **AES-256-GCM** key
 * resident in the **Android Keystore** (`AndroidKeyStore`), so the key material never leaves the
 * secure hardware/TEE and is not extractable from app-readable storage. Only the resulting ciphertext
 * + IV ([EncryptedBlob]) are persisted (by [SecureSecretStoreImpl]).
 *
 * Threat model (BYOK, scoped interim): resists casual extraction of the user's *own* key on a
 * non-rooted device. It does **not** claim to defend a rooted/compromised device. A backend proxy can
 * later replace the whole [SecureSecretStore] behind the same domain port.
 *
 * - `setUserAuthenticationRequired(false)`: a launcher assistant cannot prompt for lockscreen/biometric
 *   on every secret read. The key is extraction-resistant but app-readable without user auth — correct
 *   for this threat model, and it sidesteps most key-invalidation paths.
 * - StrongBox is attempted and falls back gracefully where unavailable (common on API 28 devices).
 * - This class is verified on-device (`SecretStoreInstrumentedTest`); Robolectric/JVM cannot fake the
 *   Keystore, hence the cipher seam.
 */
class KeystoreSecretCipher @Inject constructor() : SecretCipher {

    override fun encrypt(plaintext: String): EncryptedBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        // GCM generates a fresh random IV on init; persist it alongside the ciphertext.
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedBlob(iv = iv, ciphertext = ciphertext)
    }

    override fun decrypt(blob: EncryptedBlob): String? {
        val key = loadKey() ?: return null // no key (e.g. cleared/never created) → unusable secret
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, blob.iv))
            String(cipher.doFinal(blob.ciphertext), Charsets.UTF_8)
        } catch (e: KeyPermanentlyInvalidatedException) {
            // Lockscreen/biometric change invalidated the key → signal "re-enter".
            null
        } catch (e: GeneralSecurityException) {
            // AEADBadTagException / bad IV / corrupt blob → unusable.
            null
        }
    }

    private fun loadKey(): SecretKey? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun getOrCreateKey(): SecretKey = loadKey() ?: generateKey()

    private fun generateKey(): SecretKey {
        return try {
            buildKey(strongBox = true)
        } catch (e: StrongBoxUnavailableException) {
            // Many API-28 devices lack a StrongBox — regenerate without it.
            buildKey(strongBox = false)
        }
    }

    private fun buildKey(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(AES_KEY_BITS)
            .setUserAuthenticationRequired(false)
            .apply { if (strongBox) setIsStrongBoxBacked(true) }
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "sidr_secret_aead_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
    }
}
