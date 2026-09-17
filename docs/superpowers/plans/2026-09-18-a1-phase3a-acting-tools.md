# A1″ Phase 3a — the acting tool set and the precondition gate · Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement
> this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** ship five **acting** tools (`set_alarm`, `uninstall_app`, `set_app_alias`,
`forget_app_alias`, `forget_learned_choice`) against §7.2's floor of four, with the track's first
`CONFIRM` tool and a precondition gate that stops a worker reporting `Effected` for a refusal it cannot
see.

**Architecture:** three things change behind the A1′/A1″ boundary, which is otherwise untouched. A
production `ToolPermissionCatalog` becomes the single place that says which manifest permission a tool
needs; `Tier0IntentToolSource` stops advertising a tool whose permission is absent (the shape
`AndroidShortcutQuery` already ships for the `HOME` role) and its worker re-checks immediately before
dispatch; and a fourth adapter, `MemoryToolSource`/`MemoryToolWorker` at a new `launcher_memory` level,
turns three already-shipped use cases into tools that change the agent's own memory. `ToolVocabulary`
gains a bounded two-slot argument form — the only new engine-adjacent capability in this plan.

**Tech Stack:** Kotlin 2.4.10, Gradle 9.5.0, AGP 9.3.1, JDK 17 toolchain, Hilt, Room (schema 4,
untouched), JUnit4 + Robolectric, `kotlinx-coroutines-test`.

**Spec:** [docs/superpowers/specs/2026-09-12-a1-second-tool-mass-and-selection-design.md](../specs/2026-09-12-a1-second-tool-mass-and-selection-design.md)
— §7.6 (the repaired candidate set) and §7.7 (the precondition gate) govern this plan; §7.1–§7.5 are
its context.

**Device premise:** every Android fact this plan rests on is already measured in
[the measurements file](2026-09-12-a1-device-measurements.md) rows 13–33. **No new device measurement
round is required, and none may be invented:** a task that finds itself wanting an unmeasured Android
fact must stop and say so rather than reason from the platform's documentation (spec §3.1).

## Global Constraints

Copied verbatim from the spec and the repository's hard rules. Every task's requirements implicitly
include this section.

- **JDK 17.** Always pass `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`.
- **Block gate, one command, never piped through `tail`:**
  `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks`
- **`--rerun-tasks`, NEVER `--rerun`.** `--rerun` is not a build-level flag in Gradle 9.5.0; it prints
  `BUILD SUCCESSFUL` having executed nothing. A number counts only from a run that printed
  `N actionable tasks: N executed`.
- **Read test counts from the JUnit XML** under each module's `build/test-results/`, never the console.
  Baseline to compare against: **1375 tests, 0 failures** (`1db2cd8`).
- **`:domain` is `commonMain` + `jvmTest`.** stdlib + coroutines only — no Android, no `core/*`.
- **`OperationResult<T>` for repository/use-case ops; never throw to UI.**
- **User-facing text never originates in `domain` or in a ViewModel.** The feature layer picks the
  string via `sidrString(R.string.…)` (`HardcodedUiTextGuardTest`, `StringSeamGuardTest`).
- **`en` / `ru` / `tr` ship in the same commit as the feature** (`LocaleCompletenessGuardTest`).
- **A new translatable (Class B) string invalidates the owner-reviewed locale signature.** Never
  compute or write the `sha256` digest yourself — say in the task report that the owner must re-review
  and re-sign.
- **Not touched by any task in this plan:** `ActionIds` (seven frozen values),
  `OutboundContextPolicy.ALLOWED`, `GoalShape` (stays at two values), `ObservedFact`, `CommandFailure`,
  `ArgType`, the `Failed`/`Completed` divergence of A0.5 §6.3, Room schema 4.
- **Every new guard goes to a separate `mutation-prover` place.** The author's own green run proves
  nothing. Commit the legitimate change **before** mutating the file; restore with `trap … EXIT` +
  `git checkout --`, never `cp`.
- **Device rules (violating any is expensive):** never `connectedAndroidTest` or any Gradle task that
  touches the device — AGP uninstalls both APKs and destroys app data. Never `adb uninstall`. Never
  confirm an uninstall dialog. Read agent tables only via `tools/device/pull-agent-db.sh`.
- **Gradle is foreground.** Never end a turn waiting on a background build.

### Per-task verification rhythm

| Module | Test task |
|---|---|
| `:domain` | `:domain:jvmTest` |
| `:data:repository` | `:data:repository:testDebugUnitTest` |
| `:feature:launcher` | `:feature:launcher:testDebugUnitTest` |
| `:app` | `:app:testDebugUnitTest` |

The **full gate** runs once, at the end (Task 12), not after every task.

---

## File Structure

**Created**

| File | Responsibility |
|---|---|
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalog.kt` | The one production statement of which manifest permissions a tool needs, plus the `PermissionPresence` port and its `Context` implementation |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AppTargetResolver.kt` | Name → package, declining on zero or several; aliases first, then exact label |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolIds.kt` | The three `launcher_memory` tool ids |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolSource.kt` | `ToolRegistry` over the three memory descriptors |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolWorker.kt` | `ToolWorker` over `SaveAliasUseCase` / `DeleteAliasUseCase` / `DeleteLearnedChoiceUseCase` |
| `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalogTest.kt` | The catalog's totality against the production federation |
| `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AppTargetResolverTest.kt` | Decline-on-ambiguity, alias precedence |
| `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolWorkerTest.kt` | Each of the three tools changes exactly what it claims |
| `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentActingSeamTest.kt` | Seam-crossing: text → selection → plan → gate → worker → store changed |

**Modified**

| File | Change |
|---|---|
| `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt` | One constant: `LAUNCHER_MEMORY` |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt` | Two new descriptors; the source filters by `ToolPermissionCatalog` + `PermissionPresence` |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt` | Two new branches; a precondition re-check before every dispatch |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt` | A two-slot entry form; entries for the five acting tools |
| `app/src/main/AndroidManifest.xml` | `android.permission.REQUEST_DELETE_PACKAGES` |
| `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` | Fourth adapter; `PermissionPresence` binding |
| `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt` | Reads the production catalog instead of its hand-written column |
| `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` | Risk pin for the new tools |
| `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt` | Labels for the five tools; a two-argument step line |
| `app/src/main/res/values/strings.xml` + `values-ru` + `values-tr` | New step strings and the `launcher_memory` provenance label |

---

# Task 1: `ToolPermissionCatalog` and `PermissionPresence`

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalog.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalogTest.kt`

**Interfaces:**
- Consumes: `ToolId`, `ToolIds`, `Tier0ToolIds` (existing).
- Produces: `interface PermissionPresence { fun isGranted(permission: String): Boolean }`;
  `class ContextPermissionPresence @Inject constructor(@ApplicationContext context: Context) : PermissionPresence`;
  `class ToolPermissionCatalog @Inject constructor() { fun permissionsFor(id: ToolId): List<String>?; fun rows(): Map<ToolId, List<String>> }`.
  **`permissionsFor` returns `null` for an id with no row** — absence is not "needs nothing", it is
  "nobody said", and every consumer treats it as fail-closed.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPermissionCatalogTest {

    private val catalog = ToolPermissionCatalog()

    @Test
    fun `an unrowed id answers null, never an empty list`() {
        assertNull(catalog.permissionsFor(ToolId("nobody_declared_this")))
    }

    @Test
    fun `a tool that needs nothing has an explicit empty row`() {
        assertEquals(emptyList<String>(), catalog.permissionsFor(Tier0ToolIds.OPEN_SYSTEM_SETTINGS))
    }

    @Test
    fun `set_timer declares the permission row 27 measured as required`() {
        assertEquals(
            listOf("com.android.alarm.permission.SET_ALARM"),
            catalog.permissionsFor(Tier0ToolIds.SET_TIMER),
        )
    }

    @Test
    fun `rows are a copy - a caller cannot mutate the catalog`() {
        val first = catalog.rows()
        assertTrue(first.isNotEmpty())
        assertEquals(first, catalog.rows())
    }
}
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :data:repository:testDebugUnitTest --tests '*ToolPermissionCatalogTest' --rerun-tasks`
Expected: FAIL — unresolved reference `ToolPermissionCatalog`.

- [ ] **Step 3: Write the implementation**

```kotlin
package com.sidr.launcher.data.repository.agent

import android.content.Context
import android.content.pm.PackageManager
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Is a manifest permission held by **this** process, asked from inside it.
 *
 * Row 33 of the measurement file is why this port exists: a responder-side refusal is invisible to the
 * caller *afterwards* (`startActivity` returns normally and throws nothing whether the uninstaller
 * refuses or draws its dialog, row 32) and knowable *beforehand* — `checkSelfPermission` answered
 * `GRANTED(0)` on the declaring build and `DENIED(-1)` without it. So detection is a **precondition**,
 * never a better failure signal.
 */
interface PermissionPresence {
    fun isGranted(permission: String): Boolean
}

class ContextPermissionPresence @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionPresence {
    override fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * The one **production** statement of which manifest permissions a registered tool needs.
 *
 * It exists in `:data:repository` rather than `:domain` because `android.permission.*` strings are
 * Android's vocabulary and `:domain` is KMP `commonMain`. It exists in production rather than in a test
 * because `ToolRegistryPermissionGuardTest` used to carry this column by hand, and its own KDoc named
 * that as its weak point: a hand-written column is as green when it is wrong as when it is right.
 *
 * **`null` is not `emptyList()`.** An id with no row means nobody has stated an answer, and every
 * consumer must fail closed on it — a source does not advertise it, a worker does not dispatch it, and
 * the guard goes red. An explicit `emptyList()` is the statement "this tool needs no permission".
 *
 * Shortcut tools (`shortcut:` prefix) carry no row by construction: their ids are device-dependent.
 * That family is answered where it always was — in the guard, against the measured fact that shortcut
 * host access is the `android.app.role.HOME` role and not a manifest permission at all.
 */
class ToolPermissionCatalog @Inject constructor() {

    fun permissionsFor(id: ToolId): List<String>? = ROWS[id]

    fun rows(): Map<ToolId, List<String>> = ROWS

    private companion object {
        val ROWS: Map<ToolId, List<String>> = mapOf(
            ToolIds.LAUNCH_APP to emptyList(),
            ToolIds.PLAY_STORE_SEARCH to emptyList(),
            Tier0ToolIds.SET_TIMER to listOf("com.android.alarm.permission.SET_ALARM"),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyList(),
        )
    }
}
```

- [ ] **Step 4: Run the test and watch it pass**

Run the same command. Expected: PASS, 4 tests.

- [ ] **Step 5: Run the module's suite and commit**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :data:repository:testDebugUnitTest --rerun-tasks
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalog.kt data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolPermissionCatalogTest.kt
git commit -m "feat(agentic-5.5/A1\"): a production permission catalog, where a missing row is not consent"
```

---

# Task 2: the Tier-0 source stops advertising what it cannot run

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSourceTest.kt` (exists — add to it)

**Interfaces:**
- Consumes: `ToolPermissionCatalog.permissionsFor`, `PermissionPresence.isGranted` (Task 1).
- Produces: `Tier0IntentToolSource(catalog: ToolPermissionCatalog, presence: PermissionPresence)` —
  `all()` returns only tools whose row exists **and** all of whose permissions are granted; `find()`
  answers from that same filtered list, never from the unfiltered one.

- [ ] **Step 1: Write the failing tests**

```kotlin
    private class FakePresence(private val granted: Set<String>) : PermissionPresence {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    @Test
    fun `a tool whose permission is not held is not advertised`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertEquals(emptyList<ToolId>(), source.all().map { it.id }.filter { it == Tier0ToolIds.SET_TIMER })
        assertNull(source.find(Tier0ToolIds.SET_TIMER))
    }

    @Test
    fun `a tool needing nothing is advertised even when no permission is held`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))

        assertNotNull(source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS))
    }

    @Test
    fun `find never answers for a tool all() withholds`() {
        val source = Tier0IntentToolSource(ToolPermissionCatalog(), FakePresence(emptySet()))
        val advertised = source.all().map { it.id }.toSet()

        listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS).forEach { id ->
            assertEquals("find/all disagree for ${id.value}", id in advertised, source.find(id) != null)
        }
    }
```

- [ ] **Step 2: Run them and watch them fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*Tier0IntentToolSourceTest' --rerun-tasks`
Expected: FAIL — `Tier0IntentToolSource` takes no constructor arguments.

- [ ] **Step 3: Implement**

```kotlin
class Tier0IntentToolSource @Inject constructor(
    private val catalog: ToolPermissionCatalog,
    private val presence: PermissionPresence,
) : ToolRegistry {

    private val descriptors = listOf(/* unchanged existing two */)

    /**
     * **A source does not advertise a tool it cannot run**, and the shape is not new: this is what
     * `AndroidShortcutQuery` already does for the `HOME` role — `if (!hasShortcutHostPermission())
     * return emptyList()`. Measured basis: rows 27 and 32/33. A missing catalog row fails closed
     * (`?: return@filter false`), because "nobody stated an answer" must never read as "needs nothing".
     */
    private fun available(): List<ToolDescriptor> = descriptors.filter { descriptor ->
        val needed = catalog.permissionsFor(descriptor.id) ?: return@filter false
        needed.all(presence::isGranted)
    }

    override fun all(): List<ToolDescriptor> = available()

    override fun find(id: ToolId): ToolDescriptor? = available().firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Run and watch pass; then the module suite**

Run the `--tests '*Tier0IntentToolSourceTest'` command, then
`:data:repository:testDebugUnitTest --rerun-tasks`. Expected: PASS, no other test red. If an existing
test constructed `Tier0IntentToolSource()` with no arguments, update it to pass
`ToolPermissionCatalog()` and a presence fake granting `com.android.alarm.permission.SET_ALARM` — that
is the shipped device state (row 29: `granted=true`).

- [ ] **Step 5: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSourceTest.kt
git commit -m "feat(agentic-5.5/A1\"): the Tier-0 source withholds a tool whose permission is absent"
```

---

# Task 3: the worker re-checks immediately before dispatch

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorkerTest.kt` (exists — add to it)

**Interfaces:**
- Consumes: Task 1's catalog and presence.
- Produces: `Tier0IntentToolWorker(launcher: IntentLauncher, catalog: ToolPermissionCatalog, presence: PermissionPresence)`.
  Behaviour: a missing row or an ungranted permission yields `ToolResult.Failed(CommandFailure.Generic)`
  **and the `IntentLauncher` is never called**.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `an ungranted permission fails the step and never reaches the launcher`() = runTest {
        val launcher = RecordingLauncher()
        val worker = Tier0IntentToolWorker(launcher, ToolPermissionCatalog(), FakePresence(emptySet()))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "5 minutes")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(
            "A refusal is invisible after the fact (row 32), so a step that could not run must not " +
                "report Effected — DOC-ILM-3 requires the trace to be 1:1 with reality.",
            0,
            launcher.launched.size,
        )
    }

    @Test
    fun `a granted permission still dispatches`() = runTest {
        val launcher = RecordingLauncher()
        val worker = Tier0IntentToolWorker(
            launcher,
            ToolPermissionCatalog(),
            FakePresence(setOf("com.android.alarm.permission.SET_ALARM")),
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "5 minutes")))

        assertTrue(result is ToolResult.Effected)
        assertEquals(1, launcher.launched.size)
    }
```

- [ ] **Step 2: Run it and watch it fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*Tier0IntentToolWorkerTest' --rerun-tasks`
Expected: FAIL — constructor arity.

- [ ] **Step 3: Implement**

```kotlin
class Tier0IntentToolWorker @Inject constructor(
    private val launcher: IntentLauncher,
    private val catalog: ToolPermissionCatalog,
    private val presence: PermissionPresence,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        // The source already withheld this tool if its permission was absent (Task 2). This is the
        // narrower window that check cannot cover — state can move between the snapshot and the call —
        // and it is the same reasoning that keeps the SecurityException catch below as a backstop
        // rather than as the mechanism.
        val needed = catalog.permissionsFor(invocation.id) ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!needed.all(presence::isGranted)) return ToolResult.Failed(CommandFailure.Generic)

        return when (invocation.id) { /* existing branches, unchanged */ }
    }
}
```

- [ ] **Step 4: Run and watch pass**

Both new tests PASS; the existing worker tests must be updated to the new arity, granting
`com.android.alarm.permission.SET_ALARM`.

- [ ] **Step 5: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorkerTest.kt
git commit -m "feat(agentic-5.5/A1\"): a precondition re-check before dispatch, so a refusal is not Effected"
```

---

# Task 4: the permission guard reads production

**Files:**
- Modify: `app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt:55-60` (the
  hand-written `toolPermissions` map) and the two tests that read it.
- Modify: `app/src/test/java/com/sidr/launcher/agent/…` — `REQUIRED_TOOL_IDS` **stays a literal** and is
  not derived from the catalog (round-2 finding: deriving the floor from the column means one tidy-up
  deletes both).

**Interfaces:**
- Consumes: `ToolPermissionCatalog.rows()`.
- Produces: no new production symbol.

- [ ] **Step 1: Replace the hand-written column**

```kotlin
    /**
     * Registered tool → the permissions the platform requires of its caller, **read from production**
     * (`ToolPermissionCatalog`) rather than written out here. The previous hand-written column is what
     * this class's own KDoc named as its weak point: it was exactly as green when wrong as when right.
     * Now a tool ships with whatever production declares, and the manifest check below is what makes
     * that declaration true or red.
     */
    private val toolPermissions: Map<ToolId, List<String>> = ToolPermissionCatalog().rows()
```

- [ ] **Step 2: Run the guard and watch it stay green**

Run: `./gradlew … :app:testDebugUnitTest --tests '*ToolRegistryPermissionGuardTest' --rerun-tasks`
Expected: PASS. A green run here proves nothing yet — Step 4 is what proves it.

- [ ] **Step 3: Commit the legitimate change before mutating anything**

```bash
git add app/src/test/java/com/sidr/launcher/agent/ToolRegistryPermissionGuardTest.kt
git commit -m "test(agentic-5.5/A1\"): the permission guard reads the production catalog, not its own column"
```

- [ ] **Step 4: Dispatch a `mutation-prover` for this guard**

The prover must plant, one at a time, restoring with `trap … EXIT` + `git checkout --`:
  1. Delete `<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />` from
     `app/src/main/AndroidManifest.xml` → `every permission a registered tool needs is declared in the
     manifest` must go **RED**.
  2. Change the catalog's `SET_TIMER` row to `listOf("android.permission.NOT_DECLARED")` → the same
     test must go **RED**.
  3. Add a descriptor to `Tier0IntentToolSource` with **no** catalog row → `every registered tool has a
     permission row` must go **RED** (and Task 2's filter means it is not advertised — the prover must
     report which of the two effects it observed, because both are correct and they are different
     facts).
  4. **Legitimate growth:** add a descriptor **with** an `emptyList()` row → both tests stay **GREEN**,
     proving the guard does not over-pin.
  5. **The blind spot, proved rather than asserted — expected result GREEN.** Give a tool an
     `emptyList()` catalog row while the tool genuinely needs a permission (use `SET_TIMER`: set its row
     to `emptyList()` and leave the manifest alone). Both tests stay **GREEN**, because nothing here
     re-derives from the platform what a tool actually needs. That is **not** a defect to fix in this
     task: it is the limit this guard's own KDoc already states in prose, and this mutation turns the
     prose into an observation. The prover reports it as an observed boundary, never as a pass.

**This dispatch runs on `sonnet`, overriding the agent definition's `opus`** — not as an economy but
because the judgment has been converted into an enumerated procedure: all five mutations and their
expected colours are written above, including the one that must stay green. Two requirements make that
conversion real, and without them the override is not justified:

  - **Every RED must be reported with the actual failing assertion message, quoted from the JUnit XML**
    under `app/build/test-results/`, never as "it failed". This is what mechanically separates "the
    assertion caught the mutation" from "the module did not compile" — the classic false positive of
    mutation testing, and the thing an under-powered prover otherwise reports as proof.
  - **Restoration is `trap … EXIT` + `git checkout --`, never `cp`, and the report ends with the output
    of `git status --short`.** These mutations touch `AndroidManifest.xml`; this project has already had
    one agent die mid-edit and leave a dirty tree, and the manifest is not where that should recur.

**What the override gives up, stated so it is a decision rather than an oversight:** a prover on a
stronger model might invent a sixth mutation nobody listed. Mutation 5 above *is* that invention, made
by the controller in advance. If the prover's report suggests another, it goes to the controller as a
finding — the prover does not act on it.

---

# Task 5: `set_alarm`

**Files:**
- Modify: `Tier0IntentToolSource.kt` (descriptor), `Tier0IntentToolWorker.kt` (branch),
  `ToolPermissionCatalog.kt` (row)
- Test: `Tier0IntentToolWorkerTest.kt`

**Interfaces:**
- Produces: `Tier0ToolIds.SET_ALARM = ToolId("set_alarm")`; descriptor with
  `argSchema = listOf(ActionArg("time", description = "Clock time for the alarm, e.g. 7:30"))`,
  `level = SYSTEM_INTENT`, `effect = EXTERNAL`, `risk = SAFE`, `durability = TRANSIENT`; catalog row
  `listOf("com.android.alarm.permission.SET_ALARM")`.

**Measured basis (do not re-derive):** row 13/29 — `ACTION_SET_ALARM` **creates the alarm, already
enabled**; there is no prefilled form and nothing in the intent yields consent from the OS. Row 27 —
the permission is **required**, measured on a build without it. So `SAFE` here rests on the same
reading the owner accepted for `set_timer` (reversible, immediately visible, provenance disclosed,
nothing leaves the device) and **not** on a form argument. Write that sentence into the descriptor's
KDoc so it cannot later be "simplified" away.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `set_alarm sends hour and minutes and does not skip the responder's UI`() = runTest {
        val launcher = RecordingLauncher()
        val worker = Tier0IntentToolWorker(launcher, ToolPermissionCatalog(), FakePresence(ALARM_GRANTED))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_ALARM, mapOf("time" to "7:30")))

        assertTrue(result is ToolResult.Effected)
        val intent = launcher.launched.single()
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    @Test
    fun `an unparseable time declines instead of guessing`() = runTest {
        val launcher = RecordingLauncher()
        val worker = Tier0IntentToolWorker(launcher, ToolPermissionCatalog(), FakePresence(ALARM_GRANTED))

        listOf("", "tomorrow", "25:00", "7:75", "7", "7:3o").forEach { raw ->
            val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_ALARM, mapOf("time" to raw)))
            assertTrue("accepted a time it should decline: '$raw'", result is ToolResult.Failed)
        }
        assertEquals(0, launcher.launched.size)
    }
```

- [ ] **Step 2: Run and watch fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*Tier0IntentToolWorkerTest' --rerun-tasks`
Expected: FAIL — unresolved `Tier0ToolIds.SET_ALARM`.

- [ ] **Step 3: Implement**

```kotlin
// Tier0ToolIds
val SET_ALARM = ToolId("set_alarm")

// worker branch
Tier0ToolIds.SET_ALARM -> setAlarm(invocation.args["time"].orEmpty())

private fun setAlarm(raw: String): ToolResult {
    val at = parseClockTime(raw) ?: return ToolResult.Failed(CommandFailure.Generic)
    return launch(
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, at.first)
            .putExtra(AlarmClock.EXTRA_MINUTES, at.second)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
    )
}

/** `H:MM` or `HH:MM` only. A bounded token (spec §7.1) — never a guess, never a default. */
private fun parseClockTime(raw: String): Pair<Int, Int>? {
    val match = Regex("""^(\d{1,2}):(\d{2})$""").find(raw.trim()) ?: return null
    val hour = match.groupValues[1].toIntOrNull() ?: return null
    val minute = match.groupValues[2].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour to minute else null
}
```

- [ ] **Step 4: Run and watch pass**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): set_alarm - it creates the alarm, and the risk reading says why"
```

---

# Task 6: `AppTargetResolver` — a name becomes a package, or nothing happens

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AppTargetResolver.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AppTargetResolverTest.kt`

**Interfaces:**
- Consumes: `InstalledAppsRepository.getInstalledApps(): OperationResult<List<InstalledApp>>`,
  `AliasStore.find(phrase): OperationResult<Alias?>`, `CommandNormalizer.normalize`.
- Produces: `class AppTargetResolver @Inject constructor(apps: InstalledAppsRepository, aliases: AliasStore) { suspend fun resolve(query: String): String? }`
  — the package name, or `null`. **`null` means decline**, and every caller must treat it as such.

**Why learned resolutions are deliberately NOT consulted here, and this is a safety argument rather
than a scope cut:** a learned choice is keyed by `CapabilityKey(ActionIds.LAUNCH_APP, query)` — the
user taught the launcher what to **open**. Reusing that preference to pick a target for `uninstall_app`
would apply consent given for one act to a different, irreversible one. Aliases are different: an alias
is an explicit naming of an app, not a preference about an action. Record this in the class KDoc.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `an exact label resolves`() = runTest {
        val resolver = AppTargetResolver(FakeApps(listOf(app("Telegram", "org.telegram.messenger"))), EmptyAliases)
        assertEquals("org.telegram.messenger", resolver.resolve("telegram"))
    }

    @Test
    fun `two apps with the same label decline rather than guess`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Camera", "com.a.camera"), app("Camera", "com.b.camera"))),
            EmptyAliases,
        )
        assertNull(resolver.resolve("camera"))
    }

    @Test
    fun `no match declines`() = runTest {
        val resolver = AppTargetResolver(FakeApps(emptyList()), EmptyAliases)
        assertNull(resolver.resolve("telegram"))
    }

    @Test
    fun `an alias wins over a label and is matched on its normalized phrase`() = runTest {
        val resolver = AppTargetResolver(
            FakeApps(listOf(app("Telegram", "org.telegram.messenger"))),
            FakeAliases(mapOf("телега" to "org.telegram.messenger")),
        )
        assertEquals("org.telegram.messenger", resolver.resolve("  ТЕЛЕГА "))
    }

    @Test
    fun `a repository failure declines instead of throwing`() = runTest {
        val resolver = AppTargetResolver(FailingApps, EmptyAliases)
        assertNull(resolver.resolve("telegram"))
    }
```

- [ ] **Step 2: Run and watch fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*AppTargetResolverTest' --rerun-tasks`

- [ ] **Step 3: Implement**

```kotlin
class AppTargetResolver @Inject constructor(
    private val apps: InstalledAppsRepository,
    private val aliases: AliasStore,
) {
    suspend fun resolve(query: String): String? {
        val normalized = CommandNormalizer.normalize(query)
        if (normalized.isBlank()) return null

        (aliases.find(normalized) as? OperationResult.Success)?.value?.target?.appPackageOrNull()
            ?.let { return it }

        val installed = (apps.getInstalledApps() as? OperationResult.Success)?.value ?: return null
        return installed
            .filter { CommandNormalizer.normalize(it.label) == normalized }
            .singleOrNull()
            ?.packageName
    }
}
```

- [ ] **Step 4: Run and watch pass**

- [ ] **Step 5: Commit**

```bash
git add data/repository/src/main/java/com/sidr/launcher/data/repository/agent/AppTargetResolver.kt data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AppTargetResolverTest.kt
git commit -m "feat(agentic-5.5/A1\"): a name resolves to one package or to nothing, and never guesses"
```

---

# Task 7: `uninstall_app` — the first `CONFIRM` tool

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (one `<uses-permission>` line),
  `ToolPermissionCatalog.kt` (row), `Tier0IntentToolSource.kt` (descriptor),
  `Tier0IntentToolWorker.kt` (branch)
- Test: `Tier0IntentToolWorkerTest.kt`

**Interfaces:**
- Produces: `Tier0ToolIds.UNINSTALL_APP = ToolId("uninstall_app")`; descriptor with
  `argSchema = listOf(ActionArg("app", description = "Which app to remove"))`, `risk = ActionRiskLevel.CONFIRM`,
  `durability = ToolDurability.DURABLE`, `level = SYSTEM_INTENT`, `effect = EXTERNAL`; catalog row
  `listOf("android.permission.REQUEST_DELETE_PACKAGES")`.
- Consumes: `AppTargetResolver.resolve` (Task 6).

**Measured basis:** rows 16/26/28/29/32/33. Without the permission the uninstaller starts and dies in
~190 ms drawing nothing, and `startActivity` **returns normally**; with it the OS draws
«Удалить приложение? / Отмена / OK». The permission is `normal`, install-time, granted with no prompt.
**The refusal is undetectable afterwards and detectable beforehand** — which Tasks 1–3 already built.

**Owner ruling, 2026-09-18 («принято, разрешать»):** the manifest line ships in the release manifest.
Six conditions ride with it; three are this task's tests (explicit command only — enforced by there
being no suggestion path to it, the self-package refusal, and decline-on-unresolved), the consent card
naming both label and package is Task 11, and the wording rule is documentation.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `uninstalling our own package is refused`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher, resolver = FakeResolver(mapOf("sidr" to OWN_PACKAGE)))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "sidr")))

        assertTrue(result is ToolResult.Failed)
        assertEquals("uninstalling the launcher kills the surface running the session", 0, launcher.launched.size)
    }

    @Test
    fun `an unresolved name declines and nothing is dispatched`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher, resolver = FakeResolver(emptyMap()))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "whatever")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, launcher.launched.size)
    }

    @Test
    fun `a resolved name dispatches ACTION_DELETE for that package`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher, resolver = FakeResolver(mapOf("telegram" to "org.telegram.messenger")))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "telegram")))

        assertTrue(result is ToolResult.Effected)
        val intent = launcher.launched.single()
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:org.telegram.messenger", intent.data.toString())
    }

    @Test
    fun `the descriptor is CONFIRM and DURABLE`() {
        val descriptor = tier0Source().find(Tier0ToolIds.UNINSTALL_APP)!!
        assertEquals(ActionRiskLevel.CONFIRM, descriptor.risk)
        assertEquals(ToolDurability.DURABLE, descriptor.durability)
        assertTrue(requiresConsent(descriptor.risk))
    }
```

- [ ] **Step 2: Run and watch fail**

- [ ] **Step 3: Implement**

Manifest, beside the existing `SET_ALARM` line:

```xml
    <!--
      A1" Phase 3a / `uninstall_app`. `normal`, install-time, granted with no prompt (row 28). It
      grants no data access and cannot remove anything silently: the OS draws its own dialog and the
      user confirms there, behind Sidr's own CONFIRM gate. Owner decision 2026-09-18.
    -->
    <uses-permission android:name="android.permission.REQUEST_DELETE_PACKAGES" />
```

Worker:

```kotlin
Tier0ToolIds.UNINSTALL_APP -> uninstallApp(invocation.args["app"].orEmpty())

private suspend fun uninstallApp(query: String): ToolResult {
    val target = resolver.resolve(query) ?: return ToolResult.Failed(CommandFailure.Generic)
    // Condition 4 of the owner's ruling: the agent never removes the launcher it is running inside.
    if (target == ownPackageName) return ToolResult.Failed(CommandFailure.Generic)
    return launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$target")))
}
```

`ownPackageName` is injected as a `String` (a `@Named("appPackageName")` provider in
`AgentProvidesModule` returning `context.packageName`) rather than read from a `Context` inside the
worker, so the refusal is testable without Robolectric.

- [ ] **Step 4: Run and watch pass**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): uninstall_app, the track's first CONFIRM tool, and it refuses itself"
```

---

# Task 8: a two-slot argument form in the vocabulary

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolVocabularyTest.kt`

**Interfaces:**
- Produces: `ToolVocabulary.Entry` gains
  `val infixByLocale: Map<String, Set<String>> = emptyMap()` and `val secondArgName: String? = null`.
  A two-slot entry matches `<prefix> <A> <infix> <B>` and yields
  `ToolMatch(id, mapOf(argName to A, secondArgName to B))`. Either side blank ⇒ **no match**.
  A one-slot entry is unaffected: `infixByLocale` empty means the existing path runs unchanged.

- [ ] **Step 1: Write the failing tests**

```kotlin
    private fun twoSlot() = ToolVocabulary(listOf(
        ToolVocabulary.Entry(
            id = ToolId("t"),
            prefixByLocale = mapOf("ru" to setOf("называй")),
            infixByLocale = mapOf("ru" to setOf("как")),
            argName = "app",
            secondArgName = "phrase",
        ),
    ))

    @Test
    fun `both slots are captured`() {
        assertEquals(
            ToolMatch(ToolId("t"), mapOf("app" to "телеграм", "phrase" to "телега")),
            twoSlot().match("называй телеграм как телега"),
        )
    }

    @Test
    fun `a missing second slot is a miss, not a half-filled match`() {
        assertNull(twoSlot().match("называй телеграм как"))
        assertNull(twoSlot().match("называй как телега"))
        assertNull(twoSlot().match("называй телеграм"))
    }

    @Test
    fun `the infix is matched as a whole word, not inside one`() {
        assertNull(twoSlot().match("называй какао какао"))
    }

    @Test
    fun `a one-slot entry is unchanged by the new field`() {
        assertEquals(
            ToolMatch(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")),
            ToolVocabulary().match("set a timer for 10 minutes"),
        )
    }
```

- [ ] **Step 2: Run and watch fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*ToolVocabularyTest' --rerun-tasks`

- [ ] **Step 3: Implement**

```kotlin
    private fun Entry.toMatch(remainder: String): ToolMatch? = when {
        secondArgName != null -> twoSlotMatch(remainder)
        argName == null -> if (remainder.isBlank()) ToolMatch(id, emptyMap()) else null
        remainder.isBlank() -> null
        else -> ToolMatch(id, mapOf(argName to remainder))
    }

    /**
     * `<prefix> A <infix> B`. The infix is matched **surrounded by spaces** so "какао" cannot serve as
     * "как", and the **first** occurrence wins so a phrase containing the infix cannot silently move
     * the boundary. Either side blank is a miss: a half-filled invocation is worse than none, which is
     * the same rule `toMatch` already applies to one-slot entries.
     */
    private fun Entry.twoSlotMatch(remainder: String): ToolMatch? {
        val first = argName ?: return null
        val second = secondArgName ?: return null
        val infix = infixByLocale.values.flatten()
            .mapNotNull { form -> remainder.indexOf(" $form ").takeIf { it >= 0 }?.let { it to form } }
            .minByOrNull { it.first } ?: return null
        val left = remainder.take(infix.first).trim()
        val right = remainder.drop(infix.first + infix.second.length + 2).trim()
        if (left.isBlank() || right.isBlank()) return null
        return ToolMatch(id, mapOf(first to left, second to right))
    }
```

- [ ] **Step 4: Run and watch pass**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): a bounded two-slot form in the tool vocabulary"
```

---

# Task 9: the `launcher_memory` adapter

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt` (one constant)
- Create: `…/agent/memory/MemoryToolIds.kt`, `…/agent/memory/MemoryToolSource.kt`,
  `…/agent/memory/MemoryToolWorker.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` (fourth adapter,
  `PermissionPresence` binding)
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/memory/MemoryToolWorkerTest.kt`

**Interfaces:**
- Produces: `ToolLevels.LAUNCHER_MEMORY = ToolLevel("launcher_memory")`;
  `MemoryToolIds.SET_APP_ALIAS = ToolId("set_app_alias")`, `FORGET_APP_ALIAS = ToolId("forget_app_alias")`,
  `FORGET_LEARNED_CHOICE = ToolId("forget_learned_choice")`;
  `MemoryToolSource @Inject constructor() : ToolRegistry`;
  `MemoryToolWorker @Inject constructor(save: SaveAliasUseCase, deleteAlias: DeleteAliasUseCase, deleteChoice: DeleteLearnedChoiceUseCase, resolver: AppTargetResolver) : ToolWorker`.
- All three descriptors: `level = LAUNCHER_MEMORY`, `effect = ToolEffect.LOCAL` (nothing leaves the
  device — this is the launcher's own store), `risk = ActionRiskLevel.SAFE`,
  `durability = ToolDurability.DURABLE` (they write persistent state and `DURABLE` is about footprint,
  not about danger).
- Catalog rows: all three `emptyList()`.

**The normalization property this adapter depends on, and which must be held by a test rather than
assumed:** `SaveAliasUseCase` normalizes its phrase through `CommandNormalizer.normalize`;
`DeleteAliasUseCase` does **not** — it passes its argument to `AliasStore.delete` unchanged. So
`forget_app_alias` finds the stored key only because `ToolVocabulary` applied the *same* normalizer on
the way in. Hand the worker raw text and the delete removes nothing and reports success.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `set_app_alias stores the alias against the resolved package`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store, resolver = FakeResolver(mapOf("телеграм" to "org.telegram.messenger")))

        val result = worker.invoke(ResolvedInvocation(
            MemoryToolIds.SET_APP_ALIAS,
            mapOf("app" to "телеграм", "phrase" to "телега"),
        ))

        assertTrue(result is ToolResult.Effected)
        assertEquals(Alias("телега", AliasTarget.App("org.telegram.messenger"), any()), store.upserted.single())
    }

    @Test
    fun `set_app_alias declines when the app cannot be resolved`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store, resolver = FakeResolver(emptyMap()))

        val result = worker.invoke(ResolvedInvocation(
            MemoryToolIds.SET_APP_ALIAS,
            mapOf("app" to "нечто", "phrase" to "х"),
        ))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, store.upserted.size)
    }

    @Test
    fun `forget_app_alias deletes the phrase EXACTLY as the vocabulary normalized it`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store)

        worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "телега")))

        assertEquals(
            "DeleteAliasUseCase does not normalize; the key matches only because the vocabulary did",
            listOf(CommandNormalizer.normalize("телега")),
            store.deleted,
        )
    }

    @Test
    fun `forget_learned_choice deletes the launch_app key for that phrase, with no context`() = runTest {
        val store = RecordingPreferenceStore()
        val worker = memoryWorker(preferences = store)

        worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_LEARNED_CHOICE, mapOf("phrase" to "банк")))

        assertEquals(
            listOf(CapabilityKey(ActionIds.LAUNCH_APP, "банк") to ResolutionContext.None),
            store.deleted,
        )
    }

    @Test
    fun `a store failure is Failed, never Effected`() = runTest {
        val worker = memoryWorker(FailingAliasStore)
        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "телега")))
        assertTrue(result is ToolResult.Failed)
    }
```

- [ ] **Step 2: Run and watch fail**

- [ ] **Step 3: Implement the source, the worker and the DI wiring**

```kotlin
// AgentProvidesModule — the fourth adapter. Order is precedence (first-adapter-wins), and
// launcher_memory goes LAST: an authored system tool must never be shadowed by a memory tool.
ToolAdapter(ToolLevels.IN_APP, inAppRegistry, inAppWorker),
ToolAdapter(ToolLevels.SYSTEM_INTENT, tier0Registry, tier0Worker),
ToolAdapter(ToolLevels.APP_SHORTCUT, shortcutRegistry, shortcutWorker),
ToolAdapter(ToolLevels.LAUNCHER_MEMORY, memoryRegistry, memoryWorker),
```

```kotlin
@Provides
@Singleton
fun providePermissionPresence(impl: ContextPermissionPresence): PermissionPresence = impl
```

The worker maps each id to its use case, converts `OperationResult.Failure` to
`ToolResult.Failed(CommandFailure.Generic)`, and returns `ToolResult.Effected()` on success. It catches
nothing broadly: the use cases already map their exceptions to `OperationResult`.

- [ ] **Step 4: Run and watch pass; then `:app:testDebugUnitTest`**

`:app` matters here because `ToolRegistryPermissionGuardTest` now sees three new registered tools and
will go red until their catalog rows exist.

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): launcher_memory - the agent changes its own memory on request"
```

---

# Task 10: triggers, in three locales, proved reachable

**Files:**
- Modify: `ToolVocabulary.kt` (`DEFAULT_ENTRIES`)
- Test: `ToolVocabularyReachabilityTest.kt`, `ToolVocabularyLocaleGuardTest.kt` (both exist)

**Interfaces:** no new symbols. Five entries, `en`/`ru`/`tr` each.

**The rule every trigger must satisfy, and why a test rather than care:** six of A1′'s thirteen proposed
triggers could never fire, because FastPath's own verbs claim the text first — `"open …"`/`"открой …"`
go to `LAUNCH_VERBS`, `"… kur"` to `INSTALL_VERBS`. `ToolVocabularyReachabilityTest` runs each trigger
through the real `RuleBasedIntentMatcher` and fails if FastPath decides it.

- [ ] **Step 1: Add the entries with their triggers**

```kotlin
Entry(
    id = Tier0ToolIds.SET_ALARM,
    prefixByLocale = mapOf(
        "en" to setOf("set an alarm for", "set alarm for", "alarm for"),
        "ru" to setOf("поставь будильник на", "заведи будильник на", "будильник на"),
    ),
    suffixByLocale = mapOf("tr" to setOf("alarm kur")),
    argName = "time",
),
Entry(
    id = Tier0ToolIds.UNINSTALL_APP,
    prefixByLocale = mapOf(
        "en" to setOf("uninstall", "remove app"),
        "ru" to setOf("удали приложение", "удали"),
    ),
    suffixByLocale = mapOf("tr" to setOf("uygulamasını kaldır", "kaldır")),
    argName = "app",
),
Entry(
    id = MemoryToolIds.SET_APP_ALIAS,
    prefixByLocale = mapOf(
        "en" to setOf("call"),
        "ru" to setOf("называй"),
    ),
    infixByLocale = mapOf("en" to setOf("as"), "ru" to setOf("как")),
    argName = "app",
    secondArgName = "phrase",
),
Entry(
    id = MemoryToolIds.FORGET_APP_ALIAS,
    prefixByLocale = mapOf(
        "en" to setOf("forget the name", "forget name"),
        "ru" to setOf("забудь название", "забудь имя"),
    ),
    suffixByLocale = mapOf("tr" to setOf("adını unut")),
    argName = "phrase",
),
Entry(
    id = MemoryToolIds.FORGET_LEARNED_CHOICE,
    prefixByLocale = mapOf(
        "en" to setOf("forget what to open for"),
        "ru" to setOf("забудь что открывать по слову", "забудь выбор для"),
    ),
    suffixByLocale = mapOf("tr" to setOf("için seçimi unut")),
    argName = "phrase",
),
```

- [ ] **Step 2: Run the reachability test and expect some triggers to be RED**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*ToolVocabularyReachabilityTest' --rerun-tasks`
**This is the point of the task.** A trigger FastPath claims must be **removed or reworded**, never
forced through. Record in the task report which trigger was rejected and by which FastPath verb —
that record is what stops the next author re-adding it. The `tr` suffix `"kaldır"` and the bare `ru`
`"удали"` are the two most likely to collide; if they do, keep the longer qualified forms only.

- [ ] **Step 3: Run the locale guard**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*ToolVocabularyLocaleGuardTest' --rerun-tasks`
Every entry must cover `en`, `ru` and `tr` across the two (now three) maps. A `tr` form for
`set_app_alias` is required: Turkish is SOV, so its form is a **suffix** with the infix inside —
if the two-slot form cannot express it, say so in the report and leave the entry `en`/`ru` only **with
the guard red**, rather than shipping a silently monolingual tool. That is a decision for the
controller, not for the implementing agent.

- [ ] **Step 4: Run the whole module suite and commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): triggers for the five acting tools, each proved reachable"
```

---

# Task 11: the surface — labels, the two-argument step line, and three locales

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt:93-99`
  (`toolLabelFor`), `:150-170` (`PlanStep.line`), `:202-212` (`provenanceLabelFor`)
- Modify: `app/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentationTest.kt`

**Interfaces:** `PlanStep.line` gains a two-argument branch. The existing `literalSubject()` uses
`singleOrNull()`, so a two-argument tool falls through to the goal text — that is CLAUDE.md's named
limitation (5), and `set_app_alias` is the first tool to hit it. This task closes it **for two-argument
tools by name**, not in general.

**Consent-card condition (owner ruling condition 2):** `uninstall_app`'s step line must name **both**
the app label and the package, because resolution is fuzzy and the user must see what will be removed
before tapping Confirm.

- [ ] **Step 1: Write the failing test**

```kotlin
    @Test
    fun `a two-argument step names both arguments, not the goal text`() {
        val step = planStep(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "телеграм", "phrase" to "телега"))
        assertEquals("Называть «телеграм» словом «телега»", step.line(subject = "весь текст цели", dynamicLabels = emptyMap()))
    }

    @Test
    fun `an uninstall step names the package beside the label`() {
        val step = planStep(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "telegram"))
        assertTrue(step.line(subject = "", dynamicLabels = emptyMap()).contains("telegram"))
    }
```

- [ ] **Step 2: Run and watch fail**

- [ ] **Step 3: Add the strings and the branch**

`values/strings.xml` (and the same keys, translated, in `values-ru` and `values-tr`):

```xml
<string name="launcher_agent_step_alarm">Set an alarm for %1$s</string>
<string name="launcher_agent_step_uninstall">Remove %1$s</string>
<string name="launcher_agent_step_set_alias">Call “%1$s” “%2$s”</string>
<string name="launcher_agent_step_forget_alias">Forget the name “%1$s”</string>
<string name="launcher_agent_step_forget_choice">Forget what to open for “%1$s”</string>
<string name="launcher_tool_level_launcher_memory">Sidr’s own memory</string>
```

`toolLabelFor` gains the five ids; `provenanceLabelFor` gains
`ToolLevels.LAUNCHER_MEMORY.value -> R.string.launcher_tool_level_launcher_memory` — note it is only
reached for `EXTERNAL` effects, and memory tools are `LOCAL`, so this entry exists for completeness and
the test must assert that a memory step shows **no** provenance chip (nothing crosses the device
boundary, and claiming otherwise would be a lie in the opposite direction).

`PlanStep.line` gains, before the existing fallback:

```kotlin
    val literals = invocation.args.values.filterIsInstance<ArgSource.Literal>().map { it.value }
    if (invocation.id.value == MemoryToolIds.SET_APP_ALIAS.value && literals.size == 2) {
        return sidrString(R.string.launcher_agent_step_set_alias, literals[0], literals[1])
    }
```

**Ordering caution:** `args` is a `Map`, so `literals` order follows insertion. `ToolMatchPlanner`
builds it from `match.args`, which the two-slot form fills as `argName` then `secondArgName`. Assert
that order in the test rather than trusting it.

- [ ] **Step 4: Run and watch pass; run `LocaleCompletenessGuardTest`**

Run: `./gradlew … :app:testDebugUnitTest --tests '*LocaleCompletenessGuardTest' --rerun-tasks`

- [ ] **Step 5: Commit, and say the signature is now invalid**

```bash
git commit -am "feat(agentic-5.5/A1\"): the five acting tools on the surface, in en/ru/tr"
```

The task report **must** state: six new translatable strings landed, so
`checkOwnerReviewedLocaleStrings`'s signature over Class B keys is invalid until the owner re-reviews
and re-signs. **Do not compute the digest.**

---

# Task 12: the seam, end to end, and the phase gate

**Files:**
- Create: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/AgentActingSeamTest.kt`
- Modify: `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` (risk pin for the new ids)

**Interfaces:** no new production symbols.

**Why this task exists and may not be folded into the others:** this block's most expensive lesson,
three times now, is that four layers each fully tested in isolation summed to a dead capability. The
seams here are *vocabulary → selector → planner → federation → worker → store* and *precondition →
registry → dispatch*.

- [ ] **Step 1: Write the seam test with real objects**

```kotlin
    @Test
    fun `an alias command travels from text to the alias store through real objects`() = runTest {
        val aliasStore = RecordingAliasStore()
        val federation = ToolFederation(listOf(
            ToolAdapter(ToolLevels.SYSTEM_INTENT, tier0Source, tier0Worker),
            ToolAdapter(ToolLevels.LAUNCHER_MEMORY, MemoryToolSource(), memoryWorker(aliasStore)),
        ))
        val planner = ToolMatchPlanner(ToolSelector(ToolVocabulary(), EmptyDynamicNames))

        val planned = planner.plan(AgentGoal(GoalShape.Free("называй телеграм как телега")), federation.registry)

        val step = (planned as PlanningResult.Planned).plan.steps.single()
        assertFalse("a SAFE memory tool must not stop the loop", requiresConsent(step.risk))
        val result = federation.executor.invoke(ResolvedInvocation(step.invocation.id, literalArgs(step)))

        assertTrue(result is ToolResult.Effected)
        assertEquals("org.telegram.messenger", aliasStore.upserted.single().target.appPackageOrNull())
    }

    @Test
    fun `a CONFIRM tool's step carries consent before any dispatch`() = runTest {
        val planned = planner.plan(AgentGoal(GoalShape.Free("удали приложение telegram")), federation.registry)
        val step = (planned as PlanningResult.Planned).plan.steps.single()
        assertTrue(requiresConsent(step.risk))
    }

    @Test
    fun `a tool whose permission is absent is unreachable from text`() = runTest {
        val federation = federationWith(presence = FakePresence(emptySet()))
        val planned = planner.plan(AgentGoal(GoalShape.Free("поставь будильник на 7:30")), federation.registry)
        assertTrue("the registry withheld it, so the planner must find nothing", planned is PlanningResult.NoPlan)
    }
```

- [ ] **Step 2: Run and watch fail, then pass**

- [ ] **Step 3: Pin the new tools' risk in `DoctrineGuardTest`**

Add the five ids with their expected `ActionRiskLevel`, so `SAFE → CONFIRM` (or the reverse, which is
worse) on any of them is red. This is the `M9`/`(4)` gap of A1′, closed for these tools rather than in
general.

- [ ] **Step 4: Run the full gate**

```bash
find . -type d -path '*/build/test-results' -prune -exec rm -rf {} +
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
```

Expected: exit 0, a line reading `N actionable tasks: N executed`, and a JUnit-XML-summed count
**above 1375** with 0 failures and 0 errors. Read the counts from the XML, never the console. Report
the per-module split.

- [ ] **Step 5: Commit**

```bash
git commit -am "test(agentic-5.5/A1\"): the acting seam end to end, and the new tools' risk pinned"
```

---

## What is NOT in this plan, and where it goes

- **Phase 3b** — the eleven navigating tools, their triggers, their locales and a reachability test per
  trigger. Its own plan.
- **Phase 4 — close.** Owner acceptance checklist (which must include the `uninstall_app` consent card
  and the `CONFIRM` gate on the phone), ADR, `CLAUDE.md` + `current-status.md` sync, `§HANDOFF`
  rewritten, the ledger emptied before deletion, commit proposed to the owner.
- **The owner item** — `"sayaç ayarla"` (spec §15), still `NOT JUDGED`, and the standing fact that no
  block of this track has ever been accepted on the phone in `tr` or `en`.
- **Learned resolutions as a target source for package arguments** — deliberately excluded in Task 6,
  with the reason recorded there: consent given for *opening* must not select a target for *removing*.

## Plan self-review

- **Spec coverage:** §7.6's acting set → Tasks 5, 7, 9; its two-slot capability → Task 8; its cut of
  "favourites" → nothing to build, recorded in the spec; §7.6's navigating set → Phase 3b, out of
  scope here and named above. §7.7's four numbered items → Tasks 1, 2, 3, 4 in that order. §13's
  verification list: item 1 (seam-crossing) → Task 12; item 5 (guard mutation-proved, both blind
  spots) → Task 4 Step 4; item 6 (risk pinned) → Task 12 Step 3; item 8 (reachability per trigger) →
  Task 10; item 11 (locale completeness) → Task 11.
- **Placeholders:** none. Task 10 deliberately contains a decision the implementer may not take alone
  (a `tr` form that the two-slot shape cannot express) — that is an escalation instruction with a named
  owner, not a TODO.
- **Type consistency:** `PermissionPresence.isGranted` is spelled identically in Tasks 1, 2, 3, 9 and
  12. `ToolPermissionCatalog.permissionsFor` returns `List<String>?` in every task that calls it, and
  every caller treats `null` as fail-closed. `AppTargetResolver.resolve` returns `String?` (a package
  name) in Tasks 6, 7 and 9. `MemoryToolIds.SET_APP_ALIAS` is used in Tasks 9, 10 and 11 under that one
  name.
