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
    /**
     * The step's **stable identity**: the key of `AgentSession.observations`, of
     * `AgentSession.consents`, and of every `TraceEvent` that refers to this step.
     *
     * In A0 it must additionally equal the step's position in [ExecutionPlan.steps] — [ExecutionPlan]
     * enforces that in its `init`, so the two are interchangeable by construction. See the invariant's
     * rationale on [ExecutionPlan].
     */
    val index: Int,
    val invocation: ToolInvocation,
    val risk: ActionRiskLevel,
    val precondition: StepPrecondition,
    val rationale: StepRationale,
)

/**
 * Ordered and bounded. Deliberately **not** a DAG in A0, and never re-planned.
 *
 * **Invariant: `steps[i].index == i` for every position `i`.** `AgentExecutor` reads a step's position
 * and its `index` interchangeably in three independent places, and nothing else forces them to agree:
 *  - the cursor — `prepare` reads `steps.getOrNull(session.cursor)` as a *position* while `perform`
 *    writes `cursor = invoked.index + 1` as an *index*;
 *  - `isSatisfied`, where `observations[index - 1]` means "the previous step";
 *  - `checkpointFor`, where `it.index < step.index` means "the steps that already ran".
 *
 * A plan violating this made all three quietly wrong at once rather than one of them loudly wrong: a
 * re-review probe ran the step with `index = 1` on a 2-step plan, which set `cursor = 2`, and the
 * engine reported `Completed` with the step at `index = 0` never executed and never traced. Since
 * `RunAgentSessionUseCase` loops on `advance` and reports the terminal state, that truncation would
 * reach the user as success.
 *
 * Rejecting the plan at construction is what turns that fail-silent into a fail-fast: the offending
 * value is unconstructible, so it can never reach the engine, and all three assumptions above are
 * simultaneously true without a single defensive branch. `index` stays a field rather than becoming
 * implicit in the list order because it is the identity the trace and the maps are keyed by — a
 * persisted `TraceEvent` must still name its step after the list is gone.
 */
data class ExecutionPlan(val steps: List<PlanStep>) {
    init {
        steps.forEachIndexed { position, step ->
            require(step.index == position) {
                "ExecutionPlan requires PlanStep.index to equal its list position, but the step at " +
                    "position $position has index ${step.index}. `index` is the step's stable " +
                    "identity (the key of observations, consents and every TraceEvent) and " +
                    "AgentExecutor reads it interchangeably with the position."
            }
        }
    }
}

sealed interface PlanningResult {
    data class Planned(val plan: ExecutionPlan) : PlanningResult
    data object NoPlan : PlanningResult
}

/** Port. A0 binds the deterministic `TemplatePlanner`; A4' binds a model planner behind the same seam. */
interface Planner {
    suspend fun plan(goal: AgentGoal, registry: com.sidr.launcher.domain.tool.ToolRegistry): PlanningResult
}
