package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.ai.AiProviderConfigRepository
import com.sidr.launcher.domain.connectivity.ConnectivityChecker
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.result.OperationResult
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
 * 2. **A goal FastPath DECIDED but did not ACHIEVE ⇒ the agent** ([StartAgentSessionUseCase], A0
 *    Task 11). Exactly one outcome qualifies: [CommandMessage.NoAppFound] — FastPath understood
 *    "open X" and found no such app. Not "any Message", not "anything that did not execute";
 *    widening the list is a separate decision for a later block.
 * 3. **`localOnlyMode` on ⇒ byte-for-byte FastPath parity** — the planner is never consulted and
 *    nothing leaves the device. A FastPath hit returns untouched; a miss says so plainly
 *    ([CommandMessage.UnderstandingLocalOnly]) instead of claiming the command was unknown.
 * 4. FastPath decided confidently ⇒ return its outcome untouched; the planner is not consulted.
 * 5. **No provider configured ⇒ [CommandMessage.UnderstandingNeedsProvider]**, planner never
 *    consulted. This check lives *here*, in the gate, and not in the planner implementation (Этап
 *    4.0 fork F4): with the flag inverted it is a privacy guarantee — "no provider ⇒ no outbound
 *    call" — and a guarantee may not rest on how one adapter happens to be written.
 *    [AiProviderConfigRepository] is an existing `commonMain` port, so the gate stays KMP-portable
 *    and gains no new module edge (ADR 3/4). The API key deliberately stays the planner's business:
 *    the gate decides *whether to ask*, never touches secrets.
 * 6. **Offline ⇒ [CommandMessage.UnderstandingNeedsNetwork]** — no socket the planner would abandon.
 * 7. Map the plan: a [PlanResult.RoutedAction] surfaces as a **non-executing** proposal
 *    ([CommandOutcome.RoutedAction], Fork R4 — never auto-execute); a [PlanResult.Clarify] becomes a
 *    message; [PlanResult.NoPlan] keeps the FastPath outcome.
 *
 * **Step 2 sits above step 3 and that does not weaken the chain.** The three
 * understanding-unavailable messages stay mutually exclusive because step 2 is keyed on an outcome
 * FastPath *produced* — a decided [CommandMessage.NoAppFound] — and not on a system state, whereas
 * steps 3, 5 and 6 are each keyed on one of the three states. A command that reaches step 2 was never
 * going to reach any of them: `NoAppFound` is a decided outcome, so `isUndecided()` is false and
 * `orHonestly` would have returned it verbatim at step 3 anyway.
 *
 * **What the ordering DOES break, stated rather than glossed.** `DOC-ADL-3` used to read "…and
 * every outcome FastPath **decided** is returned byte-for-byte". `NoAppFound` is an outcome FastPath
 * decided, and under `localOnlyMode` this branch replaces it with
 * [CommandOutcome.AgentSessionStarted], so that clause was false exactly where the rule was written to
 * bite. **The amendment landed 2026-08-22** (A0 Task 14, commit `ccf7426`; matrix §6 journal row): the
 * rule now says what it always meant in substance — **no model is consulted and nothing leaves the
 * device** — because deterministic plan replay is part of the local path and may legitimately change an
 * outcome FastPath decided. What survives byte-for-byte is an outcome FastPath decided **and
 * achieved**; `NoAppFound` is decided and *not* achieved, which is why this branch may take it.
 * Cite the rule by ID: it has been amended twice and the §6 journal carries both texts.
 *
 * What the ordering does *not* break is the part that matters: A0's
 * [com.sidr.launcher.domain.agent.Planner] is deterministic and offline, so this branch consults no
 * model and transmits nothing, which is exactly what the signed local-only copy promises. Should that
 * ever stop being true, this branch has to move below step 3, not be excused.
 */
class RouteCommandUseCase(
    private val handleUserCommand: HandleUserCommandUseCase,
    private val planner: CommandPlanner,
    private val catalog: ActionCatalog,
    private val featureFlagRepository: FeatureFlagRepository,
    private val providerConfigRepository: AiProviderConfigRepository,
    private val connectivityChecker: ConnectivityChecker,
    private val startAgentSession: StartAgentSessionUseCase,
) {

    suspend fun route(rawInput: String): CommandOutcome {
        // (1) FastPath first — unchanged, offline, runs exactly once (its recording side effect too).
        val ruleOutcome = handleUserCommand.handle(rawInput)

        // (2) A goal FastPath DECIDED but did not ACHIEVE: it understood "open X" and found no such
        // app. This is the one outcome A0 hands to the agent — not "any Message", not "anything that
        // did not execute". The branch sits above the localOnlyMode check on purpose: the planner
        // here is deterministic and offline, so it consults no model and transmits nothing, which is
        // what the signed local-only copy promises ("nothing leaves this device"). It DOES falsify
        // DOC-ADL-3's byte-for-byte clause, which Task 14 Step 1 narrows per spec §8.1 — see the
        // class KDoc. Widening this list is a separate decision for a later block.
        val message = (ruleOutcome as? CommandOutcome.Message)?.message
        if (message is CommandMessage.NoAppFound) {
            val goal = AgentGoal(
                text = rawInput.trim(),
                shape = GoalShape.AppNotInstalled(message.query),
            )
            // Fails open: NoPlan, or any store failure, leaves the FastPath outcome exactly as it was.
            val started = startAgentSession.start(goal)
            if (started is OperationResult.Success && started.value != null) {
                return CommandOutcome.AgentSessionStarted(started.value)
            }
        }

        // (3) Local-only ⇒ exact FastPath parity; the planner is never consulted.
        if (featureFlagRepository.getFlags().first().localOnlyMode) {
            return ruleOutcome.orHonestly(CommandMessage.UnderstandingLocalOnly)
        }

        // (4) FastPath decided — a confident outcome is returned untouched.
        if (!ruleOutcome.isUndecided()) return ruleOutcome

        // (5) No provider ⇒ nothing may leave the device, and we say why (F4 — gate, not adapter).
        if (providerConfigRepository.activeConfig().first() == null) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider)
        }

        // (6) Offline ⇒ honest "needs network"; never open a socket the planner would abandon.
        if (!connectivityChecker.isOnline()) {
            return CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork)
        }

        // (7) Consult the planner and map its structured decision; any failure ⇒ NoPlan ⇒ FastPath outcome.
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
        requiresConsent(catalog.descriptor(plan.action.id)?.risk ?: ActionRiskLevel.DANGEROUS)
}
