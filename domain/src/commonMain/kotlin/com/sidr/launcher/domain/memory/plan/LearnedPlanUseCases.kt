package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

/**
 * Records one confirmed reading (B-8), as the learned-choice store records a choice: no row → streak 1; the same
 * template → streak + 1; another template → streak 1 with the new template, the confirmation still counted.
 * A failed read is returned, not treated as «no row» — that would overwrite a pinned plan's evidence.
 * Never throws (rethrows [CancellationException]). The cap is the store's ([MAX_LEARNED_PLANS], B-9).
 */
class RecordLearnedPlanUseCase(private val store: LearnedPlanStore) {
    suspend fun record(
        shape: LearnedPlanShape,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): OperationResult<Unit> {
        try {
            val existing = when (val found = store.find(shape.form)) {
                is OperationResult.Success -> found.value
                is OperationResult.Failure -> return found
            }
            val plan = when {
                existing == null -> LearnedPlan(shape, streak = 1, confirmations = 1, confirmedAtEpochMs = nowEpochMs)
                existing.shape.template == shape.template -> existing.copy(
                    shape = shape,
                    streak = existing.streak + 1,
                    confirmations = existing.confirmations + 1,
                    confirmedAtEpochMs = nowEpochMs,
                )
                else -> LearnedPlan(shape, streak = 1, confirmations = existing.confirmations + 1, confirmedAtEpochMs = nowEpochMs)
            }
            return store.upsert(plan)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return OperationResult.Failure(OperationError.UnknownError(t.message ?: "record failed"))
        }
    }
}

class ForgetLearnedPlanUseCase(private val store: LearnedPlanStore) {
    suspend fun forget(form: CommandForm): OperationResult<Unit> = try {
        store.delete(form)
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        OperationResult.Failure(OperationError.UnknownError(t.message ?: "forget failed"))
    }
}

class ObserveLearnedPlansUseCase(private val store: LearnedPlanStore) {
    fun observe(): Flow<List<LearnedPlan>> = store.observeAll()
}
