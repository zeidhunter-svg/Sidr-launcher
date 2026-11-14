package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * Asks each planner in order and returns the first answer that is not `NoPlan` — a plan, or a target a
 * planner claimed and could not bind (`Unresolved`, A4′ phase 3b). **Not a fallback chain in disguise:** each
 * planner owns a disjoint set of goal shapes today (`TemplatePlanner` — `AppNotInstalled`,
 * `ToolMatchPlanner` — `Free`), so the order is a tie-break that never fires rather than a priority.
 * It is a list rather than a `when` because that is the shape that grows by a planner, not by a
 * branch. The model planner is NOT one of them — it is its own port, at step 7 (A4′ phase 3a).
 *
 * It stays in `commonMain` because it is pure composition: no text, no locale, no tool names. The
 * second consumer can compose its own planners with it unchanged, which is the test of whether a
 * thing belongs here at all.
 */
class CompositePlanner(private val planners: List<Planner>) : Planner {
    override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
        planners.forEach { planner ->
            // Exhaustive with no `else`, and that is this function's whole safety property (spec
            // §3 0.0). The previous shape — `if (result is Planned) return result` — did not
            // *ignore* a third variant, it **converted** it: anything that was not `Planned` fell
            // out of the loop and left as `NoPlan`. `NoPlan` at routing step 2b falls through to
            // the cloud model, so a third variant converted here would have sent off-device, as raw
            // command text, a goal a registered tool had already matched — R14-39 with a third
            // cause, and with the whole suite green. The third variant arrived in A4′ phase 3b
            // (`Unresolved`, B-6) and was decided HERE, at the address where the decision belongs:
            // a claimed target is this composite's answer. Asking on would let a later planner
            // replace it, or let the loop's closing `NoPlan` below erase it — the fall-through
            // `Unresolved` exists to end.
            when (val result = planner.plan(request, registry)) {
                is PlanningResult.Planned -> return result
                is PlanningResult.Unresolved -> return result
                PlanningResult.NoPlan -> Unit // ask the next planner
            }
        }
        return PlanningResult.NoPlan
    }
}
