# A4′ Phase 0 — engine preconditions · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: written and reviewed 2026-09-22; corrected on the review's findings and committed
> 2026-09-25 (`0fe16b1`); reviewed a second time and corrected again 2026-09-25 (§0.6). Not started.** Branch `launcher--7`, base `074e4ce`, gate baseline **1440 / 0 / 0**.
> Spec: [§3 (0.0…0.4), §4, §9, §10 of
> 2026-09-21-a4-runtime-design.md](../specs/2026-09-21-a4-runtime-design.md) — the block's governing
> document, `ПОЛНА`, §0–§11 owner-approved 2026-09-21. Template: [A1″ phase
> 3b](2026-09-19-a1-phase3b-navigating-tools.md). ADR to **extend, not rewrite**, at block close:
> a new ADR «2026-09-2x — Этап 6 (A4′)».
>
> **This document was reviewed by a fresh agent before its first task and rewritten on the findings.**
> What changed is §0.5, and it is written down because a plan that erred is worth more as a record
> than as a clean-looking document: the review found sixteen defects, three of them blocking, and one
> of the three was a whole half of a spec item dropped silently.
>
> **It was then reviewed a second time on 2026-09-25, on the delta the first review never saw**
> (Task 3, the rewritten Task 8, the two-mode `tools/gate.sh`, and the parts of Tasks 1/4/6/9 added
> after it). That review found fifteen more, and — the part worth keeping — the two worst were in the
> **evidence machinery itself**: the script counted other modules' leftover XML, and the mutation
> blocks undid their own task's edits with `git checkout`. Both would have produced a mutation
> "proved RED" by something other than the named test. §0.6 records them; every fix below that
> could be run was run on a copy of the tree before it was written in.
>
> Phases 1–4 get their own plans, by the A1″ precedent (0–2 one plan, 3a its own, 3b its own).

**Goal:** make true, before the engine starts executing plans it has never executed, the five things
that are not true today: a third `PlanningResult` cannot silently become `NoPlan`; the planner's
input has a shape that can grow without a signature sweep; a tool that cannot see its own outcome
stops claiming one; the engine has a clock and a hanging step is bounded; and the argument sort `app`
stops being a string comparison.

**Architecture:** five independent seams, no new subsystem. `PlanningResult` gains an `else`-free
`when` at both consumers (a compile-time barrier, not a test). `Planner` takes one `PlanningRequest`
whose second field is phase 2's re-planning input. `ToolResult` gains a fourth value, `HandedOff`,
which is the honest answer both for `uninstall_app` (the OS dialog was raised; what the user then did
is not observable) and for a step cut by the wall-clock budget. The launcher surface stops collapsing
four realities into two words. `RuntimeBudget` gains a wall-clock bound enforced with `withTimeout`
at the one call site to the world. A parallel port in `:data:repository` — the `DynamicToolNames` /
`ToolPermissionCatalog` shape — replaces `ToolMatchPlanner`'s `const val APP_ARG = "app"`.

**Tech Stack:** Kotlin 2.4.10 / AGP 9.3.1 / Gradle 9.5.0 / JDK 17 toolchain · KMP `:domain`
(`commonMain` + `jvmTest`) · Jetpack Compose · Robolectric · Hilt · Room (schema **4, unchanged by
this phase**) · JUnit4 + `kotlinx-coroutines-test`.

---

## Global Constraints

Every task's requirements implicitly include this section. Values are copied verbatim from the spec
and from `CLAUDE.md`.

- **Gate command, both KMP modules listed explicitly:**
  `./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks`
  (verified 2026-09-22: that JDK path exists; `local.properties` carries no `java` line, so the `-P`
  flag is **required** — the machine's default JDK is 25 and Gradle cannot parse it).
- **The Gradle daemon is ON.** The former `--no-daemon` rule was retired by the owner on 2026-09-25 as
  no longer current; `gradle.properties` sets no `org.gradle.daemon` line, so the daemon is Gradle's
  default and nothing is passed. It matters here because this phase runs **fourteen mutations** (M3,
  M4a–c, M5, M6a–d, M7a–b, M8a–c), each its own Gradle invocation, and JVM startup plus configuration
  was being paid fourteen times.
  `CLAUDE.md`'s gate section was corrected in the same commit as this line, so the two cannot diverge.
  **What makes it safe is not the daemon's absence of state but two flags** — see the next bullet:
  `org.gradle.caching=true` is on in this repo, and the daemon keeps a watched file-system state
  between builds, so a scoped run that passed neither flag could be served a test task's
  pre-mutation XML `FROM-CACHE`.
- **Two gate modes, labelled differently on purpose** (Task 1). `tools/gate.sh` **boundary** — the
  default — clears `build/test-results` everywhere, passes `--rerun-tasks`, compares against 1440 and
  prints `GATE GREEN`/`GATE RED`. `tools/gate.sh --scoped :module:task …` clears, **counts and
  reports the named modules only** (another module's leftover XML is not this run's evidence), passes
  `--no-build-cache --no-watch-fs` instead of `--rerun-tasks`, reports how Gradle ran **each named
  task**, fails as `SCOPED NOT RUN` when a named test task did not execute, prints no baseline
  comparison, and labels itself `SCOPED GREEN … — NOT a boundary gate`. Both modes print one
  `FAILED :: <module> :: <class> > <test>` line per failing test. Intermediate TDD steps and every
  mutation run **scoped**; the run that closes a task is always **boundary**. A scoped run that
  printed `GATE GREEN` would be a new false-green generator, which is why the labels differ.
- **The mutation protocol — one shape for every mutation in this plan, and never `git checkout`**
  (§0.6 finding 2). A task is uncommitted while its mutations run, so `git checkout <file>` returns
  the file to HEAD and **erases the task's own edits in it**; on a file the task *created*, it fails
  with exit 1 and **leaves the mutation in place**. Both happened on paper in this plan. Instead:
  ```bash
  MUT="$(mktemp -d -t sidr-mut-XXXXXX)"   # once per task
  cp "$F" "$MUT/m6a"                      # the file as the task has it NOW — not HEAD
  sed -i '…' "$F"                         # plant
  tools/gate.sh --scoped …                # read the FAILED :: lines, not the label
  cp "$MUT/m6a" "$F"                      # restore exactly what was there
  ```
  **A mutation is proved by WHICH tests failed, not by `SCOPED RED`.** Each mutation below states
  its predicted `FAILED ::` set. A red whose set differs, or a `gradle=1` with **no** `FAILED ::`
  lines (a compile error), proves nothing: restore, find out why, and run it again.
- **Baseline is 1440.** Not 1439, not 1438, not 1375, not 1314, not any number in the A1″ ADR.
- **`--rerun-tasks`, never `--rerun`** (§9.1, R9). The latter is not a Gradle 9.5.0 build-level flag:
  it returns everything `UP-TO-DATE` and still prints `BUILD SUCCESSFUL`. A genuine run prints
  `N actionable tasks: N executed`.
- **Clear `build/test-results` before a boundary gate** (R14-44). Stale mutation XML is
  indistinguishable from a red gate and misleads in both directions.
- **Read counts from the JUnit XML, never from the console. Never pipe `gradlew` through `tail`** —
  that masked a red gate as exit 0 on 2026-07-13.
- **A `compileKotlin` task compiles PRODUCTION source sets only.** When a change to a sealed type is
  expected to redden `when` sites, the verification command must also name the **unit-test** compile
  tasks — `:data:repository:compileDebugUnitTestKotlin`, `:consumer:jvm:compileTestKotlin`,
  `:domain:compileTestKotlinJvm` — or half the sites stay invisible until the gate. This rule was
  bought by this plan's own review (§0.5, finding 2). **And naming them is not enough: a module never
  compiles while a module it depends on is red.** `:data:repository` and `:consumer:jvm` both depend
  on `:domain`, so one invocation over all six compile tasks reports `:domain`'s sites and nothing
  else — measured 2026-09-25: **2 lines, both the same `:domain` site**, not 8. A cross-module cost
  count is therefore taken **in stages, one dependency layer at a time, fixing between stages**, with
  `--continue` so independent modules in the same layer all report (§0.6 finding 6; Task 4 step 4).
- **Predict the per-module decomposition BEFORE each run and compare** (§9.3). A total cannot tell
  "two added" from "two added and two lost".
- **Every new guard is proved by a mutation on a named assert**, not by a green run — and "on a named
  assert" is read off the `FAILED ::` lines, per the mutation protocol above. **The last action of
  any mutation-proving task is a gate re-run from a cleared tree.**
- **Fixtures: no two expected values may share a substring** (R14-43). A realistic fixture is the
  dangerous one — a real package name usually contains the real label.
- **Frozen, not touched by this phase:** `ActionIds`, `ObservedFact`, `CommandFailure`, `GoalShape`,
  `ArgType`. (§10 criterion 10. `ToolResult` is **not** on that list — see §0.2 P2.)
- **`:domain` is `commonMain`**: stdlib + coroutines only, no Android, no `core/*`. Tests live in
  `jvmTest`.
- **`:core:android` has NO dependency injection.** Its `build.gradle.kts` carries `android.library` +
  `kotlin.android` and nothing else — no Hilt plugin, no kapt, no dagger, no `javax.inject`. A class
  there is a plain class, constructed by hand in an `:app` `@Provides`. Verified 2026-09-22.
- **Strings ship with the feature in `en`/`ru`/`tr`** (`LocaleCompletenessGuardTest`). New keys go in
  each module's ordinary `values*/strings.xml`, **never** `strings_locked.xml` — Class B keys carry
  an `OWNER-REVIEWED <date> sha256:<16 hex>` signature and adding one re-opens the release gate.
- **A line predicting what the user will see is a hypothesis until it has been run once**, and must
  be written in that tone (rule paid for three times on 2026-09-21).
- **The agent commits; the agent never pushes.**

---

## 0. Pre-flight scan of this plan against the tree

Phase 3a's pre-flight found six defects, phase 3b's found ten. This one found **eleven facts the spec
does not carry**, each read out of the tree rather than out of prose. Five of them change a task.

### 0.1 · The five owner forks of this phase, resolved 2026-09-22

The spec left four of these open with the words «фаза 0 **решает форму**». Asked before any code, per
`forks-before-code`.

| # | Fork | Owner's call |
|---|---|---|
| **F0-1** | 0.2 — what repairs `uninstall_app`'s lie about its outcome | **A fourth `ToolResult` value.** The trace on disk must stop lying too, not only the screen (`DOC-ILM-3`), and the spec itself names `ToolResult` as the seam |
| **F0-2** | 0.3 — does the wall-clock budget interrupt or merely record | **`withTimeout` — really interrupt.** The only reading of «ограничивает шаг» (criterion 3 §10) under which a hanging tool stops being bounded by nothing |
| **F0-3** | 0.3 — does phase 0 give the domain observation timestamps | **No.** Phase 0 decides and names both layers; the mechanism is phase 2's, exactly as §6.4 divides it. `AgentSession.observations` does not change type; no Room migration |
| **F0-4** | `tools/gate.sh` (§4 proposal 4+9, §9.2) | **Task 1 of this plan.** The window «сейчас, в окно» is now: the tree is green and still at `074e4ce` |
| **F0-5** | `PlanningRequest` — in phase 0, or deferred | **In phase 0.** This was never a fork in the spec: §3 0.4 says «**Решение: один `PlanningRequest` с двумя полями сегодня**», §4 names it as the worked example of building ahead of a consumer, and §9.3 lists it among what phase 0 touches in `:domain`. The first draft of this plan dropped it silently; the owner restored it. See §0.5 finding 1 |

### 0.2 · Eleven facts read out of the tree, five of which move a task

**(P1) `Effected` is a lie in exactly ONE tool, and the spec does not say so.** §3 0.2 reads as though
`Tier0IntentToolWorker.launch()` (`:327-334`) lies for everything it dispatches. It does not:

| Family | Is `Effected` true? | Read from |
|---|---|---|
| eleven navigating tools | **yes** — the effect *is* the screen opening, and `startActivity` returning proves it happened | `Tier0IntentToolWorker.kt:169-196` |
| `set_timer` / `set_alarm` | **yes** — the alarm is really created; `EXTRA_SKIP_UI = false` governs the responder's own UI, not consent (rows 13/29) | `setTimer` `:206`, `setAlarm` `:220`, and the descriptor KDoc carrying the measurement |
| **`uninstall_app`** | **no** — `Effected` is returned for the dialog merely being raised | `uninstallApp` `:263-267` → `launch` `:327-334` |

Consequence for Task 5: the fix is **one arm**, not a rewrite of `launch()`. Everything else in the
`when` is untouched, and a test must say so — otherwise the fix is unfalsifiable in the direction
that matters (fourteen tools silently downgraded).

**(P2) `ToolResult` is NOT pinned by `CoreVocabularyFreezeGuardTest`.** That guard holds exactly two
types — `ObservedFact` (two values) and `GoalShape` (two shapes). Neither is touched. §10 criterion
10's frozen list does not contain `ToolResult` either. So F0-1 needs no change-control; it needs the
cost paid, and the cost is (P3).

**(P3) The measured cost of a fourth `ToolResult` value: EIGHT `else`-free `when`s that the compiler
reddens, plus two string-keyed decode sites that it does not.** The first draft of this plan said
four, because it counted production source sets only. Four more live in test sources, and no
`compileKotlin` task reaches them.

| # | Site | File | Source set |
|---|---|---|---|
| 1 | `ToolResult.output()` | `domain/…/tool/InvocationValidator.kt:138-141` | production |
| 2 | `observationType` | `data/…/agent/AgentSessionMappers.kt:162-167` | production |
| 3 | `observationOutputJson` | `data/…/agent/AgentSessionMappers.kt:171-175` | production |
| 4 | `resultDto` | `consumer/jvm/…/store/SessionMapper.kt:127-131` | production |
| 5 | `ToolResult.branch()` | `data/…/agent/SystemIntentToolContractTest.kt:113-117` | **unit test** |
| 6 | `ToolResult.outputKeys()` | `data/…/agent/SystemIntentToolContractTest.kt:119-123` | **unit test** |
| 7 | `ToolResult.branch()` | `consumer/jvm/…/tool/SandboxToolContractTest.kt:83-87` | **unit test** |
| 8 | `ToolResult.outputKeys()` | `consumer/jvm/…/tool/SandboxToolContractTest.kt:89-93` | **unit test** |
| 9 | `readObservation` | `AgentSessionMappers.kt:326-334` | production, **compiler does NOT catch it** — keyed on a `String`, has an `else` |
| 10 | `result(dto)` | `SessionMapper.kt:133-141` | production, **same** — `else -> throw` |

Sites 9 and 10 are the ones a green suite would let through: a value written and never read back is a
session that decodes as corrupt. **No Room migration** — `observation_type`
(`AgentPlanStepEntity.kt:45`) is a string column, so schema 4 is unchanged; what a *new* value costs
is forward compatibility only (an older build reading a newer row throws `CorruptAgentRowException`,
which is the existing, correct behaviour).

**(P4) The surface's lie is wider than D1/D2 — there is a third instance, and no document names it.**
`AgentSessionPresentation.stateOf` (`:417-423`) reads `step.index == cursor -> CURRENT` **without a
single reference to `state`**. §3 0.2(1в) names `Paused` / `AwaitingConsent`. The same line also
renders the cursor step of a **terminal** session as «выполняется»: a `Failed` session has already
advanced its cursor past the failing step (`AgentExecutor.perform` writes `cursor = invoked.index + 1`
before `ended(Failed)`), so the next step reads `CURRENT` on a session that will never run again.
Task 6 closes all three with one rule, not three branches.

**(P5) Three existing tests pin the lie, and the plan must say which assertions change.** (The first
two drafts said two; the third is in a different file and was found by the second review, §0.6.)
- `AgentSessionPresentationTest:145-150` — `a plan whose every step ran is whole` asserts
  `AgentStepState.DONE` for `notInstalled`, an `Observed`. **Becomes `OBSERVED`**, and
  `everyStepExecuted()` stays `true` (the step did run).
- `AgentSessionPresentationTest:121-125` — `the step at the cursor is current…` builds a session with
  `state = ExecutionState.AwaitingConsent` and asserts `CURRENT`. **Becomes `WAITING`**, and its
  first assertion (step 0 is `DONE` for an `Observed`) **becomes `OBSERVED`**.
- `AgentSessionSurfaceProvenanceTest:164-170` — `the A0 two-step plan renders the two sentences it
  always has` renders `a0Session()` (`:111-125`: `state = ExecutionState.Paused`, `cursor = 0`) and
  asserts `"Open убер — in progress"` — D2's lie, rendered. **Becomes `"Open убер — waiting for
  you"`.** Its KDoc (`:150-162`) calls these two sentences **"the byte-identity baseline"** — what
  the surface the owner accepted on 2026-08-22 renders, pinned so A1′'s move of the step line could
  not change them. D2's fix changes one word of that accepted rendering **on purpose**: the subject
  (`Open убер`, `Find убер in the app store — not started`) stays byte-identical, the state word of a
  paused step does not. That is an owner-visible change and goes to the acceptance checklist as one.

**(P6) The wall-clock budget has a mechanically exact insertion point, and the catch ORDER is the
behaviour.** `AgentExecutor.perform` already wraps the one call site (`:223-229`) with
`catch (e: CancellationException) { throw e }` **above** `catch (e: Exception)`.
`TimeoutCancellationException` **is a** `CancellationException`, so a `withTimeout` whose catch is
placed second would be re-thrown and the timeout would kill the session instead of bounding the step.

**(P7) Nothing in the tree needs a clock port for the step bound.** `withTimeout` uses the coroutine
dispatcher's clock, which `runTest` makes virtual — so the test is deterministic and takes no real
time, and production uses real time with no injected dependency. The spec's second consumer for a
clock port is staleness, and F0-3 sent that to phase 2. `RuntimeBudget` gains a **value**, not a port.

**(P8) Three tools declare an `app` argument, and two also declare `app_label`** — so the sort
catalog of 0.4 has three rows, not one:

| Tool | `app` | `app_label` | Declared in |
|---|---|---|---|
| `uninstall_app` | required | `required = false` | `Tier0IntentToolSource.kt:226-227` |
| `open_app_info` | `required = true` (the R14-35/R14-37 pin) | — | `Tier0IntentToolSource.kt:414` |
| `set_app_alias` | required | `required = false` | `memory/MemoryToolSource.kt:47-49` |

**(P9) `usageHistoryEnabled` already has a Settings toggle** — `SettingsScreen.kt:233-238`, bound to
`uiState.usageHistoryEnabled`, under the **Home** section, not a debug surface. So §3 0.1(a)'s
standing action is a switch the owner flips, with **no code in this phase**.

**(P10) `ToolPermissionCatalog` holds exactly 20 rows, and they are exactly the authored set.**
Counted 2026-09-22. That makes it a second, independent production statement of "which tools are
authored" — which Task 1's census uses as a cross-check, so a census that forgets a source goes red
instead of printing a sixth diverging number.

**(P11) `ToolMatchPlannerTest`'s app-argument fixtures are narrower than they look, and three of its
tests will break.** Read before writing Task 8:
- the synthetic tool is `ToolId("test_app_tool")` (`:202`), and its selector `appSelector` (`:205`)
  is a **one-entry real `ToolVocabulary`** whose only trigger is `prefixByLocale = mapOf("ru" to
  setOf("удали приложение"))` with `argName = "app"`. Any goal text that is not «удали приложение …»
  matches nothing, and a test using one would decline for a reason unrelated to its subject.
  **«удали приложение» with nothing after it matches nothing either**: an entry that declares an
  `argName` refuses a bare trigger (`ToolVocabulary.kt:191`, `remainder.isBlank() -> null`).
  Measured 2026-09-25 on a copy of the tree: `appSelector.select("удали приложение")` is `null`. So
  **R14-37's shape — the vocabulary supplying no `app` — cannot be reached through `appSelector` at
  all**; Task 8 adds a second, bare-trigger selector for exactly that (§0.6 finding 5).
- **`ToolMatchPlanner(` is constructed at NINE sites in five files**, not three: `ToolMatchPlannerTest`
  `:54` (the class-level `planner`), `:244`, `:272`, `:276`, `:284`; `SelectionDeclineMeasurement:164`;
  `Tier0ToolExecutionEndToEndTest:151`; `FreeTextGoalEndToEndTest:143`; `AgentActingSeamTest:224`.
  A third constructor parameter breaks every one of them.
- `appToolDescriptor(vararg argNames)` (`:224`) hardcodes `required = name == "app"`, so an
  **optional** `app` cannot be expressed through it as written.
- three tests bind through the current literal and will break the moment the literal stops deciding:
  `:243` `an app argument is bound as a package…`, `:267` `an unresolvable app name yields NoPlan…`,
  `:283` `a descriptor that declares only app gets the package and no label`.
- the real helpers are `appToolDescriptor`, `registryWith(vararg)` (`:69`), `appSelector`,
  `appTargetsOf(...)`, `free(text)` (`:67`). There is no `descriptorWith`, `plannerFor`, `sortsWith`
  or `registryOf`.

### 0.3 · What this plan does NOT do — said, not implied

| Not here | Where |
|---|---|
| The doctrine line of §2 | **Owner's, unresolved.** It blocks **phase 1**'s close, not this phase. Not to be decided by an agent |
| FastPath overflow repair (52 %) | phase 1 |
| Rollback / compensation / re-planning | phase 2 — and phase 2 inherits this phase's honest step states (§6.0) and the second field of `PlanningRequest` (§6.4) |
| Staleness **mechanism** | phase 2, by F0-3. Both layers are **named** here (Task 7, step 9) |
| `B10` bridge, full sort system, `ArgType` | phase 3. This phase removes the literal only, and **F6 stays in force** — confirmed, not re-opened |
| Plan cache | phase 4 |
| A `PartiallyCompleted` execution state | §6.0 — partiality stays derivable; a value would be a second source of truth |
| 0.2(2), the inert `DURABLE_EFFECT` for `CONFIRM+` | **phase 2.** §6.1 sits inside the phase-2 chapter and says the repair is *not* a reordering of `checkpointFor`'s branches — A1″'s mutation M1a went red precisely because the loop stopped for a different reason, and that reason is load-bearing and behaviourally pinned |
| 0.2(3), restating entry 6's trigger | **block close, in the ADR.** §3 0.2(3) asks whether the trigger *has arrived*; this phase's answer — it has, in a form the trigger did not predict — is an ADR sentence, and it is carried in `§HANDOFF` (Task 10) so the close does not rediscover it |
| Any device round | phase 4 / block close. The block has **one** acceptance, at the end (§0 decision 1) |

### 0.4 · Owner actions this phase depends on (not agent work)

- [ ] **Turn `usageHistoryEnabled` ON on the SM-A325F** — Settings → Home → the usage-personalisation
  toggle (`SettingsScreen.kt:233-238`). §3 0.1(a): the table is **prospective, not retrospective**;
  it held 2 rows on 2026-09-21, both from the acceptance window. Without this there is no real usage
  to check phase 1's repair against. One switch; the instrument is already built.
- [ ] **The §2 doctrine line** — «ДОБАВИТЬ правило» per change-control §5, *not* a third amendment of
  `DOC-ADL-3`. Two honest readings are named in §2. **Phase 1 will not close without it**; phase 0
  does not need it.

### 0.5 · What the plan review of 2026-09-22 changed

A fresh agent reviewed this document against the spec and the tree before its first task, opening
every cited address. It found **three blockers and thirteen smaller defects**. All are fixed above and
below; they are listed because the pattern matters more than the corrections.

| # | Finding | Fixed where |
|---|---|---|
| **1** | **`PlanningRequest` — half of spec item 0.4 — was dropped silently**, and the self-review table marked 0.4 «yes». The spec decided it (§3 0.4), §4 uses it as *the* example of building ahead of a consumer, §9.3 lists it, §6.4 of phase 2 opens by consuming it | **Task 3, new.** Owner restored it (F0-5) |
| **2** | The cost of `HandedOff` was counted as four compiler sites; it is **eight** — four more in test sources no `compileKotlin` task reaches | P3, Task 4 step 4, and a new Global Constraint |
| **3** | Task 8 was **unexecutable**: four invented helpers, a type whose test double could not be built, three existing tests it would break in silence, and fixtures unreachable through the real one-entry vocabulary | P11 and Task 8, rewritten against the file |
| **4** | Task 9's `AndroidAppBuildInfo` could not compile — `:core:android` has no Hilt, no kapt, no `javax.inject`. The plan named the right precedent (`DeviceProfileProvider`) and then broke it | Global Constraints, Task 9 |
| **5** | `tools/gate.sh` could never print `GATE RED`: `set -e` kills the script on the counter's `sys.exit(1)`, losing the log path and the gradle/tests split | Task 1 step 3 |
| **6** | The plan described `no two step states are indistinguishable` as asserting a (marker, word) pair. It asserts **markers only** — `word()` is `@Composable` and unreachable from a JVM test — so with eight states it would pass unchanged while its own message became false, and **nothing would hold that the words differ** | Task 6 steps 6–7, plus a new resource-level guard |
| **7** | Mutation M5c would have stayed green, and the plan's own proposed replacement would too: `everyStepExecuted()` has exactly one production call site, inside a `@Composable` | Task 6 step 8 — a real Robolectric test on the `Completed` branch |
| **8–16** | Three more invented helpers; two "reuse the construction there" pointers aimed at files without it (one of which would have printed a census of 18 instead of 20); «six strings» for four keys; «fifteen» beside «fourteen»; M4 expected to redden one test when it reddens two; a conditional `+1` counted as certain; a snippet using unimported symbols; fixture names colliding across three files; address drift of 1–2 lines | throughout |

**The rule this review bought, and it is the block's own disease at the level of a document:** *the
plan claimed a test asserted something it does not assert.* That is the same failure as R14-15 /
R14-28 / R14-40 — evidence that does not describe the thing it is believed to describe — committed
in the very paragraph that demanded R14-43 be checked. A plan is not exempt from the rule it carries.

### 0.6 · What the second plan review of 2026-09-25 changed

The first review read a nine-task draft; about a third of this document was written after it. The
second review took **only that delta** (Task 3 whole, Task 8 rewritten, Task 1's two-mode script,
Task 4 steps 4/6, Task 6 steps 6–8, Task 9 step 3, P3/P10/P11, §0.5, the new Global Constraints,
Task 10's arithmetic) and was told to hunt one class: *the plan says a test or file holds a
property it does not hold* — the only class that executing the plan does not catch. It found
fifteen defects. **The first three are in the machinery that produces the evidence**, and none of
them could have been found by reading: each was found by **running** the script's text or the
plan's own commands on a copy of the tree.

| # | Finding | How it was established | Fixed where |
|---|---|---|---|
| **1** | **The scoped counter summed every module's XML.** It cleared the named modules, then counted `**/test-results` repo-wide — so M4a's red XML in `:data:repository` turned M4b's `--scoped :consumer:jvm:test` into `SCOPED RED (gradle=0 counts=1)` whether or not M4b's assert bit (same for M6c → M6d). Its own line «covers the named modules only» was false after the first boundary run | ran the script text on a fake tree and on a copy of the tree | Task 1 script; Global Constraints |
| **2** | **`git checkout` undid the task, not the mutation.** Task 3's note named the wrong casualty (Task 2 is committed by then; the loss is Task 3's own step 5). Unnamed in Tasks 1, 5, 6, 7, 8. Two were false proofs: after M6a's checkout, M6b's `sed` matched nothing and M6b/M6c went red on a **compile error**; `ToolArgumentSorts.kt` is untracked, so checkout failed (exit 1), M8a's deleted row stayed, and M8b/M8c went red because of it | `git checkout` on an untracked file tried in a scratch repo; file contents and step order read | the mutation protocol (Global Constraints) in every mutation block, each with a **predicted `FAILED ::` set** |
| **3** | **The zero-tasks warning could never fire, and `CLAUDE.md` already cited it as a held property.** Gradle 9.5.0 omits zero counts, so `: 0 executed` is never printed; and with `org.gradle.caching=true` a cleared test task is restored `FROM-CACHE` rather than run | `javap` of `TaskExecutionStatisticsReporter`; two consecutive scoped runs on the copy printed `1 executed, 1 from cache, 5 up-to-date` and no warning | scoped runs pass `--no-build-cache --no-watch-fs`; per-named-task check, `SCOPED NOT RUN`; `CLAUDE.md` in the same commit |
| 4 | Task 9's `orElse("unknown")` does not catch a non-zero `git` exit — the build fails outside a checkout, while the KDoc said it "must still assemble"; the happy-path check of step 2 could never see it | probe build on Gradle 9.5.0 outside git: `exit value 128`, build failed; the `runCatching` form printed `unknown`, exit 0 | Task 9 step 1 |
| 5 | Task 8's first new test could not pass on correct code: `appSelector` refuses a bare trigger, so the goal declined in the selector, before the block under test. M8c "proved" nothing through it | on the copy: `appSelector.select("удали приложение") = null`; a bare-trigger selector gives `ToolMatch(args={})` and today's planner then returns `NoPlan` — R14-37 itself | P11; Task 8 step 2 |
| 6 | Task 4 step 4 expected eight errors from one invocation; `:domain` fails first and dependents never compile. Its reading rule would have logged seven false "already non-exhaustive" findings | on the copy: **2 lines, both site 1**. Staged: **1 → 3 → 4** | Global Constraints; Task 4 step 4 |
| 7 | A third test pins D2's lie and P5 said two: `AgentSessionSurfaceProvenanceTest:164-170` (`Paused`, «in progress») | read | P5; Task 6 step 6 |
| 8 | Task 4 step 1 pointed at a **decode-only** test as "the round trip" to copy; both mutations were decode-side, so an encode bug would pass | read `AgentSessionMappersTest:143-156` against `:231-241` | Task 4 step 1 (concrete snippet), new mutation M4c |
| 9 | `JvmAgentSessionStoreTest:99` is named «…every ToolResult»; after Task 4 that name is false and stays green — `:consumer:jvm` has no `kotlin-reflect`, so no `sealedSubclasses` tripwire is possible there | read; build file checked | Task 4 step 8 (rename, and say what holds "every") |
| 10 | Task 3's grep excluded `CommandPlanner` by **file name**, so `RouteCommandUseCase.kt:172` (a `CommandPlanner` call through a field named `planner`) leaked into the list the plan said excluded it | ran the grep | Task 3 step 1 |
| 11 | `ToolMatchPlanner(` is built at nine sites in five files; Task 8 named three tests | grep | P11; Task 8 step 5 |
| 12 | Snippets using API that does not exist or is not imported: Task 3's test (`assertTrue`, `ToolResult`, `ObservedFact`), Task 4 step 8 (`store.load` — the store has `active()`, and it is `suspend`) | read | Task 3 step 2; Task 4 step 8 |
| 13 | Task 6 step 8's R14-43 fallback was aimed at the wrong risk: `onNodeWithText` matches the **whole** text by default, so shared substrings do not blind it; and the neighbour test does not resolve resources, it hardcodes English | `javap` of Compose `ui-test` 1.12.0: `substring` defaults to `false`, compared with `equals` | Task 6 step 8 |
| 14 | "`internal` primary + `@Inject` secondary is `RoomAgentSessionStore`'s pattern" — its primary is **public** (`:36`) | read | Task 8 |
| 15 | P3 named a column `observation_kind`; it is `observation_type` | read | P3 |

**The rule this review bought:** *the machine that produces the evidence is itself a claim, and it
is proved the way a guard is — by making it lie on purpose and watching it refuse.* The first
script was syntax-checked and its argument paths executed, and it still had findings 1 and 3,
because neither shows on a green tree: they need a stale red XML in another module, or a second
identical run. §0.5's rule said a plan is not exempt from the rule it carries; neither is its
tooling.

---

## Task 1: `tools/gate.sh` — the gate as a machine instead of a memory

**Spec:** §4 proposal 4+9 (slot «сейчас, в окно»), §9.2. **Owner fork F0-4.**

**Files:**
- Create: `tools/gate.sh`
- Create: `app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt`

**Interfaces:**
- Produces: `tools/gate.sh [--boundary|--scoped] [<gradle task>…]`, exit 0 only when Gradle exits 0,
  every **counted** JUnit XML reports zero failures and zero errors, and — scoped — every named test
  task actually executed; prints a per-module decomposition, a total, one `FAILED ::` line per
  failing test, and the authored registry census. Every later task's verification step calls it —
  **scoped** for TDD steps and mutations, **boundary** (no arguments) for the run that closes a task.

**What is already verified about the script text below, and what is not** — said so the implementer
knows which half is still theirs. The first version of this script (committed in `0fe16b1`) was
syntax-checked and its argument paths executed, and it still carried two defects that only a run on a
real tree could show (§0.6 findings 1 and 3). So the text below was **run**, on 2026-09-25, on a copy
of the tree at `0fe16b1` in a scratch directory (source only, no `.git`, same JDK 17, same Gradle
9.5.0 wrapper):

| Path | What was run | Observed |
|---|---|---|
| arguments | `--help`; `--scoped testDebugUnitTest`; `--scoped`; `--scoped :nosuch:mod:test` | exit 0; FATAL + exit 2; FATAL + exit 2; FATAL + exit 2 |
| RED, named | a `fail("planted")` in one `:consumer:jvm` test, planted and restored by the `cp` protocol | `:consumer:jvm:test: executed, FAILED`; `FAILED :: consumer/jvm :: …JvmAgentSessionStoreTest > an empty store has no active session`; `SCOPED RED (gradle=1 counts=1)`; exit 1 |
| **isolation** | the planted file restored but `:consumer:jvm` **not** re-run, so its red XML stays on disk; then `--scoped :domain:jvmTest` | the table lists `domain tests=440` **only**; `SCOPED GREEN`; exit 0 — the version in `0fe16b1` went red here |
| **no cache serving** | `--scoped :consumer:jvm:test` twice in a row, nothing changed | both runs: `:consumer:jvm:test: executed` |
| **NOT RUN** | `--scoped :feature:suggestions:testDebugUnitTest` (a module with no tests) | `NO-SOURCE`; `SCOPED NOT RUN …`; exit 1 |
| flags | `--console=plain --no-build-cache --no-watch-fs` on Gradle 9.5.0 | accepted; `> Task :x:y` lines as the named-task check expects |

**Not verified, and therefore step 4's and step 5's job:** a **boundary** run over the whole tree
(the copy was never given one), the census line — whether `println` reaches `<system-out>` in
`:app` (it does in `:data:repository`, seen in XML) — and anything about the real repository's
own build directories.

- [ ] **Step 1: write the registry census measurement**

It is a **measurement, not a guard** — the `SelectionDeclineMeasurement` precedent (`:data:repository`,
added at gate 1440). It carries one real assertion, and that assertion is the fix for the failure mode
a census invites: forgetting a source and printing a sixth diverging number. `ToolPermissionCatalog`
holds exactly 20 rows over exactly the authored ids (P10), so the two independent production
statements can check each other.

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **A measurement, not a guard** (the `SelectionDeclineMeasurement` precedent). It prints the
 * authored registry census in one machine-readable line so `tools/gate.sh` can quote a number that
 * came from the federation rather than from prose — §9.2, which exists because five diverging
 * registry counts are alive in the documents today.
 *
 * **It counts AUTHORED tools only, and says so in its own output.** The dynamic tier is
 * `ShortcutToolSource`, whose catalog is empty without the `android.app.role.HOME` role and which
 * cannot be enumerated off a device at all — measured 2026-09-21: the role was held by
 * `com.sec.android.app.launcher`, so that phone's registry was 20 authored tools and not 243. A
 * census that silently mixed the two tiers would be the sixth diverging number rather than the one
 * that replaces five.
 *
 * **Its one assertion is what keeps it honest about itself.** A census assembled from a source list
 * this file maintains by hand is as green when it forgets an adapter as when it does not — the exact
 * hand-written-column weakness `ToolPermissionCatalog`'s own KDoc names. So the id set is compared
 * against that catalog, which is a **separate** production statement over the same authored tools
 * (20 rows, P10): drop `SystemIntentToolSource` here and the sets differ by `launch_app` and
 * `play_store_search`, and this goes red instead of printing 18.
 */
class RegistryCensusMeasurement {

    @Test
    fun `print the authored registry census`() {
        val authored: List<ToolDescriptor> = productionAuthoredDescriptors()
        val byLevel = authored.groupingBy { it.level.value }.eachCount().toSortedMap()

        println(
            "REGISTRY :: authored=${authored.size} · " +
                byLevel.entries.joinToString(" ") { "${it.key}=${it.value}" } +
                " · dynamic=not-countable-off-device",
        )

        assertEquals(
            "the census and ToolPermissionCatalog are two production statements about the same " +
                "authored set; a difference means this file forgot an adapter (or the catalog did)",
            ToolPermissionCatalog().rows().keys,
            authored.map { it.id }.toSet(),
        )
    }
}
```

`productionAuthoredDescriptors()` is a private helper in this file, returning
`Tier0IntentToolSource(...).all() + SystemIntentToolSource(...).all() + MemoryToolSource(...).all()`.
**Read `DoctrineGuardTest.kt` for the exact constructor arguments each source needs and copy them** —
it builds the same three sources, but its own helper is `private` and it lives in the neighbouring
package `com.sidr.launcher.doctrine`, so it cannot be called from here. The assertion above is what
makes that copy safe: a copy that drifts goes red.

- [ ] **Step 2: run it and read the number it prints**

```bash
cd /home/Suleiman/Sidr-launcher
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*RegistryCensusMeasurement*' --rerun-tasks
grep -rho "REGISTRY :: .*" app/build/test-results/testDebugUnitTest/
```

Expected: one `REGISTRY :: authored=<N> · …` line. **Record `<N>` in the task log.** If nothing is
found, `println` is not reaching `<system-out>` in this module's `Test` configuration — check
`app/build.gradle.kts`'s `testOptions` before assuming the test did not run, and if it is not
captured, say so in the log and have `gate.sh` print `REGISTRY :: not captured` rather than nothing.

- [ ] **Step 3: write `tools/gate.sh`**

```bash
#!/usr/bin/env bash
# The gate, as a machine instead of a memory (spec §9.2; §4 proposal 4+9).
#
# It replaces three of the seven rules of §9.1 — each paid for by a concrete false green — with
# something that cannot forget them:
#   * `build/test-results` is cleared before the run            (R14-44: stale XML lies both ways)
#   * `--rerun-tasks` is passed on a boundary run, `--rerun` never (R9: a false green inside A1′)
#   * counts come from the JUnit XML, never from the console     (2026-07-13: `tail` hid a red gate)
#
# It does NOT replace the other four: predicting the decomposition before the run, proving a new
# guard by mutation, fixture substrings, and re-running from a cleared tree after a mutation are
# judgements, and a script that pretended to make them would be one more instance of "evidence that
# does not describe the thing it is believed to describe".
#
# TWO MODES, AND THEIR LABELS DIFFER ON PURPOSE.
#   boundary (default) — the run that closes a task. Clears every module's results, passes
#                        `--rerun-tasks`, compares the total against the 1440 baseline, and says
#                        `GATE GREEN` / `GATE RED`.
#   --scoped           — a run inside a task: a TDD step or a mutation. Clears, COUNTS and reports
#                        the named modules only, passes `--no-build-cache --no-watch-fs` instead of
#                        `--rerun-tasks`, prints NO baseline comparison, and says
#                        `SCOPED GREEN … — NOT a boundary gate`.
# The phase runs a dozen mutations; as one mode they were a dozen full `--rerun-tasks` sweeps. But
# the saving is not why the labels differ: a cheap run that printed `GATE GREEN` would be a
# false-green generator of exactly the kind this file exists to remove, and a scoped table compared
# against 1440 would be a number describing five missing modules.
#
# THREE THINGS A SCOPED RUN MUST NOT DO, each found by the second plan review (§0.6) and each
# demonstrated by running the first version of this file rather than by reading it:
#   * count another module's leftover XML. The first version cleared the named modules and then
#     summed `**/test-results` across the whole tree — so a red XML left by the previous mutation in
#     module A turned a scoped run of module B red with `gradle=0`, and B's mutation "proved" itself.
#   * let a named test task be served instead of run. `org.gradle.caching=true` is on in this repo
#     and the daemon keeps a watched file-system state between builds; together they can restore a
#     test task's pre-mutation green XML FROM-CACHE. The first version tried to catch that with
#     `grep ': 0 executed'`, a line Gradle 9.5.0 never prints: its statistics reporter omits every
#     zero count (`TaskExecutionStatisticsReporter.formatDetail`, read with `javap`).
#   * leave "RED on <named test>" unverifiable. A mutation is proved by WHICH test went red, so every
#     failing testcase is printed as a `FAILED ::` line.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
[ -d "$JDK17" ] || { echo "FATAL: JDK 17 not found at $JDK17 (set SIDR_JDK17)" >&2; exit 2; }

MODE=boundary
case "${1:-}" in
  --boundary) MODE=boundary; shift ;;
  --scoped)   MODE=scoped;   shift ;;
  -h|--help)  echo "usage: tools/gate.sh [--boundary|--scoped] [gradle tasks…]"; exit 0 ;;
esac

DEFAULT_TASKS=(:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test)
TASKS=("$@")
[ "${#TASKS[@]}" -eq 0 ] && TASKS=("${DEFAULT_TASKS[@]}")

# A scoped run must name modules, because "which results may I clear", "which results may I count"
# and "what may I leave up-to-date" are all answered from the task path. An unqualified task
# (`testDebugUnitTest`) runs in every module, which IS a boundary run — so it is refused here rather
# than silently mislabelled.
if [ "$MODE" = scoped ]; then
  [ "$#" -gt 0 ] || { echo "FATAL: --scoped needs at least one :module:task" >&2; exit 2; }
  for t in "${TASKS[@]}"; do
    case "$t" in
      :*:*) ;;
      *) echo "FATAL: --scoped takes only fully-qualified :module:task (got '$t'). An unqualified task runs every module — that is a boundary run." >&2; exit 2 ;;
    esac
  done
fi

# :data:repository:testDebugUnitTest -> data/repository ; :consumer:jvm:test -> consumer/jvm
module_of() { local m="${1#:}"; m="${m%:*}"; printf '%s' "${m//://}"; }

MODULES=()
if [ "$MODE" = boundary ]; then
  echo "== boundary: clearing build/test-results EVERYWHERE (R14-44) =="
  find . -path ./build -prune -o -type d -name test-results -print0 2>/dev/null \
    | xargs -0 -r rm -rf
else
  echo "== scoped: clearing build/test-results for the named modules only =="
  for t in "${TASKS[@]}"; do
    m="$(module_of "$t")"
    [ -d "$m" ] || { echo "FATAL: no module directory '$m' for task '$t'" >&2; exit 2; }
    rm -rf "$m/build/test-results"
    MODULES+=("$m")
  done
fi

# The daemon is ON in both modes (owner decision, 2026-09-25: the former --no-daemon rule is no
# longer current). Gradle uses it by default and `gradle.properties` sets no `org.gradle.daemon`
# line, so nothing is passed and the first invocation's JVM startup is amortised over the rest.
#
# Why that is safe in each mode, said rather than assumed. A boundary run passes `--rerun-tasks`, so
# every task executes and nothing is taken from a remembered up-to-date verdict or from the cache. A
# scoped run keeps up-to-date checks for compilation (that is its whole saving) but passes
# `--no-watch-fs`, so no file-system state survives from the previous build and every input is
# re-read, and `--no-build-cache`, so a test task whose results were just cleared has to EXECUTE —
# it cannot be restored from the cache. The named-task check below then says so from Gradle's own
# log, per task, and a named test task that did not execute fails the run as `SCOPED NOT RUN`.
GRADLE_ARGS=("-Porg.gradle.java.installations.paths=$JDK17" --console=plain)
if [ "$MODE" = boundary ]; then
  GRADLE_ARGS+=(--rerun-tasks)
else
  GRADLE_ARGS+=(--no-build-cache --no-watch-fs)
fi

LOG="$(mktemp -t sidr-gate-XXXXXX.log)"
echo "== log: $LOG =="
echo "== $MODE: ${TASKS[*]} ${GRADLE_ARGS[*]} =="
# `set +e` around BOTH long-running steps. Without it `set -e` aborts the script the moment the
# counter exits non-zero — which is exactly the run this script exists to report — and the RED
# summary, the census line and the log path are never printed.
set +e
./gradlew "${GRADLE_ARGS[@]}" "${TASKS[@]}" > "$LOG" 2>&1
GRADLE_EXIT=$?
set -e
# Never piped through `tail`: the whole log is kept and the exit code is the exit code.
ACTIONABLE="$(grep -E "actionable tasks?:" "$LOG" || true)"
echo "${ACTIONABLE:-WARNING: no 'actionable tasks' line — was anything executed?}"

NOT_RUN=0
if [ "$MODE" = scoped ]; then
  echo "== named tasks, as Gradle's plain console reported them =="
  for t in "${TASKS[@]}"; do
    line="$(grep -E "^> Task ${t}( |$)" "$LOG" | tail -n 1 || true)"
    case "$line" in
      "> Task $t")        status="executed" ;;
      "> Task $t FAILED") status="executed, FAILED" ;;
      "")                 status="ABSENT from the log" ;;
      *)                  status="${line#"> Task $t "}" ;;
    esac
    echo "  $t: $status"
    # A test task in a scoped run must EXECUTE: its results were cleared and the cache is off, so
    # UP-TO-DATE, FROM-CACHE, NO-SOURCE or absence means this run proved nothing about it.
    case "$t" in
      *[Tt]est) case "$status" in executed*) ;; *) NOT_RUN=1 ;; esac ;;
    esac
  done
fi

echo "== counts, from the JUnit XML =="
set +e
python3 - "$REPO_ROOT" "$MODE" "${MODULES[@]}" <<'PY'
import sys, glob, os
from xml.etree import ElementTree as ET

root, mode = sys.argv[1], sys.argv[2]
named = list(dict.fromkeys(sys.argv[3:]))
per_module, failed = {}, []
for path in glob.glob(os.path.join(root, "**", "build", "test-results", "**", "*.xml"),
                      recursive=True):
    rel = os.path.relpath(path, root)
    module = rel.split(os.sep + "build" + os.sep)[0]
    if mode == "scoped" and module not in named:
        continue  # another module's leftover XML is not this run's evidence
    try:
        suite = ET.parse(path).getroot()
    except ET.ParseError:
        print(f"UNPARSEABLE :: {rel}")
        continue
    if suite.tag != "testsuite":
        continue
    acc = per_module.setdefault(module, [0, 0, 0, 0])
    acc[0] += int(suite.get("tests", 0))
    acc[1] += int(suite.get("failures", 0))
    acc[2] += int(suite.get("errors", 0))
    acc[3] += int(suite.get("skipped", 0))
    for case in suite.iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            failed.append(f"{module} :: {case.get('classname')} > {case.get('name')}")

total = [0, 0, 0, 0]
for module in sorted(per_module):
    t, f, e, s = per_module[module]
    total = [a + b for a, b in zip(total, [t, f, e, s])]
    print(f"{module:<28} tests={t:<5} failures={f:<3} errors={e:<3} skipped={s}")
for module in named:
    if module not in per_module:
        print(f"{module:<28} NO RESULTS — this named module wrote no XML in this run")
print("-" * 64)
print(f"{'TOTAL':<28} tests={total[0]:<5} failures={total[1]:<3} errors={total[2]:<3} "
      f"skipped={total[3]}")
for line in failed:
    print(f"FAILED :: {line}")
# The baseline belongs to a boundary run ONLY. Printing it under a scoped run would invite comparing
# one module's count with a whole-tree number — a table describing five absent modules.
if mode == "boundary":
    print("BASELINE at 2026-09-22 (074e4ce): tests=1440 failures=0 errors=0")
else:
    print("SCOPED: this table counts the named modules only — NOT comparable with 1440")
sys.exit(1 if (total[1] or total[2]) else 0)
PY
COUNT_EXIT=$?
set -e

echo "== registry census, from the federation =="
CENSUS_IN_RUN=no
if [ "$MODE" = boundary ]; then
  CENSUS_IN_RUN=yes
else
  for t in "${TASKS[@]}"; do case "$t" in :app:*) CENSUS_IN_RUN=yes ;; esac; done
fi
if [ "$CENSUS_IN_RUN" = yes ]; then
  CENSUS="$(grep -rhoE --include='*.xml' 'REGISTRY :: .*' app/build/test-results/ 2>/dev/null || true)"
  if [ -n "$CENSUS" ]; then
    printf '%s\n' "${CENSUS%%$'\n'*}"
  else
    echo "REGISTRY :: not captured — :app ran but printed no census (Task 1 step 2)"
  fi
else
  echo "REGISTRY :: not in this run (no :app task named)"
fi

echo "== log kept at: $LOG =="
if [ "$GRADLE_EXIT" -ne 0 ] || [ "$COUNT_EXIT" -ne 0 ]; then
  if [ "$MODE" = boundary ]; then
    echo "GATE RED (gradle=$GRADLE_EXIT counts=$COUNT_EXIT)"
  else
    echo "SCOPED RED (${TASKS[*]}) (gradle=$GRADLE_EXIT counts=$COUNT_EXIT)"
  fi
  exit 1
fi
if [ "$NOT_RUN" -ne 0 ]; then
  echo "SCOPED NOT RUN (${TASKS[*]}) — a named test task did not execute; this run proved nothing about it"
  exit 1
fi
if [ "$MODE" = boundary ]; then
  echo "GATE GREEN"
else
  echo "SCOPED GREEN (${TASKS[*]}) — NOT a boundary gate"
fi
```

- [ ] **Step 4: make it executable and run the full gate through it**

```bash
chmod +x tools/gate.sh
tools/gate.sh            # no arguments = --boundary, the full task list
tools/gate.sh --help     # the two modes, for the next reader
```

Expected: `GATE GREEN`, `TOTAL tests=1441` (1440 + the census measurement), `failures=0 errors=0`,
**no** `FAILED ::` line, an `N actionable tasks: N executed` line, the `BASELINE at 2026-09-22 …
tests=1440` line, and a `REGISTRY :: authored=…` line.

**Predicted decomposition before this run** (compare it, do not skip it): `:domain` 440 ·
`:consumer:jvm` 56 · `:data:repository` 376 · `:feature:launcher` 194 · **`:app` 60** (59 + 1) ·
the other eight modules 315. **Total 1441.**

If the script reports a different module breakdown from those six lines, the script is wrong before
the tree is: check its module derivation (`rel.split("/build/")`) against the real paths — `:domain`
is KMP and writes under `domain/build/test-results/jvmTest/` — before believing any number it prints.

- [ ] **Step 5: prove the script goes red on a NAMED test, ignores other modules' leftovers, and refuses a test task it did not see run**

Everything here is scoped and cheap. The boundary label adds only a literal string to the same
counter and the same `set +e` wrapper; what must be checked on the real tree is what §0.6 found the
first version getting wrong — that a scoped run **counts the named modules only** and **cannot be
served from the cache** — plus the refusals. This step also runs the mutation protocol once (Global
Constraints), on a file this task created: `git checkout` could not have undone it at all.

```bash
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
F=app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt
cp "$F" "$MUT/t1"
sed -i 's/    fun `print the authored registry census`() {/    fun `print the authored registry census`() {\n        org.junit.Assert.fail("planted")/' "$F"
tools/gate.sh --scoped :app:testDebugUnitTest; echo "exit=$?"
```
Expected, **all five**: `:app:testDebugUnitTest: executed, FAILED`;
`FAILED :: app :: com.sidr.launcher.agent.RegistryCensusMeasurement > print the authored registry census`;
`SCOPED RED (:app:testDebugUnitTest) (gradle=1 counts=1)`; the `== log kept at: …` line; `exit=1`. A
run that exits 1 while printing no RED line at all is the `set -e` defect (§0.5 finding 5).

**Isolation — the case the first version of this script got wrong** (§0.6 finding 1):

```bash
cp "$MUT/t1" "$F"                                          # restore — and do NOT re-run :app yet
tools/gate.sh --scoped :consumer:jvm:test; echo "exit=$?"  # :app's red XML is still on disk
```
Expected: the table lists `consumer/jvm` **and nothing else**, no `FAILED ::` line,
`SCOPED GREEN (:consumer:jvm:test) — NOT a boundary gate`, `exit=0`. Red here means the counter is
reading `:app`'s leftover, and every mutation proof in this plan is suspect until it is fixed.

**No cache serving — the check that replaced the dead zero-tasks warning** (§0.6 finding 3):

```bash
tools/gate.sh --scoped :app:testDebugUnitTest; echo "exit=$?"   # twice in a row, nothing changed
tools/gate.sh --scoped :app:testDebugUnitTest; echo "exit=$?"
```
Expected, **both** runs: `:app:testDebugUnitTest: executed`, `SCOPED GREEN (:app:testDebugUnitTest)
— NOT a boundary gate`, the line `SCOPED: this table counts the named modules only — NOT comparable
with 1440`, **no** `BASELINE` line, `exit=0`. `FROM-CACHE` or `UP-TO-DATE` on the second run would
mean `--no-build-cache` did not reach Gradle.

Then the refusals — two need no build, the third builds one small module:

```bash
tools/gate.sh --scoped testDebugUnitTest; echo "exit=$?"                     # FATAL + exit=2: unqualified task
tools/gate.sh --scoped; echo "exit=$?"                                       # FATAL + exit=2: no task named
tools/gate.sh --scoped :feature:suggestions:testDebugUnitTest; echo "exit=$?" # NO-SOURCE -> SCOPED NOT RUN, exit=1
```
`:feature:suggestions` has no tests, so its test task is `NO-SOURCE`: a named test task that ran
nothing must not read as green.

**Record in the task log:** the exact `FAILED ::` and RED lines, the isolation run's one-module
table, both `executed` lines of the repeat, the absence of `BASELINE` under scoped, the two
`exit=2` refusals and the `SCOPED NOT RUN` line.

- [ ] **Step 6: commit**

```bash
git add tools/gate.sh app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt
git commit -m "$(cat <<'EOF'
build(agentic-6/A4' ф0): tools/gate.sh — гейт машиной, а не памятью, в двух режимах

Спека §9.2, предложение 4+9 §4, форк владельца F0-4.
Скрипт заменяет три правила §9.1, каждое оплаченное конкретным ложным
зелёным: чистит build/test-results (R14-44), на граничном прогоне всегда
--rerun-tasks и никогда --rerun (R9), числа из JUnit XML и никогда через
tail. Остальные четыре правила — суждения, и скрипт их не изображает.

ДВА РЕЖИМА, И МЕТКИ У НИХ РАЗНЫЕ НАМЕРЕННО. boundary (по умолчанию) —
прогон, закрывающий задачу: чистит результаты везде, --rerun-tasks,
сверка с 1440, GATE GREEN/RED. --scoped :module:task — прогон ВНУТРИ
задачи (шаг TDD или мутация): чистит, СЧИТАЕТ и показывает только
названные модули, без --rerun-tasks, БЕЗ сверки с базой, и печатает
«SCOPED GREEN … — NOT a boundary gate». Оба режима печатают строку
FAILED :: на каждый упавший тест: мутация доказывается тем, КАКОЙ тест
покраснел, а не меткой RED.

В фазе четырнадцать мутаций, и одним режимом это были бы четырнадцать
полных развёрток --rerun-tasks. Но метки разошлись не ради экономии:
дешёвый прогон, печатающий GATE GREEN, был бы генератором ложного
зелёного ровно того рода, ради устранения которого этот файл написан, а
scoped-таблица, сверенная с 1440, — числом, описывающим пять
отсутствующих модулей. Неполноквалифицированную задачу scoped ОТКАЗЫВАЕТ
(exit 2): она идёт по всем модулям, то есть это граничный прогон.

Демон ВКЛЮЧЁН в обоих режимах (решение владельца 2026-09-25: прежнее
ограничение --no-daemon перестало быть актуальным; gradle.properties не
задаёт org.gradle.daemon, значит демон — умолчание Gradle, и флаг не
передаётся). Почему это безопасно в каждом режиме, сказано, а не
подразумевается: граничный несёт --rerun-tasks и не может унаследовать
устаревший вердикт; scoped несёт --no-build-cache --no-watch-fs, поэтому
тестовая задача, чьи результаты только что стёрты, обязана ИСПОЛНИТЬСЯ —
восстановить её из кеша нельзя (org.gradle.caching=true в этом репо), а
состояние файловой системы из прошлой сборки не переживает. Скрипт
читает исход КАЖДОЙ названной задачи из лога Gradle, и названная
тестовая задача, которая не исполнилась, валит прогон как SCOPED NOT RUN.

Первая версия (0fe16b1) вместо этого грепала «: 0 executed» — строку,
которую Gradle 9.5.0 не печатает никогда (нулевые счётчики опускаются,
javap TaskExecutionStatisticsReporter), — и считала XML всех модулей
разом, так что красный XML прошлой мутации в модуле A красил scoped-
прогон модуля B при gradle=0. Оба дефекта найдены только ПРОГОНОМ
текста на копии дерева, не чтением (§0.6 плана, находки 1 и 3).
CLAUDE.md описывает именно эту версию с момента правки плана.

Обе долгие команды обёрнуты в set +e: без этого set -e убивал бы скрипт
ровно на том прогоне, ради которого он написан, и сводка RED, перепись
и путь к логу не печатались бы никогда.

RegistryCensusMeasurement — замер, не гвард (прецедент
SelectionDeclineMeasurement): печатает перепись АВТОРСКИХ инструментов
одной машинной строкой, чтобы убрать из прозы пять расходящихся чисел.
Динамический ярус вне устройства не считается, и строка это говорит о
себе сама. Единственный ассерт сверяет набор id с ToolPermissionCatalog
(20 рядов над тем же авторским набором) — перепись, собранная руками,
зелена и когда забыла адаптер; сверка с независимым production-
утверждением краснеет вместо того, чтобы напечатать 18.

Пройдено на настоящем дереве: посаженный fail -> FAILED :: с именем
теста + SCOPED RED + exit=1; откат без перезапуска :app, затем scoped
:consumer:jvm:test -> GREEN с одним модулем в таблице (изоляция); два
scoped-прогона подряд -> оба executed (кеш не подаёт); модуль без тестов
-> SCOPED NOT RUN.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: `PlanningResult` becomes exhaustive at both consumers

**Spec:** §3 0.0. This is `R14-39` with a **third cause already built and waiting**: A4′ is the first
block that will add a third variant, and on that day a clarifying question would ride to the cloud as
raw text under a fully green run.

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt:17-23`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/StartAgentSessionUseCase.kt:32-34`
- Modify (KDoc only): `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt:69-72`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: no new symbol. The deliverable is a **compile-time barrier**: a third `PlanningResult`
  variant fails to compile at two named addresses instead of becoming `NoPlan`.

**Why there is no new `@Test` here, said rather than discovered in review:** a sealed interface's
subclasses must live in the same compilation unit, and `domain/src/jvmTest` is a different one from
`commonMain` — so **no test can add a third variant**, and therefore no test can observe the
property. The proof is a compiler error, taken deliberately in step 4 and recorded. This is the
`GoalShape` precedent stated plainly in `CoreVocabularyFreezeGuardTest`'s own KDoc: *"Enforcement is
the compiler's."*

- [ ] **Step 1: make `CompositePlanner` exhaustive**

```kotlin
    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        planners.forEach { planner ->
            // Exhaustive with no `else`, and that is this function's whole safety property (spec
            // §3 0.0). The previous shape — `if (result is Planned) return result` — did not
            // *ignore* a third variant, it **converted** it: anything that was not `Planned` fell
            // out of the loop and left as `NoPlan`. `NoPlan` at routing step 2b falls through to
            // the cloud model, so the first `Clarify`-shaped variant A4' adds would have sent a
            // clarifying question off-device as raw command text, on a goal a registered tool had
            // already matched — R14-39 with a third cause, and with the whole suite green. A
            // third variant must now be a compile error HERE, at the address where the decision
            // belongs.
            when (val result = planner.plan(goal, registry)) {
                is PlanningResult.Planned -> return result
                PlanningResult.NoPlan -> Unit // ask the next planner
            }
        }
        return PlanningResult.NoPlan
    }
```

- [ ] **Step 2: make `StartAgentSessionUseCase` exhaustive**

```kotlin
    suspend fun start(goal: AgentGoal): OperationResult<AgentSessionId?> {
        // Exhaustive with no `else` (spec §3 0.0). `!is Planned -> Success(null)` answered for a
        // variant that did not exist yet, and answered it with silence. Whoever adds the third
        // variant must decide here what a session-less answer means for it, and the compiler is
        // what makes them.
        val plan = when (val planned = planner.plan(goal, registry)) {
            is PlanningResult.Planned -> planned.plan
            PlanningResult.NoPlan -> return OperationResult.Success(null)
        }
        if (!isRunnable(plan)) return OperationResult.Success(null)

        val id = ids.newId()
        val session = AgentSession(
            id = id,
            goal = goal,
            plan = plan,
            cursor = 0,
            state = ExecutionState.Running,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(listOf(TraceEvent.PlanCreated(plan.steps.size))),
        )

        return when (val saved = store.save(session)) {
            is OperationResult.Failure -> saved
            is OperationResult.Success -> OperationResult.Success(id)
        }
    }
```

- [ ] **Step 3: write the address list onto the type itself**

Replace the bare declaration in `ExecutionPlan.kt`:

```kotlin
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
```

- [ ] **Step 4: take the compile error deliberately — this is the task's proof**

```bash
cd /home/Suleiman/Sidr-launcher
sed -i 's/^    data object NoPlan : PlanningResult$/    data object NoPlan : PlanningResult\n    data object Clarify : PlanningResult/' \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:compileKotlinJvm 2>&1 | grep -E "e: .*(CompositePlanner|StartAgentSessionUseCase)"
```

Expected: **two** error lines, one naming `CompositePlanner.kt` and one naming
`StartAgentSessionUseCase.kt`, both `'when' expression must be exhaustive`. **Paste both verbatim
into the task log.** One error line is a failure of this task: it means only one consumer was
converted. Zero is a failure of the sed.

```bash
# Remove the planted line ONLY — a `git checkout` here would also revert step 3's KDoc.
sed -i '/^    data object Clarify : PlanningResult$/d' \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt
git diff --stat   # ExecutionPlan.kt must show only the KDoc addition
```

- [ ] **Step 5: gate**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1441`, decomposition unchanged from Task 1 — `:domain` **440**.
This task adds no `@Test`; if that number moved, something else did too.

- [ ] **Step 6: commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф0): PlanningResult исчерпывающ у обоих потребителей

Спека §3 0.0. Ни один потребитель не был исчерпывающ, и второй
(CompositePlanner) третий вариант не проглатывал, а ПРЕВРАЩАЛ в NoPlan;
NoPlan на шаге 2b проваливается к облачной модели. A4′ — первый блок,
который добавит третий вариант, и в тот день уточняющий вопрос уехал бы
сырым текстом в облако при полностью зелёном прогоне. R14-39 с третьей
причиной, уже построенной и ждущей.

Доказательство — компиляторное, а не зелёный прогон: посаженный
`data object Clarify` даёт ровно ДВЕ ошибки «'when' expression must be
exhaustive», по одной на каждый названный адрес. Тестом это не держится
и держаться не может: наследники sealed-интерфейса живут в своём
compilation unit, а jvmTest — не commonMain.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: `PlanningRequest` — the planner's input gets a shape that can grow

**Spec:** §3 0.4, first half — «**Решение: один `PlanningRequest` с двумя полями сегодня**». §4 uses
it as the worked example of its own distribution rule ("строить вперёд потребителя" — first column:
retrofit is dearer than doing it now). §9.3 lists it among what phase 0 touches in `:domain`. §6.4
opens phase 2 by consuming its second field. **Owner fork F0-5.**

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/ExecutionPlan.kt:74-77`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt:44`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt:17`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/StartAgentSessionUseCase.kt:32`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt:76`
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlanner.kt:37`
- Modify: every test that implements or calls `Planner.plan` — **discover them, do not trust this
  list**: `domain/…/RouteCommandUseCaseTest.kt:159`, `:445`, `CompositePlannerTest.kt:26`,
  `AgentSessionUseCasesTest.kt:355`, plus every direct call site (`TemplatePlannerTest`,
  `ToolMatchPlannerTest`, `FilePlannerTest`, `AgentSessionPresentationTest:58`,
  `AgentSessionSurfaceProvenanceTest`, `LauncherScreenAgentProvenanceTest`, `AgentLoopTest`,
  `AgentActingSeamTest`, `SelectionDeclineMeasurement`, `CompositePlannerTest:41`/`:55`,
  `AgentSessionUseCasesTest:345`, `RoomAgentSessionStoreTest:99`). Counted 2026-09-25 by running
  step 1's grep: four fakes in three files, and direct calls in twelve test files across `:domain`,
  `:data:repository`, `:consumer:jvm` and `:feature:launcher`. **`:app` has none that change** —
  `AgentProvidesModule:255`/`:279` only name the type.
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CompositePlannerTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class PlanningRequest(
      val goal: AgentGoal,
      val priorObservations: Map<Int, ToolResult> = emptyMap(),
  )
  interface Planner {
      suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult
  }
  ```
- Consumed by: every `Planner` implementation. `StartAgentSessionUseCase.start(goal)` keeps its own
  signature and builds `PlanningRequest(goal)` — its caller `RouteCommandUseCase` is untouched.

**This is a wide mechanical sweep with one new property.** Most of it is transcription. The property
worth a test is the one the shape exists for: a composite must hand **the same** request to every
planner, because phase 2 will put re-planning state in it and a composite that rebuilt the request
per planner would quietly drop it.

- [ ] **Step 1: find every site before changing any**

```bash
cd /home/Suleiman/Sidr-launcher
grep -rn "\.plan(\|override suspend fun plan(\|: Planner" --include=*.kt . | grep -v "/build/" \
  | grep -v "CommandPlanner\|LlmCommandPlanner\|FakeCommandPlanner"
```
`CommandPlanner` is a **different port** (`plan(command: String, catalog: ActionCatalog)`, the LLM
action router) and must not be touched. **The grep above does NOT fully exclude it, and why is worth
knowing:** `grep -v` filters the whole output line *including its path*, so `FakeCommandPlanner`
and `LlmCommandPlanner` drop out only because their **file names** contain the word. The one
production call of that port sits in a file whose name does not:
`RouteCommandUseCase.kt:172` — `planner.plan(rawInput.trim(), catalog)`, where `planner` is the
`CommandPlanner` field declared at `:101`. **It is in the list and must be left alone** (an edit
there fails to compile — `PlanningRequest` wants an `AgentGoal`, not a `String` — but it is cheaper
to know than to find out). Two ports in this repo spell a method `plan`, and the filter tells them
apart by accident. **Write the site list into the task log before editing, with that line struck.**

- [ ] **Step 2: write the failing test**

In `CompositePlannerTest`. That file imports only `FakeToolRegistry`, `ToolRegistry`,
`ExperimentalCoroutinesApi`, `runTest`, `assertEquals` and `Test` (`:3-8`), so add:

```kotlin
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolResult
import org.junit.Assert.assertTrue
```

```kotlin
    /**
     * The property the new shape exists for, and the only one a test can hold today: **every planner
     * is asked the same question**. Phase 2 puts re-planning state in `PlanningRequest`
     * (spec §6.4 — "второе поле получает здесь своего потребителя"), so a composite that rebuilt
     * the request per planner, or passed `PlanningRequest(goal)` while holding a richer one, would
     * silently drop that state for every planner after the first. Today that reads as a pedantic
     * assertion; on the day re-planning lands it is the difference between a second attempt that
     * knows what the first observed and one that does not.
     */
    @Test
    fun `every planner is asked with the same request instance`() = runTest {
        val seen = mutableListOf<PlanningRequest>()
        val recording = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
                seen += request
                return PlanningResult.NoPlan
            }
        }
        val request = PlanningRequest(
            goal = AgentGoal("зефир", GoalShape.Free("зефир")),
            priorObservations = mapOf(0 to ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED)),
        )

        CompositePlanner(listOf(recording, recording, recording))
            .plan(request, FakeToolRegistry.withA0Tools())

        assertEquals(3, seen.size)
        assertTrue("a composite must not rebuild the request it was given", seen.all { it === request })
    }
```

Fixture note (R14-43): `"зефир"` appears nowhere else in that file; check before committing to it.

- [ ] **Step 3: run, expect a compile failure**

```bash
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*CompositePlannerTest*' --rerun-tasks 2>&1 | grep -E "^e: " | head
```
Expected: `Unresolved reference 'PlanningRequest'` — the first of several; the recording planner's
`'plan' overrides nothing` and its companions follow from the same missing type. Any **other**
unresolved name means step 2's imports were not added.

- [ ] **Step 4: add the type and change the port**

In `ExecutionPlan.kt`, replacing the `Planner` declaration (and adding
`import com.sidr.launcher.domain.tool.ToolResult`):

```kotlin
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
    suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult
}
```

- [ ] **Step 5: sweep the four implementations and the one consumer**

Each is a one-line signature change plus reading `request.goal` where `goal` was read:
- `TemplatePlanner.plan` — `when (val shape = request.goal.shape)`
- `CompositePlanner.plan` — passes `request` through **unchanged** (this is what step 2 pins)
- `ToolMatchPlanner.plan` — `when (val shape = request.goal.shape)`
- `FilePlanner.plan` — `targetOf(request.goal)`
- `StartAgentSessionUseCase.start` — `planner.plan(PlanningRequest(goal), registry)`, everything else
  identical. Add one line to its KDoc: *"The request carries no prior observations: a first attempt
  has observed nothing, and re-planning (phase 2) does not start here."*

- [ ] **Step 6: sweep the tests from step 1's list**

Mechanical: a fake's `plan(goal, registry)` becomes `plan(request, registry)`; a direct call
`planner.plan(goal, registry)` becomes `planner.plan(PlanningRequest(goal), registry)`. **No test's
assertions change.** If any test's expectations shift, stop — that is a behaviour change this task
must not make, and it is a finding for the log.

- [ ] **Step 7: run the whole gate, expect green**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1442`, `:domain` **441**, every other module unchanged from
Task 1. A module other than `:domain` moving means a test was lost in the sweep, not adapted.

- [ ] **Step 8: mutation**

```bash
# M3 — rebuild the request per planner, which is exactly what phase 2 must not inherit
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
F=domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt
cp "$F" "$MUT/m3"
sed -i 's/when (val result = planner.plan(request, registry)) {/when (val result = planner.plan(PlanningRequest(request.goal), registry)) {/' "$F"
tools/gate.sh --scoped :domain:jvmTest
cp "$MUT/m3" "$F"
```
Predicted `FAILED ::` set — exactly one: `CompositePlannerTest > every planner is asked with the same
request instance`. The other two tests in that file read the order of questions, not the request's
identity, and must stay green.

Restored with `cp`, not `git checkout`: Task 2 is committed by now, so a checkout would have kept
Task 2's `when` and erased **this** task's step 5 — the `plan(request, …)` signature — leaving a file
that no longer implements the port (the first version of this note named Task 2 as the casualty;
§0.6 finding 2). Note that this mutation compiles and preserves every existing behaviour — that is
why it is the right one: it is the mistake a future editor would actually make.

- [ ] **Step 9: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```

```bash
git add domain/src consumer/jvm/src data/repository/src feature/launcher/src
git commit -m "$(cat <<'EOF'
refactor(agentic-6/A4' ф0): PlanningRequest — у входа планировщика появилась форма

Спека §3 0.4, первая половина: «Решение: один PlanningRequest с двумя
полями сегодня». §4 приводит его как рабочий пример собственного правила
распределения (ретрофит дороже, чем сделать сразу), §9.3 перечисляет его
среди того, что фаза 0 трогает в :domain, §6.4 открывает фазу 2 его
вторым полем. Форк владельца F0-5: в фазу 0.

Один параметр сейчас — чтобы следующие были умолчанием, а не правкой
сигнатуры у четырёх production-реализаций в трёх модулях, обоих
потребителей и всех фейков. Пустые типы A2/A3 из
agentic-os-architecture.md:104 НЕ вводятся: это буквально ошибка
:data:ai-local, из которой Master Plan §3.4 сделала правило.

priorObservations не спекулятивно: потребитель назван и датирован —
перепланирование на частичном отказе, фаза 2. Сегодня пусто на всех
площадках, и это честное состояние, а не заглушка: первая попытка не
наблюдала ничего.

Реестр остаётся отдельным параметром: это не контекст попытки, а мир,
против которого пишется план, и его читает ещё и движок.

Мутация M3 (пересобрать запрос для каждого планировщика — она
компилируется и сохраняет всё сегодняшнее поведение, поэтому её и
сделает будущий редактор) -> RED.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: `ToolResult.HandedOff` — the value, and both persistence layers

**Spec:** §3 0.2(1), owner fork **F0-1**. **`ObservedFact` stays frozen** (section «Do not»); the seam
is `ToolResult`, which no freeze guard pins (P2).

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt:52-71`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/InvocationValidator.kt:138-141`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt`
  (constants near `:78-80`, `:162-167`, `:171-175`, `:326-334`)
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt:127-141`
- Modify: `data/repository/src/test/java/…/SystemIntentToolContractTest.kt:113-123` and
  `consumer/jvm/src/test/kotlin/…/SandboxToolContractTest.kt:83-93` — **four more `else`-free `when`s
  that only a test compile reaches** (P3, sites 5–8)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/InvocationValidatorTest.kt`
- Test: `data/repository/src/test/java/…/AgentSessionMappersTest.kt`
- Test: `consumer/jvm/src/test/kotlin/…/store/JvmAgentSessionStoreTest.kt`

**Interfaces:**
- Produces: `ToolResult.HandedOff(val output: ToolOutput = ToolOutput())` — consumed by Task 5
  (`uninstall_app`), Task 6 (the surface) and Task 7 (a step cut by the wall-clock budget). The
  persisted discriminator is **`"HandedOff"`** in both stores.

- [ ] **Step 1: write the failing round-trip test on the Android mapper**

**Copy the right neighbour — there are two, and only one is a round trip** (§0.6 finding 8).
`every ObservedFact round-trips as a precondition and as an observation` (`:143-156`) is
**decode-only**: it writes a row by hand with `stepRow(observationType = "Observed", …)` and reads it
through `read(...)`. A test in that shape would be named "round trip" and never execute
`observationType` / `observationOutputJson` — the two encode sites this task adds arms to — so an
encode arm writing the wrong discriminator would pass it. The real round trip is
`every TraceEvent variant round-trips` (`:231-241`): a domain session through
`toSessionEntity` / `toStepEntities` / `toTraceEntities` and back through `toDomain`. Use that:

```kotlin
    /**
     * A0.5's "a whole class of reality unsayable" coming due (A1″ acceptance finding (a)):
     * `uninstall_app` raises the OS dialog and the caller learns nothing about what the user then
     * did. `Effected` claimed the removal happened; `Failed` claims a technical failure that did
     * not occur; `Observed` needs a frozen `ObservedFact` that has no value for this. The fourth
     * value says exactly what is true — the act left the launcher and the outcome is not ours to
     * see — and it must survive the disk, or the trace goes on lying one save later.
     *
     * **Encode AND decode.** It goes out through the three `to…Entities` mappers and back through
     * `toDomain`, like `every TraceEvent variant round-trips` — not a hand-written row, which would
     * hold the reader only. M4a (decode) and M4c (encode) each redden it on its own.
     */
    @Test
    fun `a HandedOff observation survives a round trip with its output`() {
        val original = sessionWith(emptyList()).copy(
            observations = mapOf(0 to ToolResult.HandedOff(ToolOutput(mapOf("dispatched_to" to "os-uninstaller")))),
        )

        val restored = AgentSessionMappers.toDomain(
            AgentSessionMappers.toSessionEntity(original, now = 1L),
            AgentSessionMappers.toStepEntities(original),
            AgentSessionMappers.toTraceEntities(original, now = 1L),
        )

        val observation = restored.observations[0]
        assertTrue("a HandedOff must not come back as some other kind", observation is ToolResult.HandedOff)
        assertEquals(
            mapOf("dispatched_to" to "os-uninstaller"),
            (observation as ToolResult.HandedOff).output.values,
        )
    }
```
`sessionWith(emptyList())` rather than `sessionWith(oneOfEachEvent)`: that list carries
`TraceEvent.ToolObserved(0, observed)`, and the reader rebuilds a `ToolObserved` event from the
step's own observation (`AgentSessionMappers.kt:366-370`), so a session whose step 0 holds a
`HandedOff` under a trace that says `Observed` would be a fixture contradicting itself.

Fixture note (R14-43): `"os-uninstaller"` shares no substring with any other expected value in that
file; verify before committing to it.

- [ ] **Step 2: run it and watch it fail to COMPILE, not to assert**

```bash
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*AgentSessionMappersTest*' --rerun-tasks 2>&1 \
  | grep -E "^e: " | head
```
Expected: `Unresolved reference 'HandedOff'`. A compile failure is the correct first red — the value
does not exist yet.

- [ ] **Step 3: add the value**

In `ToolInvocation.kt`, after `Observed`:

```kotlin
    /**
     * The tool handed the act to something outside the launcher and **cannot see what happened
     * next**. Not a success, not a failure, not an observation about the world: an outcome that has
     * not arrived.
     *
     * Two producers, and they are the same shape:
     *  - `uninstall_app` — `startActivity(ACTION_DELETE)` returns the moment the OS dialog is
     *    *raised*, identically whether the user then confirms, cancels, or the responder refuses
     *    silently (measured, rows 16/28/32). It is the only tool in the federation whose `Effected`
     *    could be false, and the only irreversible one, so this is where `DOC-ILM-3` (the trace is
     *    1:1 with reality) and `DOC-ILM-4` (a partial result is shown as partial) both bit.
     *  - a step cut by [RuntimeBudget.maxStepWallClockMs] — the call was made, we stopped waiting,
     *    and the side effect may well have happened.
     *
     * **This is not `ObservedFact` growing.** That type stays frozen at two values (A0.5's
     * record-don't-fix decision, held by `CoreVocabularyFreezeGuardTest`): `uninstall_app` has no
     * unsayable *fact*, it has an outcome that has not happened yet, which is a property of
     * execution rather than of the world. The seam is therefore here.
     *
     * [output] is carried for the same reason [Effected] carries one: a tool that declares an
     * `outputSchema` must honour it on **every** result, not on the branch it happened to take.
     */
    data class HandedOff(val output: ToolOutput = ToolOutput()) : ToolResult
```

- [ ] **Step 4: take ALL EIGHT compile errors — in three stages, because a module never compiles while its dependency is red**

The first draft ran only the production compile tasks and expected four errors; four more live in
test sources (P3, sites 5–8). The second draft named all six compile tasks in **one** invocation and
expected eight — and would have got **two**: `:data:repository` and `:consumer:jvm` depend on
`:domain`, site 1 is in `:domain`, so `:domain` fails first and nothing downstream ever compiles
(measured 2026-09-25 on a copy of the tree: 2 lines, both `InvocationValidator.kt:138`, one per KMP
compilation — §0.6 finding 6). The count is taken layer by layer, fixing between layers with the
code from steps 5 and 6. **The three stages below were run on that copy, with exactly these
commands, and gave exactly these counts.**

```bash
G="./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10"

# stage 1 — :domain production. Expect 1: site 1 (InvocationValidator.kt:138).
$G :domain:compileKotlinJvm 2>&1 | grep -E "^e: " | tee /tmp/handedoff-1.txt
# -> apply step 5's InvocationValidator arm

# stage 2 — every production consumer, plus :domain's own tests. Expect 3: sites 2, 3 (AgentSessionMappers.kt:162, :171) and 4 (SessionMapper.kt:127).
$G --continue :domain:compileTestKotlinJvm :data:repository:compileDebugKotlin :consumer:jvm:compileKotlin 2>&1 \
  | grep -E "^e: " | tee /tmp/handedoff-2.txt
# -> apply the rest of step 5

# stage 3 — the test source sets no compileKotlin task reaches. Expect 4: sites 5–8.
$G --continue :domain:compileTestKotlinJvm :data:repository:compileDebugUnitTestKotlin :consumer:jvm:compileTestKotlin 2>&1 \
  | grep -E "^e: " | tee /tmp/handedoff-3.txt
# -> apply step 6
```
`--continue` matters in stages 2 and 3: the modules inside one stage do not depend on each other,
and without it Gradle stops scheduling at the first failure, so one module's errors would hide the
other's. **Record all three files: 1 + 3 + 4 = 8.** A stage that shows fewer than predicted is read
against the **stage**, not against eight — fewer in stage 2 or 3 with a green previous stage means a
site was already non-exhaustive, a finding. More than predicted means P3's census of this type is
incomplete, which is also a finding, and a more interesting one.

- [ ] **Step 5: fix the four production sites the compiler caught**

`InvocationValidator.kt:138-141`:
```kotlin
    private fun ToolResult.output(): ToolOutput? = when (this) {
        is ToolResult.Effected -> output
        is ToolResult.Observed -> output
        // A handed-off step DID produce whatever its tool declared before handing over; what it
        // cannot promise is the outcome. Binding to it is therefore legal and stays governed by the
        // declared `outputSchema`, exactly as for the two above.
        is ToolResult.HandedOff -> output
        is ToolResult.Failed -> null
    }
```

`AgentSessionMappers.kt`: `private const val OBSERVATION_HANDED_OFF = "HandedOff"` beside the other
three; `is ToolResult.HandedOff -> OBSERVATION_HANDED_OFF` in `observationType`;
`is ToolResult.HandedOff -> encodeOutput(observation.output)` in `observationOutputJson`.

`SessionMapper.kt` `resultDto`:
`is ToolResult.HandedOff -> ResultDto("HandedOff", output = result.output.values)`.

- [ ] **Step 6: fix the four TEST sites the compiler caught**

These are test-only diagnostics, and the honest branch names the new value rather than folding it
into a neighbour:
- `SystemIntentToolContractTest.branch()` and `SandboxToolContractTest.branch()` —
  `is ToolResult.HandedOff -> "HandedOff"`
- both `outputKeys()` — `is ToolResult.HandedOff -> output.values.keys`

Note what these two files are: contract tests over a **source's** results. Neither source produces
`HandedOff` today, so these arms are unreached — write them as the honest mapping anyway, not as
`error(...)`, because the next adapter that hands off will reach them.

- [ ] **Step 7: fix the two sites the compiler does NOT catch (P3, rows 9–10)**

This is the step a green suite would let through: a value written and never read back decodes as
corruption.

`AgentSessionMappers.kt` `readObservation`: `OBSERVATION_HANDED_OFF -> ToolResult.HandedOff(readOutput(row))`
`SessionMapper.kt` `result(dto)`: `"HandedOff" -> ToolResult.HandedOff(ToolOutput(dto.output))`

- [ ] **Step 8: add the JVM-side round trip and the validator test**

`JvmAgentSessionStoreTest.kt` — **its `store` is a function `store()` (`:45`), its session builder is
`session(observations = mapOf(0 to …))` (`:49-90`), the store has no `load`: the read is
`active()`, it is `suspend`, and the file unwraps results with its own `value()` (`:92`) inside
`runTest`** — exactly as its round-trip test at `:99-140` does:

```kotlin
    @Test
    fun `a HandedOff observation survives this consumer's disk too`() = runTest {
        val s = store()
        s.save(session(observations = mapOf(0 to ToolResult.HandedOff(ToolOutput(mapOf("ticket" to "sandbox-42"))))))

        val observation = s.active().value()!!.observations[0]
        assertTrue("a HandedOff must not come back as some other kind", observation is ToolResult.HandedOff)
        assertEquals(mapOf("ticket" to "sandbox-42"), (observation as ToolResult.HandedOff).output.values)
    }
```

**And rename the neighbour whose name this task makes false** (§0.6 finding 9). `:99` is
`a saved session round-trips - every TraceEvent variant and every ToolResult`, and its fixture holds
`Effected`, `Observed` and `Failed` — three values. After this task there are four, the test stays
green, and its name becomes a claim nothing holds: `:consumer:jvm` has **no `kotlin-reflect`** on
its test classpath (only `:data:repository` declares it, `build.gradle.kts:77`), so the
`sealedSubclasses` tripwire `AgentSessionMappersTest` uses for `TraceEvent` is not available here,
and its three-step fixture has no fourth slot. Rename it to
`a saved session round-trips - every TraceEvent variant and the Effected, Observed and Failed results`
and add one KDoc line: *"`HandedOff` has its own test below. Nothing here holds 'every': encode is
held by the compiler (`resultDto` is an `else`-free `when`), decode by one test per value."* A rename
changes no count.

`InvocationValidatorTest.kt`:
```kotlin
    @Test
    fun `a later step may bind to a HandedOff step's declared output`() {
        val result = InvocationValidator.resolve(
            ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))),
            observations = mapOf(0 to ToolResult.HandedOff(ToolOutput(mapOf("resolved_query" to "quartz")))),
        )

        assertTrue(result is ResolutionResult.Resolved)
        assertEquals("quartz", (result as ResolutionResult.Resolved).invocation.args["query"])
    }
```
Fixture note: `"quartz"` shares no substring with `"убер"`, `"uber"` or any package name in that
file, and none of this phase's other fixtures (`os-uninstaller`, `sandbox-42`, `com.marlin.notes`)
contains it. Verify before committing to it.

- [ ] **Step 9: run the three tests, expect PASS**

```bash
tools/gate.sh --scoped :domain:jvmTest :data:repository:testDebugUnitTest :consumer:jvm:test
```
Expected: `SCOPED GREEN`, all three named tasks `executed`, no baseline line, no `FAILED ::` line.
`:domain` 442, `:data:repository` 377, `:consumer:jvm` 57 — the three modules named, and nothing
else in the table (the script counts named modules only, §0.6 finding 1).

- [ ] **Step 10: mutation — prove each persistence branch is load-bearing, decode AND encode**

```bash
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
A=data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt
J=consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt

# M4a — Android DECODE returns the wrong kind
cp "$A" "$MUT/m4a"
sed -i 's/OBSERVATION_HANDED_OFF -> ToolResult.HandedOff(readOutput(row))/OBSERVATION_HANDED_OFF -> ToolResult.Effected(readOutput(row))/' "$A"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m4a" "$A"

# M4c — Android ENCODE writes the wrong discriminator (the half a decode-only test could not see)
cp "$A" "$MUT/m4c"
sed -i 's/is ToolResult.HandedOff -> OBSERVATION_HANDED_OFF/is ToolResult.HandedOff -> OBSERVATION_EFFECTED/' "$A"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m4c" "$A"

# M4b — JVM decode drops the output
cp "$J" "$MUT/m4b"
sed -i 's/"HandedOff" -> ToolResult.HandedOff(ToolOutput(dto.output))/"HandedOff" -> ToolResult.HandedOff(ToolOutput(emptyMap()))/' "$J"
tools/gate.sh --scoped :consumer:jvm:test
cp "$MUT/m4b" "$J"
```
Predicted `FAILED ::` sets — **one test each**:
- M4a → `AgentSessionMappersTest > a HandedOff observation survives a round trip with its output`
  (the kind assertion);
- M4c → the **same** test, the same assertion — which is the proof that step 1's test goes through
  the encoder, not only the reader;
- M4b → `JvmAgentSessionStoreTest > a HandedOff observation survives this consumer's disk too` (the
  output `assertEquals`).

Under the first version of this plan M4b would have "passed" regardless: M4a's red XML was still in
`data/repository/build/test-results` when M4b ran, and the old script counted it (§0.6 finding 1).

- [ ] **Step 11: gate from a cleared tree (the last action of a mutation task)**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1445`, `:domain` 442 · `:consumer:jvm` 57 · `:data:repository`
377 · `:feature:launcher` 194 · `:app` 60 · others 315.

- [ ] **Step 12: commit**

```bash
git add domain/src data/repository/src consumer/jvm/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф0): ToolResult.HandedOff — четвёртое значение, оба хранилища

Спека §3 0.2(1), форк владельца F0-1. Находка (a) приёмки 2026-09-20:
uninstall_app возвращает Effected за ОДНО ЛИШЬ поднятие системного
диалога, поэтому отменённое удаление рисуется «выполнено». Единственный
необратимый инструмент — и единственный, у кого Effected может быть
ложью (замерено: 11 навигирующих действительно открывают экран,
set_alarm/set_timer действительно заводят будильник).

ObservedFact остаётся ЗАМОРОЖЕННЫМ (раздел «Do not»): у uninstall_app
нет невыразимого ФАКТА — у него есть исход, который ещё не наступил.
Это свойство исполнения, значит шов — ToolResult, и его не пинит ни один
freeze-гвард (проверено: CoreVocabularyFreezeGuardTest держит ровно
ObservedFact и GoalShape; критерий 10 §10 ToolResult не называет).

Цена измерена и уплачена целиком — ВОСЕМЬ исчерпывающих `when`, а не
четыре: четыре в production (InvocationValidator, AgentSessionMappers ×2,
SessionMapper) и ЧЕТЫРЕ В ТЕСТОВЫХ сорцах (SystemIntentToolContractTest,
SandboxToolContractTest — по паре branch()/outputKeys()), куда ни одна
задача compileKotlin не дотягивается. Плюс ДВЕ ветки декодирования,
которые компилятор НЕ ловит — строковые `when` с else. Именно они и есть
то, что зелёный прогон пропустил бы: значение, записанное и никогда не
прочитанное, декодируется как повреждение.

Цена снята поэтапно, а не одним вызовом: модуль не компилируется, пока
красен модуль, от которого он зависит, поэтому один вызов по шести
задачам показал бы две строки про один сайт :domain. Стадии 1 -> 3 -> 4.

Room-миграции нет: observation_type — строковая колонка, схема 4 цела.

Тест на Android — настоящий round trip (to…Entities -> toDomain), а не
чтение руками написанной строки: иначе кодирование не держал бы никто.
Тест JVM-стора с именем «…every ToolResult» переименован — после этого
коммита значений четыре, а kotlin-reflect в :consumer:jvm нет, так что
«every» там не держит ни один tripwire.

Мутации: M4a (Android-декод отдаёт не тот сорт) -> RED, M4c (Android-
кодирование пишет не тот дискриминатор) -> RED в том же тесте, M4b
(JVM-декод теряет output) -> RED. Каждая — ровно один FAILED ::.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: `uninstall_app` stops claiming an outcome it cannot see

**Spec:** §3 0.2(1). Depends on Task 4.

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt`
  (`uninstallApp`, `:263-267`; `launch` at `:327-334` is **not** touched)
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorkerTest.kt`
- Read first, and modify only if it asserts `Effected` for `uninstall_app`:
  `data/repository/src/test/java/…/Tier0ToolExecutionEndToEndTest.kt`

**Interfaces:**
- Consumes: `ToolResult.HandedOff` (Task 4). Produces nothing new.

- [ ] **Step 1: write the three failing tests**

The file's real helpers are `workerWith(launcher, presence)` (`:62`) and the class
`ThrowingIntentLauncher` (`:468`). There is no `throwingWorker` — use `workerWith` with a throwing
launcher.

```kotlin
    /**
     * A1″ acceptance finding (a). `startActivity(ACTION_DELETE)` returns the instant the OS dialog
     * is raised and returns identically whether the user confirms, cancels, or the responder
     * refuses silently (rows 16/28/32) — so `Effected` here was a claim the worker had no way to
     * make. `HandedOff` is the claim it can make.
     */
    @Test
    fun `uninstall_app hands off — it does not claim the app was removed`() = runTest {
        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "com.marlin.notes")))

        assertTrue("a raised dialog is not a removal", result is ToolResult.HandedOff)
    }

    /**
     * The non-vacuity half, and it is the half that matters: `Effected` is a lie for exactly ONE
     * tool in this worker. The eleven navigating tools really do open their screen — `startActivity`
     * returning IS the effect — and `set_alarm`/`set_timer` really create the alarm (rows 13/29). A
     * fix that downgraded them would trade one wrong word for fourteen.
     */
    @Test
    fun `every other tool in this worker still reports Effected`() = runTest {
        val stillEffecting = listOf(
            Tier0ToolIds.SET_TIMER to mapOf("duration" to "5 minutes"),
            Tier0ToolIds.SET_ALARM to mapOf("time" to "7:30"),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyMap(),
            Tier0ToolIds.SHOW_ALARMS to emptyMap(),
            Tier0ToolIds.OPEN_CAMERA to emptyMap(),
            Tier0ToolIds.OPEN_WIFI_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_SOUND_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_APP_INFO to mapOf("app" to "com.marlin.notes"),
        )

        stillEffecting.forEach { (id, args) ->
            assertTrue(
                "$id performs its act — downgrading it would trade one wrong word for fourteen",
                worker.invoke(ResolvedInvocation(id, args)) is ToolResult.Effected,
            )
        }
    }

    /** `HandedOff` must not have eaten `Failed`: a dialog that never appeared is a failure we CAN see. */
    @Test
    fun `an uninstall that could not be dispatched is Failed, not handed off`() = runTest {
        val worker = workerWith(launcher = ThrowingIntentLauncher(ActivityNotFoundException()))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "com.marlin.notes")))

        assertTrue(result is ToolResult.Failed)
    }
```

Adapt `worker`, `workerWith` and `ThrowingIntentLauncher`'s constructor to what the file actually
has — read `:62` and `:468` first. Fixture note (R14-43): `"com.marlin.notes"` is not a real package
and shares no substring with any label in that file, nor with this phase's other fixtures
(`quartz`, `os-uninstaller`, `sandbox-42`); verify.

- [ ] **Step 2: run, expect the first to FAIL and the others to PASS**

```bash
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*Tier0IntentToolWorkerTest*' --rerun-tasks
```
Expected: `uninstall_app hands off` fails with `Effected` ≠ `HandedOff`; the other two pass already.
**All three outcomes are required** — a second or third test failing too would mean the fixture is
wrong, not the product.

- [ ] **Step 3: convert the one arm**

```kotlin
    private fun uninstallApp(target: String): ToolResult {
        if (target.isBlank()) return ToolResult.Failed(CommandFailure.Generic)
        if (target == ownPackageName) return ToolResult.Failed(CommandFailure.Generic)

        // The ONE arm of this worker where `Effected` was a lie (A1″ acceptance finding (a)).
        // `launch` is not changed and must not be: the eleven navigating tools really do open their
        // screen, and `set_alarm`/`set_timer` really do create the alarm (rows 13/29) — for them
        // `startActivity` returning IS the effect. Here it is only the dialog being raised, and the
        // caller learns nothing about what the user then did (rows 16/28/32: the return is
        // byte-identical for confirm, cancel and a silent responder refusal).
        //
        // Only the SUCCESS branch is converted. A `Failed` from `launch` stays `Failed`: a dialog
        // that never appeared is a failure we CAN see, and blurring it into "handed off" would give
        // back, in the other direction, exactly the honesty this change buys.
        return when (val dispatched = launch(Intent(Intent.ACTION_DELETE, Uri.fromParts("package", target, null)))) {
            is ToolResult.Effected -> ToolResult.HandedOff()
            else -> dispatched
        }
    }
```
**Read `Tier0IntentToolWorker.kt:263-267` before pasting** — the two guards above are transcribed
from it and must match what is there, not replace it.

- [ ] **Step 4: run, expect all three PASS**

- [ ] **Step 5: mutation — and expect TWO reds, not one**

```bash
# M5 — convert the wrong branch, losing the Effected/Failed distinction in both directions
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
F=data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt
cp "$F" "$MUT/m5"
sed -i 's/is ToolResult.Effected -> ToolResult.HandedOff()/is ToolResult.Failed -> ToolResult.HandedOff()/' "$F"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m5" "$F"
```
Restored with `cp`: a `git checkout` here would have erased this task's fix itself — Task 5 is not
committed yet (§0.6 finding 2).
Expected RED on **both** `uninstall_app hands off — it does not claim the app was removed` (the
success case falls to `else` and returns `Effected` again) **and** `an uninstall that could not be
dispatched is Failed, not handed off` (the failure case is now converted). Two reds is the correct
result and is written here so the implementer does not treat the second as an unexplained regression.
One red means one of the two tests is not asserting what it claims.

- [ ] **Step 6: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1448`, `:data:repository` 380.

```bash
git add data/repository/src
git commit -m "$(cat <<'EOF'
fix(agentic-6/A4' ф0): uninstall_app больше не заявляет исход, которого не видит

Спека §3 0.2(1). Единственная арка воркера, где Effected была ложью:
startActivity(ACTION_DELETE) возвращается в момент ПОДНЯТИЯ диалога и
возвращается одинаково при подтверждении, отмене и молчаливом отказе
ответчика (строки 16/28/32). Теперь HandedOff.

launch() НЕ трогается, и это измеренная причина, а не осторожность:
одиннадцать навигирующих действительно открывают экран, а set_alarm/
set_timer действительно заводят будильник (строки 13/29) — у них возврат
startActivity И ЕСТЬ эффект. Тест на все четырнадцать держит это явно:
починка, понизившая бы их, обменяла бы одно неверное слово на четырнадцать.

Конвертируется только успешная ветка: Failed остаётся Failed — диалог,
который не появился, это отказ, который МЫ ВИДИМ, и размазать его в
«передано» значило бы вернуть ту же нечестность с другой стороны.

Мутация M5 (конвертировать не ту ветку) -> RED на ДВУХ тестах, по одному
на каждое направление. Два красных здесь — правильный результат.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: the surface stops collapsing four realities into two words

**Spec:** §3 0.2(1б)(1в), §10 criterion 4. Depends on Tasks 4–5. **This is the item the spec calls
«закрывает больше и стоит меньше».**

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt:413-462`
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt:143-145`
- Modify: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Test: `feature/launcher/src/test/java/…/agent/AgentSessionPresentationTest.kt`
- Test: `feature/launcher/src/test/java/…/agent/AgentSessionSurfaceHandedOffTest.kt` (create)
- Test: `app/src/test/java/com/sidr/launcher/i18n/StepStateWordDistinctnessGuardTest.kt` (create)

**Interfaces:**
- Consumes: `ToolResult.HandedOff` (Task 4).
- Produces: `AgentStepState.{OBSERVED, WAITING, HANDED_OFF}`; `AgentSession.anyStepHandedOff()`.
  `everyStepExecuted()` keeps its name **and its meaning** — see step 4.

- [ ] **Step 1: write the four failing presentation tests**

```kotlin
    /**
     * D1 (spec §3 0.2(1б)) — the defect is WIDER than the ADR says. The ADR frames finding (a) as a
     * worker problem; the 2026-09-21 measurement found the same lie where the worker is HONEST.
     * The vocabulary is three-valued and this function had no branch for `Observed` at all, so
     * «Открыть wattsupp — выполнено» was drawn for an app that does not exist, over a correct
     * `Observed(APP_NOT_INSTALLED)`. That is A0's single most common shape, and it predates the
     * whole A1″ block.
     */
    @Test
    fun `an observation is not an execution`() = runTest {
        val s = session(cursor = 1, state = ExecutionState.Running, observations = mapOf(0 to notInstalled))

        assertEquals(AgentStepState.OBSERVED, s.stateOf(s.plan.steps[0]))
        assertNotEquals(AgentStepState.DONE, s.stateOf(s.plan.steps[0]))
    }

    /**
     * D2 (spec §3 0.2(1в)) — `CURRENT` was `step.index == cursor` with no reference to `state`, so
     * a step the engine had STOPPED on was drawn as running while the same card carried the button
     * asking the user to continue. The surface said «работает» and «нажмите, чтобы продолжить» at
     * once.
     */
    @Test
    fun `a step the engine stopped on is not in progress`() = runTest {
        val gated = session(cursor = 1, state = ExecutionState.AwaitingConsent, observations = mapOf(0 to notInstalled))
        val paused = session(cursor = 1, state = ExecutionState.Paused, observations = mapOf(0 to notInstalled))

        assertEquals(AgentStepState.WAITING, gated.stateOf(gated.plan.steps[1]))
        assertEquals(AgentStepState.WAITING, paused.stateOf(paused.plan.steps[1]))
    }

    /**
     * The third instance of the same defect, found by this plan's pre-flight and named in no
     * document (P4). `AgentExecutor.perform` advances the cursor BEFORE `ended(...)`, so a dead
     * session pointed its cursor at a step that will never run — and that step read as «выполняется».
     */
    @Test
    fun `a terminal session has nothing in progress`() = runTest {
        val s = session(
            cursor = 1,
            state = ExecutionState.Failed,
            observations = mapOf(0 to ToolResult.Failed(CommandFailure.Generic)),
        )

        assertEquals(AgentStepState.PENDING, s.stateOf(s.plan.steps[1]))
        assertNotEquals(AgentStepState.CURRENT, s.stateOf(s.plan.steps[1]))
    }

    /**
     * The fourth value reaches the screen, and the two predicates disagree about it on purpose: the
     * step DID run (`everyStepExecuted`), and the plan still may not call itself whole
     * (`anyStepHandedOff`). Step 8 of this task is what joins them at the one place it matters.
     */
    @Test
    fun `a handed-off step is neither done nor a whole plan`() = runTest {
        val s = session(cursor = 2, observations = mapOf(0 to notInstalled, 1 to ToolResult.HandedOff()))

        assertEquals(AgentStepState.HANDED_OFF, s.stateOf(s.plan.steps[1]))
        assertTrue("the step did run — that is a different question", s.everyStepExecuted())
        assertTrue(s.anyStepHandedOff())
    }
```

- [ ] **Step 2: run, expect compile failure then assertion failures**

```bash
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest --tests '*AgentSessionPresentationTest*' --rerun-tasks
```

- [ ] **Step 3: replace the enum and `stateOf` with one rule**

```kotlin
internal enum class AgentStepState { DONE, OBSERVED, HANDED_OFF, SKIPPED, FAILED, CURRENT, WAITING, PENDING }

/**
 * **The observation decides, exhaustively; only when there is none does the session decide.**
 *
 * `when (this)` over [ToolResult] with no `else` is the point of the split: this function had a
 * `!= null -> DONE` catch-all, and that catch-all is precisely how `Observed` came to be drawn as
 * «выполнено». A fifth [ToolResult] value is now a compile error here instead of a fifth silent
 * success marker.
 */
private fun ToolResult.stepState(): AgentStepState = when (this) {
    is ToolResult.Effected -> AgentStepState.DONE
    is ToolResult.Observed -> AgentStepState.OBSERVED
    is ToolResult.HandedOff -> AgentStepState.HANDED_OFF
    is ToolResult.Failed -> AgentStepState.FAILED
}

/**
 * Where one step actually stands — read from what the session recorded, and, for a step with no
 * record yet, **from the session's own state** (A4' phase 0, spec §3 0.2(1б)(1в)).
 *
 * The 2026-08-23 round corrected the first half of this function (a step's marker had been derived
 * from the cursor, collapsing ran / skipped / failed into SUCCESS) and left the second half
 * uncorrected in three ways, all of which this rule closes at once:
 *  - `Observed` had **no branch at all** and fell into `!= null -> DONE`, so the most common shape
 *    A0 produces — "the app is not installed" — was drawn as «выполнено» (D1);
 *  - `step.index == cursor -> CURRENT` never consulted `state`, so a step the engine had stopped
 *    on for **consent** was drawn as «выполняется» beneath the button asking to continue (D2);
 *  - the same line drew the cursor step of a **terminal** session as «выполняется», because
 *    `AgentExecutor.perform` advances the cursor before `ended(...)` (found 2026-09-22, named in no
 *    document before this one).
 *
 * The order is load-bearing and is not alphabetical: a recorded observation outranks any state, a
 * skipped step outranks the cursor, and only a step that is *at* the cursor is allowed to ask what
 * the session is doing.
 */
internal fun AgentSession.stateOf(step: PlanStep): AgentStepState =
    observations[step.index]?.stepState()
        ?: when {
            trace.events.any { it is TraceEvent.StepSkipped && it.index == step.index } -> AgentStepState.SKIPPED
            step.index != cursor -> AgentStepState.PENDING
            state == ExecutionState.Running -> AgentStepState.CURRENT
            state.isTerminal -> AgentStepState.PENDING
            else -> AgentStepState.WAITING
        }
```

**And correct the file-level KDoc at `:41-43`, which this step falsifies.** It says `stateOf` "is a
subject-less `when` over predicates, not a dispatch on a sum at all … the 'none of the four signals
fired' arm and reads [AgentStepState.PENDING]". After this step the recorded-observation half **is**
a dispatch on a sum (`ToolResult.stepState()`, `else`-free), and the subject-less `when` that remains
reads the session only for a step with no record, with `else -> WAITING`. Say that.

Extend `marker()` and `word()` with the three new values:

```kotlin
internal fun AgentStepState.marker(): SidrStatus = when (this) {
    AgentStepState.DONE -> SidrStatus.SUCCESS
    AgentStepState.OBSERVED -> SidrStatus.INFO
    AgentStepState.HANDED_OFF -> SidrStatus.ATTENTION
    AgentStepState.SKIPPED -> SidrStatus.INFO
    AgentStepState.FAILED -> SidrStatus.DANGER
    AgentStepState.CURRENT -> SidrStatus.ATTENTION
    AgentStepState.WAITING -> SidrStatus.ATTENTION
    AgentStepState.PENDING -> SidrStatus.INFO
}

@Composable
@ReadOnlyComposable
internal fun AgentStepState.word(): String = when (this) {
    AgentStepState.DONE -> sidrString(R.string.launcher_agent_step_state_done)
    AgentStepState.OBSERVED -> sidrString(R.string.launcher_agent_step_state_observed)
    AgentStepState.HANDED_OFF -> sidrString(R.string.launcher_agent_step_state_handed_off)
    AgentStepState.SKIPPED -> sidrString(R.string.launcher_agent_step_state_skipped)
    AgentStepState.FAILED -> sidrString(R.string.launcher_agent_step_state_failed)
    AgentStepState.CURRENT -> sidrString(R.string.launcher_agent_step_state_current)
    AgentStepState.WAITING -> sidrString(R.string.launcher_agent_step_state_waiting)
    AgentStepState.PENDING -> sidrString(R.string.launcher_agent_step_state_pending)
}
```

- [ ] **Step 4: add `anyStepHandedOff` beside `everyStepExecuted` — two predicates, not one renamed**

```kotlin
/**
 * A step whose act left the launcher and whose outcome the launcher cannot see.
 *
 * **Deliberately a second predicate rather than a clause inside [everyStepExecuted]**, because the
 * two ask different questions and folding them would make one name false: a handed-off step *did*
 * execute. What it did not do is finish where we can see it. Keeping them apart also keeps each
 * falsifiable on its own — one mutation per property, which is the rule this block pays for.
 */
internal fun AgentSession.anyStepHandedOff(): Boolean =
    plan.steps.any { observations[it.index] is ToolResult.HandedOff }
```

And at `AgentSessionSurface.kt:144`:

```kotlin
            // «План выполнен» requires both: every step ran, and no step's outcome is out of our
            // sight. A cancelled `uninstall_app` satisfies the first and fails the second, which is
            // exactly the case `DOC-ILM-4` is about.
            val whole = session.everyStepExecuted() && !session.anyStepHandedOff()
```

- [ ] **Step 5: add three strings in three locales — and mark the wording as a hypothesis**

`values/strings.xml`:
```xml
    <string name="launcher_agent_step_state_observed">nothing changed</string>
    <string name="launcher_agent_step_state_handed_off">handed to the system</string>
    <string name="launcher_agent_step_state_waiting">waiting for you</string>
```
`values-ru/strings.xml`:
```xml
    <string name="launcher_agent_step_state_observed">без изменений</string>
    <string name="launcher_agent_step_state_handed_off">передано системе</string>
    <string name="launcher_agent_step_state_waiting">ждёт вас</string>
```
`values-tr/strings.xml`:
```xml
    <string name="launcher_agent_step_state_observed">değişiklik yok</string>
    <string name="launcher_agent_step_state_handed_off">sisteme aktarıldı</string>
    <string name="launcher_agent_step_state_waiting">sizi bekliyor</string>
```

Ordinary (Class C) keys in `strings.xml`, **not** `strings_locked.xml`, so the `OWNER-REVIEWED`
signature is neither touched nor re-signed — the same call the 2026-08-23 fix round made for its six
strings.

**Write into the task log and carry to the acceptance checklist:** *these three renderings, in all
three locales, are a hypothesis until a device round has shown them.* Nobody has seen these words on
a screen. The A1″ round found the checklist erring more often than the code, and three of its four
errors were claims about what the user would see, written without a run.

- [ ] **Step 6: correct the three tests that pinned the lie (P5), and read the fourth before touching it**

- `AgentSessionSurfaceProvenanceTest` — `the A0 two-step plan renders the two sentences it always
  has` (`:164-170`, the file the first two drafts never opened): `a0Session()` is `Paused` at
  `cursor = 0`, so `"Open убер — in progress"` becomes **`"Open убер — waiting for you"`**;
  `"Find убер in the app store — not started"` does not move. Its KDoc (`:150-162`) calls this pair
  "the byte-identity baseline" of the owner-accepted surface — add one sentence saying that A4′
  phase 0 changed the **state word** of the paused step deliberately (D2) and the **subject** of both
  sentences did not, so the baseline now pins the subject plus the corrected word. Do not soften the
  expectation to a `substring = true` match on the subject: the word is exactly what D2 is about.
- `a plan whose every step ran is whole` (`:142-151`): step 0's expectation becomes
  `AgentStepState.OBSERVED`; `everyStepExecuted()` stays `assertTrue`. Add to its KDoc: *"an
  `Observed` step ran — it is `OBSERVED`, not `DONE`; that distinction is D1."*
- `the step at the cursor is current and the ones after it are pending` (`:119-126`): it builds an
  `AwaitingConsent` session, so step 0 becomes `OBSERVED` and step 1 becomes `WAITING`. **Rename it**
  to `a gated session marks the gated step waiting, not current` — a test whose name says `current`
  while asserting `WAITING` is the next reader's trap.
- **`no two step states are indistinguishable` (`:159-169`) is NOT what this plan's first draft said
  it was**, and the correction is this step's most important line. It reads
  `AgentStepState.entries.associateWith { it.marker() }` and asserts `4 == markers.values.toSet().size`
  plus `markers[SKIPPED] == markers[PENDING]`. **There is no word in it and there cannot be** —
  `word()` is `@Composable @ReadOnlyComposable` (`:440-442`) and unreachable from a JVM test. With
  eight states it passes **unchanged**, while its own failure message ("anything else sharing one is
  a collision") becomes false: `INFO` would be shared by `OBSERVED`/`SKIPPED`/`PENDING` and
  `ATTENTION` by `HANDED_OFF`/`CURRENT`/`WAITING`.

  So: rewrite its assertion to state what is now true — four markers over eight states, with the two
  named sharing groups listed explicitly — and rewrite its message to say that **distinctness is
  carried by the word and is held elsewhere**, naming step 7's guard. A test left asserting `4` with
  a message about collisions would be the ninth instance of this block's own disease.

- [ ] **Step 7: hold word-distinctness where it can actually be held — the resources**

`R-ADL-2` says the dot is decorative and the label carries meaning. With eight states over four
markers, two states sharing a marker **and** a word would be indistinguishable in greyscale and to
TalkBack. Nothing holds that today. A JVM test cannot call `word()`, but it can read the resources
the words live in:

```kotlin
package com.sidr.launcher.i18n

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **`AgentStepState` has eight values and four markers, so the WORD is what tells six of them
 * apart** (`R-ADL-2`: status is never colour alone). `AgentSessionPresentationTest` cannot hold
 * this — `AgentStepState.word()` is `@Composable` and unreachable from a JVM test — so it is held
 * where the words actually are.
 *
 * Two states sharing a marker AND a word would be invisibly identical in greyscale and to TalkBack.
 * The pairs at risk are real, not theoretical: `OBSERVED`/`SKIPPED`/`PENDING` all render `INFO`,
 * and `HANDED_OFF`/`CURRENT`/`WAITING` all render `ATTENTION`.
 *
 * Working directory is the module dir (`app`), so the repo root is `..` — the idiom
 * `LocaleCompletenessGuardTest` uses.
 */
class StepStateWordDistinctnessGuardTest {

    private val keys = listOf(
        "launcher_agent_step_state_done",
        "launcher_agent_step_state_observed",
        "launcher_agent_step_state_handed_off",
        "launcher_agent_step_state_skipped",
        "launcher_agent_step_state_failed",
        "launcher_agent_step_state_current",
        "launcher_agent_step_state_waiting",
        "launcher_agent_step_state_pending",
    )

    @Test
    fun `every step state reads differently in every locale`() {
        listOf("values", "values-ru", "values-tr").forEach { dir ->
            val xml = File("../feature/launcher/src/main/res/$dir/strings.xml").readText()
            val values = keys.map { key ->
                Regex("""<string name="$key">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
                    .find(xml)?.groupValues?.get(1)
                    ?: error("$dir: '$key' is missing — LocaleCompletenessGuardTest should have caught this first")
            }

            assertEquals(
                "$dir: two step states render the same word. Eight states share four markers, so " +
                    "the word is the only thing telling six of them apart (R-ADL-2). " +
                    "Collisions: " + values.groupBy { it }.filter { it.value.size > 1 }.keys,
                keys.size,
                values.toSet().size,
            )
        }
    }
}
```

- [ ] **Step 8: hold the `Completed` surface line — a Robolectric test, because nothing else can**

`everyStepExecuted()` has exactly **one** production call site: `AgentSessionSurface.kt:144`, inside
the `@Composable` `ExecutionState.Completed` branch. A unit test on the two predicates — including
step 1's fourth test — does not touch that line, so a mutation deleting `&& !anyStepHandedOff()`
stays green. The property is a rendering property and is held the way `DOC-ILM-2` is held: by driving
the real surface through real resources.

Create `AgentSessionSurfaceHandedOffTest.kt` following `AgentSessionSurfaceProvenanceTest`'s shape
exactly — `@RunWith(RobolectricTestRunner::class)`, `@Config(sdk = [34])`, `createComposeRule`,
`SidrTheme`, `onNodeWithText`:

```kotlin
    /**
     * `DOC-ILM-4` at the one place it is decided. A plan whose act was handed to the OS has not been
     * shown to have succeeded — a cancelled `uninstall_app` is exactly that — so the `Completed`
     * surface must carry the `Partial` title, not «План выполнен».
     *
     * This is a rendering property and a unit test cannot hold it: `everyStepExecuted()` has one
     * production call site and it is inside a `@Composable`. Same argument, same shape, as
     * [AgentSessionSurfaceProvenanceTest].
     */
    @Test
    fun `a completed plan with a handed-off step is shown as partial, not as done`() {
        composeTestRule.setContent {
            SidrTheme {
                AgentSessionSurface(session = completedWithHandedOffStep(), /* … as the neighbour test wires it … */)
            }
        }

        composeTestRule.onNodeWithText(partialTitle).assertExists()
        composeTestRule.onNodeWithText(completedTitle).assertDoesNotExist()
    }
```
The neighbour does **not** resolve resources: it hardcodes the default-locale (`en`) sentence its
runner renders (`"Open убер — in progress"`, `:168`), with a rule named `compose` (`:62`) and a
`render(...)` helper. Do the same:
`partialTitle = "Plan finished, not every step ran"`, `completedTitle = "Plan complete"`
(`values/strings.xml:189-190`).

**The substring question does not arise, and the reason is measured, not assumed.**
`onNodeWithText` without `substring = true` matches a node's **whole** text: in Compose `ui-test`
1.12.0 (the version on this classpath), `onNodeWithText$default` sets `substring = false`, and that
path compares with `equals` — the `substring = true` path is the one that calls `contains` (read with
`javap`, 2026-09-25). So «Plan complete» cannot match «Plan finished, not every step ran» although
both start with «Plan» — and in `ru` the pair also shares «выполнен». R14-43 bites a
`substring = true` assertion; **do not switch these two to it**, and do not replace them with the
body string — the title is the line `DOC-ILM-4` is about.

- [ ] **Step 9: run, expect all green**

```bash
tools/gate.sh --scoped :feature:launcher:testDebugUnitTest :app:testDebugUnitTest
```

- [ ] **Step 10: four mutations, each on a named assert**

**By the mutation protocol, and this task is where it matters most** (§0.6 finding 2). The first
version undid each of these with `git checkout`. Task 6 is uncommitted at this point, so the checkout
after M6a returned `AgentSessionPresentation.kt` to HEAD — the five-value enum, no
`anyStepHandedOff` — M6b's `sed` then matched nothing, silently, and M6b and M6c went red on a
**compile error**: the tests reference `OBSERVED`/`WAITING`/`HANDED_OFF`, and the surface references
`anyStepHandedOff`. Both would have been logged as proved; the checkout after M6d would have deleted
the three new `ru` strings.

```bash
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
P=feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt
S=feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt
R=feature/launcher/src/main/res/values-ru/strings.xml

# M6a — restore the catch-all that made Observed a success
cp "$P" "$MUT/m6a"
sed -i 's/    is ToolResult.Observed -> AgentStepState.OBSERVED/    is ToolResult.Observed -> AgentStepState.DONE/' "$P"
tools/gate.sh --scoped :feature:launcher:testDebugUnitTest
cp "$MUT/m6a" "$P"

# M6b — stop consulting the session state
cp "$P" "$MUT/m6b"
sed -i 's/            state == ExecutionState.Running -> AgentStepState.CURRENT/            true -> AgentStepState.CURRENT/' "$P"
grep -c 'true -> AgentStepState.CURRENT' "$P"    # must print 1 — a sed that matched nothing is not a mutation
tools/gate.sh --scoped :feature:launcher:testDebugUnitTest
cp "$MUT/m6b" "$P"

# M6c — let a handed-off plan call itself whole (the mutation the first draft could not catch)
cp "$S" "$MUT/m6c"
sed -i 's/session.everyStepExecuted() \&\& !session.anyStepHandedOff()/session.everyStepExecuted()/' "$S"
tools/gate.sh --scoped :feature:launcher:testDebugUnitTest
cp "$MUT/m6c" "$S"

# M6d — collide two words in one locale
cp "$R" "$MUT/m6d"
sed -i 's|<string name="launcher_agent_step_state_waiting">ждёт вас</string>|<string name="launcher_agent_step_state_waiting">без изменений</string>|' "$R"
tools/gate.sh --scoped :app:testDebugUnitTest
cp "$MUT/m6d" "$R"
```

**Predicted `FAILED ::` sets** — derived from the rule of step 3 and the tests of steps 1, 6 and 8,
written before the run; a different set is a finding to run down, in either direction:

| Mutation | Predicted failing tests | Count |
|---|---|---:|
| M6a | `AgentSessionPresentationTest` > `an observation is not an execution`; > `a gated session marks the gated step waiting, not current` (its step 0 is `OBSERVED`); > `a plan whose every step ran is whole` (its step 0 is `OBSERVED`) | 3 |
| M6b | `AgentSessionPresentationTest` > `a step the engine stopped on is not in progress`; > `a terminal session has nothing in progress`; > `a gated session marks the gated step waiting, not current` (its step 1); `AgentSessionSurfaceProvenanceTest` > `the A0 two-step plan renders the two sentences it always has` (a `Paused` step reads «in progress» again) | 4 |
| M6c | `AgentSessionSurfaceHandedOffTest` > `a completed plan with a handed-off step is shown as partial, not as done` | 1 |
| M6d | `StepStateWordDistinctnessGuardTest` > `every step state reads differently in every locale` | 1 |

**M6c is the one that matters most** — it is the mutation the first draft of this plan would have let
pass. If it stays green, the Robolectric test of step 8 is not reaching the line, and that must be
fixed before this task closes rather than recorded as a limitation. And it must be red **on that
test**, with Gradle having compiled: a `gradle=1` with no `FAILED ::` line is a compile error and
proves nothing.

- [ ] **Step 11: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1454`, `:feature:launcher` 199 (194 + 4 + 1), `:app` 61 (60 + 1).

```bash
git add feature/launcher/src app/src/test
git commit -m "$(cat <<'EOF'
fix(agentic-6/A4' ф0): поверхность перестаёт схлопывать четыре реальности в два слова

Спека §3 0.2(1б)(1в), критерий 4 §10. Три лжи одной строкой правила:

D1 — у `Observed` не было ВЕТКИ ВОВСЕ, он падал в `!= null -> DONE`,
поэтому «Открыть wattsupp — выполнено» рисовалось для приложения,
которого нет, при честном Observed(APP_NOT_INSTALLED) от воркера. Это
самая частая форма A0, и она старше всего блока A1″.

D2 — `step.index == cursor -> CURRENT` не смотрел на `state`, и шаг,
на котором движок ОСТАНОВИЛСЯ ради согласия, рисовался «выполняется»
под кнопкой «Продолжить» на той же карточке.

Третий случай той же болезни, не названный ни одним документом (найден
предполётом плана): perform двигает курсор ДО ended(...), поэтому мёртвая
сессия рисовала «выполняется» на шаге, который уже не исполнится.

Исчерпывающий `when` по ToolResult без `else` — суть разделения: пятое
значение теперь ошибка компиляции, а не пятый молчаливый маркер успеха.

anyStepHandedOff — ВТОРОЙ предикат, а не оговорка внутри
everyStepExecuted: переданный шаг ИСПОЛНИЛСЯ, и сложить их значило бы
сделать одно имя ложным.

Две вещи, которые нашло ревью плана и без которых починка была бы
самообманом. Первая: тест `no two step states are indistinguishable`
сравнивает ТОЛЬКО маркеры — word() это @Composable и из JVM-теста
недостижим, — поэтому с восемью состояниями он прошёл бы БЕЗ ЕДИНОЙ
ПРАВКИ, а его собственное сообщение стало бы ложным; ассерт и сообщение
переписаны под то, чем он является, а различимость СЛОВ вынесена в
StepStateWordDistinctnessGuardTest над ресурсами трёх локалей. Вторая:
everyStepExecuted() имеет ровно одну production-площадку, и та внутри
@Composable, поэтому строку :144 держит Robolectric-тест по образцу
AgentSessionSurfaceProvenanceTest, а не юнит-тест над предикатами.

Три строки в en/ru/tr, все Class C — подпись локалей не тронута.
РЕНДЕРИНГ НИКТО НЕ ВИДЕЛ: слова — гипотеза до устройства, несётся в
чеклист приёмки.

Третий тест, державший ложь D2, — AgentSessionSurfaceProvenanceTest, «базовая
линия побайтовой идентичности» принятой владельцем поверхности: у шага на
паузе слово «in progress» сменилось на «waiting for you» НАМЕРЕННО, подлежащее
обеих фраз не сдвинулось. Это видимое владельцу изменение, оно идёт в
чеклист приёмки.

Мутации — по протоколу cp, не git checkout (checkout откатил бы шаги 3–5
этой же задачи, и M6b/M6c покраснели бы от ошибки компиляции): M6a -> RED
(три теста), M6b -> RED (четыре), M6c -> RED (один — та самая, что в
первой редакции плана осталась бы зелёной), M6d -> RED (один); каждый
набор FAILED :: сверен с предсказанным в плане до прогона.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: the engine gets a clock — and both staleness layers get named

**Spec:** §3 0.3, §10 criterion 3. Owner forks **F0-2** (interrupt) and **F0-3** (no timestamps here).

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt:23-29` (`RuntimeBudget`)
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt:223-229`
- Modify (KDoc only): `AgentSession.resumed()` (`:81`) and `TemplatePlanner.kt:24-33`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt`

**Interfaces:**
- Consumes: `ToolResult.HandedOff` (Task 4).
- Produces: `RuntimeBudget(maxSteps, maxConsecutiveFailures, maxStepWallClockMs: Long = 10_000)` — a
  third parameter **with a default**, so no existing construction site changes.

**Why a value and not a port (P7):** `withTimeout` reads the coroutine dispatcher's clock, which
`runTest` makes virtual — deterministic tests, no real time, no injected dependency in production.
The spec's second consumer for a clock port is staleness, and F0-3 sent that to phase 2. An empty
port now would be the `:data:ai-local` error the project's own §4 rule is built from.

- [ ] **Step 1: write the two failing tests**

`AgentExecutorTest`'s helpers are at `:34-77` — `registry`, `budget`, `notInstalled()`,
`planForMissingApp()`, `session()`. Use them as they are.

```kotlin
    /**
     * The A0 debt, closed: "a hanging tool is bounded by nothing" (`RuntimeBudget` bounded steps and
     * consecutive failures only, and the domain is deliberately clock-free). A tool that never
     * returns used to hold the session open forever with no trace event and no way out.
     *
     * The result recorded is `HandedOff` and not `Failed`, and the difference is the honest one: the
     * call WAS made, we stopped waiting, and the side effect may well have happened. Recording a
     * failure would claim knowledge in the other direction.
     */
    @Test
    fun `a tool that never returns is cut at the wall-clock budget and recorded as handed off`() = runTest {
        val hanging = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                delay(Long.MAX_VALUE / 2)
                error("unreachable: the budget must cut this call")
            }
        }
        val executor = AgentExecutor(
            registry,
            hanging,
            RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2, maxStepWallClockMs = 1_000),
        )

        val after = executor.advance(session())

        assertTrue(after.observations[0] is ToolResult.HandedOff)
        assertTrue(
            "a cut step must still close its trace — a trace left mid-step means 'the process died'",
            after.trace.events.any { it is TraceEvent.ToolObserved && it.index == 0 },
        )
        assertEquals(1, after.cursor)
    }

    /**
     * The non-vacuity half. A budget that cut everything would pass the test above and destroy the
     * product; a budget that cut nothing would pass this one. Both are needed, and the second is the
     * one a "make it green" edit breaks.
     */
    @Test
    fun `a tool that returns inside the budget keeps its own result`() = runTest {
        val prompt = object : ToolExecutor {
            override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
                delay(10)
                return notInstalled()
            }
        }
        val executor = AgentExecutor(
            registry,
            prompt,
            RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2, maxStepWallClockMs = 1_000),
        )

        val after = executor.advance(session())

        assertEquals(notInstalled(), after.observations[0])
    }
```

- [ ] **Step 2: run, expect a compile failure on `maxStepWallClockMs`**

- [ ] **Step 3: widen `RuntimeBudget`**

```kotlin
/**
 * Loop bounds — and, since A4' phase 0, a **wall-clock** bound too.
 *
 * [maxStepWallClockMs] closes the A0 debt this KDoc used to name as deferred ("a wall-clock limit
 * needs a clock port and is honestly deferred to A4'"). It needed no port: `withTimeout` reads the
 * dispatcher's clock, which `runTest` makes virtual, so the domain stays clock-free in the sense
 * that mattered — nothing here calls `System.currentTimeMillis()` and no test depends on real time.
 *
 * The default is an order of magnitude above the slowest path measured on the SM-A325F (an intent
 * dispatch is milliseconds; `launch_app` resolves through `PackageManager`). It is a bound on a
 * **hang**, not a latency policy: a tool that takes 9 seconds is not the failure this exists to
 * catch, and tightening it toward the measured numbers would start failing correct work on a cold
 * or loaded device.
 */
data class RuntimeBudget(
    val maxSteps: Int,
    val maxConsecutiveFailures: Int,
    val maxStepWallClockMs: Long = 10_000,
) {
    companion object {
        /** A0's default: two steps of headroom over the one plan shape that exists. */
        val Default = RuntimeBudget(maxSteps = 8, maxConsecutiveFailures = 2)
    }
}
```

- [ ] **Step 4: bound the one call site — and mind the catch order (P6)**

In `AgentExecutor.perform`, replacing the existing `try` block, and adding imports
`kotlinx.coroutines.TimeoutCancellationException` and `kotlinx.coroutines.withTimeout`:

```kotlin
        val result = try {
            withTimeout(budget.maxStepWallClockMs) { toolExecutor.invoke(resolved) }
        } catch (e: TimeoutCancellationException) {
            // FIRST, and the order IS the behaviour: TimeoutCancellationException IS a
            // CancellationException, so placing this after the rethrow below would send the timeout
            // up as parent cancellation and kill the session instead of bounding the step.
            //
            // `HandedOff` rather than `Failed`, for the same reason `uninstall_app` reports it: the
            // call was made and we stopped waiting. The side effect may have happened. `Failed`
            // would claim knowledge in the other direction, and the whole point of the fourth value
            // is that this engine stops claiming outcomes it cannot see.
            ToolResult.HandedOff()
        } catch (e: CancellationException) {
            throw e // never swallow parent cancellation
        } catch (e: Exception) {
            ToolResult.Failed(CommandFailure.Generic)
        }
```

The literal `toolExecutor.invoke(` still occurs exactly once in this file, so
`ToolExecutorCallSiteGuardTest` neither breaks nor weakens — verified against its counting rule
(`:120`, `:134`) on 2026-09-22.

- [ ] **Step 5: run, expect both green**

```bash
tools/gate.sh --scoped :domain:jvmTest
```

- [ ] **Step 6: two mutations**

```bash
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
E=domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt
B=domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt

# M7a — delete the timeout arm BY HAND (a sed across a multi-line catch arm is its own bug), so
#       TimeoutCancellationException falls through to the rethrow below. Same defect as writing the
#       two catches in the wrong order (P6): the timeout leaves as parent cancellation and kills the
#       session instead of bounding the step.
cp "$E" "$MUT/m7a"
#   … delete the arm in an editor …
diff "$MUT/m7a" "$E"                      # eyeball it: exactly the timeout arm, nothing else
tools/gate.sh --scoped :domain:jvmTest
cp "$MUT/m7a" "$E"

# M7b — raise the budget so nothing is ever cut
cp "$B" "$MUT/m7b"
sed -i 's/val maxStepWallClockMs: Long = 10_000,/val maxStepWallClockMs: Long = Long.MAX_VALUE,/' "$B"
tools/gate.sh --scoped :domain:jvmTest
cp "$MUT/m7b" "$B"
```
Predicted `FAILED ::` sets: M7a → `AgentExecutorTest > a tool that never returns is cut at the
wall-clock budget…` (the first test). M7b → the **first test only** — the second must stay green,
which is what proves the second is a non-vacuity check. If M7b reddens **both** tests, the second
test is not measuring what it claims and must be fixed before this task closes.

`diff` against the saved copy replaces the first version's `git diff`, and `cp` replaces its
`git checkout` — which, for M7b, would have erased this task's own `RuntimeBudget` parameter from
`AgentSession.kt` with no note saying so (§0.6 finding 2).

- [ ] **Step 7: pin the shipped default**

```kotlin
    /**
     * The default is a product decision (10 s, an order of magnitude above the slowest measured
     * path), not an accident of construction — and `RuntimeBudget.Default` is what `:app` binds.
     * A change to it is a change to how long the launcher will sit on a hung tool.
     */
    @Test
    fun `the shipped budget bounds a step by wall-clock time`() {
        assertEquals(10_000L, RuntimeBudget.Default.maxStepWallClockMs)
    }
```

- [ ] **Step 8: check every existing construction site still compiles**

```bash
grep -rn "RuntimeBudget(" --include=*.kt . | grep -v "/build/"
```
Every site should pass two positional arguments, so the defaulted third changes none of them.
**Verify that; do not assume it.** A site already passing three positional arguments would mean this
plan read a different type than the tree has.

- [ ] **Step 9: name both staleness layers — the other half of 0.3 (F0-3)**

No code. Append to `AgentSession.resumed()`'s KDoc:

```kotlin
     * **Staleness, both layers, named here because this is the function that resumes** (A4' phase 0,
     * spec §3 0.3; owner fork F0-3 — phase 0 decides and names, phase 2 builds what reacts, exactly
     * as §6.4 divides it):
     *
     *  - **Layer 1 — the engine's own observations.** This continues from the persisted cursor and
     *    nothing re-plans, so an observation taken before the pause is acted on however stale it has
     *    become (A0 acceptance §12.8: a session that already observed `APP_NOT_INSTALLED` opens the
     *    store even if the app was installed while the plan sat paused). **Addressed to phase 2**,
     *    where re-planning on partial failure is the same mechanism and where
     *    `PlanningRequest.priorObservations` is the input it needs — you cannot react to a re-check
     *    without something that re-plans.
     *  - **Layer 2 — a source's own snapshot.** `ShortcutRefreshTrigger.start()` runs once per
     *    process and observes **shortcut** changes; a grant of the `android.app.role.HOME` role is
     *    not one, so the dynamic catalog stays empty until a restart (A1″ acceptance finding (b),
     *    measured). **Named EXCLUDED from A4' with its own address**: it is a property of one
     *    Android source's refresh trigger, not of the engine, and criterion 3 §10 admits an excluded
     *    layer provided it is named rather than implied absent. It belongs to whichever block next
     *    touches `ShortcutToolSource`.
     *
     * Neither is closed by the wall-clock budget, and the budget must not be mistaken for them: it
     * bounds how long **one call** may take, not how old a **fact** may be.
```

Add a one-line cross-reference in `TemplatePlanner`'s KDoc (`:24-33`, which already describes the
§12.8 path) pointing at the paragraph above, so the two cannot drift.

- [ ] **Step 10: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1457`, `:domain` 445.

```bash
git add domain/src
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф0): у движка появились часы — шаг ограничен по стенному времени

Спека §3 0.3, критерий 3 §10, форки владельца F0-2 и F0-3.

Долг A0 закрыт: RuntimeBudget ограничивал шаги и подряд идущие отказы, а
зависший инструмент не был ограничен НИЧЕМ. withTimeout на единственной
точке вызова к миру — и порядок catch ЕСТЬ поведение:
TimeoutCancellationException ЯВЛЯЕТСЯ CancellationException, поэтому арка
таймаута стоит ПЕРВОЙ, иначе таймаут ушёл бы вверх родительской отменой и
убил сессию вместо того, чтобы ограничить шаг. Мутация M7a на этом.
Литерал toolExecutor.invoke( остаётся в файле ровно один, поэтому
ToolExecutorCallSiteGuardTest не ломается и не ослабевает.

Порта нет, и это измеренная причина, а не экономия: withTimeout читает
часы диспетчера, runTest делает их виртуальными — домен остаётся
бесчасовым в том смысле, который важен. Второй потребитель часов
(staleness) отправлен форком F0-3 в фазу 2, а пустой порт вперёд него был
бы ровно ошибкой :data:ai-local, из которой §4 сделала правило.

Таймаут пишет HandedOff, а не Failed: вызов БЫЛ сделан, мы перестали
ждать, эффект мог произойти. Failed заявлял бы знание в другую сторону.

Вторая половина 0.3 — оба слоя staleness НАЗВАНЫ на resumed(): слой
наблюдений движка адресован фазе 2 (§12.8, тот же механизм, что
перепланирование, и вход для него — PlanningRequest.priorObservations),
слой снимка источника (роль HOME, находка (b)) назван ИСКЛЮЧЁННЫМ со
своим адресом. Критерий 3 это прямо разрешает.

Мутации: M7a (снять арку таймаута) -> RED; M7b (бюджет в MAX_VALUE) ->
RED только на первом тесте, второй зелёный — этим и доказано, что второй
есть проверка на невакуумность.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: the sort minimum — the literal `"app"` goes, and `required` is read

**Spec:** §3 0.4, second half. **F6 stays in force** — confirmation with an appointed review date
(phase 3, §7.8), not a re-opening. `ToolDescriptor` is **not** touched (17 production files in four
modules read it); `ArgType` is **not** touched.

**Read P11 before starting.** `ToolMatchPlannerTest`'s fixtures are narrower than they look, three of
its tests bind through the literal this task removes, and four helper names that would be convenient
do not exist.

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt:71-153`
- Modify: `data/repository/src/test/java/…/agent/ToolMatchPlannerTest.kt` (three existing tests at
  `:243`, `:267`, `:283`, plus the `appToolDescriptor` helper at `:224`)
- Create: `data/repository/src/test/java/…/agent/ToolArgumentSortGuardTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  data class AppArgumentBinding(val arg: String, val labelArg: String?)
  class ToolArgumentSorts internal constructor(private val rows: Map<ToolId, AppArgumentBinding>) {
      @Inject constructor() : this(ROWS)
      fun appBindingFor(id: ToolId): AppArgumentBinding?
      fun rows(): Map<ToolId, AppArgumentBinding>
  }
  ```
- Consumed by: `ToolMatchPlanner`, whose constructor gains a **third** parameter. It has an `@Inject`
  constructor and `AgentProvidesModule.providePlanner` takes it as a parameter, so Hilt supplies the
  new dependency with no module edit — **verify that, do not assume it.**

**The two-constructor shape is not decoration.** A production map in a `private companion object`
cannot be varied by a test, and this task's tests need a row for the synthetic `test_app_tool`.
The precedent is `RoomAgentSessionStore` — **with one difference, said rather than blurred**: there
the primary constructor is **public** (`:36`) and the `@Inject` one is the secondary (`:42-46`); here
the primary is `internal`, so no production caller outside `:data:repository` can hand the planner
rows of its own. Dagger (kapt, in this module) looks only at the `@Inject`-annotated constructor and
ignores the other; an `internal` constructor is public in bytecode, so nothing about it can confuse
the processor. That last sentence is reasoning, not a build — step 6's green run is its proof.

- [ ] **Step 1: write the port and its rows**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.domain.tool.ToolId
import javax.inject.Inject

/**
 * Which argument of a tool carries an app, and where the words the user actually typed should go.
 *
 * **There is no `ArgumentSort` enum here, and its absence is the decision.** `ToolMatchPlanner`
 * carried `const val APP_ARG = "app"` with the KDoc "*the argument declares that its value is an
 * app, resolve it for me*" — which is a definition of a sort implemented by comparing strings. What
 * phase 0 removes is the string; a one-value enum would be a taxonomy with one member and no
 * dispatch, which is the shape §4 forbids building ahead of a requirement that can only be guessed.
 * The sort **system** — a resolver registered per sort — is phase 3, where `B10` is a consumer whose
 * requirement can be NAMED (spec §7.8), and `F6` is closed there by ADR rather than silently. Until
 * then the sort this catalog speaks of is in the method's name.
 *
 * [labelArg] is `null` for a tool that declares no companion: `open_app_info` has none, which is why
 * its step line renders the resolved package rather than the typed word (A1″ residual (10) — a
 * legibility limit, named and deliberately not softened).
 */
data class AppArgumentBinding(val arg: String, val labelArg: String?)

/**
 * The one **production** statement of which tool arguments carry an app — the [ToolPermissionCatalog]
 * shape, for the same reason: a hand-written column in a test is as green when it is wrong as when
 * it is right.
 *
 * **A missing row is not "resolve it anyway".** An id with no row means nobody has declared a sort,
 * and the planner then resolves nothing and passes the value through as the vocabulary produced it.
 * That is the conservative direction: resolving an undeclared argument would be the guess
 * `AppTargetResolver` exists to refuse.
 *
 * **Two constructors on purpose.** The `@Inject` one is production and takes no arguments, so Dagger
 * sees exactly what it saw before; the `internal` one lets a test state its own rows for a synthetic
 * tool without that tool appearing in production data. `RoomAgentSessionStore`'s shape, except that
 * its primary constructor is public and this one is not.
 *
 * It lives in `:data:repository` because `AppTargetResolver` — the only thing that can answer this
 * sort today — lives here, and because `:domain` is `commonMain`.
 */
class ToolArgumentSorts internal constructor(
    private val rows: Map<ToolId, AppArgumentBinding>,
) {
    @Inject constructor() : this(ROWS)

    fun appBindingFor(id: ToolId): AppArgumentBinding? = rows[id]

    fun rows(): Map<ToolId, AppArgumentBinding> = rows

    private companion object {
        val ROWS: Map<ToolId, AppArgumentBinding> = mapOf(
            // A1″ phase 3a, Task 6b / fork F5: resolved ABOVE the consent gate, so the CONFIRM card
            // names the package that will actually be removed rather than the words typed.
            Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "app", labelArg = "app_label"),
            // A1″ phase 3b, Task 4. No companion label argument — see AppArgumentBinding's KDoc.
            Tier0ToolIds.OPEN_APP_INFO to AppArgumentBinding(arg = "app", labelArg = null),
            // A1″ phase 3a, Task 9. The agent changes its OWN memory, on explicit command.
            MemoryToolIds.SET_APP_ALIAS to AppArgumentBinding(arg = "app", labelArg = "app_label"),
        )
    }
}
```

- [ ] **Step 2: widen the existing test helper, then write the two new tests**

`appToolDescriptor(vararg argNames)` (`:224`) hardcodes `required = name == "app"`. Widen it — a
modification of an existing helper, not a new one:

```kotlin
    private fun appToolDescriptor(vararg argNames: String, optional: Set<String> = emptySet()) =
        ToolDescriptor(
            id = appTool,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            argSchema = argNames.map { name ->
                ActionArg(name, required = name == "app" && name !in optional, description = "The app to act on")
            },
            risk = ActionRiskLevel.CONFIRM,
            durability = ToolDurability.TRANSIENT,
        )
```

Add a sorts fixture beside `appSelector` (`:205`), using the `internal` constructor:

```kotlin
    /** The synthetic tool's own sort row — production data stays free of `test_app_tool`. */
    private val appSorts = ToolArgumentSorts(
        mapOf(appTool to AppArgumentBinding(arg = "app", labelArg = "app_label")),
    )
```

**`appSelector` cannot reach R14-37, and the first version of this step's test depended on it
reaching it** (§0.6 finding 5). Its entry declares `argName = "app"`, and `ToolVocabulary` refuses a
bare trigger for such an entry (`ToolVocabulary.kt:191`), so `free("удали приложение")` declined in
the **selector**, before the resolution block — the test would have been red on correct code, and
M8c would have "proved" nothing through it. R14-37 is the vocabulary and the tool **disagreeing**
about `app`: the trigger supplies none, the descriptor declares it optional (the planner's own
comment, `ToolMatchPlanner.kt:86-89`, names this as what the required-argument check is for). So add
a second selector, beside `appSelector`, whose trigger supplies nothing:

```kotlin
    /**
     * The shape R14-37 needs and [appSelector] cannot produce: a trigger that supplies NO `app`
     * (`argName = null`, so it matches only as the whole text). [appSelector] declares
     * `argName = "app"`, and `ToolVocabulary` refuses a bare trigger for such an entry — a goal
     * through it declines in the selector, before the resolution block the R14-37 test is about.
     * Measured 2026-09-25 on a copy of the tree: `appSelector.select("удали приложение")` is `null`;
     * this selector's `select("покажи приложение")` is `ToolMatch(test_app_tool, {})`, and the
     * pre-A4′ planner then returned `NoPlan` from `resolve("")` — R14-37 itself.
     */
    private val bareAppSelector = ToolSelector(
        ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = appTool,
                    prefixByLocale = mapOf("ru" to setOf("покажи приложение")),
                ),
            ),
        ),
        namesOf(),
    )
```

Every **other** goal in these tests still goes through `appSelector`, whose only trigger is
`"удали приложение …"` with something after it (`ru`) — P11. A goal with any other wording declines
for an unrelated reason.

```kotlin
    /**
     * **R14-37, closed for the shape that produced it.** The old planner keyed on the argument being
     * NAMED `app`, never on whether it was `required`: a descriptor declaring an OPTIONAL `app`
     * entered the branch anyway, `match.args["app"]` was missing, `raw` became `""`,
     * `resolve("")` returned `null` — the resolver's considered refusal — and the elvis returned
     * `NoPlan` for EVERY goal, forever, with the whole suite green. A tool registered, reachable by
     * its trigger, and dead.
     *
     * [bareAppSelector], not [appSelector]: this needs a trigger that supplies no `app` at all, and
     * [appSelector]'s cannot match without one.
     *
     * Two per-descriptor pins were shipped against the opposite direction (`uninstall_app` R14-35,
     * `open_app_info` phase 3b) and neither generalised. This is the generalisation.
     */
    @Test
    fun `an optional app argument the vocabulary did not supply does not kill the plan`() = runTest {
        val descriptor = appToolDescriptor("app", optional = setOf("app"))
        val planner = ToolMatchPlanner(bareAppSelector, appTargetsOf(), appSorts)

        val result = planner.plan(PlanningRequest(free("покажи приложение")), registryWith(descriptor))

        assertTrue("an absent OPTIONAL argument is not a reason to decline", result is PlanningResult.Planned)
        assertEquals(
            "absent means absent — not an empty string, not a guessed package",
            emptyMap<String, ArgSource>(),
            (result as PlanningResult.Planned).plan.steps.single().invocation.args,
        )
    }

    /**
     * The literal is gone: an argument named `app` on a tool with **no row** is passed through
     * untouched rather than resolved. Resolution is now something a tool DECLARES, not something its
     * spelling earns.
     */
    @Test
    fun `an app argument with no declared sort is not resolved`() = runTest {
        val planner = ToolMatchPlanner(
            appSelector,
            appTargetsOf("Telegram" to "org.telegram.messenger"),
            ToolArgumentSorts(),
        )

        val result = planner.plan(
            PlanningRequest(free("удали приложение telegram")),
            registryWith(appToolDescriptor("app")),
        )

        val step = (result as PlanningResult.Planned).plan.steps.single()
        assertEquals(ArgSource.Literal("telegram"), step.invocation.args["app"])
    }
```

**Note the `PlanningRequest(...)` wrapper** — Task 3 changed the port, and `free(text)` (`:67`)
returns an `AgentGoal`.

- [ ] **Step 3: write the catalog guard**

```kotlin
/**
 * **The bridge between the spelling convention and the declaration that replaces it**, and the only
 * thing that keeps the migration from being a silent downgrade.
 *
 * Before A4' phase 0 the string `"app"` WAS the contract. A row-based catalog is only safer than a
 * literal if the rows are total over the tools the literal used to catch — otherwise a tool that
 * used to be resolved silently stops being, which is the same class of failure as R14-37 with the
 * sign flipped.
 */
class ToolArgumentSortGuardTest {

    @Test
    fun `every production tool declaring an app argument has a sort row`() {
        val offenders = productionAuthoredDescriptors()
            .filter { d -> d.argSchema.any { it.name == "app" } }
            .filter { ToolArgumentSorts().appBindingFor(it.id) == null }
            .map { it.id.value }

        assertEquals(
            "a tool that declares an `app` argument and no sort row silently stops being resolved — " +
                "the R14-37 failure mode with the sign flipped",
            emptyList<String>(),
            offenders,
        )
    }

    @Test
    fun `no sort row names an argument its tool does not declare`() {
        val descriptors = productionAuthoredDescriptors().associateBy { it.id }
        val orphans = ToolArgumentSorts().rows().mapNotNull { (id, binding) ->
            val names = descriptors[id]?.argSchema?.map { it.name } ?: return@mapNotNull "${id.value}: no such tool"
            when {
                binding.arg !in names -> "${id.value}: arg `${binding.arg}` is not declared"
                binding.labelArg != null && binding.labelArg !in names ->
                    "${id.value}: labelArg `${binding.labelArg}` is not declared"
                else -> null
            }
        }

        assertEquals(emptyList<String>(), orphans)
    }
}
```
`productionAuthoredDescriptors()` is a private helper in **this** file. Build the three authored
sources the way `Tier0IntentToolSourceTest` builds its one (`:54`) and extend it with
`SystemIntentToolSource` and `MemoryToolSource` — that file builds **only** `Tier0IntentToolSource`,
so it is a template for constructor arguments, not a complete list. The form that compiled and ran
green on the copy of the tree (2026-09-25):

```kotlin
    private fun productionAuthoredDescriptors(): List<ToolDescriptor> =
        Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }).all() +
            SystemIntentToolSource(DefaultActionCatalog()).all() +
            MemoryToolSource().all()
```
with `import com.sidr.launcher.data.repository.action.DefaultActionCatalog` and
`import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource`; `PermissionPresence { true }`
is `Tier0IntentToolSourceTest`'s `grantsEverything` (`:40`) inlined.

- [ ] **Step 4: rewrite the planner's resolution block**

```kotlin
        // An app name becomes a package HERE — above the consent checkpoint, see the class KDoc.
        //
        // **What decides is the tool's DECLARED sort, not the argument's spelling** (A4' phase 0,
        // spec §3 0.4). `const val APP_ARG = "app"` was a definition of a sort implemented by
        // comparing strings, and R14-37 is the failure mode that produced: it keyed on the NAME and
        // never on `required`, so a descriptor declaring an OPTIONAL `app` returned `NoPlan` for
        // every goal, forever, with a green suite. Both halves are fixed here — the sort is
        // declared, and an absent optional value is a value that was not needed.
        val binding = sorts.appBindingFor(descriptor.id)
        val appArg = binding?.arg?.let { name -> descriptor.argSchema.firstOrNull { it.name == name } }

        val resolvedArgs = buildMap<String, String> {
            putAll(match.args)
            if (binding != null && appArg != null) {
                val raw = match.args[binding.arg].orEmpty()
                if (raw.isBlank() && !appArg.required) {
                    // Declared, optional, and the vocabulary supplied nothing. There is nothing to
                    // resolve and nothing to decline: an absent optional argument is absent.
                    remove(binding.arg)
                } else {
                    val target = appTargets.resolve(raw) ?: return PlanningResult.NoPlan
                    put(binding.arg, target)
                    binding.labelArg
                        ?.takeIf { label -> descriptor.argSchema.any { it.name == label } }
                        ?.let { put(it, raw) }
                }
            }
        }
```
Delete the `APP_ARG` / `APP_LABEL_ARG` companion block and add `private val sorts: ToolArgumentSorts`
to the constructor. **Rewrite the two KDoc paragraphs that describe the old convention** — the ones
beginning "An argument named `app` is resolved to a package HERE" and "`app` / `app_label` is a
string convention, not a type" — so they describe the catalog. A KDoc still claiming a spelling
convention after the spelling stopped deciding is exactly the "evidence that does not describe the
thing it is believed to describe" this block is named for.

- [ ] **Step 5: adapt all NINE construction sites — they WILL break, and which sorts each gets is the decision**

A third constructor parameter breaks every `ToolMatchPlanner(` in the tree, and there are nine, in
five files (P11) — not the three tests the first version of this step named:

| Site | Gets | Why |
|---|---|---|
| `ToolMatchPlannerTest:244`, `:272`, `:276`, `:284` (the three tests at `:243`, `:267`, `:283`) | `appSorts` | they plan `test_app_tool`, which has no production row |
| `ToolMatchPlannerTest:54` (the class-level `planner`) | `ToolArgumentSorts()` | it plans the production vocabulary's tools |
| `SelectionDeclineMeasurement:164`, `Tier0ToolExecutionEndToEndTest:151`, `FreeTextGoalEndToEndTest:143`, `AgentActingSeamTest:224` | `ToolArgumentSorts()` | production-shaped wirings: the production rows are exactly what they must exercise — `AgentActingSeamTest`'s uninstall tests depend on `uninstall_app` being resolved |

**No assertion at any of the nine changes** — if one has to, stop: this task must not change
behaviour for a tool that declares its sort, and an assertion that must move is a finding for the
log. Measured 2026-09-25 on a copy of the tree with this task applied from this plan's own code
blocks (under the pre-Task-3 `plan(goal, registry)` signature): `:data:repository` **380** green,
376 + the four new tests, no existing assertion touched; `kaptDebugKotlin` accepted the `internal`
primary + `@Inject` secondary constructor.

- [ ] **Step 6: run, expect green**

```bash
tools/gate.sh --scoped :data:repository:testDebugUnitTest
```

- [ ] **Step 7: three mutations, each on a named assert**

```bash
MUT="$(mktemp -d -t sidr-mut-XXXXXX)"
T=data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt
M=data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt

# M8a — delete a row: totality must bite
cp "$T" "$MUT/m8a"
sed -i '/Tier0ToolIds.OPEN_APP_INFO to AppArgumentBinding/d' "$T"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m8a" "$T"

# M8b — misname a row's argument: the orphan check must bite
cp "$T" "$MUT/m8b"
sed -i 's/Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "app"/Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "package"/' "$T"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m8b" "$T"

# M8c — stop reading `required`: R14-37 must come back
cp "$M" "$MUT/m8c"
sed -i 's/if (raw.isBlank() \&\& !appArg.required) {/if (false) {/' "$M"
tools/gate.sh --scoped :data:repository:testDebugUnitTest
cp "$MUT/m8c" "$M"
```

**`ToolArgumentSorts.kt` is a file this task CREATED, so `git checkout` could never have undone M8a
or M8b**: on an untracked file it fails with exit 1 and leaves the mutation in place. Under the
first version of this step M8a's deleted row would have survived into M8b and M8c, both would have
gone red because of it, and both would have been logged as proved (§0.6 finding 2).

`FAILED ::` sets — **measured**, not predicted: the three runs above were executed on 2026-09-25 on a
copy of the tree carrying this task, with exactly these commands.

| Mutation | Failing tests | Count |
|---|---|---:|
| M8a | `ToolArgumentSortGuardTest` > `every production tool declaring an app argument has a sort row` | 1 |
| M8b | `ToolArgumentSortGuardTest` > `no sort row names an argument its tool does not declare`; and `AgentActingSeamTest` > `a typed uninstall command stops at AwaitingConsent with the resolved package already bound` / `consent granted - the uninstall dispatches the resolved package and the trace records the order` / `our own package is refused by the worker - after a consent card has already named it` / `R14-39 - an unresolvable target makes a matched tool command fall through to the cloud model` | 5 |
| M8c | `ToolMatchPlannerTest` > `an optional app argument the vocabulary did not supply does not kill the plan` | 1 |

M8b's four extra reds are **expected, not a regression**: a row whose `arg` names nothing makes the
planner resolve nothing for `uninstall_app`, and those four tests are the behaviour that resolution
carries. What M8b proves is that the orphan guard is **among** them — the guard catches the
misnamed row at the catalog, before anything downstream has to.

- [ ] **Step 8: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```
**Recount before the run:** step 2 adds two `@Test`s, step 3 adds two, and step 5 adds none (it
adapts nine construction sites). That is **+4**, not +5 (measured on the copy: 376 → 380) — and the phase total below is computed with +4. If your own
count differs, reconcile it **before** running, not after.
Expected: `GATE GREEN`, `TOTAL tests=1461`, `:data:repository` 384.

```bash
git add data/repository/src
git commit -m "$(cat <<'EOF'
refactor(agentic-6/A4' ф0): сорт объявляется инструментом, а не угадывается по написанию

Спека §3 0.4, вторая половина. В ToolMatchPlanner стоял
`const val APP_ARG = "app"` с KDoc «аргумент объявляет, что его значение
— приложение, разреши его за меня»: ЭТО ОПРЕДЕЛЕНИЕ СОРТА, РЕАЛИЗОВАННОЕ
СРАВНЕНИЕМ СТРОК. Литерал снят; ToolArgumentSorts — одна production-карта
по образцу ToolPermissionCatalog, ряд отсутствует => резолва нет
(консервативное направление: резолвить необъявленное значило бы сделать
догадку, от которой AppTargetResolver отказывается). Enum сорта НЕ
заводится: таксономия из одного члена без диспетчеризации — форма,
которую §4 запрещает строить вперёд угадываемого требования.

И ЧИТАЕТСЯ `required`, иначе закрыт литерал, а не режим отказа R14-37:
дескриптор с НЕОБЯЗАТЕЛЬНЫМ `app` входил в ветку по имени, raw становился
"", resolve("") -> null, и инструмент давал NoPlan НА ВСЕ ЦЕЛИ НАВСЕГДА
при зелёном прогоне. Два per-descriptor пина (R14-35, фаза 3b) не
обобщались — это обобщение.

Два конструктора не украшение: production-карта в private companion не
варьируется тестом, а тестам нужен ряд для синтетического test_app_tool.
Форма RoomAgentSessionStore, но первичный здесь internal, а там публичный;
Dagger видит только @Inject-конструктор без аргументов (kapt это принял).

R14-37 держит тест через ВТОРОЙ селектор с голым триггером: appSelector
объявляет argName = "app", а словарь такой записи голый триггер не
отдаёт, так что через него цель отказывалась в селекторе, до блока
разрешения. Девять площадок конструктора ToolMatchPlanner, а не три:
синтетические — appSorts, производственные — ToolArgumentSorts().

ToolDescriptor НЕ ТРОНУТ (17 production-файлов в четырёх модулях),
ArgType НЕ ТРОНУТ, F6 ОСТАЁТСЯ В СИЛЕ — подтверждение с назначенным
сроком пересмотра (фаза 3, §7.8), а не переоткрытие.

Мутации по протоколу cp (ToolArgumentSorts.kt создан этой задачей —
git checkout его не откатил бы вовсе): M8a (удалить ряд) -> RED, один
тест; M8b (переименовать аргумент ряда) -> RED, гвард сирот плюс четыре
теста удаления в AgentActingSeamTest — ожидаемо; M8c (перестать читать
required) -> RED, один тест.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: `versionName` from the git hash, visible in Settings

**Spec:** §4 proposal 8, slot **phase 0** — «нужно до приёмки». The block has one acceptance, at the
end, and an acceptance that cannot name the build it ran on is an acceptance of an unnamed build.

**Files:**
- Modify: `app/build.gradle.kts:19-31` (`defaultConfig`)
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/device/AppBuildInfo.kt`
- Create: `core/android/src/main/java/com/sidr/launcher/core/android/device/AndroidAppBuildInfo.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/DeviceProfileProvidesModule.kt`
- Modify: `feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsUiState.kt`,
  `SettingsViewModel.kt` (the state block at `:58-68`), `SettingsScreen.kt` (System section, `:270-272`)
- Modify: `feature/settings/src/main/res/values/strings.xml` + `values-ru` + `values-tr`
- Test: `feature/settings/src/test/java/com/sidr/launcher/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- Produces: `interface AppBuildInfo { val versionName: String }` in `:domain`, implemented by
  `AndroidAppBuildInfo` in `:core:android`; `SettingsUiState.appVersion: String`.

**`:core:android` has NO dependency injection** (Global Constraints, verified): no Hilt plugin, no
kapt, no dagger, no `javax.inject`. `AndroidDeviceProfiler` is a **plain class** assembled by hand in
`DeviceProfileProvidesModule`'s `@Provides`. That is the precedent and it must be followed literally
— annotating the new class would not compile.

- [ ] **Step 1: derive the version name from git**

In `app/build.gradle.kts`, above `android { }`:

```kotlin
/**
 * The build's identity, so an acceptance can name what it ran on (spec §4 proposal 8).
 *
 * Falls back to `unknown` rather than failing the build: a source archive with no `.git`, or a
 * machine with no `git` on `PATH`, must still assemble. `-dirty` is not cosmetic — it is the
 * difference between "the owner accepted commit X" and "the owner accepted something near X".
 *
 * `runCatching`, not `.orElse("unknown")`: a non-zero `git` exit is an EXCEPTION thrown by `.get()`
 * ("finished with non-zero exit value 128"), not an absent value, so `orElse` never sees it —
 * measured on Gradle 9.5.0 outside a git checkout, where the `orElse` form failed the build and
 * this one printed `unknown`. `ifEmpty` covers the other way to get nothing: a `git` that exits 0
 * and prints nothing.
 */
val gitVersionSuffix: String = runCatching {
    providers.exec {
        commandLine("git", "describe", "--always", "--dirty", "--abbrev=7")
    }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
}.getOrDefault("unknown")
```
and in `defaultConfig`: `versionName = "0.1.0+$gitVersionSuffix"`.

**The first version of this step used `.map { it.trim() }.orElse("unknown").get()` and claimed the
same fallback in its KDoc — and step 2, which runs inside a git checkout, could never have shown
that the claim was false** (§0.6 finding 4). Both forms were run on 2026-09-25 in a throwaway
Gradle 9.5.0 project outside any git repository (plain Gradle, no AGP): the `orElse` form failed
with `Process 'command 'git'' finished with non-zero exit value 128`, exit 1; the form above printed
`unknown`, exit 0. **Not run:** the form above inside AGP 9.3.1's `app/build.gradle.kts`. Step 2
covers the happy path there; that AGP does not change how a configuration-time `providers.exec`
fails is reasoning, not a measurement.

- [ ] **Step 2: verify the value actually reaches the manifest**

```bash
cd /home/Suleiman/Sidr-launcher
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:assembleDebug --rerun-tasks
find app/build/intermediates -name AndroidManifest.xml -path "*debug*" \
  -exec grep -ho 'versionName="[^"]*"' {} + | sort -u
```
Expected: `versionName="0.1.0+<7 hex>"`, or `…+<7 hex>-dirty` on a dirty tree. **Record the exact
string.**

- [ ] **Step 3: the port and its implementation — no annotations in `:core:android`**

```kotlin
package com.sidr.launcher.domain.device

/**
 * What build this is. A port because `:domain` is `commonMain` and a version string is the
 * platform's to answer — the `DeviceProfileProvider` shape.
 *
 * It exists for one procedural reason: an acceptance that cannot name the build it ran on is an
 * acceptance of an unnamed build. The 2026-09-20 round was conducted partly in the belief that a
 * provider was configured when it was not; naming the artefact is the cheapest half of not
 * repeating that.
 */
interface AppBuildInfo {
    val versionName: String
}
```

```kotlin
package com.sidr.launcher.core.android.device

import android.content.Context
import com.sidr.launcher.domain.device.AppBuildInfo

/**
 * Reads what is **installed**, not what was compiled into this module — which is the question an
 * acceptance asks. `PackageManager` access lives in `:core:android` by the contract table.
 *
 * **A plain class with no annotations**, exactly like [AndroidDeviceProfiler] beside it: this module
 * has no Hilt plugin, no kapt and no `javax.inject` on its classpath, and it is assembled by hand in
 * `:app`'s `DeviceProfileProvidesModule`.
 */
class AndroidAppBuildInfo(private val context: Context) : AppBuildInfo {
    override val versionName: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"
}
```

In `DeviceProfileProvidesModule`, following its existing `@Provides` style:

```kotlin
    @Provides
    @Singleton
    fun provideAppBuildInfo(@ApplicationContext context: Context): AppBuildInfo =
        AndroidAppBuildInfo(context = context)
```

- [ ] **Step 4: write the failing ViewModel test**

```kotlin
    @Test
    fun `settings state carries the installed build's version`() = runTest {
        val vm = buildViewModel(buildInfo = object : AppBuildInfo { override val versionName = "0.1.0+ab12cd3" })

        assertEquals("0.1.0+ab12cd3", vm.uiState.value.appVersion)
    }
```
Read `SettingsViewModelTest`'s existing `buildViewModel` helper and add the parameter the way its
neighbours are added. Fixture note: `"0.1.0+ab12cd3"` shares no substring with any other expected
value in that file — check.

- [ ] **Step 5: run, expect red; then wire it**

`SettingsUiState`:
```kotlin
    /** The installed build, `0.1.0+<git short sha>[-dirty]` — so an acceptance can name what it ran on. */
    val appVersion: String = "",
```
`SettingsViewModel`: inject `AppBuildInfo`, add `appVersion = buildInfo.versionName` to the state it
builds at `:58-68`.

`SettingsScreen`, in the System section beside the default-launcher row. **`SidrText` and
`SidrTextRole` are NOT imported in that file** — its existing use at `:274-276` is fully qualified,
so this one must be too:

```kotlin
            com.sidr.launcher.core.ui.primitive.SidrText(
                text = sidrString(R.string.settings_app_version_title, uiState.appVersion),
                role = com.sidr.launcher.core.ui.primitive.SidrTextRole.PROVENANCE,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
```
Strings (ordinary `strings.xml`, all three locales — Class C, signature untouched):
```xml
<!-- values -->    <string name="settings_app_version_title">Build %1$s</string>
<!-- values-ru --> <string name="settings_app_version_title">Сборка %1$s</string>
<!-- values-tr --> <string name="settings_app_version_title">Sürüm %1$s</string>
```

**Hypothesis, not a claim:** the placement in the System section and the rendering above have not
been seen on a device. Both go on the acceptance checklist in that tone.

- [ ] **Step 6: gate, then commit**

```bash
tools/gate.sh
```
Expected: `GATE GREEN`, `TOTAL tests=1462`, the other-eight-modules line 316.

```bash
git add app/build.gradle.kts domain/src core/android/src feature/settings/src app/src/main/java/com/sidr/launcher/di/
git commit -m "$(cat <<'EOF'
feat(agentic-6/A4' ф0): versionName из git-хэша, видимый в Настройках

Спека §4 предложение 8, слот — фаза 0, «нужно до приёмки». У блока одна
приёмка, в конце, и приёмка, которая не может назвать сборку, на которой
шла, есть приёмка безымянной сборки. Раунд 2026-09-20 владелец вёл,
считая провайдера настроенным.

versionName = "0.1.0+<git describe --always --dirty --abbrev=7>";
падение назад на `unknown`, чтобы дерево без .git собиралось. Через
runCatching, а не .orElse(): ненулевой код git — исключение из .get(),
а не отсутствующее значение, и orElse его не видит (замерено на Gradle
9.5.0 вне git: форма с orElse валит сборку, эта печатает unknown).
`-dirty` не косметика: это разница между «владелец принял коммит X» и
«владелец принял что-то около X».

Порт AppBuildInfo в :domain + AndroidAppBuildInfo в :core:android, форма
DeviceProfileProvider — feature/settings не видит BuildConfig из :app, а
доступ к PackageManager по таблице контрактов принадлежит core/android.
Читается УСТАНОВЛЕННОЕ, а не вкомпилированное: это и есть тот вопрос,
который задаёт приёмка.

Класс БЕЗ аннотаций и собирается руками в @Provides: у :core:android нет
ни Hilt-плагина, ни kapt, ни dagger, ни javax.inject — прецедент
AndroidDeviceProfiler соблюдён буквально, а не по названию.

РАЗМЕЩЕНИЕ И РЕНДЕРИНГ НИКТО НЕ ВИДЕЛ — гипотеза до устройства, несётся
в чеклист приёмки в этом тоне.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: close the phase

**Files:**
- Modify: `docs/governing/sidr-doctrine-matrix-v1.0.md` (the `DOC-ILM-4` row, line 125)
- Modify: this plan's status header
- Modify: `docs/superpowers/plans/2026-08-18-agentic-track-restart.md` — `§HANDOFF`

- [ ] **Step 1: the boundary gate, from a genuinely cleared tree**

```bash
cd /home/Suleiman/Sidr-launcher
git status --porcelain      # must be clean: a mutation left behind is the R14-44 disease
tools/gate.sh
```

**Predicted decomposition, written BEFORE the run** (§9.3 — a sum cannot tell "two added" from "two
added and two lost"):

| Module | Baseline 1440 | Δ | Predicted |
|---|---:|---:|---:|
| `:domain` (`jvmTest`) | 440 | +1 (T3) +1 (T4) +3 (T7) | **445** |
| `:consumer:jvm` | 56 | +1 (T4) | **57** |
| `:data:repository` | 376 | +1 (T4) +3 (T5) +4 (T8) | **384** |
| `:feature:launcher` | 194 | +5 (T6) | **199** |
| `:app` | 59 | +1 (T1) +1 (T6) | **61** |
| the other eight | 315 | +1 (T9, `:feature:settings`) | **316** |
| **TOTAL** | **1440** | **+22** | **1462** |

Per-task chain: 1441 · 1441 · 1442 · 1445 · 1448 · 1454 · 1457 · 1461 · 1462 · 1462.
A mismatch is a finding to run down before anything is committed, in either direction.

- [ ] **Step 2: `:app:assembleRelease`, because Task 9 touched `defaultConfig`**

```bash
./gradlew -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:assembleRelease --rerun-tasks
```
Expected: `BUILD SUCCESSFUL`, and `checkOwnerReviewedLocaleStrings` green — this phase adds **four
keys in two modules** (`launcher_agent_step_state_observed`, `…_handed_off`, `…_waiting`,
`settings_app_version_title`), that is **twelve entries across three locales**, and **none of them is
Class B**, so no signature is re-signed. If that task goes red, a key landed in a `strings_locked.xml`
by mistake.

- [ ] **Step 3: correct the `DOC-ILM-4` row — it now holds more than it did**

Its current cell names `AgentSessionPresentationTest` for "a skipped step is not an executed one, a
failed one is not an executed one". Append, in the same voice as the rest of the table:

> …и с A4′ фазы 0 — **наблюдение не есть исполнение** (`Observed` имел ветку `!= null -> DONE` и
> рисовался «выполнено» для приложения, которого нет — самая частая форма A0, предшествующая всему
> блоку A1″), **шаг, на котором движок остановился, не есть идущий шаг** (`CURRENT` читался как
> `index == cursor` без единой ссылки на `state`, поэтому шаг под кнопкой «Продолжить» рисовался
> «выполняется»), и **план, чей акт передан наружу, не называет себя целым** — последнее держит
> `AgentSessionSurfaceHandedOffTest`, потому что `everyStepExecuted()` имеет единственную
> production-площадку и та внутри `@Composable`, а юнит-тест над предикатами её не касается.
> Различимость слов восьми состояний над четырьмя маркерами держит
> `StepStateWordDistinctnessGuardTest` над ресурсами трёх локалей.

Do **not** add a test name that does not exist: `DoctrineMatrixGuardTest` fails the build on a false
claim, which is the point of the table.

- [ ] **Step 4: run the doctrine guards specifically**

```bash
tools/gate.sh --scoped :app:testDebugUnitTest
```
Expected green, `DoctrineMatrixGuardTest` included.

- [ ] **Step 5: write `§HANDOFF` for the phase-1 session**

Keep it to what is true:
- phase 0 is `CODE-GREEN` at gate **1462**; **no device round** (the block has one acceptance, at the
  end — §0 decision 1);
- the five owner forks F0-1…F0-5 and their answers;
- **the two open owner items phase 1 needs**: the §2 doctrine line (change-control §5 «добавить
  правило», *not* a third amendment of `DOC-ADL-3`), and confirmation that `usageHistoryEnabled` was
  turned on **with the date** — phase 1's acceptance is measured against usage recorded *after* that
  date, and usage recorded before it does not exist;
- **the rendering hypotheses this phase shipped**: four new keys × three locales that nobody has seen
  on a screen, plus the Settings placement — all of it goes to the acceptance checklist in the
  hypothesis tone; **and one deliberate change to an owner-accepted rendering**: a paused plan's
  cursor step no longer reads «in progress» / «выполняется» but «waiting for you» / «ждёт вас» — the
  2026-08-22 byte-identity baseline in `AgentSessionSurfaceProvenanceTest` moved by one word (D2),
  its subject did not;
- **what phase 0 did NOT close, named rather than implied absent**: staleness layer 1 (→ phase 2,
  with re-planning and `PlanningRequest.priorObservations`), staleness layer 2 (→ excluded, the
  `ShortcutRefreshTrigger` address), 0.2(2)'s inert `DURABLE_EFFECT` (→ phase 2, §6.1), the
  `DOC-ILM-3` fidelity gap where a persisted `Failed` still loses its `CommandFailure` variant
  (untouched), and A1″ residual (8) — `DoctrineGuardTest`'s `String.contains` blindness — which this
  phase did not go near;
- **the restatement of entry 6's trigger** (§3 0.2(3)): it arrived, in a form the trigger did not
  predict — the vocabulary gap surfaced through a one-step tool reporting a false success, not
  through a multi-step plan failing in public. That sentence belongs in the block's ADR at close;
- **the plan-review record** (§0.5, §0.6): sixteen defects found in this plan by the first review,
  three blocking, and fifteen more by the second — the worst two **in the evidence machinery**
  (`tools/gate.sh` counting other modules' leftovers; mutations undone with `git checkout`), each
  found only by running it. Worth carrying because the phase-1 plan will be written by the same
  hand, and because the rule it bought — *the tooling that produces evidence is proved by making it
  lie on purpose* — applies to every plan after this one.

- [ ] **Step 6: propose the phase commit to the owner**

```bash
git add docs/
git commit -m "$(cat <<'EOF'
docs(agentic-6/A4' ф0): фаза 0 закрыта — CODE-GREEN на 1462

Пять пунктов спеки §3 (0.0…0.4), из них 0.4 обеими половинами, плюс
предложение 8 §4 и tools/gate.sh. Устройства НЕ было и быть не должно:
у блока одна приёмка, в конце (решение владельца 1, §0).

Матрица: строка DOC-ILM-4 расширена тремя свойствами, которые она теперь
действительно держит, и каждое названо своим тестом. Имён
несуществующих тестов не добавлено — DoctrineMatrixGuardTest валит
сборку на ложном утверждении, в этом и смысл таблицы.

Чего фаза 0 НЕ закрыла, названо в §HANDOFF: слой 1 staleness (фаза 2),
слой 2 (исключён, свой адрес), инертный DURABLE_EFFECT (фаза 2, §6.1),
потеря варианта CommandFailure при восстановлении Failed, остаток A1″ (8).

Четыре новых ключа в трёх локалях никто не видел на экране — все
предсказания о видимом идут в чеклист приёмки гипотезами.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

Then **stop and propose to the owner** — the agent commits, the agent never pushes.

---

## Self-review against the spec

Run before the first task, and again before Task 10.

| Spec item | Task | Complete? |
|---|---|---|
| 0.0 `PlanningResult` exhaustive at both consumers | 2 | yes — both addresses, compile-proved |
| 0.1(a) `intent_match` — turn the flag on | Owner action §0.4 | yes — **no code exists to write**; the toggle is `SettingsScreen.kt:233-238` |
| 0.1(b) execution-model measurement | — | already taken 2026-09-21; nothing to build |
| 0.2(1) honest outcome for `uninstall_app` | 4, 5 | yes — F0-1 |
| 0.2(1б) `Observed` drawn as «выполнено» | 6 | yes — D1 |
| 0.2(1в) consent-blocked step drawn as «выполняется» | 6 | yes — D2, plus the third instance P4 |
| 0.2(2) `DURABLE_EFFECT` inert for `CONFIRM+` | — | **phase 2**, §6.1, reason in §0.3 |
| 0.2(3) entry 6's trigger restated | 10 (`§HANDOFF`) | as a block-close ADR sentence, carried so the close does not rediscover it |
| **0.3 wall-clock budget** | 7 | yes — F0-2 |
| **0.3 staleness, both layers** | 7 step 9 | yes — F0-3: one addressed, one **named excluded** |
| **0.4 first half — `PlanningRequest`** | **3** | **yes — F0-5. The first draft dropped this and marked 0.4 done; §0.5 finding 1** |
| 0.4 second half — the literal `"app"`, and `required` | 8 | yes |
| §4 proposal 4+9 `tools/gate.sh` | 1 | yes — F0-4 |
| §4 proposal 8 `versionName` | 9 | yes |

**Type consistency:** `ToolResult.HandedOff` (Task 4) is used by Tasks 5, 6 and 7 under exactly that
spelling; `AgentStepState.HANDED_OFF`/`OBSERVED`/`WAITING` are presentation-layer values and never
domain ones; `anyStepHandedOff()` is defined in Task 6 and used there and in Task 10's matrix text;
`RuntimeBudget.maxStepWallClockMs` is spelled identically in Task 7's code, tests and mutations;
`PlanningRequest`/`priorObservations` are spelled identically in Tasks 3, 7 (KDoc), 8 (test call
sites) and 10; `ToolArgumentSorts.appBindingFor` / `AppArgumentBinding` are used under those names in
Task 8's planner, fixtures and both guards.

**Placeholders:** every code step carries its code, except two places that deliberately carry an
instruction instead — Task 6 step 8's surface wiring and Task 1 step 1's
`productionAuthoredDescriptors()`. In both the instruction is *"read the neighbouring test and reuse
its construction"*, which after §0.5 finding 3 is stricter than inventing a fixture shape, not
looser — three of the first review's sixteen findings were invented helpers. Two former placeholders
became code after the second review, and it matters which way: Task 4 step 1's "copy the nearest
round-trip test" pointed at a decode-only test (§0.6 finding 8), and Task 8 step 3's helper is now the
form that compiled and ran on a copy of the tree. An instruction to copy a neighbour is only as good
as the neighbour it names.
