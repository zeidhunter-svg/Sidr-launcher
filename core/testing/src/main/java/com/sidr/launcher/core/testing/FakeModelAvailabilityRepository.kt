package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.ai.local.ModelAvailability
import com.sidr.launcher.domain.ai.local.ModelAvailabilityRepository
import com.sidr.launcher.domain.ai.local.ModelId
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [ModelAvailabilityRepository] backed by a per-[ModelId] [MutableStateFlow].
 *
 * Set initial availability via [setAvailability]. Calls to [markAvailable]/[markMissing] update
 * the flow unless [errorToReturn] is set (to simulate write failures). Not wired into any Hilt
 * graph — use directly in unit tests.
 */
class FakeModelAvailabilityRepository : ModelAvailabilityRepository {

    private val states = mutableMapOf<String, MutableStateFlow<ModelAvailability>>()

    var errorToReturn: OperationError? = null

    val markAvailableCalls = mutableListOf<ModelId>()
    val markMissingCalls = mutableListOf<ModelId>()

    private fun stateFor(modelId: ModelId): MutableStateFlow<ModelAvailability> =
        states.getOrPut(modelId.value) { MutableStateFlow(ModelAvailability.Unverified) }

    fun setAvailability(modelId: ModelId, availability: ModelAvailability) {
        stateFor(modelId).value = availability
    }

    override fun availability(modelId: ModelId): Flow<ModelAvailability> = stateFor(modelId)

    override suspend fun markAvailable(modelId: ModelId): OperationResult<Unit> {
        markAvailableCalls += modelId
        val err = errorToReturn
        return if (err != null) {
            OperationResult.Failure(err)
        } else {
            stateFor(modelId).value = ModelAvailability.Available
            OperationResult.Success(Unit)
        }
    }

    override suspend fun markMissing(modelId: ModelId): OperationResult<Unit> {
        markMissingCalls += modelId
        val err = errorToReturn
        return if (err != null) {
            OperationResult.Failure(err)
        } else {
            stateFor(modelId).value = ModelAvailability.Missing
            OperationResult.Success(Unit)
        }
    }

    fun reset() {
        states.clear()
        errorToReturn = null
        markAvailableCalls.clear()
        markMissingCalls.clear()
    }
}
