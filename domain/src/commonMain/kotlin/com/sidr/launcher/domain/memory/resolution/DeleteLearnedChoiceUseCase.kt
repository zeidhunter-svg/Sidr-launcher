package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationResult

class DeleteLearnedChoiceUseCase(private val store: ResolutionPreferenceStore) {
    suspend fun delete(key: CapabilityKey, context: ResolutionContext): OperationResult<Unit> = store.delete(key, context)
}
