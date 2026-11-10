package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.sort.ArgSort
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult

/** What must hold before a step may run. Evaluated fresh on every attempt, including after a resume. */
sealed interface StepPrecondition {
    data object None : StepPrecondition

    /** The immediately preceding step must have observed [fact]. */
    data class PreviousStepObserved(val fact: ObservedFact) : StepPrecondition
}

/**
 * Typed provenance — why this step is in the plan. The feature layer renders it to localized text.
 *
 * [COMPENSATION] (A4′ phase 2): the step undoes a step of another, finished plan. It is what marks a
 * plan as an undo — `Compensation.isUndo` — so that it offers no undo of its own and is never planned
 * again from its goal. Persisted by name in an existing TEXT column; no migration.
 *
 * [MODEL_PROPOSED] (A4′ phase 3a, spec §7.12.3 condition 4): the bridge's model planner wrote the step.
 * A paused plan made of such steps, in which nothing has acted, is never planned again from its goal by
 * the deterministic planner — it goes back through the route ([isModelProposed],
 * `ResumeAgentSessionUseCase`). Persisted by name, like [COMPENSATION]; never shown — a step's line is
 * keyed on its tool, so it needs no string.
 */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK, COMPENSATION, MODEL_PROPOSED }

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

/** A plan the bridge's model planner wrote (A4′ phase 3a) — its steps carry [StepRationale.MODEL_PROPOSED]. */
fun ExecutionPlan.isModelProposed(): Boolean = steps.any { it.rationale == StepRationale.MODEL_PROPOSED }

/**
 * What a DETERMINISTIC planner answers. Three variants since A4′ phase 3b (plan §0.2 B-6):
 *  - [Planned] — a plan to run;
 *  - [NoPlan] — this planner has no answer it can give for the goal. At `RouteCommandUseCase` step 2b
 *    it falls through to the cloud model. It no longer means "a tool claimed it and its target did not
 *    resolve", which it also meant from A1″ Task 6b until this phase (R14-39) — that is [Unresolved]
 *    now. It means "no registered tool claimed the text", or "a second goal rides in a target's words"
 *    (B-15, on purpose — the model plans both), or — since phase 3b's Task 6, controller ruling E6-R1 —
 *    "a tool claimed it and its sort cannot read the VALUE" (on purpose: a value sort is a pure function
 *    of the command text, and the vocabulary is no filter on understanding; this covers a second goal in
 *    a value slot too). **It also still means three cases in which a tool DID
 *    claim the text**, each a defect class older than this phase and each still reaching step 7, named
 *    rather than implied away: the claimed tool is not in the registry (withheld — e.g. for a permission
 *    the process does not hold); the vocabulary and the descriptor disagree about a required argument's
 *    NAME (`ToolMatchPlanner`); the plan does not validate against the registry
 *    (`StartAgentSessionUseCase.planning` maps it to `NoPlan`);
 *  - [Unresolved] — a tool claimed the goal and its target needs the user ([UnresolvedTarget]). It never
 *    reaches step 7: step 2b answers it on the device (R14-39, closed).
 *
 * **Adding a variant here is a multi-address edit, and every address is a compile error by
 * construction** (A4' phase 0, spec §3 0.0, which built them for exactly the day [Unresolved] arrived):
 *  - `CompositePlanner.plan` — where a non-`Planned` result used to become `NoPlan` silently; it now
 *    stops on [Unresolved], since a claimed target is the composite's answer;
 *  - `StartAgentSessionUseCase.planning` and `.plan` — where a non-`Planned` result used to become
 *    `Success(null)`; `planning` passes [Unresolved] through, `plan` (resume's reader) reads it as none;
 *  - `RouteCommandUseCase` step 2b — the `when` over `planning`, in `domain/ai/router`.
 *
 * Neither phase 0 conversion was wrong while two variants existed; both were answers given in advance
 * for a variant nobody had written, and `NoPlan` at step 2b falling through to the cloud model is what
 * made their silence doctrinal rather than stylistic.
 *
 * No test holds this and none can: a sealed interface admits subclasses only from its own
 * compilation unit, and `jvmTest` is not `commonMain`. The compiler is the enforcement; this
 * paragraph is where the message lands.
 */
sealed interface PlanningResult {
    data class Planned(val plan: ExecutionPlan) : PlanningResult
    data object NoPlan : PlanningResult
    data class Unresolved(val target: UnresolvedTarget) : PlanningResult
}

/**
 * A target a deterministic planner recognised and could not bind by itself — the answer that closes
 * R14-39 (A4′ phase 3b, fork 3b-E as refined in plan §0.1; spec §7.12.3). Step 2b used to receive it
 * as [PlanningResult.NoPlan] and send the raw command to the cloud model, so one unresolved name
 * decided what left the device. Now the device answers it: «нет такого приложения», a pick, or an
 * honest failure — and nothing reaches step 7.
 *
 * [pending] is the one step the planner built, **without** [arg] and without [labelArg]; [bind] is the
 * one way to finish it. **When [arg] is a REQUIRED argument it does not validate as written** — its
 * target is missing — so nothing can start it by mistake. That holds for every app-sorted argument
 * reachable at step 2b today (`uninstall_app`, `open_app_info`, `set_app_alias` all declare `app`
 * required). An OPTIONAL app-sorted argument whose words did not resolve would leave a pending step that
 * DOES validate — a step with no target; no such tool is reachable at 2b (`launch_app`'s optional `app`
 * is never selected by the vocabulary), named so the next one is not added unawares. The value-sorted
 * arguments reachable at 2b (`set_timer`'s `duration`, `set_alarm`'s `time`) are required too. [words]
 * are what the user typed for the target, as the vocabulary handed them over (normalised, never the raw
 * span). [sort] is what [words] were meant to be: `ArgSorts.APP` (Task 5), or since phase 3b's Task 6 a
 * VALUE sort the device could not consult — always with [unreadable] (below). A value its sort merely
 * cannot READ is not an `Unresolved` at all: `ToolMatchPlanner` answers it `NoPlan`, left to the model
 * like any unclaimed text (controller ruling E6-R1).
 *
 * [candidates] empty: for an app, nothing on the device answers to [words]; when [unreadable], the
 * device could not be asked. (An empty, readable `Unresolved` of a value sort has no production producer
 * since E6-R1; step 2b would say «не понял» in [words], a fail-safe.) Non-empty (an app only — the
 * type is `InstalledApp`): the user picks; [confirm] is `true` when they are OFFERED rather than matched
 * — a word form (3b-G), or a window of the words short of the whole (3b-E: «a window equal to the whole
 * slot → plan; a window shorter than the slot → confirm»).
 *
 * [unreadable] (controller ruling E5-P1): the app list or the alias store could not be read — or, since
 * Task 6, a value sort had no registered resolver, could not be asked, or answered with a choice this
 * type cannot carry (E6-F2).
 * Not "not found" — that would be a lie, the app may be installed — and not a reason to ask the model,
 * which would let a disk error decide what leaves the device. Step 2b answers it as step 7 answers
 * `Binding.Unavailable`: a generic failure.
 */
data class UnresolvedTarget(
    val goal: AgentGoal,
    val pending: PlanStep,
    val arg: String,
    val labelArg: String?,
    val sort: ArgSort,
    val words: String,
    val candidates: List<InstalledApp>,
    val confirm: Boolean,
    val unreadable: Boolean = false,
)

/**
 * The tap on a pick (controller ruling E5-P2): [packageName] into [UnresolvedTarget.arg], the user's
 * [UnresolvedTarget.words] into [UnresolvedTarget.labelArg] when the tool has one, and the one-step
 * plan `StartAgentSessionUseCase.startPlanned` receives. `null` when [packageName] was not offered — a
 * tap binds nothing it was not asked about.
 *
 * **The one place this binding is spelled.** `RouteCommandUseCaseTest` and the launcher's command
 * session both call it, so the surface cannot bind differently from what the domain test holds. No
 * interpretation card stands between the tap and the session (spec criterion 15, second half): the user
 * chose the act by typing it and the target by tapping it. The engine still gates the step by its risk,
 * so a `CONFIRM` tool stops at its own consent card naming the package that will be acted on.
 */
fun UnresolvedTarget.bind(packageName: String): ExecutionPlan? {
    if (candidates.none { it.packageName == packageName }) return null
    val label = labelArg?.let { mapOf(it to ArgSource.Literal(words)) }.orEmpty()
    val args = pending.invocation.args + (arg to ArgSource.Literal(packageName)) + label
    // Index 0: the one step of a one-step plan (`ExecutionPlan`'s invariant), whatever the planner wrote.
    return ExecutionPlan(listOf(pending.copy(index = 0, invocation = pending.invocation.copy(args = args))))
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
 * [priorObservations] is **not speculative, and its consumer moved once**: re-planning after a
 * partial failure was dated to phase 2 (§6.4), and the owner moved it to phase 3 on 2026-09-27 (fork
 * F2-3.1). None of the deterministic planners can do anything better after a failure, and the first
 * planner that can — the bridge's model planner — arrives there. Phase 2's re-planning (on resume)
 * plans afresh with an EMPTY request on purpose: what the stale attempt observed is exactly what is
 * no longer trusted. It is empty at every call site, and that is the honest state.
 *
 * The tool registry stays a separate parameter. It is not context about the attempt; it is the world
 * the plan is written against, and it is read by the engine as well as by the planner.
 */
data class PlanningRequest(
    val goal: AgentGoal,
    val priorObservations: Map<Int, ToolResult> = emptyMap(),
)

/**
 * Port of the DETERMINISTIC planners, asked at step 2b and on resume — where no model may be called
 * (`DOC-ADL-3`). The model planner is a different port, `com.sidr.launcher.domain.ai.bridge.ModelPlanner`,
 * asked at step 7 only (A4′ phase 3a, spec §7.12.2).
 */
interface Planner {
    suspend fun plan(request: PlanningRequest, registry: com.sidr.launcher.domain.tool.ToolRegistry): PlanningResult
}
