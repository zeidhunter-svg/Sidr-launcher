package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.LauncherAction

/**
 * The **third** AI pipeline (AIL-4): structured routing-via-LLM. Distinct from [IntentMatcher]
 * (offline classification → `IntentMatchResult`) and from `GenerateReplyUseCase` (the assistant's
 * conversational `Flow<AiChunk>`). A [CommandPlanner] maps a natural-language [command] onto a
 * **registered** [LauncherAction] drawn from the [ActionCatalog] — one structured decision, not a
 * token stream and not a classification.
 *
 * Invariants (ADR 2026-07-05 — AIL-4):
 * - **Never throws.** Offline / no BYOK key / provider error / non-tool-capable model / unparseable
 *   or hallucinated reply → [PlanResult.NoPlan]; the caller ([RouteCommandUseCase]) then keeps the
 *   rule outcome. A planner failure must be invisible beyond "no smarter suggestion appeared".
 * - **Fail-closed parsing.** Only a strict, schema-valid structured reply becomes a
 *   [PlanResult.RoutedAction]; free text never drives an action.
 * - **Consulted only on low rule-confidence** (the trigger lives in [RouteCommandUseCase]), so
 *   "local matching runs before any LLM call" holds.
 * - **Privacy:** the impl sends only the user command + the static [ActionCatalog] schema — no
 *   device/usage/calendar/location/history/clipboard context (see `OutboundContextPolicy`).
 */
interface CommandPlanner {
    /**
     * Returns a routing decision for [command] over the currently-registered [catalog]. Never throws;
     * any failure collapses to [PlanResult.NoPlan].
     */
    suspend fun plan(command: String, catalog: ActionCatalog): PlanResult
}

/** The outcome of a [CommandPlanner.plan] call. */
sealed interface PlanResult {

    /**
     * The LLM proposed a concrete registered action. [confidence] is the model's own advisory 0..1
     * (not a hard gate). Per the AIL-4 decision (Fork R4) a routed action is **never auto-executed** —
     * the caller surfaces it for suggestion/confirmation (AIL-5).
     */
    data class RoutedAction(val action: LauncherAction, val confidence: Float) : PlanResult

    /** The LLM needs one disambiguating answer; the UI shows [question] and nothing executes. */
    data class Clarify(val question: String) : PlanResult

    /** Declined / offline / unparseable / non-tool-capable model — the caller keeps the rule outcome. */
    data object NoPlan : PlanResult
}
