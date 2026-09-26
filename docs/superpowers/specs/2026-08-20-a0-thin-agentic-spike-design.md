# A0 - Thin Agentic Spike (Design Spec)

> **Status: PROPOSED (2026-08-20), amended 2026-08-21 (F6 — step-to-step data flow).** A0 is the first
> vertical slice of the agentic track. One real two-step goal passes
> `goal -> plan -> gate -> tool -> observe -> tool -> result -> trace` and is accepted on device. The
> purpose is to **prove the engine, not to build the layers**.
>
> **Amendment 2026-08-21 — F6.** The tool contract gains typed outputs and argument binding, so a step
> can consume what a previous step produced. Reason and change-control record: ADR "2026-08-21 —
> Развилка агентного трека: портируемое ядро с двумя потребителями с самого начала" in
> `ai-context/decisions.md`. It lands **before** the Room migration of §7, because afterwards the same
> change costs a migration rather than a contract edit. Sections touched: §2 (F6), §3, §4.1, §4.2,
> §6.1, §6.2, §7, §11, §13, §14, §15. **§12 (device acceptance) is deliberately unchanged** — the
> binding is not separately observable on the phone, and inventing an acceptance item that the owner
> cannot actually check would be the kind of decorative gate Этап 0.5 exists to prevent.
>
> **Amendment 2026-08-23 — the review round.** A cross-cutting review of the closed block found nine
> defects; eight are fixed in code, each mutation-verified. Four of them are amendments to what *this
> document* says, and they are recorded here rather than only in the ADR («2026-08-23 — Сквозное ревью
> блока A0»), because the spec is binding authority over the plan:
>
> - **§6.2/§7 — "resume re-validates against the current registry" was true only of shape.**
>   `InvocationValidator` checks tool identity, argument schema, output schema and types, and knows
>   nothing about risk; the consent gate read `risk` off the persisted `PlanStep` while reading
>   `permissionGate` and `durability` from the live registry. A plan written when a tool was `SAFE` ran
>   it with **no** `ConsentRequested` after a build raised it to `CONFIRM`. The gate now acts on
>   `maxOf(plan, registry)` — see §6.3.
> - **§4.2/§6.2 — "validate runs at plan time and again before every step" had only the second half.**
>   `StartAgentSessionUseCase` persisted whatever a `Planner` returned. It now validates the plan
>   before writing it, and refuses a 0-step plan (which stays constructible — ADR 4/4 makes it a real
>   future shape).
> - **§9 — `Completed` is not always a completion.** A step skipped by its precondition is a normal part
>   of a `Completed` run (§3), but it is *not* a step that ran, and a failed step under the default
>   budget also ends the plan `Completed`. Both were drawn as success. See §9.
> - **§6.5 — the mid-step signal was unreadable on the only path that produces it.** The restore
>   transitions write trace events on top of the pending `ToolInvoked`, so a predicate keyed on the
>   trace *tail* missed it and the step was re-cleared and re-run. See §6.5.
>
> **§12 (device acceptance) is affected and says so:** items 5 and 8 must be re-run, because the fixes
> change what the owner accepted on 2026-08-22.
>
> **Governing sources:** `docs/governing/sidr-agentic-master-plan-v1.0.md` §3.1 (block A0, its exit
> list, acceptance and doctrine debts), §4 (agentic-block DoD), §5 (change-control);
> `docs/superpowers/plans/2026-08-18-agentic-track-restart.md` §"Решения владельца", §"Новое правило
> вместо rule-first", §"Правило роста", section "Этап 4", §HANDOFF; `docs/governing/sidr-doctrine-matrix-v1.0.md`
> (`DOC-ADL-3`, `DOC-ILM-3`, `DOC-HMA-2`, `DOC-HMA-3`); `CLAUDE.md` Hard rules; the 2026-07-11 A4 spec
> (`2026-07-11-a4-agent-runtime-design.md`) as the pre-ADR baseline this one narrows and corrects.
>
> **Relationship to the 2026-07-11 A4 spec.** That document is the full runtime; this one is the thin
> slice under it. Three of its statements are **superseded** here: (a) its planner ordering prose
> ("rule/template planner runs before model planner", written pre-ADR 1/4) is replaced by the new rule -
> understanding belongs to the model, and the deterministic path is the plan cache, not a filter;
> (b) its DAG-shaped `ExecutionPlan` is out of scope - A0 is ordered-only; (c) its nine session states
> are reduced to the eight A0 can actually produce.
>
> **Hard rules this block obeys.** `:domain` stays stdlib + coroutines and compiles for both KMP
> targets (production in `commonMain`, tests in `jvmTest`). Interfaces in `domain`, implementations in
> `data/*`. No `feature -> feature` edge. Repository/use-case ops return `OperationResult`. No
> user-facing text originates in `domain` or in a ViewModel. `ActionIds`' seven values are untouched.
> Nothing the planner proposes executes without passing the deterministic gates. The launcher core
> keeps working fully offline.

## 1. Goal

Give SIDR a bounded, fail-closed, traceable agent loop that can carry one goal across **two** tool
calls, where the second call depends on what the first one **observed**, and where the risk transition
between them stops the loop for human consent.

Everything in A0 exists to make that sentence true and testable. Anything that does not serve it is a
non-goal (§13), including capabilities that are obviously coming later.

## 2. Owner-resolved forks

Five forks were raised before any code, per the track's forks-before-code rule. All five were answered
by the owner on 2026-08-20.

| # | Fork | Decision |
|---|---|---|
| F1 | Which real two-step goal proves the engine | "Open X" -> X is not installed -> offer the store. `launch_app` (SAFE) -> observation `APP_NOT_INSTALLED` -> `play_store_search` (CONFIRM). Both tools come from the seven frozen `ActionIds`. |
| F2 | How two tools reach the registry without deciding the A1 fork | `domain/tool/` gets its **own** `ToolId`/`ToolDescriptor`. The registry is a list of sources; A0 has exactly one - a projection of `ActionCatalog` into two descriptors. The projection lives in the adapter, not in the type system, so A1' may either add sources or discard the projection. |
| F3 | Who builds the plan in A0 | A deterministic `TemplatePlanner` over the **goal shape** - the seed of the learned-plan cache (rule 2 of the new rule). The model planner is A4'. This requires amending `DOC-ADL-3` (§8.1). |
| F4 | Where the agent branch cuts into `RouteCommandUseCase` | **Before** the `localOnlyMode` check - the agent runs in every state, including local-only and offline. The signed Class B toggle copy promises only "nothing leaves this device", which a local planner does not breach, so that string is **not** re-signed. |
| F5 | What survives process death, where it lives, when it is deleted | The whole active session - goal, plan, observations, consents, trace - in Room (migration 3 -> 4), deleted by cascade on any terminal state. |
| F6 | Can a step consume what a previous step produced | **Yes - laid now, before the migration.** Tools declare an `outputSchema` and return a `ToolOutput`; a step's arguments are `ArgSource.Literal` or `ArgSource.FromStep(index, key)`, resolved by the engine and type-checked by the validator. Raised and answered 2026-08-21, after F1-F5. |

Two further decisions were taken by the agent and approved with the design sections rather than as
forks: the execution surface lives on Home and not in a PREVIEW tab (§9), and the
`LauncherViewModel` split is a behaviour-preserving refactor that ships first (§10).

**Why F6 is here rather than in A4'.** It is the one item of the 2026-08-21 revision that could not
wait for its own block. Today `ToolResult.Observed` carries a two-valued enum and `ToolInvocation.args`
carries literal strings, so carrying a value from step 0 to step 1 is not "unimplemented" - it is
**unexpressible in the types**. Every plan the engine can hold is therefore a fallback chain, never a
composition. The cost of fixing that is one contract edit today and a Room migration 4 -> 5 plus a
persisted-trace conversion the moment §7 ships. It also stops being optional at A0.5, where PC tools
return values as a matter of course.

## 3. The proof goal

```text
goal:  "открой убер"          (Uber is not installed)
  |
  v  TemplatePlanner
ExecutionPlan - 2 steps, both declared up front (this is not a re-plan)
  step 0  launch_app(query = Literal("убер"))          risk SAFE     precondition None
  step 1  play_store_search(query = FromStep(0,        risk CONFIRM  precondition PreviousStepObserved(
                            "resolved_query"))                         APP_NOT_INSTALLED)
  |
  v
gate   step 0 is SAFE and does not raise risk        -> runs
tool   launch_app -> IntentActionResolver finds no match
       -> ToolResult.Observed(APP_NOT_INSTALLED, output = { resolved_query: "убер" })
observe step 1's precondition is SATISFIED, and its `query` binds to step 0's `resolved_query`
gate   risk rises SAFE -> CONFIRM                    -> ConsentCheckpoint
         session state AwaitingConsent - the loop is STOPPED
  |
  v  owner confirms
tool   play_store_search -> ToolResult.Effected
result Completed (2 steps executed)
trace  every step present, in order
```

**The same plan when the app *is* installed:** step 0 launches it and observes `Effected`; step 1's
precondition is unsatisfied, so the step is **skipped** and recorded as `StepSkipped`; the session ends
`Completed` with one executed step. This is a precondition, not a re-plan, and it is deliberately not
`PartiallyCompleted` (§4.4). The unresolved binding is never evaluated, because a skipped step is never
resolved.

**Why the binding is real work and not decoration (F6).** Before the amendment the planner wrote the
same literal `"убер"` into both steps, so the two steps could silently disagree about what was being
searched for - the plan carried the query twice and nothing tied the copies together. With the binding,
step 1 searches for **exactly what step 0 failed to find**, because it is the same value and not a
second copy of it. That is the smallest honest demonstration of data flow available inside A0's two
tools, and it costs no third tool and no widening of scope: `launch_app` already knows the query it
resolved against and currently discards it.

Today, without A0, this command ends at "Приложение «убер» не найдено" and nothing else happens.

## 4. Domain contracts

### 4.1 `domain/tool/`

The tool vocabulary. It carries identity and metadata only - **no user-facing copy**; the feature layer
maps `ToolId` to a localized string. `ActionArg`, `ArgType`, `ActionRiskLevel` and `PermissionFeature`
are **reused unchanged** from `domain/action/` and `domain/permission/` rather than duplicated.

```kotlin
@JvmInline value class ToolId(val value: String)

/**
 * How much of a footprint a tool leaves behind. [DURABLE] is the "irreversible steps are marked BEFORE
 * execution" half of `DOC-HMA-3` without claiming the machinery. Both A0 tools are [TRANSIENT].
 */
enum class ToolDurability { TRANSIENT, DURABLE }

data class ToolDescriptor(
    val id: ToolId,
    val argSchema: List<ActionArg> = emptyList(),
    /**
     * What this tool can hand to a later step (F6). Declared, not inferred: a step may only bind to a
     * key that appears here, so the validator can reject a bad binding at plan time instead of
     * discovering it mid-run. Empty for a tool that produces nothing.
     *
     * `ActionArg` is reused for outputs as well as inputs, so one type describes both ends of a
     * binding and the type check is a comparison rather than a mapping.
     */
    val outputSchema: List<ActionArg> = emptyList(),
    val risk: ActionRiskLevel,
    val durability: ToolDurability,             // A0 MARKS only; rollback machinery is A4'
    val permissionGate: PermissionFeature? = null,
)

/**
 * The two tools A0 registers. The strings mirror the frozen `ActionIds` and are repeated rather than
 * imported, so `domain/tool` and `domain/agent` never reference the action vocabulary - the absent edge
 * that keeps the A1 fork genuinely open. `ToolIdsTest` pins them against `ActionIds` so they cannot
 * drift silently.
 */
object ToolIds {
    val LAUNCH_APP = ToolId("launch_app")
    val PLAY_STORE_SEARCH = ToolId("play_store_search")
}

/**
 * Where one argument's value comes from (F6). A plan is written in terms of [ArgSource]; only the
 * engine ever holds concrete values, and only for the step it is about to run.
 */
sealed interface ArgSource {
    data class Literal(val value: String) : ArgSource
    /** The value produced by the step at [stepIndex] under [key]. [stepIndex] must be strictly earlier. */
    data class FromStep(val stepIndex: Int, val key: String) : ArgSource
}

/** What a plan says to call. Arguments may still be unresolved references. */
data class ToolInvocation(val id: ToolId, val args: Map<String, ArgSource> = emptyMap())

/**
 * What is actually called. Every argument is a concrete value, so the executor **cannot** be handed an
 * unresolved reference - that is a compile-time property, not a convention. `ToolExecutor` takes this
 * type and never [ToolInvocation].
 */
data class ResolvedInvocation(val id: ToolId, val args: Map<String, String> = emptyMap())

/**
 * What a tool produced. Keys must appear in the tool's [ToolDescriptor.outputSchema]. Values are
 * opaque strings in A0 - richer types wait for a real consumer, exactly as `ArgType` already does for
 * inputs.
 */
data class ToolOutput(val values: Map<String, String> = emptyMap())

enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }

sealed interface ToolResult {
    /** The tool performed its side effect, and may have produced values for later steps. */
    data class Effected(val output: ToolOutput = ToolOutput()) : ToolResult
    /** The tool ran and reported a fact; nothing changed on the device. */
    data class Observed(val fact: ObservedFact, val output: ToolOutput = ToolOutput()) : ToolResult
    /** Technical failure; [failure] is safe to display (no PII/stack). Produces no output by construction. */
    data class Failed(val failure: CommandFailure) : ToolResult
}

/** Port: the registered tools. A0 registers one source; A1' federates. */
interface ToolRegistry {
    fun all(): List<ToolDescriptor>
    fun find(id: ToolId): ToolDescriptor?
}

/** Port: the ONLY path from the agent to the world. */
interface ToolExecutor {
    suspend fun invoke(invocation: ResolvedInvocation): ToolResult
}
```

`ToolResult` is the load-bearing piece, and F6 is what makes it load-bearing twice over. `Observed`
turns "app not found" from a dead end into an **observation** the next step can depend on; `ToolOutput`
turns that observation into a **value** the next step can consume. With only the first, every plan the
engine can express is a fallback chain - "if this failed, try that" - and the chosen goal is two
unrelated commands sharing a copied literal. With both, a plan can compose.

**Two tools A0 registers, with their schemas:**

| Tool | `argSchema` | `outputSchema` | risk | durability |
|---|---|---|---|---|
| `launch_app` | `query` (required) | `resolved_query` | SAFE | TRANSIENT |
| `play_store_search` | `query` (required) | - | CONFIRM | TRANSIENT |

`launch_app` emits `resolved_query` on **every** result, including `Effected`: a tool's outputs are a
property of the tool, not of the branch it happened to take, and a schema that only sometimes holds is
not a schema. Nothing consumes it on the `Effected` path in A0, and that is fine - an unused declared
output is not the same liability as an unused `ContextSnapshot` field (Master Plan §3.4), because it
crosses no privacy boundary and costs no permission.

### 4.2 `domain/agent/`

```kotlin
/**
 * What the deterministic layer already recognised about the goal. A0 has exactly one shape; the `when`
 * over it in the planner is exhaustive, so a second shape later forces a deliberate decision.
 */
sealed interface GoalShape {
    /** FastPath resolved the command to an app launch and found no such app installed. */
    data class AppNotInstalled(val query: String) : GoalShape
}

/**
 * [text] is the raw command, kept for the surface and for A4''s model planner. [shape] is what the cut
 * site already knew, so the planner never re-parses text the deterministic layer had understood.
 */
data class AgentGoal(val text: String, val shape: GoalShape)

sealed interface StepPrecondition {
    data object None : StepPrecondition
    data class PreviousStepObserved(val fact: ObservedFact) : StepPrecondition
}

/** Typed provenance - why this step exists. Rendered to text by the feature layer. */
enum class StepRationale { GOAL_DIRECT, APP_NOT_INSTALLED_FALLBACK }

data class PlanStep(
    val index: Int,
    val invocation: ToolInvocation,
    val risk: ActionRiskLevel,
    val precondition: StepPrecondition,
    val rationale: StepRationale,
)

/** Ordered and bounded. NOT a DAG in A0. */
data class ExecutionPlan(val steps: List<PlanStep>)

interface Planner {
    suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult
}

sealed interface PlanningResult {
    data class Planned(val plan: ExecutionPlan) : PlanningResult
    data object NoPlan : PlanningResult
}

data class RuntimeBudget(val maxSteps: Int, val maxConsecutiveFailures: Int)

data class ConsentCheckpoint(val stepIndex: Int, val reason: ConsentReason)
enum class ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, DURABLE_EFFECT }

/**
 * Fail-closed argument validation, modeled on `ProposalValidator`. Pure, stdlib-only, and deliberately
 * free of every `domain/agent` type: the layering is agent -> tool, and F6 must not invert it.
 */
object InvocationValidator {

    /**
     * Shape check. Needs no observations, so it runs at plan time **and** again before every step,
     * including after a resume.
     *
     * @param precedingTools the tool id of each earlier step, position == step index. Its size *is*
     *   this step's index (`ExecutionPlan` pins `index == position`), so a `FromStep` naming an index
     *   outside it is a forward or out-of-range reference and is rejected without a special case.
     */
    fun validate(
        invocation: ToolInvocation,
        precedingTools: List<ToolId>,
        registry: ToolRegistry,
    ): InvocationCheck

    /** Binding. Turns every [ArgSource] into a value or fails closed; the only producer of [ResolvedInvocation]. */
    fun resolve(invocation: ToolInvocation, observations: Map<Int, ToolResult>): ResolutionResult
}

sealed interface InvocationCheck {
    data object Valid : InvocationCheck
    data class Rejected(val reason: RejectionReason) : InvocationCheck
}

sealed interface ResolutionResult {
    data class Resolved(val invocation: ResolvedInvocation) : ResolutionResult
    data class Rejected(val reason: RejectionReason) : ResolutionResult
}

enum class RejectionReason {
    UNKNOWN_TOOL, UNDECLARED_ARG, MISSING_REQUIRED_ARG,
    /** A `FromStep` naming this step, a later one, or an index the plan does not have. */
    FORWARD_ARG_SOURCE,
    /** A `FromStep` whose key is not in the source tool's declared `outputSchema`. */
    UNDECLARED_OUTPUT,
    /** The source step ran but produced no value under that key - e.g. it Failed. Run-time only. */
    UNRESOLVED_ARG_SOURCE,
}
```

### 4.3 `domain/trace/`

```kotlin
data class ExecutionTrace(val events: List<TraceEvent>)   // ordered, append-only

sealed interface TraceEvent {
    data class PlanCreated(val stepCount: Int) : TraceEvent
    data class StepStarted(val index: Int) : TraceEvent
    data class StepSkipped(val index: Int, val precondition: StepPrecondition) : TraceEvent
    data class StepRejected(val index: Int, val reason: RejectionReason) : TraceEvent
    data class ConsentRequested(val index: Int, val reason: ConsentReason) : TraceEvent
    data class ConsentResolved(val index: Int, val granted: Boolean) : TraceEvent
    data class ToolInvoked(val index: Int, val toolId: ToolId) : TraceEvent
    data class ToolObserved(val index: Int, val result: ToolResult) : TraceEvent
    data object SessionPaused : TraceEvent
    data object SessionResumed : TraceEvent
    data class SessionEnded(val state: ExecutionState) : TraceEvent
}
```

The goal text is stored **once**, on the session, never repeated per event. Events carry **no
timestamp**: `commonMain` has no clock across two targets, so the data layer stamps rows when it
persists them — the same reason the wall-clock budget is honestly deferred to A4'.

### 4.4 Session states - eight, not nine

```kotlin
enum class ExecutionState {
    Planning, Running, AwaitingConsent, Paused, Completed, Cancelled, Failed, Blocked
}

@JvmInline value class AgentSessionId(val value: String)

/** Port: `commonMain` has no UUID API, and tests must be deterministic. */
interface AgentSessionIdFactory { fun newId(): AgentSessionId }

data class AgentSession(
    val id: AgentSessionId,
    val goal: AgentGoal,
    val plan: ExecutionPlan,
    val cursor: Int,                 // index of the next step to consider
    val state: ExecutionState,
    val observations: Map<Int, ToolResult>,
    val consents: Map<Int, Boolean>,
    val trace: ExecutionTrace,
)
```

`PartiallyCompleted` from the 2026-07-11 A4 spec is **not introduced**. Without re-planning A0 cannot
produce it, and a state the engine cannot reach is a lie in the type. A step skipped by an unsatisfied
precondition is a normal part of a `Completed` run.

`AgentSession` is an immutable snapshot (id, goal, plan, cursor, state, consent decisions, trace);
`AgentExecutor` is the engine over it. Keeping state as a value and behaviour as a function is what
makes the loop testable without coroutine timing.

### 4.5 Use cases and who turns the crank

`AgentExecutor.advance` performs one transition and is pure. Someone has to loop it, persist between
transitions, and be interruptible. That someone is four small use cases in `domain`, each returning
`OperationResult`:

| Use case | Responsibility |
|---|---|
| `StartAgentSessionUseCase` | goal -> `Planner` -> `PlanningResult`; on `Planned`, persist a new session and hand back its `AgentSessionId`. `NoPlan` means the caller keeps the FastPath outcome untouched. |
| `RunAgentSessionUseCase` | loops `advance` + persist until the session reaches `AwaitingConsent` or a terminal state. Re-validates the plan against the registry on entry, which is what makes resume safe. |
| `ResolveConsentUseCase` | records granted/denied for one checkpoint through the conditional write of §7, then re-enters `RunAgentSessionUseCase`. Idempotent: a second call for the same step is a no-op. |
| `CancelAgentSessionUseCase` | moves the session to `Cancelled` and deletes it; a cancel arriving between transitions guarantees the next step never starts. |

Splitting start / run / consent / cancel this way is what keeps the engine free of coroutine timing:
tests drive whole use cases, and the only concurrency question left - two confirmations racing - is
answered by the database rather than by the code (§7).

## 5. Entry point

`RouteCommandUseCase` gains one step, immediately after FastPath:

```text
1  FastPath (unchanged, runs exactly once, owns its recording side effect)
2  NEW: outcome is Message(NoAppFound(query))  =>  AgentGoal  =>  StartAgentSessionUseCase
3  localOnlyMode        => honest message (unchanged)
4  FastPath decided     => untouched outcome (unchanged)
5  no provider          => UnderstandingNeedsProvider (unchanged)
6  offline              => UnderstandingNeedsNetwork (unchanged)
7  model planner        => as today
```

`CommandOutcome` gains `AgentSessionStarted(id: AgentSessionId)`.

**Where the cut sits in the real pipeline.** `RouteCommandUseCase` is not what the ViewModel calls; it
is the innermost link of a chain built in `MemoryProvidesModule`:

```text
LauncherViewModel
  -> ResolveCommandWithAliasUseCase          (S2-2 - acts only on Unknown)
     -> ResolveCommandWithPreferenceUseCase  (S2-1 - acts only on app ambiguity)
        -> CommandRouteStep = RouteCommandUseCase.route
           -> HandleUserCommandUseCase       (FastPath)
```

Cutting in at the innermost link is safe, and for a checkable reason rather than an assumption: neither
decorator acts on a `CommandOutcome.Message`. The alias decorator fills `Unknown` only; the preference
decorator resolves app ambiguity only. `Message(NoAppFound)` passes through both untouched today, so
relocating that one outcome changes nothing about learned resolutions or aliases - and a test pins this
rather than leaving it to a comment.

**Which consumers the compiler will catch, and which it will not.** The two decorators do **not** match
exhaustively over `CommandOutcome`; each passes anything it does not recognise straight through by an
early return (`if (outcome !is NeedsConfirmation) return ...` and `as? Unknown ?: return resolved`).
That is the behaviour A0 wants, but it is **silent** - adding a variant compiles clean and a future
edit could break the pass-through without any compiler complaint. It is therefore pinned by a test
(§11), not left to the shape of the code.

`LauncherViewModel.applyOutcome` is the opposite case: an exhaustive `when` with no `else`. Adding
`AgentSessionStarted` fails compilation there until a real branch exists, which is exactly the site
where a silent default would be wrong.

**The cut is narrow by construction.** The only FastPath outcome that opens the agent branch in A0 is
`CommandMessage.NoAppFound` - not "any `Message`", not "anything that did not execute". Widening that
list is a separate decision for a later block, and the mutual-exclusivity property of steps 3/5/6 that
`§HANDOFF` warns about is untouched, because the new branch is an early return placed by its own
externally-visible condition, not a fourth "reason understanding is unavailable".

The three existing understanding-unavailable messages and their `all three causes at once` test keep
their current behaviour and ordering.

## 6. Execution and the seven boundaries

```kotlin
class AgentExecutor(
    private val registry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val validator: InvocationValidator,
    private val budget: RuntimeBudget,
) {
    suspend fun advance(session: AgentSession): AgentSession   // EXACTLY one transition
}
```

`advance`, not `run`. The caller loops until the session reaches a state that needs the outside world
(`AwaitingConsent`) or a terminal state. Two acceptance requirements fall out for free: cancellation is
checked **between** transitions, so the next step cannot start; and tests drive the machine without
coroutine timing.

Per the growth rule, all seven boundaries are laid on this first slice, for two tools:

**1. The registry is the only path to the world.** `AgentExecutor` knows nothing of `LauncherAction`,
`ExecutableAction`, `Intent`, or the word "Play Store" - only `toolExecutor.invoke(ResolvedInvocation)`.
This is what keeps the owner's requirement open: when A1' registers F-Droid, Galaxy Store or a
vendor site as tools, the engine does not change by a line. Guard: `domain/agent/` must not reference
`ActionId` / `LauncherAction` / `ExecutableAction`.

**2. Argument validation, fail-closed, modeled on `ProposalValidator`.** Unknown `ToolId`, an arg
outside the declared schema, or a missing/blank required arg => the step does not run, the session goes
`Failed`, the trace records why. Validation runs **before every step**, not once at planning time: a
plan can outlive a process restart, and by the time it resumes the app may have been uninstalled or a
permission revoked. A plan valid yesterday is not valid today.

F6 splits this boundary into two phases, and the split is what keeps it fail-closed rather than
merely more capable:

- **Shape (`validate`)** - everything checkable without having run anything: the three original
  reasons, plus `FORWARD_ARG_SOURCE` (a binding that names this step, a later one, or an index the
  plan does not have) and `UNDECLARED_OUTPUT` (a binding to a key the source tool never promised).
  Both are **static defects in the plan itself**, so they are caught before the first step runs and
  again on every resume - a plan restored against a build whose tool no longer declares that output
  now fails here instead of half-executing.
- **Binding (`resolve`)** - the only producer of `ResolvedInvocation`, and therefore the only way a
  value reaches the executor. A source step that ran but produced nothing under that key (it
  `Failed`, or the adapter returned an empty output) is `UNRESOLVED_ARG_SOURCE`: the step does not
  run. **Nothing is substituted, defaulted, or left blank** - an agent that silently searches for an
  empty string is worse than one that stops and says why.

**Binding runs in `prepare`, before `ToolInvoked` is recorded - not in `perform`.** `perform` runs only
on a session that is already mid-step, and "`ToolInvoked(i)` with no `ToolObserved(i)`" has exactly one
meaning in this design: the process died during the call, so the step must not be re-run and the session
comes back `Paused` (§6.5, §7). A rejection raised after that event would counterfeit that shape without
a process ever having died, and `DOC-ILM-3`'s "the trace is 1:1 with reality" would be false. `perform`
re-resolves only to obtain the value; that is deterministic and free, because `resolve` is a pure
function of the invocation and the observations, and observations cannot change between the `prepare`
and the `perform` of one step.

The type system carries the guarantee rather than a convention: `ToolExecutor.invoke` accepts only
`ResolvedInvocation`, and `resolve` is the only function that constructs one. An unresolved reference
therefore cannot reach the world even if a future edit forgets to check the result - it will not
compile.

The type check between a binding's source and target (`ActionArg.type` on both ends) is written and
tested, and is **vacuous today**: `ArgType` has one value, `STRING`. That is stated rather than
implied, on the same terms as the `DURABLE` gate in boundary 7 - the seam is laid, not demonstrated.

**3. The consent gate has exactly one call site.** `ToolExecutor.invoke` is called from exactly one
place in the whole codebase, and that place sits after the checkpoint. The guard counts call sites and
fails on two — it reads a file name and a count, never a position; "below the checkpoint" is held
behaviourally by `AgentExecutorTest` (corrected 2026-08-22, and again in `CLAUDE.md` 2026-08-23). This is
the mechanical version of "tool #21 gets consent for free" - not because we will remember, but because
there is nowhere to forget. Triggers in A0: `risk >= CONFIRM`; risk **rises** relative to the previous
executed step; the tool **declares** a permission gate; the tool is marked `DURABLE`.

**Which risk the gate acts on (amended 2026-08-23).** `maxOf(PlanStep.risk, registry.risk)`, for the
current step and for the earlier ones the rise is measured against. A plan is a snapshot of the build
that wrote it; the registry is the current truth; neither alone is safe, and `maxOf` settles it without
an argument about which is more authoritative. A tool the registry does not know counts as `DANGEROUS`
— unreachable while `validate` runs first, and written that way so the *default* is a stop rather than
a pass. The permission trigger fires on the declaration rather than on the real grant state: reading
the grant state would put `PermissionChecker` inside the engine, and stopping unconditionally is the
fail-safe half of that. Neither A0 tool declares a gate.

**4. Loop bounds.** `RuntimeBudget(maxSteps, maxConsecutiveFailures)`. Exhaustion maps to `Blocked`,
never to a silent stop. **Named gap:** a wall-clock limit needs a clock port, and `:domain` is
stdlib + coroutines across two KMP targets; it belongs to A4' and A0 does not pretend otherwise.

**5. Trace - `DOC-ILM-3`.** `ToolInvoked` is written **before** the call, `ToolObserved` after, in the
same transaction that moves the cursor. Consequence, which is correct behaviour rather than a defect:
if the process dies between the write and the call, resume sees `ToolInvoked` with no result, and that
step is **not** re-executed automatically - the session sits in `Paused` and asks, and when the user says
yes the **pending call is performed**, not re-issued. Guard: every executed step has both events.

**Amended 2026-08-23.** "Resume sees `ToolInvoked` with no result" is read from the last `ToolInvoked`,
not from the last trace *event*: the restore path (`pausedForRestore()` then `resumed()`) writes
`SessionPaused` and `SessionResumed` **on top of** the pending event, so a tail-keyed predicate missed
the one shape it exists to recognise, and the step was re-cleared, re-traced and re-run. Tests that
simulate the restart by calling `advance` directly do not exercise this; `LauncherAgentSessionTest`
walks the real path.

**6. Egress allow-list, as a checkable absence.** The A0 agent has no path off the device at all: the
planner is local and both tools are system intents. The boundary is laid as a test - `domain/agent/`
and `domain/tool/` depend on no transport, no `GenerativeAiEngine` and no `CommandPlanner`, and a
sentinel planted in the goal text appears in no outbound channel. When the model planner arrives in A4',
it must pass through `OutboundContextPolicy`.

**7. Rollback.** A0 **marks** `durability` and refuses to run a `DURABLE` step without explicit
consent. Both A0 tools are `TRANSIENT`, so the trigger is unit-tested rather than exercised in the
slice — the boundary is laid, not demonstrated. There is no compensation machinery, and `DOC-HMA-3` is **not** claimed as closed.

## 7. Persistence and resume

`AgentSessionStore` is a port in `domain`; the Room implementation lives in `data/repository`.
Migration 3 -> 4, exported schema `4.json`, following `Migration1To2` / `Migration2To3` and the
existing `MigrationTest`.

```text
agent_session      0 or 1 row - id, goal_text, goal_shape, goal_shape_arg, state, cursor, created_at
agent_plan_step    session_id, step_index, tool_id, args_json, risk, precondition_fact,
                   rationale, observation_type, observation_fact, observation_output_json,
                   consent
agent_trace_event  session_id, seq, type, step_index, detail, at
                   (both children ON DELETE CASCADE)
```

- **`goal_shape_arg`, not `goal_query`** (renamed 2026-08-21, during Task 10). It carries the single
  argument of `goal_shape` — for `AppNotInstalled`, the app name the command named. `query` is a
  forbidden term in `RoomColumnNamesGuardTest`'s denylist, and the only way to keep the spec's original
  name was a second entry in `APPROVED_SENSITIVE_COLUMNS`, which would have widened the database's one
  owner-granted exemption to two. The name is also simply more accurate. Do not rename it back.
- **Deletion is part of the contract.** Any terminal state (`Completed` / `Failed` / `Cancelled`)
  deletes the session by cascade. At rest, with no run in flight, the three tables are empty - the
  command text you typed lives on disk only while its plan is unfinished. This is a recovery record,
  not a journal; the persistent trace journal is A5's decision, and the 2026-07-11 A4 spec's non-goal
  "no hidden persistent trace journal" is respected.
- **Resume is idempotent by write, not by flag.** The transition is applied as
  `UPDATE ... WHERE status = 'PENDING'`. A second confirmation finds nothing to update and is a no-op.
  Two fast taps cannot diverge. Whole-object writes were deliberately avoided here: that pattern
  already cost this project the `autoHideNavBar` bug (DS-11), which only surfaced on device.
- **Resume re-validates.** Before continuing, the surviving plan is re-checked against the current
  registry (§6.2). A session that outlived its process is presented as `Paused` and never resumed
  silently — the user asked for this minutes or days ago, and continuing unasked would be the system
  deciding for them.
- **Trace events are flat columns, not a JSON blob**, so A5 ("trace as a surface") can query "the last
  N events" without converting a format.
- **`args_json` stores `ArgSource`, not values (F6).** A `Literal` persists its string; a `FromStep`
  persists its index and key. This is what makes a resumed plan re-bind against the observations that
  actually survived rather than replaying a value captured at plan time - and it is the reason this
  amendment had to land before the migration rather than after it. `observation_output_json` holds the
  producing side of the same pair; a step with no output stores `null`, not `{}`, so "produced nothing"
  and "produced an empty map" stay distinguishable.
- **One named fidelity gap.** A persisted `Failed` observation keeps its type but not the
  `CommandFailure` variant, and restores as `Generic`. In A0 it never round-trips — a failed step ends
  the session, which is then deleted. If A4' starts keeping failed sessions, that column must widen.
  `Failed` carries no output by construction, so nothing is lost on the F6 side of the same row.

## 8. Doctrine

### 8.1 `DOC-ADL-3` - amendment required

Current text: local-only / no provider / offline => the model planner is not consulted, nothing leaves
the device, and **every outcome FastPath decided is returned byte-for-byte**.

`NoAppFound` is an outcome FastPath decided. With F3 and F4 resolved, a deterministic plan replaces it
in all three states, so the last clause becomes false exactly where the rule matters.

The doctrine already contradicts itself here: rule 5 of the new rule reads "local-only / offline /
no-key => FastPath + **the plan cache** + an honest statement of which it is". A0 resolves the
contradiction in the direction the new rule already points, and narrows the parity clause to what it
has always meant in substance:

> **Amended `DOC-ADL-3`:** local-only / no provider / offline => the **model planner** is not consulted
> and nothing leaves the device. Deterministic plan replay is part of the local path (rule 2 of the new
> rule) and may change an outcome FastPath decided; what may never change is that no model is consulted
> and nothing is transmitted.

This is a change-control item: it needs an ADR and a row in the matrix's §6 amendment journal, per the
`§HANDOFF` instruction that a rule's ID survives an amendment and its text does not. `RouteCommandUseCaseTest`
is re-anchored to the amended wording.

The signed Class B string `settings_local_only_description` is **not** touched: it promises "On:
nothing leaves this device", which a local planner does not breach. No re-signing, no new digest.

### 8.2 Debts A0 closes

| Rule | How |
|---|---|
| `DOC-ILM-3` - no step executes without a trace entry; the trace is 1:1 with reality | §6.5, guard test over executed steps, and the session/trace persisted together so the property survives resume |
| `DOC-HMA-2` - a risk transition or tool-level change stops the loop until explicit consent | §6.3, `ConsentReason.RISK_RAISED`, device acceptance item 2 |

Both are closed **in the scope of one slice**, which is exactly what Master Plan §3.1 asks. Their matrix
rows get real test names.

### 8.3 Debts A0 explicitly does not close

`DOC-HMA-3` (rollback and compensation), `DOC-ILM-2` (tool levels and provenance), `DOC-NYH-3`
(multi-turn clarification), `DOC-HMA-4` (execution model / foreground service). They stay named debts of
A1' and A4'.

## 9. Surface

**Where:** Home, inside the existing command-feedback area. The PREVIEW tabs (Tasks / Agents /
Activity / Terminal) are **not** touched - turning a PREVIEW surface live before its own block is a
change-control item requiring an ADR (Master Plan §5).

**From what:** DS-5 primitives only. **Nothing new in `core/ui`.**

| State | Primitive |
|---|---|
| `Planning` | `SidrProgress` |
| `Running` | step list, current one marked |
| `AwaitingConsent` | `SidrActionGate` |
| `Paused` | offer to continue + what has already happened |
| `Completed` | `SidrResultSurface` — tone `Completed` only when **every** step actually ran; a plan that skipped or failed a step is `Partial` with its own title (amended 2026-08-23) |
| `Failed` | `SidrErrorSurface` |
| `Blocked` | `SidrBlockedState` |
| `Cancelled` | return to the ordinary screen |

Each step in the list carries **the state the session recorded for it** — done / not needed / did not
work / in progress / not started — as a word beside the marker, not as a colour alone (`R-ADL-2`;
amended 2026-08-23, when the marker was derived from the cursor and all three reasons the cursor moves
read as success).

The surface is **a function of state, not of a mockup**: one component per runtime state, composed from
what the session actually reported. A 2-step plan and a 7-step plan differ in list length, not in
layout, so neither a longer plan nor a new state forces a redesign. The drawn Execution Stream from the
audit is an input, not a spec (DS-10 precedent).

Strings ship `en` / `ru` / `tr` in the same commit (`LocaleCompletenessGuardTest`). `domain` emits typed
`StepRationale` / `ObservedFact` / `ConsentReason`; the feature layer chooses the string via
`sidrString` (`HardcodedUiTextGuardTest`, `StringSeamGuardTest`).

## 10. `LauncherViewModel` split - the mandatory preparatory action

886 lines, 13 dependencies, 9 areas of responsibility. The agent runtime would be the tenth. Master
Plan §3.1 calls this Risk 1 and requires the split **before** the runtime, not after.

**Interpretation of the parity clause, stated because it shapes the work order.** Acceptance says
"`git diff` over the existing ViewModel suites is empty (parity)". A0 changes behaviour, so this cannot
hold across the whole block. It is read as a statement about **the split**: the split is a pure
refactor with a zero test diff, and A0's behavioural changes land in later commits with their own tests.

Consequences: `LauncherViewModel` **stays a facade with the same public API** - any signature change
would edit the tests by definition - and the areas move into collaborators inside `feature/launcher`:

```text
LauncherViewModel (facade: navigation + composition)
  |- app list ....... loading, retry, usage-sort, favorites
  |- command session  input, live results, submit, feedback, router proposal,
  |                   learned-resolution token
  |- suggestions .... flag observation, restore/refresh/clear, label resolution
  |- voice
  '- dev console
```

The prayer `StateFlow` is already separate and cold and is not touched. No new module, no
`feature -> feature` edge. The agent runtime is added as a **tenth collaborator afterwards**, not as a
tenth area inside 886 lines. Class names are fixed in the implementation plan.

## 11. Verification

- **Gate:** `./gradlew --no-daemon :domain:jvmTest testDebugUnitTest assembleDebug` under JDK 17,
  output **not** piped through `tail`. `:domain:jvmTest` is listed explicitly because
  `testDebugUnitTest` does **not** reach it and the entire engine lives there (`§HANDOFF`).
  `:core:ui:verifyRoborazziDebug` is run as a check that `core/ui` was genuinely not touched.
- **Unit tests (`:domain:jvmTest`):** planner over goal shape; validator fail-closed cases; precondition
  satisfied / unsatisfied / skipped; risk-transition consent; cancel between transitions; budget
  exhaustion -> `Blocked`; every state transition; trace completeness.
- **F6 tests (`:domain:jvmTest`):** a binding resolves to the producing step's value; `FORWARD_ARG_SOURCE`
  for a self-, later- and out-of-range reference; `UNDECLARED_OUTPUT` for a key absent from the source
  tool's `outputSchema`; `UNRESOLVED_ARG_SOURCE` when the source step `Failed`, and the step does **not**
  run and nothing is substituted; a skipped step never evaluates its binding; the two-step goal end to
  end with `play_store_search` receiving exactly what `launch_app` reported.
- **Data tests:** migration 3 -> 4; cascade delete on each terminal state; idempotent resume under a
  double confirmation; a `FromStep` binding survives a save/restore round trip and re-binds against the
  restored observation rather than a value frozen at plan time.
- **Pipeline tests:** `Message(NoAppFound)` reaches the agent branch; both memory decorators pass
  `AgentSessionStarted` through untouched; alias and learned-resolution behaviour is unchanged for every
  other outcome.
- **Guards:** exactly one `ToolExecutor.invoke` call site; sentinel from the goal text never leaves;
  every executed step has `ToolInvoked` + `ToolObserved`; all three tables empty after a terminal state;
  `domain/agent` does not reference the action vocabulary.
- **The Gradle trap from `§HANDOFF`:** any guard that reads sources outside its own source set must
  declare `inputs.file(...)`, or the task stays `UP-TO-DATE` and the guard silently does not run.
- **Mutation check on every new guard:** deliberately break exactly what the guard should catch and
  confirm it goes red. A green run of a new guard proves nothing on its own.

## 12. Device acceptance (SM-A325F, Android 13)

1. one real two-step goal passes the whole cycle;
2. the consent gate stops execution at the risk transition;
3. cancelling mid-plan does not execute the next step;
4. the trace contains every step;
5. `adb shell am force-stop` mid-plan -> relaunch shows the session `Paused` with an honest offer to
   continue;
6. a double confirmation of resume does not execute a step twice;
7. `git diff` over the existing ViewModel suites is empty (after the split, §10);
8. with the app **installed**, step 1 is skipped by its precondition and the result is `Completed`, not
   partial.

**Items 5 and 8 must be re-run after the 2026-08-23 fix round.** Item 5's Continue now resumes the
pending call instead of re-issuing it (one `ToolInvoked` on disk, not two), and item 8's wording changed:
a plan that skipped a step now reads «План пройден, выполнено не всё» with the store step marked «не
потребовалось». Item 8's original phrasing above ("`Completed`, not partial") was written before the
review and is superseded: a skipped step makes the run partial, and saying so is the point.

**Outstanding debt that lands here.** Этап 4.0 is still `CODE-GREEN`, and `§HANDOFF` forbids declaring a
later block `DEVICE-ACCEPTED` on top of an unverified one. Either 4.0's own check runs in the same
session on the phone (`ru-RU`, no provider configured => the honest message, not `Unknown command`), or
A0 closes as `CODE-GREEN` and the debt gains a second layer. The former is recommended: it is one minute
of device time.

## 13. Non-goals

A3 / A5 / A6 · DAG plans · re-planning · `PartiallyCompleted` · new `core/ui` primitives · turning any
PREVIEW surface live · **richer `ArgType` values than `STRING`, and any binding that transforms rather
than passes a value through** (both wait for a consumer; the seam is the point, not the expressiveness)
· **binding to anything other than a previous step's declared output** — no session variables, no goal
fields, no ambient context · the model planner and its multi-step response schema · tool levels
(`DOC-ILM-2`) · choosing among install sources or registering new sources · widening `ActionIds` ·
foreground service or any background autonomous loop · a wall-clock budget · rollback machinery
(`DOC-HMA-3`) · the A1 fork (parallel tool vocabulary vs. evolving `ActionCatalog` in place) - it
belongs to A1' and a spec rewritten after ADR 2/4, and A0 is built so that both banks stay equally
cheap.

## 14. Work order

1. `LauncherViewModel` split - zero diff on existing tests ✅
2. `domain/tool` + `domain/agent` + `domain/trace` + `AgentExecutor` + `InvocationValidator` + tests ✅
2b. **F6 - step-to-step data flow.** Re-opens item 2's contract: `outputSchema`, `ArgSource`,
   `ResolvedInvocation`, `ToolOutput`, the two-phase validator, and the executor's resolve step,
   with the tests of §11. **Must land before item 5** - after the migration the same change costs
   a migration 4 -> 5 and a persisted-trace conversion. ✅ (landed before item 5, as required)
3. `TemplatePlanner` ✅ (amended by 2b: step 1 binds instead of repeating the literal)
4. `SystemIntentToolSource` + `ToolExecutor` over the **unchanged** `ExecuteActionUseCase` ✅
5. Room 3 -> 4 + `AgentSessionStore` ✅
6. the `RouteCommandUseCase` cut + `CommandOutcome.AgentSessionStarted` + the pass-through branches the
   compiler demands in both memory decorators and in `applyOutcome` ✅
7. surface + `en`/`ru`/`tr` strings ✅
8. guards + mutation check
9. ADR + `DOC-ADL-3` amendment (matrix §6 journal) + `CLAUDE.md` / `ai-context/current-status.md` sync

Each numbered item is at least one commit; the split may be several.

## 15. Success criteria

- The cycle `goal -> plan -> gate -> tool -> observe -> tool -> result -> trace` runs on device.
- **Step 1 acts on what step 0 produced, not on a copy of the same literal (F6)** - and an unresolvable
  binding stops the step instead of substituting a blank. This is the difference between a plan that
  composes and a fallback chain that happens to have two entries.
- Consent is inside the loop, not a wrapper around it: there is one call site to the world and it is
  behind the gate.
- The session survives process death and resumes without executing anything twice.
- The trace is 1:1 with what happened, including across a restart.
- `LauncherViewModel` is split, with the existing suites untouched.
- Nothing accumulates on disk: at rest the agent tables are empty.
- `DOC-ILM-3` and `DOC-HMA-2` have real test names in the matrix; `DOC-ADL-3` carries its amendment row.
- Adding an install source in A1' requires no change inside the engine.
