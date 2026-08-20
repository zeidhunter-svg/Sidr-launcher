package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * The deterministic planner of A0 — the seed of the learned-plan cache, not a stand-in for the model.
 *
 * It replays one already-understood goal shape offline and for free, which is what makes A0's device
 * acceptance repeatable and what makes the agent work in local-only mode without anything leaving the
 * device. The model planner arrives in A4' behind this same [Planner] port.
 *
 * **It never names a destination.** Risk, schema and identity all come from the registry, so when A1'
 * registers F-Droid, a vendor site or Galaxy Store, choosing among them is a planner concern and the
 * engine below is untouched.
 *
 * Step 0 re-attempts the launch that FastPath already tried. That is deliberate: the plan must be
 * self-contained across a process restart (it cannot depend on an observation that lives outside the
 * session), and if the user installed the app while the session was paused, step 0 launches it and
 * step 1 correctly skips.
 */
class TemplatePlanner : Planner {

    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult =
        when (val shape = goal.shape) {
            is GoalShape.AppNotInstalled -> planMissingApp(shape, registry)
        }

    private fun planMissingApp(shape: GoalShape.AppNotInstalled, registry: ToolRegistry): PlanningResult {
        val query = shape.query.trim()
        if (query.isEmpty()) return PlanningResult.NoPlan

        val launch = registry.find(ToolIds.LAUNCH_APP) ?: return PlanningResult.NoPlan
        val store = registry.find(ToolIds.PLAY_STORE_SEARCH) ?: return PlanningResult.NoPlan

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(launch.id, mapOf("query" to query)),
                        risk = launch.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(store.id, mapOf("query" to query)),
                        risk = store.risk,
                        precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                        rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
                    ),
                ),
            ),
        )
    }
}
