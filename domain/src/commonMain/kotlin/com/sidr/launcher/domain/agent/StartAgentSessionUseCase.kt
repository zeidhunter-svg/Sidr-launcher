package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Goal -> plan -> a persisted `Running` session. `NoPlan` returns `null` and writes nothing; so does
 * `Unresolved` (A4′ phase 3b) — a target the user must pick is not a plan, and step 2b reads it through
 * [planning], never through [start].
 *
 * **The plan is validated here too, not only before every step** (review finding F10, 2026-08-23).
 * Spec §4.2 and §6.2 both say the shape check "runs at plan time **and** again before every step";
 * only the second half existed, so whatever a `Planner` returned was persisted unread. For A0's
 * `TemplatePlanner` that costs nothing — it cannot emit an invalid plan — and since A4′ phase 3a the
 * model's plans arrive here too, through `startPlanned` from the interpretation card — validated against
 * the whole federation, the engine's second line behind the bridge's validator. The difference is
 * between an unrunnable plan that was never written and one that is on disk, on screen, and carrying
 * the raw command text until its first step fails.
 *
 * A rejected plan is returned as `Success(null)`: the same answer as `NoPlan`, which the caller
 * already handles by keeping the FastPath outcome untouched. Failing *open to the previous behaviour*
 * is the honest move — the user asked for something the deterministic layer had already answered.
 */
class StartAgentSessionUseCase(
    private val planner: Planner,
    private val store: AgentSessionStore,
    private val ids: AgentSessionIdFactory,
    private val registry: ToolRegistry,
) {
    /** Plans [goal] and persists a `Running` session; `Success(null)` when there is nothing to run. */
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        val plan = plan(goal) ?: return OperationResult.Success(null)
        return when (val started = startPlanned(goal, plan)) {
            is OperationResult.Failure -> started
            is OperationResult.Success -> OperationResult.Success(started.value?.id)
        }
    }

    /**
     * The first attempt's answer for [goal], persisting nothing (A4′ phase 3b, plan §0.2 B-6) — what
     * `RouteCommandUseCase` step 2b reads, because it must tell the three answers apart: [PlanningResult.Planned]
     * only when the plan is runnable against the registry NOW (an unrunnable one is [PlanningResult.NoPlan],
     * the fail-open [start] has always given it); [PlanningResult.Unresolved] passed through untouched —
     * the target is the user's to pick, and nothing is validated or written before the pick. The request
     * carries no prior observations: a first attempt has observed nothing, and since owner fork F2-3.1
     * re-planning after a failure is phase 3's (spec §6.4, amended).
     */
    suspend fun planning(goal: AgentGoal): PlanningResult =
        // Exhaustive with no `else` (spec §3 0.0): a fourth variant must decide here what it means
        // before a session exists, and the compiler is what makes its author decide.
        when (val planned = planner.plan(PlanningRequest(goal), registry)) {
            is PlanningResult.Planned -> if (isRunnable(planned.plan)) planned else PlanningResult.NoPlan
            is PlanningResult.Unresolved -> planned
            PlanningResult.NoPlan -> PlanningResult.NoPlan
        }

    /**
     * [planning]'s plan, for a caller that has no user to ask — [start] and `ResumeAgentSessionUseCase`'s
     * fresh look. A target the user must pick is not a plan here: [PlanningResult.Unresolved] is `null`,
     * so a resumed session whose goal no longer binds by itself ends rather than guessing.
     */
    suspend fun plan(goal: AgentGoal): ExecutionPlan? =
        // Exhaustive with no `else` (spec §3 0.0), the second half of this file's address.
        when (val planned = planning(goal)) {
            is PlanningResult.Planned -> planned.plan
            is PlanningResult.Unresolved -> null
            PlanningResult.NoPlan -> null
        }

    /**
     * Persists a `Running` session for [plan] and returns it — a plan the caller built itself (an
     * undo, spec §6.2) or took from [plan]. `Success(null)` when [plan] is not runnable: validated
     * here too, so no caller can persist an unchecked plan (F10).
     *
     * **Saving REPLACES whatever session the store held** — the store keeps one session at rest (Room:
     * `replaceSession` deletes every other id; JVM: one file). That is what lets a fresh plan supersede
     * a stale one atomically on resume (spec §6.4, plan §0.3 P5).
     */
    suspend fun startPlanned(goal: AgentGoal, plan: ExecutionPlan): OperationResult<AgentSession?> {
        if (!isRunnable(plan)) return OperationResult.Success(null)
        val session = AgentSession(
            id = ids.newId(),
            goal = goal,
            plan = plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(plan.steps.size))),
        )
        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(session)
        }
    }

    /**
     * Every step's shape checks out against the current registry, and there is at least one step.
     *
     * The emptiness check is **A0's**, not the type's: `ExecutionPlan(emptyList())` stays
     * constructible because ADR 4/4 makes a 0-step plan a real future shape — "a 0-step plan *is* a
     * spoken reply". A0 has no surface for that reply, and a 0-step session would run to `Completed`
     * having done and traced nothing, reporting success for a goal nothing acted on. Whoever builds
     * the spoken-reply surface removes this line and puts the reply behind it.
     */
    private fun isRunnable(plan: ExecutionPlan): Boolean {
        if (plan.steps.isEmpty()) return false
        return plan.steps.all { step ->
            val preceding = plan.steps.filter { it.index < step.index }.map { it.invocation.id }
            InvocationValidator.validate(step.invocation, preceding, registry) is InvocationCheck.Valid
        }
    }
}
