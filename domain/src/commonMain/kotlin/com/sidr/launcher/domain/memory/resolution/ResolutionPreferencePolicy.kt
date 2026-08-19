package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionRiskLevel

interface ResolutionPreferencePolicy {
    fun decide(
        preference: ResolutionPreference?,
        candidates: CandidateSet,
        risk: ActionRiskLevel,
    ): ResolutionDecision
}

class DefaultResolutionPreferencePolicy(
    private val autoResolveStreakThreshold: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
) : ResolutionPreferencePolicy {

    /** Single confidence evolution point. v1: streak threshold. */
    private fun strength(e: PreferenceEvidence): PreferenceStrength =
        if (e.streak >= autoResolveStreakThreshold) PreferenceStrength.CONFIDENT else PreferenceStrength.WEAK

    override fun decide(
        preference: ResolutionPreference?,
        candidates: CandidateSet,
        risk: ActionRiskLevel,
    ): ResolutionDecision {
        if (preference == null) return ResolutionDecision.NoPreference
        if (preference.preferredTarget !in candidates.targets) return ResolutionDecision.Stale(preference)
        val eligible = strength(preference.evidence) == PreferenceStrength.CONFIDENT &&
            risk == ActionRiskLevel.SAFE &&
            fingerprintOf(candidates) == preference.learnedInSetFingerprint
        return if (eligible) ResolutionDecision.AutoResolve(preference.preferredTarget)
        else ResolutionDecision.RankFirst(preference.preferredTarget)
    }
}
