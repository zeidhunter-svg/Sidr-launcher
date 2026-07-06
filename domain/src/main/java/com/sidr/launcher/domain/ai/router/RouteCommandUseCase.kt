package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import kotlinx.coroutines.flow.first

/**
 * Composes the AIL-4 LLM router **above** the unchanged rule pipeline (Fork R2 = b): rule-first, then
 * the [CommandPlanner] second and only when the rules couldn't decide. [HandleUserCommandUseCase] is
 * **not** modified — this wraps it — so the "local matching runs before any LLM call" invariant holds
 * and the proven offline path is untouched.
 *
 * Order of decision (ADR 2026-07-05 — AIL-4):
 * 1. Run the rule path (`handle`) exactly once — offline, unchanged, and the source of the recorded
 *    side effects.
 * 2. **Router off ⇒ byte-for-byte rule-only parity** — return the rule outcome without ever touching
 *    the planner.
 * 3. **Trigger (R1 = b):** consult the planner only when the rule outcome is [CommandOutcome.Unknown]
 *    or [CommandOutcome.LowConfidence]; every confident rule outcome is returned untouched.
 * 4. **Offline ⇒ rule outcome** — skip the planner entirely when there's no connectivity (the planner
 *    would only return [PlanResult.NoPlan] anyway; this avoids the wasted call). No-key / provider /
 *    parse failures are handled inside the planner impl, which returns [PlanResult.NoPlan].
 * 5. Map the plan: a [PlanResult.RoutedAction] surfaces as a **non-executing** proposal
 *    ([CommandOutcome.RoutedAction], Fork R4 — never auto-execute); a [PlanResult.Clarify] becomes a
 *    message; [PlanResult.NoPlan] keeps the original rule outcome.
 */
class RouteCommandUseCase(
    private val handleUserCommand: HandleUserCommandUseCase,
    private val planner: CommandPlanner,
    private val catalog: ActionCatalog,
    private val featureFlagRepository: FeatureFlagRepository,
    private val connectivityChecker: ConnectivityChecker,
) {

    suspend fun route(rawInput: String): CommandOutcome {
        // (1) Rule path first — unchanged, offline, runs exactly once (its recording side effect too).
        val ruleOutcome = handleUserCommand.handle(rawInput)

        // (2) Router off ⇒ exact rule-only parity.
        if (!featureFlagRepository.getFlags().first().llmRouterEnabled) return ruleOutcome

        // (3) Only natural-language / unrecognized cases reach the LLM (R1).
        if (ruleOutcome !is CommandOutcome.Unknown && ruleOutcome !is CommandOutcome.LowConfidence) {
            return ruleOutcome
        }

        // (4) Offline ⇒ keep the rule outcome; never open a socket the planner would abandon.
        if (!connectivityChecker.isOnline()) return ruleOutcome

        // (5) Consult the planner and map its structured decision; any failure ⇒ NoPlan ⇒ rule outcome.
        return when (val plan = planner.plan(rawInput.trim(), catalog)) {
            is PlanResult.RoutedAction -> CommandOutcome.RoutedAction(
                action = plan.action,
                confidence = plan.confidence,
                needsConfirmation = needsConfirmation(plan),
            )
            is PlanResult.Clarify -> CommandOutcome.Message(plan.question)
            PlanResult.NoPlan -> ruleOutcome
        }
    }

    /**
     * A router proposal never auto-executes (R4). [ActionRiskLevel.SAFE] families may render as a
     * one-tap suggestion (`false`); everything else — including any unregistered/unknown descriptor —
     * requires an explicit confirm card (`true`), the fail-safe default.
     */
    private fun needsConfirmation(plan: PlanResult.RoutedAction): Boolean =
        catalog.descriptor(plan.action.id)?.risk != ActionRiskLevel.SAFE
}
