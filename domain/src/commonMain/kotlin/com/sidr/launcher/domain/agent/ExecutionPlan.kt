package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolInvocation

/** What must hold before a step may run. Evaluated fresh on every attempt, including after a resume. */
sealed interface StepPrecondition {
    data object None : StepPrecondition

    /** The immediately preceding step must have observed [fact]. */
    data class PreviousStepObserved(val fact: ObservedFact) : StepPrecondition
}

/** Typed provenance — why this step is in the plan. The feature layer renders it to localized text. */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK }

data class PlanStep(
    val index: Int,
    val invocation: ToolInvocation,
    val risk: ActionRiskLevel,
    val precondition: StepPrecondition,
    val rationale: StepRationale,
)

/** Ordered and bounded. Deliberately **not** a DAG in A0, and never re-planned. */
data class ExecutionPlan(val steps: List<PlanStep>)

sealed interface PlanningResult {
    data class Planned(val plan: ExecutionPlan) : PlanningResult
    data object NoPlan : PlanningResult
}

/** Port. A0 binds the deterministic `TemplatePlanner`; A4' binds a model planner behind the same seam. */
interface Planner {
    suspend fun plan(goal: AgentGoal, registry: com.sidr.launcher.domain.tool.ToolRegistry): PlanningResult
}
