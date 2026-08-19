package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class SaveAliasUseCase(private val store: AliasStore) {
    /**
     * Creates or updates an alias. Blank or over-length phrases are scope no-ops; all other failures
     * are mapped to category-only [OperationResult.Failure] so UI callers never receive exceptions.
     */
    suspend fun save(
        phrase: String,
        target: AliasTarget,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): OperationResult<Unit> {
        val normalized = CommandNormalizer.normalize(phrase)
        if (normalized.isBlank() || normalized.length > MAX_ALIAS_PHRASE_LENGTH) {
            return OperationResult.Success(Unit)
        }
        return try {
            store.upsert(Alias(phrase = normalized, target = target, createdAtEpochMs = nowEpochMs))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(e.message ?: "save alias failed"))
        }
    }
}
