package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAliasStore(
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
) : AliasStore {
    private val state = MutableStateFlow<List<Alias>>(emptyList())

    override suspend fun find(phrase: String): OperationResult<Alias?> =
        if (failReads) OperationResult.Failure(OperationError.UnknownError("io"))
        else OperationResult.Success(state.value.firstOrNull { it.phrase == phrase })

    override suspend fun upsert(alias: Alias): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { it.phrase == alias.phrase } + alias
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(phrase: String): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { it.phrase == phrase }
        return OperationResult.Success(Unit)
    }

    override fun observeAll(): Flow<List<Alias>> = state
}
