package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class DeleteAliasUseCase(private val store: AliasStore) {
    suspend fun delete(phrase: String): OperationResult<Unit> =
        try {
            store.delete(phrase)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(OperationError.UnknownError(e.message ?: "delete alias failed"))
        }
}
