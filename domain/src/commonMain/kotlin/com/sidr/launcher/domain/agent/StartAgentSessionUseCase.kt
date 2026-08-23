package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Goal -> plan -> a persisted `Running` session. `NoPlan` returns `null` and writes nothing.
 *
 * **The plan is validated here too, not only before every step** (review finding F10, 2026-08-23).
 * Spec §4.2 and §6.2 both say the shape check "runs at plan time **and** again before every step";
 * only the second half existed, so whatever a `Planner` returned was persisted unread. For A0's
 * `TemplatePlanner` that costs nothing — it cannot emit an invalid plan — but the port exists so A4'
 * can bind a model planner behind it, and the difference then is between an unrunnable plan that was
 * never written and one that is on disk, on screen, and carrying the raw command text until its first
 * step fails.
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
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        val planned = planner.plan(goal, registry)
        if (planned !is PlanningResult.Planned) return OperationResult.Success(null)
        if (!isRunnable(planned.plan)) return OperationResult.Success(null)

        val id = ids.newId()
        val session = AgentSession(
            id = id,
            goal = goal,
            plan = planned.plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(planned.plan.steps.size))),
        )

        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(id)
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
