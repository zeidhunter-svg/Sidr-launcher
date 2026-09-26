package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId

/** v1 default: number of consecutive consistent explicit choices before auto-resolve is eligible. */
const val DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD: Int = 3
/** Max stored normalized-slot length; longer queries are never persisted (privacy + sanity). */
const val MAX_QUERY_LENGTH: Int = 64
/** Safety-net cap on stored preferences; LRU-evicted by lastChosenAt when exceeded. */
const val MAX_RESOLUTION_PREFERENCES: Int = 500

/** WHAT the user is trying to do, independent of which target. v1 = (action family + normalized slot). */
data class CapabilityKey(val actionId: ActionId, val query: String)

/** WHERE/WHEN. v1 degenerate; grows (time/place) without signature churn. */
sealed interface ResolutionContext { data object None : ResolutionContext }

/** The chosen thing. v1 = app only; sealed so it generalizes without breaking ports. */
sealed interface ResolvedTarget { data class App(val packageName: String) : ResolvedTarget }

/** The offered candidates — for validation + change-awareness, not identity. */
data class CandidateSet(val targets: List<ResolvedTarget>)

@JvmInline value class CandidateSetFingerprint(val value: String)

/** Stable, type-prefixed id for a target — exhaustive `when`, no unsafe cast; grows with ResolvedTarget. */
fun targetId(target: ResolvedTarget): String = when (target) {
    is ResolvedTarget.App -> "app:${target.packageName}"
}

/** The app package for an [ResolvedTarget.App], or null for a non-app target. Exhaustive, cast-free. */
fun ResolvedTarget.appPackageOrNull(): String? = when (this) {
    is ResolvedTarget.App -> packageName
}

/** Deterministic, order-independent fingerprint of the candidate target ids. NOT anonymization. */
fun fingerprintOf(set: CandidateSet): CandidateSetFingerprint =
    CandidateSetFingerprint(set.targets.map(::targetId).sorted().joinToString("|"))

/** Raw deterministic evidence the policy interprets. */
data class PreferenceEvidence(val streak: Int, val totalChoices: Int, val lastChosenAtEpochMs: Long)

data class ResolutionPreference(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val preferredTarget: ResolvedTarget,
    val evidence: PreferenceEvidence,
    val learnedInSetFingerprint: CandidateSetFingerprint,
)

/** Opaque TRANSIENT token an ambiguous outcome carries so the VM records a choice without building a key. */
data class ResolutionLearningToken(
    val capabilityKey: CapabilityKey,
    val context: ResolutionContext,
    val candidateSet: CandidateSet,
    val fingerprint: CandidateSetFingerprint,
    val isAppAmbiguityFlow: Boolean,
)
