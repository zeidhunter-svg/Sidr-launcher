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

**Revision 2 (2026-09-18), after an independent review of revision 1 (`e029c31`).** The review
returned six Critical and nine Important findings and a verdict of *rework*; the controller verified
each at the source before accepting it. Four of them were defects of the same class this block exists
to close — a claim in prose that no test could see. What changed structurally: the three memory tools
are `TRANSIENT`, not `DURABLE` (the engine treats `DURABLE` as a **consent trigger**, so the plan's own
"footprint, not danger" reading would have stopped the loop on «называй телеграм телегой»); app-name
resolution moves **into the planner**, above the consent checkpoint, so the consent card names what will
actually be removed; and the mutation round no longer contains a mutation that cannot go red. Full
finding-by-finding record: the block ledger, 2026-09-18.

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

**`:app` runs in every task that adds or changes a registered tool** — Tasks 2, 3, 5, 7, 8, 10 — and not
only at the end. (Revision 2 inserts one task as **Task 6b** rather than renumbering — the convention this block
already used for Task 13b.) Two `:app` guards quantify over the **whole production federation** the moment a tool
appears: `DoctrineGuardTest`'s risk pin (`every registered tool's declared risk is pinned here`) and its
surface scan (`every tool … has a non-generic label on the surface`). A task that introduces a tool and
does not pin its risk, give it a label and run `:app` leaves the module red for every commit that
follows. Review finding I1.

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
| `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` | The five new step strings, `en`/`ru`/`tr` — **this module**, not `:app`: the keys resolve through `com.sidr.launcher.feature.launcher.R` |
| `feature/launcher/src/main/res/values/strings_locked.xml` | The `launcher_memory` provenance label, `translatable="false"` — Class A, so the owner-reviewed locale signature is untouched |
| `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt` | Task 6b: resolves an `app` argument to a package **at plan time**, above the consent checkpoint |

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
    fun `rows is stable and non-empty, so a caller reading it twice sees one answer`() {
        // Controller ruling R14-23: this asserts stability and non-vacuity, NOT immutability.
        // `rows()` returns the companion's map directly and Kotlin's `Map` is already read-only, so
        // "a caller cannot mutate the catalog" would be a name promising a property no code holds.
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
 * **`null` is not `emptyList()`.** An id with no row means nobody has stated an answer, and the
 * **Tier-0 source and its worker** fail closed on it — that source does not advertise it, that worker
 * does not dispatch it, and the guard goes red. An explicit `emptyList()` is the statement "this tool
 * needs no permission". Stated no wider than it is true (review finding M1): `SystemIntentToolSource`,
 * `ShortcutToolSource` and Task 9's `MemoryToolSource` do **not** consult this catalog. No live defect
 * follows — every row of theirs is `emptyList()` — but a KDoc claiming a property the code does not
 * have is the exact failure mode this block exists to remove.
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

- [ ] **Step 4: Fix EVERY call site — they are enumerated, not left to be discovered**

Changing this constructor breaks **two modules**, and the previous revision of this plan said only "if
an existing test constructed it" while running `:data:repository` alone (review finding C6). The call
sites, verified in the tree at `e029c31`:

| Module | File | Occurrences |
|---|---|---|
| `:data:repository` | `agent/Tier0IntentToolSourceTest.kt` | 2 |
| `:data:repository` | `agent/ToolMatchPlannerTest.kt` | 1 |
| `:data:repository` | `agent/FreeTextGoalEndToEndTest.kt` | 1 |
| `:app` | `agent/ToolRegistryPermissionGuardTest.kt` | 1 |
| `:app` | `doctrine/DoctrineGuardTest.kt` | 2 |

Every one gets `Tier0IntentToolSource(ToolPermissionCatalog(), presence)`. **Which presence:** in the
two `:app` guards and in every test whose subject is the registry's *contents* rather than the filter,
pass a fixture that grants everything —

```kotlin
private val grantsEverything = PermissionPresence { true }
```

— and write **why** beside it: those guards assert over the tools the product ships, and a fixture that
withheld one would make them vacuous rather than strict (this is the same trap as review finding C5).
A fixture that grants nothing belongs only in the three tests of Step 1, whose subject **is** the
filter.

- [ ] **Step 4b: Run both modules**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```

Expected: PASS in both. **`:app` is not optional here** — it is where two of the six call sites live, and
a commit that leaves `:app:compileDebugUnitTestKotlin` broken is a commit nobody can bisect through.

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

- [ ] **Step 4: Fix EVERY call site, then run both modules**

Same rule as Task 2 Step 4, different constructor. Verified call sites at `e029c31`:

| Module | File | Occurrences |
|---|---|---|
| `:data:repository` | `agent/Tier0IntentToolWorkerTest.kt` | 15 (12 + 3 throwing-launcher cases) |
| `:data:repository` | `agent/Tier0ToolExecutionEndToEndTest.kt` | 1 |
| `:app` | `doctrine/DoctrineGuardTest.kt` | 1 |

Each becomes `Tier0IntentToolWorker(launcher, ToolPermissionCatalog(), grantsEverything)` except the
three tests of Step 1, whose subject is the precondition itself. Then:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```

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

**Fixture, and it is load-bearing:** the guard builds `Tier0IntentToolSource(ToolPermissionCatalog(),
PermissionPresence { true })`. With any other fixture the mutations below prove something other than
what they claim — a tool withheld by the filter leaves `registry.all()`, and a test that quantifies over
`registry.all()` then passes by **absence**. Review finding C5 caught exactly that in revision 1.

The prover plants these, one at a time, restoring with `trap … EXIT` + `git checkout --`:
  1. Delete `<uses-permission android:name="com.android.alarm.permission.SET_ALARM" />` from
     `app/src/main/AndroidManifest.xml` → `every permission a registered tool needs is declared in the
     manifest` must go **RED**.
  2. Change the catalog's `SET_TIMER` row to `listOf("android.permission.NOT_DECLARED")` → the same
     test must go **RED**.
  3. Add a descriptor with **no** catalog row to a source that does **not** filter by the catalog —
     `SystemIntentToolSource` (or Task 9's `MemoryToolSource`, if it has landed) → `every registered
     tool has a permission row` must go **RED**.
     **Why not `Tier0IntentToolSource`, which revision 1 named here:** after Task 2 that source drops an
     unrowed descriptor before it is ever registered, so "registered without a row" is no longer a
     reachable state for it and the mutation could only ever be GREEN. A mutation that cannot fail is
     not a proof — it is the vacuous guard this protocol exists to catch, and revision 1 contained one.
  4. **Legitimate growth:** add a descriptor **with** an `emptyList()` row → both tests stay **GREEN**,
     proving the guard does not over-pin.
  5. **The silent-disappearance case, which nothing caught before.** Delete the `SET_TIMER` row from the
     catalog entirely → the tool vanishes from the registry (Task 2's fail-closed filter) and
     `the registry this guard reads contains the tools this federation is known to ship` must go **RED**
     via `REQUIRED_TOOL_IDS`. This is what stops a forgotten catalog row from turning a shipped tool
     silently invisible instead of loudly red — §7.7's own purpose, applied to §7.7's own mechanism.
  6. **The blind spot, proved rather than asserted — expected result GREEN.** Give a tool an
     `emptyList()` catalog row while the tool genuinely needs a permission (use `SET_TIMER`: set its row
     to `emptyList()` and leave the manifest alone). Both tests stay **GREEN**, because nothing here
     re-derives from the platform what a tool actually needs. That is **not** a defect to fix in this
     task: it is the limit this guard's own KDoc already states in prose, and this mutation turns the
     prose into an observation. The prover reports it as an observed boundary, never as a pass.

**This dispatch runs on `sonnet`, overriding the agent definition's `opus`** — not as an economy but
because the judgment has been converted into an enumerated procedure: all six mutations and their
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
stronger model might invent a mutation nobody listed. Mutations 5 and 6 above *are* that invention —
one by the controller, one by the plan's reviewer. If the prover's report suggests another, it goes to the controller as a
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

- [ ] **Step 4b: Pin the risk and give it a label IN THIS TASK, not later**

Two `:app` guards quantify over the whole federation the moment a tool is registered (review finding
I1), so a tool introduced without these leaves `:app` red for every commit until Task 11:

1. `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt` — add `set_alarm` to
   `declaredRisk` with `ActionRiskLevel.SAFE`.
2. `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt`
   — add `private const val TIER0_SET_ALARM = "set_alarm"` beside the existing `TIER0_*` literals and a
   `toolLabelFor` branch to `R.string.launcher_agent_step_alarm`. **A literal, not
   `Tier0ToolIds.SET_ALARM.value`:** `:feature:launcher` does not depend on `:data:repository` and must
   not (review finding C2); the existing literals carry a KDoc saying exactly this.
3. `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` — `launcher_agent_step_alarm`, all three
   locales in this commit.

Then run **both** modules plus `:app`:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```

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

        val installed = (apps.getInstalledApps() as? OperationResult.Success)?.value ?: return null

        // An alias outlives its target: the app it named can be uninstalled while the row stays. A
        // package that resolves from an alias but is not installed would send ACTION_DELETE at nothing,
        // and row 32 measured that such a refusal is invisible to the caller — so the step would report
        // Effected over emptiness. Checked here rather than trusted (review finding M3).
        (aliases.find(normalized) as? OperationResult.Success)?.value?.target?.appPackageOrNull()
            ?.takeIf { pkg -> installed.any { it.packageName == pkg } }
            ?.let { return it }

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

# Task 6b: the planner resolves the app name, ABOVE the consent checkpoint

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolMatchPlannerTest.kt`

**Interfaces:**
- Consumes: `AppTargetResolver.resolve(query): String?` (Task 6).
- Produces: `ToolMatchPlanner(selector: ToolSelector, appTargets: AppTargetResolver)`. For a descriptor
  that declares an argument named **`app`**, the planner resolves the vocabulary's raw value to a
  package and binds `ArgSource.Literal(package)`; if the descriptor **also** declares `app_label`, the
  raw text is bound there. **Resolution failure ⇒ `PlanningResult.NoPlan`** — the decline happens
  before a plan exists, not after the user has consented.

**Why this task exists (review finding C4, owner decision 2026-09-18).** `AgentExecutor` evaluates
`checkpointFor` at [AgentExecutor.kt:113] and only reaches `toolExecutor.invoke` at [:224]. So anything
resolved inside a worker is resolved **after** consent. With resolution in the worker, a consent card
for «удали telegram» could only ever say what the user typed — the package it will actually delete does
not exist yet at that moment, and on a device with two Telegram-like labels the user would be
confirming one thing and getting another. That is fork **F5** («consent fires before argument
binding»), recorded in `CLAUDE.md` and addressed to A4′. This task does **not** close F5 in general: it
closes it for arguments the planner can resolve deterministically, which is what the owner's condition 2
requires and what makes that condition a test rather than a sentence.

**The convention, and its named limit.** "An argument called `app` holds an app name" is a string
convention, not a type. It is pinned by a test here and stated in the planner's KDoc. A typed argument
kind is the `ArgType` debt (spec §7.5), addressed to the block that first ships MCP/AppFunctions — not
widened here.

**Fixture correction, revision 2.2 (ruling R14-34) — read this before writing the tests.** The three
tests below need a tool whose descriptor declares `app` (and `app_label`) AND a vocabulary entry that
routes text to it. **Neither ships yet**: `uninstall_app` is Task 7 and its triggers are Task 10, and
`ToolVocabulary.DEFAULT_ENTRIES` today holds exactly two entries, `set_timer` and
`open_system_settings`. Written against the default vocabulary these tests fail for the wrong reason —
the selector never matches, so every case returns `NoPlan` and test 2 passes vacuously while tests 1
and 3 fail.

Build the fixture instead, in the test file only:
`ToolVocabulary`'s primary constructor is `internal constructor(val entries: List<Entry>)` and exists
for exactly this (its KDoc: "so tests can build a vocabulary"). Construct a test-local vocabulary
carrying one synthetic entry with `argName = "app"`, pair it with `registryWith(...)` for a descriptor
declaring `app` and `app_label`, and keep the existing `set_timer` entry for test 3.
**Do NOT add any entry to `DEFAULT_ENTRIES`** — production triggers are Task 10's, they are owner-visible,
and a trigger invented here could silently capture a verb FastPath already owns.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `an app argument is bound as a package, with the raw text kept as the label`() = runTest {
        val planner = ToolMatchPlanner(selector, FakeTargets(mapOf("telegram" to "org.telegram.messenger")))

        val planned = planner.plan(AgentGoal(GoalShape.Free("удали приложение telegram")), registry)

        val args = (planned as PlanningResult.Planned).plan.steps.single().invocation.args
        assertEquals(ArgSource.Literal("org.telegram.messenger"), args["app"])
        assertEquals(ArgSource.Literal("telegram"), args["app_label"])
    }

    @Test
    fun `an unresolvable app name yields NoPlan, so nothing is ever consented to`() = runTest {
        val planner = ToolMatchPlanner(selector, FakeTargets(emptyMap()))

        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(AgentGoal(GoalShape.Free("удали приложение нечто")), registry),
        )
    }

    @Test
    fun `a tool with no app argument is untouched by resolution`() = runTest {
        val planner = ToolMatchPlanner(selector, FakeTargets(emptyMap()))

        val planned = planner.plan(AgentGoal(GoalShape.Free("set a timer for 10 minutes")), registry)

        assertEquals(
            ArgSource.Literal("10 minutes"),
            (planned as PlanningResult.Planned).plan.steps.single().invocation.args["duration"],
        )
    }
```

- [ ] **Step 2: Run and watch fail**

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*ToolMatchPlannerTest' --rerun-tasks`

- [ ] **Step 3: Implement**

Inside `plan`, between the existing required-argument check and the `args` construction:

```kotlin
        val resolvedArgs = buildMap<String, String> {
            putAll(match.args)
            if (descriptor.argSchema.any { it.name == APP_ARG }) {
                val raw = match.args[APP_ARG].orEmpty()
                val target = appTargets.resolve(raw) ?: return PlanningResult.NoPlan
                put(APP_ARG, target)
                if (descriptor.argSchema.any { it.name == APP_LABEL_ARG }) put(APP_LABEL_ARG, raw)
            }
        }
```

and the existing filter/`mapValues` run over `resolvedArgs` instead of `match.args`. Constants
`APP_ARG = "app"` and `APP_LABEL_ARG = "app_label"` live in the planner's companion.

- [ ] **Step 4: Run and watch pass, then the module suite**

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): the planner resolves an app name, so consent names what will happen"
```

---

# Task 7: `uninstall_app` — the first `CONFIRM` tool

**Files:**
- Modify: `app/src/main/AndroidManifest.xml` (one `<uses-permission>` line),
  `ToolPermissionCatalog.kt` (row), `Tier0IntentToolSource.kt` (descriptor),
  `Tier0IntentToolWorker.kt` (branch),
  `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` — the `@Named("appPackageName")`
  provider Step 3 requires (controller ruling R14-22: revision 2's file list omitted it)
- Test: `Tier0IntentToolWorkerTest.kt`

**Interfaces:**
- Produces: `Tier0ToolIds.UNINSTALL_APP = ToolId("uninstall_app")`; descriptor with
  `argSchema = listOf(ActionArg("app", description = "Package of the app to remove"), ActionArg("app_label", description = "What the user called it", required = false))`,
  `risk = ActionRiskLevel.CONFIRM`, `durability = ToolDurability.DURABLE`, `level = SYSTEM_INTENT`,
  `effect = EXTERNAL`; catalog row `listOf("android.permission.REQUEST_DELETE_PACKAGES")`.
- Consumes: the **already-resolved package** in `invocation.args["app"]` (Task 6b). **The worker does
  not resolve anything** — resolution happens above the consent checkpoint, which is what makes the
  owner's condition 2 achievable at all.

**Measured basis:** rows 16/26/28/29/32/33. Without the permission the uninstaller starts and dies in
~190 ms drawing nothing, and `startActivity` **returns normally**; with it the OS draws
«Удалить приложение? / Отмена / OK». The permission is `normal`, install-time, granted with no prompt.
**The refusal is undetectable afterwards and detectable beforehand** — which Tasks 1–3 already built.

**Owner ruling, 2026-09-18 («принято, разрешать»):** the manifest line ships in the release manifest.
Where each of the six conditions is actually enforced, after review finding I9 pointed out that
"enforced by there being no path" is an assumption until something holds it:

| Condition | Held by |
|---|---|
| 1. Explicit user command only | Task 12's one-step-plan test + the outbound sentinel test |
| 2. The consent card names label **and** package | Task 6b (resolution above the gate) + Task 11's two-argument string + Task 12's `AwaitingConsent` assertion |
| 3. Exact resolution or decline | Task 6 + Task 6b (`NoPlan` on failure) |
| 4. Our own package refused | this task, Step 1 |
| 5. Wording says "precondition checked", never "refusal detected" | documentation, `:app` KDoc |
| 6. The manifest line held mechanically | Task 4's mutation 1 |

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `uninstalling our own package is refused`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to OWN_PACKAGE)))

        assertTrue(result is ToolResult.Failed)
        assertEquals("uninstalling the launcher kills the surface running the session", 0, launcher.launched.size)
    }

    @Test
    fun `a blank package declines and nothing is dispatched`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, launcher.launched.size)
    }

    @Test
    fun `a package dispatches ACTION_DELETE for exactly that package`() = runTest {
        val launcher = RecordingLauncher()
        val worker = tier0Worker(launcher)

        val result = worker.invoke(ResolvedInvocation(
            Tier0ToolIds.UNINSTALL_APP,
            mapOf("app" to "org.telegram.messenger", "app_label" to "telegram"),
        ))

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

private fun uninstallApp(target: String): ToolResult {
    if (target.isBlank()) return ToolResult.Failed(CommandFailure.Generic)
    // Condition 4 of the owner's ruling: the agent never removes the launcher it is running inside.
    if (target == ownPackageName) return ToolResult.Failed(CommandFailure.Generic)
    return launch(Intent(Intent.ACTION_DELETE, Uri.parse("package:$target")))
}
```

The argument is already a package name: Task 6b resolved it at plan time so that the consent card could
name it. A worker that resolved it again here would be resolving **after** consent — the defect this
plan's revision 2 exists to remove.

`ownPackageName` is injected as a `String` (a `@Named("appPackageName")` provider in
`AgentProvidesModule` returning `context.packageName`) rather than read from a `Context` inside the
worker, so the refusal is testable without Robolectric.

- [ ] **Step 4: Run and watch pass**

- [ ] **Step 4b: Pin the risk and the label in this task (review finding I1)**

`DoctrineGuardTest.declaredRisk` gains `uninstall_app` → `ActionRiskLevel.CONFIRM`;
`AgentSessionPresentation` gains `private const val TIER0_UNINSTALL_APP = "uninstall_app"` (a literal —
`:feature:launcher` has no `:data:repository` edge) and its `toolLabelFor` branch; the three
`feature/launcher` `strings.xml` files gain `launcher_agent_step_uninstall`. Run
`:data:repository:testDebugUnitTest :feature:launcher:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks`.

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

- [ ] **Step 4b: Teach the two guards about the new shape — HERE, not in Task 10**

`ToolVocabularyReachabilityTest` builds each entry's sample as `"$form $SAMPLE_ARGUMENT"` and branches
only on `entry.argName == null`. For a two-slot entry that yields `"называй 10 minutes"` — no infix, so
`twoSlotMatch` returns `null`, and **two** guard tests go red for a reason that has nothing to do with
the trigger being unreachable (review finding I3). Left to Task 10, its Step 2 would diagnose this as
"FastPath claimed the trigger" and the implementer would delete a working trigger.

- In `ToolVocabularyReachabilityTest`: when `entry.secondArgName != null`, build
  `"$form $SAMPLE_ARGUMENT ${entry.infixByLocale.values.first().first()} $SAMPLE_ARGUMENT_2"` with a new
  `SAMPLE_ARGUMENT_2` constant. **Controller ruling R14-21: that file has THREE loops over
  `guardedEntries()`, not one** — `no vocabulary trigger is claimed by FastPath before the planner is
  asked`, `every trigger recognises its own sample command`, and `an authored trigger cannot be
  shadowed by a third-party shortcut name`. Each builds its sample by branching on
  `entry.argName == null`, so each needs the two-slot arm. Extract one `sampleCommands(entry)` helper
  and have all three read it, rather than patching the first and leaving two red for a reason that has
  nothing to do with reachability. The suffix shape of a two-slot entry is
  `"$SAMPLE_ARGUMENT ${infix} $SAMPLE_ARGUMENT_2 $form"` — the infix sits inside the remainder, which
  is what makes a Turkish SOV two-slot form expressible at all (see Task 10 Step 3).
- In `ToolVocabularyLocaleGuardTest`: include `infixByLocale` in the locale-coverage union, so a
  two-slot entry that carries `en`/`ru` prefixes but only an `en` infix is red rather than quietly
  monolingual.

Run: `./gradlew … :data:repository:testDebugUnitTest --tests '*ToolVocabulary*' --rerun-tasks`

- [ ] **Step 5: Commit**

```bash
git commit -am "feat(agentic-5.5/A1\"): a bounded two-slot form in the tool vocabulary"
```

---

# Task 9: the `launcher_memory` adapter

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt` (one constant)
- Modify: `…/agent/ToolPermissionCatalog.kt` — three `emptyList()` rows (controller ruling R14-22).
  `MemoryToolSource` does not consult the catalog, but `ToolRegistryPermissionGuardTest`'s
  `every registered tool has a permission row` quantifies over the **whole** federation and goes red
  the moment these three are registered without rows.
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
  `MemoryToolWorker @Inject constructor(save: SaveAliasUseCase, deleteAlias: DeleteAliasUseCase, deleteChoice: DeleteLearnedChoiceUseCase) : ToolWorker`.
  **Controller ruling R14-17 — the worker resolves NOTHING and takes no `AppTargetResolver`.**
  Revision 2 moved app-name resolution into the planner (Task 6b, owner decision 2026-09-18) but left
  this signature and two of the tests below in their revision-1 shape. A worker that resolved again
  would be handed `"org.telegram.messenger"` by the planner and would try to resolve *that* as a
  **label**, which fails. `invocation.args["app"]` is already a package here; a **blank** one is
  `Failed`.
- All three descriptors: `level = LAUNCHER_MEMORY`, `effect = ToolEffect.LOCAL` (nothing leaves the
  device — this is the launcher's own store), `risk = ActionRiskLevel.SAFE`,
  **`durability = ToolDurability.TRANSIENT`**.
- **`argSchema`, written out because a missing one fails silently** (review finding I4 —
  `ToolMatchPlanner` drops any argument the schema does not declare, the required-check then passes over
  an empty list, and the worker writes an alias keyed on an empty string):
  - `SET_APP_ALIAS`: `listOf(ActionArg("app", …), ActionArg("phrase", …), ActionArg("app_label", …, required = false))`
    — `app`/`phrase` are identical to Task 10's `argName` / `secondArgName`. **`app` triggers Task 6b's
    resolution**, so the worker receives a package and `app_label` carries what the user typed. All
    three must be declared: `ToolMatchPlanner` drops any argument the schema does not name, so an
    undeclared `app_label` would silently never reach the surface (Task 11).
  - `FORGET_APP_ALIAS`: `listOf(ActionArg("phrase", …))`.
  - `FORGET_LEARNED_CHOICE`: `listOf(ActionArg("phrase", …))`.

**Why `TRANSIENT` and not `DURABLE` — review finding C1, owner decision 2026-09-18.** `DURABLE` is not
a label in this engine, it is a **consent trigger**: `AgentExecutor.checkpointFor`'s fourth branch
returns `ConsentCheckpoint(DURABLE_EFFECT)` for any descriptor marked `DURABLE` whose earlier branches
did not fire — which is exactly the case for a `SAFE` tool. Marked `DURABLE`, «называй телеграм телегой»
would stop the loop and raise a consent card typed `ExternalHandoff` for an operation that hands nothing
outside, on a screen that (A0's recorded residual) does not even draw the plan in `AwaitingConsent`.
`ToolDurability`'s own KDoc defines `DURABLE` as an **irreversible** footprint (`DOC-HMA-3`); a row in
the launcher's own database, written on an explicit command and removable from Settings in two taps, is
not that. These three tools therefore behave like `set_timer`: you asked, it happened.
- Catalog rows: all three `emptyList()`.

**The normalization property this adapter depends on, and which must be held by a test rather than
assumed:** `SaveAliasUseCase` normalizes its phrase through `CommandNormalizer.normalize`;
`DeleteAliasUseCase` does **not** — it passes its argument to `AliasStore.delete` unchanged. So
`forget_app_alias` finds the stored key only because `ToolVocabulary` applied the *same* normalizer on
the way in. Hand the worker raw text and the delete removes nothing and reports success.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `set_app_alias stores the alias against the package the planner resolved`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store)

        // R14-17: `app` arrives ALREADY RESOLVED (Task 6b), and `app_label` carries the raw text for
        // the surface. The worker never calls AppTargetResolver.
        val result = worker.invoke(ResolvedInvocation(
            MemoryToolIds.SET_APP_ALIAS,
            mapOf("app" to "org.telegram.messenger", "app_label" to "телеграм", "phrase" to "телега"),
        ))

        assertTrue(result is ToolResult.Effected)
        val stored = store.upserted.single()
        assertEquals("телега", stored.phrase)
        assertEquals(AliasTarget.App("org.telegram.messenger"), stored.target)
    }

    @Test
    fun `set_app_alias declines a blank package instead of storing an alias pointing nowhere`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store)

        // R14-17: "the name could not be resolved" is decided ONE LAYER UP and yields PlanningResult
        // .NoPlan (Task 6b), so it never reaches a worker at all. What this worker still owes is a
        // refusal to write an alias whose target is empty.
        val result = worker.invoke(ResolvedInvocation(
            MemoryToolIds.SET_APP_ALIAS,
            mapOf("app" to "", "phrase" to "х"),
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

    // Review finding I6: SaveAliasUseCase returns Success(Unit) WITHOUT WRITING for a blank or
    // over-length phrase (`SaveAliasUseCase.kt:18-21`, MAX_ALIAS_PHRASE_LENGTH = 64). Passing that
    // through as Effected would put "Выполнено" on the surface and ToolObserved(Effected) in the trace
    // for an alias that does not exist — the exact class of lie Tasks 1-3 exist to remove, committed by
    // the block that removes it.
    @Test
    fun `an over-length phrase is Failed, not a silent no-op reported as done`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store, resolver = FakeResolver(mapOf("телеграм" to "org.telegram.messenger")))

        val result = worker.invoke(ResolvedInvocation(
            MemoryToolIds.SET_APP_ALIAS,
            mapOf("app" to "org.telegram.messenger", "phrase" to "x".repeat(65)),
        ))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, store.upserted.size)
    }

    @Test
    fun `a delete use case that throws is Failed, not an escaped exception`() = runTest {
        // `DeleteLearnedChoiceUseCase.delete` has NO try/catch — it hands `store.delete` straight
        // back (`DeleteLearnedChoiceUseCase.kt:5-7`), unlike Save/DeleteAliasUseCase which both map
        // exceptions to OperationResult. So "the use cases already map their exceptions" is true of
        // two of the three, and this worker owes the third a net. Controller finding, pre-flight scan.
        val worker = memoryWorker(preferences = ThrowingPreferenceStore)

        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_LEARNED_CHOICE, mapOf("phrase" to "банк")))

        assertTrue(result is ToolResult.Failed)
    }

    @Test
    fun `forgetting a phrase that was never stored is still Effected, and the KDoc says what that means`() = runTest {
        val store = RecordingAliasStore()
        val worker = memoryWorker(store)

        val result = worker.invoke(ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "никогда")))

        // AliasStore.delete reports no row count, so the worker cannot distinguish "removed one" from
        // "there was none". Effected here means THE OPERATION RAN, not THAT SOMETHING WAS REMOVED, and
        // the worker's KDoc must say exactly that sentence. Asserted so the limitation is a test's
        // subject rather than a reader's inference.
        assertTrue(result is ToolResult.Effected)
        assertEquals(listOf("никогда"), store.deleted)
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
`ToolResult.Failed(CommandFailure.Generic)`, and returns `ToolResult.Effected()` on success.

**It catches, and the reason is measured rather than defensive.** Revision 2 said "the use cases
already map their exceptions to `OperationResult`" — true of `SaveAliasUseCase` and
`DeleteAliasUseCase`, **false of `DeleteLearnedChoiceUseCase`**, which is
`suspend fun delete(key, context) = store.delete(key, context)` with no `try` at all
(`DeleteLearnedChoiceUseCase.kt:5-7`). A throwing `ResolutionPreferenceStore` would escape into
`AgentExecutor.perform`'s one un-`try`ed `toolExecutor.invoke` call site — CLAUDE.md's named residual
(8), the crash A1′'s final review fixed once already. So this worker catches the way
`Tier0IntentToolWorker` does: rethrow `CancellationException`, map anything else to
`Failed(CommandFailure.Generic)`. Held by the throwing-store test above. Closing it in the **engine**
stays A4′'s (spec §8.1).

**Two things it must do that the use cases do not** (review finding I6): before calling
`SaveAliasUseCase`, reject a blank phrase or one longer than `MAX_ALIAS_PHRASE_LENGTH` (64) with
`Failed`, because that use case answers `Success(Unit)` without writing; and carry a KDoc sentence
saying that for `forget_*`, `Effected` means **the operation ran**, not that something was removed —
`AliasStore.delete` reports no row count, so the stronger claim cannot be made and must not be implied.

- [ ] **Step 3b: Pin risk and labels for all three tools in this task (review finding I1)**

`DoctrineGuardTest.declaredRisk` gains three `SAFE` rows; `AgentSessionPresentation` gains three
`private const val` literals and three `toolLabelFor` branches; `feature/launcher`'s three `strings.xml`
gain `launcher_agent_step_set_alias`, `_forget_alias`, `_forget_choice`.

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
    // NOT "alarm kur": `RuleBasedIntentMatcher.INSTALL_VERBS.suffixByLocale["tr"]` is `setOf("kur")`
    // and matches on `endsWith(" kur")`, so "07:30 alarm kur" becomes a Play Store search. That is the
    // documented reason "zamanlayıcı kur" / "sayaç kur" were already deleted from this vocabulary, and
    // revision 1 of this plan re-proposed the same collision under a different noun (review finding I2).
    suffixByLocale = mapOf("tr" to setOf("alarm ayarla")),
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
forced through. Record in the task report which trigger was rejected and by which FastPath verb — that
record is what stops the next author re-adding it. Known collisions, two of them already paid for:
`"alarm kur"` is **excluded above** (`INSTALL_VERBS` `tr` suffix `"kur"`, review finding I2); the `tr`
suffix `"kaldır"` and the bare `ru` `"удали"` are the two most likely remaining, and if they collide,
keep the longer qualified forms only.

**Before diagnosing a red as a FastPath collision, check the shape.** A two-slot entry that fails its
own sample is a *generator* problem, fixed in Task 8 Step 4b — not a reachability problem. If Task 8's
Step 4b was skipped, stop and do it there rather than deleting a trigger here (review finding I3).

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
- Modify: `feature/launcher/src/main/res/values{,-ru,-tr}/strings.xml` — **this module, not `:app`**
  (review finding I5: these keys are read through `com.sidr.launcher.feature.launcher.R`; placed under
  `app/` they resolve to a different `R` and the file does not compile)
- Modify: `feature/launcher/src/main/res/values/strings_locked.xml` for the provenance label only, with
  `translatable="false"`, beside its four existing siblings
- Test: `feature/launcher/src/test/java/…/agent/AgentSessionPresentationTest.kt` (`toolLabelFor` only)
  and the existing Robolectric render test (`AgentSessionSurfaceProvenanceTest` /
  `LauncherScreenAgentProvenanceTest`) for anything that calls `PlanStep.line`

**Interfaces:** `PlanStep.line` gains a two-argument branch. The existing `literalSubject()` uses
`singleOrNull()`, so a two-argument tool falls through to the goal text — that is CLAUDE.md's named
limitation (5), and `set_app_alias` is the first tool to hit it. This task closes it **for two-argument
tools by name**, not in general.

**Consent-card condition (owner ruling condition 2):** `uninstall_app`'s step line must name **both**
the app label and the package, because resolution is fuzzy and the user must see what will be removed
before tapping Confirm.

**Two constraints that decide where these tests can live at all:**

- **`PlanStep.line` is `@Composable @ReadOnlyComposable`** (`AgentSessionPresentation.kt:162-171`), so it
  **cannot be called from a plain JUnit `@Test`** — revision 1 of this plan asked for exactly that and
  the implementer would have hit a compile error on step 2 and "fixed" the test into meaninglessness
  (review finding C3). `AgentSessionPresentationTest` is plain JUnit and today calls only
  `toolLabelFor`, which returns an `Int`. Keep it that way; assertions about rendered text go in the
  existing Robolectric render tests.
- **No `:data:repository` symbols here.** `:feature:launcher` depends on `:core:common`, `:core:ui`,
  `:domain` and nothing else (`feature/launcher/build.gradle.kts:43-45`). `MemoryToolIds` and
  `Tier0ToolIds` are unreachable; use `ToolId("set_app_alias")` in tests and `private const val`
  literals in production, as the existing `TIER0_*` literals already do and explain (review finding C2).
  `DoctrineGuardTest`'s surface scan looks for the **literal string** in this file, so a literal is what
  it needs.

- [ ] **Step 1: Write the failing tests, in the two places they can actually run**

In `AgentSessionPresentationTest` (plain JUnit — ids and resource ids only):

```kotlin
    @Test
    fun `each new tool maps to its own label, not the generic one`() {
        listOf("set_alarm", "uninstall_app", "set_app_alias", "forget_app_alias", "forget_learned_choice")
            .forEach { id ->
                assertNotEquals(
                    "a tool without its own label reads as 'Run this step for …' on the gate",
                    R.string.launcher_agent_step_generic,
                    toolLabelFor(ToolId(id)),
                )
            }
    }
```

In the existing Robolectric render test (real composition, real resources):

```kotlin
    @Test
    fun `a two-argument alias step names both arguments, not the goal text`() {
        // set_app_alias binds app_label and phrase; the generic path would print the goal text instead.
        composeRule.setContent { AgentSessionSurface(state = stateWithAliasStep()) }
        composeRule.onNodeWithText("telegram", substring = true).assertExists()
        composeRule.onNodeWithText("телега", substring = true).assertExists()
    }

    @Test
    fun `an uninstall step on the consent gate names BOTH the label and the package`() {
        // Owner condition 2. Both substrings, because "contains(\"telegram\")" alone passes on a card
        // that shows only what the user typed - which is what revision 1 asserted (review finding C4).
        composeRule.setContent { AgentSessionSurface(state = awaitingConsentForUninstall()) }
        composeRule.onNodeWithText("telegram", substring = true).assertExists()
        composeRule.onNodeWithText("org.telegram.messenger", substring = true).assertExists()
    }
```

- [ ] **Step 2: Run and watch fail**

- [ ] **Step 3: Add the strings and the branch**

`feature/launcher/src/main/res/values/strings.xml` (and the same keys, translated, in `values-ru` and
`values-tr` **of the same module**):

```xml
<string name="launcher_agent_step_alarm">Set an alarm for %1$s</string>
<string name="launcher_agent_step_uninstall">Remove %1$s (%2$s)</string>
<string name="launcher_agent_step_set_alias">Call “%1$s” “%2$s”</string>
<string name="launcher_agent_step_forget_alias">Forget the name “%1$s”</string>
<string name="launcher_agent_step_forget_choice">Forget what to open for “%1$s”</string>
```

and, in `feature/launcher/src/main/res/values/strings_locked.xml` beside its four siblings:

```xml
<string name="launcher_tool_level_launcher_memory" translatable="false">launcher_memory</string>
```

`launcher_agent_step_uninstall` takes **two** arguments — the label and the package — because that is
what owner condition 2 requires the consent card to show, and Task 6b is what makes both available at
gate time.

`toolLabelFor` gains the five ids; `provenanceLabelFor` gains
`ToolLevels.LAUNCHER_MEMORY.value -> R.string.launcher_tool_level_launcher_memory` — note it is only
reached for `EXTERNAL` effects, and memory tools are `LOCAL`, so this entry exists for completeness and
the test must assert that a memory step shows **no** provenance chip (nothing crosses the device
boundary, and claiming otherwise would be a lie in the opposite direction).

`PlanStep.line` gains, before the existing fallback:

```kotlin
    // Controller rulings R14-18 and R14-19. Read the arguments BY NAME, never by position or count,
    // and compare the id against a literal, never against `MemoryToolIds` — `:feature:launcher` has
    // no `:data:repository` edge (`feature/launcher/build.gradle.kts:43-45`), which this task's own
    // constraints section states and revision 2's snippet then violated.
    val literal = { name: String -> (invocation.args[name] as? ArgSource.Literal)?.value }
    when (invocation.id.value) {
        MEMORY_SET_APP_ALIAS -> {
            val app = literal(ARG_APP_LABEL) ?: literal(ARG_APP)
            val phrase = literal(ARG_PHRASE)
            if (app != null && phrase != null) {
                return sidrString(R.string.launcher_agent_step_set_alias, app, phrase)
            }
        }
        TIER0_UNINSTALL_APP -> {
            // Owner condition 2: the card names BOTH, because resolution is fuzzy. `app_label` is
            // `required = false`, so fall back to the package alone rather than to the goal text —
            // saying less, never something false.
            val pkg = literal(ARG_APP)
            val label = literal(ARG_APP_LABEL)
            if (pkg != null && label != null) {
                return sidrString(R.string.launcher_agent_step_uninstall, label, pkg)
            }
        }
    }
```

with `private const val ARG_APP = "app"`, `ARG_APP_LABEL = "app_label"`, `ARG_PHRASE = "phrase"`
beside the existing `TIER0_*` literals.

**Why not `literals.size == 2`, which revision 2 wrote.** After Task 6b a `set_app_alias` step carries
**three** literal arguments (`app`, `app_label`, `phrase`) and an `uninstall_app` step **two**, so a
count test selects neither correctly; and reading positionally would print the package where the
label belongs — which is the defect C4 was raised about, reintroduced at the last layer. Naming the
arguments also removes the `Map`-ordering caution entirely: nothing here depends on insertion order,
so there is no order left to assert.

`literalSubject()`'s `singleOrNull()` fallback is **unchanged** and still governs every other tool.
CLAUDE.md's named limitation (5) is therefore closed **for these two tools by name**, exactly as this
task's Interfaces section says — not in general.

- [ ] **Step 4: Run and watch pass; run `LocaleCompletenessGuardTest`**

Run: `./gradlew … :app:testDebugUnitTest --tests '*LocaleCompletenessGuardTest' --rerun-tasks`

- [ ] **Step 5: Commit, and say the signature is now invalid**

```bash
git commit -am "feat(agentic-5.5/A1\"): the five acting tools on the surface, in en/ru/tr"
```

**The locale signature is NOT invalidated by this task, and the report must say so rather than the
opposite** (review finding I5 — revision 1 asserted the reverse and would have cost the owner a review
cycle for nothing). Verified at `app/build.gradle.kts:218-228`: `checkOwnerReviewedLocaleStrings` walks
**only** files named `strings_locked.xml` in a `values` directory, and within them only keys **without**
`translatable="false"`. The five `launcher_agent_step_*` keys live in `strings.xml` — outside the scan
entirely — and `launcher_tool_level_launcher_memory` is `translatable="false"`, i.e. Class A. This is
the same precedent `CLAUDE.md` records for the 2026-08-23 A0 fix round.

The report states: **"no new Class B keys; the locale signature is untouched — checked against
`app/build.gradle.kts:218`"**. If a later task does add a Class B key, the rule stands unchanged: say
the owner must re-review and re-sign, and **never compute the digest**.

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

**The seam must cross `AgentExecutor`, not stop at the federation** (review finding I7). Revision 1
called `federation.executor.invoke(...)` directly, which skips `prepare`/`checkpointFor` — and that is
precisely where both of this review's Critical findings lived: a `DURABLE` memory tool silently raising
a consent card, and a consent card built before the package was resolved. A seam test that cannot see
the consent decision is not testing this block's seam. Drive it through `AgentExecutor.prepare` /
`perform` (or the `Start`/`Run` use cases) with a fake `AgentSessionStore`, and assert on
**`session.state`**.

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
    fun `a SAFE memory tool runs without ever raising a checkpoint`() = runTest {
        // Asserted on the SESSION, not on requiresConsent(step.risk): the latter is true-by-definition
        // for SAFE and cannot see checkpointFor's DURABLE branch — which is what revision 1 got wrong
        // (review finding C1).
        //
        // Controller ruling R14-20: `AgentSession` has NO `consentCheckpoint` property
        // (`AgentSession.kt:40-49`) and the enum values are `Running`/`AwaitingConsent`, not
        // SCREAMING_CASE. What is checkable is the state plus the trace. If the trace event's exact
        // type does not match what is written here, repair this assertion UPWARD — to whatever the
        // engine really records when `checkpointFor` fires — never downward to something that
        // compiles and checks nothing.
        val session = executor.prepare(sessionFor("называй телеграм как телега"))

        assertEquals(ExecutionState.Running, session.state)
        assertTrue(
            "a memory write must not stop the loop",
            session.trace.events.none { it is TraceEvent.ConsentRequested },
        )
    }

    @Test
    fun `uninstall_app stops at AwaitingConsent, and the pending step already carries the package`() = runTest {
        val session = executor.prepare(sessionFor("удали приложение telegram"))

        assertEquals(ExecutionState.AwaitingConsent, session.state)
        val pending = session.plan.steps.single()
        assertEquals(ArgSource.Literal("org.telegram.messenger"), pending.invocation.args["app"])
        assertEquals(ArgSource.Literal("telegram"), pending.invocation.args["app_label"])
    }

    @Test
    fun `the planner never builds more than one step for a recognised tool`() = runTest {
        // Owner condition 1: uninstall_app is reachable only from an explicit command. Nothing today
        // can append it as a second step; this is what holds that property when something changes
        // (review finding I9).
        listOf("удали приложение telegram", "поставь будильник на 7:30", "называй телеграм как телега")
            .forEach { text ->
                val planned = planner.plan(AgentGoal(GoalShape.Free(text)), federation.registry)
                assertEquals(1, (planned as PlanningResult.Planned).plan.steps.size)
            }
    }

    @Test
    fun `parity - a recognised tool runs with the model planner never consulted`() = runTest {
        // Review finding I8: five new tools become reachable through routing step 2b, i.e. in
        // localOnlyMode and offline. Parity means exactly two things — the model planner is not
        // consulted and nothing leaves the device — and nothing else in this block re-checks it.
        val countingModelPlanner = CountingCommandPlanner()
        routeCommand(text = "называй телеграм как телега", planner = countingModelPlanner, localOnly = true)

        assertEquals(0, countingModelPlanner.calls)
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

- [ ] **Step 3b: One sentinel line in the outbound guard (review finding I9)**

Add a planted `ToolId` sentinel to the existing `OutboundContextPolicy` guard test and assert it never
reaches the outbound payload. It holds by construction today — the model is offered `ActionIds`, and
`ToolRegistry`/`ToolDescriptor` appear nowhere in `domain/prompt`, `domain/planner` or `data/ai-cloud`,
which the plan's reviewer verified independently. The test is what keeps it true the day some block
teaches the planner to offer the registry to a model.

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
  "favourites" → nothing to build, recorded in the spec; §7.6's navigating set → Phase 3b, out of scope
  here and named above. §7.7's four numbered items → Tasks 1, 2, 3, 4 in that order. §13's verification
  list: item 1 (seam-crossing) → Task 12, **now through `AgentExecutor`**; item 6 (risk pinned) → **each
  introducing task**, 5, 7 and 9, rather than deferred to the end; item 8 (reachability per trigger) →
  Task 10, with Task 8 Step 4b's generator fix as its precondition; item 10 (parity) → Task 12; item 11
  (locale completeness) → Task 11.
  **Item 5, stated precisely after review finding I8.** The registry-keyed guard and its two named blind
  spots — "an intent built in a helper", "a registered tool with no worker branch" — were built and
  mutation-proved in **Phase 0**, not here. Task 4 is a **re-proof after changing the guard's data
  source**: a narrower claim, and the only one this plan may make. Whether Phase 0's round actually
  covered both blind spots is checked against Phase 0's own record before Task 4 reports; if it did not,
  that is Phase 0's debt carried forward, not this task's to invent.
- **M4 — a note for whoever reads the spec next:** §7.6 gives the example «называй телеграм телегой»
  (instrumental case, no separator); the implemented form is «называй X как Y», because a two-slot match
  needs a separator it can find. The spec's phrasing is illustrative, not a required surface form.
- **Line references** were re-checked against the tree after review finding M2: `toolLabelFor` is
  `AgentSessionPresentation.kt:93-99`, `PlanStep.line` is **:162-171**, `provenanceLabelFor` is
  **:201-210** (revision 1 quoted :150-170 and :202-212).
- **Placeholders:** none. Task 10 deliberately contains a decision the implementer may not take alone
  (a `tr` form that the two-slot shape cannot express) — that is an escalation instruction with a named
  owner, not a TODO.
- **Type consistency:** `PermissionPresence.isGranted` is spelled identically in Tasks 1, 2, 3, 9 and
  12. `ToolPermissionCatalog.permissionsFor` returns `List<String>?` in every task that calls it, and
  every caller treats `null` as fail-closed. `AppTargetResolver.resolve` returns `String?` (a package
  name) in Tasks 6, 7 and 9. `MemoryToolIds.SET_APP_ALIAS` is used in Tasks 9, 10 and 11 under that one
  name.
