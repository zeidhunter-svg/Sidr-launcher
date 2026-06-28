package com.sidr.launcher.data.ailocal

import com.sidr.launcher.data.ailocal.provision.Sha256Verifier
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * On-disk model store (Block Q) — the implementation of P's [LocalModelFiles] seam.
 *
 * Hard invariant: **no unverified file is ever exposed.** A download lands in a per-model
 * *quarantine* path; it is promoted into the *ready* path only after its SHA-256 matches the pinned
 * hash, via an atomic rename. [modelFile] returns the ready file, so the classifier can only ever
 * load a hash-verified artifact. Verify-fail deletes the quarantine file and leaves the ready path
 * untouched.
 *
 * Storage is **app-internal only**: [rootDir] is `context.filesDir/models` (or `noBackupFilesDir`)
 * in production and a temp dir in tests — never external/shared. Constructed with a [rootDir] +
 * [vocabOpener] seam (no `Context` reference) so the whole store is JVM-testable.
 *
 * Vocab (§5.D decision): **bundled as an asset**, not downloaded — it is small (~200 KB), static,
 * and version-locked to [WordPieceTokenizer]; bundling means no second download, no second hash, and
 * it cannot drift from the tokenizer. [vocabOpener] resolves it (from `assets` in production, a fake
 * stream in tests) and returns null when absent, so a missing vocab degrades to the rule path.
 *
 * Not an `ai.onnxruntime` type.
 */
class ModelStore(
    private val rootDir: File,
    private val vocabOpener: (ModelId) -> InputStream?,
    private val verifier: Sha256Verifier = Sha256Verifier(),
) : LocalModelFiles {

    private val readyDir: File get() = File(rootDir, READY_DIR)
    private val quarantineDir: File get() = File(rootDir, QUARANTINE_DIR)

    /** The ready (verified) `.onnx` path for [modelId]. May not exist yet. */
    fun readyFile(modelId: ModelId): File = File(readyDir, "${modelId.value}.onnx")

    /** The quarantine (pre-verification) path for [modelId]; parent dirs are created. */
    fun quarantineFile(modelId: ModelId): File {
        quarantineDir.mkdirs()
        return File(quarantineDir, "${modelId.value}.onnx.tmp")
    }

    /** Removes any leftover quarantine file (e.g. after a failed/cancelled download). Safe if absent. */
    fun deleteQuarantine(modelId: ModelId) {
        quarantineFile(modelId).delete()
    }

    /**
     * Verifies the quarantine file for [modelId] against [expectedSha256] and, on match, atomically
     * promotes it into the ready path. On mismatch (or a blank/unpinned hash) the quarantine file is
     * deleted and the ready path is left untouched — nothing unverified is ever exposed.
     */
    fun promote(modelId: ModelId, expectedSha256: String): OperationResult<Unit> {
        val quarantine = quarantineFile(modelId)
        if (!verifier.verify(quarantine, expectedSha256)) {
            quarantine.delete()
            return OperationResult.Failure(OperationError.UnknownError(reason = "model_verification_failed"))
        }
        readyDir.mkdirs()
        val ready = readyFile(modelId)
        return try {
            atomicMove(quarantine, ready)
            OperationResult.Success(Unit)
        } catch (e: Exception) {
            // Promotion failed after a *passing* verify — do not leave a half-state: drop the
            // quarantine file; the ready path was never written by a non-atomic step.
            quarantine.delete()
            OperationResult.Failure(OperationError.UnknownError(reason = "model_promote_failed"))
        }
    }

    private fun atomicMove(source: File, target: File) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            // Same-filesystem fallback (atomic move unsupported on some FS): a plain replace. Both
            // source and target live under rootDir, so this is the same volume in practice.
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    // ── LocalModelFiles ─────────────────────────────────────────────────────────
    override fun modelFile(modelId: ModelId): File? = readyFile(modelId).takeIf { it.isFile }

    override fun vocabStream(modelId: ModelId): InputStream? = vocabOpener(modelId)

    private companion object {
        const val READY_DIR = "models"
        const val QUARANTINE_DIR = "models/.quarantine"
    }
}
