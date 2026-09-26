package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.memory.resolution.*
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow

class FakeResolutionPreferenceStore(
    var failReads: Boolean = false,
    var failWrites: Boolean = false,
) : ResolutionPreferenceStore {
    private val state = MutableStateFlow<List<ResolutionPreference>>(emptyList())
    private fun keyOf(p: ResolutionPreference) = p.capabilityKey to p.context

    override suspend fun find(key: CapabilityKey, context: ResolutionContext): OperationResult<ResolutionPreference?> =
        if (failReads) OperationResult.Failure(OperationError.UnknownError("io"))
        else OperationResult.Success(state.value.firstOrNull { it.capabilityKey == key && it.context == context })

    override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { keyOf(it) == keyOf(preference) } + preference
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> {
        if (failWrites) return OperationResult.Failure(OperationError.UnknownError("io"))
        state.value = state.value.filterNot { it.capabilityKey == key && it.context == context }
        return OperationResult.Success(Unit)
    }

    override fun observeAll(): Flow<List<ResolutionPreference>> = state
}
