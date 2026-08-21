# A0 Thin Agentic Spike — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended)
> or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`)
> syntax for tracking.

**Goal:** Carry one real goal across two tool calls where the second depends on what the first
observed, stopping the loop for consent at the risk transition, with every step traced and the session
surviving process death.

**Architecture:** A pure `:domain` engine (`domain/tool/`, `domain/agent/`, `domain/trace/`) whose only
path to the world is `ToolExecutor`, driven by four small use cases, planned deterministically by a
`TemplatePlanner`, persisted in Room and deleted on any terminal state. The existing
`ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain becomes the one registered tool
source, unchanged. `LauncherViewModel` is split into collaborators first, as a pure refactor.

**Tech Stack:** Kotlin Multiplatform (`:domain` = `commonMain` production, `jvmTest` tests, JUnit4 +
`kotlinx-coroutines-test`), Room 3 -> 4 with exported schemas, Hilt, Jetpack Compose with DS-5
primitives.

**Spec:** [docs/superpowers/specs/2026-08-20-a0-thin-agentic-spike-design.md](../specs/2026-08-20-a0-thin-agentic-spike-design.md)

## Global Constraints

Every task's requirements implicitly include this section.

- **JDK 17.** The machine default is newer and Gradle cannot parse it. Every Gradle invocation in this
  plan is:
  `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 <tasks>`
- **Never pipe `gradlew` through `tail`.** It masked a red gate as exit 0 on 2026-07-13. Check the exit
  code and read the real output.
- **Block gate:** `:domain:jvmTest testDebugUnitTest assembleDebug`. `:domain:jvmTest` is listed
  explicitly because `testDebugUnitTest` does **not** reach it and the whole engine lives there.
- `:domain` is **stdlib + coroutines only**. No Android, no `core/*`, no `java.*`-only APIs in
  `commonMain` — it must compile for both the `android` and `jvm` targets. Production code goes in
  `domain/src/commonMain/kotlin/`, tests in `domain/src/jvmTest/kotlin/`.
- Interfaces in `domain`, implementations in `data/*`. No `feature -> feature` edge. Repository and
  use-case operations return `OperationResult<T>` and never throw to the UI.
- **No user-facing text originates in `domain` or in a ViewModel.** Domain emits typed values; the
  feature layer picks the string via `sidrString(R.string....)`. Enforced by `HardcodedUiTextGuardTest`
  and `StringSeamGuardTest`.
- **`en`/`ru`/`tr` ship in the same commit as the feature** (`LocaleCompletenessGuardTest`).
- **`ActionIds`' seven values are frozen byte-for-byte** (ADR 3/4). No task widens or edits them.
- **`core/ui` is not modified.** No new primitives. `:core:ui:verifyRoborazziDebug` is run at the end
  as proof it was not touched.
- **Agent commits, agent never pushes.** Each task ends in at least one commit. Do not commit on a red
  gate — fix it or park it.
- **Ordered `ActionRiskLevel` comparison** is by declaration order `SAFE < CONFIRM < DANGEROUS`
  (`enum.ordinal`); do not introduce a second risk scale.

---

## File Structure

**Created — `:domain` (`domain/src/commonMain/kotlin/com/sidr/launcher/domain/`)**

| File | Responsibility |
|---|---|
| `tool/ToolId.kt` | `ToolId` value class |
| `tool/ToolDescriptor.kt` | `ToolDescriptor` — schema, risk, reversibility, permission gate |
| `tool/ToolInvocation.kt` | `ToolInvocation`, `ToolResult`, `ObservedFact` |
| `tool/ToolRegistry.kt` | `ToolRegistry` port |
| `tool/ToolExecutor.kt` | `ToolExecutor` port — the only path to the world |
| `tool/InvocationValidator.kt` | fail-closed argument validation |
| `agent/AgentGoal.kt` | `AgentGoal`, `AgentSessionId` |
| `agent/ExecutionPlan.kt` | `PlanStep`, `ExecutionPlan`, `StepPrecondition`, `StepRationale` |
| `agent/Planner.kt` | `Planner` port, `PlanningResult` |
| `agent/AgentSession.kt` | `AgentSession`, `ExecutionState`, `ConsentCheckpoint`, `ConsentReason`, `RuntimeBudget` |
| `agent/AgentExecutor.kt` | the engine — exactly one transition per `advance` |
| `agent/AgentSessionStore.kt` | persistence port |
| `agent/StartAgentSessionUseCase.kt` | goal -> plan -> persisted session |
| `agent/RunAgentSessionUseCase.kt` | loops `advance` + persist until it must stop |
| `agent/ResolveConsentUseCase.kt` | idempotent consent write, then re-enter the run loop |
| `agent/CancelAgentSessionUseCase.kt` | cancel + delete |
| `trace/TraceEvent.kt` | `TraceEvent`, `ExecutionTrace` |

**Created — `:data:repository`**

| File | Responsibility |
|---|---|
| `agent/SystemIntentToolSource.kt` | projects `ActionCatalog` into two `ToolDescriptor`s; implements `ToolRegistry` |
| `agent/SystemIntentToolExecutor.kt` | `ToolExecutor` over the unchanged `ExecuteActionUseCase` |
| `db/entity/AgentSessionEntity.kt`, `AgentPlanStepEntity.kt`, `AgentTraceEventEntity.kt` | rows |
| `db/dao/AgentSessionDao.kt` | queries incl. the conditional-write resume |
| `db/migrations/Migration3To4.kt` | schema 3 -> 4 |
| `agent/RoomAgentSessionStore.kt` | `AgentSessionStore` implementation + mappers |

**Created — `:core:testing`**

| File | Responsibility |
|---|---|
| `FakeToolExecutor.kt` | records invocations, returns scripted `ToolResult`s |
| `FakeAgentSessionStore.kt` | in-memory store for domain tests |

**Created — `:feature:launcher`**

| File | Responsibility |
|---|---|
| `LauncherDevConsole.kt`, `LauncherVoiceInput.kt`, `LauncherSuggestions.kt`, `LauncherAppLaunch.kt`, `LauncherAppList.kt`, `LauncherCommandSession.kt` | the six collaborators the ViewModel splits into |
| `agent/AgentSessionPresentation.kt` | maps `ExecutionState` / `StepRationale` / `ObservedFact` to string resources |
| `agent/AgentSessionSurface.kt` | the execution surface, DS-5 primitives only |

**Modified**

| File | Change |
|---|---|
| `domain/.../intent/CommandOutcome.kt` | `+ AgentSessionStarted(id)` |
| `domain/.../ai/router/RouteCommandUseCase.kt` | the agent cut, step 2 |
| `data/.../db/SidrDatabase.kt` | version 4, three entities, one DAO |
| `feature/launcher/.../LauncherViewModel.kt` | 886 lines -> facade over six collaborators, then one new branch |
| `app/.../di/*` | bindings for registry, executor, store, use cases |
| `app/build.gradle.kts` | `inputs.file` for any guard reading files outside a source set |
| `docs/governing/sidr-doctrine-matrix-v1.0.md` | `DOC-ADL-3` amendment + §6 journal row; test names for `DOC-ILM-3`, `DOC-HMA-2` |

---

# Phase 1 — The mandatory preparatory split

Master Plan §3.1 requires the split **before** the runtime. Acceptance requires
`git diff` over the existing ViewModel suites to be **empty**, so Phase 1 is a pure refactor: the public
API of `LauncherViewModel` does not change by one character, and `LauncherViewModelTest` (2151 lines)
plus `LauncherScreenPrayerStripTest` are never edited.

**The shape.** Each collaborator is a plain class in the same package (`internal`), constructed in the
ViewModel's property initializers, receiving its dependencies plus the `CoroutineScope`
(`viewModelScope`). It owns its `MutableStateFlow`s; the ViewModel re-exposes them as the same public
`StateFlow` properties it exposes today. No new module, no new Hilt binding, no `feature -> feature`
edge.

**The test for every task in this phase is the same and is non-negotiable:**

```bash
git diff --stat -- feature/launcher/src/test/ | wc -l     # must print 0
```

### Task 1: Extract the dev console and voice input

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherDevConsole.kt`
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherVoiceInput.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt` (**read-only — never edited**)

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: `internal class LauncherDevConsole(scope: CoroutineScope)` exposing
  `val armed: StateFlow<Boolean>`, `val consoleOn: StateFlow<Boolean>`,
  `val lines: StateFlow<List<ConsoleLine>>`, `fun arm()`, `fun append(command: String, summary: String)`,
  `fun toggle(on: Boolean)`.
  `internal class LauncherVoiceInput(speechInputSource: SpeechInputSource, scope: CoroutineScope)`
  exposing `val state: StateFlow<SpeechRecognitionState>` (or the exact type the ViewModel exposes
  today) and `fun start(languageTag: String?)`.

- [ ] **Step 1: Record the baseline**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest
git rev-parse HEAD > /tmp/a0-baseline-sha
```

Expected: PASS. This is the behaviour that must survive byte-for-byte.

- [ ] **Step 2: Read the two regions before moving them**

Read `LauncherViewModel.kt` lines 296–322 (`_devArmed`, `_devConsoleOn`, `_consoleLines`), the
`armDevMode()` function at line 358, `outcomeSummary()` at line 421, and `startVoiceInput()` at
line 523. Note every read and write of those fields anywhere else in the file — the console is appended
to from `onCommandSubmitted` and `confirmRoutedAction`, and those call sites become
`devConsole.append(...)`.

- [ ] **Step 3: Create `LauncherDevConsole.kt`**

Move the three `MutableStateFlow` declarations and their public `StateFlow` counterparts verbatim.
`ConsoleLine` moves with them if it is declared inside the ViewModel; if it is a top-level type in
another file, leave it where it is and import it.

```kotlin
package com.sidr.launcher.feature.launcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Этап 4 / A0, preparatory split. The Developer Command console — session-only and in-memory, with no
 * persisted key, exactly as it was inside `LauncherViewModel`. Extracted unchanged: this class is a
 * move, not a redesign, and `LauncherViewModelTest` must not need one character of edit.
 */
internal class LauncherDevConsole(private val scope: CoroutineScope) {

    private val _armed = MutableStateFlow(false)
    val armed: StateFlow<Boolean> = _armed.asStateFlow()

    private val _consoleOn = MutableStateFlow(false)
    val consoleOn: StateFlow<Boolean> = _consoleOn.asStateFlow()

    private val _lines = MutableStateFlow<List<ConsoleLine>>(emptyList())
    val lines: StateFlow<List<ConsoleLine>> = _lines.asStateFlow()

    fun arm() { _armed.value = true }

    fun toggle(on: Boolean) { _consoleOn.value = on }

    fun append(command: String, summary: String) {
        _lines.value = _lines.value + ConsoleLine(command, summary)
    }
}
```

If the current `armDevMode()` body does more than set a flag (check line 358 before writing), move that
body verbatim into `arm()` rather than the simplified version above.

- [ ] **Step 4: Create `LauncherVoiceInput.kt`**

Move `startVoiceInput()`'s body and the speech state flow verbatim, keeping the same collection,
`catch`, and error mapping. Do not "improve" the error handling — a behaviour change here shows up as a
test edit, which is the failure condition for this phase.

- [ ] **Step 5: Rewire the ViewModel**

In `LauncherViewModel`, replace the moved fields with:

```kotlin
private val devConsole = LauncherDevConsole(viewModelScope)
private val voiceInput = LauncherVoiceInput(speechInputSource, viewModelScope)
```

and make every public member that the tests touch delegate, keeping the **identical** name, type and
nullability:

```kotlin
val devArmed: StateFlow<Boolean> get() = devConsole.armed
fun armDevMode() = devConsole.arm()
fun startVoiceInput(languageTag: String? = null) = voiceInput.start(languageTag)
```

Check the current declarations before writing these three lines and copy their exact signatures,
including default arguments.

- [ ] **Step 6: Run the suite and prove the tests were not edited**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest
git diff --stat -- feature/launcher/src/test/
```

Expected: tests PASS, and `git diff --stat` over the test directory prints **nothing**. If a test
needed an edit, the extraction changed behaviour — revert and redo it as a move.

- [ ] **Step 7: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/
git commit -m "refactor(agentic-4/A0): extract dev console and voice input from LauncherViewModel

Pure move, zero diff on the existing ViewModel suites."
```

### Task 2: Extract suggestions

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherSuggestions.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: existing suites, **read-only**

**Interfaces:**
- Consumes: nothing from Task 1.
- Produces: `internal class LauncherSuggestions(suggestionEngine, suggestionsCacheRepository, featureFlagRepository, installedAppsRepository, ioDispatcher, scope)`
  exposing `val suggestions: StateFlow<List<Suggestion>>`, `fun observeFlag()`,
  `suspend fun restoreAndRefresh()`, `suspend fun clear()`,
  `fun resolveLabels(raw: List<Suggestion>, apps: List<InstalledApp>): List<Suggestion>`.

- [ ] **Step 1: Read the region**

`_suggestions` (line 131), `observeSuggestionFlag()` (660), `restoreAndRefreshSuggestions()` (681),
`clearSuggestions()` (713), `resolveSuggestionLabels()` (774). Note that `onSuggestionClicked()` (460)
**launches an app** — it stays in the ViewModel for now and moves in Task 3, because it depends on the
app-launch concern that does not exist yet.

- [ ] **Step 2: Create `LauncherSuggestions.kt`**

Move all five members verbatim, including the `flowOn(ioDispatcher)` placement and the
`WhileSubscribed`/`stateIn` parameters if any. Precomputation timing is behaviour: if a flow is cold
today it must stay cold.

- [ ] **Step 3: Rewire and delegate**

```kotlin
private val suggestionsSection = LauncherSuggestions(
    suggestionEngine = suggestionEngine,
    suggestionsCacheRepository = suggestionsCacheRepository,
    featureFlagRepository = featureFlagRepository,
    installedAppsRepository = installedAppsRepository,
    ioDispatcher = ioDispatcher,
    scope = viewModelScope,
)
```

Every ViewModel member that read `_suggestions` now reads `suggestionsSection.suggestions`. The public
`StateFlow` the UI collects keeps its current name and type.

- [ ] **Step 4: Run and prove zero test diff**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest
git diff --stat -- feature/launcher/src/test/
```

Expected: PASS, empty diff.

- [ ] **Step 5: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/
git commit -m "refactor(agentic-4/A0): extract the suggestions section from LauncherViewModel

Pure move, zero diff on the existing ViewModel suites."
```

### Task 3: Extract app launch and the app list

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherAppLaunch.kt`
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherAppList.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: existing suites, **read-only**

**Interfaces:**
- Consumes: `LauncherSuggestions` from Task 2 (the app list resolves suggestion labels against the
  loaded apps).
- Produces:
  `internal class LauncherAppLaunch(actionExecutor, usageHistoryRepository, recordResolutionChoice, applicationScope, scope)`
  exposing `suspend fun launch(app: InstalledApp): ActionExecutionResult`,
  `fun rememberLearningToken(token: ResolutionLearningToken?)`,
  `fun recordChoiceIfPending(packageName: String)`.
  `internal class LauncherAppList(installedAppsRepository, usageHistoryRepository, userPreferencesRepository, ioDispatcher, scope)`
  exposing `val state: StateFlow<UiState<List<InstalledApp>>>` (copy the exact current type),
  `val favorites: StateFlow<List<InstalledApp>>`, `fun load()`, `fun retry()`, `fun dismissSetupHint()`.

**Why these two split together:** `launchApp()`, `recordUsage()` and `recordChoiceIfPending()` are
called from **both** the app grid and the command pipeline. Putting them in a third collaborator that
both hold is what prevents a `LauncherAppList <-> LauncherCommandSession` cycle in Task 4.

- [ ] **Step 1: Read the region**

`_rawAppsResult` (130), `loadApps()` (324), `retry()` (339), `onAppClicked()` (480),
`recordChoiceIfPending()` (497), `sortByUsage()` (722), `deriveFavorites()` (742),
`dismissSetupHint()` (759), `launchApp()` (790), `recordUsage()` (820), and the `_pendingLearningToken`
field (294). Note which of them run on `applicationScope` rather than `viewModelScope` — that
distinction is load-bearing (a quick nav-away must not drop a recorded choice) and must survive the
move exactly.

- [ ] **Step 2: Create `LauncherAppLaunch.kt`**

Move `launchApp()`, `recordUsage()`, `recordChoiceIfPending()` and `_pendingLearningToken`, keeping
`applicationScope` for the fire-and-forget recording and `viewModelScope` for the launch itself.

- [ ] **Step 3: Create `LauncherAppList.kt`**

Move the remaining members. The `combine(...)`/`stateIn(...)` expression that builds the exposed UI
state moves whole; do not re-derive it.

- [ ] **Step 4: Rewire and delegate**

The ViewModel keeps `onAppClicked` and `onSuggestionClicked` as public entry points, now bodies of two
or three lines that call `appLaunch` and `appList`.

- [ ] **Step 5: Run and prove zero test diff**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest
git diff --stat -- feature/launcher/src/test/
```

Expected: PASS, empty diff.

- [ ] **Step 6: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/
git commit -m "refactor(agentic-4/A0): extract app launch and app list from LauncherViewModel

Pure move, zero diff on the existing ViewModel suites. App launch is its own
collaborator because both the grid and the command pipeline call it."
```

### Task 4: Extract the command session

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Test: existing suites, **read-only**

**Interfaces:**
- Consumes: `LauncherAppLaunch` (Task 3), `LauncherDevConsole` (Task 1).
- Produces:
  `internal class LauncherCommandSession(resolveCommand, executeAction, actionCatalog, universalInputRouter, savedStateHandle, appLaunch, devConsole, scope)`
  exposing `val input: StateFlow<String>`, `val feedback: StateFlow<CommandFeedback>`,
  `val pendingRoutedAction: StateFlow<PendingRoutedAction?>`, `val liveResults: StateFlow<...>` (copy
  the exact current type), `fun onChanged(text: String)`, `fun submit(text: String)`,
  `fun submitWebSearch(query: String)`, `fun submitSite(query: String)`, `fun confirm()`,
  `fun cancel()`, `fun dismissFeedback()`, and **`fun applyOutcome(outcome: CommandOutcome)`**, which
  Task 11 extends with the new branch.

- [ ] **Step 1: Read the region**

Lines 245–295 (input, live results, feedback, pending action), `onCommandChanged` (346),
`onCommandSubmitted` (363), `outcomeSummary` (421), `submitWebSearch` (442), `submitSite` (454),
`dismissFeedback` (514), `applyOutcome` (552), `confirmRoutedAction` (630), `cancelRoutedAction` (644),
`commandLineFor` (650), `suggestedIntentFor` (836), `setCommandInput` (250).

`setCommandInput` writes to `savedStateHandle` — the typed command survives process death (H3). That
behaviour moves with it.

- [ ] **Step 2: Create `LauncherCommandSession.kt`**

Move all of the above verbatim. `applyOutcome` keeps its **exhaustive `when` with no `else`** — that
property is what makes Task 11 fail to compile until the new outcome is handled deliberately, and it
must not be softened into an `else`.

- [ ] **Step 3: Rewire the ViewModel into a facade**

What remains in `LauncherViewModel`: the Hilt constructor, the navigation `Channel` with `navigateTo` /
`navigateBack` / `isKnownRoute`, the cold `prayerContext` flow (untouched throughout Phase 1), the six
collaborator instances, and delegating one-liners for every public member the UI and the tests use.

- [ ] **Step 4: Run the full module suite and prove zero test diff**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest
git diff --stat -- feature/launcher/src/test/
wc -l feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt
```

Expected: PASS; empty test diff; the ViewModel well under 300 lines.

- [ ] **Step 5: Run the whole gate before leaving Phase 1**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug
```

Expected: BUILD SUCCESSFUL. Read the output; do not pipe it through `tail`.

- [ ] **Step 6: Commit**

```bash
git add feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/
git commit -m "refactor(agentic-4/A0): extract the command session; LauncherViewModel is a facade

Closes the mandatory preparatory action of Master Plan 3.1 (Risk 1). Nine areas
of responsibility are now six collaborators behind an unchanged public API; the
agent runtime will be added as a seventh rather than as a tenth area inside 886
lines. Zero diff on the existing ViewModel suites."
```

---

# Phase 2 — The domain engine

Everything here is `commonMain` (stdlib + coroutines) with tests in `jvmTest`. Nothing in this phase
touches Android, Room, or the UI, so the whole engine is provable before a single wire is connected.

**Eight refinements this phase makes to the spec's sketch**, each for a stated reason — they are
already reflected in the code below:

1. `ToolDescriptor.reversible: Boolean` becomes `durability: ToolDurability { TRANSIENT, DURABLE }`.
   "Reversible" promised a rollback A0 does not have; `DURABLE` is the *marking* `DOC-HMA-3` asks for
   without claiming the machinery. Both A0 tools are `TRANSIENT` (launching an app and opening a store
   page write no durable state), so the trigger exists, is unit-tested, and never fires in A0.
2. `AgentGoal` carries a typed `shape` beside its text. The cut site already knows the shape
   (`NoAppFound(query)`); making the planner re-parse raw text would be fragile and would discard
   information we hold.
3. `TraceEvent.StepRejected` is added. A validation rejection is a step that did not run, and
   `DOC-ILM-3` requires the trace to be 1:1 with reality including the refusals.
4. `ToolIds` constants live in `domain/tool/` with a test pinning them to `ActionIds`, so the duplicated
   frozen strings cannot drift silently.
5. `AgentSessionIdFactory` is a port. `java.util.UUID` does not exist in `commonMain`, and injecting the
   factory makes tests deterministic.
6. Trace events carry **no timestamp**. The data layer stamps rows; the domain stays clock-free, which
   is also why the wall-clock budget is honestly deferred to A4'.
7. A tool that declares a `permissionGate` **always** stops for consent in A0. Consulting the real grant
   state would mean injecting `PermissionChecker` into the engine; stopping unconditionally is the
   fail-safe half of that and costs nothing, because neither A0 tool declares a gate.
8. Consecutive failures are derived from the trailing observations rather than stored in a field —
   one less thing to persist and to keep consistent across a restart.

### Task 5: The tool vocabulary and fail-closed validation

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolId.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolDescriptor.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolRegistry.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolExecutor.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/InvocationValidator.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/InvocationValidatorTest.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolIdsTest.kt`

**Interfaces:**
- Consumes: `ActionArg`, `ArgType`, `ActionRiskLevel`, `PermissionFeature`, `CommandFailure` — all
  existing, all reused unchanged.
- Produces: `ToolId`, `ToolIds.LAUNCH_APP`, `ToolIds.PLAY_STORE_SEARCH`, `ToolDescriptor`,
  `ToolDurability`, `ToolInvocation`, `ToolResult` (`Effected` / `Observed(fact)` / `Failed(failure)`),
  `ObservedFact`, `ToolRegistry.all()/find(id)`, `ToolExecutor.invoke(invocation)`,
  `InvocationValidator.validate(invocation, registry): InvocationCheck`, `RejectionReason`.

- [ ] **Step 1: Write the failing validator test**

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/InvocationValidatorTest.kt`:

```kotlin
package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A0's fail-closed argument gate, modeled on `ProposalValidator`. It runs before **every** step, not
 * once at planning time: a plan can outlive a process restart, and by the time it resumes the app may
 * be gone or a permission revoked.
 */
class InvocationValidatorTest {

    private val launchApp = ToolDescriptor(
        id = ToolIds.LAUNCH_APP,
        argSchema = listOf(ActionArg("query", description = "The app name to launch")),
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private val registry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = listOf(launchApp)
        override fun find(id: ToolId): ToolDescriptor? = all().firstOrNull { it.id == id }
    }

    @Test
    fun `a well-formed invocation is valid`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")),
            registry,
        )
        assertEquals(InvocationCheck.Valid, check)
    }

    @Test
    fun `an unregistered tool is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolId("not_registered"), mapOf("query" to "убер")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL), check)
    }

    @Test
    fun `an argument outside the declared schema is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер", "sudo" to "true")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG), check)
    }

    @Test
    fun `a missing required argument is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, emptyMap()),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }

    @Test
    fun `a blank required argument is rejected`() {
        val check = InvocationValidator.validate(
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "   ")),
            registry,
        )
        assertEquals(InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG), check)
    }
}
```

- [ ] **Step 2: Write the failing drift test**

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolIdsTest.kt`:

```kotlin
package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ToolIds` deliberately repeats two of the seven frozen `ActionIds` strings, because the A0 planner
 * names tools and must not import the action vocabulary (that edge is what keeps the A1 fork open).
 * A duplicated constant that can drift silently is a bug waiting to happen, so it is pinned here.
 * `ActionIds`' seven values are frozen byte-for-byte by ADR 3/4 — if this test ever fails, the fix is
 * to correct `ToolIds`, never `ActionIds`.
 */
class ToolIdsTest {

    @Test
    fun `tool ids mirror the frozen action ids byte-for-byte`() {
        assertEquals(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP.value)
        assertEquals(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH.value)
    }
}
```

- [ ] **Step 3: Run both tests to verify they fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*InvocationValidatorTest' --tests '*ToolIdsTest'
```

Expected: FAIL — compilation error, `ToolDescriptor` / `ToolIds` / `InvocationValidator` unresolved.

- [ ] **Step 4: Write the vocabulary**

`domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolId.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/**
 * Opaque, stable identifier of a registered tool. A value class over a raw [String] for the same
 * reason `ActionId` is one: the string is the identity a plan step references and, from A4' on, the
 * identity a model emits.
 */
@JvmInline
value class ToolId(val value: String)

/**
 * The two tools A0 registers. Their strings mirror the frozen `ActionIds` (ADR 3/4) and `ToolIdsTest`
 * pins that. They are repeated rather than imported so `domain/tool` and `domain/agent` never
 * reference the action vocabulary — the edge whose absence keeps the A1 fork (parallel vocabulary vs.
 * evolving `ActionCatalog` in place) genuinely open for A1'.
 */
object ToolIds {
    val LAUNCH_APP = ToolId("launch_app")
    val PLAY_STORE_SEARCH = ToolId("play_store_search")
}
```

`ToolDescriptor.kt`:

```kotlin
package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * How much of a footprint a tool leaves behind.
 *
 * [DURABLE] is the "irreversible steps are marked BEFORE execution" half of `DOC-HMA-3`. A0 marks and
 * gates on it; the compensation/rollback machinery is A4', and this block does not claim otherwise.
 * Both A0 tools are [TRANSIENT] — launching an app and opening a store page write no durable state —
 * so the gate below exists, is unit-tested, and never fires in this slice.
 */
enum class ToolDurability { TRANSIENT, DURABLE }

/**
 * What the registry knows about one tool. Carries **no user-facing copy**: the surface maps [id] to a
 * string resource in the feature layer. [ActionArg] / [ActionRiskLevel] / [PermissionFeature] are
 * reused unchanged rather than duplicated — the spec's "reused without a single edit" list.
 */
data class ToolDescriptor(
    val id: ToolId,
    val argSchema: List<ActionArg> = emptyList(),
    val risk: ActionRiskLevel,
    val durability: ToolDurability,
    val permissionGate: PermissionFeature? = null,
)
```

`ToolInvocation.kt`:

```kotlin
package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.intent.CommandFailure

/** One validated call: which tool, with which arguments. */
data class ToolInvocation(val id: ToolId, val args: Map<String, String> = emptyMap())

/**
 * A fact a tool reported instead of performing an effect. This vocabulary is what makes a plan a
 * loop rather than a batch: without it "the app is not installed" is a dead end, and with it it is an
 * observation the next step's precondition can depend on.
 */
enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }

sealed interface ToolResult {
    /** The tool performed its side effect. */
    data object Effected : ToolResult

    /** The tool ran and reported [fact]; nothing on the device changed. */
    data class Observed(val fact: ObservedFact) : ToolResult

    /** Technical failure. [failure] is safe to display — no stack, no PII. */
    data class Failed(val failure: CommandFailure) : ToolResult
}
```

`ToolRegistry.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/**
 * Port: the registered tools. A0 registers exactly one source (a projection of `ActionCatalog`);
 * A1' turns this into a federation of adapters over one vocabulary. Read-only and side-effect free.
 */
interface ToolRegistry {
    fun all(): List<ToolDescriptor>
    fun find(id: ToolId): ToolDescriptor?
}
```

`ToolExecutor.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/**
 * Port: **the only path from the agent to the world.**
 *
 * `ToolExecutorCallSiteGuardTest` asserts there is exactly one call site of [invoke] in the whole
 * codebase, and that it sits behind the consent checkpoint. That is the mechanical version of the
 * growth rule's promise: tool #21 gets consent for free not because we will remember, but because
 * there is nowhere to forget.
 */
interface ToolExecutor {
    suspend fun invoke(invocation: ToolInvocation): ToolResult
}
```

`InvocationValidator.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/** Why an invocation was refused. Every value is traced, never swallowed. */
enum class RejectionReason { UNKNOWN_TOOL, UNDECLARED_ARG, MISSING_REQUIRED_ARG }

sealed interface InvocationCheck {
    data object Valid : InvocationCheck
    data class Rejected(val reason: RejectionReason) : InvocationCheck
}

/**
 * Pure, fail-closed argument validation — the A0 counterpart of `ProposalValidator`, which stays
 * untouched and served as the model. Stdlib only.
 */
object InvocationValidator {

    fun validate(invocation: ToolInvocation, registry: ToolRegistry): InvocationCheck {
        val descriptor = registry.find(invocation.id)
            ?: return InvocationCheck.Rejected(RejectionReason.UNKNOWN_TOOL)

        val declared = descriptor.argSchema.map { it.name }.toSet()
        if (invocation.args.keys.any { it !in declared }) {
            return InvocationCheck.Rejected(RejectionReason.UNDECLARED_ARG)
        }

        val unsatisfied = descriptor.argSchema.any { arg ->
            arg.required && invocation.args[arg.name]?.isNotBlank() != true
        }
        if (unsatisfied) return InvocationCheck.Rejected(RejectionReason.MISSING_REQUIRED_ARG)

        return InvocationCheck.Valid
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*InvocationValidatorTest' --tests '*ToolIdsTest'
```

Expected: PASS, 6 tests.

- [ ] **Step 6: Prove `commonMain` still compiles for both KMP targets**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:compileKotlinJvm :domain:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL. A `java.*`-only API would fail the Android target here rather than at
integration time.

- [ ] **Step 7: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/
git commit -m "feat(agentic-4/A0): the tool vocabulary and fail-closed invocation validation

ToolId/ToolIds/ToolDescriptor/ToolInvocation/ToolResult, the registry and
executor ports, and InvocationValidator. ToolIds mirrors two frozen ActionIds
strings on purpose and a test pins them so they cannot drift."
```

### Task 5b: Step-to-step data flow (F6) — added 2026-08-21

> **Why this task exists and why it is here.** Tasks 5–8 shipped a contract in which a tool cannot
> return a value and a step cannot consume one: `ToolResult.Observed` carries a two-valued enum and
> `ToolInvocation.args` carries literal strings. Every plan the engine can express is therefore a
> fallback chain, never a composition. Spec §2 fork **F6** (2026-08-21) closes that, and ADR
> "2026-08-21 — Развилка агентного трека" records the decision. It is Task **5b** rather than a later
> task because it **must land before Task 10**: once migration 3 → 4 ships with the current
> `args_json` / `observation_*` shape, the same change costs a migration 4 → 5 plus a persisted-trace
> conversion instead of a contract edit.
>
> **This task is written compactly on purpose.** The contract it changes is already specified in
> design-spec §4.1/§4.2/§6.2, and reproducing every test body here would add ~200 lines of plan for
> ~120 lines of code. The spec is the contract; this is the work order.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolDescriptor.kt` — add `outputSchema`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt` — `ArgSource`,
  `ResolvedInvocation`, `ToolOutput`, `args: Map<String, ArgSource>`, outputs on `Effected` / `Observed`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolExecutor.kt` — takes `ResolvedInvocation`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/InvocationValidator.kt` — two phases
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt` — resolve before invoke
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt` — step 1 binds
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolExecutor.kt`,
  `FakeToolRegistry.kt` — follow the signatures
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/InvocationValidatorTest.kt` (extend)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt` (extend)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentSessionUseCasesTest.kt` (follow)

**Interfaces:**
- Produces: `ArgSource.Literal` / `ArgSource.FromStep(stepIndex, key)`, `ResolvedInvocation`,
  `ToolOutput`, `ToolDescriptor.outputSchema`, `InvocationValidator.validate(invocation,
  precedingTools, registry)`, `InvocationValidator.resolve(invocation, observations)`,
  `ResolutionResult`, `RejectionReason.{FORWARD_ARG_SOURCE, UNDECLARED_OUTPUT, UNRESOLVED_ARG_SOURCE}`.
- Unchanged on purpose: `ToolId`, `ToolIds`, `ObservedFact`, `ToolRegistry`, every `domain/agent` type
  except `AgentExecutor` and `TemplatePlanner`, and the whole `domain/trace` vocabulary —
  `TraceEvent.ToolObserved` already carries the full `ToolResult`, so the output is traced for free.

- [x] **Step 1: Extend `InvocationValidatorTest` red first**

Cases, in this order: a `Literal`-only invocation still validates exactly as before (regression);
`FromStep` naming its own index, a later index, and an index beyond the plan → `FORWARD_ARG_SOURCE`;
`FromStep` naming a key absent from the source tool's `outputSchema` → `UNDECLARED_OUTPUT`; `resolve`
binds to the producing step's value; `resolve` on a source step that `Failed` → `UNRESOLVED_ARG_SOURCE`
and **no `ResolvedInvocation` is produced**; `resolve` never substitutes a blank or a default.

- [x] **Step 2: Change the tool contract**

`outputSchema` on `ToolDescriptor`; `ArgSource`, `ResolvedInvocation`, `ToolOutput` in
`ToolInvocation.kt`; `Effected` becomes a `data class` with a defaulted `output`; `Observed` gains one.
`ToolExecutor.invoke` takes `ResolvedInvocation`. Keep `resolve` the **only** constructor call site of
`ResolvedInvocation` — that is what makes "an unresolved reference cannot reach the world" a
compile-time property rather than a convention.

- [x] **Step 3: Split the validator into `validate` + `resolve`**

`validate` keeps the three original reasons and adds the two static ones; it takes `precedingTools:
List<ToolId>` (position == step index) so `domain/tool` still references no `domain/agent` type — the
layering is agent → tool and F6 must not invert it. `resolve` takes `observations: Map<Int, ToolResult>`
for the same reason.

- [x] **Step 4: `AgentExecutor` — resolve in `prepare`, before `ToolInvoked` is written**

`prepare` gains two things: `validate` now receives `precedingTools` built from
`session.plan.steps.filter { it.index < next.index }.map { it.invocation.id }`, and **`resolve` runs
here**, after the consent checkpoint and **before** `StepStarted` / `ToolInvoked` are recorded. A
`ResolutionResult.Rejected` records `TraceEvent.StepRejected(index, reason)` and ends the session
`Failed` — the same fail-closed shape a validator rejection already uses, so there is one rejection
path and not two.

**Resolution must not sit in `perform`, and the reason is the trace.** `perform` runs only when the
session is already mid-step, i.e. when `ToolInvoked(i)` is recorded with no matching `ToolObserved(i)`.
That shape has exactly one meaning today — "the process died during the tool call" — and the resume
path reads it to decide **not** to re-run the step and to present the session as `Paused` instead. A
rejection raised after `ToolInvoked` would leave the trace in precisely that shape without a process
ever having died, so a plan with a bad binding would come back as "we may have half-run something,
please decide" instead of the honest "this step could not be bound". `DOC-ILM-3` says the trace is 1:1
with reality; that would break it.

`perform` therefore re-resolves purely to obtain the value it passes to the executor. That is
deterministic and free: `resolve` is a pure function of `(invocation, observations)`, and observations
cannot change between the `prepare` and the `perform` of the same step. If the second call somehow
rejects, `perform` records `ToolObserved(index, ToolResult.Failed(...))` before ending the session, so
the trace never sits mid-step — belt and braces, and it costs three lines.

The call site count stays **one**; `ToolExecutorCallSiteGuardTest` must stay green without being
relaxed.

- [x] **Step 5: `TemplatePlanner` — bind instead of repeating the literal**

Step 0 stays `launch_app(query = Literal(query))`. Step 1 becomes
`play_store_search(query = FromStep(0, "resolved_query"))`. The planner keeps naming no destination.

- [x] **Step 6: The tool source declares `resolved_query`** *(carried into Task 9)*

`launch_app` declares `outputSchema = listOf(ActionArg("resolved_query", …))` and returns it on every
result — outputs are a property of the tool, not of the branch it took. Task 9's adapter already has
the value; today it discards it.

- [x] **Step 7: Run the gate**

```bash
./gradlew --no-daemon :domain:jvmTest testDebugUnitTest assembleDebug
```

Output **not** piped through `tail`; check the exit code. Then the mutation check: break exactly what
each new rejection reason should catch (a forward reference, an undeclared output key, a failed source
step) and confirm each goes red on its own test and only on it.

- [x] **Step 8: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/ \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ \
        core/testing/src/main/java/com/sidr/launcher/core/testing/
git commit -m "feat(agentic-4/A0): step-to-step data flow — tools return values, steps bind to them

F6. ToolDescriptor.outputSchema, ArgSource, ResolvedInvocation and ToolOutput;
InvocationValidator splits into a static validate and a binding resolve, with
three new fail-closed rejection reasons. ToolExecutor takes only a resolved
invocation, so an unbound reference cannot reach the world. Lands before the
Room migration, where the same change would cost 4 -> 5 plus a trace conversion."
```

### Task 6: The agent types, the trace, and the executor

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentGoal.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/trace/TraceEvent.kt`
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolExecutor.kt`
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt`

**Interfaces:**
- Consumes: everything from Task 5.
- Produces: `AgentGoal(text, shape)`, `GoalShape.AppNotInstalled(query)`, `AgentSessionId`,
  `StepPrecondition`, `StepRationale`, `PlanStep`, `ExecutionPlan`, `ExecutionState`,
  `ConsentReason`, `ConsentCheckpoint`, `RuntimeBudget`, `AgentSession`,
  `AgentExecutor(registry, toolExecutor, budget).advance(session): AgentSession`,
  `TraceEvent`, `ExecutionTrace`. `FakeToolExecutor(script).invocations` records every call in order.

- [ ] **Step 1: Write the failing executor test**

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The A0 engine. `advance` performs exactly one transition, which is what lets cancellation be checked
 * *between* transitions and lets every test drive the machine without coroutine timing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentExecutorTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val budget = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)

    private fun planForMissingApp(query: String) = ExecutionPlan(
        listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to query)),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.GOAL_DIRECT,
            ),
            PlanStep(
                index = 1,
                invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to query)),
                risk = ActionRiskLevel.CONFIRM,
                precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
            ),
        ),
    )

    private fun session(query: String = "убер") = AgentSession(
        id = AgentSessionId("s1"),
        goal = AgentGoal(text = "открой $query", shape = GoalShape.AppNotInstalled(query)),
        plan = planForMissingApp(query),
        cursor = 0,
        state = ExecutionState.Running,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `a SAFE first step runs without consent and records the observation`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val after = AgentExecutor(registry, tools, budget).advance(session())

        assertEquals(listOf(ToolIds.LAUNCH_APP), tools.invocations.map { it.id })
        assertEquals(1, after.cursor)
        assertEquals(ExecutionState.Running, after.state)
        assertEquals(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), after.observations[0])
    }

    @Test
    fun `the risk transition stops the loop before the second tool is ever called`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        val afterFirst = executor.advance(session())
        val afterSecond = executor.advance(afterFirst)

        assertEquals(ExecutionState.AwaitingConsent, afterSecond.state)
        assertEquals(1, tools.invocations.size)          // the store was NOT called
        assertTrue(
            afterSecond.trace.events.any {
                it is TraceEvent.ConsentRequested && it.reason == ConsentReason.RISK_LEVEL
            },
        )
    }

    @Test
    fun `granted consent runs the second step and completes`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))
        s = executor.advance(s)

        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), tools.invocations.map { it.id })
        assertEquals(ExecutionState.Completed, s.state)
    }

    @Test
    fun `denied consent cancels and never invokes the tool`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s.copy(consents = mapOf(1 to false)))

        assertEquals(ExecutionState.Cancelled, s.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `an installed app skips the fallback step and still completes`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected))
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())      // step 0 launches the app
        s = executor.advance(s)                  // step 1's precondition is unsatisfied -> skipped
        s = executor.advance(s)                  // end of plan -> Completed

        assertEquals(1, tools.invocations.size)
        assertEquals(ExecutionState.Completed, s.state)
        assertTrue(s.trace.events.any { it is TraceEvent.StepSkipped && it.index == 1 })
    }

    @Test
    fun `an invocation the validator rejects fails the session and is traced`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val broken = session().let {
            it.copy(
                plan = ExecutionPlan(
                    listOf(
                        it.plan.steps[0].copy(
                            invocation = ToolInvocation(ToolId("not_registered"), mapOf("query" to "x")),
                        ),
                    ),
                ),
            )
        }

        val after = AgentExecutor(registry, tools, budget).advance(broken)

        assertEquals(ExecutionState.Failed, after.state)
        assertEquals(0, tools.invocations.size)
        assertTrue(after.trace.events.any { it is TraceEvent.StepRejected })
    }

    @Test
    fun `exhausting the step budget blocks instead of stopping silently`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected, ToolResult.Effected))
        val tight = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 1, maxConsecutiveFailures = 2))

        var s = tight.advance(session())
        s = tight.advance(s.copy(consents = mapOf(1 to true)))

        assertEquals(ExecutionState.Blocked, s.state)
    }

    @Test
    fun `repeated tool failures fail the session rather than looping`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Failed(CommandFailure.Generic), ToolResult.Failed(CommandFailure.Generic)),
        )
        val executor = AgentExecutor(registry, tools, RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 1))

        val after = executor.advance(session())

        assertEquals(ExecutionState.Failed, after.state)
    }

    @Test
    fun `advance on a terminal or awaiting session is a no-op`() = runTest {
        val tools = FakeToolExecutor(emptyList())
        val executor = AgentExecutor(registry, tools, budget)

        for (state in listOf(
            ExecutionState.AwaitingConsent, ExecutionState.Paused, ExecutionState.Completed,
            ExecutionState.Cancelled, ExecutionState.Failed, ExecutionState.Blocked,
        )) {
            val frozen = session().copy(state = state)
            assertEquals(frozen, executor.advance(frozen))
        }
        assertEquals(0, tools.invocations.size)
    }

    @Test
    fun `every executed step carries both a ToolInvoked and a ToolObserved event`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        val executor = AgentExecutor(registry, tools, budget)

        var s = executor.advance(session())
        s = executor.advance(s)
        s = executor.advance(s.copy(state = ExecutionState.Running, consents = mapOf(1 to true)))

        val invoked = s.trace.events.filterIsInstance<TraceEvent.ToolInvoked>().map { it.index }
        val observed = s.trace.events.filterIsInstance<TraceEvent.ToolObserved>().map { it.index }
        assertEquals(invoked, observed)
        assertEquals(listOf(0, 1), invoked)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*AgentExecutorTest'
```

Expected: FAIL — compilation error, nothing in `domain.agent` exists yet.

- [ ] **Step 3: Write the fakes**

`core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt`:

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolRegistry

/** In-memory registry for domain tests. [withA0Tools] mirrors what `SystemIntentToolSource` projects. */
class FakeToolRegistry(private val descriptors: List<ToolDescriptor>) : ToolRegistry {

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }

    companion object {
        fun withA0Tools() = FakeToolRegistry(
            listOf(
                ToolDescriptor(
                    id = ToolIds.LAUNCH_APP,
                    argSchema = listOf(ActionArg("query", description = "The app name to launch")),
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = ToolIds.PLAY_STORE_SEARCH,
                    argSchema = listOf(ActionArg("query", description = "The app name to find")),
                    risk = ActionRiskLevel.CONFIRM,
                    durability = ToolDurability.TRANSIENT,
                ),
            ),
        )
    }
}
```

`core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolExecutor.kt`:

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult

/**
 * Scripted executor. [invocations] is the record tests assert against — "the store was never called"
 * is the shape of most A0 assertions, so the recording matters more than the return values.
 */
class FakeToolExecutor(private val script: List<ToolResult>) : ToolExecutor {

    val invocations = mutableListOf<ToolInvocation>()

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val index = invocations.size
        invocations += invocation
        return script.getOrElse(index) { ToolResult.Failed(CommandFailure.Generic) }
    }
}
```

- [ ] **Step 4: Write the trace vocabulary**

`domain/src/commonMain/kotlin/com/sidr/launcher/domain/trace/TraceEvent.kt`:

```kotlin
package com.sidr.launcher.domain.trace

import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolResult

/**
 * What actually happened, in order. `DOC-ILM-3`: no step executes without an entry, and the trace is
 * 1:1 with reality — including the steps that were refused or skipped, which is why [StepRejected] and
 * [StepSkipped] exist.
 *
 * Events carry **no timestamp**: the domain has no clock (`commonMain` is stdlib + coroutines across
 * two targets), and the data layer stamps rows when it persists them. The goal text is stored once, on
 * the session, and never repeated per event.
 */
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

/** Ordered, append-only. */
data class ExecutionTrace(val events: List<TraceEvent> = emptyList())
```

- [ ] **Step 5: Write the agent types**

`domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentGoal.kt`:

```kotlin
package com.sidr.launcher.domain.agent

/** Opaque session identity. Produced by [AgentSessionIdFactory] so the domain needs no UUID API. */
@JvmInline
value class AgentSessionId(val value: String)

/**
 * What the planner recognised about the goal. A0 has exactly one shape, and the `when` over it in
 * `TemplatePlanner` is exhaustive on purpose: adding a second shape later forces a deliberate decision
 * rather than falling into a default.
 */
sealed interface GoalShape {
    /** FastPath resolved the command to an app launch and found no such app installed. */
    data class AppNotInstalled(val query: String) : GoalShape
}

/**
 * The user's goal. [text] is the raw command, kept for the surface and for A4''s model planner;
 * [shape] is what the deterministic layer already knows, so the planner never re-parses text the cut
 * site had already understood.
 */
data class AgentGoal(val text: String, val shape: GoalShape)
```

`ExecutionPlan.kt`:

```kotlin
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
```

`AgentSession.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * The eight states A0 can actually produce. `PartiallyCompleted` from the 2026-07-11 A4 spec is
 * deliberately absent: without re-planning A0 cannot reach it, and a state the engine cannot produce
 * is a lie in the type. A step skipped by an unsatisfied precondition is part of a normal [Completed].
 */
enum class ExecutionState {
    Planning, Running, AwaitingConsent, Paused, Completed, Cancelled, Failed, Blocked;

    val isTerminal: Boolean
        get() = this == Completed || this == Cancelled || this == Failed || this == Blocked
}

enum class ConsentReason { RISK_LEVEL, RISK_RAISED, MISSING_PERMISSION, DURABLE_EFFECT }

data class ConsentCheckpoint(val stepIndex: Int, val reason: ConsentReason)

/** Loop bounds. A wall-clock limit needs a clock port and is honestly deferred to A4'. */
data class RuntimeBudget(val maxSteps: Int, val maxConsecutiveFailures: Int) {
    companion object {
        /** A0's default: two steps of headroom over the one plan shape that exists. */
        val Default = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)
    }
}

/** Port: the domain must not know about UUIDs, and tests must be deterministic. */
interface AgentSessionIdFactory {
    fun newId(): AgentSessionId
}

/**
 * An immutable snapshot. Keeping state as a value and behaviour as a function ([AgentExecutor]) is what
 * lets every test drive the machine without coroutine timing, and what makes persistence a plain save.
 */
data class AgentSession(
    val id: AgentSessionId,
    val goal: AgentGoal,
    val plan: ExecutionPlan,
    val cursor: Int,
    val state: ExecutionState,
    val observations: Map<Int, ToolResult>,
    val consents: Map<Int, Boolean>,
    val trace: ExecutionTrace,
) {
    internal fun record(event: TraceEvent): AgentSession =
        copy(trace = ExecutionTrace(trace.events + event))

    internal fun ended(next: ExecutionState): AgentSession =
        copy(state = next).record(TraceEvent.SessionEnded(next))
}
```

- [ ] **Step 6: Write the executor**

`AgentExecutor.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * The A0 engine: **exactly one transition per [advance]**, never a loop.
 *
 * That shape is not stylistic. Cancellation is checked between transitions by the caller, so a cancel
 * that arrives mid-plan cannot be raced by the next step; and the persistence layer gets a save point
 * after every transition, which is what makes surviving `am force-stop` a consequence of the design
 * rather than a feature bolted on.
 *
 * The engine knows nothing of `LauncherAction`, `ExecutableAction`, `Intent`, or any store's name — it
 * only knows [ToolExecutor]. `AgentVocabularyGuardTest` pins that, because it is what keeps A1' free to
 * register F-Droid, a vendor site, or an MCP tool without touching a line in here.
 */
class AgentExecutor(
    private val registry: ToolRegistry,
    private val toolExecutor: ToolExecutor,
    private val budget: RuntimeBudget = RuntimeBudget.Default,
) {

    suspend fun advance(session: AgentSession): AgentSession =
        if (session.state == ExecutionState.Running) step(session) else session

    private suspend fun step(session: AgentSession): AgentSession {
        val next = session.plan.steps.getOrNull(session.cursor)
            ?: return session.ended(ExecutionState.Completed)

        if (session.cursor >= budget.maxSteps) return session.ended(ExecutionState.Blocked)

        if (!isSatisfied(next.precondition, session, next.index)) {
            return session
                .record(TraceEvent.StepSkipped(next.index, next.precondition))
                .copy(cursor = session.cursor + 1)
        }

        when (val check = InvocationValidator.validate(next.invocation, registry)) {
            is InvocationCheck.Rejected ->
                return session
                    .record(TraceEvent.StepRejected(next.index, check.reason))
                    .ended(ExecutionState.Failed)
            InvocationCheck.Valid -> Unit
        }

        val checkpoint = checkpointFor(session, next)
        if (checkpoint != null) {
            when (session.consents[next.index]) {
                null -> return session
                    .record(TraceEvent.ConsentRequested(next.index, checkpoint.reason))
                    .copy(state = ExecutionState.AwaitingConsent)
                false -> return session
                    .record(TraceEvent.ConsentResolved(next.index, granted = false))
                    .ended(ExecutionState.Cancelled)
                true -> Unit
            }
        }

        // The ONE call site to the world. It is below the checkpoint by construction, and
        // ToolExecutorCallSiteGuardTest fails the build if a second one ever appears.
        val started = session
            .record(TraceEvent.StepStarted(next.index))
            .record(TraceEvent.ToolInvoked(next.index, next.invocation.id))
        val result = toolExecutor.invoke(next.invocation)

        val observed = started
            .record(TraceEvent.ToolObserved(next.index, result))
            .copy(
                cursor = started.cursor + 1,
                observations = started.observations + (next.index to result),
            )

        return if (result is ToolResult.Failed && observed.trailingFailures() >= budget.maxConsecutiveFailures) {
            observed.ended(ExecutionState.Failed)
        } else {
            observed
        }
    }

    private fun isSatisfied(
        precondition: StepPrecondition,
        session: AgentSession,
        index: Int,
    ): Boolean = when (precondition) {
        StepPrecondition.None -> true
        is StepPrecondition.PreviousStepObserved -> {
            val previous = session.observations[index - 1]
            previous is ToolResult.Observed && previous.fact == precondition.fact
        }
    }

    /**
     * Four triggers, all fail-safe. [ConsentReason.MISSING_PERMISSION] fires whenever a tool merely
     * *declares* a gate: consulting the real grant state would put `PermissionChecker` inside the
     * engine, and stopping unconditionally is the conservative half of that. Neither A0 tool declares
     * one, so the branch is unit-tested rather than exercised in the slice.
     */
    private fun checkpointFor(session: AgentSession, step: PlanStep): ConsentCheckpoint? {
        val descriptor = registry.find(step.invocation.id) ?: return null
        val previousRisk = session.plan.steps
            .filter { it.index < step.index && session.observations.containsKey(it.index) }
            .maxOfOrNull { it.risk }
            ?: ActionRiskLevel.SAFE

        return when {
            step.risk >= ActionRiskLevel.CONFIRM ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_LEVEL)
            step.risk > previousRisk ->
                ConsentCheckpoint(step.index, ConsentReason.RISK_RAISED)
            descriptor.permissionGate != null ->
                ConsentCheckpoint(step.index, ConsentReason.MISSING_PERMISSION)
            descriptor.durability == com.sidr.launcher.domain.tool.ToolDurability.DURABLE ->
                ConsentCheckpoint(step.index, ConsentReason.DURABLE_EFFECT)
            else -> null
        }
    }

    /** Consecutive failures are derived, not stored — one less field to keep consistent across a restart. */
    private fun AgentSession.trailingFailures(): Int =
        generateSequence(cursor - 1) { it - 1 }
            .takeWhile { it >= 0 && observations[it] is ToolResult.Failed }
            .count()
}
```

- [ ] **Step 7: Run the executor tests to verify they pass**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*AgentExecutorTest'
```

Expected: PASS, 10 tests. If `the risk transition stops the loop` fails because the store *was*
called, the checkpoint is being evaluated after the invocation — fix the order, never the test.

- [ ] **Step 8: Verify both KMP targets still compile**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:compileKotlinJvm :domain:compileDebugKotlinAndroid
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 9: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ \
        domain/src/commonMain/kotlin/com/sidr/launcher/domain/trace/ \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/ \
        core/testing/src/main/java/com/sidr/launcher/core/testing/FakeTool*.kt
git commit -m "feat(agentic-4/A0): the agent session, the trace, and the one-transition executor

advance() performs exactly one transition, so cancellation is checked between
transitions and every transition is a save point. Consent is evaluated before
the single call site to the world; a denied checkpoint cancels without invoking."
```

### Task 7: The deterministic planner

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/TemplatePlannerTest.kt`

**Interfaces:**
- Consumes: Tasks 5 and 6.
- Produces: `class TemplatePlanner : Planner`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolIds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A0's planner is the seed of the learned-plan cache (rule 2 of the new rule): it replays an
 * already-understood goal **shape** deterministically and offline. The model planner is A4'.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TemplatePlannerTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val goal = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))

    @Test
    fun `a missing app plans launch then store, in that order`() = runTest {
        val result = TemplatePlanner().plan(goal, registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), plan.steps.map { it.invocation.id })
        assertEquals(listOf(0, 1), plan.steps.map { it.index })
    }

    @Test
    fun `the query flows into both steps unchanged`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertTrue(plan.steps.all { it.invocation.args == mapOf("query" to "убер") })
    }

    @Test
    fun `the second step is gated on the first having observed that the app is missing`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(StepPrecondition.None, plan.steps[0].precondition)
        assertEquals(
            StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
            plan.steps[1].precondition,
        )
    }

    @Test
    fun `risk is copied from the registry, never invented by the planner`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(ActionRiskLevel.SAFE, plan.steps[0].risk)
        assertEquals(ActionRiskLevel.CONFIRM, plan.steps[1].risk)
    }

    @Test
    fun `each step carries typed provenance rather than a sentence`() = runTest {
        val plan = (TemplatePlanner().plan(goal, registry) as PlanningResult.Planned).plan

        assertEquals(StepRationale.GOAL_DIRECT, plan.steps[0].rationale)
        assertEquals(StepRationale.APP_NOT_INSTALLED_FALLBACK, plan.steps[1].rationale)
    }

    @Test
    fun `an incomplete registry yields NoPlan instead of a half plan`() = runTest {
        val launchOnly = FakeToolRegistry(
            FakeToolRegistry.withA0Tools().all().filter { it.id == ToolIds.LAUNCH_APP },
        )

        assertEquals(PlanningResult.NoPlan, TemplatePlanner().plan(goal, launchOnly))
    }

    @Test
    fun `a blank query yields NoPlan`() = runTest {
        val blank = AgentGoal(text = "открой", shape = GoalShape.AppNotInstalled("   "))

        assertEquals(PlanningResult.NoPlan, TemplatePlanner().plan(blank, registry))
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*TemplatePlannerTest'
```

Expected: FAIL — `TemplatePlanner` unresolved.

- [ ] **Step 3: Write the planner**

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * The deterministic planner of A0 — the seed of the learned-plan cache, not a stand-in for the model.
 *
 * It replays one already-understood goal shape offline and for free, which is what makes A0's device
 * acceptance repeatable and what makes the agent work in local-only mode without anything leaving the
 * device. The model planner arrives in A4' behind this same [Planner] port.
 *
 * **It never names a destination.** Risk, schema and identity all come from the registry, so when A1'
 * registers F-Droid, a vendor site or Galaxy Store, choosing among them is a planner concern and the
 * engine below is untouched.
 *
 * Step 0 re-attempts the launch that FastPath already tried. That is deliberate: the plan must be
 * self-contained across a process restart (it cannot depend on an observation that lives outside the
 * session), and if the user installed the app while the session was paused, step 0 launches it and
 * step 1 correctly skips.
 */
class TemplatePlanner : Planner {

    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult =
        when (val shape = goal.shape) {
            is GoalShape.AppNotInstalled -> planMissingApp(shape, registry)
        }

    private fun planMissingApp(shape: GoalShape.AppNotInstalled, registry: ToolRegistry): PlanningResult {
        val query = shape.query.trim()
        if (query.isEmpty()) return PlanningResult.NoPlan

        val launch = registry.find(ToolIds.LAUNCH_APP) ?: return PlanningResult.NoPlan
        val store = registry.find(ToolIds.PLAY_STORE_SEARCH) ?: return PlanningResult.NoPlan

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(launch.id, mapOf("query" to query)),
                        risk = launch.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(store.id, mapOf("query" to query)),
                        risk = store.risk,
                        precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                        rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
                    ),
                ),
            ),
        )
    }
}
```

- [ ] **Step 4: Run it to verify it passes**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*TemplatePlannerTest'
```

Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/TemplatePlannerTest.kt
git commit -m "feat(agentic-4/A0): the deterministic template planner

Replays one understood goal shape offline and for free. Risk and schema come
from the registry, so no destination is named anywhere in the plan or engine."
```

### Task 8: The four use cases and the persistence port

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSessionStore.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/StartAgentSessionUseCase.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/RunAgentSessionUseCase.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ResolveConsentUseCase.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CancelAgentSessionUseCase.kt`
- Create: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeAgentSessionStore.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentSessionUseCasesTest.kt`

**Interfaces:**
- Consumes: Tasks 5–7.
- Produces:
  `AgentSessionStore.active()/save(session)/delete(id)/recordConsentIfPending(id, stepIndex, granted)`,
  `StartAgentSessionUseCase.start(goal): OperationResult<AgentSessionId?>`,
  `RunAgentSessionUseCase.run(session): OperationResult<AgentSession>`,
  `ResolveConsentUseCase.resolve(id, stepIndex, granted): OperationResult<AgentSession?>`,
  `CancelAgentSessionUseCase.cancel(id): OperationResult<Unit>`.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AgentSessionUseCasesTest {

    private val registry = FakeToolRegistry.withA0Tools()
    private val store = FakeAgentSessionStore()
    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }
    private val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))

    private fun runner(tools: FakeToolExecutor) =
        RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), store)

    @Test
    fun `start persists a running session and returns its id`() = runTest {
        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val id = (started as OperationResult.Success).value
        assertEquals(AgentSessionId("s1"), id)
        assertEquals(ExecutionState.Running, store.saved.last().state)
        assertTrue(store.saved.last().trace.events.any { it is com.sidr.launcher.domain.trace.TraceEvent.PlanCreated })
    }

    @Test
    fun `start returns null and persists nothing when there is no plan`() = runTest {
        val blank = AgentGoal("открой", GoalShape.AppNotInstalled("  "))

        val started = StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(blank)

        assertNull((started as OperationResult.Success).value)
        assertTrue(store.saved.isEmpty())
    }

    @Test
    fun `run stops at the consent checkpoint with the session persisted`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)

        val ran = runner(tools).run(store.active)

        assertEquals(ExecutionState.AwaitingConsent, (ran as OperationResult.Success).value.state)
        assertEquals(ExecutionState.AwaitingConsent, store.active.state)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a completed session is deleted, leaving nothing at rest`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val run = runner(tools)
        run.run(store.active)

        val resolved = ResolveConsentUseCase(store, run).resolve(AgentSessionId("s1"), 1, granted = true)

        assertEquals(ExecutionState.Completed, (resolved as OperationResult.Success).value?.state)
        assertNull(store.activeOrNull)
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), tools.invocations.map { it.id })
    }

    @Test
    fun `a second confirmation of the same step does not execute it twice`() = runTest {
        val tools = FakeToolExecutor(
            listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), ToolResult.Effected),
        )
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        val run = runner(tools)
        run.run(store.active)
        val consent = ResolveConsentUseCase(store, run)

        consent.resolve(AgentSessionId("s1"), 1, granted = true)
        val second = consent.resolve(AgentSessionId("s1"), 1, granted = true)

        assertNull((second as OperationResult.Success).value)
        assertEquals(2, tools.invocations.size)   // NOT 3
    }

    @Test
    fun `cancelling deletes the session and the next step never runs`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        runner(tools).run(store.active)

        CancelAgentSessionUseCase(store).cancel(AgentSessionId("s1"))

        assertNull(store.activeOrNull)
        assertEquals(1, tools.invocations.size)
    }

    @Test
    fun `a store failure is surfaced as a Failure and never thrown`() = runTest {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)))
        StartAgentSessionUseCase(TemplatePlanner(), store, ids, registry).start(goal)
        store.failNextSave = true

        val ran = runner(tools).run(store.active)

        assertTrue(ran is OperationResult.Failure)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*AgentSessionUseCasesTest'
```

Expected: FAIL — the use cases and the store port do not exist.

- [ ] **Step 3: Write the port**

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Port: persistence for the **one** active session. There is no history here — a terminal state
 * deletes the record, so at rest the store is empty. A durable trace journal is A5's decision, not
 * something A0 quietly introduces.
 */
interface AgentSessionStore {

    /** The active session, or `null` when none is in flight. */
    suspend fun active(): OperationResult<AgentSession?>

    suspend fun save(session: AgentSession): OperationResult<Unit>

    suspend fun delete(id: AgentSessionId): OperationResult<Unit>

    /**
     * Conditional write: records [granted] for [stepIndex] **only if** that step is still awaiting a
     * decision. Returns `true` iff it applied.
     *
     * This is where "a double confirmation must not execute a step twice" is actually solved. Not a
     * flag the UI checks and not a debounce — a conditional `UPDATE`, so two taps that race cannot
     * both win. Whole-object writes were avoided deliberately: that pattern already cost this project
     * the `autoHideNavBar` bug (DS-11), which only surfaced on device.
     */
    suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean>
}
```

- [ ] **Step 4: Write the four use cases**

`StartAgentSessionUseCase.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/** Goal -> plan -> a persisted `Running` session. `NoPlan` returns `null` and writes nothing. */
пукп    private val planner: Planner,
    private val store: AgentSessionStore,
    private val ids: AgentSessionIdFactory,
    private val registry: ToolRegistry,
) {
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        val planned = planner.plan(goal, registry)
        if (planned !is PlanningResult.Planned) return OperationResult.Success(null)

        val id = ids.newId()
        val session = AgentSession(
            id = id,
            goal = goal,
            plan = planned.plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(planned.plan.steps.size))),
        )

        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(id)
        }
    }
}
```

`RunAgentSessionUseCase.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/**
 * Turns the crank: `advance` + persist, until the session must stop. Every transition is saved before
 * the next one begins, which is what makes `am force-stop` recoverable rather than lossy.
 */
class RunAgentSessionUseCase(
    private val executor: AgentExecutor,
    private val store: AgentSessionStore,
) {
    suspend fun run(session: AgentSession): OperationResult<AgentSession> {
        var current = session
        while (current.state == ExecutionState.Running) {
            val next = executor.advance(current)
            // Safety net: a Running transition that changes nothing would spin forever. The engine has
            // no such branch today; this makes that a Blocked session rather than a hung device if a
            // future branch forgets to move the cursor or the state.
            if (next == current) {
                current = next.ended(ExecutionState.Blocked)
                return persist(current)
            }
            current = next
            when (val saved = store.save(current)) {
                is OperationResult.Failure -> return saved
                is OperationResult.Success -> Unit
            }
        }
        return persist(current)
    }

    private suspend fun persist(session: AgentSession): OperationResult<AgentSession> {
        val saved = store.save(session)
        if (saved is OperationResult.Failure) return saved
        if (session.state.isTerminal) {
            val deleted = store.delete(session.id)
            if (deleted is OperationResult.Failure) return deleted
        }
        return OperationResult.Success(session)
    }
}
```

`ResolveConsentUseCase.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Records one consent decision and resumes. Returns `null` when the decision did not apply — the step
 * was not awaiting one, which is exactly what a second tap on the same button looks like.
 */
class ResolveConsentUseCase(
    private val store: AgentSessionStore,
    private val run: RunAgentSessionUseCase,
) {
    suspend fun resolve(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<AgentSession?> {
        val applied = when (val write = store.recordConsentIfPending(id, stepIndex, granted)) {
            is OperationResult.Failure -> return write
            is OperationResult.Success -> write.value
        }
        if (!applied) return OperationResult.Success(null)

        val current = when (val active = store.active()) {
            is OperationResult.Failure -> return active
            is OperationResult.Success -> active.value ?: return OperationResult.Success(null)
        }

        val resumed = current
            .record(TraceEvent.ConsentResolved(stepIndex, granted))
            .copy(state = ExecutionState.Running)

        return when (val ran = run.run(resumed)) {
            is OperationResult.Failure -> ran
            is OperationResult.Success -> OperationResult.Success(ran.value)
        }
    }
}
```

`CancelAgentSessionUseCase.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.result.OperationResult

/** Cancels and deletes. A cancel that lands between transitions guarantees the next step never starts. */
class CancelAgentSessionUseCase(private val store: AgentSessionStore) {
    suspend fun cancel(id: AgentSessionId): OperationResult<Unit> = store.delete(id)
}
```

`AgentSession.record` and `AgentSession.ended` are `internal`, so `ResolveConsentUseCase` and
`RunAgentSessionUseCase` reach them only because they live in the same module — which is the intent.

- [ ] **Step 5: Write `FakeAgentSessionStore`**

```kotlin
package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult

/**
 * In-memory store mirroring the Room contract, including the conditional consent write — the property
 * the double-confirmation test depends on. [saved] keeps every write so tests can assert the sequence,
 * not only the end state.
 */
class FakeAgentSessionStore : AgentSessionStore {

    val saved = mutableListOf<AgentSession>()
    private var current: AgentSession? = null
    var failNextSave: Boolean = false

    val activeOrNull: AgentSession? get() = current
    val active: AgentSession get() = requireNotNull(current) { "no active session" }

    override suspend fun active(): OperationResult<AgentSession?> = OperationResult.Success(current)

    override suspend fun save(session: AgentSession): OperationResult<Unit> {
        if (failNextSave) {
            failNextSave = false
            return OperationResult.Failure(OperationError.UnknownError("save failed"))
        }
        saved += session
        current = session
        return OperationResult.Success(Unit)
    }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> {
        if (current?.id == id) current = null
        return OperationResult.Success(Unit)
    }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> {
        val session = current
        if (session == null || session.id != id) return OperationResult.Success(false)
        if (session.state != ExecutionState.AwaitingConsent) return OperationResult.Success(false)
        if (session.consents.containsKey(stepIndex)) return OperationResult.Success(false)

        current = session.copy(consents = session.consents + (stepIndex to granted))
        return OperationResult.Success(true)
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*AgentSessionUseCasesTest'
```

Expected: PASS, 7 tests. `a second confirmation ... does not execute it twice` asserting 2 invocations
rather than 3 is the acceptance criterion for double-resume, proven here before any device is involved.

- [ ] **Step 7: Run the whole domain suite and both targets**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest :domain:compileKotlinJvm :domain:compileDebugKotlinAndroid
```

Expected: PASS — the 315 pre-existing domain tests plus the new ones.

- [ ] **Step 8: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/ \
        core/testing/src/main/java/com/sidr/launcher/core/testing/FakeAgentSessionStore.kt
git commit -m "feat(agentic-4/A0): start/run/consent/cancel use cases and the session store port

Consent resolution is idempotent through a conditional write, so a double
confirmation cannot execute a step twice. Terminal sessions are deleted, so
nothing accumulates at rest."
```

---

# Phase 3 — Adapters and the cut

### Task 9: The one tool source and the executor over the unchanged action path

> ℹ️ **Task 5b is done (`08b632f`) — this task is unblocked, but its contract moved.** `ToolExecutor`
> now takes `ResolvedInvocation`, not `ToolInvocation`. And 5b Step 6 deliberately deferred half of its
> own work to here: `launch_app`'s descriptor must declare
> `outputSchema = listOf(ActionArg("resolved_query", …))`, and the adapter must return that value on
> **every** result — including `Effected` — because a tool's outputs are a property of the tool, not of
> the branch it happened to take. A schema that only sometimes holds is not a schema.

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolExecutor.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSourceTest.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolExecutorTest.kt`

**Interfaces:**
- Consumes: `ToolRegistry`, `ToolExecutor`, `ToolDescriptor`, `ToolInvocation`, `ToolResult`,
  `ObservedFact`, `ToolIds` (Task 5); the **unchanged** `ExecuteActionUseCase` and `ActionCatalog`.
- Produces: `SystemIntentToolSource(catalog: ActionCatalog) : ToolRegistry`,
  `SystemIntentToolExecutor(executeAction: ExecuteActionUseCase) : ToolExecutor`.

**Why the projection lives here and not in the type system:** this is the whole answer to fork F2. The
mapping `ActionDescriptor -> ToolDescriptor` is thirty lines in one adapter, so A1' can add sources
next to it *or* delete it and grow `ActionCatalog` in place. Neither bank is made cheaper by A0.

- [ ] **Step 1: Write the failing source test**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A0 registers exactly two tools, both projected from the real `ActionCatalog`. The projection is
 * deliberately narrow: registering all seven families would hand the planner capabilities the slice
 * has not gated or traced.
 */
class SystemIntentToolSourceTest {

    private val source = SystemIntentToolSource(DefaultActionCatalog())

    @Test
    fun `exactly two tools are registered`() {
        assertEquals(listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH), source.all().map { it.id })
    }

    @Test
    fun `risk is carried over from the catalog, not restated`() {
        assertEquals(ActionRiskLevel.SAFE, source.find(ToolIds.LAUNCH_APP)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(ToolIds.PLAY_STORE_SEARCH)!!.risk)
    }

    @Test
    fun `the argument schema is carried over from the catalog`() {
        assertEquals(listOf("query"), source.find(ToolIds.LAUNCH_APP)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(ToolIds.PLAY_STORE_SEARCH)!!.argSchema.map { it.name })
    }

    @Test
    fun `both A0 tools are transient - neither writes durable state`() {
        assertEquals(ToolDurability.TRANSIENT, source.find(ToolIds.LAUNCH_APP)!!.durability)
        assertEquals(ToolDurability.TRANSIENT, source.find(ToolIds.PLAY_STORE_SEARCH)!!.durability)
    }

    @Test
    fun `an action family that is not an A0 tool is not registered`() {
        assertNull(source.find(ToolId("web_search")))
        assertNull(source.find(ToolId("open_url")))
    }
}
```

- [ ] **Step 2: Write the failing executor test**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `SYSTEM_INTENT` tier: the agent's only path to the world runs through the **unchanged**
 * `ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain. No new executor surface.
 *
 * The load-bearing mapping is `Message(NoAppFound) -> ToolResult.Observed(APP_NOT_INSTALLED)`: what the
 * command pipeline treats as a dead end becomes the observation step 1's precondition reads.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemIntentToolExecutorTest {

    private val apps = FakeInstalledAppsRepository()
    private val actionExecutor = FakeActionExecutor()

    private fun executor() = SystemIntentToolExecutor(
        ExecuteActionUseCase(IntentActionResolver(apps), actionExecutor),
    )

    @Test
    fun `launching a missing app is an observation, not a failure`() = runTest {
        apps.apps = emptyList()

        val result = executor().invoke(ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED), result)
    }

    @Test
    fun `launching an installed app effects it`() = runTest {
        apps.apps = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))

        val result = executor().invoke(ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(ToolResult.Effected, result)
    }

    @Test
    fun `two apps with the same label are an ambiguity observation`() = runTest {
        apps.apps = listOf(
            InstalledApp(packageName = "com.a", label = "убер", activityName = "Main"),
            InstalledApp(packageName = "com.b", label = "убер", activityName = "Main"),
        )

        val result = executor().invoke(ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(ToolResult.Observed(ObservedFact.APP_AMBIGUOUS), result)
    }

    @Test
    fun `the store search effects`() = runTest {
        val result = executor().invoke(ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to "убер")))

        assertEquals(ToolResult.Effected, result)
    }

    @Test
    fun `an unmappable tool id fails closed and never reaches the action path`() = runTest {
        val result = executor().invoke(ToolInvocation(com.sidr.launcher.domain.tool.ToolId("nope")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, actionExecutor.executed.size)
    }

    @Test
    fun `an executor failure is carried through as a safe failure`() = runTest {
        apps.apps = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))
        actionExecutor.nextFailure = CommandFailure.Generic

        val result = executor().invoke(ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertTrue(result is ToolResult.Failed)
    }
}
```

Read `FakeInstalledAppsRepository`, `FakeActionExecutor` and `InstalledApp` before writing this file and
match their real property names; the fields above (`apps`, `executed`, `nextFailure`) are the shape to
look for, not a guarantee of the exact names.

- [ ] **Step 3: Run both to verify they fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*SystemIntentTool*'
```

Expected: FAIL — neither class exists.

- [ ] **Step 4: Write the source**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * A0's single tool source: a projection of the existing [ActionCatalog] into the two tools this slice
 * registers. The registry is a list of sources; A1' turns it into a federation by adding adapters
 * beside this one, or discards the projection and grows the catalog — **A0 decides neither**, and the
 * mapping lives here, in thirty lines of adapter, precisely so both remain cheap.
 *
 * Only two of the seven families are projected. Registering all seven would hand the planner
 * capabilities this slice has neither gated, traced, nor accepted on a device.
 */
class SystemIntentToolSource @Inject constructor(
    private val catalog: ActionCatalog,
) : ToolRegistry {

    private val projected: List<ToolDescriptor> by lazy {
        listOfNotNull(
            project(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP),
            project(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH),
        )
    }

    override fun all(): List<ToolDescriptor> = projected

    override fun find(id: ToolId): ToolDescriptor? = projected.firstOrNull { it.id == id }

    private fun project(actionId: String, toolId: ToolId): ToolDescriptor? {
        val descriptor = catalog.descriptor(com.sidr.launcher.domain.action.ActionId(actionId)) ?: return null
        return ToolDescriptor(
            id = toolId,
            argSchema = descriptor.argSchema,
            risk = descriptor.risk,
            // Neither A0 tool writes durable state: launching an app and opening a store page both
            // leave nothing to undo. When A1' registers a tool that does, it declares DURABLE and the
            // consent gate picks it up with no change to the engine.
            durability = ToolDurability.TRANSIENT,
            permissionGate = descriptor.permissionGate,
        )
    }
}
```

- [ ] **Step 5: Write the executor**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import javax.inject.Inject

/**
 * The `SYSTEM_INTENT` tier of the agent's world: the **unchanged**
 * `ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain, wearing the [ToolExecutor]
 * contract. No new executor surface is introduced, which is why the launcher's existing behaviour is
 * unaffected by construction rather than by testing.
 *
 * The mapping that matters is `Message(NoAppFound) -> Observed(APP_NOT_INSTALLED)`. To the command
 * pipeline "no such app" is a terminal message; to the agent it is an observation, and the whole
 * two-step plan hangs off that reinterpretation.
 */
class SystemIntentToolExecutor @Inject constructor(
    private val executeAction: ExecuteActionUseCase,
) : ToolExecutor {

    override suspend fun invoke(invocation: ToolInvocation): ToolResult {
        val action = actionFor(invocation)
            ?: return ToolResult.Failed(CommandFailure.Generic)
        return map(executeAction.execute(action))
    }

    private fun actionFor(invocation: ToolInvocation): LauncherAction? {
        val query = invocation.args["query"]?.trim().orEmpty()
        if (query.isEmpty()) return null
        return when (invocation.id) {
            ToolIds.LAUNCH_APP -> LauncherAction.LaunchApp(query)
            ToolIds.PLAY_STORE_SEARCH -> LauncherAction.PlayStoreSearch(query)
            else -> null
        }
    }

    private fun map(outcome: CommandOutcome): ToolResult = when (outcome) {
        CommandOutcome.Executed -> ToolResult.Effected
        is CommandOutcome.NeedsConfirmation -> ToolResult.Observed(ObservedFact.APP_AMBIGUOUS)
        is CommandOutcome.Message ->
            if (outcome.message is CommandMessage.NoAppFound) {
                ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)
            } else {
                ToolResult.Failed(CommandFailure.Generic)
            }
        is CommandOutcome.Failed -> ToolResult.Failed(outcome.failure)
        // The two projected families cannot produce any other outcome. Failing closed rather than
        // adding branches keeps this adapter honest about what it actually maps.
        else -> ToolResult.Failed(CommandFailure.Generic)
    }
}
```

- [ ] **Step 6: Run both to verify they pass**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*SystemIntentTool*'
```

Expected: PASS, 11 tests.

- [ ] **Step 7: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ \
        data/repository/src/test/java/com/sidr/launcher/data/repository/agent/
git commit -m "feat(agentic-4/A0): the one tool source and the SYSTEM_INTENT executor

Two tools projected from the existing ActionCatalog, executed through an
unchanged ExecuteActionUseCase. 'No such app' becomes an observation rather
than a dead end, which is what makes the two-step plan a loop."
```

### Task 10: Room 3 -> 4 and the session store

> ℹ️ **Task 5b is done (`08b632f`), so the schema below is the right one — it is already post-F6.**
> `args_json` holds `ArgSource` (a `Literal`'s string, or a `FromStep`'s index and key) and **not**
> resolved values: a resumed plan must re-bind against the observations that survived, not replay a
> value frozen at plan time. `observation_output_json` holds the producing side. This was the migration
> whose window F6 had to beat — getting the shape right here is what that ordering bought.

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/AgentSessionEntity.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/AgentPlanStepEntity.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/entity/AgentTraceEventEntity.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/dao/AgentSessionDao.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/migrations/Migration3To4.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/RoomAgentSessionStore.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/db/SidrDatabase.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/` — wherever the Room builder adds migrations
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/db/AgentSessionDaoTest.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/RoomAgentSessionStoreTest.kt`
- Test: `data/repository/src/androidTest/java/com/sidr/launcher/data/repository/db/MigrationTest.kt` (extend)

**Interfaces:**
- Consumes: `AgentSessionStore` and every `domain/agent` + `domain/trace` type.
- Produces: `RoomAgentSessionStore(dao) : AgentSessionStore`; `AgentSessionDao` with
  `recordConsentIfPending(sessionId, stepIndex, granted): Int` returning rows updated.

**Schema (three tables, cascade on `session_id`):**

```sql
agent_session(
  id TEXT PRIMARY KEY, goal_text TEXT NOT NULL, goal_shape TEXT NOT NULL,
  goal_query TEXT NOT NULL, state TEXT NOT NULL, cursor INTEGER NOT NULL,
  created_at INTEGER NOT NULL)

agent_plan_step(
  session_id TEXT NOT NULL, step_index INTEGER NOT NULL, tool_id TEXT NOT NULL,
  args_json TEXT NOT NULL, risk TEXT NOT NULL, precondition_fact TEXT,
  rationale TEXT NOT NULL, observation_type TEXT, observation_fact TEXT,
  observation_output_json TEXT,
  consent INTEGER,
  PRIMARY KEY(session_id, step_index),
  FOREIGN KEY(session_id) REFERENCES agent_session(id) ON DELETE CASCADE)

agent_trace_event(
  session_id TEXT NOT NULL, seq INTEGER NOT NULL, type TEXT NOT NULL,
  step_index INTEGER, detail TEXT, at INTEGER NOT NULL,
  PRIMARY KEY(session_id, seq),
  FOREIGN KEY(session_id) REFERENCES agent_session(id) ON DELETE CASCADE)
```

`goal_shape` exists although A0 has one shape: adding a second shape must then be a migration, not a
silent reinterpretation of an existing column. Trace events are **flat columns rather than a JSON
blob**, so A5 ("trace as a surface") can query "the last N events" without a format conversion.

**`args_json` and `observation_output_json` are the two halves of one F6 pair** (Task 5b, spec §7).
`args_json` stores `ArgSource` — a `Literal`'s string, or a `FromStep`'s index and key — and **not**
resolved values: a resumed plan must re-bind against the observations that actually survived, not
replay a value frozen when the plan was written. `observation_output_json` stores the producing side.
A step with no output stores `NULL`, not `{}`, so "produced nothing" stays distinguishable from
"produced an empty map"; assert that in the DAO test rather than relying on the mapper's default.

**One named fidelity gap:** a `Failed` observation persists only its type, not the `CommandFailure`
variant, and restores as `CommandFailure.Generic`. In A0 that never round-trips — a failed step ends
the session, which is then deleted. If A4' starts keeping failed sessions, this column must widen.
Write that as a comment on the mapper, not only here.

- [ ] **Step 1: Write the failing DAO test**

```kotlin
package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.AgentSessionDao
import com.sidr.launcher.data.repository.db.entity.AgentPlanStepEntity
import com.sidr.launcher.data.repository.db.entity.AgentSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AgentSessionDaoTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AgentSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.agentSessionDao()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seedAwaitingConsent() {
        dao.upsertSession(
            AgentSessionEntity(
                id = "s1", goalText = "открой убер", goalShape = "AppNotInstalled",
                goalQuery = "убер", state = "AwaitingConsent", cursor = 1, createdAt = 1L,
            ),
        )
        dao.upsertStep(
            AgentPlanStepEntity(
                sessionId = "s1", stepIndex = 1, toolId = "play_store_search",
                argsJson = """{"query":"убер"}""", risk = "CONFIRM",
                preconditionFact = "APP_NOT_INSTALLED", rationale = "APP_NOT_INSTALLED_FALLBACK",
                observationType = null, observationFact = null, consent = null,
            ),
        )
    }

    @Test
    fun `the first consent write applies`() = runTest {
        seedAwaitingConsent()

        assertEquals(1, dao.recordConsentIfPending("s1", 1, granted = true))
    }

    @Test
    fun `a second consent write for the same step applies to nothing`() = runTest {
        seedAwaitingConsent()
        dao.recordConsentIfPending("s1", 1, granted = true)

        assertEquals(0, dao.recordConsentIfPending("s1", 1, granted = true))
    }

    @Test
    fun `consent cannot be written while the session is not awaiting it`() = runTest {
        seedAwaitingConsent()
        dao.updateState("s1", "Running")

        assertEquals(0, dao.recordConsentIfPending("s1", 1, granted = true))
    }

    @Test
    fun `deleting the session cascades to steps and trace`() = runTest {
        seedAwaitingConsent()

        dao.deleteSession("s1")

        assertNull(dao.activeSession())
        assertEquals(0, dao.stepsFor("s1").size)
        assertEquals(0, dao.traceFor("s1").size)
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*AgentSessionDaoTest'
```

Expected: FAIL — the entities, the DAO and `agentSessionDao()` do not exist.

- [ ] **Step 3: Write the three entities**

Follow `AliasEntity` exactly: `@Entity(tableName = ..., primaryKeys = [...])` with `@ColumnInfo(name =
"snake_case")` on every field. Declare the two children's foreign keys:

```kotlin
@Entity(
    tableName = "agent_plan_step",
    primaryKeys = ["session_id", "step_index"],
    foreignKeys = [
        ForeignKey(
            entity = AgentSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["session_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class AgentPlanStepEntity(
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "step_index") val stepIndex: Int,
    @ColumnInfo(name = "tool_id") val toolId: String,
    @ColumnInfo(name = "args_json") val argsJson: String,
    @ColumnInfo(name = "risk") val risk: String,
    @ColumnInfo(name = "precondition_fact") val preconditionFact: String?,
    @ColumnInfo(name = "rationale") val rationale: String,
    @ColumnInfo(name = "observation_type") val observationType: String?,
    @ColumnInfo(name = "observation_fact") val observationFact: String?,
    @ColumnInfo(name = "consent") val consent: Boolean?,
)
```

`AgentSessionEntity` and `AgentTraceEventEntity` follow the SQL above with the same conventions.

- [ ] **Step 4: Write the DAO**

```kotlin
@Dao
interface AgentSessionDao {

    @Query("SELECT * FROM agent_session LIMIT 1")
    suspend fun activeSession(): AgentSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: AgentSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStep(entity: AgentPlanStepEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrace(entities: List<AgentTraceEventEntity>)

    @Query("SELECT * FROM agent_plan_step WHERE session_id = :id ORDER BY step_index")
    suspend fun stepsFor(id: String): List<AgentPlanStepEntity>

    @Query("SELECT * FROM agent_trace_event WHERE session_id = :id ORDER BY seq")
    suspend fun traceFor(id: String): List<AgentTraceEventEntity>

    @Query("UPDATE agent_session SET state = :state WHERE id = :id")
    suspend fun updateState(id: String, state: String)

    @Query("DELETE FROM agent_session WHERE id = :id")
    suspend fun deleteSession(id: String)

    /**
     * The conditional write that makes a double confirmation harmless. Two taps that race both run
     * this statement; the second matches no row because `consent IS NULL` no longer holds, and the
     * caller sees 0. No flag, no debounce, no window in which both can win.
     */
    @Query(
        """
        UPDATE agent_plan_step SET consent = :granted
        WHERE session_id = :sessionId
          AND step_index = :stepIndex
          AND consent IS NULL
          AND EXISTS (SELECT 1 FROM agent_session WHERE id = :sessionId AND state = 'AwaitingConsent')
        """,
    )
    suspend fun recordConsentIfPending(sessionId: String, stepIndex: Int, granted: Boolean): Int
}
```

- [ ] **Step 5: Register the entities, bump the version, write the migration**

In `SidrDatabase.kt`: add the three entities, `version = 4`, and `abstract fun agentSessionDao(): AgentSessionDao`.
Update the KDoc the same way versions 2 and 3 were documented.

`Migration3To4.kt` mirrors `Migration2To3`'s shape with three `CREATE TABLE IF NOT EXISTS` statements
matching the SQL above **exactly** — Room compares the migrated schema against the generated one and
fails loudly on any drift. Register it wherever `Migration1To2` and `Migration2To3` are passed to the
Room builder.

- [ ] **Step 6: Run the DAO test to verify it passes and commit the golden schema**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*AgentSessionDaoTest'
git status --short data/repository/schemas/
```

Expected: PASS, 4 tests, and `data/repository/schemas/4.json` appears as a new file. Commit it —
`1.json`, `2.json` and `3.json` stay frozen.

- [ ] **Step 7: Write the store and its mappers**

`RoomAgentSessionStore` implements the four `AgentSessionStore` methods. `save` writes the session row,
all step rows (including observations and consents) and the trace rows in **one** `@Transaction`, so a
crash cannot leave a cursor ahead of its trace. `delete` deletes the session row only — the children
cascade. `active` reassembles an `AgentSession` from the three tables. Every method wraps its body in
`runCatching` and returns `OperationResult.Failure(OperationError.UnknownError(...))` on a throw:
nothing from this layer may throw to the caller.

Mapper rules, all as explicit `when` expressions in both directions — no reflection, no
`enumValueOf` on unvalidated input:

- `ExecutionState`, `ActionRiskLevel`, `ObservedFact`, `StepRationale`, `RejectionReason`,
  `ConsentReason` map to and from their `name`.
- args map to `args_json` with `kotlinx.serialization` (`Json.encodeToString(MapSerializer(...))`) —
  the module already has the plugin and the dependency.
- a `TraceEvent` becomes `(type, step_index, detail)`: `type` is the class's simple name, `detail` is
  the single extra value that variant carries (a reason name, a tool id, a precondition fact, a
  granted flag, a state name, a step count) or `null`.
- `at` is stamped here, in the data layer. The domain has no clock, deliberately.
- an unknown string on the way **in** returns `OperationResult.Failure`, never a guessed default. A
  corrupt row must not silently become a runnable plan.

- [ ] **Step 8: Write the store round-trip test**

`RoomAgentSessionStoreTest` (Robolectric, same setup as the DAO test):

- a session saved and re-read is `assertEquals`-equal to the original, trace and observations included;
- `active()` returns `null` on an empty database;
- after `delete`, `activeSession()`, `stepsFor` and `traceFor` are all empty — the at-rest guarantee;
- `recordConsentIfPending` returns `true` once and `false` on the second call for the same step;
- a row with an unrecognised `state` string yields `OperationResult.Failure`, not a crash and not a
  guessed state.

- [ ] **Step 9: Extend the instrumented migration test**

Add a 3 -> 4 case to `data/repository/src/androidTest/.../MigrationTest.kt` following the existing
1 -> 2 and 2 -> 3 cases. This one needs a device or emulator and is **not** part of the unit gate; run
it when one is available and say so plainly in the ADR if it has not been run.

- [ ] **Step 10: Run the module suite**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest
```

Expected: PASS, including every pre-existing test in the module.

- [ ] **Step 11: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/db/ \
        data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ \
        data/repository/src/test/java/com/sidr/launcher/data/repository/ \
        data/repository/src/androidTest/java/com/sidr/launcher/data/repository/db/MigrationTest.kt \
        data/repository/schemas/4.json app/src/main/java/com/sidr/launcher/di/
git commit -m "feat(agentic-4/A0): Room 3->4 and the agent session store

One active session, deleted by cascade on any terminal state, so nothing
accumulates at rest. Consent is a conditional UPDATE, which is what makes a
double confirmation apply to nothing instead of running a step twice."
```

### Task 11: The cut into the command pipeline

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/intent/CommandOutcome.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt`
- Create: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCaseTest.kt` (extend)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/AgentOutcomePassThroughTest.kt`

**Interfaces:**
- Consumes: `StartAgentSessionUseCase` (Task 8).
- Produces: `CommandOutcome.AgentSessionStarted(val id: AgentSessionId)`; `RouteCommandUseCase` gains a
  `startAgentSession: StartAgentSessionUseCase` constructor parameter.

- [ ] **Step 1: Write the failing router tests**

Append to `RouteCommandUseCaseTest`:

```kotlin
@Test
fun `a command for an app that is not installed becomes an agent session`() = runTest {
    appsRepo.apps = emptyList()

    val outcome = useCase.route("открой убер")

    assertTrue(outcome is CommandOutcome.AgentSessionStarted)
}

@Test
fun `the agent runs in local-only mode and the model planner is still never consulted`() = runTest {
    appsRepo.apps = emptyList()
    flags.set(FeatureFlags(localOnlyMode = true))

    val outcome = useCase.route("открой убер")

    assertTrue(outcome is CommandOutcome.AgentSessionStarted)
    assertEquals(0, planner.calls.size)
}

@Test
fun `the agent runs offline and with no provider configured, and nothing is transmitted`() = runTest {
    appsRepo.apps = emptyList()
    connectivity.online = false
    providerConfig.set(null)

    val outcome = useCase.route("открой убер")

    assertTrue(outcome is CommandOutcome.AgentSessionStarted)
    assertEquals(0, planner.calls.size)
}

@Test
fun `when the agent produces no plan the FastPath outcome is returned byte-for-byte`() = runTest {
    appsRepo.apps = emptyList()
    val noPlanUseCase = buildUseCase(planner = neverPlans())

    val outcome = noPlanUseCase.route("открой убер")

    assertEquals(CommandOutcome.Message(CommandMessage.NoAppFound("убер")), outcome)
}

@Test
fun `an outcome FastPath decided and achieved is untouched by the agent`() = runTest {
    appsRepo.apps = listOf(InstalledApp("com.uber", "убер", "Main"))

    val outcome = useCase.route("открой убер")

    assertEquals(CommandOutcome.Executed, outcome)
}
```

Match the existing fixture's real member names (`flags`, `connectivity`, `providerConfig`, `planner`,
`appsRepo`) — read the top of the file first; the names above are its current shape.

- [ ] **Step 2: Write the failing pass-through test**

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/memory/AgentOutcomePassThroughTest.kt`:

```kotlin
package com.sidr.launcher.domain.memory

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.intent.CommandOutcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Neither memory decorator matches exhaustively over `CommandOutcome`: each passes what it does not
 * recognise straight through by an early return. That is the behaviour A0 wants, and it is **silent** —
 * adding `AgentSessionStarted` compiled clean and would have compiled clean if a future edit broke the
 * pass-through. Hence this test rather than trust in the shape of the code.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AgentOutcomePassThroughTest {

    private val agentOutcome = CommandOutcome.AgentSessionStarted(AgentSessionId("s1"))

    @Test
    fun `the learned-resolution decorator passes an agent session through untouched`() = runTest {
        // Build ResolveCommandWithPreferenceUseCase over a CommandRouteStep that returns agentOutcome,
        // with an empty FakeResolutionPreferenceStore, and assert the resolved outcome is identical.
    }

    @Test
    fun `the alias decorator passes an agent session through untouched`() = runTest {
        // Build ResolveCommandWithAliasUseCase over a ResolvedCommandStep returning agentOutcome,
        // with an empty FakeAliasStore, and assert the resolved outcome is identical.
    }
}
```

Fill both bodies using the constructor shapes in `MemoryProvidesModule.kt` and the existing
`ResolveCommandWithPreferenceUseCaseTest` fixtures. Assert `assertEquals(agentOutcome, result.outcome)`
and that no store was consulted.

- [ ] **Step 3: Run both to verify they fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*RouteCommandUseCaseTest' --tests '*AgentOutcomePassThroughTest'
```

Expected: FAIL — `CommandOutcome.AgentSessionStarted` does not exist.

- [ ] **Step 4: Add the outcome variant**

```kotlin
/**
 * A goal FastPath decided but did not achieve — the app does not exist — has been handed to the agent,
 * and [id] identifies the session now in flight. The command surface stops rendering a message and
 * starts rendering session state.
 */
data class AgentSessionStarted(val id: AgentSessionId) : CommandOutcome
```

Compilation will now fail in `LauncherViewModel`'s (post-split: `LauncherCommandSession`'s)
`applyOutcome`, whose `when` has no `else`. Leave it failing — Task 12 writes that branch. Everything
else compiles, because the two decorators pass unrecognised outcomes through by early return.

- [ ] **Step 5: Write the cut**

In `RouteCommandUseCase.route`, immediately after the FastPath call:

```kotlin
// (2) A goal FastPath DECIDED but did not ACHIEVE: it understood "open X" and found no such app.
// This is the one outcome A0 hands to the agent — not "any Message", not "anything that did not
// execute". The branch sits above the localOnlyMode check on purpose: the planner here is
// deterministic and offline, so it consults no model and transmits nothing, which is what the
// amended DOC-ADL-3 requires and what the signed local-only copy promises ("nothing leaves this
// device"). Widening this list is a separate decision for a later block.
val message = (ruleOutcome as? CommandOutcome.Message)?.message
if (message is CommandMessage.NoAppFound) {
    val goal = AgentGoal(
        text = rawInput.trim(),
        shape = GoalShape.AppNotInstalled(message.query),
    )
    // Fails open: NoPlan, or any store failure, leaves the FastPath outcome exactly as it was.
    val started = startAgentSession.start(goal)
    if (started is OperationResult.Success && started.value != null) {
        return CommandOutcome.AgentSessionStarted(started.value)
    }
}
```

Renumber the existing comments (3)–(7) and extend the class KDoc so the ordered chain still reads
correctly, including why the new branch does not disturb the mutual exclusivity of the three
understanding-unavailable messages: it is keyed on an outcome FastPath produced, not on a system state.

- [ ] **Step 6: Wire the graph**

Create `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` binding, all `@Singleton`:
`ToolRegistry` -> `SystemIntentToolSource`, `ToolExecutor` -> `SystemIntentToolExecutor`,
`AgentSessionStore` -> `RoomAgentSessionStore`, `Planner` -> `TemplatePlanner`,
`AgentSessionIdFactory` -> a `UUID.randomUUID().toString()` implementation, `AgentExecutor` with
`RuntimeBudget.Default`, and the four use cases. Add `startAgentSession` to
`RouterProvidesModule.provideRouteCommandUseCase`.

- [ ] **Step 7: Run the domain tests**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest
```

Expected: PASS. `:app` and `:feature:launcher` still fail to compile until Task 12 — that is the
intended, visible consequence of the exhaustive `when`.

- [ ] **Step 8: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/ \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ \
        app/src/main/java/com/sidr/launcher/di/
git commit -m "feat(agentic-4/A0): hand a decided-but-unachieved goal to the agent

RouteCommandUseCase gains one branch, keyed on NoAppFound and placed above the
local-only check: the A0 planner is deterministic and offline, so it consults no
model and transmits nothing. Fails open to the FastPath outcome on NoPlan."
```

---

# Phase 4 — Surface, guards, doctrine, acceptance

### Task 12: The execution surface

**Files:**
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSession.kt`
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt`
- Create: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherScreen.kt`
- Modify: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/LauncherAgentSessionTest.kt`

**Interfaces:**
- Consumes: the four use cases and `AgentSessionStore` (Task 8), `CommandOutcome.AgentSessionStarted`
  (Task 11).
- Produces: the seventh collaborator
  `internal class LauncherAgentSession(runSession, resolveConsent, cancelSession, store, scope)` with
  `val session: StateFlow<AgentSession?>`, `fun attach(id: AgentSessionId)`,
  `fun restoreOnStart()`, `fun continueSession()`, `fun confirm(stepIndex: Int)`,
  `fun deny(stepIndex: Int)`, `fun cancel()`.

**Copy classification, stated rather than assumed.** The new strings are **Class A** (ordinary UI), not
Class B. Class B is the owner-signed privacy/consent disclosure set in `strings_locked.xml`; the agent's
step confirmation is a *risk* gate, the same class as the AIL-5 confirm card, whose copy has always been
Class A. No new owner signature is required by this task. If the owner wants the agent's consent line
owner-reviewed, that is a separate decision — flag it, do not decide it here.

- [ ] **Step 1: Write the failing collaborator test**

```kotlin
package com.sidr.launcher.feature.launcher.agent

import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The surface is a function of state. These tests pin the two behaviours a user would notice and that
 * no unit test elsewhere covers: a session that survived process death is presented as `Paused` with an
 * offer, and confirming twice does not run the step twice.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LauncherAgentSessionTest {

    @Test
    fun `a session found at startup is presented as Paused, never silently resumed`() = runTest {
        // Seed FakeAgentSessionStore with a Running session at cursor 1, build LauncherAgentSession,
        // call restoreOnStart(), and assert session.value?.state == ExecutionState.Paused and that the
        // tool executor was never invoked.
    }

    @Test
    fun `continuing a paused session that owes consent returns to AwaitingConsent`() = runTest {
        // restoreOnStart() then continueSession(); assert AwaitingConsent and zero new invocations.
    }

    @Test
    fun `confirming the same step twice invokes the tool once`() = runTest {
        // confirm(1) twice; assert the FakeToolExecutor recorded exactly one store invocation.
    }

    @Test
    fun `cancelling clears the surface and deletes the session`() = runTest {
        // cancel(); assert session.value == null and store.activeOrNull == null.
    }
}
```

Fill the bodies with `FakeAgentSessionStore`, `FakeToolExecutor` and `FakeToolRegistry` from Phase 2 —
every collaborator here takes ports, so no Android or Compose is needed.

- [ ] **Step 2: Run it to verify it fails**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest --tests '*LauncherAgentSessionTest'
```

Expected: FAIL — `LauncherAgentSession` does not exist; the module also still fails to compile from
Task 11's exhaustive `when`.

- [ ] **Step 3: Write the collaborator**

```kotlin
package com.sidr.launcher.feature.launcher.agent

/**
 * The seventh collaborator. It exists *because* Phase 1 happened: the agent runtime is a peer of the
 * command session and the app list, not a tenth responsibility inside an 886-line ViewModel.
 *
 * A session that outlived its process is presented as [ExecutionState.Paused] and never resumed
 * silently: the user asked for a two-step action minutes or days ago, and continuing without asking
 * would be the system deciding on their behalf. `continueSession()` records `SessionResumed`, sets
 * `Running`, and lets the engine re-evaluate — which puts a pending consent checkpoint back on screen
 * rather than stepping past it.
 */
internal class LauncherAgentSession(
    private val runSession: RunAgentSessionUseCase,
    private val resolveConsent: ResolveConsentUseCase,
    private val cancelSession: CancelAgentSessionUseCase,
    private val store: AgentSessionStore,
    private val scope: CoroutineScope,
) {
    private val _session = MutableStateFlow<AgentSession?>(null)
    val session: StateFlow<AgentSession?> = _session.asStateFlow()

    fun attach(id: AgentSessionId) = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        if (active.id != id) return@launch
        _session.value = (runSession.run(active) as? OperationResult.Success)?.value
    }

    /** Called once from the ViewModel's init. Anything that survived is Paused, with an honest offer. */
    fun restoreOnStart() = scope.launch {
        val active = (store.active() as? OperationResult.Success)?.value ?: return@launch
        val paused = active.pausedForRestore()
        store.save(paused)
        _session.value = paused
    }

    fun continueSession() = scope.launch {
        val paused = _session.value ?: return@launch
        _session.value = (runSession.run(paused.resumed()) as? OperationResult.Success)?.value
    }

    fun confirm(stepIndex: Int) = resolve(stepIndex, granted = true)

    fun deny(stepIndex: Int) = resolve(stepIndex, granted = false)

    private fun resolve(stepIndex: Int, granted: Boolean) = scope.launch {
        // A second tap resolves to null: the conditional write matched no row. Keep the current
        // surface rather than blanking it — the user pressed a button that had already been honoured.
        val next = (resolveConsent.resolve(requireNotNull(_session.value).id, stepIndex, granted)
            as? OperationResult.Success)?.value
        if (next != null) _session.value = next
    }

    fun cancel() = scope.launch {
        _session.value?.let { cancelSession.cancel(it.id) }
        _session.value = null
    }
}
```

`pausedForRestore()` and `resumed()` are two small `internal` helpers added next to `AgentSession` in
`:domain` — they record `SessionPaused` / `SessionResumed` and set the state, so the trace keeps its 1:1
property across a restart. Add a domain test for the pair in `AgentExecutorTest`'s file or its own.

- [ ] **Step 4: Write the presentation mapper**

`AgentSessionPresentation.kt` maps the typed domain values to string resources — this is the seam the
hard rule requires, and the reason `StepRationale` and `ObservedFact` are enums rather than sentences:

```kotlin
@Composable
internal fun StepRationale.label(query: String): String = when (this) {
    StepRationale.GOAL_DIRECT -> sidrString(R.string.launcher_agent_step_launch, query)
    StepRationale.APP_NOT_INSTALLED_FALLBACK -> sidrString(R.string.launcher_agent_step_store, query)
}
```

with equivalents for `ExecutionState` titles and `ConsentReason`.

- [ ] **Step 5: Write the surface**

`AgentSessionSurface.kt` — one composable per state, DS-5 primitives only, **nothing new in `core/ui`**:

| State | Primitive | Notes |
|---|---|---|
| `Planning` | `SidrProgress` | |
| `Running` | step rows + `SidrProvenanceLine` | the current step is marked |
| `AwaitingConsent` | `SidrActionGate(type = SidrActionGateType.Confirmation, title, consequence, confirmLabel, onConfirm, onCancel)` | `confirming` is bound to the in-flight flag, so it disables both controls |
| `Paused` | `SidrResultSurface(tone = ..., title, body, primaryAction = continue, secondaryAction = cancel)` | |
| `Completed` | `SidrResultSurface` | |
| `Failed` | `SidrErrorSurface` | |
| `Blocked` | `SidrBlockedState` | |
| `Cancelled` | nothing — the ordinary command surface returns | |

The step list is rendered from `session.plan.steps`, so a 2-step and a 7-step plan differ in list length
and in nothing else. Do not hard-code two rows.

- [ ] **Step 6: Write the `applyOutcome` branch**

In `LauncherCommandSession`:

```kotlin
is CommandOutcome.AgentSessionStarted -> {
    _commandFeedback.value = CommandFeedback.None
    agentSession.attach(outcome.id)
}
```

The module compiles again from here.

- [ ] **Step 7: Add the strings in all three locales**

`feature/launcher/src/main/res/values/strings.xml` and its `values-ru` / `values-tr` siblings, in the
same commit (`LocaleCompletenessGuardTest`):

`launcher_agent_title`, `launcher_agent_step_launch`, `launcher_agent_step_store`,
`launcher_agent_gate_title`, `launcher_agent_gate_consequence`, `launcher_agent_gate_confirm`,
`launcher_agent_cancel`, `launcher_agent_running_title`, `launcher_agent_paused_title`,
`launcher_agent_paused_body`, `launcher_agent_paused_continue`, `launcher_agent_completed_title`,
`launcher_agent_failed_title`, `launcher_agent_blocked_title`, `launcher_agent_blocked_body`.

The Paused copy must say what actually happened and what will happen — the user left mid-plan and is
being offered the remaining step, not being told something went wrong.

- [ ] **Step 8: Run the module suite and the i18n guards**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest :app:testDebugUnitTest
git diff --stat -- feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherViewModelTest.kt
```

Expected: PASS, including `LocaleCompletenessGuardTest`, `HardcodedUiTextGuardTest` and
`StringSeamGuardTest`. `LauncherViewModelTest` may now legitimately gain tests, but check the diff and
be able to say why every changed line changed — Phase 1's parity claim was about the refactor, and it
already holds in the history.

- [ ] **Step 9: Commit**

```bash
git add feature/launcher/src/main/ feature/launcher/src/test/ \
        domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt
git commit -m "feat(agentic-4/A0): the execution surface, on existing DS-5 primitives

One component per runtime state, composed from what the session reported, so a
2-step and a 7-step plan differ only in list length. A session that outlived its
process is presented as Paused with an offer, never resumed silently. en/ru/tr
ship here. Nothing new in core/ui."
```

### Task 13: The guards, and proving they are not vacuous

**Files:**
- Create: `app/src/test/java/com/sidr/launcher/agent/ToolExecutorCallSiteGuardTest.kt`
- Create: `app/src/test/java/com/sidr/launcher/agent/AgentVocabularyGuardTest.kt`
- Create: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentEgressSentinelGuardTest.kt`
- Create: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentAtRestGuardTest.kt`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: everything.
- Produces: four guards, each named in the doctrine matrix by Task 14.

- [ ] **Step 1: Declare the scanned sources as Gradle inputs — before writing the guards**

Two of these guards read `.kt` files outside `:app`'s source set. Gradle cannot know that, so the test
task stays `UP-TO-DATE` when only the scanned sources change and the guard silently does not run. This
already bit the project once (`§HANDOFF`: the doctrine matrix). Extend the existing block in
`app/build.gradle.kts`:

```kotlin
tasks.withType<Test>().configureEach {
    inputs.file(rootProject.file("docs/governing/sidr-doctrine-matrix-v1.0.md"))
        .withPropertyName("doctrineMatrix")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    // Этап 4 / A0. ToolExecutorCallSiteGuardTest and AgentVocabularyGuardTest scan these roots, which
    // are outside :app's source set — same UP-TO-DATE trap as the matrix above.
    inputs.dir(rootProject.file("domain/src/commonMain/kotlin"))
        .withPropertyName("domainSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("data/repository/src/main/java"))
        .withPropertyName("dataRepositorySources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(rootProject.file("feature/launcher/src/main/java"))
        .withPropertyName("launcherSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
```

- [ ] **Step 2: Write the call-site guard**

```kotlin
package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **The consent boundary, expressed mechanically.** The growth rule promises that tool #21 gets consent
 * for free. That promise is only true if there is exactly one place where a tool can be invoked, and
 * that place sits below the checkpoint. This test counts those places.
 *
 * It is a textual scan, deliberately: an architectural claim that a *reader* can check in ten seconds
 * is worth more here than one that needs a bytecode analyser. Working directory is the module dir, so
 * the repo root is `..` — same convention as the i18n and doctrine guards.
 */
class ToolExecutorCallSiteGuardTest {

    private val repoRoot = File("..")

    private val productionRoots = listOf(
        "domain/src/commonMain/kotlin",
        "data/repository/src/main/java",
        "feature/launcher/src/main/java",
        "app/src/main/java",
    ).map { File(repoRoot, it) }

    @Test
    fun `scanned roots all exist`() {
        // A guard whose walk finds nothing passes vacuously — the exact failure Этап 2 found in three
        // privacy guards. Assert the roots first.
        productionRoots.forEach { root ->
            assertEquals("missing scan root: $root", true, root.isDirectory)
        }
    }

    @Test
    fun `there is exactly one call site of ToolExecutor invoke in production code`() {
        val hits = productionRoots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }
            .flatMap { file ->
                file.readLines()
                    .withIndex()
                    .filter { (_, line) -> line.contains("toolExecutor.invoke(") }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }

        assertEquals(
            "ToolExecutor.invoke must have exactly one call site, below the consent checkpoint. Found: $hits",
            1,
            hits.size,
        )
    }

    @Test
    fun `the one call site lives in AgentExecutor`() {
        val files = productionRoots
            .flatMap { it.walkTopDown().filter { f -> f.extension == "kt" } }
            .filter { it.readText().contains("toolExecutor.invoke(") }
            .map { it.name }

        assertEquals(listOf("AgentExecutor.kt"), files)
    }
}
```

- [ ] **Step 3: Write the vocabulary guard**

Same shape. `domain/agent/` and `domain/tool/` production files must contain none of `LauncherAction`,
`ExecutableAction`, `ActionCatalog`, `ActionId`, `GenerativeAiEngine`, `CommandPlanner`, `io.ktor`,
`HttpClient`. `ActionArg`, `ActionRiskLevel` and `PermissionFeature` are explicitly allowed and the test
says so in a comment, because they are the reuse the spec sanctioned. Assert the two directories exist
before walking them.

`Planner.kt` names `ToolRegistry` with a fully-qualified reference; if that trips a substring check,
fix the import rather than loosening the guard.

- [ ] **Step 4: Write the egress sentinel guard**

```kotlin
package com.sidr.launcher.domain.agent

/**
 * A0's agent has no path off the device: the planner is local and both tools are system intents. This
 * proves it rather than asserting it — a sentinel planted in the goal text must reach no outbound
 * channel, in every state including the ones where a model *would* have been consulted.
 *
 * When A4' adds a model planner, this test is where its outbound path has to be reconciled with
 * `OutboundContextPolicy` — it will fail first, which is the point.
 */
class AgentEgressSentinelGuardTest {

    private val sentinel = "SIDR-EGRESS-SENTINEL-A0"

    // Run a full session whose goal text contains the sentinel, through RouteCommandUseCase with a
    // FakeCommandPlanner and a FakeGenerativeAiEngine, in all four states (local-only on/off x
    // online/offline), and assert: FakeCommandPlanner recorded zero calls, FakeGenerativeAiEngine
    // recorded zero calls, and no recorded outbound payload anywhere contains the sentinel.
}
```

- [ ] **Step 5: Write the at-rest guard**

`AgentAtRestGuardTest` (Robolectric, `:data:repository`): for **each** terminal state — `Completed`,
`Cancelled`, `Failed`, `Blocked` — drive a session to it through the real store and assert
`activeSession()` is null and `stepsFor` / `traceFor` are empty. The claim "at rest, the agent tables
are empty" is a privacy claim, so it is tested per state rather than once.

- [ ] **Step 6: Run all four guards**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*GuardTest' \
  :domain:jvmTest --tests '*GuardTest' \
  :data:repository:testDebugUnitTest --tests '*GuardTest'
```

Expected: PASS.

- [ ] **Step 7: Mutation-check every new guard — a green run proves nothing**

For each guard, break exactly what it should catch, confirm RED, then revert:

| Guard | Mutation | Expected |
|---|---|---|
| call-site | add a second `toolExecutor.invoke(...)` in a data-layer class | RED, listing both sites |
| call-site | rename `AgentExecutor.kt` | RED on the file-name assertion |
| call-site | point a scan root at a nonexistent directory | RED on `scanned roots all exist`, **not** a vacuous pass |
| vocabulary | import `LauncherAction` into `AgentExecutor.kt` | RED |
| sentinel | make the agent branch call the model planner | RED |
| at-rest | drop the `delete` from `RunAgentSessionUseCase.persist` | RED for all four states |
| Gradle inputs | revert the `inputs.dir` block, edit only a scanned source | task reports `UP-TO-DATE`; guard does not run — restore the block |

Record the result of each mutation in the ADR. `§HANDOFF` is explicit that a green run of a new guard
proves nothing on its own, and the last row is how you prove the `UP-TO-DATE` trap is actually closed.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/java/com/sidr/launcher/agent/ app/build.gradle.kts \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentEgressSentinelGuardTest.kt \
        data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentAtRestGuardTest.kt
git commit -m "test(agentic-4/A0): four guards, each mutation-verified

One call site to the world; no action vocabulary inside the engine; a planted
sentinel never leaves the device; the agent tables are empty at rest in every
terminal state. Scanned roots are declared as Gradle inputs so the guards cannot
silently skip on an incremental build."
```

### Task 14: The doctrine amendment, the ADR, and the document sync

**Files:**
- Modify: `docs/governing/sidr-doctrine-matrix-v1.0.md`
- Modify: `ai-context/decisions.md`
- Modify: `CLAUDE.md`
- Modify: `ai-context/current-status.md`
- Modify: `docs/superpowers/plans/2026-08-18-agentic-track-restart.md`

- [ ] **Step 1: Amend `DOC-ADL-3` and log it**

Replace the rule's text with the wording agreed in spec §8.1:

> local-only / no provider / offline ⇒ the **model planner** is not consulted and nothing leaves the
> device. Deterministic plan replay is part of the local path (rule 2 of the new rule) and may change an
> outcome FastPath decided; what may never change is that no model is consulted and nothing is
> transmitted.

Add a row to §6, the amendment journal, in the same voice as the 2026-08-20 row already there: what the
old text said, why A0 made its last clause false, that rule 5 of the new rule already put the plan cache
on the local path, and that the rule ID survives while the text does not.

- [ ] **Step 2: Give `DOC-ILM-3` and `DOC-HMA-2` real test names**

Replace their `<нет>` cells with the actual class names from Tasks 6 and 13. `DoctrineMatrixGuardTest`
fails the build on a claim naming a test that does not exist, so run it immediately:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*DoctrineMatrixGuardTest'
```

Leave `DOC-HMA-3`, `DOC-ILM-2`, `DOC-NYH-3` and `DOC-HMA-4` as `<нет>` with their debt owners. A matrix
that cannot show debt is a matrix where debt hides.

- [ ] **Step 3: Write the ADR**

Append to `ai-context/decisions.md`, following the format of the 2026-08-20 Этап 4.0 entry. It must
record: the five owner-resolved forks and the reasoning that moved the recommendation on F3; the eight
refinements Phase 2 made to the spec and why; the `DOC-ADL-3` amendment; that `DOC-HMA-3` is **not**
closed; the mutation results from Task 13 Step 7; the named gaps (no wall-clock budget; a persisted
`Failed` observation loses its `CommandFailure` variant; the 3 -> 4 migration test is instrumented and
may not have run); and the honest status label.

- [ ] **Step 4: Sync `CLAUDE.md`**

Update the stage table (Этап 4 -> ✅ with its status), the Shipped-surface list (the agent slice, and
that a FastPath "no such app" now becomes a two-step plan in every network state), the Known-debt list,
and the Contract -> Owner module table with `domain/tool`, `domain/agent`, `domain/trace`,
`AgentSessionStore` and the data-layer adapters. Add the Этап 4 entry to the history map. Do **not** put
history into `CLAUDE.md` — it points at the ADR.

- [ ] **Step 5: Sync `ai-context/current-status.md`**

Re-base on Этап 4 with the status vocabulary from Этап 0.5, naming what was and was not verified on a
device.

- [ ] **Step 6: Mark the stage and rewrite `§HANDOFF`**

In `docs/superpowers/plans/2026-08-18-agentic-track-restart.md`, mark the Этап 4 section ✅ with a
result paragraph, and rewrite `§HANDOFF` for the next session. It must carry forward at minimum: that
A1' owns the tool-levels and install-source work and that the A1 fork is still **not** to be decided;
that `DOC-ADL-3` has now been amended twice and its ID is the stable handle; the `UP-TO-DATE` Gradle
trap and that A0 closed it for two more roots; that `:domain:jvmTest` is not reached by
`testDebugUnitTest`; and every debt this block left open.

- [ ] **Step 7: Run the full gate**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :core:ui:verifyRoborazziDebug
```

Expected: BUILD SUCCESSFUL for both. Read the output. Do not pipe through `tail`. `verifyRoborazziDebug`
passing with no golden changes is the proof that `core/ui` was genuinely not touched.

- [ ] **Step 8: Commit**

```bash
git add docs/ ai-context/ CLAUDE.md
git commit -m "docs(agentic-4/A0): ADR, DOC-ADL-3 amendment, and document sync

DOC-ILM-3 and DOC-HMA-2 get real test names; DOC-HMA-3 stays an open debt.
DOC-ADL-3's parity clause is narrowed to what it always meant: no model is
consulted and nothing is transmitted."
```

### Task 15: Device acceptance and closing

This task is performed **with the owner**, on the SM-A325F. Nothing here is an agent claim.

- [ ] **Step 1: Install the release-configured debug build**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:installDebug
```

- [ ] **Step 2: Walk the eight acceptance items from spec §12**

1. type «открой <an app that is not installed>» — a two-step plan appears;
2. the gate stops execution at the risk transition, before the store step;
3. cancel mid-plan — the store step never runs;
4. the trace shows every step;
5. `adb shell am force-stop com.sidr.launcher` mid-plan, relaunch — the session is `Paused` with an
   honest offer;
6. double-tap continue/confirm — the step runs once;
7. `git diff` over the existing ViewModel suites is empty for the Phase 1 commits;
8. with the app **installed**, the second step is skipped and the result is `Completed`, not partial.

- [ ] **Step 3: Clear the inherited debt in the same session**

Этап 4.0 is still `CODE-GREEN` and `§HANDOFF` forbids declaring a later block `DEVICE-ACCEPTED` on top
of it. Check it now, on the same phone: `ru-RU`, no provider configured, type a command FastPath cannot
match — the honest message must appear, and **not** `Unknown command`. One minute.

- [ ] **Step 4: Record the honest status**

`DEVICE-ACCEPTED` only if the owner personally ran steps 2–3 and signed off. Otherwise `CODE-GREEN`,
with the outstanding check named in `CLAUDE.md` § Known debt. An agent-driven `adb`/`uiautomator` pass
does **not** count (Этап 0.5 vocabulary). Update the ADR, `CLAUDE.md` and `current-status.md` to
whichever is true.

- [ ] **Step 5: Propose the closing commit**

Propose message and scope to the owner; the owner says yes or no; the agent commits and **never**
pushes. Do not start A1' automatically — Master Plan §4 DoD: "the next block is not begun
automatically."

---

## Self-review notes

**Spec coverage.** Every spec section maps to a task: §3 proof goal -> Tasks 7, 9, 15; §4.1 -> Task 5;
§4.2–4.4 -> Task 6; §4.5 -> Task 8; §5 entry point -> Task 11; §6 boundaries 1–7 -> Tasks 5, 6, 13;
§7 persistence -> Task 10; §8 doctrine -> Task 14; §9 surface -> Task 12; §10 split -> Tasks 1–4;
§11 verification -> every task plus Task 13; §12 acceptance -> Task 15; §13 non-goals -> the Global
Constraints and the "not included" lists; §14 work order -> the phase order here.

**Deviations from the spec, all deliberate and argued in Phase 2's preamble:** `durability` replaces
`reversible`; `AgentGoal` gains a typed `shape`; `TraceEvent.StepRejected` is added; `ToolIds` exists
with a drift test; `AgentSessionIdFactory` is a port; trace events carry no timestamp; a declared
permission gate always stops for consent in A0; consecutive failures are derived rather than stored.
Task 14 records these in the ADR, and the spec is patched to match before implementation begins.

**Known thin spots, named rather than hidden.** Four test bodies are specified as precise prose rather
than literal code — Task 11 Step 2, Task 12 Step 1, Task 13 Steps 3–5 — because each depends on fixture
member names in files the implementer must read anyway, and a guessed name in a plan is worse than an
instruction to read. Every one of them states the exact assertions required.
