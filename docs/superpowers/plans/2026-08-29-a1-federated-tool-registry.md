# A1′ — Federated ToolRegistry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the tool registry from one source projecting the launcher's own actions into a federation of adapters over one vocabulary, with provenance, a single risk→gate point, and a second real source whose tools are actually reachable.

**Architecture:** Federation lives **under** the boundary. `ToolFederation` owns one `List<ToolAdapter>` and exposes two faces derived from it — a `ToolRegistry` (read) and the single `ToolExecutor` implementation (dispatch). Per-source workers implement `ToolWorker`, which the engine never sees, so the engine's view is unchanged: one read port, one write port, one call site. Reachability comes from one **generic** planner arm over `GoalShape.Free`, not from a goal shape per tool.

**Tech Stack:** Kotlin 2.4.10, KMP (`:domain` = `commonMain` + `jvmTest`), Hilt, Room, Compose, JUnit4, Gradle 9.5.0, AGP 9.3.1, JDK 17.

**Spec:** [docs/superpowers/specs/2026-08-29-a1-federated-tool-registry-design.md](../specs/2026-08-29-a1-federated-tool-registry-design.md) — read it before Task 1. The plan argues from the spec; §8.4 in particular is the reason Tasks 8–10 exist.

## Global Constraints

- **JDK 17 only.** Every Gradle invocation carries `-Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10`. The machine default is JDK 25 and Gradle cannot parse it.
- **Never pipe `gradlew` through `tail`.** Read the exit code and the real output. Read test counts from the JUnit XML, not the console.
- **Block gate (Task 13 runs it in full):** `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test`. `:domain:jvmTest` and `:consumer:jvm:test` MUST be listed: `testDebugUnitTest` reaches neither. Baseline to beat: **1219 tests, 0 failures**.
- **Use `--rerun-tasks`, not the plain `--rerun` this plan originally said.** Every gate invocation below has already been corrected to `--rerun-tasks` — bare `--rerun` is not a valid Gradle 9.5.0 build-level flag (`--help` lists only `--rerun-tasks`), and it silently returns every task `UP-TO-DATE` while still printing `BUILD SUCCESSFUL`; it produced exactly this false green once inside this block's own Task 3 (round 2: 11s, zero of four test tasks executed). Ruling R9 in the block's ledger records the fix. Do not "restore" bare `--rerun` if a future edit of this file reverts it — that is regressing to the flag that produced the false green.
- **`:domain` is `commonMain` + stdlib + coroutines only.** No Android, no `java.*`, no `core/*`. Production code goes in `domain/src/commonMain/kotlin`, tests in `domain/src/jvmTest/kotlin`.
- **User-facing text never originates in `:domain` or in a ViewModel.** Typed values only; the feature layer picks the string via `sidrString(R.string.…)`.
- **`en`/`ru`/`tr` ship in the same commit as the feature.** `LocaleCompletenessGuardTest` enforces it. Files: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`.
- **`ActionIds`' seven values are frozen byte-for-byte.** Read them, never write them.
- **`GoalShape` gains no value** (`B1`). **`ObservedFact` gains no value** (owner instruction 2026-08-23). **`StepRationale` and `ArgType` gain no value** (spec §10).
- **Every new guard test must be mutation-proved.** Plant the mutation and revert it in **one** shell invocation with `trap … EXIT`; assert the edit **landed** before running the suite; afterwards verify the tree with a full `git status --porcelain --untracked-files=all`. A green run of a new guard proves nothing.
- **Commit per task.** Conventional commits, scope `agentic-5/A1'`. Agent commits; agent never pushes.

---

### Task 1: Provenance fields on the descriptor

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolDescriptor.kt`
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt`
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSource.kt`
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt`
- Modify: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlannerTest.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSourceTest.kt`

**Interfaces:**
- Produces: `ToolLevel(value: String)` value class; `ToolLevels.IN_APP`, `ToolLevels.SYSTEM_INTENT`, `ToolLevels.SANDBOX`; `enum ToolEffect { LOCAL, EXTERNAL }`; `ToolDescriptor.level: ToolLevel` and `ToolDescriptor.effect: ToolEffect`, **both without defaults**.

- [ ] **Step 1: Write the failing test**

Append to `SystemIntentToolSourceTest`:

```kotlin
@Test
fun `every projected tool declares the in-app level and an external effect`() {
    val source = SystemIntentToolSource(DefaultActionCatalog())

    assertEquals(listOf(ToolLevels.IN_APP, ToolLevels.IN_APP), source.all().map { it.level })
    assertEquals(listOf(ToolEffect.EXTERNAL, ToolEffect.EXTERNAL), source.all().map { it.effect })
}
```

- [ ] **Step 2: Run it and watch it fail to compile**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*SystemIntentToolSourceTest*'
```
Expected: compilation error — `Unresolved reference: ToolLevels`.

- [ ] **Step 3: Create the vocabulary**

`domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolLevel.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/**
 * Which source supplies a tool. An **open** value class over [String], not a closed sum, and the choice
 * is measured rather than stylistic: A0.5 recorded three new `ToolId`s costing zero core edits against
 * one new `GoalShape` value costing four files in three modules. Levels are the axis that grows —
 * AppFunctions, MCP, Accessibility — so they take the cheap shape.
 *
 * Openness costs nothing here because nothing orders levels: `DOC-HMA-2` asks whether the level
 * *changed*, which is inequality. Precedence between colliding sources comes from the federation's
 * construction order (`ToolFederation`), never from the type.
 */
@JvmInline
value class ToolLevel(val value: String)

/** The levels with a live source. A level with no adapter behind it is not declared here. */
object ToolLevels {
    /** The launcher's own action families, projected. */
    val IN_APP = ToolLevel("in_app")

    /** Android system intents that are not among the frozen seven `ActionIds`. */
    val SYSTEM_INTENT = ToolLevel("system_intent")

    /** `:consumer:jvm`'s sandboxed file tools. */
    val SANDBOX = ToolLevel("sandbox")
}

/**
 * Whether a tool's effect crosses the device boundary. Closed at two on purpose, and the asymmetry with
 * [ToolLevel] is the point: "who supplies this" grows without bound, "does this leave the device" is a
 * yes/no. `DOC-ILM-2` hangs off [EXTERNAL].
 */
enum class ToolEffect { LOCAL, EXTERNAL }
```

- [ ] **Step 4: Add the fields with no defaults**

In `ToolDescriptor.kt`, insert after `val id: ToolId,`:

```kotlin
    /**
     * Provenance, half one: which source supplied this tool. **No default.** A defaulted level would
     * let an adapter that forgets the field inherit someone else's provenance silently, which is
     * fail-open on the exact field `DOC-ILM-2` rests on.
     */
    val level: ToolLevel,

    /**
     * Provenance, half two: whether the effect crosses the device boundary. **No default**, for the
     * same reason — a defaulted `LOCAL` is a lie an adapter can tell by omission.
     */
    val effect: ToolEffect,
```

- [ ] **Step 5: Fill the four construction sites**

`SystemIntentToolSource.project(...)` — add to the `ToolDescriptor(...)` call:
```kotlin
            level = ToolLevels.IN_APP,
            // Both projected families reach another app. No projected tool is LOCAL today; the LOCAL
            // value is carried by `:consumer:jvm`'s sandbox. Named, not implied absent (spec §6.4).
            effect = ToolEffect.EXTERNAL,
```
`SandboxToolSource` — every descriptor gets `level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL,`.
`FakeToolRegistry.withA0Tools()` — both descriptors get `level = ToolLevels.IN_APP, effect = ToolEffect.EXTERNAL,`.
`FilePlannerTest` — any descriptor it builds gets `level = ToolLevels.SANDBOX, effect = ToolEffect.LOCAL,`.

- [ ] **Step 6: Run the test and the two suites it moves**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest :data:repository:testDebugUnitTest :consumer:jvm:test
```
Expected: PASS. If any other construction site appears, the compiler names it — that is the no-default design working.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): tools declare where they came from and whether they leave the device"
```

---

### Task 2: The federation — one object, two faces

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolFederationTest.kt`

**Interfaces:**
- Consumes: `ToolLevel`, `ToolLevels`, `ToolDescriptor`, `ToolRegistry`, `ToolExecutor`, `ResolvedInvocation`, `ToolResult` (Task 1 and existing).
- Produces: `interface ToolWorker { suspend fun invoke(invocation: ResolvedInvocation): ToolResult }`; `data class ToolAdapter(val level: ToolLevel, val registry: ToolRegistry, val worker: ToolWorker)`; `class ToolFederation(adapters: List<ToolAdapter>)` with `val registry: ToolRegistry` and `val executor: ToolExecutor`.

- [ ] **Step 1: Write the failing tests**

`ToolFederationTest.kt`:

```kotlin
package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionRiskLevel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ToolFederationTest {

    private fun descriptor(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private class FixedRegistry(private val items: List<ToolDescriptor>) : ToolRegistry {
        override fun all(): List<ToolDescriptor> = items
        override fun find(id: ToolId): ToolDescriptor? = items.firstOrNull { it.id == id }
    }

    private class NamedWorker(val name: String) : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Effected(ToolOutput(mapOf("worker" to name)))
    }

    private fun federation(vararg adapters: ToolAdapter) = ToolFederation(adapters.toList())

    private fun adapter(level: ToolLevel, workerName: String, vararg ids: String) = ToolAdapter(
        level = level,
        registry = FixedRegistry(ids.map { descriptor(it, level) }),
        worker = NamedWorker(workerName),
    )

    @Test
    fun `all concatenates its sources in adapter order`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        assertEquals(listOf("launch_app", "set_timer"), f.registry.all().map { it.id.value })
    }

    @Test
    fun `find reaches a tool in any source`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        assertEquals(ToolLevels.SYSTEM_INTENT, f.registry.find(ToolId("set_timer"))?.level)
        assertNull(f.registry.find(ToolId("nothing")))
    }

    @Test
    fun `on a colliding id the first adapter wins and the later declaration is dropped`() {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "launch_app"),
        )

        // Not symmetric exclusion: dropping both would let a later source take a built-in down with
        // it by naming a collision. The built-in survives; the intruder is what disappears.
        assertEquals(listOf(ToolLevels.IN_APP), f.registry.all().map { it.level })
        assertEquals(ToolLevels.IN_APP, f.registry.find(ToolId("launch_app"))?.level)
    }

    @Test
    fun `the executor routes each id to the worker of the adapter that declares it`() = runTest {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "set_timer"),
        )

        val launched = f.executor.invoke(ResolvedInvocation(ToolId("launch_app")))
        val timed = f.executor.invoke(ResolvedInvocation(ToolId("set_timer")))

        assertEquals(ToolOutput(mapOf("worker" to "in_app")), (launched as ToolResult.Effected).output)
        assertEquals(ToolOutput(mapOf("worker" to "system")), (timed as ToolResult.Effected).output)
    }

    @Test
    fun `a dropped collision is not routable either - the registry and the dispatcher cannot disagree`() = runTest {
        val f = federation(
            adapter(ToolLevels.IN_APP, "in_app", "launch_app"),
            adapter(ToolLevels.SYSTEM_INTENT, "system", "launch_app"),
        )

        assertEquals(
            ToolOutput(mapOf("worker" to "in_app")),
            (f.executor.invoke(ResolvedInvocation(ToolId("launch_app"))) as ToolResult.Effected).output,
        )
    }
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*ToolFederationTest*'
```
Expected: compilation error — `Unresolved reference: ToolWorker`.

- [ ] **Step 3: Write the federation**

`ToolFederation.kt`:

```kotlin
package com.sidr.launcher.domain.tool

/**
 * One source's worker: what actually performs a tool call for the adapter that declares it.
 *
 * **This is not a second boundary.** The boundary is [ToolExecutor], of which [ToolFederation] provides
 * the single implementation; a worker is reachable only from that implementation, and
 * `ToolWorkerCallSiteGuardTest` holds that mechanically. What federation adds is one hop, guarded
 * twice — not a strengthening, and this KDoc must not be read as claiming one.
 */
interface ToolWorker {
    suspend fun invoke(invocation: ResolvedInvocation): ToolResult
}

/** One federated source: its level, what it offers, and how it runs. */
data class ToolAdapter(
    val level: ToolLevel,
    val registry: ToolRegistry,
    val worker: ToolWorker,
)

/**
 * The federation. **One object, two faces, one list** — and that is load-bearing rather than tidy.
 *
 * Two independent combinators each taking a `List<ToolAdapter>` would receive their list by wiring
 * convention; a graph that ever provided them separately could hand them different lists, and the
 * registry would advertise a tool the dispatcher cannot route. That is the shape of A0 review finding
 * `F2`, where the consent gate read `risk` from the persisted plan and `permissionGate` from the live
 * registry — two sources for one decision. Deriving both faces from [adapters] here makes "the same
 * list" a property of construction rather than of discipline.
 *
 * **Collision policy: first adapter wins.** A duplicate [ToolId] is dropped from the later adapter, in
 * both faces, from the one index below. Symmetric exclusion was rejected: under a future third-party
 * source it is a capability-denial vector — naming a collision would take the built-in down too.
 * Precedence is therefore the composition root's ordering decision, which is why [ToolLevel] needs no
 * order of its own. Production is additionally held collision-free by `DoctrineGuardTest`, so this
 * runtime rule is defence in depth, not the mechanism.
 */
class ToolFederation(private val adapters: List<ToolAdapter>) {

    /**
     * `id -> adapter`, first declaration winning. Built once; both faces read it, so they cannot
     * disagree about what exists or about who runs it.
     */
    private val byId: Map<ToolId, ToolAdapter> = buildMap {
        adapters.forEach { adapter ->
            adapter.registry.all().forEach { descriptor ->
                if (descriptor.id !in this) put(descriptor.id, adapter)
            }
        }
    }

    private val descriptors: List<ToolDescriptor> = adapters.flatMap { adapter ->
        adapter.registry.all().filter { byId[it.id] === adapter }
    }

    val registry: ToolRegistry = object : ToolRegistry {
        override fun all(): List<ToolDescriptor> = descriptors
        override fun find(id: ToolId): ToolDescriptor? =
            byId[id]?.registry?.find(id)?.takeIf { descriptors.any { d -> d.id == id } }
    }

    val executor: ToolExecutor = object : ToolExecutor {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
            // Unreachable in a well-formed graph: `InvocationValidator.validate` already rejected an
            // unregistered tool against THIS registry, and `byId` is what that registry is built from.
            // Kept fail-closed and labelled as defence in depth rather than named in `CommandFailure`
            // — a variant for a branch that cannot run is the F2/D10 disease (spec §4.4).
            val adapter = byId[invocation.id] ?: return ToolResult.Failed(
                com.sidr.launcher.domain.intent.CommandFailure.Generic,
            )
            return adapter.worker.invoke(invocation)
        }
    }
}
```

- [ ] **Step 4: Run the tests**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*ToolFederationTest*'
```
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): the federation — one list, two faces, first adapter wins"
```

---

### Task 3: Workers, and both halves of the boundary guarded

**Files:**
- Rename: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolExecutor.kt` → `SystemIntentToolWorker.kt` (class renamed with it)
- Rename: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutor.kt` → `SandboxToolWorker.kt`
- Modify: `data/repository/src/test/java/.../SystemIntentToolExecutorTest.kt` → `SystemIntentToolWorkerTest.kt`, and `SystemIntentToolContractTest.kt`
- Modify: `consumer/jvm/src/test/kotlin/.../SandboxToolExecutorTest.kt` → `SandboxToolWorkerTest.kt`
- Modify: `app/src/test/java/com/sidr/launcher/agent/ToolExecutorCallSiteGuardTest.kt`
- Create: `app/src/test/java/com/sidr/launcher/agent/ToolWorkerCallSiteGuardTest.kt`
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolExecutor.kt` (add a `FakeToolWorker` alongside; the existing fake stays, `AgentExecutor` still takes a `ToolExecutor`)

**Interfaces:**
- Consumes: `ToolWorker` (Task 2).
- Produces: `SystemIntentToolWorker : ToolWorker`, `SandboxToolWorker : ToolWorker`, `FakeToolWorker : ToolWorker` (same scripted shape as `FakeToolExecutor`).

- [ ] **Step 1: Rename the two adapters to what they are**

Both classes keep their bodies **unchanged** except the supertype: `: ToolExecutor` becomes `: ToolWorker`. A class named `…ToolExecutor` that is not the boundary is the "prose says one thing, code another" defect this block is trying not to repeat.

```bash
git mv data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolExecutor.kt \
       data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolWorker.kt
git mv consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutor.kt \
       consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolWorker.kt
grep -rl 'SystemIntentToolExecutor\|SandboxToolExecutor' --include=*.kt . | grep -v '/build/' \
  | xargs sed -i 's/SystemIntentToolExecutor/SystemIntentToolWorker/g; s/SandboxToolExecutor/SandboxToolWorker/g'
git mv data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolExecutorTest.kt \
       data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolWorkerTest.kt
git mv consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutorTest.kt \
       consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolWorkerTest.kt
```

Then change the supertype in each renamed production file from `) : ToolExecutor {` to `) : ToolWorker {`, and fix the two imports (`domain.tool.ToolExecutor` → `domain.tool.ToolWorker`).

- [ ] **Step 2: Update the call-site guard's expected holder list**

In `ToolExecutorCallSiteGuardTest`, replace the test `the declared holders of a ToolExecutor are exactly the known four` with:

```kotlin
    /**
     * The scan the call-site count cannot do for itself. Asserted as a **sorted list, not a size**: a
     * holder that disappears has to be as red as one that appears.
     *
     * A1′ took this list from four to three, and that is the federation working rather than the guard
     * weakening. `ToolExecutor` now has exactly **one** implementation — the dispatcher inside
     * `ToolFederation` — and the two former adapters became `ToolWorker`s, reachable only from it.
     * The second hop has its own guard (`ToolWorkerCallSiteGuardTest`); neither is sufficient alone,
     * and together they hold what one type held before.
     *
     * The three, and why each is legitimate:
     *  - `AgentExecutor.kt` — the one holder, and the one call site;
     *  - `AgentProvidesModule.kt` — the Hilt binding that hands the dispatcher to the engine;
     *  - `ToolFederation.kt` — the one implementation, `val executor: ToolExecutor`.
     */
    @Test
    fun `the declared holders of a ToolExecutor are exactly the known three`() {
        val files = productionSources()
            .filter { declaresToolExecutor.containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
            .sorted()

        assertEquals(
            "Every declaration of ToolExecutor is a potential second call site, and the call-site " +
                "count above only sees the receiver spelled `toolExecutor`. If this list grew, the " +
                "new holder must be justified and this guard updated deliberately; if it shrank, the " +
                "wiring moved and the premise of this whole guard needs re-checking. Found: $files",
            listOf(
                "AgentExecutor.kt",
                "AgentProvidesModule.kt",
                "ToolFederation.kt",
            ),
            files,
        )
    }
```

- [ ] **Step 3: Write the sibling guard for the second hop**

`ToolWorkerCallSiteGuardTest.kt`:

```kotlin
package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * **The second hop, held the same way as the first.** `ToolExecutorCallSiteGuardTest` holds that the
 * engine reaches the world through one call in one file. Federation put a dispatcher below that call,
 * so "one path to the world" is now two hops, and a hop nobody guards is a hop where a call site can
 * appear. This guard is the second half; neither is sufficient alone.
 *
 * It asserts, over comment-stripped production text across the same roots:
 *  1. exactly one call spelled `worker.invoke(`, and that it lives in `ToolFederation.kt`;
 *  2. exactly the known set of files declaring `: ToolWorker` — the adapter type itself plus one
 *     worker per registered source. A new source adds one entry here and nowhere else, which is the
 *     growth rule's promise made checkable: a worker cannot be reached except through the dispatcher.
 *
 * `D2` predicted this shape («регекс держателя не видит `Map<…, ToolExecutor>` — вероятная форма A1′»)
 * and it is closed by construction rather than by a wider regex: the dispatcher holds *workers*, and
 * the map it holds them in is `ToolFederation`'s own.
 */
class ToolWorkerCallSiteGuardTest {

    private val repoRoot = File("..")

    private val productionRoots: List<File> =
        kmpProductionRoots(File(repoRoot, "domain/src")) +
            listOf(
                "data/repository/src/main/java",
                "feature/launcher/src/main/java",
                "app/src/main/java",
                "consumer/jvm/src/main/kotlin",
            ).map { File(repoRoot, it) }

    private val declaresToolWorker = Regex(""":\s*ToolWorker\b""")

    private fun productionSources(): List<File> =
        productionRoots.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }

    @Test
    fun `scanned roots all exist`() {
        assertEquals(
            "no :domain source root was derived — this guard went blind to the module that declares " +
                "ToolWorker",
            true,
            kmpProductionRoots(File(repoRoot, "domain/src")).isNotEmpty(),
        )
        productionRoots.forEach { root ->
            assertEquals("missing scan root: $root", true, root.isDirectory)
        }
    }

    @Test
    fun `there is exactly one call site of ToolWorker invoke in production code`() {
        val hits = productionSources()
            .flatMap { file ->
                stripComments(file.readText())
                    .lines()
                    .withIndex()
                    .filter { (_, line) -> line.contains("worker.invoke(") }
                    .map { (i, line) -> "${file.path}:${i + 1}: ${line.trim()}" }
            }

        assertEquals(
            "A ToolWorker may be reached only from the dispatcher. Found: $hits",
            1,
            hits.size,
        )
    }

    @Test
    fun `the one worker call site lives in ToolFederation`() {
        val files = productionSources()
            .filter { stripComments(it.readText()).contains("worker.invoke(") }
            .map { it.name }

        assertEquals(listOf("ToolFederation.kt"), files)
    }

    @Test
    fun `the declared holders of a ToolWorker are exactly the known four`() {
        val files = productionSources()
            .filter { declaresToolWorker.containsMatchIn(stripComments(it.readText())) }
            .map { it.name }
            .sorted()

        assertEquals(
            "A new ToolWorker declaration is a new path to the world. It is legitimate only as a " +
                "registered adapter's worker; if this list grew without a matching adapter, the " +
                "growth rule was bypassed. Found: $files",
            listOf(
                "SandboxToolWorker.kt",
                "SystemIntentToolWorker.kt",
                "Tier0IntentToolWorker.kt",
                "ToolFederation.kt",
            ),
            files,
        )
    }
}
```

> The `Tier0IntentToolWorker.kt` entry is written now and turns this test red until Task 6 creates it. That is deliberate: run Task 3 to green **without** that entry, add it in Task 6's commit. Note it in the commit message so the sequence is not read as an oversight.

- [ ] **Step 4: Declare the new guard's scan roots as task inputs**

In `app/build.gradle.kts`, the `Test` task's `inputs.dir(...)` declarations already cover these roots for `ToolExecutorCallSiteGuardTest`. Verify by reading the file; if the new guard's roots are identical (they are), add a comment naming the second consumer of those declarations rather than duplicating them.

- [ ] **Step 5: Run both guards**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*CallSiteGuardTest*' --rerun-tasks
```
Expected: PASS (with `Tier0IntentToolWorker.kt` omitted from the list for now).

- [ ] **Step 6: Mutation-prove both guards — one shell invocation, landing asserted**

```bash
cd /home/Suleiman/Sidr-launcher
cp domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt /tmp/tf.bak
trap 'cp /tmp/tf.bak domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt; rm -f /tmp/tf.bak' EXIT
# Plant a second worker call site by giving the dispatcher a decoy path.
python3 - <<'PY'
p="domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolFederation.kt"
s=open(p).read()
old="            return adapter.worker.invoke(invocation)"
new="            if (invocation.args.isEmpty()) return adapter.worker.invoke(invocation)\n            return adapter.worker.invoke(invocation)"
assert s.count(old)==1, f"landing check failed: {s.count(old)} occurrences"
open(p,"w").write(s.replace(old,new))
print("mutation landed")
PY
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*ToolWorkerCallSiteGuardTest*' --rerun-tasks; echo "EXIT=$?"
```
Expected: **RED** — "A ToolWorker may be reached only from the dispatcher. Found: [2 hits]". A green run here means the guard is decorative and must be fixed before proceeding.

- [ ] **Step 7: Verify the tree and commit**

```bash
git status --porcelain --untracked-files=all   # must show only intended files; no probe left behind
git add -A && git commit -m "refactor(agentic-5/A1'): adapters become workers; the boundary keeps one call site, now guarded in two hops"
```

---

### Task 4: Wire both consumers to the federation

**Files:**
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt`
- Modify: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt:51-53`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt` (existing — must stay green untouched)

**Interfaces:**
- Consumes: `ToolFederation`, `ToolAdapter`, `ToolLevels` (Task 2), `SystemIntentToolWorker`, `SandboxToolWorker` (Task 3).
- Produces: `AgentProvidesModule.provideToolFederation(...)`, and `provideToolRegistry`/`provideToolExecutor` now derived from it.

- [ ] **Step 1: Replace the two direct bindings with the federation**

In `AgentProvidesModule`, replace `provideToolRegistry` and `provideToolExecutor` with:

```kotlin
    /**
     * The federation, and the **composition root's ordering decision**: built-ins first. Collision
     * precedence is first-adapter-wins (`ToolFederation`), so this order is what prevents a later
     * source from shadowing a projected family. `DoctrineGuardTest` asserts this list is the declared
     * set and that no adapter in it names a network type.
     */
    @Provides
    @Singleton
    fun provideToolFederation(
        inAppRegistry: SystemIntentToolSource,
        inAppWorker: SystemIntentToolWorker,
        tier0Registry: Tier0IntentToolSource,
        tier0Worker: Tier0IntentToolWorker,
    ): ToolFederation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, inAppRegistry, inAppWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, tier0Registry, tier0Worker),
        ),
    )

    /**
     * Both ports come from the **same** federation object, which is why the registry cannot advertise
     * a tool the dispatcher fails to route. Deriving them separately from a shared list would restore
     * exactly the two-sources-one-decision shape of A0 finding F2.
     */
    @Provides
    @Singleton
    fun provideToolRegistry(federation: ToolFederation): ToolRegistry = federation.registry

    @Provides
    @Singleton
    fun provideToolExecutor(federation: ToolFederation): ToolExecutor = federation.executor
```

> Task 6 creates `Tier0IntentToolSource`/`Tier0IntentToolWorker`. Until then, wire the federation with the single in-app adapter and add the second `ToolAdapter` line in Task 6's commit.

- [ ] **Step 2: Wire the console harness the same way**

`ConsoleHarness.kt`, replacing lines 51–53:

```kotlin
    private val federation = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SANDBOX, SandboxToolSource(), SandboxToolWorker(root))),
    )
    private val registry = federation.registry
    private val executor = AgentExecutor(registry, federation.executor)
```

- [ ] **Step 3: Run both consumers' suites**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :consumer:jvm:test :app:testDebugUnitTest :app:assembleDebug
```
Expected: PASS. `AgentLoopTest` and `ConsoleHarnessTest` must pass **unmodified** — the consumer's behaviour is identical, only its wiring moved.

- [ ] **Step 4: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): both consumers reach the world through one federation"
```

---

### Task 5: One risk → gate predicate

**Files:**
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/action/ConsentPolicy.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentExecutor.kt:285`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt:156`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/resolution/ResolutionPreferencePolicy.kt:29`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/memory/resolution/EvaluateLearnedChoiceDisplayStateUseCase.kt:21`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/action/ConsentPolicyTest.kt`

**Interfaces:**
- Produces: `fun requiresConsent(risk: ActionRiskLevel): Boolean` in `com.sidr.launcher.domain.action`.

- [ ] **Step 1: Write the failing tests, including the one that matters**

```kotlin
package com.sidr.launcher.domain.action

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsentPolicyTest {

    @Test
    fun `SAFE runs without consent and everything above it stops`() {
        assertEquals(false, requiresConsent(ActionRiskLevel.SAFE))
        assertEquals(true, requiresConsent(ActionRiskLevel.CONFIRM))
        assertEquals(true, requiresConsent(ActionRiskLevel.DANGEROUS))
    }

    /**
     * The wake-up test, and the reason `DOC-ADL-1` is a latent defect rather than a missing
     * abstraction. Before this block four sites decided gating in two spellings — `>= CONFIRM` in the
     * agent, `!= SAFE` in the model, memory and UI paths. With three levels the two coincide, so the
     * defect is invisible today and arrives the day a level is inserted below CONFIRM (`D11`).
     *
     * Written against the enum's own ordering so it needs no new level to be meaningful: every level
     * that is not the lowest must require consent, whatever the enum grows to.
     */
    @Test
    fun `every level above the lowest requires consent, whatever levels exist`() {
        val levels = ActionRiskLevel.entries
        val lowest = levels.first()

        assertEquals(false, requiresConsent(lowest))
        levels.drop(1).forEach { level ->
            assertEquals("$level must require consent", true, requiresConsent(level))
        }
    }
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*ConsentPolicyTest*'
```
Expected: FAIL — `Unresolved reference: requiresConsent`.

- [ ] **Step 3: Write the predicate**

`ConsentPolicy.kt`:

```kotlin
package com.sidr.launcher.domain.action

/**
 * **The single point where risk becomes a gate** (`DOC-ADL-1`): the same risk gets the same gate
 * regardless of who proposed the action — a rule, the model, the agent, automation, or direct UI.
 *
 * Four sites decided this independently before A1′, in two spellings that coincide only because there
 * are exactly three levels: `risk >= CONFIRM` (agent) and `risk != SAFE` (model, learned memory, UI).
 * Insert a level below `CONFIRM` and three of the four gate it while the agent does not. That is why
 * this function exists, and why it is written against **the lowest level** rather than against a named
 * one: a level added below `SAFE` must gate too.
 *
 * What unifies is the **predicate**, not the outcome. Each caller still answers its own question —
 * a consent checkpoint, a confirm card, an auto-resolve permission, a display state — and merging
 * those four return types would be forcing, not unifying.
 */
fun requiresConsent(risk: ActionRiskLevel): Boolean = risk != ActionRiskLevel.entries.first()
```

- [ ] **Step 4: Converge the four call sites**

- `AgentExecutor.checkpointFor`: `risk >= ActionRiskLevel.CONFIRM ->` becomes `requiresConsent(risk) ->`.
- `RouteCommandUseCase.needsConfirmation`: body becomes
  `requiresConsent(catalog.descriptor(plan.action.id)?.risk ?: ActionRiskLevel.DANGEROUS)` — the
  fail-safe default is now explicit instead of riding on `!= SAFE` being true for `null`.
- `DefaultResolutionPreferencePolicy.decide`: `risk == ActionRiskLevel.SAFE &&` becomes `!requiresConsent(risk) &&`.
- `EvaluateLearnedChoiceDisplayStateUseCase.evaluate`: `if (risk != ActionRiskLevel.SAFE)` becomes `if (requiresConsent(risk))`.

- [ ] **Step 5: Run every suite that touches those four**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --rerun-tasks
```
Expected: PASS, no behaviour change — the four rewrites are equivalent at three levels, which is exactly the point being recorded.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): one predicate decides consent, four call sites stop spelling it themselves"
```

---

### Task 6: The Tier-0 adapter — a second real source

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` (add the second `ToolAdapter`)
- Modify: `app/src/test/java/com/sidr/launcher/agent/ToolWorkerCallSiteGuardTest.kt` (add `Tier0IntentToolWorker.kt`)
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSourceTest.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorkerTest.kt`

**Interfaces:**
- Produces: `Tier0ToolIds.SET_TIMER = ToolId("set_timer")`, `Tier0ToolIds.OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")`; `Tier0IntentToolSource : ToolRegistry`; `Tier0IntentToolWorker : ToolWorker`.

- [ ] **Step 1: Write the failing source test**

```kotlin
@Test
fun `the tier-0 source offers two tools at the system-intent level, both external and safe`() {
    val source = Tier0IntentToolSource()

    assertEquals(
        listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS),
        source.all().map { it.id },
    )
    assertEquals(true, source.all().all { it.level == ToolLevels.SYSTEM_INTENT })
    assertEquals(true, source.all().all { it.effect == ToolEffect.EXTERNAL })
    assertEquals(true, source.all().none { requiresConsent(it.risk) })
}

@Test
fun `set_timer takes a required duration and open_system_settings takes nothing`() {
    val source = Tier0IntentToolSource()

    assertEquals(listOf("duration"), source.find(Tier0ToolIds.SET_TIMER)!!.argSchema.map { it.name })
    assertEquals(emptyList<String>(), source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS)!!.argSchema.map { it.name })
}
```

- [ ] **Step 2: Run and watch it fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest --tests '*Tier0IntentToolSourceTest*'
```
Expected: compilation error — `Unresolved reference: Tier0IntentToolSource`.

- [ ] **Step 3: Write the source**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/** The two Tier-0 ids. Not projections of `ActionIds` — those seven are frozen and contain neither. */
object Tier0ToolIds {
    val SET_TIMER = ToolId("set_timer")
    val OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")
}

/**
 * A1′'s second source: Android system intents that are **not** among the frozen seven `ActionIds`, so
 * they mint their own ids (spec §5.1 — identity C binds projections only).
 *
 * Both tools are `EXTERNAL` and `SAFE`, and that combination is the one `DOC-ILM-2` is actually about:
 * a `CONFIRM` tool is already stopped by the consent gate, so provenance is the only mechanism telling
 * the user where a `SAFE` effect went. Neither skips the OS's own UI — the "prefilled but not sent"
 * shape leaves the final act with the user, which is what makes `SAFE` honest rather than convenient.
 *
 * Permissions: `ACTION_SETTINGS` needs none; `ACTION_SET_TIMER` needs
 * `com.android.alarm.permission.SET_ALARM` (`protectionLevel: normal`, install-time), which must be
 * declared in `app/src/main/AndroidManifest.xml`.
 *
 * [Corrected 2026-09-05. This listing told the implementer to write "Zero new permissions:
 * `ACTION_SET_TIMER` and `ACTION_SETTINGS` both need none", which is false and was faithfully
 * implemented, shipping a tool that could never run. The listing is corrected — rather than merely
 * annotated — because it is an instruction to write code, and anyone re-running this step from the
 * uncorrected text would reproduce the defect. The plan's *narrative* claims elsewhere are left as
 * written; see Step 8's commit message.]
 */
class Tier0IntentToolSource @Inject constructor() : ToolRegistry {

    private val descriptors = listOf(
        ToolDescriptor(
            id = Tier0ToolIds.SET_TIMER,
            argSchema = listOf(
                ActionArg("duration", description = "How long the timer should run, e.g. 10 minutes"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Write the failing worker test**

```kotlin
@Test
fun `an unparseable duration fails closed and never issues an intent`() = runTest {
    val launched = mutableListOf<Intent>()
    val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

    val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "soon")))

    assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
    assertEquals(emptyList<Intent>(), launched)
}

@Test
fun `ten minutes becomes six hundred seconds and the clock UI is not skipped`() = runTest {
    val launched = mutableListOf<Intent>()
    val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

    worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")))

    assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    assertEquals(false, launched.single().getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
}
```

- [ ] **Step 5: Write the worker and its launcher seam**

The seam exists so the tests above need no Robolectric activity, and so the worker has exactly one
place where an intent leaves it. `Tier0IntentToolWorker.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The one place an intent leaves this worker. Injected so the parse can be tested without Android. */
interface IntentLauncher {
    fun launch(intent: Intent)
}

class ContextIntentLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : IntentLauncher {
    override fun launch(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * The `SYSTEM_INTENT` level's worker: two Android intents that are not among the frozen seven
 * `ActionIds`, so they do not travel the `ExecuteActionUseCase` chain and this class is the whole of
 * their execution.
 *
 * **Neither skips the OS's own UI.** `EXTRA_SKIP_UI` stays `false` and settings opens its own screen,
 * so the final act is the user's. That is what makes `SAFE` an honest declaration rather than a
 * convenient one, and it is the "prefilled but not sent" form Master Plan §3.6 `B4` describes.
 */
class Tier0IntentToolWorker @Inject constructor(
    private val launcher: IntentLauncher,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = when (invocation.id) {
        Tier0ToolIds.SET_TIMER -> setTimer(invocation.args["duration"].orEmpty())
        Tier0ToolIds.OPEN_SYSTEM_SETTINGS -> {
            launcher.launch(Intent(Settings.ACTION_SETTINGS))
            ToolResult.Effected()
        }
        // Unreachable in a well-formed graph — the federation routes by the registry this adapter
        // declares. Fail-closed and labelled as such, never named in `CommandFailure` (spec §4.4).
        else -> ToolResult.Failed(CommandFailure.Generic)
    }

    private fun setTimer(duration: String): ToolResult {
        val seconds = parseSeconds(duration) ?: return ToolResult.Failed(CommandFailure.Generic)
        launcher.launch(
            Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
        )
        return ToolResult.Effected()
    }

    /**
     * Leading integer plus an optional unit word. Returns `null` for anything it cannot read, and the
     * caller fails closed on that: an agent that silently starts a zero-length timer is worse than one
     * that declines and says so. Same reasoning as `InvocationValidator.resolve`'s refusal to bind a
     * blank.
     *
     * Unit words are read here rather than in `ToolVocabulary` because they belong to reading the
     * **value**, not to recognising the tool; the vocabulary hands this string over verbatim.
     */
    private fun parseSeconds(raw: String): Int? {
        val text = raw.trim().lowercase()
        val digits = text.takeWhile { it.isDigit() }
        val amount = digits.toIntOrNull() ?: return null
        if (amount <= 0 || digits.length > 4) return null
        val unit = text.drop(digits.length).trim()
        val multiplier = when {
            unit.isEmpty() -> MINUTE
            SECOND_FORMS.any { unit.startsWith(it) } -> 1
            MINUTE_FORMS.any { unit.startsWith(it) } -> MINUTE
            HOUR_FORMS.any { unit.startsWith(it) } -> MINUTE * 60
            else -> return null
        }
        return amount * multiplier
    }

    private companion object {
        const val MINUTE = 60
        val SECOND_FORMS = listOf("sec", "сек", "saniye")
        val MINUTE_FORMS = listOf("min", "мин", "dakika", "dk")
        val HOUR_FORMS = listOf("hour", "час", "saat")
    }
}
```

Bind the seam in `AgentProvidesModule`:

```kotlin
    @Provides
    @Singleton
    fun provideIntentLauncher(impl: ContextIntentLauncher): IntentLauncher = impl
```

And the fake the Step 4 tests use, in the test source set beside `Tier0IntentToolWorkerTest`:

```kotlin
private class FakeIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}
```

> `Tier0IntentToolWorkerTest` reads `Intent` extras, so it runs under Robolectric like the other
> `:data:repository` Android-touching tests. Copy whatever `@RunWith`/`@Config` annotation
> `SystemIntentToolWorkerTest` already carries rather than inventing one.

- [ ] **Step 6: Add the adapter to the graph and the worker to the guard**

`AgentProvidesModule.provideToolFederation` gains its second `ToolAdapter` line (Task 4, Step 1).
`ToolWorkerCallSiteGuardTest`'s expected list gains `Tier0IntentToolWorker.kt`.

- [ ] **Step 7: Run**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :data:repository:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug --rerun-tasks
```
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): a second source — two Tier-0 intents, zero new permissions"
```

> **Historical, deliberately not rewritten (2026-09-05).** "zero new permissions" in the message above
> is false — `ACTION_SET_TIMER` requires `com.android.alarm.permission.SET_ALARM`. This line is left
> exactly as it was because the commit **was made with this text** and stands in git history:
> editing it here would make the plan describe a commit message that never existed, and a document
> that disagrees with immutable primary evidence is a worse failure than one carrying a marked stale
> claim. The correction lives where a reader can act on it — the source KDoc, the spec's §8.1 and F3,
> `CLAUDE.md`, `ai-context/current-status.md` — and is enforced by `ToolPermissionManifestGuardTest`.

---

### Task 7: Identity C — a projected tool's id is derived, not copied

**Files:**
- Modify: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolId.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/tool/ToolIdsTest.kt`

- [ ] **Step 1: Rewrite the test to pin the derivation instead of the copy**

```kotlin
@Test
fun `a projected tool's id is the action's id, not a copy of its spelling`() {
    // Identity C (owner fork F1, 2026-08-29). The two must be equal because one is DERIVED from the
    // other in the adapter, not because someone kept two string literals in step. The derivation lives
    // in `SystemIntentToolSource` on purpose: `domain/tool` keeps no edge to `domain/action`, which is
    // the edge A0 left absent so the A1 fork stayed open.
    assertEquals(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP.value)
    assertEquals(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH.value)
}
```

- [ ] **Step 2: Derive in the adapter**

In `SystemIntentToolSource.project`, replace the `toolId` parameter with a derivation:

```kotlin
    private fun project(
        actionId: ActionId,
        outputSchema: List<ActionArg> = emptyList(),
    ): ToolDescriptor? {
        val descriptor = catalog.descriptor(actionId) ?: return null
        return ToolDescriptor(
            // Identity C: the tool IS the action, so its id is the action's id rather than a second
            // spelling of it kept in step by a test. Tools that are not projections mint their own.
            id = ToolId(actionId.value),
            …
        )
    }
```
and update the two `project(...)` calls to drop the now-derived argument.

- [ ] **Step 3: Re-point `ToolIds`' KDoc**

`ToolIds`' constants stay (`TemplatePlanner` and the workers reference them) but the KDoc's claim that
they are "repeated rather than imported" becomes false in spirit: rewrite it to say the strings are now
**pinned to** `ActionIds` by `ToolIdsTest` and produced by derivation in the adapter, while the absence
of an import edge from `domain/tool` to `domain/action` is what still keeps the two vocabularies
separable.

- [ ] **Step 4: Run and commit**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest :data:repository:testDebugUnitTest --rerun-tasks
git add -A && git commit -m "feat(agentic-5/A1'): a projected tool's id is derived from the action, not copied"
```

---

### Task 8: `ToolMatchPlanner` — one generic arm, no taxonomy

**Files:**
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolMatchPlanner.kt`
- Create: `data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt`
- Create: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/CompositePlanner.kt`
- Modify: `app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt` (`providePlanner`)
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/ToolMatchPlannerTest.kt`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CompositePlannerTest.kt`

**Interfaces:**
- Consumes: `Planner`, `PlanningResult`, `ExecutionPlan`, `PlanStep`, `StepPrecondition.None`, `StepRationale.GOAL_DIRECT`, `ToolRegistry`, `ArgSource.Literal`.
- Produces: `class CompositePlanner(private val planners: List<Planner>) : Planner`; `class ToolMatchPlanner(private val vocabulary: ToolVocabulary) : Planner`; `ToolVocabulary.match(text: String): ToolMatch?` returning `ToolMatch(id: ToolId, args: Map<String, String>)`.

- [ ] **Step 1: Write the failing composite test**

```kotlin
@Test
fun `the composite returns the first plan and stops asking`() = runTest {
    val asked = mutableListOf<String>()
    val silent = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
            asked += "silent"; return PlanningResult.NoPlan
        }
    }
    val speaking = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
            asked += "speaking"; return PlanningResult.Planned(ExecutionPlan(emptyList()))
        }
    }
    val never = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
            asked += "never"; return PlanningResult.NoPlan
        }
    }

    val result = CompositePlanner(listOf(silent, speaking, never))
        .plan(AgentGoal("x", GoalShape.Free("x")), FakeToolRegistry.withA0Tools())

    assertEquals(true, result is PlanningResult.Planned)
    assertEquals(listOf("silent", "speaking"), asked)
}
```

- [ ] **Step 2: Write the failing planner test**

```kotlin
@Test
fun `a matched tool becomes a one-step plan whose risk comes from the registry`() = runTest {
    val planner = ToolMatchPlanner(ToolVocabulary())
    val registry = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker)),
    ).registry

    val result = planner.plan(AgentGoal("set a timer for 10 minutes", GoalShape.Free("set a timer for 10 minutes")), registry)

    val plan = (result as PlanningResult.Planned).plan
    assertEquals(1, plan.steps.size)
    assertEquals(Tier0ToolIds.SET_TIMER, plan.steps[0].invocation.id)
    assertEquals(ArgSource.Literal("10 minutes"), plan.steps[0].invocation.args["duration"])
    assertEquals(ActionRiskLevel.SAFE, plan.steps[0].risk)
    assertEquals(StepRationale.GOAL_DIRECT, plan.steps[0].rationale)
    assertEquals(StepPrecondition.None, plan.steps[0].precondition)
}

@Test
fun `an unmatched goal is NoPlan, so routing falls through exactly as before`() = runTest {
    val planner = ToolMatchPlanner(ToolVocabulary())
    val registry = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker)),
    ).registry

    assertEquals(
        PlanningResult.NoPlan,
        planner.plan(AgentGoal("what is the weather", GoalShape.Free("what is the weather")), registry),
    )
}

@Test
fun `a tool the registry does not have is NoPlan even when the words match`() = runTest {
    val planner = ToolMatchPlanner(ToolVocabulary())
    val emptyRegistry = FakeToolRegistry(emptyList())

    assertEquals(
        PlanningResult.NoPlan,
        planner.plan(AgentGoal("set a timer for 10 minutes", GoalShape.Free("set a timer for 10 minutes")), emptyRegistry),
    )
}

@Test
fun `a required argument the text does not supply is NoPlan, never a blank`() = runTest {
    val planner = ToolMatchPlanner(ToolVocabulary())
    val registry = ToolFederation(
        listOf(ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker)),
    ).registry

    // "Set a timer" with no duration: an agent that silently starts a zero-length timer is worse than
    // one that declines. Same reasoning as `InvocationValidator.resolve`'s UNRESOLVED_ARG_SOURCE.
    assertEquals(
        PlanningResult.NoPlan,
        planner.plan(AgentGoal("set a timer", GoalShape.Free("set a timer")), registry),
    )
}
```

- [ ] **Step 3: Write `CompositePlanner`**

`CompositePlanner` (in `:domain`, pure):

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * Asks each planner in order and returns the first plan. **Not a fallback chain in disguise:** each
 * planner owns a disjoint set of goal shapes today (`TemplatePlanner` — `AppNotInstalled`,
 * `ToolMatchPlanner` — `Free`), so the order is a tie-break that never fires rather than a priority.
 * It is a list rather than a `when` because that is the shape that stops growing when A4′ adds the
 * model planner behind this same port.
 */
class CompositePlanner(private val planners: List<Planner>) : Planner {
    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        planners.forEach { planner ->
            val result = planner.plan(goal, registry)
            if (result is PlanningResult.Planned) return result
        }
        return PlanningResult.NoPlan
    }
}
```


- [ ] **Step 4: Write `ToolVocabulary`**

`data/repository/src/main/java/com/sidr/launcher/data/repository/agent/ToolVocabulary.kt`:

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId
import javax.inject.Inject

/** One recognised tool and the arguments the text supplied for it. */
data class ToolMatch(val id: ToolId, val args: Map<String, String>)

/**
 * Deterministic, localized recognition of a tool from raw text — FastPath for tools.
 *
 * It lives in `:data:repository` beside `RuleBasedIntentMatcher` for the reason the hard rule gives:
 * `:domain` is `commonMain` and must stay free of locale tables and of text parsing, so the JVM
 * consumer is unaffected by anything here.
 *
 * **It recognises, it does not understand.** A miss is not an error and never becomes "unknown
 * command" — `ToolMatchPlanner` answers `NoPlan` and routing falls through to the model exactly as it
 * did before. That is the doctrine's rule 1: FastPath is a latency optimization, not a filter.
 *
 * Locale shape mirrors `RuleBasedIntentMatcher.VerbForms`: `en`/`ru` are prefix languages, `tr` is a
 * suffix language. A guard test asserts every entry carries a non-empty form for all three locales, so
 * a tool added with English triggers only is red rather than quietly monolingual.
 */
class ToolVocabulary @Inject constructor() {

    /**
     * One tool's triggers. [argName] is `null` for a zero-argument tool; when it is set, whatever
     * follows (or precedes, in `tr`) the trigger is handed over **verbatim** as that argument's value.
     * Reading the value is the worker's business, not the vocabulary's.
     */
    data class Entry(
        val id: ToolId,
        val prefixByLocale: Map<String, Set<String>>,
        val suffixByLocale: Map<String, Set<String>> = emptyMap(),
        val argName: String? = null,
    )

    /** Public so `ToolVocabularyLocaleGuardTest` can read it directly, as `FastPathLocaleGuardTest` does. */
    val entries: List<Entry> = listOf(
        Entry(
            id = Tier0ToolIds.SET_TIMER,
            prefixByLocale = mapOf(
                "en" to setOf("set a timer for", "set timer for", "timer for"),
                "ru" to setOf("поставь таймер на", "таймер на"),
            ),
            suffixByLocale = mapOf("tr" to setOf("zamanlayıcı kur", "sayaç kur")),
            argName = "duration",
        ),
        Entry(
            id = Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            prefixByLocale = mapOf(
                "en" to setOf("open system settings", "system settings", "android settings"),
                "ru" to setOf("открой настройки системы", "системные настройки", "настройки андроид"),
            ),
            suffixByLocale = mapOf("tr" to setOf("sistem ayarlarını aç", "android ayarlarını aç")),
        ),
    )

    /**
     * The **unambiguous** match, or `null`. Two entries matching the same text is a vocabulary defect,
     * not a ranking problem, so it yields `null` rather than a guess — the same fail-closed choice the
     * federation makes for a colliding id.
     */
    fun match(text: String): ToolMatch? {
        val normalized = text.trim().lowercase()
        if (normalized.isEmpty()) return null

        val hits = entries.mapNotNull { entry -> entry.matchIn(normalized) }
        return hits.singleOrNull()
    }

    private fun Entry.matchIn(text: String): ToolMatch? {
        val prefix = prefixByLocale.values.flatten()
            .filter { text.startsWith(it) }
            .maxByOrNull { it.length }
        if (prefix != null) return toMatch(text.removePrefix(prefix).trim())

        val suffix = suffixByLocale.values.flatten()
            .filter { text.endsWith(it) }
            .maxByOrNull { it.length }
        if (suffix != null) return toMatch(text.removeSuffix(suffix).trim())

        return null
    }

    /**
     * A required argument the text did not supply is **no match at all**, not a match with a blank.
     * The planner would refuse it one step later anyway; refusing here keeps the reason readable.
     */
    private fun Entry.toMatch(remainder: String): ToolMatch? = when {
        argName == null -> ToolMatch(id, emptyMap())
        remainder.isBlank() -> null
        else -> ToolMatch(id, mapOf(argName to remainder))
    }
}
```

- [ ] **Step 5: Write `ToolMatchPlanner`**

```kotlin
package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * **One generic arm, not a taxonomy** (spec §8.4). A recognised tool becomes a one-step plan, whatever
 * the tool is — so `A1″`'s twelve tools cost twelve vocabulary entries and **zero** planner branches.
 * The rejected alternative was one `GoalShape` value and one arm per tool, which is linear per tool
 * and contradicts in code the claim this whole block exists to establish.
 *
 * `GoalShape` gains nothing: this plans the `Free` value A0.5 already added, which until now answered
 * `NoPlan` on Android. `StepRationale` gains nothing either — a one-step plan is `GOAL_DIRECT`, and
 * the surface names the tool rather than assuming a launch.
 *
 * **Risk comes from the registry, never from here.** The planner records what the registry declares at
 * plan time, and `AgentExecutor` gates on `maxOf(plan, registry)` (A0 finding F2), so a tool that
 * becomes riskier between planning and execution still stops.
 */
class ToolMatchPlanner @Inject constructor(
    private val vocabulary: ToolVocabulary,
) : Planner {

    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        // Exhaustive with no `else`: a third GoalShape must make a deliberate decision here.
        val text = when (val shape = goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> return PlanningResult.NoPlan
        }

        val match = vocabulary.match(text) ?: return PlanningResult.NoPlan
        val descriptor = registry.find(match.id) ?: return PlanningResult.NoPlan

        // Every required argument must have a non-blank value. Nothing is defaulted or substituted:
        // an agent that silently runs a half-filled tool is worse than one that declines.
        val required = descriptor.argSchema.filter { it.required }.map { it.name }
        if (required.any { match.args[it].isNullOrBlank() }) return PlanningResult.NoPlan

        // An argument the schema does not declare is dropped rather than passed: `InvocationValidator`
        // would reject the whole invocation as UNDECLARED_ARG, and a plan that cannot validate is
        // worse than no plan.
        val args = match.args
            .filterKeys { key -> descriptor.argSchema.any { it.name == key } }
            .mapValues { (_, value) -> ArgSource.Literal(value) }

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(descriptor.id, args),
                        risk = descriptor.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }
}
```

The `NoopWorker` the Step 2 tests build their federation with, beside `ToolMatchPlannerTest`:

```kotlin
private object NoopWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
}
```

- [ ] **Step 6: Write the locale guard for the new vocabulary**

`ToolVocabularyLocaleGuardTest`, modelled on `FastPathLocaleGuardTest`:

```kotlin
@Test
fun `every tool entry carries a non-empty trigger for en, ru and tr`() {
    ToolVocabulary().entries.forEach { entry ->
        val locales = entry.prefixByLocale.keys + entry.suffixByLocale.keys
        assertEquals(
            "tool ${entry.id.value} is missing a locale: a tool recognised only in English is a " +
                "capability the other two locales silently do not have",
            setOf("en", "ru", "tr"),
            locales,
        )
        (entry.prefixByLocale + entry.suffixByLocale).forEach { (locale, forms) ->
            assertEquals("${entry.id.value}/$locale has no forms", true, forms.isNotEmpty())
        }
    }
}
```

- [ ] **Step 7: Compose the planners in the graph**

```kotlin
    /**
     * A0 bound the deterministic template planner alone. A1′ composes it with the tool matcher so a
     * registered tool is reachable **without a goal shape of its own** — the alternative was one
     * `GoalShape` value and one planner arm per tool, which is linear per tool and contradicts the
     * federation's whole claim. A4′ adds the model planner to this same list.
     */
    @Provides
    @Singleton
    fun providePlanner(vocabulary: ToolVocabulary): Planner =
        CompositePlanner(listOf(TemplatePlanner(), ToolMatchPlanner(vocabulary)))
```

- [ ] **Step 8: Run and commit**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest :data:repository:testDebugUnitTest --rerun-tasks
git add -A && git commit -m "feat(agentic-5/A1'): a registered tool is plannable without a goal shape of its own"
```

---

### Task 9: Routing step 2b, and the parity it must not break

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCase.kt` (insert after line 106)
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/ai/router/RouteCommandUseCaseTest.kt`

- [ ] **Step 1: Write the failing tests — the parity ones first**

Append to `RouteCommandUseCaseTest`. It already has the helper this needs:
`useCase(localOnly: Boolean = false, agentPlanner: Planner = TemplatePlanner())` at line 116, the
`provider` / `connectivity` fakes at lines 75 and 109, and `driveUnknown()` at line 136 to make
FastPath undecided. Use those; do not build a second harness.

```kotlin
    /** A planner that recognises nothing — the state of the world for any text with no tool behind it. */
    private val neverPlans = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry) = PlanningResult.NoPlan
    }

    /** A planner that plans one SAFE step, standing in for a matched Tier-0 tool. */
    private val plansOneStep = object : Planner {
        override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
            val tool = registry.all().firstOrNull() ?: return PlanningResult.NoPlan
            return PlanningResult.Planned(
                ExecutionPlan(
                    listOf(
                        PlanStep(
                            index = 0,
                            invocation = ToolInvocation(tool.id, mapOf("query" to ArgSource.Literal("x"))),
                            risk = tool.risk,
                            precondition = StepPrecondition.None,
                            rationale = StepRationale.GOAL_DIRECT,
                        ),
                    ),
                ),
            )
        }
    }

    @Test
    fun `local-only with no matching tool behaves exactly as before`() = runTest {
        // The three local states are the acceptance-sensitive surface. A planner that plans nothing
        // must leave every one of them byte-for-byte identical, or step 2b is a behaviour change
        // wearing a capability's clothes.
        driveUnknown()

        val outcome = useCase(localOnly = true, agentPlanner = neverPlans).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingLocalOnly), outcome)
    }

    @Test
    fun `no provider with no matching tool still says no provider`() = runTest {
        driveUnknown()
        provider.clearActiveConfig()
        connectivity.online = true

        val outcome = useCase(agentPlanner = neverPlans).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsProvider), outcome)
    }

    @Test
    fun `offline with no matching tool still says needs network`() = runTest {
        driveUnknown()
        connectivity.online = false

        val outcome = useCase(agentPlanner = neverPlans).route("do a barrel roll")

        assertEquals(CommandOutcome.Message(CommandMessage.UnderstandingNeedsNetwork), outcome)
    }

    @Test
    fun `a matching tool starts an agent session even in local-only mode`() = runTest {
        // DOC-ADL-3 as amended 2026-08-22: deterministic plan replay is part of the local path and MAY
        // change an outcome FastPath decided. What stays invariant is that no model was consulted and
        // nothing left the device — `AgentEgressSentinelGuardTest` holds that half, and this test's
        // planner is `plansOneStep`, which is not a model.
        driveUnknown()

        val outcome = useCase(localOnly = true, agentPlanner = plansOneStep).route("do a barrel roll")

        assertEquals(true, outcome is CommandOutcome.AgentSessionStarted)
    }

    @Test
    fun `a decided FastPath outcome is never handed to the tool planner`() = runTest {
        // Step 2b is keyed on `isUndecided()`, so a confident FastPath hit keeps its byte-for-byte
        // return and never becomes an agent session.
        driveConfidentShowApps()

        val outcome = useCase(agentPlanner = plansOneStep).route("show apps")

        assertEquals(false, outcome is CommandOutcome.AgentSessionStarted)
    }
```

- [ ] **Step 2: Run and watch the last two fail**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --tests '*RouteCommandUseCaseTest*'
```
Expected: the three parity tests PASS already; the two new-capability tests FAIL.

- [ ] **Step 3: Insert step 2b**

After the existing `NoAppFound` block (line 106) and **above** the `localOnlyMode` check:

```kotlin
        // (2b) FastPath could not decide, but a registered tool may match the text deterministically.
        // This use case learns NOTHING about tools: it offers the raw goal and the planner answers.
        // `NoPlan` — the case where nothing matches — leaves `ruleOutcome` untouched and the chain
        // below runs exactly as it did before, which is what the three parity tests pin.
        //
        // It sits above the localOnlyMode check for the same reason branch (2) does: the planner here
        // is deterministic and offline, so no model is consulted and nothing leaves the device. That
        // is precisely what DOC-ADL-3 permits since its 2026-08-22 amendment.
        if (ruleOutcome.isUndecided()) {
            val trimmed = rawInput.trim()
            val started = startAgentSession.start(AgentGoal(text = trimmed, shape = GoalShape.Free(trimmed)))
            if (started is OperationResult.Success && started.value != null) {
                return CommandOutcome.AgentSessionStarted(started.value)
            }
        }
```

- [ ] **Step 4: Extend the class KDoc's numbered chain**

Add step 2b to the KDoc list and to the "Step 2 sits above step 3" paragraph: the mutual-exclusion
property survives because 2b reports **none** of the three causes — it either returns an agent session
or falls through untouched. The existing test
`all three causes at once — only the outermost is reported` must stay green **unmodified**; if it needs
editing, the property broke and the branch is in the wrong place.

- [ ] **Step 5: Run and commit**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest --rerun-tasks
git add -A && git commit -m "feat(agentic-5/A1'): a FastPath miss may reach a registered tool, and the three local states are unmoved"
```

---

### Task 10: The surface — a step names its tool, and an EXTERNAL tool shows where it went

**Files:**
- Modify: `feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentation.kt`
- Modify: `feature/launcher/src/main/res/values/strings.xml`, `values-ru/strings.xml`, `values-tr/strings.xml`
- Modify: `app/src/test/java/com/sidr/launcher/i18n/DomainIdentifierLeakGuardTest.kt` (KDoc: `ToolTier` → `ToolLevel`)
- Test: `feature/launcher/src/test/java/com/sidr/launcher/feature/launcher/agent/AgentSessionPresentationTest.kt`

- [ ] **Step 1: Write the failing tests**

Append to `AgentSessionPresentationTest`:

```kotlin
    @Test
    fun `a step line names the tool rather than assuming a launch`() {
        val label = toolLabelFor(Tier0ToolIds.SET_TIMER)

        // Before A1' every GOAL_DIRECT step rendered `launcher_agent_step_launch` ("Open %1$s"), so a
        // timer step would have read "Open set a timer for 10 minutes". The rationale says WHY the step
        // is in the plan; the tool says WHAT it does, and only the second belongs in this line.
        assertEquals(R.string.launcher_agent_step_timer, label)
    }

    @Test
    fun `an EXTERNAL tool shows provenance and a LOCAL one shows none`() {
        assertEquals(
            R.string.launcher_tool_level_system_intent,
            provenanceLabelFor(ToolLevels.SYSTEM_INTENT, ToolEffect.EXTERNAL),
        )
        assertNull(provenanceLabelFor(ToolLevels.SANDBOX, ToolEffect.LOCAL))
    }

    @Test
    fun `a level with no string resource falls back to a generic label, never to the raw value`() {
        // ToolLevel is an OPEN value class, so an unknown level is reachable by construction — that is
        // the whole point of the type. The surface must never print "mcp" at a user: invisible in
        // English, untranslated in ru/tr, which is exactly the bug DomainIdentifierLeakGuardTest exists
        // for. Fail closed to a generic label.
        assertEquals(
            R.string.launcher_tool_level_unknown,
            provenanceLabelFor(ToolLevel("mcp"), ToolEffect.EXTERNAL),
        )
    }
```

- [ ] **Step 2: Implement the two mappings in the feature layer**

In `AgentSessionPresentation.kt`, add — and note both return `@StringRes Int`, not `String`, so they
are unit-testable without a Compose runtime, which is why the tests above assert resource ids:

```kotlin
/**
 * `ToolId -> string resource`. This is the mapping `ToolDescriptor`'s own KDoc prescribes ("carries no
 * user-facing copy: the surface maps `id` to a string resource in the feature layer"), and it is what
 * lets a one-step plan for any tool render correctly without `StepRationale` growing a value per tool.
 *
 * An unregistered id falls back to the generic step line rather than throwing: this is the UI seam,
 * and the hard rule forbids exceptions here.
 */
@StringRes
internal fun toolLabelFor(id: ToolId): Int = when (id.value) {
    ToolIds.LAUNCH_APP.value -> R.string.launcher_agent_step_launch
    ToolIds.PLAY_STORE_SEARCH.value -> R.string.launcher_agent_step_store
    Tier0ToolIds.SET_TIMER.value -> R.string.launcher_agent_step_timer
    Tier0ToolIds.OPEN_SYSTEM_SETTINGS.value -> R.string.launcher_agent_step_settings
    else -> R.string.launcher_agent_step_generic
}

/**
 * Provenance for one step, or `null` when there is nothing to disclose.
 *
 * `DOC-ILM-2` is about the `EXTERNAL` case, and the interesting half of it is an `EXTERNAL` tool that
 * is also `SAFE`: a `CONFIRM` tool is already stopped by the consent gate, so for a `SAFE` one this
 * line is the only thing telling the user the effect left the launcher.
 *
 * **The raw `ToolLevel.value` never reaches a sink.** `ToolLevel` is an open value class, so an
 * unmapped level is reachable by construction and falls back to a generic label — the alternative is
 * printing a domain identifier at a user, which is precisely what `DomainIdentifierLeakGuardTest` was
 * written after.
 */
@StringRes
internal fun provenanceLabelFor(level: ToolLevel, effect: ToolEffect): Int? {
    if (effect != ToolEffect.EXTERNAL) return null
    return when (level.value) {
        ToolLevels.IN_APP.value -> R.string.launcher_tool_level_in_app
        ToolLevels.SYSTEM_INTENT.value -> R.string.launcher_tool_level_system_intent
        else -> R.string.launcher_tool_level_unknown
    }
}
```

Then change `StepRationale.label` to take the step's tool and stop hardcoding the launch string:

```kotlin
@Composable
@ReadOnlyComposable
internal fun PlanStep.line(subject: String): String =
    sidrString(toolLabelFor(invocation.id), subject)
```
Keep `StepRationale.label` if another caller still needs "why"; **do not** add a `StepRationale` value
(spec §10.3).

- [ ] **Step 3: Add the strings in all three locales, in this commit**

`values/strings.xml`:

```xml
    <string name="launcher_agent_step_timer">Set a timer for %1$s</string>
    <string name="launcher_agent_step_settings">Open Android settings</string>
    <string name="launcher_agent_step_generic">Run this step for %1$s</string>
    <string name="launcher_tool_level_in_app">SIDR · EXTERNAL</string>
    <string name="launcher_tool_level_system_intent">SYSTEM INTENT · EXTERNAL</string>
    <string name="launcher_tool_level_unknown">ANOTHER APP · EXTERNAL</string>
```

`values-ru/strings.xml`:

```xml
    <string name="launcher_agent_step_timer">Поставить таймер на %1$s</string>
    <string name="launcher_agent_step_settings">Открыть настройки Android</string>
    <string name="launcher_agent_step_generic">Выполнить шаг для %1$s</string>
    <string name="launcher_tool_level_in_app">SIDR · ВНЕ ПРИЛОЖЕНИЯ</string>
    <string name="launcher_tool_level_system_intent">СИСТЕМА · ВНЕ ПРИЛОЖЕНИЯ</string>
    <string name="launcher_tool_level_unknown">ДРУГОЕ ПРИЛОЖЕНИЕ · ВНЕ ПРИЛОЖЕНИЯ</string>
```

`values-tr/strings.xml`:

```xml
    <string name="launcher_agent_step_timer">%1$s için zamanlayıcı kur</string>
    <string name="launcher_agent_step_settings">Android ayarlarını aç</string>
    <string name="launcher_agent_step_generic">%1$s için bu adımı çalıştır</string>
    <string name="launcher_tool_level_in_app">SIDR · UYGULAMA DIŞI</string>
    <string name="launcher_tool_level_system_intent">SİSTEM · UYGULAMA DIŞI</string>
    <string name="launcher_tool_level_unknown">BAŞKA UYGULAMA · UYGULAMA DIŞI</string>
```

> These six keys are **not Class B**, so the owner-reviewed locale signature is neither touched nor
> re-signed. Confirm that against `checkOwnerReviewedLocaleStrings`' definition of Class B **before**
> committing; if any of them is Class B, the owner reads the text and signs — the agent never writes the
> digest itself.

- [ ] **Step 4: Re-point the leak barrier's forecast**

`DomainIdentifierLeakGuardTest`'s KDoc names «`A1`'s planned `ToolId`/`ToolTier`/`ToolEffect`/
`ActionCategory` vocabulary». The type A1′ actually introduced is `ToolLevel`, not `ToolTier` — the
doctrine matrix is the governing text and it says «уровень инструмента» (`DOC-HMA-2`). Update the KDoc
to `ToolLevel` and add one sentence: the risk it forecast is now live, and `provenanceLabelFor` is
where it is answered.

- [ ] **Step 5: Run the i18n guards and the feature suite**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :feature:launcher:testDebugUnitTest :app:testDebugUnitTest --rerun-tasks
```
Expected: PASS, including `LocaleCompletenessGuardTest`, `HardcodedUiTextGuardTest`,
`StringSeamGuardTest` and `DomainIdentifierLeakGuardTest`.

- [ ] **Step 6: Commit**

```bash
git add -A && git commit -m "feat(agentic-5/A1'): a step says which tool it is, and an external one says where it goes"
```

---

### Task 11: `DoctrineGuardTest` — ethics in CI

**Files:**
- Create: `app/src/test/java/com/sidr/launcher/doctrine/DoctrineGuardTest.kt`
- Modify: `docs/governing/sidr-doctrine-matrix-v1.0.md` (rows `DOC-ILM-2`, `DOC-ADL-1`, `DOC-ADL-3`)

**Interfaces:**
- Consumes: `ToolFederation`, `ToolAdapter`, `ToolLevels`, `SystemIntentToolSource`, `Tier0IntentToolSource`, `DefaultActionCatalog`.

- [ ] **Step 1: Write the three failing assertions**

```kotlin
package com.sidr.launcher.doctrine

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * The block's doctrine test. Three properties, one per rule A1′ closes, and each is mutation-proved
 * separately in Step 3 — a green run of a new guard proves nothing.
 *
 * It deliberately builds the **production** federation rather than a fixture: a guard that checks a
 * fixture checks the fixture.
 */
class DoctrineGuardTest {

    private val repoRoot = File("..")

    /**
     * Defined here rather than reused: `ToolMatchPlannerTest`'s `NoopWorker` is private to
     * `:data:repository`'s test source set, and a guard that borrows across modules is a guard that
     * breaks when someone tidies the other module.
     */
    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
    }

    private fun productionFederation() = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(), NoopWorker),
        ),
    )

    /** `DOC-ILM-2` half one — no tool is silently shadowed, so provenance names a real supplier. */
    @Test
    fun `no two registered tools share an id`() {
        val ids = productionFederation().registry.all().map { it.id.value }

        assertEquals(
            "A shadowed tool is a capability that exists and can never run. Duplicates: " +
                ids.groupingBy { it }.eachCount().filterValues { it > 1 },
            ids.distinct(),
            ids,
        )
    }

    /** `DOC-ILM-2` half two — every tool declares where it came from and whether it leaves the device. */
    @Test
    fun `every registered tool carries provenance and an EXTERNAL one is renderable`() {
        productionFederation().registry.all().forEach { descriptor ->
            assertEquals("${descriptor.id.value} has a blank level", true, descriptor.level.value.isNotBlank())
        }
    }

    /**
     * `DOC-ADL-3`, the A1′ half. `AgentVocabularyGuardTest` holds mechanically that `domain/agent` and
     * `domain/tool` cannot NAME a network type; beyond those two packages "nothing leaves the device"
     * was a fact about the single source, not a check. Federation is where that stops being enough:
     * every registered adapter is scanned for the same forbidden names.
     *
     * Honest about its own reach: this is a textual scan of the adapter files, so a source reaching the
     * network through a helper in another file is outside it. That direction is a false GREEN and is
     * why the adapter set itself is pinned below — a new source must be added deliberately, and an ADR
     * is where its egress is argued.
     */
    @Test
    fun `no registered adapter names a network type`() {
        val forbidden = listOf("HttpClient", "OkHttp", "Retrofit", "java.net", "Ktor", "URLConnection")
        val adapterFiles = listOf(
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolSource.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/SystemIntentToolWorker.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt",
            "data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolWorker.kt",
        ).map { File(repoRoot, it) }

        adapterFiles.forEach { file ->
            assertEquals("missing adapter file: $file", true, file.isFile)
            val text = file.readText()
            forbidden.forEach { term ->
                assertEquals("${file.name} names $term — a tool source may not reach the network", false, text.contains(term))
            }
        }
    }

    /**
     * The adapter set is a **declared** list, so a source added without an ADR turns this red. This is
     * the change-control §5 line "give a tool a path to the world outside ToolRegistry/ToolExecutor"
     * made mechanical at the composition root.
     */
    @Test
    fun `the composition root registers exactly the declared adapters`() {
        val module = File(repoRoot, "app/src/main/java/com/sidr/launcher/di/AgentProvidesModule.kt").readText()
        val declared = Regex("""ToolAdapter\(\s*(ToolLevels\.\w+)""").findAll(module).map { it.groupValues[1] }.toList()

        assertEquals(
            "A new adapter is a new path to the world and needs an ADR, not a line. Found: $declared",
            listOf("ToolLevels.IN_APP", "ToolLevels.SYSTEM_INTENT"),
            declared,
        )
    }
}
```

- [ ] **Step 2: Run it green**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*DoctrineGuardTest*' --rerun-tasks
```

- [ ] **Step 3: Mutation-prove each assertion separately**

Three mutations, each planted and reverted in **one** shell invocation, each asserting the edit landed
before the suite runs:

```bash
cd /home/Suleiman/Sidr-launcher
SRC=data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt
cp $SRC /tmp/t0.bak
trap 'cp /tmp/t0.bak $SRC; rm -f /tmp/t0.bak' EXIT
# (a) collision: make a Tier-0 tool claim a projected id
python3 - <<'PY'
import os
p=os.environ.get("SRC","data/repository/src/main/java/com/sidr/launcher/data/repository/agent/Tier0IntentToolSource.kt")
s=open(p).read(); old='val SET_TIMER = ToolId("set_timer")'; new='val SET_TIMER = ToolId("launch_app")'
assert s.count(old)==1, f"landing check failed: {s.count(old)}"
open(p,"w").write(s.replace(old,new)); print("mutation (a) landed")
PY
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests '*DoctrineGuardTest*' --rerun-tasks; echo "EXIT=$?"
```
Expected **RED** on `no two registered tools share an id`.

Repeat for (b) an `import io.ktor.client.HttpClient` added to `Tier0IntentToolSource` → RED on
`no registered adapter names a network type`; and (c) a third `ToolAdapter(ToolLevels.SANDBOX, …)` line
added to `AgentProvidesModule` → RED on `the composition root registers exactly the declared adapters`.

- [ ] **Step 4: Fill the three matrix rows**

`DOC-ILM-2` third column: `DoctrineGuardTest` · `AgentSessionPresentationTest` (the provenance actually
reaches the surface), **and its verification type changes `arch-guard` → `unit`** with its own §6 journal
line — change-control §5, declared in spec §6.3 before code.
`DOC-ADL-1`: `ConsentPolicyTest` · `AgentExecutorTest` · `RouteCommandUseCaseTest`.
`DOC-ADL-3`: append `DoctrineGuardTest` to the existing cell and strike the «долг A1′» clause for the
federation half only — the rest of that cell stands.

- [ ] **Step 5: Verify the tree and commit**

```bash
git status --porcelain --untracked-files=all
git add -A && git commit -m "test(agentic-5/A1'): ethics in CI — collisions, provenance, the declared adapter set, no egress from a source"
```

---

### Task 12: `FakeToolRegistry` parity

**Files:**
- Modify: `core/testing/src/main/java/com/sidr/launcher/core/testing/FakeToolRegistry.kt`
- Test: `data/repository/src/test/java/com/sidr/launcher/data/repository/agent/SystemIntentToolContractTest.kt`

- [ ] **Step 1:** add a test asserting the fake's `withA0Tools()` descriptors equal `SystemIntentToolSource(DefaultActionCatalog()).all()` field-for-field. CLAUDE.md lists this mirror as owned by no block; federation multiplies it, so this block pays.
- [ ] **Step 2:** run, then commit: `test(agentic-5/A1'): the fake registry is pinned to the source it mirrors`

---

### Task 13: The gate, the documents, and the device checklist

- [ ] **Step 1: Full gate, with `--rerun-tasks` so no suite reports UP-TO-DATE**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test --rerun-tasks
echo "EXIT=$?"
```
Read the counts from the JUnit XML under each module's `build/test-results/`, not from the console. Expected: ≥ 1219 tests, 0 failures.

- [ ] **Step 2: `:core:ui:verifyRoborazziDebug`** if `core/ui` was touched at all.

- [ ] **Step 3: Write the ADR** in `ai-context/decisions.md`, house format. It must carry: the seven owner forks; the six hostile-read findings about the A0.5 ADR plus the `args_json` finding about `§HANDOFF`; the amendment that produced §8.4; the mutation table; and the residual limitations named rather than implied absent.

- [ ] **Step 4: Sync the documents.**
`CLAUDE.md` (shipped surface, contract→owner table, known debt), `ai-context/current-status.md`, the
three doctrine rows plus the `DOC-ILM-2` verification-type change-control entry, Master Plan §3.2/§3.6
(add `A1″` and point `B2`/`B3`/`B4` at it), restart plan Этап 5 marked ✅ with a result paragraph, and
`§HANDOFF` rewritten for `A1″` — including the corrected `args_json` sentence.

- [ ] **Step 5: Write the device-acceptance checklist** at `docs/superpowers/plans/2026-08-29-a1-device-acceptance.md`, split the way the A0 re-check file is: **A** what the owner must look at, **B** no-regression re-runs, **C** what only the agent can show instrumentally and which therefore clears nothing.

- [ ] **Step 6: Propose the closing commit to the owner.** The agent commits; the agent never pushes.
