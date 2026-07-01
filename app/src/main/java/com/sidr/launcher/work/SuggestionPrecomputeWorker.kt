package com.sidr.launcher.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Best-effort daily refresh of the launcher suggestions cache/history.
 *
 * The heavy lifting stays inside [SuggestionEngine.refresh]. This worker only enforces the
 * background gate and intentionally fails closed: if the gate is shut or refresh fails, the current
 * cache remains in place and the next periodic run gets another chance.
 */
@HiltWorker
class SuggestionPrecomputeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val suggestionEngine: SuggestionEngine,
    private val precomputeGate: SuggestionPrecomputeGate,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!precomputeGate.allowExecution()) return Result.success()

        return when (suggestionEngine.refresh()) {
            is OperationResult.Success -> Result.success()
            is OperationResult.Failure -> Result.success()
        }
    }

    companion object {
        const val UNIQUE_NAME = "sidr_suggestion_precompute"
    }
}
