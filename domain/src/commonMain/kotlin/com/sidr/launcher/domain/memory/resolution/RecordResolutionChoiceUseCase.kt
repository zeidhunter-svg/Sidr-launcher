package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException

class RecordResolutionChoiceUseCase(private val store: ResolutionPreferenceStore) {
    /**
     * Records an explicit candidate choice. Deterministic evidence update (create / reinforce /
     * hard-switch). Returns [OperationResult] per the global contract (the VM may ignore it in
     * fire-and-forget). Never throws (rethrows CancellationException); an empty/over-length query is a
     * no-op that returns [OperationResult.Success].
     */
    suspend fun record(
        key: CapabilityKey,
        context: ResolutionContext,
        chosen: ResolvedTarget,
        candidates: CandidateSet,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): OperationResult<Unit> {
        if (key.query.isBlank() || key.query.length > MAX_QUERY_LENGTH) return OperationResult.Success(Unit)
        try {
            val existing = (store.find(key, context) as? OperationResult.Success)?.value
            val fp = fingerprintOf(candidates)
            val evidence = when {
                existing == null -> PreferenceEvidence(streak = 1, totalChoices = 1, lastChosenAtEpochMs = nowEpochMs)
                existing.preferredTarget == chosen -> existing.evidence.copy(
                    streak = existing.evidence.streak + 1,
                    totalChoices = existing.evidence.totalChoices + 1,
                    lastChosenAtEpochMs = nowEpochMs,
                )
                else -> PreferenceEvidence(streak = 1, totalChoices = existing.evidence.totalChoices + 1, lastChosenAtEpochMs = nowEpochMs)
            }
            return store.upsert(
                ResolutionPreference(
                    capabilityKey = key, context = context, preferredTarget = chosen,
                    evidence = evidence, learnedInSetFingerprint = fp,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            return OperationResult.Failure(OperationError.UnknownError(t.message ?: "record failed"))
        }
    }
}
