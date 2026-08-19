package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import kotlinx.coroutines.flow.first

/**
 * The deterministic gate in front of the model planner (Этап 4.0, ADR 1/4).
 *
 * **Understanding belongs to the model; execution belongs to the deterministic layer.** FastPath
 * ([HandleUserCommandUseCase]) is a latency optimization, not a filter on understanding: it answers
 * frequent exact commands without a model, and a FastPath *miss* is no longer grounds to answer
 * "Unknown command". What stays deterministic is everything on the execution side — nothing the
 * planner proposes auto-executes (Fork R4), and every reason understanding is unavailable is stated
 * honestly rather than disguised as an unrecognized command.
 *
 * Order of decision — an ordered chain of early returns, so **at most one** of the three
 * understanding-unavailable messages can be reached for a single command:
 * 1. Run FastPath (`handle`) exactly once — offline, unchanged, and the source of the recorded side
 *    effects.
 * 2. **`localOnlyMode` on ⇒ byte-for-byte FastPath parity** — the planner is never consulted and
 *    nothing leaves the device. A FastPath hit returns untouched; a miss says so plainly
 *    ([CommandMessage.UnderstandingLocalOnly]) instead of claiming the command was unknown.
 * 3. FastPath decided confidently ⇒ return its outcome untouched; the planner is not consulted.
 * 4. **No provider configured ⇒ [CommandMessage.UnderstandingNeedsProvider]**, planner never
 *    consulted. This check lives *here*, in the gate, and not in the planner implementation (Этап
 *    4.0 fork F4): with the flag inverted it is a privacy guarantee — "no provider ⇒ no outbound
 *    call" — and a guarantee may not rest on how one adapter happens to be written.
 *    [AiProviderConfigRepository] is an existing `commonMain` port, so the gate stays KMP-portable
 *    and gains no new module edge (ADR 3/4). The API key deliberately stays the planner's business:
 *    the gate decides *whether to ask*, never touches secrets.
 * 5. **Offline ⇒ [CommandMessage.UnderstandingNeedsNetwork]** — no socket the planner would abandon.
 * 6. Map the plan: a [PlanResult.RoutedAction] surfaces as a **non-executing** proposal
 *    ([CommandOutcome.RoutedAction], Fork R4 — never auto-execute); a [PlanResult.Clarify] becomes a
 *    message; [PlanResult.NoPlan] keeps the FastPath outcome.
 */
class RouteCommandUseCase(
    private val handleUserCommand: HandleUserCommandUseCase,
    private val planner: CommandPlanner,
    private val catalog: ActionCatalog,
    private val featureFlagRepository: FeatureFlagRepository,
    private val providerConfigRepository: AiProviderConfigRepository,
    private val connectivityChecker: ConnectivityChecker,
) {

    suspend fun route(rawInput: String): CommandOutcome {
        // (1) FastPath first — unchanged, offline, runs exactly once (its recording side effect too).
        val ruleOutcome = handleUserCommand.handle(rawInput)

        // (2) Local-only ⇒ exact FastPath parity; the planner is never consulted.
        if (featureFlagRepository.getFlags().first().localOnlyMode) {
            return ruleOutcome.orHonestly(CommandMessage.UnderstandingLocalOnly)
        }

        // (3) FastPath decided — a confident outcome is returned untouched.
        if (!ruleOutcome.isUndecided()) return ruleOutcome

        // (4) No provider ⇒ nothing may leave the device, and we say why (F4 — gate, not adapter).
        if (providerConfigRepository.activeConfig().first() == null) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider)
        }

        // (5) Offline ⇒ honest "needs network"; never open a socket the planner would abandon.
        if (!connectivityChecker.isOnline()) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork)
        }

        // (6) Consult the planner and map its structured decision; any failure ⇒ NoPlan ⇒ FastPath outcome.
        return when (val plan = planner.plan(rawInput.trim(), catalog)) {
            is PlanResult.RoutedAction -> CommandOutcome.RoutedAction(
                action = plan.action,
                confidence = plan.confidence,
                needsConfirmation = needsConfirmation(plan),
            )
            is PlanResult.Clarify -> CommandOutcome.Message(CommandMessage.Verbatim(plan.question))
            PlanResult.NoPlan -> ruleOutcome
        }
    }

    /**
     * FastPath could not decide — the only two states in which understanding is wanted at all.
     * [CommandOutcome.Empty] is deliberately *not* undecided: an empty submit is a hint, not a goal,
     * and must never cost a round-trip or an "understanding unavailable" line.
     */
    private fun CommandOutcome.isUndecided(): Boolean =
        this is CommandOutcome.Unknown || this is CommandOutcome.LowConfidence

    /** Keeps a decided FastPath outcome byte-for-byte; replaces only the "unknown command" claim. */
    private fun CommandOutcome.orHonestly(message: CommandMessage): CommandOutcome =
        if (isUndecided()) CommandOutcome.Message(message) else this

    /**
     * A router proposal never auto-executes (R4). [ActionRiskLevel.SAFE] families may render as a
     * one-tap suggestion (`false`); everything else — including any unregistered/unknown descriptor —
     * requires an explicit confirm card (`true`), the fail-safe default.
     */
    private fun needsConfirmation(plan: PlanResult.RoutedAction): Boolean =
        catalog.descriptor(plan.action.id)?.risk != ActionRiskLevel.SAFE
}
