package com.sidr.launcher.domain.memory.alias

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

interface AliasStore {
    suspend fun find(phrase: String): OperationResult<Alias?>
    suspend fun upsert(alias: Alias): OperationResult<Unit>
    suspend fun delete(phrase: String): OperationResult<Unit>
    fun observeAll(): Flow<List<Alias>>
}
