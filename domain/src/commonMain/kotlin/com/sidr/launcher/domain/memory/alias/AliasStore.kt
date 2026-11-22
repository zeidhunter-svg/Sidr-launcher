package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface AliasStore {
    suspend fun find(phrase: String): OperationResult<Alias?>
    suspend fun upsert(alias: Alias): OperationResult<Unit>
    suspend fun delete(phrase: String): OperationResult<Unit>
    fun observeAll(): Flow<List<Alias>>

    companion object {
        /**
         * No aliases (A4′ acceptance tail, task 3): [find] finds nothing, [observeAll] is empty, and a write is
         * refused rather than silently dropped. The default of the two readers that consult aliases since task 3 —
         * FastPath's `IntentActionResolver` and the route's step 1a — so every construction site that predates them
         * stays as it was (precedent: `LearnedPlans.NONE`, B-12). The production providers pass the device's store.
         */
        val NONE: AliasStore = object : AliasStore {
            override suspend fun find(phrase: String): OperationResult<Alias?> = OperationResult.Success(null)
            override suspend fun upsert(alias: Alias): OperationResult<Unit> =
                OperationResult.Failure(OperationError.UnknownError(reason = "alias_store_none"))
            override suspend fun delete(phrase: String): OperationResult<Unit> =
                OperationResult.Failure(OperationError.UnknownError(reason = "alias_store_none"))
            override fun observeAll(): Flow<List<Alias>> = flowOf(emptyList())
        }
    }
}
