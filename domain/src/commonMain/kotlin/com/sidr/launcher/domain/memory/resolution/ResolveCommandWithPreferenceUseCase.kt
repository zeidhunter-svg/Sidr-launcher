package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.text.TextFolding

/** The underlying rule-first router, wrapped as a seam so the decorator is trivially testable. */
fun interface CommandRouteStep { suspend fun route(rawInput: String): CommandOutcome }

/** Result of preference-aware resolution. `AutoLaunch` is a directive — NOT an executed outcome. */
sealed interface ResolvedCommand {
    /** Render [outcome] as-is. [learningToken] is present only for an app-ambiguity list (for recording). */
    data class Outcome(val outcome: CommandOutcome, val learningToken: ResolutionLearningToken?) : ResolvedCommand
    /** Auto-resolve directive: the VM launches [target] via launchApp; on failure it renders [fallback]. */
    data class AutoLaunch(val target: ResolvedTarget.App, val fallback: CommandOutcome) : ResolvedCommand
}

/**
 * FastPath's learned launch choices (S2-1). It reads and arms only a [CommandOutcome.NeedsConfirmation] —
 * several apps for one name, or (A4′ phase 3b, Task 7) one or more apps offered for a word FORM of their
 * labels, `confirm`; both are learned alike (B-3): the same store, the same policy, streak ≥ 3
 * auto-resolves, so a form confirmed three times launches without asking. A [ResolvedCommand.AutoLaunch]'s
 * fallback, and every reordered list, keep the outcome's `query` and `confirm`.
 *
 * The key is [LearnedChoiceKeys.launch] of the outcome's `query` (B-4) under [folding] — the device's one
 * `TextFolding` in production (`MemoryProvidesModule`), the domain's [PlainTextFolding] by default.
 */
class ResolveCommandWithPreferenceUseCase(
    private val route: CommandRouteStep,
    private val store: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
    private val catalog: ActionCatalog,
    private val folding: TextFolding = PlainTextFolding,
) {
    suspend fun resolve(rawInput: String): ResolvedCommand {
        val outcome = route.route(rawInput)
        if (outcome !is CommandOutcome.NeedsConfirmation) return ResolvedCommand.Outcome(outcome, null)

        val candidates = CandidateSet(outcome.candidates.map { ResolvedTarget.App(it.packageName) })
        // A4′ phase 3b, Task 7 (B-4): keyed on the app NAME this outcome carries (FastPath's
        // `displayNameQuery`), not on the command it came in — «открой tessar» keys `tessar`, the key the
        // bridge reads for the mention «tessar». `launch` normalises the name, strips English launch
        // fillers from it (`LaunchSlotExtractor`, so «my bank app» still keys `bank`) and folds it. Rows
        // learned under a `ru`/`tr` whole phrase no longer match: the user is asked again — no migration.
        val key = LearnedChoiceKeys.launch(outcome.query, folding)
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
        return outcome.copy(candidates = outcome.candidates.sortedByDescending { it.packageName == pkg })
    }
}
