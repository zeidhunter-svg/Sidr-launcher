package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.result.OperationResult

/** The underlying rule-first router, wrapped as a seam so the decorator is trivially testable. */
fun interface CommandRouteStep { suspend fun route(rawInput: String): CommandOutcome }

/** Result of preference-aware resolution. `AutoLaunch` is a directive — NOT an executed outcome. */
sealed interface ResolvedCommand {
    /** Render [outcome] as-is. [learningToken] is present only for an app-ambiguity list (for recording). */
    data class Outcome(val outcome: CommandOutcome, val learningToken: ResolutionLearningToken?) : ResolvedCommand
    /** Auto-resolve directive: the VM launches [target] via launchApp; on failure it renders [fallback]. */
    data class AutoLaunch(val target: ResolvedTarget.App, val fallback: CommandOutcome) : ResolvedCommand
}

class ResolveCommandWithPreferenceUseCase(
    private val route: CommandRouteStep,
    private val store: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
    private val catalog: ActionCatalog,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val outcome = route.route(rawInput)
        if (outcome !is CommandOutcome.NeedsConfirmation) return ResolvedCommand.Outcome(outcome, null)

        val candidates = CandidateSet(outcome.candidates.map { ResolvedTarget.App(it.packageName) })
        // v1 slot: narrow deterministic verb/filler strip for LAUNCH_APP ambiguity only (not a parser).
        val slot = LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(rawInput))
        val key = CapabilityKey(ActionIds.LAUNCH_APP, slot)
        val token = ResolutionLearningToken(
            capabilityKey = key, context = ResolutionContext.None, candidateSet = candidates,
            fingerprint = fingerprintOf(candidates), isAppAmbiguityFlow = true,
        )
        val risk = catalog.descriptor(ActionIds.LAUNCH_APP)?.risk ?: ActionRiskLevel.CONFIRM // fail-safe
        val pref = (store.find(key, ResolutionContext.None) as? OperationResult.Success)?.value

        return when (val decision = policy.decide(pref, candidates, risk)) {
            ResolutionDecision.NoPreference -> ResolvedCommand.Outcome(outcome, token)
            is ResolutionDecision.Stale -> {
                store.delete(key, ResolutionContext.None) // best-effort prune; result ignored
                ResolvedCommand.Outcome(outcome, token)
            }
            is ResolutionDecision.RankFirst -> ResolvedCommand.Outcome(reorder(outcome, decision.target), token)
            is ResolutionDecision.AutoResolve -> when (val t = decision.target) {
                is ResolvedTarget.App -> ResolvedCommand.AutoLaunch(t, reorder(outcome, t))
            }
        }
    }

    private fun reorder(outcome: CommandOutcome.NeedsConfirmation, first: ResolvedTarget): CommandOutcome.NeedsConfirmation {
        val pkg = first.appPackageOrNull()
        return CommandOutcome.NeedsConfirmation(outcome.candidates.sortedByDescending { it.packageName == pkg })
    }
}
