# A0 - Thin Agentic Spike (Design Spec)

> **Status: PROPOSED (2026-08-20).** A0 is the first vertical slice of the agentic track. One real
> two-step goal passes `goal -> plan -> gate -> tool -> observe -> tool -> result -> trace` and is
> accepted on device. The purpose is to **prove the engine, not to build the layers**.
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

Two further decisions were taken by the agent and approved with the design sections rather than as
forks: the execution surface lives on Home and not in a PREVIEW tab (§9), and the
`LauncherViewModel` split is a behaviour-preserving refactor that ships first (§10).

## 3. The proof goal

```text
goal:  "открой убер"          (Uber is not installed)
  |
  v  TemplatePlanner
ExecutionPlan - 2 steps, both declared up front (this is not a re-plan)
  step 0  launch_app(query="убер")         risk SAFE     precondition None
  step 1  play_store_search(query="убер")  risk CONFIRM  precondition PreviousStepObserved(APP_NOT_INSTALLED)
  |
  v
gate   step 0 is SAFE and does not raise risk        -> runs
tool   launch_app -> IntentActionResolver finds no match -> ToolResult.Observed(APP_NOT_INSTALLED)
observe step 1's precondition is SATISFIED
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
`PartiallyCompleted` (§4.4).

Today, without A0, this command ends at "Приложение «убер» не найдено" and nothing else happens.

## 4. Domain contracts

### 4.1 `domain/tool/`

The tool vocabulary. It carries identity and metadata only - **no user-facing copy**; the feature layer
maps `ToolId` to a localized string. `ActionArg`, `ArgType`, `ActionRiskLevel` and `PermissionFeature`
are **reused unchanged** from `domain/action/` and `domain/permission/` rather than duplicated.

```kotlin
@JvmInline value class ToolId(val value: String)

data class ToolDescriptor(
    val id: ToolId,
    val argSchema: List<ActionArg> = emptyList(),
    val risk: ActionRiskLevel,
    val reversible: Boolean,                    // A0 MARKS only; rollback machinery is A4'
    val permissionGate: PermissionFeature? = null,
)

data class ToolInvocation(val id: ToolId, val args: Map<String, String>)

enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }

sealed interface ToolResult {
    /** The tool performed its side effect. */
    data object Effected : ToolResult
    /** The tool ran and reported a fact; nothing changed on the device. */
    data class Observed(val fact: ObservedFact) : ToolResult
    /** Technical failure; [failure] is safe to display (no PII/stack). */
    data class Failed(val failure: CommandFailure) : ToolResult
}

/** Port: the registered tools. A0 registers one source; A1' federates. */
interface ToolRegistry {
    fun all(): List<ToolDescriptor>
    fun find(id: ToolId): ToolDescriptor?
}

/** Port: the ONLY path from the agent to the world. */
interface ToolExecutor {
    suspend fun invoke(invocation: ToolInvocation): ToolResult
}
```

`ToolResult.Observed` is the load-bearing piece: it is what turns "app not found" from a dead end into
an **observation** the next step can depend on. Without it the chosen goal is two unrelated commands.

### 4.2 `domain/agent/`

```kotlin
data class AgentGoal(val text: String)

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
enum class ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, IRREVERSIBLE }

/** Fail-closed argument validation, modeled on `ProposalValidator`. Pure, stdlib-only. */
object InvocationValidator {
    fun validate(invocation: ToolInvocation, registry: ToolRegistry): InvocationCheck
}
sealed interface InvocationCheck {
    data object Valid : InvocationCheck
    data class Rejected(val reason: RejectionReason) : InvocationCheck
}
enum class RejectionReason { UNKNOWN_TOOL, UNDECLARED_ARG, MISSING_REQUIRED_ARG }
```

### 4.3 `domain/trace/`

```kotlin
data class ExecutionTrace(val events: List<TraceEvent>)   // ordered, append-only

sealed interface TraceEvent {
    data class PlanCreated(val stepCount: Int) : TraceEvent
    data class StepStarted(val index: Int) : TraceEvent
    data class StepSkipped(val index: Int, val precondition: StepPrecondition) : TraceEvent
    data class ConsentRequested(val index: Int, val reason: ConsentReason) : TraceEvent
    data class ConsentResolved(val index: Int, val granted: Boolean) : TraceEvent
    data class ToolInvoked(val index: Int, val toolId: ToolId) : TraceEvent
    data class ToolObserved(val index: Int, val result: ToolResult) : TraceEvent
    data object SessionPaused : TraceEvent
    data object SessionResumed : TraceEvent
    data class SessionEnded(val state: ExecutionState) : TraceEvent
}
```

The goal text is stored **once**, on the session, never repeated per event.

### 4.4 Session states - eight, not nine

```kotlin
enum class ExecutionState {
    Planning, Running, AwaitingConsent, Paused, Completed, Cancelled, Failed, Blocked
}

@JvmInline value class AgentSessionId(val value: String)

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

**The new variant is enumerated by the compiler, not by us.** `CommandOutcome` is consumed by exhaustive
`when` expressions with no `else` - in the decorators and in `LauncherViewModel.applyOutcome`. Adding
`AgentSessionStarted` therefore fails compilation at every site that must decide what to do with it,
which is the desired behaviour: pass-through in the decorators, a real branch in the ViewModel.

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
`ExecutableAction`, `Intent`, or the word "Play Store" - only `toolExecutor.invoke(ToolInvocation)`.
This is what keeps the owner's requirement open: when A1' registers F-Droid, Galaxy Store or a
vendor site as tools, the engine does not change by a line. Guard: `domain/agent/` must not reference
`ActionId` / `LauncherAction` / `ExecutableAction`.

**2. Argument validation, fail-closed, modeled on `ProposalValidator`.** Unknown `ToolId`, an arg
outside the declared schema, or a missing/blank required arg => the step does not run, the session goes
`Failed`, the trace records why. Validation runs **before every step**, not once at planning time: a
plan can outlive a process restart, and by the time it resumes the app may have been uninstalled or a
permission revoked. A plan valid yesterday is not valid today.

**3. The consent gate has exactly one call site.** `ToolExecutor.invoke` is called from exactly one
place in the whole codebase, and that place sits after the checkpoint. The guard counts call sites and
fails on two. This is the mechanical version of "tool #21 gets consent for free" - not because we will
remember, but because there is nowhere to forget. Triggers in A0: `risk >= CONFIRM`; risk **rises**
relative to the previous executed step; a required permission is missing; the step is marked
irreversible.

**4. Loop bounds.** `RuntimeBudget(maxSteps, maxConsecutiveFailures)`. Exhaustion maps to `Blocked`,
never to a silent stop. **Named gap:** a wall-clock limit needs a clock port, and `:domain` is
stdlib + coroutines across two KMP targets; it belongs to A4' and A0 does not pretend otherwise.

**5. Trace - `DOC-ILM-3`.** `ToolInvoked` is written **before** the call, `ToolObserved` after, in the
same transaction that moves the cursor. Consequence, which is correct behaviour rather than a defect:
if the process dies between the write and the call, resume sees `ToolInvoked` with no result, and that
step is **not** re-executed automatically - the session sits in `Paused` and asks. Guard: every executed
step has both events.

**6. Egress allow-list, as a checkable absence.** The A0 agent has no path off the device at all: the
planner is local and both tools are system intents. The boundary is laid as a test - `domain/agent/`
and `domain/tool/` depend on no transport, no `GenerativeAiEngine` and no `CommandPlanner`, and a
sentinel planted in the goal text appears in no outbound channel. When the model planner arrives in A4',
it must pass through `OutboundContextPolicy`.

**7. Rollback.** A0 **marks** `reversible` and refuses to run an irreversible step without explicit
consent. There is no compensation machinery, and `DOC-HMA-3` is **not** claimed as closed.

## 7. Persistence and resume

`AgentSessionStore` is a port in `domain`; the Room implementation lives in `data/repository`.
Migration 3 -> 4, exported schema `4.json`, following `Migration1To2` / `Migration2To3` and the
existing `MigrationTest`.

```text
agent_session      0 or 1 row - id, goal_text, state, cursor, created_at
agent_plan_step    session_id, index, tool_id, args_json, risk, precondition, status, observation
agent_trace_event  session_id, seq, type, payload, at
                   (both children ON DELETE CASCADE)
```

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
  registry (§6.2).

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
| `Completed` | `SidrResultSurface` |
| `Failed` | `SidrErrorSurface` |
| `Blocked` | `SidrBlockedState` |
| `Cancelled` | return to the ordinary screen |

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
- **Data tests:** migration 3 -> 4; cascade delete on each terminal state; idempotent resume under a
  double confirmation.
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

**Outstanding debt that lands here.** Этап 4.0 is still `CODE-GREEN`, and `§HANDOFF` forbids declaring a
later block `DEVICE-ACCEPTED` on top of an unverified one. Either 4.0's own check runs in the same
session on the phone (`ru-RU`, no provider configured => the honest message, not `Unknown command`), or
A0 closes as `CODE-GREEN` and the debt gains a second layer. The former is recommended: it is one minute
of device time.

## 13. Non-goals

A3 / A5 / A6 · DAG plans · re-planning · `PartiallyCompleted` · new `core/ui` primitives · turning any
PREVIEW surface live · the model planner and its multi-step response schema · tool levels
(`DOC-ILM-2`) · choosing among install sources or registering new sources · widening `ActionIds` ·
foreground service or any background autonomous loop · a wall-clock budget · rollback machinery
(`DOC-HMA-3`) · the A1 fork (parallel tool vocabulary vs. evolving `ActionCatalog` in place) - it
belongs to A1' and a spec rewritten after ADR 2/4, and A0 is built so that both banks stay equally
cheap.

## 14. Work order

1. `LauncherViewModel` split - zero diff on existing tests
2. `domain/tool` + `domain/agent` + `domain/trace` + `AgentExecutor` + `InvocationValidator` + tests
3. `TemplatePlanner`
4. `SystemIntentToolSource` + `ToolExecutor` over the **unchanged** `ExecuteActionUseCase`
5. Room 3 -> 4 + `AgentSessionStore`
6. the `RouteCommandUseCase` cut + `CommandOutcome.AgentSessionStarted` + the pass-through branches the
   compiler demands in both memory decorators and in `applyOutcome`
7. surface + `en`/`ru`/`tr` strings
8. guards + mutation check
9. ADR + `DOC-ADL-3` amendment (matrix §6 journal) + `CLAUDE.md` / `ai-context/current-status.md` sync

Each numbered item is at least one commit; the split may be several.

## 15. Success criteria

- The cycle `goal -> plan -> gate -> tool -> observe -> tool -> result -> trace` runs on device.
- Consent is inside the loop, not a wrapper around it: there is one call site to the world and it is
  behind the gate.
- The session survives process death and resumes without executing anything twice.
- The trace is 1:1 with what happened, including across a restart.
- `LauncherViewModel` is split, with the existing suites untouched.
- Nothing accumulates on disk: at rest the agent tables are empty.
- `DOC-ILM-3` and `DOC-HMA-2` have real test names in the matrix; `DOC-ADL-3` carries its amendment row.
- Adding an install source in A1' requires no change inside the engine.
