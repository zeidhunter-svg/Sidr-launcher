package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanStore
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory [LearnedPlanStore], rows keyed by [CommandForm.key]. [reads] counts every [find] and [all]; with
 * [failReads] those return a Failure and [observeAll]'s flow throws on collection. No cap: that is the Room store's.
 */
class FakeLearnedPlanStore(var failReads: Boolean = false) : LearnedPlanStore {
    private val state = MutableStateFlow<Map<String, LearnedPlan>>(emptyMap())

    val rows: Map<String, LearnedPlan> get() = state.value
    var reads: Int = 0
        private set

    override suspend fun find(form: CommandForm): OperationResult<LearnedPlan?> {
        reads++
        return if (failReads) OperationResult.Failure(OperationError.UnknownError("io"))
        else OperationResult.Success(state.value[form.key])
    }

    override suspend fun all(): OperationResult<List<LearnedPlan>> {
        reads++
        return if (failReads) OperationResult.Failure(OperationError.UnknownError("io"))
        else OperationResult.Success(state.value.values.toList())
    }

    override suspend fun upsert(plan: LearnedPlan): OperationResult<Unit> {
        state.value = state.value + (plan.shape.form.key to plan)
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(form: CommandForm): OperationResult<Unit> {
        state.value = state.value - form.key
        return OperationResult.Success(Unit)
    }

    override fun observeAll(): Flow<List<LearnedPlan>> = state.map { rows ->
        if (failReads) throw IllegalStateException("io")
        rows.values.toList()
    }
}
