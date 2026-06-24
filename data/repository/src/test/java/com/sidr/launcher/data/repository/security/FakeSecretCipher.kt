package com.sidr.launcher.data.repository.security

/**
 * JVM fake [SecretCipher] for testing [SecureSecretStoreImpl] without the Android Keystore.
 *
 * Lives in `:data:repository` test sources (NOT `:core:testing`): `SecretCipher` is a
 * `:data:repository`-internal type and `:core:testing` is a JVM-only module that cannot depend on the
 * Android `:data:repository`.
 *
 * "Encryption" is a trivial reversible transform (a byte XOR + UTF-8) — enough to prove the store's
 * encode/persist/decode/orchestration logic and key isolation. Behaviour is configurable:
 * - [failEncrypt]: [encrypt] throws (drives the `put → Failure` path).
 * - [invalidate]: [decrypt] returns `null` for everything (drives the key-invalidation `get → null +
 *   clear` path).
 */
class FakeSecretCipher : SecretCipher {

    var failEncrypt: Boolean = false
    var invalidate: Boolean = false

    override fun encrypt(plaintext: String): EncryptedBlob {
        if (failEncrypt) throw IllegalStateException("fake encrypt failure")
        val iv = byteArrayOf(MASK)
        val ciphertext = plaintext.toByteArray(Charsets.UTF_8).map { (it.toInt() xor MASK.toInt()).toByte() }.toByteArray()
        return EncryptedBlob(iv = iv, ciphertext = ciphertext)
    }

    override fun decrypt(blob: EncryptedBlob): String? {
        if (invalidate) return null
        val mask = blob.iv.firstOrNull() ?: return null
        val bytes = blob.ciphertext.map { (it.toInt() xor mask.toInt()).toByte() }.toByteArray()
        return String(bytes, Charsets.UTF_8)
    }

    private companion object {
        const val MASK: Byte = 0x5A
    }
}
