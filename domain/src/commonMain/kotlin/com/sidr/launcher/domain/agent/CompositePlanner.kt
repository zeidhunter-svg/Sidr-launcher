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
            val result = planner.plan(goal, registry)
            if (result is PlanningResult.Planned) return result
        }
        return PlanningResult.NoPlan
    }
}
