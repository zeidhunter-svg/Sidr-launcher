# A1″ — Tool mass and selection · Implementation Plan (Phases 0–2)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** give the agent ≥15 real tools from ≥3 levels by making a tool source *dynamic*, making
selection fail closed at that size, and closing the two engine/guard holes that would otherwise be
multiplied by every new worker.

**Architecture:** the boundary A1′ built is untouched — one `ToolExecutor` implementation, one call
site, one federation object. Three things change behind it: the federation derives its two faces **per
call** instead of caching them at construction, so a source whose tool set moves is visible; a new
`app_shortcut` adapter turns `LauncherApps` shortcuts into descriptors whose display names come from
data and never enter `:domain`; and a `ToolSelector` in `:data:repository` decides between an authored
localized vocabulary and those dynamic names, declining on any ambiguity.

**Tech Stack:** Kotlin 2.4.10, Gradle 9.5.0, AGP 9.3.1, JDK 17 toolchain, Hilt, Room (schema 4,
unchanged by this plan), JUnit4 + Robolectric, `kotlinx-coroutines-test`.

**Spec:** [docs/superpowers/specs/2026-09-12-a1-second-tool-mass-and-selection-design.md](../specs/2026-09-12-a1-second-tool-mass-and-selection-design.md)

**Scope of THIS plan:** spec §14 Phases 0, 1 and 2, plus the two measurement tasks. **Phase 3
(authored Tier-0 mass, spec §7) is deliberately not planned here**: every one of its descriptors
depends on a device measurement that does not exist yet, and spec §3.1 forbids writing an Android
premise from anything but a measurement. Task 13 produces that measurement; Phase 3's plan is written
from it. This plan on its own leaves a working, shipped, testable product.

---

## Global Constraints

Copied verbatim from the spec and the repository's hard rules. Every task's requirements implicitly
include this section.

- **JDK 17.** The machine default is newer and Gradle cannot parse it. Always pass
  `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`.
- **Block gate, one command, never piped through `tail`:**
  `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks`
- **`--rerun-tasks`, NEVER `--rerun`.** `--rerun` is not a build-level flag in Gradle 9.5.0; it prints
  `BUILD SUCCESSFUL` having executed nothing. A number counts only from a run that printed
  `N actionable tasks: N executed`.
- **Read test counts from the JUnit XML** under each module's `build/test-results/`, never the console.
  Baseline to compare against: **1314 tests, 0 failures** (`1ef158d`).
- **`:domain` is `commonMain` + `jvmTest`.** Production code goes in `domain/src/commonMain/kotlin`;
  tests in `domain/src/jvmTest/kotlin`. stdlib + coroutines only — no Android, no `core/*`. Use
  `kotlin.coroutines.cancellation.CancellationException`, never the `kotlinx` one.
- **`OperationResult<T>` for repository/use-case ops; never throw to UI.**
- **User-facing text never originates in `domain` or in a ViewModel.** The feature layer picks the
  string via `sidrString(R.string.…)`. Enforced by `HardcodedUiTextGuardTest` / `StringSeamGuardTest`.
- **`en` / `ru` / `tr` ship in the same commit as the feature** (`LocaleCompletenessGuardTest`).
- **A new *translatable* (Class B) string invalidates the owner-reviewed locale signature.** Never
  compute or write the `sha256` digest yourself — say that the owner must re-review and re-sign, and
  note it in the task's report.
- **Not touched by any task in this plan:** `ActionIds` (seven frozen values),
  `OutboundContextPolicy.ALLOWED`, `GoalShape` (stays at two values), `ObservedFact`,
  `CommandFailure`, `ArgType`, the `Failed`/`Completed` divergence of A0.5 §6.3, Room schema 4.
- **Every new guard goes to a separate `mutation-prover` place.** The author's own green run proves
  nothing. Commit the legitimate change **before** mutating the file; restore with
  `trap … EXIT` + `git checkout --`, never `cp`.
- **Device work is measurement, not acceptance.** Read the agent tables on the device **only** via
  `tools/device/pull-agent-db.sh`.
- **Gradle is foreground.** Never end a turn waiting on a background build.

### Per-task verification rhythm

Run the narrow test task while iterating, then the owning module's full unit-test task before the
commit:

| Module | Test task |
|---|---|
| `:domain` | `:domain:jvmTest` |
| `:data:repository` | `:data:repository:testDebugUnitTest` |
| `:feature:launcher` | `:feature:launcher:testDebugUnitTest` |
| `:app` | `:app:testDebugUnitTest` |

The **full gate** runs at each phase boundary (after Tasks 3, 9 and 12), not after every task.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt` | Permission totality keyed on what the production federation **declares**, not on worker files |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/AppShortcut.kt` | The four fields a shortcut contributes; no Android types |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutCatalog.kt` | `LauncherApps` access, the snapshot, and the explicit refresh |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolIds.kt` | Derived `ToolId` for a shortcut, and its inverse |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolSource.kt` | `ToolRegistry` over the snapshot; also the `DynamicToolNames` implementation |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutToolWorker.kt` | `ToolWorker` that calls `startShortcut`, containing its own errors |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/DynamicToolNames.kt` | The port: id → a display name that came from data |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolSelector.kt` | Authored vocabulary + dynamic names → at most one `ToolMatch` |
| `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` | Every measured Android fact this block rests on |

**Modified**

| File | Change |
|---|---|
| `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt` | Containment at the one call site |
| `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt` | Two faces derived per call from one snapshot function |
| `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt` | One constant: `APP_SHORTCUT` |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt` | Consumes `ToolSelector` instead of `ToolVocabulary` directly |
| `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt` | A dynamic-named step renders its data name |
| `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt` | Passes `toolNames` beside the existing `toolProvenance` |
| `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` | Third adapter; `DynamicToolNames` binding |
| `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` | Risk pinning; the label rule learns about dynamic tools |
| `app/src/main/res/values*/strings.xml` | One new step string, `en`/`ru`/`tr` |

---

# Phase 0 — preconditions (spec §8)

No new tool ships until all three tasks are green and the two new guards are mutation-proved.

---

### Task 1: Contain a throwing worker in the engine

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt` (the
  `toolExecutor.invoke(resolved)` line in `perform`, currently line 200)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: the guarantee every later worker relies on — `toolExecutor.invoke` never propagates an
  exception out of `AgentExecutor.perform`.

**Why this is first:** A1′'s final review found a device with no activity for `ACTION_SET_TIMER`
produced a `SAFE` one-step plan the consent gate does not stop, and the throw **killed the home-screen
process**. It was fixed inside one worker. This plan adds a shortcut worker and (in Phase 3) up to a
dozen more; the contract "an invocation always yields a `ToolResult`" must stop being a convention.

- [ ] **Step 1: Write the failing test**

Add to `AgentExecutorTest.kt`:

```kotlin
@Test
fun `a worker that throws is observed as Failed rather than killing the caller`() = runTest {
    val throwing = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            throw IllegalStateException("no activity found to handle this intent")
    }
    val executor = AgentExecutor(toolExecutor = throwing, budget = RuntimeBudget.Default)

    val advanced = executor.advance(runningSessionWithOneStep())

    val observed = advanced.observations.getValue(0)
    assertTrue("expected a Failed observation, got $observed", observed is ToolResult.Failed)
    assertEquals(1, advanced.cursor)
    assertTrue(
        "the throw must still be recorded in the trace",
        advanced.trace.events.any { it is TraceEvent.ToolObserved && it.index == 0 },
    )
}

@Test
fun `cancellation is never converted into a Failed observation`() = runTest {
    val cancelling = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            throw CancellationException("parent scope cancelled")
    }
    val executor = AgentExecutor(toolExecutor = cancelling, budget = RuntimeBudget.Default)

    assertFailsWith<CancellationException> { executor.advance(runningSessionWithOneStep()) }
}
```

Reuse the file's existing session-building helper rather than inventing a new one; if none is named
`runningSessionWithOneStep`, rename these calls to whatever the file already uses to build a `Running`
session whose `prepare` clears step 0. Do not add a second helper for the same shape.

- [ ] **Step 2: Run it and watch it fail**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*AgentExecutorTest*' --rerun-tasks
```

Expected: the first test fails by **propagating** `IllegalStateException` out of `advance`, not by an
assertion. That is the defect in one line.

- [ ] **Step 3: Implement**

In `perform`, replace

```kotlin
        val result = toolExecutor.invoke(resolved)
```

with

```kotlin
        // The ONE call site to the world — and the floor under every worker's own containment.
        //
        // `ToolWorker`'s contract says an invocation always yields a `ToolResult`. Until this `try`
        // that was held by convention plus three implementations with three different nets: a fourth
        // adapter whose worker threw took the home-screen process down with it (A1′ final review,
        // `356fe1a` — a device with no activity for ACTION_SET_TIMER, a SAFE one-step plan the consent
        // gate does not stop, and no `try` anywhere above here).
        //
        // Catching HERE rather than in the launcher is deliberate and was re-reviewed once already:
        // `ContextIntentLauncher.launch` returns `Unit`, so a swallowed failure there is
        // indistinguishable from success and the trace would record `Effected` for an effect that never
        // happened — `DOC-ILM-3` would be lied to. At this call site the result type is already
        // `ToolResult`, so a caught throw becomes an honest `Failed`.
        //
        // `Exception`, not `Throwable`: an `Error` (OOM, stack overflow) is not a tool failure and must
        // not be reported as one. Same line `SandboxToolWorker` already draws.
        val result = try {
            toolExecutor.invoke(resolved)
        } catch (e: CancellationException) {
            throw e // never swallow parent cancellation
        } catch (e: Exception) {
            ToolResult.Failed(CommandFailure.Generic)
        }
```

Add the import `kotlin.coroutines.cancellation.CancellationException` — **not** the `kotlinx` one:
`:domain` is `commonMain` and must compile for both targets.

- [ ] **Step 4: Run the tests and watch them pass**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --rerun-tasks
```

Expected: PASS, and no other `:domain` test changes behaviour — this adds a branch, it does not alter
an existing one.

- [ ] **Step 5: Note what this does NOT close**

Add one sentence to the class KDoc of `AgentExecutor` saying the adapter-level catches stay where they
are because they produce *specific* failures, and this is only the floor. Do not claim the
`CommandFailure` variant is preserved — it is not; a contained throw is `Generic`, and that is the
same named limitation the persisted-`Failed` gap already carries.

- [ ] **Step 6: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/AgentExecutorTest.kt
git commit -m "fix(agentic-5.5/A1\"): a throwing worker is contained at the one call site, not by convention"
```

---

### Task 2: A permission guard keyed on the registry

**Files:**
- Create: `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt`
- Modify: `app/build.gradle.kts` — only if the `Test` inputs block does not already declare every path
  this guard reads (it declares the manifest today; verify before editing)
- Read, do not edit: `app/src/test/java/com/sidr/launcher/agent/ToolPermissionManifestGuardTest.kt`

**Interfaces:**
- Consumes: `DoctrineGuardTest`'s production-graph shape (a `List<ToolAdapter>` built from the real
  sources). Copy that construction rather than importing a private helper across test classes.
- Produces: a declared map `ToolId → List<String>` (permissions) that Task 7 and Phase 3 must extend
  when they register a tool.

**Why:** the existing guard's own KDoc names two measured blind spots and addresses them to "the author
of adapter #3", which is this block. It answers *"does every intent a **scanned worker** issues have a
declared permission"*. The 2026-09-05 defect was an instance of the other question — *"can every
**registered tool** actually run"* — and a shared intent-building helper, the natural thing to write
when adding ten intents, is invisible to it entirely.

The old guard is **not deleted**. Two guards, two questions; the new one's KDoc says which is which.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0ToolIds
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Permission totality keyed on **what the registry declares**, not on which files contain both a
 * `ToolWorker` and an `Intent(`.
 *
 * [ToolPermissionManifestGuardTest] answers "does every intent a scanned worker issues have a declared
 * permission". This class answers the question the 2026-09-05 defect was actually an instance of: **can
 * every registered tool run at all** — is there a row for it, and is that row's permission in the
 * manifest. Neither subsumes the other, and both are cheap.
 *
 * What this class cannot check, said rather than implied: [toolPermissions] is hand-written, so it is
 * exactly as strong as it is honest. Writing `emptyList()` for a tool that needs a permission leaves
 * every test here green and the app broken — the same weakness its sibling names about its own column.
 * What it does buy is **totality over the registry**: a tool registered by any adapter, with or
 * without a worker branch, with its intent built inline or in a helper three files away, is red until
 * someone writes its row.
 */
class ToolRegistryPermissionGuardTest {

    private val repoRoot = File("..")
    private val manifestFile = File(repoRoot, "app/src/main/AndroidManifest.xml")

    /** Registered tool → the permissions the platform requires of its caller. */
    private val toolPermissions: Map<ToolId, List<String>> = mapOf(
        ToolIds.LAUNCH_APP to emptyList(),
        ToolIds.PLAY_STORE_SEARCH to emptyList(),
        Tier0ToolIds.SET_TIMER to listOf("com.android.alarm.permission.SET_ALARM"),
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyList(),
    )

    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Failed(com.sidr.launcher.domain.intent.CommandFailure.Generic)
    }

    private fun productionFederation() = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker),
        ),
    )

    private fun declaredPermissions(): Set<String> {
        assertTrue("missing app manifest: ${manifestFile.canonicalPath}", manifestFile.isFile)
        return Regex("""<uses-permission\b[^>]*?/?>""")
            .findAll(manifestFile.readText())
            .filterNot { it.value.contains("""tools:node="remove"""") }
            .mapNotNull { Regex("""android:name\s*=\s*"([^"]+)"""").find(it.value)?.groupValues?.get(1) }
            .toSet()
    }

    @Test
    fun `every registered tool has a permission row`() {
        val registered = productionFederation().registry.all().map { it.id }
        val unrowed = registered.filterNot { it in toolPermissions }
        assertEquals(
            "A registered tool with no row here can ship needing a permission nobody declared — the " +
                "2026-09-05 defect exactly. Add a row (use emptyList() to mean 'needs none'): " +
                "${unrowed.map { it.value }}",
            emptyList<ToolId>(),
            unrowed,
        )
    }

    @Test
    fun `every permission a registered tool needs is declared in the manifest`() {
        val declared = declaredPermissions()
        val registered = productionFederation().registry.all().map { it.id }.toSet()
        val missing = toolPermissions
            .filterKeys { it in registered }
            .flatMap { (id, needed) -> needed.filterNot { it in declared }.map { id.value to it } }
        assertEquals(
            "Registered, reachable, and refused by ActivityTaskManager at every invocation: $missing",
            emptyList<Pair<String, String>>(),
            missing,
        )
    }

    @Test
    fun `the registry this guard reads is not empty`() {
        val registered = productionFederation().registry.all()
        assertTrue(
            "Every assertion here loops over the production registry. If construction ever yields an " +
                "empty list, those loops pass having checked nothing.",
            registered.size >= 4,
        )
    }

    @Test
    fun `the manifest scan reads real permissions`() {
        assertTrue(
            "The scan found no known permission — the manifest moved or the attribute spelling changed, " +
                "and the totality test above is now vacuous.",
            "android.permission.INTERNET" in declaredPermissions(),
        )
    }
}
```

- [ ] **Step 2: Run it**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*ToolRegistryPermissionGuardTest*' --rerun-tasks
```

Expected: PASS on the first run — the manifest is currently correct. A guard that is green on arrival
proves nothing, which is what Step 4 is for.

- [ ] **Step 3: Check the Gradle input declaration before trusting the result**

Open `app/build.gradle.kts` and confirm the `Test` task's `inputs` already declare
`app/src/main/AndroidManifest.xml`. It was added for the sibling guard; verify rather than assume. If a
path this guard reads is not declared, add it **in this commit**, and say so in the report: an
undeclared input means the task returns `UP-TO-DATE` with a live defect and exit 0.

- [ ] **Step 4: Commit the legitimate change, THEN hand the guard to `mutation-prover`**

```bash
git add app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt
git commit -m "test(agentic-5.5/A1\"): permission totality keyed on the registry, not on worker files"
```

Then dispatch `mutation-prover` with these four mutations, each restored by `trap … EXIT` +
`git checkout --`:

| # | Mutation | Must be |
|---|---|---|
| M1 | Delete the `SET_ALARM` line from `AndroidManifest.xml` | **RED**, naming `set_timer` |
| M2 | Delete the `Tier0ToolIds.SET_TIMER` row from `toolPermissions` | **RED**, naming the unrowed id |
| M3 | Register a third fake tool in the federation with no row | **RED** — this is the blind spot the class exists to close |
| M4 | Add a legitimate fourth row and a matching manifest entry | **GREEN** — no over-pinning |

A control the prover must also run: build the federation from an adapter list that yields zero tools
and confirm `the registry this guard reads is not empty` goes **RED** rather than the loops passing
vacuously.

---

### Task 3: Pin declared risk over the production federation

**Files:**
- Modify: `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt`

**Interfaces:**
- Consumes: the existing `productionAdapters()` / `REQUIRED_TOOL_IDS` helpers in that file.
- Produces: a declared `ToolId → ActionRiskLevel` map that Task 7 and Phase 3 extend.

**Why:** `DoctrineGuardTest` does not pin declared risk (mutation M9, A1′ task 11). Today's four tools
are covered by assertions inside their own sources' tests in `:data:repository`; a **new adapter**
arrives with no pin unless its author writes one, and this plan adds one adapter now and more later.
The assertion belongs in the guard, over the federation, not in each source's own test.

- [ ] **Step 1: Write the failing test**

Add to `DoctrineGuardTest`:

```kotlin
    /**
     * Declared risk, pinned where totality can be asserted. Each source's own test pins its own tools
     * (`Tier0IntentToolSourceTest`, the task-12 parity test) — which is why a `SAFE → CONFIRM` mutation
     * on a Tier-0 tool is caught in `:data:repository` and the whole `:app` suite stays green. That
     * arrangement covers today's four tools and **nothing a future adapter registers**.
     */
    private val declaredRisk: Map<ToolId, ActionRiskLevel> = mapOf(
        ToolIds.LAUNCH_APP to ActionRiskLevel.SAFE,
        ToolIds.PLAY_STORE_SEARCH to ActionRiskLevel.SAFE,
        Tier0ToolIds.SET_TIMER to ActionRiskLevel.SAFE,
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS to ActionRiskLevel.SAFE,
    )

    @Test
    fun `every registered tool's declared risk is pinned here`() {
        val registered = productionAdapters().flatMap { it.registry.all() }
        val unpinned = registered.map { it.id }.filterNot { it in declaredRisk }
        assertEquals(
            "A tool whose risk is pinned by nothing can change gate behaviour silently: " +
                "${unpinned.map { it.value }}",
            emptyList<ToolId>(),
            unpinned,
        )

        val drifted = registered
            .filter { declaredRisk[it.id] != null && declaredRisk[it.id] != it.risk }
            .map { "${it.id.value}: pinned ${declaredRisk[it.id]}, declared ${it.risk}" }
        assertEquals(
            "Declared risk changed without this pin changing with it: $drifted",
            emptyList<String>(),
            drifted,
        )
    }
```

Add the import `com.sidr.launcher.domain.action.ActionRiskLevel`. Read the tools from
`productionAdapters()` — **before** federation de-duplication — for the reason that file's KDoc already
gives about the id-collision assertion.

- [ ] **Step 2: Run it**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*DoctrineGuardTest*' --rerun-tasks
```

Expected: PASS.

- [ ] **Step 3: Commit, then mutation-prove**

```bash
git add app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt
git commit -m "test(agentic-5.5/A1\"): declared risk is pinned over the federation, not per source"
```

Mutations for a separate `mutation-prover` place:

| # | Mutation | Must be |
|---|---|---|
| M1 | `Tier0IntentToolSource`'s `set_timer` risk `SAFE → CONFIRM` | **RED** in `:app` — the exact M9 gap |
| M2 | Remove one row from `declaredRisk` | **RED**, naming the unpinned id |
| M3 | Add a legitimately registered fifth tool **with** its row | **GREEN** |

- [ ] **Step 4: Phase 0 boundary — run the full gate**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
```

Confirm the output contains `N actionable tasks: N executed`, then read the counts from the JUnit XML.
Report the total against **1314**.

---

# Phase 1 — the dynamic seam (spec §4, §5)

---

### Task 4: `ToolFederation` derives its two faces per call

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolFederationTest.kt`

**Interfaces:**
- Produces: the property Task 7 depends on — a source whose `all()` returns a different list later is
  visible through `registry.all()`, `registry.find()` **and** `executor.invoke()`.

**Why:** `byId`, `descriptors` and `descriptorById` are computed in the constructor and
`provideToolFederation` is a `@Singleton`, so a source whose tool set moves is read once per process
and never again. Nothing in the Master Plan, A1′'s spec or its ADR names this, because all three
shipped sources are static.

**What must not be lost:** the class KDoc argues that deriving both faces from one list is load-bearing
rather than tidy. Per-call derivation *preserves* that argument — both faces still read one function's
output — and adds "cannot be stale". Update the KDoc to say so; do not delete the paragraph.

- [ ] **Step 1: Write the failing test**

Add to `ToolFederationTest.kt`:

```kotlin
    private class MutableRegistry(var tools: List<ToolDescriptor>) : ToolRegistry {
        override fun all(): List<ToolDescriptor> = tools
        override fun find(id: ToolId): ToolDescriptor? = tools.firstOrNull { it.id == id }
    }

    @Test
    fun `a source that gains a tool after construction is visible through both faces`() = runTest {
        val source = MutableRegistry(listOf(descriptor(ToolId("first"))))
        val worker = RecordingWorker()
        val federation = ToolFederation(listOf(ToolAdapter(ToolLevel("dynamic"), source, worker)))

        assertEquals(listOf("first"), federation.registry.all().map { it.id.value })

        source.tools = source.tools + descriptor(ToolId("second"))

        assertEquals(listOf("first", "second"), federation.registry.all().map { it.id.value })
        assertNotNull(federation.registry.find(ToolId("second")))

        federation.executor.invoke(resolved(ToolId("second")))
        assertEquals(listOf(ToolId("second")), worker.invoked)
    }

    @Test
    fun `a source that loses a tool stops advertising and stops routing it`() = runTest {
        val source = MutableRegistry(listOf(descriptor(ToolId("first")), descriptor(ToolId("second"))))
        val federation = ToolFederation(listOf(ToolAdapter(ToolLevel("dynamic"), source, RecordingWorker())))

        source.tools = source.tools.filter { it.id.value != "second" }

        assertNull(federation.registry.find(ToolId("second")))
        assertTrue(federation.executor.invoke(resolved(ToolId("second"))) is ToolResult.Failed)
    }
```

Reuse the file's existing `descriptor(...)` / `resolved(...)` / worker fakes; if the names differ,
match the file rather than adding duplicates.

- [ ] **Step 2: Run it and watch it fail**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*ToolFederationTest*' --rerun-tasks
```

Expected: the first test fails at the second `assertEquals` — the federation still reports only
`first`.

- [ ] **Step 3: Implement**

Replace the three cached properties with one snapshot function that both faces call:

```kotlin
    /**
     * One derivation, read by both faces on every call.
     *
     * It was computed once in the constructor until A1″. That was correct while every source was
     * static and wrong the moment one was not: `provideToolFederation` is a `@Singleton`, so a source
     * whose tool set moves — app installed, shortcut removed — would have been read once per process.
     * Deriving per call keeps the property this class exists for (both faces read **one** list, so
     * they cannot disagree about what exists) and adds a second: neither can be stale.
     *
     * Cost: two maps rebuilt per call, over a registry of ~150 descriptors. Measured rather than
     * assumed — see this block's ADR.
     */
    private class Snapshot(
        val descriptors: List<ToolDescriptor>,
        val byId: Map<ToolId, ToolAdapter>,
    ) {
        val descriptorById: Map<ToolId, ToolDescriptor> = descriptors.associateBy { it.id }
    }

    private fun snapshot(): Snapshot {
        val byId = buildMap {
            adapters.forEach { adapter ->
                adapter.registry.all().forEach { descriptor ->
                    if (descriptor.id !in this) put(descriptor.id, adapter)
                }
            }
        }
        val descriptors = adapters.flatMap { adapter ->
            adapter.registry.all().filter { byId[it.id] === adapter }
        }
        return Snapshot(descriptors, byId)
    }

    val registry: ToolRegistry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = snapshot().descriptors
        override fun find(id: ToolId): ToolDescriptor? = snapshot().descriptorById[id]
    }

    val executor: ToolExecutor = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
            // Reachable since A1″: a dynamic source may lose a tool between planning and invocation.
            // `InvocationValidator` still rejects an unregistered tool at plan time and on resume; this
            // is the narrower window, and it fails closed rather than routing to a stale adapter.
            val adapter = snapshot().byId[invocation.id] ?: return ToolResult.Failed(CommandFailure.Generic)
            return adapter.worker.invoke(invocation)
        }
    }
```

Also update the KDoc comment inside `executor.invoke`: it currently says a dispatch miss is
"unreachable in a well-formed graph". That stops being true with a dynamic source — rewrite it to the
paragraph above rather than leaving a sentence the tree contradicts.

- [ ] **Step 4: Run `:domain:jvmTest` whole**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --rerun-tasks
```

Expected: PASS, including the pre-existing `all concatenates its sources in adapter order`,
`find reaches a tool in any source`, and the collision tests — per-call derivation must not change
collision behaviour.

- [ ] **Step 5: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt \
        domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolFederationTest.kt
git commit -m "feat(agentic-5.5/A1\"): the federation derives both faces per call, so a source may move"
```

---

### Task 5: Measure `LauncherApps` on the device — before any of Task 6 is written

**Files:**
- Create: `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`

**Interfaces:**
- Produces: the answers Tasks 6 and 7 are built against. **No line of Task 6 may be written before
  this file has them.**

**Why this is a task and not a footnote:** spec §3.1. A1′ shipped its headline tool dead because two
inherited Android sentences were both false. Nothing about `LauncherApps` may be written from
documentation or recall.

- [ ] **Step 1: Confirm a device is attached**

```bash
adb devices -l
```

Expected: the SM-A325F listed as `device`. If nothing is attached, **stop and report** — do not
proceed against an emulator and call it a measurement; the whole point is this phone.
Never disable the phone's data or Wi-Fi: the laptop is tethered through it.

- [ ] **Step 2: Measure, with Sidr NOT the default home**

Write a small instrumented probe or use an existing debug entry point to call, on the device:

```kotlin
val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
val query = LauncherApps.ShortcutQuery().setQueryFlags(
    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
)
val result = runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }
```

Record **exactly** what `result` is: a value, or which exception with which message.

- [ ] **Step 3: Repeat with Sidr set as the default home**

Set Sidr as the home app in system settings, repeat Step 2, and additionally record: how many
shortcuts come back in total; how many distinct packages contribute; whether `shortLabel` or
`longLabel` is populated; and what `startShortcut` does for one of them (does it launch, does it throw,
does it need anything `getShortcuts` did not).

- [ ] **Step 4: Write the measurement file**

Create `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` with a table whose columns are:
*what was called*, *device state*, *observed result verbatim*, *date*, *build sha*. One row per
measurement. Add a header stating that this file is the only admissible source for an Android premise
in this block, and that an unmeasured row may not be filled from documentation.

- [ ] **Step 5: Record the consequence for the design**

Add a short section answering, from the measurements only:

1. what the adapter must do when Sidr is not the default home (spec §5.3 requires: degrade to an empty
   tool set — never a crash, never a surface offering a tool it cannot run);
2. whether any permission must be added to the manifest, and therefore whether Task 2's
   `toolPermissions` map needs a row for shortcut tools;
3. roughly how many descriptors the registry will carry, which is the number Task 12 measures against.

- [ ] **Step 6: Commit**

```bash
git add docs/superpowers/plans/2026-09-12-a1-device-measurements.md
git commit -m "docs(agentic-5.5/A1\"): what LauncherApps actually does on the SM-A325F, measured"
```

---

### Task 6: `ShortcutCatalog` — snapshot and refresh

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/AppShortcut.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutCatalog.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/shortcut/ShortcutCatalogTest.kt`

**Interfaces:**
- Consumes: Task 5's measurement file.
- Produces:
  - `data class AppShortcut(val packageName: String, val shortcutId: String, val appLabel: String, val shortcutLabel: String)`
  - `class ShortcutCatalog` with `fun current(): List<AppShortcut>` and `suspend fun refresh()`

- [ ] **Step 1: Write the failing test**

```kotlin
@Test
fun `current is empty until a refresh has run`() {
    val catalog = ShortcutCatalog(query = { emptyList() }, ioDispatcher = UnconfinedTestDispatcher())
    assertEquals(emptyList<AppShortcut>(), catalog.current())
}

@Test
fun `a refresh replaces the snapshot wholesale`() = runTest {
    var backing = listOf(shortcut("com.a", "new_chat"))
    val catalog = ShortcutCatalog(query = { backing }, ioDispatcher = UnconfinedTestDispatcher())

    catalog.refresh()
    assertEquals(listOf("new_chat"), catalog.current().map { it.shortcutId })

    backing = listOf(shortcut("com.b", "new_tab"))
    catalog.refresh()
    assertEquals(listOf("new_tab"), catalog.current().map { it.shortcutId })
}

@Test
fun `a query that fails leaves an empty snapshot rather than propagating`() = runTest {
    val catalog = ShortcutCatalog(
        query = { throw SecurityException("caller is not the default launcher") },
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    catalog.refresh()

    assertEquals(emptyList<AppShortcut>(), catalog.current())
}
```

The `query` seam is a `() -> List<AppShortcut>` constructor parameter precisely so these tests need no
Robolectric and no `LauncherApps`: the Android call lives behind it.

- [ ] **Step 2: Run it and watch it fail to compile**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*ShortcutCatalogTest*' --rerun-tasks
```

Expected: compilation failure — `ShortcutCatalog` does not exist.

- [ ] **Step 3: Implement**

`AppShortcut.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent.shortcut

/**
 * One shortcut an installed app publishes, reduced to the four fields the tool layer needs.
 *
 * [appLabel] and [shortcutLabel] are **third-party copy**: they are authored by another app, in
 * whatever language that app chose, and they are neither translated nor sanitized by us. They stay in
 * `:data:repository` and reach the surface through `DynamicToolNames`, so no third-party string
 * crosses into `:domain` — see the block spec §5.2 for why `ToolDescriptor` deliberately did not gain
 * a `label` field.
 */
data class AppShortcut(
    val packageName: String,
    val shortcutId: String,
    val appLabel: String,
    val shortcutLabel: String,
)
```

`ShortcutCatalog.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent.shortcut

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The snapshot behind the `app_shortcut` adapter, and the only place that talks to `LauncherApps`.
 *
 * **Why a snapshot and not a live read.** `ToolRegistry.all()` is documented read-only and side-effect
 * free, and it is called by `InvocationValidator`, by the selector, by guards and by the presentation
 * mapper. Putting a binder call behind that contract would hide I/O behind a synchronous, pure-looking
 * signature. So the source returns a list it was *given*, and refreshing is this class's explicit,
 * suspending job.
 *
 * **Degradation is a requirement, not politeness.** [refresh] never throws: a device where Sidr is not
 * the default home yields an empty tool set, which means the registry advertises nothing it cannot
 * run. The opposite failure — advertising a tool that always fails — is what cost A1′ its first
 * acceptance run.
 */
@Singleton
class ShortcutCatalog @Inject constructor(
    private val query: ShortcutQuery,
    private val ioDispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var snapshot: List<AppShortcut> = emptyList()

    fun current(): List<AppShortcut> = snapshot

    suspend fun refresh() {
        snapshot = withContext(ioDispatcher) {
            runCatching { query.shortcuts() }.getOrElse { emptyList() }
        }
    }
}

/** The Android seam, so [ShortcutCatalog] is testable without Robolectric. */
fun interface ShortcutQuery {
    fun shortcuts(): List<AppShortcut>
}
```

Then the Android implementation, in the same package, written **against Task 5's measurements**:

```kotlin
package com.sidr.launcher.data.repository.agent.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * `LauncherApps` is available to the **default home app**. `AndroidManifest.xml` declares
 * `android.intent.category.HOME`, so Sidr can hold that role — holding it is a runtime condition the
 * user controls, and what the API does when Sidr does not hold it is recorded in
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`, measured on the SM-A325F. Do not
 * change the handling below on the strength of documentation.
 */
class AndroidShortcutQuery @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShortcutQuery {

    override fun shortcuts(): List<AppShortcut> {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        val packageManager = context.packageManager
        val request = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )

        val shortcuts = runCatching {
            launcherApps.getShortcuts(request, Process.myUserHandle()).orEmpty()
        }.getOrElse { return emptyList() }

        val labels = mutableMapOf<String, String>()
        return shortcuts.mapNotNull { info ->
            if (!info.isEnabled) return@mapNotNull null
            val label = (info.longLabel ?: info.shortLabel)?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            val appLabel = labels.getOrPut(info.`package`) {
                runCatching {
                    packageManager.getApplicationInfo(info.`package`, 0).loadLabel(packageManager).toString()
                }.getOrElse { info.`package` }
            }
            AppShortcut(
                packageName = info.`package`,
                shortcutId = info.id,
                appLabel = appLabel,
                shortcutLabel = label,
            )
        }
    }
}
```

If any of Task 5's measurements contradicts a line above — a different exception, an empty list where a
throw was expected, `longLabel` never populated — **follow the measurement and say so in the KDoc**.
The code here is the shape, the device is the authority.

- [ ] **Step 4: Run the module's tests**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/shortcut/ \
        data/repository/src/test/java/com/sidr/launcher/data/repository/agent/shortcut/
git commit -m "feat(agentic-5.5/A1\"): a shortcut snapshot that degrades to empty instead of throwing"
```

---

### Task 7: The `app_shortcut` adapter

**Files:**
- Create: `.../agent/shortcut/ShortcutToolIds.kt`, `.../agent/shortcut/ShortcutToolSource.kt`,
  `.../agent/shortcut/ShortcutToolWorker.kt`, `.../agent/DynamicToolNames.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt` (one constant)
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` (third adapter + binding)
- Modify: `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` (adapter list + risk row)
- Modify: `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt` (row for
  shortcut tools, per Task 5's answer)
- Test: `.../agent/shortcut/ShortcutToolSourceTest.kt`, `.../agent/shortcut/ShortcutToolWorkerTest.kt`

**Interfaces:**
- Consumes: `AppShortcut`, `ShortcutCatalog` (Task 6); per-call federation (Task 4).
- Produces:
  - `object ShortcutToolIds { const val PREFIX = "shortcut:"; fun of(packageName: String, shortcutId: String): ToolId; fun parse(id: ToolId): Pair<String, String>? }`
  - `class ShortcutToolSource : ToolRegistry, DynamicToolNames`
  - `class ShortcutToolWorker : ToolWorker`
  - `interface DynamicToolNames { fun all(): List<DynamicToolName> }` and
    `data class DynamicToolName(val id: ToolId, val qualifier: String, val name: String)`

- [ ] **Step 1: Write the failing tests**

```kotlin
// ShortcutToolSourceTest
@Test
fun `every shortcut becomes one descriptor with shortcut provenance`() {
    val catalog = fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message"))
    val source = ShortcutToolSource(catalog)

    val descriptor = source.all().single()

    assertEquals(ToolId("shortcut:com.a/new_chat"), descriptor.id)
    assertEquals(ToolLevels.APP_SHORTCUT, descriptor.level)
    assertEquals(ToolEffect.EXTERNAL, descriptor.effect)
    assertEquals(ActionRiskLevel.SAFE, descriptor.risk)
    assertEquals(ToolDurability.TRANSIENT, descriptor.durability)
    assertEquals(emptyList<ActionArg>(), descriptor.argSchema)
}

@Test
fun `an id survives a refresh so a persisted plan still resolves`() {
    val catalog = fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message"))
    val before = ShortcutToolSource(catalog).all().single().id
    catalog.replaceWith(shortcut("com.a", "new_chat", "Telegram", "Новое сообщение"))
    assertEquals(before, ShortcutToolSource(catalog).all().single().id)
}

@Test
fun `no shortcut id can collide with an authored id`() {
    val authored = listOf(ToolIds.LAUNCH_APP, ToolIds.PLAY_STORE_SEARCH,
        Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS)
    assertTrue(authored.none { it.value.startsWith(ShortcutToolIds.PREFIX) })
}

@Test
fun `names carry the app as qualifier and the shortcut as name`() {
    val source = ShortcutToolSource(fakeCatalogOf(shortcut("com.a", "new_chat", "Telegram", "New message")))
    assertEquals(
        listOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "New message")),
        source.all(),
    )
}

// ShortcutToolWorkerTest
@Test
fun `an unparseable id fails closed and starts nothing`() = runTest {
    val launcher = RecordingShortcutLauncher()
    val result = ShortcutToolWorker(launcher).invoke(resolved(ToolId("set_timer")))
    assertTrue(result is ToolResult.Failed)
    assertTrue(launcher.started.isEmpty())
}

@Test
fun `a launcher that throws yields Failed rather than propagating`() = runTest {
    val throwing = ShortcutLauncher { _, _ -> throw IllegalStateException("shortcut is gone") }
    val result = ShortcutToolWorker(throwing).invoke(resolved(ToolId("shortcut:com.a/new_chat")))
    assertTrue(result is ToolResult.Failed)
}
```

- [ ] **Step 2: Run and watch them fail to compile**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*Shortcut*' --rerun-tasks
```

- [ ] **Step 3: Add the level constant**

In `ToolLevel.kt`, beside the existing constants:

```kotlin
    /**
     * Tools contributed by other apps' shortcuts, read through `LauncherApps`. The third level, and the
     * first whose membership changes while the process lives — see `ToolFederation`'s snapshot KDoc.
     */
    val APP_SHORTCUT = ToolLevel("app_shortcut")
```

`ToolLevel` is an open value class over `String` (A1′ fork F4), so this costs exactly this line: no
exhaustive `when` anywhere widens.

- [ ] **Step 4: Implement ids, source and worker**

```kotlin
// ShortcutToolIds.kt
package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.domain.tool.ToolId

/**
 * A shortcut's [ToolId] is **derived in the adapter**, never hand-written — identity C, the same rule
 * `SystemIntentToolSource` follows for a projected `ActionId`. Two properties are required and both are
 * tested: the id is stable across a refresh, so a plan persisted before a restart still resolves; and
 * it cannot collide with an authored id, because no authored id contains [PREFIX]'s colon.
 */
object ShortcutToolIds {
    const val PREFIX = "shortcut:"

    fun of(packageName: String, shortcutId: String): ToolId = ToolId("$PREFIX$packageName/$shortcutId")

    fun parse(id: ToolId): Pair<String, String>? {
        if (!id.value.startsWith(PREFIX)) return null
        val rest = id.value.removePrefix(PREFIX)
        val slash = rest.indexOf('/')
        if (slash <= 0 || slash == rest.lastIndex) return null
        return rest.substring(0, slash) to rest.substring(slash + 1)
    }
}
```

```kotlin
// DynamicToolNames.kt  (package com.sidr.launcher.data.repository.agent)
/**
 * Display names that came from **data** rather than from our string resources.
 *
 * `ToolDescriptor`'s KDoc says it carries no user-facing copy, and A1″ keeps that true: a shortcut's
 * name is authored by another app, so it travels through this port — read by `ToolSelector` for
 * matching and by the launcher's ViewModel for rendering — and never enters `:domain`.
 */
data class DynamicToolName(val id: ToolId, val qualifier: String, val name: String)

interface DynamicToolNames {
    fun all(): List<DynamicToolName>
}
```

```kotlin
// ShortcutToolSource.kt
@Singleton
class ShortcutToolSource @Inject constructor(
    private val catalog: ShortcutCatalog,
) : ToolRegistry, DynamicToolNames {

    override fun all(): List<ToolDescriptor> = catalog.current().map { shortcut ->
        ToolDescriptor(
            id = ShortcutToolIds.of(shortcut.packageName, shortcut.shortcutId),
            level = ToolLevels.APP_SHORTCUT,
            effect = ToolEffect.EXTERNAL,
            // SAFE, with the reason stated rather than assumed — spec §5.4. A shortcut launch is the
            // same act the user performs by long-pressing the app icon: it happens in front of them,
            // it is what the OS itself offers, and nothing about it leaves the device. The one property
            // that does NOT transfer from the `set_timer` ruling is "we know what it does": nothing in
            // the ShortcutInfo contract forbids an app from shipping a shortcut that acts immediately.
            // That is the limit of this rating, named here rather than implied absent.
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        )
    }

    override fun find(id: ToolId): ToolDescriptor? = all().firstOrNull { it.id == id }

    override fun names(): List<DynamicToolName> = catalog.current().map { shortcut ->
        DynamicToolName(
            id = ShortcutToolIds.of(shortcut.packageName, shortcut.shortcutId),
            qualifier = shortcut.appLabel,
            name = shortcut.shortcutLabel,
        )
    }
}
```

**Why the port's method is `names()` and not `all()`:** one class implements both `ToolRegistry` and
`DynamicToolNames`, and two `all()` overrides cannot coexist. The port is therefore declared as
`fun names(): List<DynamicToolName>` in `DynamicToolNames.kt` above, and `names()` is the spelling used
everywhere in this plan — Tasks 8, 10 and 11 included. Do not resolve this by splitting the class in
two: the names and the descriptors are derived from the same snapshot, and separating them would let
one go stale against the other, which is the F2 disease the federation exists to prevent.

```kotlin
// ShortcutToolWorker.kt
class ShortcutToolWorker @Inject constructor(
    private val launcher: ShortcutLauncher,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        val parsed = ShortcutToolIds.parse(invocation.id) ?: return ToolResult.Failed(CommandFailure.Generic)
        return runCatching { launcher.start(parsed.first, parsed.second) }
            .fold(
                onSuccess = { ToolResult.Effected },
                onFailure = { ToolResult.Failed(CommandFailure.Generic) },
            )
    }
}

/** The Android seam: `LauncherApps.startShortcut`. Kept behind an interface so the worker is testable. */
fun interface ShortcutLauncher {
    fun start(packageName: String, shortcutId: String)
}
```

Check `ToolResult`'s actual success variant name in
`domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolResult.kt` and use it — do not assume
`Effected` without reading the file.

The worker keeps its own `runCatching` even though Task 1 added the engine floor: adapter-level catches
produce specific failures and the floor is a floor. Say that in the KDoc.

- [ ] **Step 5: Wire the third adapter**

`ShortcutCatalog` takes a `ShortcutQuery` and a `CoroutineDispatcher`, so the graph needs two bindings
before the adapter can be provided: `ShortcutQuery` → `AndroidShortcutQuery`, and the IO dispatcher
qualifier this module already uses for other data-layer dependencies (read the module and reuse it —
do not introduce a second dispatcher qualifier). `ShortcutLauncher` binds to a small implementation
calling `LauncherApps.startShortcut`, written the same way `AndroidShortcutQuery` is.

In `AgentProvidesModule.provideToolFederation`, add `ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutSource, shortcutWorker)`
**after** the two existing adapters — collision precedence is first-adapter-wins, so an authored tool
must never be displaceable by a third-party one. Bind `DynamicToolNames` to `ShortcutToolSource`.

Update `DoctrineGuardTest`'s `productionAdapters()` and its `the composition root registers exactly the
declared adapters` test, and add the shortcut source to `ToolRegistryPermissionGuardTest`'s map — with
whatever permission Task 5 measured, `emptyList()` if none.

- [ ] **Step 6: Trigger a refresh**

`ShortcutCatalog.refresh()` must be called when the launcher starts and when Android says shortcuts or
packages changed (`LauncherApps.registerCallback`). Put the registration in the same Android-facing
place that already owns app-list refresh; do not invent a new lifecycle owner. Nothing in this path
runs on the main thread and nothing touches the network.

- [ ] **Step 7: Run the affected modules**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```

Expected: `DoctrineGuardTest`'s `every tool in the production federation has a non-generic label on the
surface` **fails** — a shortcut tool has no string resource. That is a real finding, and Task 8 is
where it is answered. Do not weaken the test here to make this task green; commit with that test red
only if the two tasks land in one commit, otherwise fold Task 8 into this commit.

- [ ] **Step 8: Commit (together with Task 8)**

---

### Task 8: A dynamic tool's name and provenance on the surface

**Files:**
- Create: nothing
- Modify: `feature/launcher/.../agent/AgentSessionPresentation.kt`,
  `feature/launcher/.../LauncherViewModel.kt`,
  `app/src/main/res/values/strings.xml` + `values-ru/` + `values-tr/`,
  `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/LauncherScreenAgentProvenanceTest.kt`

**Interfaces:**
- Consumes: `DynamicToolNames.names()` (Task 7).
- Produces: the rendering `DoctrineGuardTest`'s label rule will assert for dynamic tools.

**Why:** `toolLabelFor(id)` maps a `ToolId` to a string resource, and a shortcut has no resource — its
name is data. `DOC-ILM-2` still applies: an `EXTERNAL` tool's provenance must reach the user, and it is
held **behaviourally**, by rendering the real surface, never by a structural scan (A1′ §6.3, where the
first draft of that check was a tautology).

- [ ] **Step 1: Write the failing test**

Add to `LauncherScreenAgentProvenanceTest` a test that renders the real `LauncherScreen` over a real
`LauncherViewModel` whose federation includes a shortcut tool, and asserts:

```kotlin
composeRule.onNodeWithText("Telegram — New message").assertExists()
composeRule.onNodeWithContentDescription("source APP SHORTCUT · EXTERNAL").assertExists()
```

Match the existing test's exact content-description format by reading it first; the strings above are
the shape, not the spelling.

- [ ] **Step 2: Run it and watch it fail**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest --tests '*AgentProvenance*' --rerun-tasks
```

- [ ] **Step 3: Implement the presentation change**

```kotlin
/**
 * One localized line per step — or, for a tool whose name is **data**, that name.
 *
 * [dynamicNames] is empty for every authored tool, so A0's and A1′'s lines are byte-for-byte what they
 * were and the rendering baseline holds. A shortcut has no string resource to map to: its name was
 * authored by another app, in that app's language, and translating it is not ours to do. What IS ours
 * is the sentence around it, which is why the resource below takes the name as an argument instead of
 * the surface concatenating one.
 */
@Composable
@ReadOnlyComposable
internal fun PlanStep.line(subject: String, dynamicNames: Map<ToolId, String>): String {
    val dynamic = dynamicNames[invocation.id]
    return if (dynamic != null) {
        sidrString(R.string.launcher_agent_step_shortcut, dynamic)
    } else {
        sidrString(toolLabelFor(invocation.id), invocation.literalSubject() ?: subject)
    }
}
```

`LauncherViewModel` builds `dynamicNames` as
`dynamicToolNames.names().associate { it.id to "${it.qualifier} — ${it.name}" }` and hands it down
beside the existing `toolProvenance`. The em-dash join is copy and therefore belongs in the resource if
it ever varies by locale; today it does not, and the KDoc says that is why it is here.

- [ ] **Step 4: Add the string in all three locales**

`values/strings.xml`: `<string name="launcher_agent_step_shortcut">Open “%1$s”</string>`
`values-ru/strings.xml`: `<string name="launcher_agent_step_shortcut">Открыть «%1$s»</string>`
`values-tr/strings.xml`: `<string name="launcher_agent_step_shortcut">“%1$s” aç</string>`

This is a **translatable (Class B) key**, so it invalidates the owner-reviewed locale signature.
**Do not compute or write the digest.** Say in the task report that the owner must re-review and
re-sign before `:app:assembleRelease` is green again, and that the block gate does not include
`assembleRelease`.

- [ ] **Step 5: Teach `DoctrineGuardTest`'s label rule about dynamic tools**

Its `every tool in the production federation has a non-generic label on the surface` test must now
read: an authored tool maps to a non-generic resource; a tool whose id starts with
`ShortcutToolIds.PREFIX` is exempt from that rule **and** must appear in `DynamicToolNames.names()`.
Write the exemption as a positive requirement, not as a filter — a dynamic tool with no name is the
same defect the original rule exists to catch, pointing the other way.

- [ ] **Step 6: Run the three affected modules**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest :app:testDebugUnitTest :data:repository:testDebugUnitTest --rerun-tasks
```

- [ ] **Step 7: Commit Tasks 7 and 8**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ \
        domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt \
        app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt \
        app/src/test app/src/main/res feature/launcher/src \
        data/repository/src/test
git commit -m "feat(agentic-5.5/A1\"): app shortcuts become tools, named by their own apps"
```

---

### Task 9: The seam a tool set that moves opens

**Files:**
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ShortcutStalenessEndToEndTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 4, 6, 7.
- Produces: nothing — this task's deliverable is the test.

**Why:** this block's most expensive lesson, now three times over: four layers each fully tested in
isolation summed to a dead capability, and every test was green. The new seam is
*dynamic registry → federation → validator → worker*, and no per-task test crosses it.

- [ ] **Step 1: Write the test — real objects, no fakes except the Android seam**

```kotlin
@Test
fun `a shortcut that disappears between planning and invocation fails rather than crashing`() = runTest {
    val catalog = ShortcutCatalog(query = { listOf(appShortcut("com.a", "new_chat")) }, ioDispatcher = dispatcher)
    catalog.refresh()
    val source = ShortcutToolSource(catalog)
    val federation = ToolFederation(
        listOf(ToolAdapter(ToolLevels.APP_SHORTCUT, source, ShortcutToolWorker { _, _ -> })),
    )

    val id = federation.registry.all().single().id
    val plan = ExecutionPlan(listOf(PlanStep(0, ToolInvocation(id, emptyMap()), ActionRiskLevel.SAFE,
        StepPrecondition.None, StepRationale.GOAL_DIRECT)))

    // The app is uninstalled while the plan sits paused.
    val emptied = ShortcutCatalog(query = { emptyList() }, ioDispatcher = dispatcher).also { it.refresh() }
    val afterUninstall = ToolFederation(
        listOf(ToolAdapter(ToolLevels.APP_SHORTCUT, ShortcutToolSource(emptied), ShortcutToolWorker { _, _ -> })),
    )

    val result = afterUninstall.executor.invoke(ResolvedInvocation(id, emptyMap()))

    assertTrue("a vanished tool must fail closed, not route to a stale adapter", result is ToolResult.Failed)
    assertNull(afterUninstall.registry.find(id))
}

@Test
fun `a worker that throws inside the real federation still yields a Failed observation`() = runTest {
    // Crosses Task 1's engine floor and Task 7's adapter catch in one run, over the real federation.
    val catalog = ShortcutCatalog(query = { listOf(appShortcut("com.a", "new_chat")) }, ioDispatcher = dispatcher)
    catalog.refresh()
    val federation = ToolFederation(
        listOf(
            ToolAdapter(
                ToolLevels.APP_SHORTCUT,
                ShortcutToolSource(catalog),
                ShortcutToolWorker { _, _ -> throw IllegalStateException("shortcut is gone") },
            ),
        ),
    )
    val id = federation.registry.all().single().id
    val executor = AgentExecutor(toolExecutor = federation.executor, budget = RuntimeBudget.Default)

    val advanced = executor.advance(runningSessionFor(id))

    assertTrue(advanced.observations.getValue(0) is ToolResult.Failed)
    assertTrue(advanced.trace.events.any { it is TraceEvent.ToolObserved && it.index == 0 })
}
```

`runningSessionFor(id)` builds a one-step `Running` session over that tool the same way the first test
builds its plan — reuse the helper the first test already needs rather than adding a second. The value
of this test is that the throw is contained **twice over** (worker `runCatching`, engine `try`) and the
assertion still reads the engine's output, so removing either containment is visible here.

- [ ] **Step 2: Run it**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*ShortcutStaleness*' --rerun-tasks
```

Expected: PASS. **If it passes on the first run without you having touched production code, say so —
that is the good outcome here**, and it is what makes the claim "the seam is crossed" a measurement
rather than a hope.

- [ ] **Step 3: Phase 1 boundary — full gate**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
```

Confirm `N actionable tasks: N executed`; read counts from XML; report against 1314.

- [ ] **Step 4: Commit**

```bash
git add data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ShortcutStalenessEndToEndTest.kt
git commit -m "test(agentic-5.5/A1\"): the seam a moving tool set opens, crossed end to end"
```

---

# Phase 2 — selection (spec §6)

---

### Task 10: `ToolSelector`

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolSelector.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolSelectorTest.kt`

**Interfaces:**
- Consumes: `ToolVocabulary.match(text): ToolMatch?`, `DynamicToolNames.names(): List<DynamicToolName>`.
- Produces: `class ToolSelector { fun select(text: String): ToolMatch? }` — Task 11 injects it into
  `ToolMatchPlanner` in place of `ToolVocabulary`.

**Why:** `ToolVocabulary.match` ends `hits.singleOrNull()`, so two entries claiming one text yields **no
match at all**. Its KDoc says the ambiguity branch is unreachable with two entries and becomes "a
question of when" at A1″'s size. A table entry shadowed by a *dynamic* name is invisible to
`ToolVocabularyReachabilityTest`, because the shortcut is not in the table.

- [ ] **Step 1: Write the failing tests — one per rule in spec §6.3**

```kotlin
@Test
fun `an authored trigger wins outright over a third-party name that claims the same text`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.x/s"), "Settings", "system settings")),
    )
    assertEquals(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, selector.select("system settings")?.id)
}

@Test
fun `a dynamic candidate that does not name its app is declined`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new message")),
    )
    assertNull(selector.select("new message"))
    assertEquals(ToolId("shortcut:com.a/new_chat"), selector.select("telegram new message")?.id)
}

@Test
fun `two dynamic candidates of equal strength decline rather than guessing`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(
            DynamicToolName(ToolId("shortcut:com.a/new"), "Telegram", "new message"),
            DynamicToolName(ToolId("shortcut:com.b/new"), "Telegram", "new message"),
        ),
    )
    assertNull(selector.select("telegram new message"))
}

@Test
fun `a partial shortcut name is not a match`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/new_chat"), "Telegram", "new saved message")),
    )
    assertNull(selector.select("telegram new message"))
}

@Test
fun `blank and empty text match nothing`() {
    val selector = ToolSelector(ToolVocabulary(), namesOf())
    assertNull(selector.select(""))
    assertNull(selector.select("   "))
}

@Test
fun `a dynamic name with a blank qualifier or blank name can never match`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.a/x"), "", "")),
    )
    assertNull(selector.select("anything at all"))
}
```

- [ ] **Step 2: Run and watch them fail to compile**

- [ ] **Step 3: Implement**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.intent.CommandNormalizer
import javax.inject.Inject

/**
 * One command, at most one tool — over a registry that is now partly **third-party and dynamic**.
 *
 * `ToolVocabulary` alone was enough for two authored tools. It is not enough here for a reason its own
 * KDoc predicted: `match` ends `singleOrNull`, so ambiguity yields nothing, and a table entry shadowed
 * by a shortcut label is invisible to `ToolVocabularyReachabilityTest` because the shortcut is not in
 * the table. The ordering below is the whole of the policy, and each rule is here because of a failure
 * mode rather than a preference (spec §6.3):
 *
 *  1. **Authored beats dynamic, outright.** Our vocabulary is deliberate and localized; an app's label
 *     is neither, and any app may ship a shortcut named "system settings".
 *  2. **A dynamic candidate must name its app.** "telegram new message" matches; bare "new message"
 *     does not, even with exactly one candidate. A shortcut launch performs an effect, so a false
 *     positive must be structurally unlikely, not statistically unlikely.
 *  3. **Equal candidates decline.** Breaking a tie would choose an effect for the user in silence. A
 *     miss is free: routing falls through to the existing chain exactly as before.
 *
 * It does not learn, rank by usage, or consult a model — those are A2/A3 and A4′'s plan cache, and a
 * selector whose behaviour depends on history is untestable by the guards this block ships.
 */
class ToolSelector @Inject constructor(
    private val vocabulary: ToolVocabulary,
    private val dynamicNames: DynamicToolNames,
) {
    fun select(text: String): ToolMatch? {
        val normalized = CommandNormalizer.normalize(text)
        if (normalized.isEmpty()) return null

        vocabulary.match(normalized)?.let { return it }

        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        val hits = dynamicNames.names().filter { it.matches(tokens) }
        return hits.singleOrNull()?.let { ToolMatch(it.id, emptyMap()) }
    }

    /**
     * Every word of the shortcut's own name must be present, **and** at least one word of the app's
     * name. Requiring all of the name rather than any of it is what keeps "telegram new message" from
     * reaching a shortcut called "new saved message": a looser rule buys recall in a place where a
     * wrong answer performs an effect.
     */
    private fun DynamicToolName.matches(tokens: Set<String>): Boolean {
        val qualifierTokens = CommandNormalizer.normalize(qualifier).split(' ').filter { it.isNotBlank() }
        val nameTokens = CommandNormalizer.normalize(name).split(' ').filter { it.isNotBlank() }
        if (qualifierTokens.isEmpty() || nameTokens.isEmpty()) return false
        return qualifierTokens.any { it in tokens } && nameTokens.all { it in tokens }
    }
}
```

Note `vocabulary.match` normalizes again internally; that is idempotent and harmless — do not remove
the normalization from either place to "optimize" it, because each has its own reason to be there.

- [ ] **Step 4: Run the module**

- [ ] **Step 5: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolSelector.kt \
        data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolSelectorTest.kt
git commit -m "feat(agentic-5.5/A1\"): selection that declines instead of guessing at registry scale"
```

---

### Task 11: The planner consumes the selector

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt`
- Modify: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolMatchPlannerTest.kt`
- Modify: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolVocabularyReachabilityTest.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` if the planner is constructed
  there rather than by `@Inject`

**Interfaces:**
- Consumes: `ToolSelector.select` (Task 10).
- Produces: the unchanged `Planner` contract — `RouteCommandUseCase` step 2b is **not touched**.

- [ ] **Step 1: Change the dependency**

In `ToolMatchPlanner`, replace the `vocabulary: ToolVocabulary` constructor parameter with
`selector: ToolSelector`, and `vocabulary.match(text)` with `selector.select(text)`. Nothing else in
that class changes: it still checks required arguments are present, still drops undeclared ones, still
produces a one-step plan with the registry's risk.

Update the class KDoc: the sentence about the vocabulary handing over a normalized remainder stays
true and still points at the free-text limitation; add that selection now also covers third-party
names and that the planner deliberately learns nothing about the distinction.

- [ ] **Step 2: Add the shadowing test to the reachability guard**

```kotlin
@Test
fun `an authored trigger cannot be shadowed by a third-party shortcut name`() {
    val selector = ToolSelector(
        vocabulary = ToolVocabulary(),
        dynamicNames = namesOf(DynamicToolName(ToolId("shortcut:com.x/s"), "Timer", "set timer for")),
    )
    ToolVocabulary().entries.forEach { entry ->
        val sample = sampleCommandFor(entry)
        assertEquals(
            "an authored trigger stopped recognising its own sample once a shortcut claimed it: $sample",
            entry.id,
            selector.select(sample)?.id,
        )
    }
}
```

Reuse the file's existing sample-command builder rather than writing a second one.

- [ ] **Step 3: Run `:data:repository` and `:app`**

- [ ] **Step 4: Commit**

```bash
git add data/repository/src app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt
git commit -m "feat(agentic-5.5/A1\"): the planner selects, and an authored trigger cannot be shadowed"
```

---

### Task 12: Measure what a tool-shaped prompt would weigh

**Files:**
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/ToolSchemaWeightTest.kt`

**Interfaces:**
- Produces: three numbers for this block's ADR.

**Why:** Master Plan `B3` justifies selection with "two hundred descriptors do not fit in a prompt",
and spec §3.2 measured that no `ToolDescriptor` has ever reached a prompt — `CatalogSchemaRenderer`
renders an `ActionCatalog`. The row is answered in this block for the deterministic branch; the block
that first plans tools with a model deserves a **number** rather than the slogan.

- [ ] **Step 1: Write the measurement**

```kotlin
/**
 * What a tool-shaped schema would weigh, if a future block ever renders one.
 *
 * This is a measurement, not a feature: nothing in the product renders tools to a model today, and
 * this test asserts only that the weight is what it is measured to be. It exists because Master Plan
 * §3.6 `B3` justifies selection with a prompt-size claim about a code path that does not exist, and
 * the block that builds that path should start from a number.
 */
@Test
fun `tool schema weight is measured for the sizes A1'' actually produces`() {
    listOf(2, 15, 120).forEach { n ->
        val rendered = renderToolSchema(List(n) { descriptor(ToolId("tool_$it")) })
        println("tool schema: n=$n chars=${rendered.length} approx_tokens=${rendered.length / 4}")
    }

    val fifteen = renderToolSchema(List(15) { descriptor(ToolId("tool_$it")) })
    assertTrue(
        "15 descriptors rendering to ${fifteen.length} chars is far outside the shape this measured; " +
            "re-measure and update the ADR rather than widening this bound",
        fifteen.length < 4_000,
    )
}
```

`renderToolSchema` is a **local test helper** modelled on `CatalogSchemaRenderer.render`'s one-line-per
-descriptor shape. Do not add a production renderer: §12 of the spec lists rendering tools to the model
as a non-goal, and shipping an unused renderer is how an unreviewed egress path appears.

- [ ] **Step 2: Run it and record the printed numbers**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*ToolSchemaWeightTest*' --rerun-tasks --info
```

Copy the three printed lines verbatim into the task report; they go into the ADR.

- [ ] **Step 3: Phase 2 boundary — full gate**

```
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
```

- [ ] **Step 4: Commit**

```bash
git add domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/ToolSchemaWeightTest.kt
git commit -m "test(agentic-5.5/A1\"): what a tool-shaped prompt would weigh, measured not asserted"
```

---

### Task 13: Measure the Phase 3 intent table on the device

**Files:**
- Modify: `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`

**Interfaces:**
- Produces: the filled §7.2 table that Phase 3's plan is written from. **Phase 3 is not planned until
  this task lands.**

**Why:** spec §3.1 and §7.2. Both halves of `B4`'s justification were measured false on this phone;
every cell is a measurement or the row does not ship.

- [ ] **Step 1: For each candidate in spec §7.2, measure two things on the SM-A325F**

```bash
adb shell am start -a android.settings.WIFI_SETTINGS
adb logcat -d | grep -i 'Permission Denial'
```

Record, per row: (a) whether the invocation is refused and which permission the refusal names;
(b) what the device actually **does** — opens a screen, opens a prefilled form, or performs the act
before any further input. The second question is the one `EXTRA_SKIP_UI` was wrongly assumed to answer.

- [ ] **Step 2: Add each result as a row, verbatim**

Same five columns as Task 5's table. An unmeasured row stays empty; it does not get a plausible value.

- [ ] **Step 3: Record which candidates survive**

A row ships in Phase 3 only if both cells are filled and the behaviour is compatible with its proposed
risk level. Note explicitly any row whose measurement contradicts spec §7.2's proposal — including
`uninstall_app`, whose `CONFIRM` rating (spec §7.3) stands even if the OS confirms on its own, and say
why.

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/plans/2026-09-12-a1-device-measurements.md
git commit -m "docs(agentic-5.5/A1\"): every Tier-0 candidate measured on the phone, none inherited"
```

---

## What is NOT in this plan, and where it goes

- **Phase 3 (spec §7)** — its own plan, written from Task 13's table.
- **Owner acceptance checklist, ADR, `CLAUDE.md` / `current-status.md` sync, `§HANDOFF` rewrite,
  ledger emptied before deletion, commit proposed** — spec §14 Phase 4, after Phase 3.
- **The owner item** — `"sayaç ayarla"` (spec §15), still `NOT JUDGED`, and the standing fact that no
  block of this track has ever been accepted on the phone in `tr` or `en`.

## Plan self-review

- **Spec coverage:** §4 → Task 4; §5 → Tasks 5–8; §6 → Tasks 10–12; §7 → Task 13 (measurement) plus a
  follow-up plan; §8.1 → Task 1; §8.2 → Task 2; §8.3 → Task 3; §13's items 1–4, 7–9 → Tasks 1, 4, 9,
  10, 11; item 5 → Task 2's mutation table; item 6 → Task 3's; item 12 → Task 12. **Gap accepted
  deliberately:** §13 items 8 (reachability for new authored triggers), 10 (parity re-run at scale) and
  11 (locale completeness for Phase 3 strings) belong to Phase 3 and are listed in its plan's scope.
- **Placeholders:** none. The one ellipsis this plan originally carried (Task 9's second test body) was
  written out. Tasks 5, 6 and 13 depend on device measurements by design — that is spec §3.1's rule,
  not a deferral, and each says exactly what to measure and what to do with the answer.
- **Type consistency:** `DynamicToolNames.names()` — the clash with `ToolRegistry.all()` is called out
  in Task 7 Step 4 and the name `names()` is used consistently in Tasks 8, 10 and 11. `ToolResult`'s
  success variant is deliberately named "check the file" rather than assumed.
