package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * The learned-plan cache's store (spec §8): one row per [CommandForm]. The cap ([MAX_LEARNED_PLANS]) and the
 * eviction order (B-9) are the implementation's, inside [upsert]; the use cases do not know them.
 */
interface LearnedPlanStore {
    suspend fun find(form: CommandForm): OperationResult<LearnedPlan?>
    suspend fun all(): OperationResult<List<LearnedPlan>>
    suspend fun upsert(plan: LearnedPlan): OperationResult<Unit>
    suspend fun delete(form: CommandForm): OperationResult<Unit>
    fun observeAll(): Flow<List<LearnedPlan>>
}
