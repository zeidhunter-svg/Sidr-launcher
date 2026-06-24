package com.sidr.launcher.data.repository.security

/**
 * Ciphertext plus the GCM IV it was produced with. A **data-layer crypto detail** — never a domain
 * type, never persisted in the clear with identifying context. Equality is content-based so tests can
 * compare blobs.
 */
data class EncryptedBlob(val iv: ByteArray, val ciphertext: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedBlob) return false
        return iv.contentEquals(other.iv) && ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + ciphertext.contentHashCode()
}

/**
 * The only crypto seam (Fork 7). [SecureSecretStoreImpl] orchestrates this + a DataStore and is fully
 * JVM-testable with a fake cipher; the single Keystore-touching implementation ([KeystoreSecretCipher])
 * is exercised in `androidTest`. This is a `:data:repository`-internal detail, **not** a domain type.
 *
 * Failure contract:
 * - [encrypt] may throw on a hard crypto/key-generation failure — the store maps that to `Failure`.
 * - [decrypt] returns `null` for an *unusable* secret (key invalidated, missing, or corrupt blob) so
 *   the store can clear the entry and signal "re-enter"; it must NOT throw on those expected paths.
 */
interface SecretCipher {
    fun encrypt(plaintext: String): EncryptedBlob
    fun decrypt(blob: EncryptedBlob): String?
}
