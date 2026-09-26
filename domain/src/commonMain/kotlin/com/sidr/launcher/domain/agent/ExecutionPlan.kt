package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult

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

/**
 * **Adding a variant here is a two-address edit, and both addresses are compile errors by
 * construction** (A4' phase 0, spec §3 0.0):
 *  - `CompositePlanner.plan` — where a non-`Planned` result used to become `NoPlan` silently;
 *  - `StartAgentSessionUseCase.start` — where a non-`Planned` result used to become `Success(null)`.
 *
 * Neither conversion was wrong while two variants existed; both were answers given in advance for a
 * variant nobody had written. `NoPlan` at `RouteCommandUseCase` step 2b falls through to the cloud
 * model, so the cost of that silence is measured and doctrinal, not stylistic — see R14-39.
 *
 * No test holds this and none can: a sealed interface admits subclasses only from its own
 * compilation unit, and `jvmTest` is not `commonMain`. The compiler is the enforcement; this
 * paragraph is where the message lands.
 */
sealed interface PlanningResult {
    data class Planned(val plan: ExecutionPlan) : PlanningResult
    data object NoPlan : PlanningResult
}

/**
 * Everything the planner is allowed to see about **this** planning attempt.
 *
 * **One parameter now so that later ones are a default, not a sweep** (spec §3 0.4).
 * `agentic-os-architecture.md:104` asks for `plan(goal, ContextSnapshot, ToolRegistry, UserMemory)`,
 * and the A2/A3 types in that signature do not exist. Introducing them empty would be literally the
 * `:data:ai-local` mistake Master Plan §3.4 turned into a rule. Introducing nothing would mean that
 * the first real addition edits the signature of four production implementations in three modules,
 * both consumers, and every test fake — which is the cost this type is here to not pay twice.
 *
 * [priorObservations] is **not speculative**: its consumer is named and dated — re-planning after a
 * partial failure, phase 2 (§6.4), which cannot work without what the first attempt observed. It is
 * empty at every call site phase 0 ships, and that is the honest state rather than a placeholder: a
 * first attempt has observed nothing.
 *
 * The tool registry stays a separate parameter. It is not context about the attempt; it is the world
 * the plan is written against, and it is read by the engine as well as by the planner.
 */
data class PlanningRequest(
    val goal: AgentGoal,
    val priorObservations: Map<Int, ToolResult> = emptyMap(),
)

/** Port. A0 binds the deterministic `TemplatePlanner`; A4' binds a model planner behind the same seam. */
interface Planner {
    suspend fun plan(request: PlanningRequest, registry: com.sidr.launcher.domain.tool.ToolRegistry): PlanningResult
}
