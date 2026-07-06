package com.sidr.launcher.domain.memory.resolution

sealed interface ResolutionDecision {
    data object NoPreference : ResolutionDecision
    data class Stale(val preference: ResolutionPreference) : ResolutionDecision
    data class RankFirst(val target: ResolvedTarget) : ResolutionDecision
    data class AutoResolve(val target: ResolvedTarget) : ResolutionDecision
}

enum class PreferenceStrength { WEAK, CONFIDENT }
