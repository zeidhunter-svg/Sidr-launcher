package com.sidr.launcher.domain.ai.local

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * Port: observable model availability state + write-side for the WorkManager download worker.
 *
 * Reads are [Flow]-based (collectors react to download completion without polling).
 * Writes return [OperationResult] and never throw — codebase-wide convention.
 * Implementation lives in :data:repository (Block Q).
 */
interface ModelAvailabilityRepository {
    fun availability(modelId: ModelId): Flow<ModelAvailability>
    suspend fun markAvailable(modelId: ModelId): OperationResult<Unit>
    suspend fun markMissing(modelId: ModelId): OperationResult<Unit>
}
