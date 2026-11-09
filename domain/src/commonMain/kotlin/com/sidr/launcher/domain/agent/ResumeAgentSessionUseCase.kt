package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * What «Продолжить» did — or that the ROUTE must decide instead (A4′ phase 3a, spec §7.12.3 condition 4).
 */
sealed interface Resumption {
    /** The engine took it: continued from the cursor, planned afresh, or ended honestly (phase 2, M1(b)). */
    data class Ran(val session: AgentSession) : Resumption

    /**
     * The ROUTE must look again, not the engine. Two shapes, both of a paused plan in which nothing has
     * acted:
     *  - a plan the MODEL wrote (A4′ phase 3a, condition 4). It is not planned again here — the
     *    deterministic planner would answer `NoPlan`, and the engine names no model port;
     *  - a plan started from a pick at step 2b (A4′ phase 3b, controller ruling E5-R1): the goal, as
     *    typed, still needs the user's choice — the fresh look answers `PlanningResult.Unresolved`.
     *    Ending it `Failed` would throw that choice away; the route's step 2b asks for it again.
     *
     * The layer that holds the route closes the stale session and takes [goal]'s words through the
     * route, where gates 3, 5 and 6, the interpretation card and step 2b's pick are met afresh.
     */
    data class NeedsRoute(val goal: AgentGoal) : Resumption
}

/**
 * «Продолжить» on a paused plan (A4′ phase 2, spec §6.4 as amended by owner fork F2-3.1).
 *
 * If nothing in the plan can have acted yet ([AgentSession.replansOnResume]), the goal is planned
 * AFRESH and run as a new session. What it saw before the pause may be stale — A0's acceptance
 * (§12.8) opened the store for an app the user had installed meanwhile. The new session **replaces**
 * the stale one in the store, because the store keeps one session at rest (plan §0.3 P5). A re-run
 * under the same step index would have made the trace lie (§0.3 P4), which is why this is a new
 * session and not a revision.
 *
 * If something may have acted, a call is in flight, or the plan is an undo, the paused session
 * continues from its cursor exactly as before phase 2. If the goal no longer plans, the stale session
 * ends [ExecutionState.Failed]: continuing a plan whose re-check just failed is the staleness A0 §12.8
 * condemned (owner decision 2026-10-03, M1(b)). Nothing is performed; the trace records
 * `SessionEnded(Failed)`; at rest the store is empty.
 *
 * A plan the model wrote (A4′ phase 3a) in which nothing has acted is handed back as
 * [Resumption.NeedsRoute] — see there; so is one whose fresh look needs the user's pick again (a goal
 * step 2b answered with a target pick, A4′ phase 3b, E5-R1).
 *
 * Re-planning after a FAILURE is not here: none of the deterministic planners can do anything better
 * after one, and its first consumer is phase 3's model planner (the amendment).
 */
class ResumeAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val run: RunAgentSessionUseCase,
) {
    suspend fun resume(paused: AgentSession): OperationResult<Resumption> {
        if (!paused.replansOnResume()) return run.run(paused.resumed()).ran()
        // A4′ phase 3a (condition 4): a plan the model wrote goes back through the route, never through
        // the deterministic planner here — and nothing about that is decided by the engine.
        if (paused.plan.isModelProposed()) return OperationResult.Success(Resumption.NeedsRoute(paused.goal))
        // Exhaustive with no `else` — one of `PlanningResult`'s addresses (its KDoc).
        val fresh = when (val planned = start.planning(paused.goal)) {
            // The goal plans by itself now: a new session, from a fresh look (F2-3.1).
            is PlanningResult.Planned -> planned.plan
            // E5-R1: the goal still needs the user's pick (step 2b's `ChooseTarget`). Back to the route,
            // which asks again — as it re-asks a model plan — never `Failed`, never a guess.
            is PlanningResult.Unresolved -> return OperationResult.Success(Resumption.NeedsRoute(paused.goal))
            // The goal no longer plans: the stale session ends `Failed` (owner decision M1(b)).
            PlanningResult.NoPlan -> return run.run(paused.ended(ExecutionState.Failed)).ran()
        }
        val started = when (val saved = start.startPlanned(paused.goal, fresh)) {
            is OperationResult.Failure -> return saved
            // Practically unreachable: `planning` validated against the same registry just above, but
            // the federation is derived per call (A1″), so a source's tool set can change between that
            // `planning` and this `startPlanned`. It means the same as the no-plan case above — the goal
            // no longer plans against the current registry — so it ends the same way (owner decision
            // 2026-10-03, M1(b)): the stale session ends `Failed`, it is not continued.
            is OperationResult.Success -> saved.value ?: return run.run(paused.ended(ExecutionState.Failed)).ran()
        }
        return run.run(started).ran()
    }

    private fun OperationResult<AgentSession>.ran(): OperationResult<Resumption> = when (this) {
        is OperationResult.Failure -> this
        is OperationResult.Success -> OperationResult.Success(Resumption.Ran(value))
    }
}
