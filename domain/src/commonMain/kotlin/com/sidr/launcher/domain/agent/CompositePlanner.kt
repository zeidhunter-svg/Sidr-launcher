package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * Asks each planner in order and returns the first plan. **Not a fallback chain in disguise:** each
 * planner owns a disjoint set of goal shapes today (`TemplatePlanner` — `AppNotInstalled`,
 * `ToolMatchPlanner` — `Free`), so the order is a tie-break that never fires rather than a priority.
 * It is a list rather than a `when` because that is the shape that stops growing when A4' adds the
 * model planner behind this same port.
 *
 * It stays in `commonMain` because it is pure composition: no text, no locale, no tool names. The
 * second consumer can compose its own planners with it unchanged, which is the test of whether a
 * thing belongs here at all.
 */
class CompositePlanner(private val planners: List<Planner>) : Planner {
    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        planners.forEach { planner ->
            // Exhaustive with no `else`, and that is this function's whole safety property (spec
            // §3 0.0). The previous shape — `if (result is Planned) return result` — did not
            // *ignore* a third variant, it **converted** it: anything that was not `Planned` fell
            // out of the loop and left as `NoPlan`. `NoPlan` at routing step 2b falls through to
            // the cloud model, so the first `Clarify`-shaped variant A4' adds would have sent a
            // clarifying question off-device as raw command text, on a goal a registered tool had
            // already matched — R14-39 with a third cause, and with the whole suite green. A
            // third variant must now be a compile error HERE, at the address where the decision
            // belongs.
            when (val result = planner.plan(goal, registry)) {
                is PlanningResult.Planned -> return result
                PlanningResult.NoPlan -> Unit // ask the next planner
            }
        }
        return PlanningResult.NoPlan
    }
}
