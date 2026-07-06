package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.ai.router.CommandPlanner
import com.sidr.launcher.domain.ai.router.PlanResult

/**
 * In-memory fake [CommandPlanner]. Returns [resultToReturn] (default [PlanResult.NoPlan], the
 * fail-closed outcome) and records what it was asked, so tests can assert both the mapping of a plan
 * and — crucially — that the planner was **never consulted** on the router-off / confident-rule /
 * offline paths. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakeCommandPlanner(
    var resultToReturn: PlanResult = PlanResult.NoPlan,
) : CommandPlanner {

    var planCallCount: Int = 0
        private set

    /** The last command the planner was asked to route; `null` until [plan] is called. */
    var lastCommand: String? = null
        private set

    override suspend fun plan(command: String, catalog: ActionCatalog): PlanResult {
        planCallCount++
        lastCommand = command
        return resultToReturn
    }
}
