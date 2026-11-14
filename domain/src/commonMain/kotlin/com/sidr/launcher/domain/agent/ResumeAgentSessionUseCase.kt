package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolResult

/**
 * What «Продолжить» did — or that the ROUTE, or the CARD, must decide instead (A4′ phase 3a, spec
 * §7.12.3 condition 4; phase 3b, fork 3b-A).
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

    /**
     * The CARD must be shown again (A4′ phase 3b, Task 11; fork 3b-A, plan §0.2 B-10): a paused plan the
     * MODEL wrote in which a step already acted, with no call in flight. [plan] is its remainder
     * ([ResumeAgentSessionUseCase.remainder]) — never empty. The model is NOT asked again: the act it
     * would re-plan around has happened, and what that act left is not the model's to read (D1). The
     * layer that holds the binder closes the stale session, re-binds every app of [plan] on the device
     * (`MentionBinder.rebind`) and shows the interpretation card; nothing runs before «Выполнить», which
     * starts a NEW session.
     */
    data class NeedsCard(val goal: AgentGoal, val plan: ExecutionPlan) : Resumption
}

/**
 * «Продолжить» on a paused plan (A4′ phase 2, spec §6.4 as amended by owner fork F2-3.1).
 *
 * If nothing in the plan can have acted yet ([AgentSession.replansOnResume]), the goal is planned
 * AFRESH and run as a new session. What it saw before the pause may be stale — A0's acceptance
 * (§12.8) opened the store for an app the user had installed meanwhile. The new session **replaces**
 * the stale one in the store, because the store keeps one session at rest (plan §0.3 P5). A re-run
 * under the same step index would have made the trace lie (§0.3 P4), which is why this is a new
 * session and not a revision. If the goal no longer plans, the stale session ends
 * [ExecutionState.Failed]: continuing a plan whose re-check just failed is the staleness A0 §12.8
 * condemned (owner decision 2026-10-03, M1(b)). Nothing is performed; the trace records
 * `SessionEnded(Failed)`; at rest the store is empty.
 *
 * A plan the model wrote (A4′ phase 3a) in which nothing has acted is handed back as
 * [Resumption.NeedsRoute] — see there; so is one whose fresh look needs the user's pick again (a goal
 * step 2b answered with a target pick, A4′ phase 3b, E5-R1).
 *
 * **A plan the model wrote in which a step ACTED, with no call in flight** (A4′ phase 3b, Task 11; fork
 * 3b-A, B-10) is handed back as [Resumption.NeedsCard] with its [remainder]: the steps after the last act,
 * for a card, re-bound on the device — the model is not asked. An EMPTY remainder (the last step itself
 * acted) continues from the cursor as before: the engine performs nothing and ends it (controller ruling
 * E11-P6). A remainder that cannot be written — a reference into a finished step that recorded no value
 * — ends the stale session [ExecutionState.Failed], as M1(b) ends a goal that no longer plans.
 *
 * Everything else that may have acted continues from its cursor exactly as before phase 2: a
 * DETERMINISTIC plan that acted (B-10 — `ResumeAgentSessionUseCaseTest > a paused plan in which a step
 * already acted continues from its cursor`), an undo, and any plan with a call in flight — resumed once
 * (`DOC-HMA-1`), never re-planned and never re-carded, since that call may already have acted.
 *
 * Re-planning after a FAILURE is not here: none of the deterministic planners can do anything better
 * after one. «Повторить остаток» on a finished `Failed`/`Blocked` model-plan card reads the same
 * [remainder] (controller ruling E11-P3); it is the user's tap, not a re-plan, and it asks no model.
 */
class ResumeAgentSessionUseCase(
    private val start: StartAgentSessionUseCase,
    private val run: RunAgentSessionUseCase,
) {
    private val remainders = RemainderPlanner()

    suspend fun resume(paused: AgentSession): OperationResult<Resumption> {
        if (!paused.replansOnResume()) {
            // A4′ phase 3b (fork 3b-A, B-10): a model plan that acted comes back as a card for its remainder.
            // Everything else here continues from the cursor, as since A0 — and so does this branch when it
            // does not apply (not the model's plan, an undo, a call in flight) or leaves nothing (E11-P6).
            if (paused.hasActed()) {
                // Exhaustive with no `else` — one of `PlanningResult`'s addresses (its KDoc).
                when (val rest = remainderOf(paused)) {
                    null -> Unit
                    is PlanningResult.Planned ->
                        if (rest.plan.steps.isNotEmpty()) return OperationResult.Success(Resumption.NeedsCard(paused.goal, rest.plan))
                    // A reference the remainder cannot carry: the stale session ends `Failed` (M1(b)).
                    // `RemainderPlanner` never answers `Unresolved`; were it to, a guess is not a remainder.
                    PlanningResult.NoPlan, is PlanningResult.Unresolved ->
                        return run.run(paused.ended(ExecutionState.Failed)).ran()
                }
            }
            return run.run(paused.resumed()).ran()
        }
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

    /**
     * The ONE builder of a session's remainder (controller ruling E11-P3), read by [resume] and by
     * «Повторить остаток» on a finished `Failed`/`Blocked` model-plan card: the steps of [session]'s plan
     * after its last act, re-indexed, every reference into a finished step its recorded value
     * ([RemainderPlanner], B-10). `null` when there is none to offer: not a plan the model wrote, an undo,
     * a call in flight (it may have acted — `DOC-HMA-1`), a plan the clock stopped on a call that may still
     * be acting ([cutCallMayStillAct] — the evidence that withholds «Отменить» too, owner fork F2-1b; fix
     * round 1, controller ruling E11-R1), a reference with no value, or nothing left. Pure — it plans
     * nothing anew and asks no model.
     */
    fun remainder(session: AgentSession): ExecutionPlan? =
        when (val rest = remainderOf(session)) {
            is PlanningResult.Planned -> rest.plan.takeIf { it.steps.isNotEmpty() }
            null, PlanningResult.NoPlan, is PlanningResult.Unresolved -> null
        }

    /** `null` when the remainder rule does not apply to [session] at all; otherwise [RemainderPlanner]'s answer. */
    private fun remainderOf(session: AgentSession): PlanningResult? {
        if (!session.plan.isModelProposed() || Compensation.isUndo(session.plan) || session.midStepInvocation() != null) return null
        if (session.cutCallMayStillAct()) return null
        return remainders.remainderOf(PlanningRequest(session.goal, priorObservations = session.observations, previous = session.plan))
    }

    private fun AgentSession.hasActed(): Boolean =
        observations.values.any { it is ToolResult.Effected || it is ToolResult.HandedOff }

    private fun OperationResult<AgentSession>.ran(): OperationResult<Resumption> = when (this) {
        is OperationResult.Failure -> this
        is OperationResult.Success -> OperationResult.Success(Resumption.Ran(value))
    }
}
