package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.requiresConsent

class EvaluateLearnedChoiceDisplayStateUseCase(
    private val autoResolveStreakThreshold: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
) {
    /**
     * @param currentCandidates the reconstructed live candidate set, or null if it could not be
     *   reliably reconstructed on the management screen (→ degrade Auto to AutoReady).
     */
    fun evaluate(
        preference: ResolutionPreference,
        currentCandidates: CandidateSet?,
        risk: ActionRiskLevel,
        targetInstalled: Boolean,
    ): LearnedChoiceDisplayState {
        if (!targetInstalled) return LearnedChoiceDisplayState.Unavailable
        val confident = preference.evidence.streak >= autoResolveStreakThreshold
        if (!confident) return LearnedChoiceDisplayState.Learning(preference.evidence.streak, autoResolveStreakThreshold)
        if (requiresConsent(risk)) return LearnedChoiceDisplayState.NeedsReconfirm
        if (currentCandidates == null) return LearnedChoiceDisplayState.AutoReady
        val setConsistent = fingerprintOf(currentCandidates) == preference.learnedInSetFingerprint &&
            preference.preferredTarget in currentCandidates.targets
        return if (setConsistent) LearnedChoiceDisplayState.Auto else LearnedChoiceDisplayState.NeedsReconfirm
    }
}
