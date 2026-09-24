# A4′ Phase 0 — engine preconditions · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans` to implement this plan task by task. Steps use
> checkbox (`- [ ]`) syntax for tracking.

> **Status: written and reviewed 2026-09-22; corrected on the review's findings and committed
> 2026-09-25. Not started.** Branch `launcher--7`, base `074e4ce`, gate baseline **1440 / 0 / 0**.
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
  `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks`
  (verified 2026-09-22: that JDK path exists; `local.properties` carries no `java` line, so the `-P`
  flag is **required** — the machine's default JDK is 25 and Gradle cannot parse it).
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
  bought by this plan's own review (§0.5, finding 2).
- **Predict the per-module decomposition BEFORE each run and compare** (§9.3). A total cannot tell
  "two added" from "two added and two lost".
- **Every new guard is proved by a mutation on a named assert**, not by a green run. **The last
  action of any mutation-proving task is a gate re-run from a cleared tree.**
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
session that decodes as corrupt. **No Room migration** — `observation_kind` is a string column, so
schema 4 is unchanged; what a *new* value costs is forward compatibility only (an older build reading
a newer row throws `CorruptAgentRowException`, which is the existing, correct behaviour).

**(P4) The surface's lie is wider than D1/D2 — there is a third instance, and no document names it.**
`AgentSessionPresentation.stateOf` (`:417-423`) reads `step.index == cursor -> CURRENT` **without a
single reference to `state`**. §3 0.2(1в) names `Paused` / `AwaitingConsent`. The same line also
renders the cursor step of a **terminal** session as «выполняется»: a `Failed` session has already
advanced its cursor past the failing step (`AgentExecutor.perform` writes `cursor = invoked.index + 1`
before `ended(Failed)`), so the next step reads `CURRENT` on a session that will never run again.
Task 6 closes all three with one rule, not three branches.

**(P5) Two existing tests pin the lie, and the plan must say which assertions change.**
- `AgentSessionPresentationTest:145-150` — `a plan whose every step ran is whole` asserts
  `AgentStepState.DONE` for `notInstalled`, an `Observed`. **Becomes `OBSERVED`**, and
  `everyStepExecuted()` stays `true` (the step did run).
- `AgentSessionPresentationTest:121-125` — `the step at the cursor is current…` builds a session with
  `state = ExecutionState.AwaitingConsent` and asserts `CURRENT`. **Becomes `WAITING`**, and its
  first assertion (step 0 is `DONE` for an `Observed`) **becomes `OBSERVED`**.

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

---

## Task 1: `tools/gate.sh` — the gate as a machine instead of a memory

**Spec:** §4 proposal 4+9 (slot «сейчас, в окно»), §9.2. **Owner fork F0-4.**

**Files:**
- Create: `tools/gate.sh`
- Create: `app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt`

**Interfaces:**
- Produces: `tools/gate.sh [<gradle task>…]`, exit 0 only when every parsed JUnit XML reports zero
  failures and zero errors; prints a per-module decomposition, a total, and the authored registry
  census. Every later task's verification step calls it.

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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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
#   * `--rerun-tasks` is always passed, `--rerun` never is       (R9: a false green inside A1′)
#   * counts come from the JUnit XML, never from the console     (2026-07-13: `tail` hid a red gate)
#
# It does NOT replace the other four: predicting the decomposition before the run, proving a new
# guard by mutation, fixture substrings, and re-running from a cleared tree after a mutation are
# judgements, and a script that pretended to make them would be the eighth instance of "evidence
# that does not describe the thing it is believed to describe".
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

JDK17="${SIDR_JDK17:-/home/Suleiman/jdks/jdk-17.0.19+10}"
[ -d "$JDK17" ] || { echo "FATAL: JDK 17 not found at $JDK17 (set SIDR_JDK17)" >&2; exit 2; }

GRADLE_TASKS=(:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test)
[ "$#" -gt 0 ] && GRADLE_TASKS=("$@")

echo "== clearing build/test-results (R14-44) =="
find . -path ./build -prune -o -type d -name test-results -print0 2>/dev/null \
  | xargs -0 -r rm -rf

LOG="$(mktemp -t sidr-gate-XXXXXX.log)"
echo "== log: $LOG =="
echo "== running: ${GRADLE_TASKS[*]} --rerun-tasks =="
# `set +e` around BOTH long-running steps. Without it `set -e` aborts the script the moment the
# counter exits non-zero — which is exactly the run this script exists to report — and the RED
# summary, the census line and the log path are never printed.
set +e
./gradlew --no-daemon "-Porg.gradle.java.installations.paths=$JDK17" \
  "${GRADLE_TASKS[@]}" --rerun-tasks > "$LOG" 2>&1
GRADLE_EXIT=$?
set -e
# Never piped through `tail`: the whole log is kept and the exit code is the exit code.
grep -E "actionable tasks:" "$LOG" || echo "WARNING: no 'actionable tasks' line — was anything executed?"

echo "== counts, from the JUnit XML =="
set +e
python3 - "$REPO_ROOT" <<'PY'
import sys, glob, os
from xml.etree import ElementTree as ET

root = sys.argv[1]
per_module = {}
for path in glob.glob(os.path.join(root, "**", "build", "test-results", "**", "*.xml"),
                      recursive=True):
    rel = os.path.relpath(path, root)
    module = rel.split(os.sep + "build" + os.sep)[0]
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

total = [0, 0, 0, 0]
for module in sorted(per_module):
    t, f, e, s = per_module[module]
    total = [a + b for a, b in zip(total, [t, f, e, s])]
    print(f"{module:<28} tests={t:<5} failures={f:<3} errors={e:<3} skipped={s}")
print("-" * 64)
print(f"{'TOTAL':<28} tests={total[0]:<5} failures={total[1]:<3} errors={total[2]:<3} "
      f"skipped={total[3]}")
print("BASELINE at 2026-09-22 (074e4ce): tests=1440 failures=0 errors=0")
sys.exit(1 if (total[1] or total[2]) else 0)
PY
COUNT_EXIT=$?
set -e

echo "== registry census, from the federation =="
grep -rho "REGISTRY :: .*" app/build/test-results/ 2>/dev/null | head -1 \
  || echo "REGISTRY :: not in this run (task set did not include :app tests)"

echo "== log kept at: $LOG =="
if [ "$GRADLE_EXIT" -ne 0 ] || [ "$COUNT_EXIT" -ne 0 ]; then
  echo "GATE RED (gradle=$GRADLE_EXIT counts=$COUNT_EXIT)"
  exit 1
fi
echo "GATE GREEN"
```

- [ ] **Step 4: make it executable and run the full gate through it**

```bash
chmod +x tools/gate.sh
tools/gate.sh
```

Expected: `GATE GREEN`, `TOTAL tests=1441` (1440 + the census measurement), `failures=0 errors=0`,
an `N actionable tasks: N executed` line, and a `REGISTRY :: authored=…` line.

**Predicted decomposition before this run** (compare it, do not skip it): `:domain` 440 ·
`:consumer:jvm` 56 · `:data:repository` 376 · `:feature:launcher` 194 · **`:app` 60** (59 + 1) ·
the other eight modules 315. **Total 1441.**

If the script reports a different module breakdown from those six lines, the script is wrong before
the tree is: check its module derivation (`rel.split("/build/")`) against the real paths — `:domain`
is KMP and writes under `domain/build/test-results/jvmTest/` — before believing any number it prints.

- [ ] **Step 5: prove the script can go red — both halves of the summary**

```bash
# plant a failure in a cheap module, run, expect the RED summary, revert
sed -i 's/    fun `print the authored registry census`() {/    fun `print the authored registry census`() {\n        org.junit.Assert.fail("planted")/' \
  app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt
tools/gate.sh :app:testDebugUnitTest; echo "exit=$?"
```
Expected, and **all four things must appear**: the per-module table, `GATE RED (gradle=1 counts=1)`,
the `== log kept at: …` line, and `exit=1`. A run that exits 1 while printing no `GATE RED` is the
`set -e` defect this step exists to catch (§0.5 finding 5).

```bash
git checkout app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt
tools/gate.sh :app:testDebugUnitTest; echo "exit=$?"   # expect: GATE GREEN, exit=0
```

Record both exit codes and the presence of the `GATE RED` line in the task log.

- [ ] **Step 6: commit**

```bash
git add tools/gate.sh app/src/test/java/com/sidr/launcher/agent/RegistryCensusMeasurement.kt
git commit -m "$(cat <<'EOF'
build(agentic-6/A4' ф0): tools/gate.sh — гейт машиной, а не памятью

Спека §9.2, предложение 4+9 §4, форк владельца F0-4.
Скрипт заменяет три правила §9.1, каждое оплаченное конкретным ложным
зелёным: чистит build/test-results (R14-44), всегда --rerun-tasks и
никогда --rerun (R9), числа из JUnit XML и никогда через tail.
Остальные четыре правила — суждения, и скрипт их не изображает.

Обе долгие команды обёрнуты в set +e: без этого set -e убивал бы скрипт
ровно на том прогоне, ради которого он написан, и сводка GATE RED,
перепись и путь к логу не печатались бы никогда.

RegistryCensusMeasurement — замер, не гвард (прецедент
SelectionDeclineMeasurement): печатает перепись АВТОРСКИХ инструментов
одной машинной строкой, чтобы убрать из прозы пять расходящихся чисел.
Динамический ярус вне устройства не считается, и строка это говорит о
себе сама. Единственный ассерт сверяет набор id с ToolPermissionCatalog
(20 рядов над тем же авторским набором) — перепись, собранная руками,
зелена и когда забыла адаптер; сверка с независимым production-
утверждением краснеет вместо того, чтобы напечатать 18.

Красный путь пройден: посаженный fail -> GATE RED + exit=1, откат -> GREEN.

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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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
  `AgentActingSeamTest`, `SelectionDeclineMeasurement`)
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
action router) and must not be touched — the grep above excludes it, and the exclusion is the point:
two ports in this repo spell a method `plan`. **Write the site list into the task log before editing.**

- [ ] **Step 2: write the failing test**

In `CompositePlannerTest`:

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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*CompositePlannerTest*' --rerun-tasks 2>&1 | grep -E "^e: " | head
```
Expected: `Unresolved reference 'PlanningRequest'`.

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
sed -i 's/when (val result = planner.plan(request, registry)) {/when (val result = planner.plan(PlanningRequest(request.goal), registry)) {/' \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt
tools/gate.sh :domain:jvmTest   # expect RED on `every planner is asked with the same request instance`
git checkout domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt
# the checkout also reverts Task 2's exhaustive `when` in this file — re-apply it and confirm with
# `git diff` before moving on
```
Note that this mutation compiles and preserves every existing behaviour — that is why it is the right
one: it is the mistake a future editor would actually make.

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

Read `AgentSessionMappersTest.kt` first — its helpers are `sessionRow(...)` (`:45`) and
`sessionWith(events: List<TraceEvent>)` (`:199`), **neither of which is a "session with an
observation" builder**. Copy the shape of its nearest existing `Observed` round-trip test and follow
whatever construction that test uses. Then add beside it:

```kotlin
    /**
     * A0.5's "a whole class of reality unsayable" coming due (A1″ acceptance finding (a)):
     * `uninstall_app` raises the OS dialog and the caller learns nothing about what the user then
     * did. `Effected` claimed the removal happened; `Failed` claims a technical failure that did
     * not occur; `Observed` needs a frozen `ObservedFact` that has no value for this. The fourth
     * value says exactly what is true — the act left the launcher and the outcome is not ours to
     * see — and it must survive the disk, or the trace goes on lying one save later.
     */
    @Test
    fun `a HandedOff observation survives a round trip with its output`() {
        // …build a session whose step 0 observation is
        //   ToolResult.HandedOff(ToolOutput(mapOf("dispatched_to" to "os-uninstaller")))
        // exactly the way the neighbouring Observed round-trip test builds its session, then map
        // out and back through the same entry points that test uses.

        val observation = restored.observations[0]
        assertTrue("a HandedOff must not come back as some other kind", observation is ToolResult.HandedOff)
        assertEquals(
            mapOf("dispatched_to" to "os-uninstaller"),
            (observation as ToolResult.HandedOff).output.values,
        )
    }
```

Fixture note (R14-43): `"os-uninstaller"` shares no substring with any other expected value in that
file; verify before committing to it.

- [ ] **Step 2: run it and watch it fail to COMPILE, not to assert**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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

- [ ] **Step 4: take ALL EIGHT compile errors — production AND test source sets**

The first draft of this plan ran only the three production compile tasks and expected four errors.
Four more live in test sources (P3, sites 5–8) and no `compileKotlin` task reaches them:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:compileKotlinJvm :domain:compileTestKotlinJvm \
  :data:repository:compileDebugKotlin :data:repository:compileDebugUnitTestKotlin \
  :consumer:jvm:compileKotlin :consumer:jvm:compileTestKotlin 2>&1 \
  | grep -E "^e: " | tee /tmp/handedoff-errors.txt
wc -l /tmp/handedoff-errors.txt
```
Expected: **eight** `'when' expression must be exhaustive` errors, at the eight addresses of P3
(rows 1–8). **Record all of them.** Fewer than eight means a site was already non-exhaustive — a
finding to write down. More than eight means the plan's census of this type is incomplete, which is
also a finding, and a more interesting one.

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

`JvmAgentSessionStoreTest.kt` — **its `store` is a function `store()` (`:45`) and its session builder
is `session(observations = mapOf(0 to …))` (`:49-52`); use those, not a `sessionWith`:**

```kotlin
    @Test
    fun `a HandedOff observation survives this consumer's disk too`() {
        val store = store()
        val saved = session(observations = mapOf(0 to ToolResult.HandedOff(ToolOutput(mapOf("ticket" to "sandbox-42")))))

        store.save(saved)

        val observation = (store.load(saved.id) as OperationResult.Success).value?.observations?.get(0)
        assertTrue(observation is ToolResult.HandedOff)
        assertEquals(mapOf("ticket" to "sandbox-42"), (observation as ToolResult.HandedOff).output.values)
    }
```
Adapt the save/load entry points to whatever that file already calls; the assertions are the point.

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
tools/gate.sh :domain:jvmTest :data:repository:testDebugUnitTest :consumer:jvm:test
```
Expected: `GATE GREEN`. `:domain` 442, `:data:repository` 377, `:consumer:jvm` 57.

- [ ] **Step 10: mutation — prove each decode branch is load-bearing**

```bash
# M4a — Android decode returns the wrong kind
sed -i 's/OBSERVATION_HANDED_OFF -> ToolResult.HandedOff(readOutput(row))/OBSERVATION_HANDED_OFF -> ToolResult.Effected(readOutput(row))/' \
  data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt
tools/gate.sh :data:repository:testDebugUnitTest   # expect RED on
                                                   # "a HandedOff must not come back as some other kind"
git checkout data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AgentSessionMappers.kt
# the checkout reverts steps 5 and 7 in this file — re-apply them and confirm with `git diff`

# M4b — JVM decode drops the output
sed -i 's/"HandedOff" -> ToolResult.HandedOff(ToolOutput(dto.output))/"HandedOff" -> ToolResult.HandedOff(ToolOutput(emptyMap()))/' \
  consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt
tools/gate.sh :consumer:jvm:test                   # expect RED on the output assertEquals
git checkout consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt
# re-apply steps 5 and 7 in this file too
```

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

Room-миграции нет: observation_kind — строковая колонка, схема 4 цела.

Мутации: M4a (Android-декод отдаёт не тот сорт) -> RED, M4b (JVM-декод
теряет output) -> RED.

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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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
sed -i 's/is ToolResult.Effected -> ToolResult.HandedOff()/is ToolResult.Failed -> ToolResult.HandedOff()/' \
  data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt
tools/gate.sh :data:repository:testDebugUnitTest
git checkout data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt
```
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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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

- [ ] **Step 6: correct the two tests that pinned the lie (P5), and read the third before touching it**

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
Resolve `partialTitle` / `completedTitle` from the real resources the way the neighbouring test
resolves its expected sentence — `launcher_agent_completed_partial_title` and
`launcher_agent_completed_title`. Fixture note: those two strings must not share a substring, or
`assertDoesNotExist` cannot distinguish them; **check them before relying on the assertion**, and if
they do share one, assert on the `Partial` body string instead and say so in the log.

- [ ] **Step 9: run, expect all green**

```bash
tools/gate.sh :feature:launcher:testDebugUnitTest :app:testDebugUnitTest
```

- [ ] **Step 10: four mutations, each on a named assert**

```bash
# M6a — restore the catch-all that made Observed a success
sed -i 's/    is ToolResult.Observed -> AgentStepState.OBSERVED/    is ToolResult.Observed -> AgentStepState.DONE/' \
  feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt
tools/gate.sh :feature:launcher:testDebugUnitTest   # expect RED on `an observation is not an execution`
git checkout feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt

# M6b — stop consulting the session state
sed -i 's/            state == ExecutionState.Running -> AgentStepState.CURRENT/            true -> AgentStepState.CURRENT/' \
  feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt
tools/gate.sh :feature:launcher:testDebugUnitTest   # expect RED on `a step the engine stopped on…`
                                                    # AND on `a terminal session has nothing in progress`
git checkout feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt

# M6c — let a handed-off plan call itself whole (the mutation the first draft could not catch)
sed -i 's/session.everyStepExecuted() \&\& !session.anyStepHandedOff()/session.everyStepExecuted()/' \
  feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt
tools/gate.sh :feature:launcher:testDebugUnitTest   # expect RED on
                                                    # `a completed plan with a handed-off step is shown as partial…`
git checkout feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionSurface.kt

# M6d — collide two words in one locale
sed -i 's|<string name="launcher_agent_step_state_waiting">ждёт вас</string>|<string name="launcher_agent_step_state_waiting">без изменений</string>|' \
  feature/launcher/src/main/res/values-ru/strings.xml
tools/gate.sh :app:testDebugUnitTest                # expect RED on
                                                    # `every step state reads differently in every locale`
git checkout feature/launcher/src/main/res/values-ru/strings.xml
```

**M6c is the one that matters most** — it is the mutation the first draft of this plan would have let
pass. If it stays green, the Robolectric test of step 8 is not reaching the line, and that must be
fixed before this task closes rather than recorded as a limitation.

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

Мутации: M6a -> RED, M6b -> RED (два теста), M6c -> RED (та самая, что
в первой редакции плана осталась бы зелёной), M6d -> RED.

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
tools/gate.sh :domain:jvmTest
```

- [ ] **Step 6: two mutations**

```bash
# M7a — delete the timeout arm BY HAND (a sed across a multi-line catch arm is its own bug), so
#       TimeoutCancellationException falls through to the rethrow below. Same defect as writing the
#       two catches in the wrong order (P6): the timeout leaves as parent cancellation and kills the
#       session instead of bounding the step.
git diff domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt   # eyeball it
tools/gate.sh :domain:jvmTest   # expect RED on
                                # `a tool that never returns is cut at the wall-clock budget…`
git checkout domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt
# re-apply step 4 and confirm with `git diff` before moving on

# M7b — raise the budget so nothing is ever cut
sed -i 's/val maxStepWallClockMs: Long = 10_000,/val maxStepWallClockMs: Long = Long.MAX_VALUE,/' \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt
tools/gate.sh :domain:jvmTest   # expect RED on the FIRST test only — the second must stay green,
                                # which is what proves the second is a non-vacuity check
git checkout domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentSession.kt
```
If M7b reddens **both** tests, the second test is not measuring what it claims and must be fixed
before this task closes.

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
cannot be varied by a test, and this task's tests need a row for the synthetic `test_app_tool`. The
`internal` primary plus `@Inject` secondary is `RoomAgentSessionStore`'s own pattern
(`RoomAgentSessionStore.kt:46`), and it keeps Dagger looking at a no-argument constructor.

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
 * tool without that tool appearing in production data. Same shape as `RoomAgentSessionStore`.
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

**Every goal in these tests must go through `appSelector`, whose only trigger is `"удали приложение"`
(`ru`)** — P11. A goal with any other wording declines for an unrelated reason.

```kotlin
    /**
     * **R14-37, closed for the shape that produced it.** The old planner keyed on the argument being
     * NAMED `app`, never on whether it was `required`: a descriptor declaring an OPTIONAL `app`
     * entered the branch anyway, `match.args["app"]` was missing, `raw` became `""`,
     * `resolve("")` returned `null` — the resolver's considered refusal — and the elvis returned
     * `NoPlan` for EVERY goal, forever, with the whole suite green. A tool registered, reachable by
     * its trigger, and dead.
     *
     * Two per-descriptor pins were shipped against the opposite direction (`uninstall_app` R14-35,
     * `open_app_info` phase 3b) and neither generalised. This is the generalisation.
     */
    @Test
    fun `an optional app argument the vocabulary did not supply does not kill the plan`() = runTest {
        val descriptor = appToolDescriptor("app", optional = setOf("app"))
        val planner = ToolMatchPlanner(appSelector, appTargetsOf(), appSorts)

        val result = planner.plan(PlanningRequest(free("удали приложение")), registryWith(descriptor))

        assertTrue("an absent OPTIONAL argument is not a reason to decline", result is PlanningResult.Planned)
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
so it is a template for constructor arguments, not a complete list.

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

- [ ] **Step 5: adapt the three existing tests — they WILL break, and that is expected**

`:243`, `:267` and `:283` all construct `ToolMatchPlanner(appSelector, appTargetsOf(...))` against
`test_app_tool`, which has no production row. Each gains `appSorts` as its third argument. **No
assertion in any of the three changes** — if one has to, stop: this task must not change behaviour
for a tool that declares its sort, and an assertion that must move is a finding for the log.

- [ ] **Step 6: run, expect green**

```bash
tools/gate.sh :data:repository:testDebugUnitTest
```

- [ ] **Step 7: three mutations, each on a named assert**

```bash
# M8a — delete a row: totality must bite
sed -i '/Tier0ToolIds.OPEN_APP_INFO to AppArgumentBinding/d' \
  data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt
tools/gate.sh :data:repository:testDebugUnitTest  # expect RED on
                                                  # `every production tool declaring an app argument has a sort row`
git checkout data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt

# M8b — misname a row's argument: the orphan check must bite
sed -i 's/Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "app"/Tier0ToolIds.UNINSTALL_APP to AppArgumentBinding(arg = "package"/' \
  data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt
tools/gate.sh :data:repository:testDebugUnitTest  # expect RED on `no sort row names an argument its tool does not declare`
git checkout data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolArgumentSorts.kt

# M8c — stop reading `required`: R14-37 must come back
sed -i 's/if (raw.isBlank() \&\& !appArg.required) {/if (false) {/' \
  data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt
tools/gate.sh :data:repository:testDebugUnitTest  # expect RED on
                                                  # `an optional app argument the vocabulary did not supply does not kill the plan`
git checkout data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt
```
Re-apply steps 1 and 4 after any `git checkout` that reverts them; confirm with `git diff` before
moving on.

- [ ] **Step 8: gate from a cleared tree, then commit**

```bash
tools/gate.sh
```
**Recount before the run:** step 2 adds two `@Test`s, step 3 adds two, and step 5 adds none (it
adapts three). That is **+4**, not +5 — and the phase total below is computed with +4. If your own
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
Форма RoomAgentSessionStore; Dagger по-прежнему видит конструктор без
аргументов.

ToolDescriptor НЕ ТРОНУТ (17 production-файлов в четырёх модулях),
ArgType НЕ ТРОНУТ, F6 ОСТАЁТСЯ В СИЛЕ — подтверждение с назначенным
сроком пересмотра (фаза 3, §7.8), а не переоткрытие.

Мутации: M8a (удалить ряд) -> RED, M8b (переименовать аргумент ряда) ->
RED, M8c (перестать читать required) -> RED.

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
 */
val gitVersionSuffix: String = providers.exec {
    commandLine("git", "describe", "--always", "--dirty", "--abbrev=7")
}.standardOutput.asText.map { it.trim() }.orElse("unknown").get()
```
and in `defaultConfig`: `versionName = "0.1.0+$gitVersionSuffix"`.

If `providers.exec { }` is unavailable or throws on a non-zero `git` exit in this Gradle/AGP
combination, fall back to a `ProcessBuilder` read wrapped in `runCatching { … }.getOrDefault("unknown")`
— and **say in the task log which one shipped**, because the two behave differently outside a git
checkout.

- [ ] **Step 2: verify the value actually reaches the manifest**

```bash
cd /home/Suleiman/Sidr-launcher
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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
падение назад на `unknown`, чтобы дерево без .git собиралось. `-dirty`
не косметика: это разница между «владелец принял коммит X» и «владелец
принял что-то около X».

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
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
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
tools/gate.sh :app:testDebugUnitTest
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
  hypothesis tone;
- **what phase 0 did NOT close, named rather than implied absent**: staleness layer 1 (→ phase 2,
  with re-planning and `PlanningRequest.priorObservations`), staleness layer 2 (→ excluded, the
  `ShortcutRefreshTrigger` address), 0.2(2)'s inert `DURABLE_EFFECT` (→ phase 2, §6.1), the
  `DOC-ILM-3` fidelity gap where a persisted `Failed` still loses its `CommandFailure` variant
  (untouched), and A1″ residual (8) — `DoctrineGuardTest`'s `String.contains` blindness — which this
  phase did not go near;
- **the restatement of entry 6's trigger** (§3 0.2(3)): it arrived, in a form the trigger did not
  predict — the vocabulary gap surfaced through a one-step tool reporting a false success, not
  through a multi-step plan failing in public. That sentence belongs in the block's ADR at close;
- **the plan-review record** (§0.5): sixteen defects found in this plan before its first task, three
  blocking. Worth carrying because the phase-1 plan will be written by the same hand.

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

**Placeholders:** every code step carries its code, except four places that deliberately carry an
instruction instead — Task 4 step 1's session construction, Task 6 step 8's surface wiring, Task 8
step 3's `productionAuthoredDescriptors()`, and Task 1 step 1's same helper. In all four the
instruction is *"read the neighbouring test and reuse its construction"*, which after §0.5 finding 3
is stricter than inventing a fixture shape, not looser — three of this plan's sixteen review findings
were invented helpers.
