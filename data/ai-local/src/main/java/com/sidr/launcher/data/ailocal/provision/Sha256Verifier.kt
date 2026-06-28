package com.sidr.launcher.data.ailocal.provision

import java.io.File
import java.security.MessageDigest

/**
 * Streaming SHA-256 over a file, plus a constant-form [verify] against a pinned hex digest.
 *
 * Pure JVM (stdlib `MessageDigest` only — no Android, no `ai.onnxruntime`), so the integrity gate
 * is unit-testable over a temp dir with no `Context` and no real model. This is the single guard
 * standing between a downloaded byte stream and the "ready" path: a file is promoted **only** when
 * its digest matches [ModelDownloadConfig.expectedSha256].
 */
class Sha256Verifier {

    /** Lowercase hex SHA-256 of [file], read in bounded chunks (large model files never fully buffered). */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    /**
     * True iff [file] exists and its SHA-256 equals [expectedHex] (case-insensitive). A blank
     * [expectedHex] (unpinned — OQ#2 open) always fails closed: an unverifiable file is never
     * accepted.
     */
    fun verify(file: File, expectedHex: String): Boolean {
        if (expectedHex.isBlank() || !file.isFile) return false
        return sha256Hex(file).equals(expectedHex, ignoreCase = true)
    }
}
