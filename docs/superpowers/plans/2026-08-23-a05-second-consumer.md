# A0.5 — Second Consumer of the Portable Core: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the `jvm()` target of `:domain` its first product consumer — a headless console harness
that carries one goal through `goal → plan → gate → tool → observe → tool → result → trace` with no
Android anywhere in its graph — and record, in evidence, what the core forces a non-Android consumer
to say.

**Architecture:** A new `kotlin.jvm` module `:consumer:jvm` depending only on `:domain`. It brings its
own `Planner`, `ToolRegistry`, `ToolExecutor`, `AgentSessionStore` and `AgentSessionIdFactory`, and
drives the **unchanged** `AgentExecutor` / `RunAgentSessionUseCase` / `ResolveConsentUseCase`. Exactly
two edits land in `commonMain` (`GoalShape.Free` and a `TemplatePlanner` arm). Every other
launcher-shaped contract the consumer collides with is **recorded with an address, not fixed**.

**Tech Stack:** Kotlin 2.4.10, Gradle 9.5.0, JDK 17 (Temurin), kotlinx-serialization-json 1.7.3,
kotlinx-coroutines 1.9.0, JUnit4.

**Spec:** [docs/superpowers/specs/2026-08-23-a05-second-consumer-design.md](../specs/2026-08-23-a05-second-consumer-design.md)
(committed `5e98497`, amended `ec8eca1`, re-anchored to `a7f4755` on 2026-08-23). The plan argues from
the spec; read both.

> **Re-anchored 2026-08-23 (evening) after the A0 review round (`a7f4755`).** A cross-cutting review of
> the closed A0 block found nine defects and fixed eight, **inside the `commonMain` engine this module
> is the second consumer of** (ADR «2026-08-23 — Сквозное ревью блока A0»). Nothing in this plan's
> architecture changes; four things in its detail do, and each is marked at the task that owns it:
>
> - **Task 7** — `checkpointFor` now branches on `maxOf(PlanStep.risk, registry.risk)`. `FilePlanner`
>   copies risk out of the registry, so the two agree and the hit path is unchanged; the assertion's
>   wording is corrected, and one new test pins the agreement instead of assuming it.
> - **Task 7 / Task 8** — a **mid-call** process death is now a required demonstration, not an optional
>   one. It is the seam A0's finding F1 exposed, and on JVM it costs a function call where Android
>   needed `pm disable-user` plus an on-device poll inside a 157–170 ms window.
> - **Task 9** — `AgentVocabularyGuardTest` scans **three** packages now (`domain/trace` was added by
>   finding F7). It still is not extended to the consumer; do not "fix" either fact.
> - **Global Constraints** — the repo's test baseline moved from 1138 to 1158 (`:domain:jvmTest` 405 →
>   416), and one new rule was added below: do not let a test *simulate* a path the harness walks.

## Global Constraints

Every task's requirements implicitly include this section.

- **JDK 17.** Every Gradle command in this plan runs as
  `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 <tasks>`.
  Abbreviated below as `./gradlew <tasks>`; **type the full form**.
- **Never pipe `gradlew` through `tail`.** Check the exit code and read the real output. This masked a
  red gate as exit 0 on 2026-07-13.
- **Before any build, run `git status`.** If `gradle/gradle-daemon-jvm.properties` has appeared,
  delete it — Android Studio regenerates it with `toolchainVersion=25` and it is read *before*
  `-Porg.gradle.java.installations.paths`. The uncommitted modification to `gradle.properties`
  (`org.gradle.tooling.parallel=true`) is deliberate, an owner decision of 2026-08-22 — **do not touch
  it and do not commit it.**

  > **FALSE — corrected 2026-08-29.** No document ever carried that decision; the label travelled from
  > kickoff to kickoff without a source. The owner confirmed he did not write it: Android Studio
  > appended it (file mtime 2026-08-22 03:37, the same IDE session that produced the
  > `gradle-daemon-jvm.properties` trap `§HANDOFF` documents). Reverted. It was inert for the gate
  > anyway — `org.gradle.parallel=true` is already line 2, and `org.gradle.tooling.parallel` is a
  > Tooling-API/IDE-sync key that `./gradlew` does not use.
- **Block gate:** `:domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test`.
  `:domain:jvmTest` must be listed explicitly — `testDebugUnitTest` has not reached it since `:domain`
  went KMP, and **416 of the repo's 1158 tests** live there (baseline at `a7f4755`, after the A0 review
  round added twenty; it was 405 of 1138 at `7442da4`).
- **`:domain` is stdlib + coroutines only.** Production code lives in `commonMain` and must compile for
  both `androidTarget()` and `jvm()`. Introduce nothing JVM- or Android-specific there. Tests go in
  `jvmTest`.
- **`ActionIds`' seven values are frozen byte-for-byte** (ADR 3/4). Not touched by this block.
- **`OutboundContextPolicy.ALLOWED` is not widened.** Nothing in this block goes outbound at all.
- **The A1 fork stays undecided.** Both of its branches — a parallel tool vocabulary, or evolving
  `ActionCatalog` in place — must be exactly as cheap after this block as before.
- **The §6.3 asymmetry stays.** "Unify the Android and JVM outcomes" is **not** a task here (owner
  instruction, 2026-08-23). See Task 7, Step 5.
- **No test may *simulate* a path the harness actually walks.** Three of the A0 review's nine findings
  grew from a single domain test that stood in for a restart by calling `advance` on a prepared
  session, while the product went through `restoreOnStart()` → `pausedForRestore()` and
  `continueSession()` → `resumed()` — both of which write trace events, and those events were exactly
  what hid the defect. In this block: if `ConsoleHarness` has a transition, a test drives **the
  harness** through it, not an equivalent-looking sequence of use-case calls. A path with a transition
  no test walks is unverified, however complete the case list looks.
- **The agent commits; the agent never pushes.**
- No Android code, no desktop UI, no distribution, no public SDK, no model planner, no MCP client.

---

### Task 1: The module, and the property that defines it

**Files:**
- Modify: `settings.gradle.kts`
- Create: `consumer/jvm/build.gradle.kts`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ModuleIsolationTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: the Gradle module `:consumer:jvm`, source roots `consumer/jvm/src/main/kotlin` and
  `consumer/jvm/src/test/kotlin`, base package `com.sidr.launcher.consumer.jvm`.

- [ ] **Step 1: Write the failing test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ModuleIsolationTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two properties that make this module the *second consumer* rather than a second Android module.
 *
 * The first is a compile-time claim as much as a runtime one: if `:domain` were not on this module's
 * classpath, this file would not compile, so the assertions are the visible half of a check the
 * compiler already made.
 *
 * The second is the one worth a test. `:consumer:jvm` must reach the portable engine and **nothing**
 * Android. A dependency added by accident — on `:core:android`, on `:data:repository`, on AGP — would
 * make `android.content.Intent` loadable, and this test is what says so out loud.
 */
class ModuleIsolationTest {

    @Test
    fun `the portable engine contracts are on this module's classpath`() {
        assertEquals(8, RuntimeBudget.Default.maxSteps)
        assertEquals(2, RuntimeBudget.Default.maxConsecutiveFailures)
        assertEquals("workspace_info", ToolId("workspace_info").value)
    }

    @Test
    fun `no Android class is reachable from this module`() {
        val android = runCatching { Class.forName("android.content.Intent") }
        assertTrue(
            "android.* must not be reachable from :consumer:jvm — the second consumer proves the core " +
                "is portable only if it has no Android on its classpath. Found: ${android.getOrNull()}",
            android.isFailure,
        )
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test`

Expected: FAIL — `Project 'consumer' not found in root project 'SidrLauncher'`. The module does not
exist yet.

- [ ] **Step 3: Create the module**

Append to `settings.gradle.kts`, after `include(":baselineprofile")`:

```kotlin
// A0.5 — the second consumer of the portable core (Master Plan §2 criterion 10, §3.1a).
// A headless JVM harness: depends on :domain and nothing else, and nothing depends on it.
include(":consumer:jvm")
```

Create `consumer/jvm/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(17)
}

// :domain is the ONLY project dependency, and that is the point of this module (spec §4). No core/*,
// no data/*, no feature/*, nothing Android. `ModuleIsolationTest` holds it.
dependencies {
    implementation(project(":domain"))
    implementation(libs.coroutines.core)
    implementation(libs.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.coroutines.test)
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test`

Expected: PASS, 2 tests.

- [ ] **Step 5: Prove which variant it consumes — a resolved fact, not an inference**

Run:

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :consumer:jvm:dependencyInsight --configuration compileClasspath --dependency :domain \
  2>&1 | sed -n '/^project :domain/,/^$/p'
```

Expected: `Variant jvmApiElements:` with `org.jetbrains.kotlin.platform.type | jvm`. **Record this
output** — it is the evidence for Master Plan §2 criterion 10 and goes into the ADR. The plugin id
alone does not prove it; the resolution does.

- [ ] **Step 6: Confirm the three i18n guard-the-guards still pass**

Adding a module to `settings.gradle.kts` is parsed by
`LocaleCompletenessGuardTest.module_prefixes_cover_every_module_that_ships_strings`,
`HardcodedUiTextGuardTest.scoped_roots_cover_every_ui_module` and
`DomainIdentifierLeakGuardTest.scoped_roots_cover_every_ui_module`. Each gates on the module actually
containing `@Composable` under `src/main` or shipping `res/values/strings*.xml`. A JVM console module
has neither, so all three must pass — verify rather than assume.

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :app:testDebugUnitTest --tests "*i18n*"`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add settings.gradle.kts consumer/jvm/build.gradle.kts \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ModuleIsolationTest.kt
git commit -m "feat(agentic-4.5/A0.5): :consumer:jvm — the second consumer's module

Depends on :domain and nothing else; nothing depends on it. dependencyInsight
confirms it resolves :domain's jvmApiElements variant, which is the first
product consumer the jvm() target has ever had.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: `GoalShape.Free` — the only `commonMain` change, and the guard that freezes the rest

**Files:**
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentGoal.kt`
- Modify: `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt:52-55`
- Test: `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CoreVocabularyFreezeGuardTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `GoalShape.Free(text: String)` — the goal shape every `:consumer:jvm` goal carries.
  `TemplatePlanner.plan` returns `PlanningResult.NoPlan` for it.

- [ ] **Step 1: Write the failing test**

Create `domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CoreVocabularyFreezeGuardTest.kt`:

```kotlin
package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ObservedFact
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The record-don't-fix decision of A0.5, held mechanically.**
 *
 * A0.5's whole scope decision (spec §2, Approach A) is that the core's launcher-shaped vocabularies
 * are *recorded with an address*, not repaired — because A1' has not arrived and Master Plan §3.4
 * forbids building a contract for a consumer that has not come. The predictable way that decision
 * gets reversed by accident is an implementer meeting one of these closed types, reading it as a gap,
 * and adding a value. This test is what makes that a conscious act.
 *
 * If you are here because one of these went red: adding a value to either type is **change-control**
 * (Master Plan §5 for `GoalShape` via §3.6 `B1`; the A1 fork for `ObservedFact`). Take it to the
 * owner. Do not update the expectation to match your edit.
 */
class CoreVocabularyFreezeGuardTest {

    /**
     * `ObservedFact` is the type A0.5 most wants to grow and must not: "the file is not there" has no
     * value here, which is exactly the finding spec §6.3 records against A1'.
     */
    @Test
    fun `ObservedFact still holds exactly the two values A0 shipped`() {
        assertEquals(
            "Adding an ObservedFact value decides the A1 fork by drift and reverses A0.5's " +
                "record-don't-fix decision (spec §2, §6.3). It is an owner decision, not a fix.",
            listOf("APP_NOT_INSTALLED", "APP_AMBIGUOUS"),
            ObservedFact.entries.map { it.name },
        )
    }

    /**
     * Master Plan §3.6 `B1` holds `GoalShape` at one value until A4'; A0.5 adds exactly one more, and
     * the argument for it (spec §7.1) is that `Free` is the *absence* of a shape and so cannot start a
     * taxonomy — there can never be a second `Free`. `B1` otherwise stands: no **recognised** shape may
     * be added before A4'.
     *
     * The `when` below is exhaustive and has no `else` on purpose: a third `GoalShape` makes this file
     * fail to **compile**, which is earlier and louder than a failing assertion. This gives `B1` its
     * first test.
     */
    @Test
    fun `GoalShape holds exactly the two shapes this project has decided on`() {
        val shapes: List<GoalShape> = listOf(
            GoalShape.AppNotInstalled(query = "x"),
            GoalShape.Free(text = "x"),
        )

        val names = shapes.map { shape ->
            when (shape) {
                is GoalShape.AppNotInstalled -> "AppNotInstalled"
                is GoalShape.Free -> "Free"
            }
        }

        assertEquals(listOf("AppNotInstalled", "Free"), names)
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest --tests "*CoreVocabularyFreezeGuardTest*"`

Expected: FAIL — compilation error, `Unresolved reference: Free`.

- [ ] **Step 3: Add the shape**

In `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentGoal.kt`, replace the `GoalShape`
declaration with:

```kotlin
/**
 * What the planner recognised about the goal.
 *
 * [AppNotInstalled] is A0's one recognised shape, and the `when` over this type in `TemplatePlanner`
 * is exhaustive on purpose: adding a **recognised** shape forces a deliberate decision rather than
 * falling into a default.
 *
 * [Free] is A0.5's addition and is deliberately not a recognised shape at all — it is the *absence* of
 * one, carrying the raw text for a planner that does its own reading. Master Plan §3.6 `B1` holds this
 * type at one value until A4', and its stated failure mode is a taxonomy built for a consumer that has
 * not arrived; `Free` cannot start one, because there can never be a second `Free`. It is also the
 * shape A4''s model planner needs regardless — a model plans from text. `B1` otherwise stands.
 */
sealed interface GoalShape {
    /** FastPath resolved the command to an app launch and found no such app installed. */
    data class AppNotInstalled(val query: String) : GoalShape

    /** No shape was recognised; the planner receives the raw goal text and reads it itself. */
    data class Free(val text: String) : GoalShape
}
```

- [ ] **Step 4: Add the `TemplatePlanner` arm**

In `domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt`, replace the body
of `plan`:

```kotlin
    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult =
        when (val shape = goal.shape) {
            is GoalShape.AppNotInstalled -> planMissingApp(shape, registry)
            // The Android planner does not plan free-text goals — reading raw text is the model
            // planner's job and arrives in A4' behind this same `Planner` port. Answering `NoPlan`
            // here is the honest "I recognise nothing", not a stub.
            is GoalShape.Free -> PlanningResult.NoPlan
        }
```

- [ ] **Step 5: Run the whole domain suite and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :domain:jvmTest`

Expected: PASS, 416 + 2 tests, 0 failures. If any existing `TemplatePlanner` or `AgentGoal` test fails,
**stop and report** — the change is meant to be purely additive.

- [ ] **Step 6: Commit**

```bash
git add domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/AgentGoal.kt \
  domain/src/commonMain/kotlin/com/sidr/launcher/domain/agent/TemplatePlanner.kt \
  domain/src/jvmTest/kotlin/com/sidr/launcher/domain/agent/CoreVocabularyFreezeGuardTest.kt
git commit -m "feat(agentic-4.5/A0.5): GoalShape.Free — the block's only commonMain change

AgentGoal.shape is non-nullable and GoalShape held one launcher-shaped value,
so a JVM goal could not be phrased at all. Free is the absence of a shape, so
it cannot start the taxonomy Master Plan §3.6 B1 warns about — B1 otherwise
stands and now has its first test.

CoreVocabularyFreezeGuardTest holds the record-don't-fix decision mechanically:
ObservedFact stays at two values, and a third GoalShape fails to compile.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: `SandboxToolSource` — three tools, three arities

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolIds.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSource.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSourceTest.kt`

**Interfaces:**
- Consumes: `ToolRegistry`, `ToolDescriptor`, `ToolDurability`, `ToolId`, `ActionArg`,
  `ActionRiskLevel` from `:domain`.
- Produces: `SandboxToolIds.{WORKSPACE_INFO, FIND_FILE, DELETE_FILE}: ToolId`;
  `SandboxKeys.{ROOT, QUERY, RESOLVED_PATH, PATH}: String`; `class SandboxToolSource : ToolRegistry`
  with a no-arg constructor.

- [ ] **Step 1: Write the failing test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSourceTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The three tools exist to answer Master Plan §3.1a question 1 — does `ToolDescriptor` stretch to
 * three arities — inside one real plan rather than as a registry curiosity. Each assertion below is
 * one leg of that answer.
 */
class SandboxToolSourceTest {

    private val source = SandboxToolSource()

    @Test
    fun `workspace_info is the zero-arity leg and still declares an output`() {
        val d = source.find(SandboxToolIds.WORKSPACE_INFO)!!
        assertTrue("zero arity means an empty argSchema", d.argSchema.isEmpty())
        assertEquals(listOf(SandboxKeys.ROOT), d.outputSchema.map { it.name })
        assertEquals(ActionRiskLevel.SAFE, d.risk)
        assertEquals(ToolDurability.TRANSIENT, d.durability)
        assertNull(d.permissionGate)
    }

    @Test
    fun `find_file is the typed-arguments leg with two args and one output`() {
        val d = source.find(SandboxToolIds.FIND_FILE)!!
        assertEquals(listOf(SandboxKeys.QUERY, SandboxKeys.ROOT), d.argSchema.map { it.name })
        assertTrue("both args are required", d.argSchema.all { it.required })
        assertEquals(listOf(SandboxKeys.RESOLVED_PATH), d.outputSchema.map { it.name })
        assertEquals(ActionRiskLevel.SAFE, d.risk)
        assertEquals(ToolDurability.TRANSIENT, d.durability)
    }

    /**
     * The first `DANGEROUS` and the first `DURABLE` tool this project has ever registered.
     * `ActionRiskLevel`'s own KDoc still says `DANGEROUS` is "not producible in the MVP"; on the second
     * consumer it is, and that asymmetry against `play_store_search`'s `CONFIRM` is the evidence for
     * spec §11.3 — two adapters over one core-owned scale, disagreeing about what a comparable act is
     * worth.
     */
    @Test
    fun `delete_file is DANGEROUS and DURABLE, and declares no output`() {
        val d = source.find(SandboxToolIds.DELETE_FILE)!!
        assertEquals(listOf(SandboxKeys.PATH), d.argSchema.map { it.name })
        assertTrue("a tool that produces nothing declares nothing", d.outputSchema.isEmpty())
        assertEquals(ActionRiskLevel.DANGEROUS, d.risk)
        assertEquals(ToolDurability.DURABLE, d.durability)
    }

    @Test
    fun `the registry holds exactly these three and nothing else`() {
        assertEquals(
            listOf("workspace_info", "find_file", "delete_file"),
            source.all().map { it.id.value },
        )
        assertNull(source.find(ToolId("launch_app")))
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*SandboxToolSourceTest*"`

Expected: FAIL — `Unresolved reference: SandboxToolIds`.

- [ ] **Step 3: Write the ids and keys**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolIds.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ToolId

/**
 * This consumer's tool vocabulary — **declared here, not in `commonMain`.**
 *
 * `ToolIds` in `domain/tool` keeps exactly its two A0 values. Adding these three there would be
 * precisely the per-consumer taxonomy A0.5 exists to warn about (spec §11.2), and `ToolId`'s
 * value-class-over-`String` design already makes it unnecessary: "adding one requires no change to a
 * central enum", as `ActionId`'s KDoc puts it. That this works with no core edit is one of the two
 * positive findings of the block — the shape that survived contact with a second consumer.
 */
object SandboxToolIds {
    val WORKSPACE_INFO = ToolId("workspace_info")
    val FIND_FILE = ToolId("find_file")
    val DELETE_FILE = ToolId("delete_file")
}

/**
 * The one spelling of every argument and output key. Declaration and emission read the same constants,
 * so they cannot drift on a name — the pattern `SystemIntentToolSource.RESOLVED_QUERY` already sets.
 */
object SandboxKeys {
    const val ROOT = "root"
    const val QUERY = "query"
    const val RESOLVED_PATH = "resolved_path"
    const val PATH = "path"
}
```

- [ ] **Step 4: Write the source**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSource.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * The second consumer's only tool source: three filesystem tools over a sandbox directory.
 *
 * It is a `ToolRegistry` directly, the same shape `SystemIntentToolSource` takes, so the registry
 * stays "a list of sources" and A1' can still federate or discard either one. **The A1 fork is not
 * touched here** — this source declares its own ids (see [SandboxToolIds]) and projects nothing from
 * `ActionCatalog`, which is what keeps both branches of that fork equally cheap.
 *
 * Risk is assigned **here**, in the adapter, exactly as `DefaultActionCatalog` assigns it on the
 * Android side. The three-level scale is core-owned and untouched (spec §11.3).
 */
class SandboxToolSource : ToolRegistry {

    private val descriptors: List<ToolDescriptor> = listOf(
        ToolDescriptor(
            id = SandboxToolIds.WORKSPACE_INFO,
            argSchema = emptyList(),
            outputSchema = listOf(
                ActionArg(SandboxKeys.ROOT, description = "Absolute path of the sandbox root"),
            ),
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = SandboxToolIds.FIND_FILE,
            argSchema = listOf(
                ActionArg(SandboxKeys.QUERY, description = "Exact file name to look for"),
                ActionArg(SandboxKeys.ROOT, description = "Directory to search, must be inside the sandbox"),
            ),
            outputSchema = listOf(
                ActionArg(
                    SandboxKeys.RESOLVED_PATH,
                    description = "Absolute path of the match; blank when nothing matched",
                ),
            ),
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = SandboxToolIds.DELETE_FILE,
            argSchema = listOf(
                ActionArg(SandboxKeys.PATH, description = "Absolute path of the file to delete"),
            ),
            outputSchema = emptyList(),
            // The first DANGEROUS + DURABLE tool in this repository. Deleting a file is irreversible
            // and writes durable state; nothing on the Android side is either.
            risk = ActionRiskLevel.DANGEROUS,
            durability = ToolDurability.DURABLE,
        ),
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
```

- [ ] **Step 5: Run the test and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*SandboxToolSourceTest*"`

Expected: PASS, 4 tests.

- [ ] **Step 6: Commit**

```bash
git add consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/ \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/
git commit -m "feat(agentic-4.5/A0.5): three sandbox tools — zero, two and one arity

Tool ids declared in the consumer, not in commonMain: ToolId's value-class
design means a second consumer needs no core edit, which is one of the two
contracts that survived contact with a second consumer.

delete_file is the first DANGEROUS + DURABLE tool in the repository.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: `SandboxToolExecutor` — the world, and the boundary around it

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutor.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutorTest.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolContractTest.kt`

**Interfaces:**
- Consumes: `SandboxToolIds`, `SandboxKeys`, `SandboxToolSource` (Task 3); `ToolExecutor`,
  `ResolvedInvocation`, `ToolResult`, `ToolOutput`, `CommandFailure` from `:domain`.
- Produces: `class SandboxToolExecutor(root: java.nio.file.Path) : ToolExecutor`.

- [ ] **Step 1: Write the failing behaviour test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutorTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class SandboxToolExecutorTest {

    @get:Rule val temp = TemporaryFolder()

    private fun executor() = SandboxToolExecutor(temp.root.toPath())

    private fun rootArg() = temp.root.toPath().toAbsolutePath().toString()

    @Test
    fun `workspace_info reports the sandbox root`() = runTest {
        val result = executor().invoke(ResolvedInvocation(SandboxToolIds.WORKSPACE_INFO))
        val effected = result as ToolResult.Effected
        assertEquals(rootArg(), effected.output.values[SandboxKeys.ROOT])
    }

    @Test
    fun `find_file returns the path of a match`() = runTest {
        val file = temp.newFile("stale.lock")
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "stale.lock", SandboxKeys.ROOT to rootArg()),
            ),
        )
        val effected = result as ToolResult.Effected
        assertEquals(file.toPath().toAbsolutePath().toString(), effected.output.values[SandboxKeys.RESOLVED_PATH])
    }

    /**
     * The miss branch still emits the declared key, blank. `ToolDescriptor` requires a declared output
     * to be a property of the tool and not of the branch it took, and a blank value is not a loophole:
     * `InvocationValidator.resolve` applies `.takeIf { it.isNotBlank() }`, so the binding fails closed.
     */
    @Test
    fun `find_file emits the declared key blank when nothing matched`() = runTest {
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "absent.lock", SandboxKeys.ROOT to rootArg()),
            ),
        )
        val effected = result as ToolResult.Effected
        assertEquals(setOf(SandboxKeys.RESOLVED_PATH), effected.output.values.keys)
        assertEquals("", effected.output.values[SandboxKeys.RESOLVED_PATH])
    }

    @Test
    fun `delete_file deletes a file inside the sandbox`() = runTest {
        val file = temp.newFile("stale.lock")
        val result = executor().invoke(
            ResolvedInvocation(
                SandboxToolIds.DELETE_FILE,
                mapOf(SandboxKeys.PATH to file.toPath().toAbsolutePath().toString()),
            ),
        )
        assertTrue(result is ToolResult.Effected)
        assertTrue("the file is gone", !file.exists())
    }

    /**
     * The sandbox boundary. A tool is the only path to the world, so its own containment check is the
     * last thing between a bound argument and an irreversible act. Refusal is a `Failed` result, never
     * an exception: an escaping path is a fact the trace must carry, not a crash.
     */
    @Test
    fun `delete_file refuses a path outside the sandbox and deletes nothing`() = runTest {
        val outside = Files.createTempFile("outside", ".lock")
        try {
            val result = executor().invoke(
                ResolvedInvocation(
                    SandboxToolIds.DELETE_FILE,
                    mapOf(SandboxKeys.PATH to outside.toAbsolutePath().toString()),
                ),
            )
            assertTrue("an escaping path must fail, not delete", result is ToolResult.Failed)
            assertTrue("the outside file survives", Files.exists(outside))
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `delete_file refuses a dot-dot escape even when it names a real file`() = runTest {
        val outside = Files.createTempFile("outside", ".lock")
        try {
            val escape = temp.root.toPath().resolve("..").resolve(outside.fileName).toString()
            val result = executor().invoke(
                ResolvedInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to escape)),
            )
            assertTrue(result is ToolResult.Failed)
            assertTrue("the outside file survives", Files.exists(outside))
        } finally {
            Files.deleteIfExists(outside)
        }
    }

    @Test
    fun `an unknown tool id fails closed`() = runTest {
        val result = executor().invoke(ResolvedInvocation(com.sidr.launcher.domain.tool.ToolId("nope")))
        assertTrue(result is ToolResult.Failed)
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*SandboxToolExecutorTest*"`

Expected: FAIL — `Unresolved reference: SandboxToolExecutor`.

- [ ] **Step 3: Write the executor**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutor.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolExecutor
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The second consumer's **only path to the world**, and the second implementation of [ToolExecutor] in
 * this repository. `ToolExecutorCallSiteGuardTest` (Task 9) is extended to see this file: it becomes
 * the fourth declared holder while the call-site count stays at **one**, because this class is invoked
 * by `AgentExecutor` and never by the harness directly.
 *
 * Every failure is a [ToolResult.Failed], never a thrown exception — an escaping path or a vanished
 * file is a fact the trace must carry.
 *
 * **A recorded limitation, and a finding of the block (spec §3.4):** every failure here reports
 * [CommandFailure.Generic], because `CommandFailure` is a closed five-value type whose other four
 * values are launcher-shaped (`CantOpenApp`, `NoSearchApp`, `CantOpenUrl`, `NoStoreApp`). A PC tool
 * cannot say "permission denied" or "outside the sandbox" in the vocabulary the core gives it. Owned
 * by A1'; **not** repaired here (Approach A, spec §2).
 */
class SandboxToolExecutor(private val root: Path) : ToolExecutor {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = when (invocation.id) {
        SandboxToolIds.WORKSPACE_INFO -> ToolResult.Effected(
            ToolOutput(mapOf(SandboxKeys.ROOT to canonicalRoot().toString())),
        )
        SandboxToolIds.FIND_FILE -> findFile(invocation)
        SandboxToolIds.DELETE_FILE -> deleteFile(invocation)
        // A tool this executor does not implement must not silently succeed. The registry and the
        // executor are separate objects, so "registered but unimplemented" is a reachable state.
        else -> ToolResult.Failed(CommandFailure.Generic)
    }

    private fun findFile(invocation: ResolvedInvocation): ToolResult {
        val query = invocation.args[SandboxKeys.QUERY].orEmpty()
        val dir = contained(invocation.args[SandboxKeys.ROOT].orEmpty())
            ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!Files.isDirectory(dir)) return ToolResult.Failed(CommandFailure.Generic)

        val match = Files.walk(dir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString() == query }
                .findFirst()
                .orElse(null)
        }

        // The declared key is emitted on EVERY branch — blank when nothing matched. `ToolDescriptor`:
        // "outputs are a property of the tool, not of the branch it happened to take, and a schema
        // that only sometimes holds is not a schema." The blank is fail-closed downstream, because
        // `InvocationValidator.resolve` rejects a blank binding as UNRESOLVED_ARG_SOURCE.
        return ToolResult.Effected(
            ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to (match?.toAbsolutePath()?.toString() ?: ""))),
        )
    }

    private fun deleteFile(invocation: ResolvedInvocation): ToolResult {
        val target = contained(invocation.args[SandboxKeys.PATH].orEmpty())
            ?: return ToolResult.Failed(CommandFailure.Generic)
        if (!Files.isRegularFile(target)) return ToolResult.Failed(CommandFailure.Generic)
        return if (Files.deleteIfExists(target)) {
            ToolResult.Effected()
        } else {
            ToolResult.Failed(CommandFailure.Generic)
        }
    }

    private fun canonicalRoot(): Path = root.toAbsolutePath().normalize().toRealPath()

    /**
     * The sandbox boundary. `..` is normalised and symlinks are resolved **before** the comparison, so
     * neither can walk out. A path that does not exist yet is compared after normalisation only —
     * `toRealPath` would throw on it, and a non-existent path is a legitimate argument to a tool that
     * is about to report "nothing there".
     *
     * Returns `null` for anything outside, so the caller fails the step rather than acting.
     */
    private fun contained(raw: String): Path? {
        if (raw.isBlank()) return null
        val base = canonicalRoot()
        val candidate = Paths.get(raw).toAbsolutePath().normalize()
        val real = if (Files.exists(candidate)) candidate.toRealPath() else candidate
        return real.takeIf { it == base || it.startsWith(base) }
    }
}
```

- [ ] **Step 4: Run the behaviour test and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*SandboxToolExecutorTest*"`

Expected: PASS, 7 tests.

- [ ] **Step 5: Write the contract test — source and executor must agree on every branch**

This mirrors `SystemIntentToolContractTest`, which is the only thing on the Android side that pins the
declared `outputSchema` against what the executor actually emits. Without its twin here, the source and
executor could drift and the failure would arrive as `UNRESOLVED_ARG_SOURCE` mid-plan.

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolContractTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The twin of `SystemIntentToolContractTest`, for the second consumer.
 *
 * A tool that declares an output must return it on **every** result it can carry one on. The Android
 * source satisfies that only because `resolved_query` is an *echo of the input*; `find_file`'s output
 * is genuinely **discovered**, which is what makes this test the interesting one — it is the branch
 * where the two consumers' contracts are hardest to hold identically.
 */
class SandboxToolContractTest {

    @get:Rule val temp = TemporaryFolder()

    private val source = SandboxToolSource()
    private fun executor() = SandboxToolExecutor(temp.root.toPath())
    private fun rootArg() = temp.root.toPath().toAbsolutePath().toString()

    private data class Case(
        val toolId: ToolId,
        val args: Map<String, String>,
        val setUp: () -> Unit,
        val branch: String,
    )

    @Test
    fun `every result that can carry an output carries exactly the keys its tool declares`() = runTest {
        val cases = listOf(
            Case(SandboxToolIds.WORKSPACE_INFO, emptyMap(), {}, "Effected"),
            Case(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "hit.lock", SandboxKeys.ROOT to rootArg()),
                { temp.newFile("hit.lock") },
                "Effected",
            ),
            Case(
                SandboxToolIds.FIND_FILE,
                mapOf(SandboxKeys.QUERY to "absent.lock", SandboxKeys.ROOT to rootArg()),
                {},
                "Effected",
            ),
        )

        cases.forEach { case ->
            case.setUp()
            val result = executor().invoke(ResolvedInvocation(case.toolId, case.args))
            val declared = source.find(case.toolId)!!.outputSchema.map { it.name }.toSet()

            assertEquals("${case.toolId.value}: wrong branch for this world state", case.branch, result.branch())
            assertEquals(
                "${case.toolId.value} / ${case.branch}: emitted output keys must equal the declared outputSchema",
                declared,
                result.outputKeys(),
            )
        }
    }

    @Test
    fun `at least one sandbox tool declares an output, so the comparison above is not empty sets`() {
        assertTrue(source.all().any { it.outputSchema.isNotEmpty() })
    }

    private fun ToolResult.branch(): String = when (this) {
        is ToolResult.Effected -> "Effected"
        is ToolResult.Observed -> "Observed($fact)"
        is ToolResult.Failed -> "Failed(${failure::class.simpleName})"
    }

    private fun ToolResult.outputKeys(): Set<String> = when (this) {
        is ToolResult.Effected -> output.values.keys
        is ToolResult.Observed -> output.values.keys
        is ToolResult.Failed -> emptySet()
    }
}
```

- [ ] **Step 6: Run the contract test and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*SandboxToolContractTest*"`

Expected: PASS, 2 tests.

- [ ] **Step 7: Mutate the contract test — a green run proves nothing**

In `SandboxToolSource.kt`, temporarily delete `find_file`'s `outputSchema` entry (leave it
`emptyList()`), run the contract test, confirm **RED** on the hit case, then restore. Plant and restore
**inside one shell invocation** with a `trap … EXIT`, per the standing rule — an agent once died
mid-round and left a probe in production source:

```bash
cd /home/Suleiman/Sidr-launcher
F=consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolSource.kt
cp "$F" /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/SandboxToolSource.bak
trap 'cp /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/SandboxToolSource.bak "$F"' EXIT
python3 - "$F" <<'PY'
import re, sys
p = sys.argv[1]
s = open(p).read()
s = s.replace('''            outputSchema = listOf(
                ActionArg(
                    SandboxKeys.RESOLVED_PATH,
                    description = "Absolute path of the match; blank when nothing matched",
                ),
            ),''', '            outputSchema = emptyList(),')
open(p, 'w').write(s)
PY
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :consumer:jvm:test --tests "*SandboxToolContractTest*"
echo "EXIT=$?"
```

Expected: **exit 1**, failing on "emitted output keys must equal the declared outputSchema". Record the
log. The `trap` restores the file when the shell exits; confirm with `git diff --stat` afterwards.

- [ ] **Step 8: Commit**

```bash
git add consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/tool/SandboxToolExecutor.kt \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/tool/
git commit -m "feat(agentic-4.5/A0.5): SandboxToolExecutor — the second path to the world

Sandbox containment resolves symlinks and .. before comparing; an escaping path
is a Failed result, never an exception. find_file emits its declared key on
every branch, blank on a miss, which honours ToolDescriptor's totality rule and
stays fail-closed because resolve() rejects a blank binding.

Recorded, not fixed: every failure here is CommandFailure.Generic, because the
other four values are launcher-shaped. Owned by A1'.

Contract test mirrors SystemIntentToolContractTest and was mutated RED.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: `FilePlanner` — a second `Planner` behind the same port

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlanner.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlannerTest.kt`

**Interfaces:**
- Consumes: `SandboxToolIds`, `SandboxKeys` (Task 3); `GoalShape.Free` (Task 2); `Planner`,
  `PlanningResult`, `ExecutionPlan`, `PlanStep`, `StepPrecondition`, `StepRationale`, `ToolInvocation`,
  `ArgSource`, `AgentGoal`, `ToolRegistry` from `:domain`.
- Produces: `class FilePlanner : Planner` with a no-arg constructor.

- [ ] **Step 1: Write the failing test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlannerTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.plan

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolRegistry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilePlannerTest {

    private val planner = FilePlanner()
    private val registry = SandboxToolSource()

    private fun goal(text: String) = AgentGoal(text = text, shape = GoalShape.Free(text))

    @Test
    fun `a recognised goal plans three steps with two step-to-step bindings`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        val steps = planned.plan.steps

        assertEquals(3, steps.size)
        assertEquals(
            listOf(SandboxToolIds.WORKSPACE_INFO, SandboxToolIds.FIND_FILE, SandboxToolIds.DELETE_FILE),
            steps.map { it.invocation.id },
        )

        assertTrue("step 0 takes no arguments", steps[0].invocation.args.isEmpty())

        assertEquals(
            mapOf(
                SandboxKeys.QUERY to ArgSource.Literal("stale.lock"),
                SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
            ),
            steps[1].invocation.args,
        )

        assertEquals(
            mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH)),
            steps[2].invocation.args,
        )
    }

    @Test
    fun `risk comes from the registry and rises only at the last step`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        assertEquals(
            listOf(ActionRiskLevel.SAFE, ActionRiskLevel.SAFE, ActionRiskLevel.DANGEROUS),
            planned.plan.steps.map { it.risk },
        )
    }

    /**
     * `StepRationale` has two values and the other one is `APP_NOT_INSTALLED_FALLBACK` — dead
     * vocabulary for any non-launcher consumer. `GOAL_DIRECT` is honest for all three steps here:
     * each directly serves the goal. Recorded as a finding against A1' (spec §11.2), not worked around.
     */
    @Test
    fun `every step is GOAL_DIRECT and unconditional`() = runTest {
        val planned = planner.plan(goal("remove stale.lock"), registry) as PlanningResult.Planned
        assertTrue(planned.plan.steps.all { it.rationale == StepRationale.GOAL_DIRECT })
        assertTrue(planned.plan.steps.all { it.precondition == StepPrecondition.None })
    }

    @Test
    fun `an unreadable goal is NoPlan, not a guess`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("what is the weather"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove   "), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("removestale.lock"), registry))
    }

    @Test
    fun `the Android goal shape is not this planner's business`() = runTest {
        val android = AgentGoal(text = "открой убер", shape = GoalShape.AppNotInstalled("убер"))
        assertEquals(PlanningResult.NoPlan, planner.plan(android, registry))
    }

    @Test
    fun `a registry missing a tool yields NoPlan rather than a half plan`() = runTest {
        val partial = object : ToolRegistry {
            private val kept = registry.all().filterNot { it.id == SandboxToolIds.DELETE_FILE }
            override fun all(): List<ToolDescriptor> = kept
            override fun find(id: ToolId): ToolDescriptor? = kept.firstOrNull { it.id == id }
        }
        assertEquals(PlanningResult.NoPlan, planner.plan(goal("remove stale.lock"), partial))
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*FilePlannerTest*"`

Expected: FAIL — `Unresolved reference: FilePlanner`.

- [ ] **Step 3: Write the planner**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/plan/FilePlanner.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.plan

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
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

/**
 * The second consumer's planner — deterministic, and behind the **same** [Planner] port `TemplatePlanner`
 * sits behind.
 *
 * That is the point of the block that reusing `TemplatePlanner` could not have made: `AgentExecutor`
 * takes no planner at all, and `StartAgentSessionUseCase` takes one by constructor, so a second
 * consumer supplies its own with **zero** change to the engine. `TemplatePlanner` hard-codes
 * `ToolIds.LAUNCH_APP`/`PLAY_STORE_SEARCH`, so reusing it would have forced this consumer to name its
 * tools after Android families — a costume, not a proof (spec §2, `F4`).
 *
 * **Reading the goal is deliberately trivial.** One verb, then the rest of the line. Not a parser, not
 * a matcher, not an understanding layer — anything richer would be building this consumer's own
 * FastPath, which is neither this block's subject nor in its budget. A goal it cannot read is
 * [PlanningResult.NoPlan].
 *
 * Like `TemplatePlanner`, it **names no destination**: risk, schema and identity all come from the
 * registry, so the tools can change without the planner changing.
 */
class FilePlanner : Planner {

    override suspend fun plan(goal: AgentGoal, registry: ToolRegistry): PlanningResult {
        val target = targetOf(goal) ?: return PlanningResult.NoPlan

        val info = registry.find(SandboxToolIds.WORKSPACE_INFO) ?: return PlanningResult.NoPlan
        val find = registry.find(SandboxToolIds.FIND_FILE) ?: return PlanningResult.NoPlan
        val delete = registry.find(SandboxToolIds.DELETE_FILE) ?: return PlanningResult.NoPlan

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(info.id),
                        risk = info.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(
                            find.id,
                            mapOf(
                                SandboxKeys.QUERY to ArgSource.Literal(target),
                                SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
                            ),
                        ),
                        risk = find.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 2,
                        invocation = ToolInvocation(
                            delete.id,
                            mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH)),
                        ),
                        risk = delete.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }

    private fun targetOf(goal: AgentGoal): String? {
        val text = when (val shape = goal.shape) {
            is GoalShape.Free -> shape.text.trim()
            // The Android shape belongs to `TemplatePlanner`. Answering NoPlan rather than guessing is
            // what keeps the two planners from quietly overlapping.
            is GoalShape.AppNotInstalled -> return null
        }
        val prefix = "$VERB "
        if (!text.startsWith(prefix)) return null
        return text.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val VERB = "remove"
    }
}
```

- [ ] **Step 4: Run the test and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*FilePlannerTest*"`

Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/plan/ \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/plan/
git commit -m "feat(agentic-4.5/A0.5): FilePlanner — a second Planner behind the same port

Three steps, two step-to-step bindings, risk rising only at the last. The
engine needed zero changes to accept it: AgentExecutor takes no planner and
StartAgentSessionUseCase takes one by constructor. That is what reusing
TemplatePlanner could not have proved.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: `JvmAgentSessionStore` — is `AgentSessionStore` a port or a Room shape?

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionDto.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionStore.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionIdFactory.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionStoreTest.kt`

**Interfaces:**
- Consumes: `AgentSessionStore`, `AgentSession`, `AgentSessionId`, `AgentSessionIdFactory`,
  `ExecutionState`, `ExecutionPlan`, `PlanStep`, `StepPrecondition`, `StepRationale`, `AgentGoal`,
  `GoalShape`, `ToolResult`, `ToolOutput`, `ObservedFact`, `CommandFailure`, `TraceEvent`,
  `ExecutionTrace`, `ArgSource`, `ToolInvocation`, `ToolId`, `ActionRiskLevel`, `ConsentReason`,
  `RejectionReason`, `OperationResult`, `OperationError` from `:domain`.
- Produces: `class JvmAgentSessionStore(file: java.nio.file.Path) : AgentSessionStore`;
  `class JvmAgentSessionIdFactory : AgentSessionIdFactory`.

> **Scope note for the implementer.** This is the largest task, and its size *is* one of the block's
> reported findings (spec §8): both consumers had to hand-write a full mapper, because `:domain` is
> deliberately serialization-free and no domain type may carry `@Serializable`. When you finish, record
> the line count of `SessionDto.kt` + `SessionMapper.kt` — Task 10 puts the measured number in the ADR
> rather than an estimate.

- [ ] **Step 1: Write the failing test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionStoreTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JvmAgentSessionStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private fun store() = JvmAgentSessionStore(temp.root.toPath().resolve("session.json"))

    private val id = AgentSessionId("s-1")

    private fun session(
        state: ExecutionState = ExecutionState.Running,
        cursor: Int = 0,
        observations: Map<Int, ToolResult> = emptyMap(),
        consents: Map<Int, Boolean> = emptyMap(),
        trace: ExecutionTrace = ExecutionTrace(listOf(TraceEvent.PlanCreated(3))),
    ) = AgentSession(
        id = id,
        goal = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(0, ToolInvocation(SandboxToolIds.WORKSPACE_INFO), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
                PlanStep(
                    1,
                    ToolInvocation(
                        SandboxToolIds.FIND_FILE,
                        mapOf(
                            SandboxKeys.QUERY to ArgSource.Literal("stale.lock"),
                            SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
                        ),
                    ),
                    ActionRiskLevel.SAFE,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    2,
                    ToolInvocation(SandboxToolIds.DELETE_FILE, mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH))),
                    ActionRiskLevel.DANGEROUS,
                    StepPrecondition.None,
                    StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = cursor,
        state = state,
        observations = observations,
        consents = consents,
        trace = trace,
    )

    private fun <T> OperationResult<T>.value(): T = (this as OperationResult.Success).value

    @Test
    fun `an empty store has no active session`() = runTest {
        assertNull(store().active().value())
    }

    @Test
    fun `a saved session round-trips byte-for-byte through the file`() = runTest {
        val original = session(
            state = ExecutionState.AwaitingConsent,
            cursor = 2,
            observations = mapOf(
                0 to ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box"))),
                1 to ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.RESOLVED_PATH to "/tmp/box/stale.lock"))),
            ),
            consents = mapOf(1 to true),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.PlanCreated(3),
                    TraceEvent.StepStarted(0),
                    TraceEvent.ToolInvoked(0, SandboxToolIds.WORKSPACE_INFO),
                    TraceEvent.ToolObserved(0, ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.ROOT to "/tmp/box")))),
                    TraceEvent.StepSkipped(1, StepPrecondition.None),
                    TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL),
                    TraceEvent.SessionPaused,
                    TraceEvent.SessionResumed,
                ),
            ),
        )
        val s = store()
        s.save(original)
        assertEquals(original, s.active().value())
    }

    /**
     * The Android mapper drops a persisted `Failed`'s `CommandFailure` variant and restores it as
     * `Generic` — a named A0 gap. This consumer preserves it, which shows the loss is a **mapper
     * choice**, not a limitation of the contract. Useful to A4' and recorded in the ADR.
     */
    @Test
    fun `a persisted Failed keeps its CommandFailure variant here`() = runTest {
        val s = store()
        s.save(session(observations = mapOf(0 to ToolResult.Failed(CommandFailure.NoStoreApp))))
        assertEquals(
            ToolResult.Failed(CommandFailure.NoStoreApp),
            s.active().value()!!.observations[0],
        )
    }

    @Test
    fun `delete leaves the store empty - no history at rest`() = runTest {
        val s = store()
        s.save(session())
        s.delete(id)
        assertNull(s.active().value())
    }

    @Test
    fun `recordConsentIfPending applies once when the session awaits a decision`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(s.recordConsentIfPending(id, 2, granted = true).value())
        assertEquals(mapOf(2 to true), s.active().value()!!.consents)
    }

    @Test
    fun `recordConsentIfPending does not apply twice for the same step`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(s.recordConsentIfPending(id, 2, granted = true).value())
        assertTrue("a second decision must not apply", !s.recordConsentIfPending(id, 2, granted = false).value())
        assertEquals("the first decision stands", mapOf(2 to true), s.active().value()!!.consents)
    }

    @Test
    fun `recordConsentIfPending does not apply when the session is not awaiting`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.Running, cursor = 2))
        assertTrue(!s.recordConsentIfPending(id, 2, granted = true).value())
    }

    @Test
    fun `recordConsentIfPending does not apply to a different session id`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))
        assertTrue(!s.recordConsentIfPending(AgentSessionId("other"), 2, granted = true).value())
    }

    /**
     * **The property this whole task exists to test.** `recordConsentIfPending` is a compare-and-set
     * contract that exists because Room can express `UPDATE … WHERE state = pending` in one statement.
     * Whether a file store can honour it *honestly* — rather than by weakening it to a whole-object
     * write — is what decides whether `AgentSessionStore` is a portable port or a Room shape wearing an
     * interface (spec §8, §11.2).
     *
     * Two taps that race must not both win. The whole-object-write pattern is what already cost this
     * project the `autoHideNavBar` bug (DS-11), which only surfaced on device.
     */
    @Test
    fun `two concurrent consent writes for the same step - exactly one applies`() = runTest {
        val s = store()
        s.save(session(state = ExecutionState.AwaitingConsent, cursor = 2))

        val results = withContext(Dispatchers.Default) {
            List(2) { async { s.recordConsentIfPending(id, 2, granted = true) } }.awaitAll()
        }

        assertEquals(
            "exactly one of two racing consent writes may apply",
            1,
            results.count { it.value() },
        )
        assertEquals(mapOf(2 to true), s.active().value()!!.consents)
    }
}
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*JvmAgentSessionStoreTest*"`

Expected: FAIL — `Unresolved reference: JvmAgentSessionStore`.

- [ ] **Step 3: Write the DTOs**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionDto.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.store

import kotlinx.serialization.Serializable

/**
 * The consumer's **own** serializable mirror of the session graph.
 *
 * No domain type carries `@Serializable`, and none may: `:domain` is stdlib + coroutines by hard rule,
 * so the serialization plugin cannot be applied there. That means every consumer that persists a
 * session hand-writes a mapper — `AgentSessionMappers` on Android, [SessionMapper] here. **That cost is
 * a finding of this block, not an accident** (spec §8): the price of a second consumer includes one
 * mapper per consumer, and A0.5 reports the measured size rather than estimating it.
 *
 * Everything is stored by **name**, never by ordinal: an enum reordered in `commonMain` must not
 * silently re-interpret a file written by an earlier build.
 */
@Serializable
internal data class SessionDto(
    val id: String,
    val goalText: String,
    val goalFreeText: String,
    val steps: List<StepDto>,
    val cursor: Int,
    val state: String,
    val observations: Map<Int, ResultDto> = emptyMap(),
    val consents: Map<Int, Boolean> = emptyMap(),
    val trace: List<TraceDto> = emptyList(),
)

@Serializable
internal data class StepDto(
    val index: Int,
    val toolId: String,
    val args: Map<String, ArgSourceDto> = emptyMap(),
    val risk: String,
    val precondition: PreconditionDto,
    val rationale: String,
)

/** `kind` is `"Literal"` or `"FromStep"`; the other fields are populated per kind. */
@Serializable
internal data class ArgSourceDto(
    val kind: String,
    val value: String? = null,
    val stepIndex: Int? = null,
    val key: String? = null,
)

/** `kind` is `"None"` or `"PreviousStepObserved"`. */
@Serializable
internal data class PreconditionDto(val kind: String, val fact: String? = null)

/** `kind` is `"Effected"`, `"Observed"` or `"Failed"`. */
@Serializable
internal data class ResultDto(
    val kind: String,
    val fact: String? = null,
    val failure: String? = null,
    val output: Map<String, String> = emptyMap(),
)

/** `kind` is the `TraceEvent` subtype's simple name; the other fields are populated per kind. */
@Serializable
internal data class TraceDto(
    val kind: String,
    val index: Int? = null,
    val stepCount: Int? = null,
    val toolId: String? = null,
    val reason: String? = null,
    val granted: Boolean? = null,
    val state: String? = null,
    val precondition: PreconditionDto? = null,
    val result: ResultDto? = null,
)
```

- [ ] **Step 4: Write the mapper**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent

/**
 * Domain ↔ [SessionDto]. Total in both directions; an unreadable value throws
 * [IllegalArgumentException], which [JvmAgentSessionStore] turns into an `OperationResult.Failure`.
 *
 * **Failing loudly is deliberate.** A store that guesses at a value it cannot read produces a session
 * that looks whole and is not — the failure mode `RoomAgentSessionStoreTest` guards against on the
 * other side with "a `ToolObserved` with no observation on its step is a `Failure`, not a guessed
 * state".
 *
 * **This consumer preserves a `Failed` observation's [CommandFailure] variant.** The Android mapper
 * drops it and restores `Generic` — a named A0 gap. Keeping it here shows that loss is a mapper choice
 * rather than a limitation of the contract.
 */
internal object SessionMapper {

    fun toDto(session: AgentSession): SessionDto = SessionDto(
        id = session.id.value,
        goalText = session.goal.text,
        goalFreeText = when (val shape = session.goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> throw IllegalArgumentException(
                "JvmAgentSessionStore persists GoalShape.Free only; this consumer never produces " +
                    "AppNotInstalled, and writing one would mean the wrong planner built this session.",
            )
        },
        steps = session.plan.steps.map(::stepDto),
        cursor = session.cursor,
        state = session.state.name,
        observations = session.observations.mapValues { (_, r) -> resultDto(r) },
        consents = session.consents,
        trace = session.trace.events.map(::traceDto),
    )

    fun fromDto(dto: SessionDto): AgentSession = AgentSession(
        id = AgentSessionId(dto.id),
        goal = AgentGoal(dto.goalText, GoalShape.Free(dto.goalFreeText)),
        plan = ExecutionPlan(dto.steps.map(::step)),
        cursor = dto.cursor,
        state = enum(ExecutionState.entries, dto.state, "ExecutionState"),
        observations = dto.observations.mapValues { (_, d) -> result(d) },
        consents = dto.consents,
        trace = ExecutionTrace(dto.trace.map(::traceEvent)),
    )

    private fun stepDto(step: PlanStep) = StepDto(
        index = step.index,
        toolId = step.invocation.id.value,
        args = step.invocation.args.mapValues { (_, s) -> argDto(s) },
        risk = step.risk.name,
        precondition = preconditionDto(step.precondition),
        rationale = step.rationale.name,
    )

    private fun step(dto: StepDto) = PlanStep(
        index = dto.index,
        invocation = ToolInvocation(ToolId(dto.toolId), dto.args.mapValues { (_, d) -> arg(d) }),
        risk = enum(ActionRiskLevel.entries, dto.risk, "ActionRiskLevel"),
        precondition = precondition(dto.precondition),
        rationale = enum(StepRationale.entries, dto.rationale, "StepRationale"),
    )

    private fun argDto(source: ArgSource) = when (source) {
        is ArgSource.Literal -> ArgSourceDto(kind = "Literal", value = source.value)
        is ArgSource.FromStep -> ArgSourceDto(kind = "FromStep", stepIndex = source.stepIndex, key = source.key)
    }

    private fun arg(dto: ArgSourceDto): ArgSource = when (dto.kind) {
        "Literal" -> ArgSource.Literal(require(dto.value, "ArgSource.Literal.value"))
        "FromStep" -> ArgSource.FromStep(
            require(dto.stepIndex, "ArgSource.FromStep.stepIndex"),
            require(dto.key, "ArgSource.FromStep.key"),
        )
        else -> throw IllegalArgumentException("unknown ArgSource kind: ${dto.kind}")
    }

    private fun preconditionDto(p: StepPrecondition) = when (p) {
        StepPrecondition.None -> PreconditionDto(kind = "None")
        is StepPrecondition.PreviousStepObserved -> PreconditionDto("PreviousStepObserved", p.fact.name)
    }

    private fun precondition(dto: PreconditionDto): StepPrecondition = when (dto.kind) {
        "None" -> StepPrecondition.None
        "PreviousStepObserved" -> StepPrecondition.PreviousStepObserved(
            enum(ObservedFact.entries, require(dto.fact, "precondition.fact"), "ObservedFact"),
        )
        else -> throw IllegalArgumentException("unknown StepPrecondition kind: ${dto.kind}")
    }

    private fun resultDto(result: ToolResult) = when (result) {
        is ToolResult.Effected -> ResultDto("Effected", output = result.output.values)
        is ToolResult.Observed -> ResultDto("Observed", fact = result.fact.name, output = result.output.values)
        is ToolResult.Failed -> ResultDto("Failed", failure = failureName(result.failure))
    }

    private fun result(dto: ResultDto): ToolResult = when (dto.kind) {
        "Effected" -> ToolResult.Effected(ToolOutput(dto.output))
        "Observed" -> ToolResult.Observed(
            enum(ObservedFact.entries, require(dto.fact, "result.fact"), "ObservedFact"),
            ToolOutput(dto.output),
        )
        "Failed" -> ToolResult.Failed(failure(require(dto.failure, "result.failure")))
        else -> throw IllegalArgumentException("unknown ToolResult kind: ${dto.kind}")
    }

    private fun failureName(failure: CommandFailure) = when (failure) {
        CommandFailure.Generic -> "Generic"
        CommandFailure.CantOpenApp -> "CantOpenApp"
        CommandFailure.NoSearchApp -> "NoSearchApp"
        CommandFailure.CantOpenUrl -> "CantOpenUrl"
        CommandFailure.NoStoreApp -> "NoStoreApp"
    }

    private fun failure(name: String): CommandFailure = when (name) {
        "Generic" -> CommandFailure.Generic
        "CantOpenApp" -> CommandFailure.CantOpenApp
        "NoSearchApp" -> CommandFailure.NoSearchApp
        "CantOpenUrl" -> CommandFailure.CantOpenUrl
        "NoStoreApp" -> CommandFailure.NoStoreApp
        else -> throw IllegalArgumentException("unknown CommandFailure: $name")
    }

    private fun traceDto(event: TraceEvent): TraceDto = when (event) {
        is TraceEvent.PlanCreated -> TraceDto("PlanCreated", stepCount = event.stepCount)
        is TraceEvent.StepStarted -> TraceDto("StepStarted", index = event.index)
        is TraceEvent.StepSkipped -> TraceDto("StepSkipped", index = event.index, precondition = preconditionDto(event.precondition))
        is TraceEvent.StepRejected -> TraceDto("StepRejected", index = event.index, reason = event.reason.name)
        is TraceEvent.ConsentRequested -> TraceDto("ConsentRequested", index = event.index, reason = event.reason.name)
        is TraceEvent.ConsentResolved -> TraceDto("ConsentResolved", index = event.index, granted = event.granted)
        is TraceEvent.ToolInvoked -> TraceDto("ToolInvoked", index = event.index, toolId = event.toolId.value)
        is TraceEvent.ToolObserved -> TraceDto("ToolObserved", index = event.index, result = resultDto(event.result))
        TraceEvent.SessionPaused -> TraceDto("SessionPaused")
        TraceEvent.SessionResumed -> TraceDto("SessionResumed")
        is TraceEvent.SessionEnded -> TraceDto("SessionEnded", state = event.state.name)
    }

    private fun traceEvent(dto: TraceDto): TraceEvent = when (dto.kind) {
        "PlanCreated" -> TraceEvent.PlanCreated(require(dto.stepCount, "PlanCreated.stepCount"))
        "StepStarted" -> TraceEvent.StepStarted(require(dto.index, "StepStarted.index"))
        "StepSkipped" -> TraceEvent.StepSkipped(
            require(dto.index, "StepSkipped.index"),
            precondition(require(dto.precondition, "StepSkipped.precondition")),
        )
        "StepRejected" -> TraceEvent.StepRejected(
            require(dto.index, "StepRejected.index"),
            enum(RejectionReason.entries, require(dto.reason, "StepRejected.reason"), "RejectionReason"),
        )
        "ConsentRequested" -> TraceEvent.ConsentRequested(
            require(dto.index, "ConsentRequested.index"),
            enum(ConsentReason.entries, require(dto.reason, "ConsentRequested.reason"), "ConsentReason"),
        )
        "ConsentResolved" -> TraceEvent.ConsentResolved(
            require(dto.index, "ConsentResolved.index"),
            require(dto.granted, "ConsentResolved.granted"),
        )
        "ToolInvoked" -> TraceEvent.ToolInvoked(
            require(dto.index, "ToolInvoked.index"),
            ToolId(require(dto.toolId, "ToolInvoked.toolId")),
        )
        "ToolObserved" -> TraceEvent.ToolObserved(
            require(dto.index, "ToolObserved.index"),
            result(require(dto.result, "ToolObserved.result")),
        )
        "SessionPaused" -> TraceEvent.SessionPaused
        "SessionResumed" -> TraceEvent.SessionResumed
        "SessionEnded" -> TraceEvent.SessionEnded(
            enum(ExecutionState.entries, require(dto.state, "SessionEnded.state"), "ExecutionState"),
        )
        else -> throw IllegalArgumentException("unknown TraceEvent kind: ${dto.kind}")
    }

    private fun <T> require(value: T?, what: String): T =
        value ?: throw IllegalArgumentException("missing $what in the persisted session")

    private fun <E : Enum<E>> enum(all: List<E>, name: String, what: String): E =
        all.firstOrNull { it.name == name }
            ?: throw IllegalArgumentException("unknown $what: $name")
}
```

- [ ] **Step 5: Write the store and the id factory**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionStore.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionStore
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * The second consumer's [AgentSessionStore]: one JSON file holding at most one session.
 *
 * **No history at rest.** A terminal state deletes the file, exactly as the Room store deletes by
 * cascade — a durable trace journal is A5's decision, not one A0.5 quietly introduces.
 *
 * **[recordConsentIfPending] is a real compare-and-set**, and that is the point of this class. The
 * contract exists because Room can say `UPDATE … WHERE state = pending` in one statement; honouring it
 * here without weakening it to a whole-object write is what decides whether `AgentSessionStore` is a
 * portable port or a Room shape wearing an interface (spec §8, §11.2). Two mechanisms, because they
 * exclude different things: a [Mutex] for coroutines inside this process, a [java.nio.channels.FileLock]
 * for a second process opening the same file.
 *
 * Whole-object writes were avoided deliberately: that pattern already cost this project the
 * `autoHideNavBar` bug (DS-11), which only surfaced on device.
 */
class JvmAgentSessionStore(private val file: Path) : AgentSessionStore {

    private val mutex = Mutex()
    private val lockFile: Path = file.resolveSibling(file.fileName.toString() + ".lock")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    override suspend fun active(): OperationResult<AgentSession?> = mutex.withLock {
        guarded { withFileLock { read() } }
    }

    override suspend fun save(session: AgentSession): OperationResult<Unit> = mutex.withLock {
        guarded { withFileLock { write(session) } }
    }

    override suspend fun delete(id: AgentSessionId): OperationResult<Unit> = mutex.withLock {
        guarded {
            withFileLock {
                if (read()?.id == id) {
                    Files.deleteIfExists(file)
                }
            }
        }
    }

    override suspend fun recordConsentIfPending(
        id: AgentSessionId,
        stepIndex: Int,
        granted: Boolean,
    ): OperationResult<Boolean> = mutex.withLock {
        guarded {
            withFileLock {
                val current = read()
                when {
                    current == null -> false
                    current.id != id -> false
                    current.state != ExecutionState.AwaitingConsent -> false
                    // The decisive condition: a step that already carries a decision is no longer
                    // pending, so a second tap that raced the first cannot also win.
                    current.consents.containsKey(stepIndex) -> false
                    else -> {
                        write(current.copy(consents = current.consents + (stepIndex to granted)))
                        true
                    }
                }
            }
        }
    }

    private fun read(): AgentSession? {
        if (!Files.exists(file)) return null
        val text = Files.readString(file)
        if (text.isBlank()) return null
        return SessionMapper.fromDto(json.decodeFromString(SessionDto.serializer(), text))
    }

    private fun write(session: AgentSession) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            json.encodeToString(SessionDto.serializer(), SessionMapper.toDto(session)),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )
    }

    private fun <T> withFileLock(block: () -> T): T {
        Files.createDirectories(lockFile.parent)
        return FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
            .use { channel -> channel.lock().use { block() } }
    }

    /**
     * The hard rule: repository/use-case operations return `OperationResult` and never throw to the
     * caller. [SessionMapper] throws on an unreadable value on purpose; this is where that becomes a
     * `Failure` rather than a crash.
     */
    private inline fun <T> guarded(block: () -> T): OperationResult<T> =
        runCatching(block).fold(
            onSuccess = { OperationResult.Success(it) },
            onFailure = { OperationResult.Failure(OperationError.UnknownError(it.message)) },
        )
}
```

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/JvmAgentSessionIdFactory.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import java.util.UUID

/**
 * `java.util.UUID` lives **here**, in the consumer — which is the entire reason
 * [AgentSessionIdFactory] is a port. A0's spec made it one because `UUID` does not exist in
 * `commonMain`; A0.5 is the first thing to show the port earning its keep, with a second
 * implementation the core needed no edit to accept. One of the two contracts that survived contact
 * with a second consumer (spec §11.2).
 */
class JvmAgentSessionIdFactory : AgentSessionIdFactory {
    override fun newId(): AgentSessionId = AgentSessionId(UUID.randomUUID().toString())
}
```

- [ ] **Step 6: Run the store tests and verify they pass**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*JvmAgentSessionStoreTest*"`

Expected: PASS, 9 tests.

- [ ] **Step 7: Record the mapper's size for the ADR**

```bash
wc -l consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionDto.kt \
      consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/SessionMapper.kt
```

Write the total into your task notes. Task 10 puts the **measured** number in the ADR beside
`AgentSessionMappers`' own line count, as the reported cost of "one mapper per consumer".

- [ ] **Step 8: Commit**

```bash
git add consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/store/ \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/store/
git commit -m "feat(agentic-4.5/A0.5): JvmAgentSessionStore — the CAS contract, honoured honestly

recordConsentIfPending is a real compare-and-set: a Mutex for in-process
coroutines, a FileLock for a second process. Two racing consent writes, exactly
one applies — the DS-11 property, proved on the second consumer.

Own DTOs, because no domain type may carry @Serializable. Both consumers had to
hand-write a mapper, and that cost is a reported finding rather than an
estimate. This mapper preserves a Failed observation's CommandFailure variant,
showing the Android mapper's loss is a mapper choice, not a contract limit.

java.util.UUID lives in the consumer — AgentSessionIdFactory earning its keep.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: The loop end to end — and the divergence that must NOT be fixed

**Files:**
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 2–6, plus `AgentExecutor`, `StartAgentSessionUseCase`,
  `RunAgentSessionUseCase`, `ResolveConsentUseCase`, `CancelAgentSessionUseCase` from `:domain`.
- Produces: no production code. This task is the proof that the engine runs unmodified.

- [ ] **Step 1: Write the hit-path test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxToolExecutor
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.RejectionReason
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The block's actual claim: one goal passes `goal → plan → gate → tool → observe → tool → result →
 * trace` on plain JVM, over the **unchanged** engine.
 */
class AgentLoopTest {

    @get:Rule val temp = TemporaryFolder()

    private val registry = SandboxToolSource()
    private fun store() = JvmAgentSessionStore(temp.root.toPath().resolve("state/session.json"))
    private fun executor() = AgentExecutor(registry, SandboxToolExecutor(temp.root.toPath()))

    private fun <T> OperationResult<T>.value(): T = (this as OperationResult.Success).value

    private fun goal() = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock"))

    private suspend fun startAndRun(store: JvmAgentSessionStore): AgentSession {
        val start = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        val id = start.start(goal()).value()!!
        val run = RunAgentSessionUseCase(executor(), store)
        val session = store.active().value()!!
        return run.run(session).value().also { assertEquals(id, it.id) }
    }

    @Test
    fun `the loop stops for consent at the risk transition, before the world is touched`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()

        val paused = startAndRun(store)

        assertEquals(ExecutionState.AwaitingConsent, paused.state)
        assertEquals(2, paused.cursor)
        assertTrue("nothing was deleted before consent", file.exists())
        assertTrue(
            // `checkpointFor`'s first branch since 2026-08-23 is `maxOf(PlanStep.risk, registry.risk)
            // >= CONFIRM` (A0 finding F2). DANGEROUS takes it either way, and the reason is RISK_LEVEL
            // rather than DURABLE_EFFECT — spec §11.3's recorded finding, not a defect to fix here.
            "the gate names the risk level, not durability — DANGEROUS takes checkpointFor's first branch",
            paused.trace.events.contains(TraceEvent.ConsentRequested(2, ConsentReason.RISK_LEVEL)),
        )
        assertTrue(
            "step 1 bound its path from step 0's output",
            paused.trace.events.contains(TraceEvent.ToolInvoked(1, SandboxToolIds.FIND_FILE)),
        )
    }

    /**
     * **The plan's risk and the registry's risk agree — asserted, not assumed** (A0 finding F2,
     * 2026-08-23).
     *
     * The gate acts on `maxOf(PlanStep.risk, registry.risk)` because the two have different lifetimes:
     * a plan is a snapshot of the build that wrote it, the registry is the declaration in force when it
     * runs. `FilePlanner` copies risk out of `SandboxToolSource`, so for this consumer they are the same
     * value — which is what makes the hit path above take the branch it does. That agreement is a
     * property of `FilePlanner`, not a law, so it is pinned here: a future `FilePlanner` that assigned
     * its own risk levels would still be *safe* (the max wins) but would stop being the clean evidence
     * spec §11.3 reports.
     */
    @Test
    fun `the planner's risk for every step is the registry's risk`() = runTest {
        val planned = FilePlanner().plan(goal(), registry)
        assertTrue("expected a plan", planned is PlanningResult.Planned)

        (planned as PlanningResult.Planned).plan.steps.forEach { step ->
            val declared = registry.find(step.invocation.id)
            assertTrue("no descriptor for ${step.invocation.id.value}", declared != null)
            assertEquals(
                "step ${step.index} (${step.invocation.id.value}) disagrees with the registry",
                declared!!.risk,
                step.risk,
            )
        }
    }

    /**
     * **A process death DURING a tool call is resumed, not re-issued** (A0 finding F1, 2026-08-23).
     *
     * This is the seam the A0 review found unheld, and the reason it went unheld for a whole block is
     * worth carrying into this one: the domain test that "covered" it simulated the restart by calling
     * `advance` on the prepared session, while the product goes through `pausedForRestore()` and
     * `resumed()` — both of which append trace events on top of the pending `ToolInvoked`. Keyed on the
     * trace *tail*, the engine's mid-step predicate missed the one shape it exists to recognise.
     *
     * Android could only reach this window with `pm disable-user` plus an on-device `force-stop` poll,
     * 157–170 ms wide. Here it is three lines: prepare, persist, walk away. That asymmetry — a core
     * invariant that the second consumer can hold and the first could only approximate — is itself an
     * answer to "what is a second consumer for", and Task 10 Step 4 reports it as one.
     */
    @Test
    fun `a step interrupted mid-call is performed once when the session is picked back up`() = runTest {
        temp.newFile("stale.lock")
        val store = store()
        val start = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        start.start(goal()).value()

        // The mid-call shape, written the way `RunAgentSessionUseCase` writes it: prepare, save, die.
        val prepared = executor().prepare(store.active().value()!!)
        store.save(prepared)
        val tail = prepared.trace.events.last()
        assertTrue("the seed must be mid-step, was $tail", tail is TraceEvent.ToolInvoked)

        // A fresh process: find it, offer it, and continue only on an explicit choice.
        val offered = store.active().value()!!.pausedForRestore()
        store.save(offered)
        assertEquals(ExecutionState.Paused, offered.state)

        val continued = RunAgentSessionUseCase(executor(), store).run(offered.resumed()).value()

        val invokedZero = continued.trace.events.count {
            it is TraceEvent.ToolInvoked && it.index == 0
        }
        assertEquals(
            "one pending call is one ToolInvoked, however many times the process died: " +
                "${continued.trace.events}",
            1,
            invokedZero,
        )
        assertEquals(1, continued.trace.events.count { it is TraceEvent.StepStarted && it.index == 0 })
        assertEquals(ExecutionState.AwaitingConsent, continued.state)
    }

    @Test
    fun `granting consent completes the plan and the file is gone`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val done = resolve.resolve(paused.id, stepIndex = 2, granted = true).value()!!

        assertEquals(ExecutionState.Completed, done.state)
        assertTrue("the file is deleted", !file.exists())
    }

    @Test
    fun `refusing consent cancels and deletes nothing`() = runTest {
        val file = temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        val done = resolve.resolve(paused.id, stepIndex = 2, granted = false).value()!!

        assertEquals(ExecutionState.Cancelled, done.state)
        assertTrue("a refusal must not act", file.exists())
        assertEquals("a terminal state leaves nothing at rest", null, store.active().value())
    }

    @Test
    fun `a second tap on confirm does not run the step twice`() = runTest {
        temp.newFile("stale.lock")
        val store = store()
        val paused = startAndRun(store)

        val resolve = ResolveConsentUseCase(store, RunAgentSessionUseCase(executor(), store))
        assertTrue(resolve.resolve(paused.id, 2, granted = true).value() != null)
        assertEquals(
            "the second tap must not apply — recordConsentIfPending is a CAS",
            null,
            resolve.resolve(paused.id, 2, granted = true).value(),
        )
    }
}
```

- [ ] **Step 2: Run the hit-path tests and verify they pass**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*AgentLoopTest*"`

Expected: PASS, **6 tests** (four hit-path, plus the risk-agreement and mid-call ones added when the
spec was re-anchored to `a7f4755`). **If `RunAgentSessionUseCase` or `AgentExecutor` needed any change
to make this pass, stop and report** — the block's claim is that the engine runs unmodified, and after
the A0 review round that claim is sharper rather than weaker: eight defects were repaired inside that
engine without either consumer needing a line.

- [ ] **Step 3: Commit the hit path**

```bash
git add consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt
git commit -m "test(agentic-4.5/A0.5): the loop runs end to end on JVM, engine unmodified

Three steps, two bindings, consent at the SAFE -> DANGEROUS transition before
anything is deleted, refusal leaves the file, a second tap does not re-run.
Plus the two the A0 review round earned: the planner's risk equals the
registry's (the gate takes the max of the two since finding F2), and a step
interrupted mid-call is performed once rather than re-issued (finding F1) —
the seam Android could only reach through a 157-170 ms window.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

- [ ] **Step 4: Add the miss-path test**

Append to `AgentLoopTest`:

```kotlin
    @Test
    fun `a missing target rejects the bound step and never reaches the world`() = runTest {
        val store = store()
        val ended = startAndRun(store)

        assertEquals(ExecutionState.Failed, ended.state)
        assertTrue(
            "the binding failed closed rather than deleting something blank",
            ended.trace.events.contains(TraceEvent.StepRejected(2, RejectionReason.UNRESOLVED_ARG_SOURCE)),
        )
        assertTrue(
            "delete_file was never invoked",
            ended.trace.events.none { it is TraceEvent.ToolInvoked && it.index == 2 },
        )
    }
```

- [ ] **Step 5: Add the divergence test — READ THIS STEP BEFORE WRITING IT**

> **Owner instruction, 2026-08-23.** The `Failed` / `Completed` asymmetry this test pins is a **recorded
> architectural finding owned by A1′**, not a defect. "Unify the Android and JVM outcomes" is **not a
> task in this block**. Making them agree requires a new `ObservedFact` value in `commonMain` — the
> Approach B the spec rejected in §2 — and would silently convert a record-don't-fix block into a
> fix-`commonMain` block. If you believe the asymmetry is wrong, that is a conversation with the owner,
> not an edit.

Append to `AgentLoopTest`:

```kotlin
    /**
     * **A recorded architectural finding, held as a test rather than as a paragraph** (spec §6.3,
     * §11.2; owner instruction 2026-08-23).
     *
     * The same shape of reality — "the thing you named is not there" — takes two different paths:
     *  - on the **Android** consumer it has a name, `ObservedFact.APP_NOT_INSTALLED`. It satisfies the
     *    next step's precondition, the store step runs, and the session ends `Completed`.
     *  - **here** it has no name. `find_file` reports a blank `resolved_path`, the binding fails closed
     *    as `UNRESOLVED_ARG_SOURCE`, and the session ends `Failed`.
     *
     * Same reality, two session outcomes and two traces, purely because `ObservedFact` is a closed
     * two-value Android-shaped enum. It is a **trace-fidelity** gap, not a safety gap: the step
     * correctly does not run either way.
     *
     * **THE ASYMMETRY IS INTENDED AND STAYS.** Owned by A1'. If you have just made this test fail by
     * making the two agree, you have changed the thing this block exists to record — revert it, or take
     * the decision to the owner. Relaxing or deleting this test is a conscious act against a named
     * decision, not a tidy-up.
     */
    @Test
    fun `a missing target ends Failed here where Android ends Completed - INTENDED divergence, owned by A1 prime`() = runTest {
        val store = store()
        val ended = startAndRun(store)

        // Half one: the outcome on this consumer.
        assertEquals(
            "the JVM consumer ends Failed for a missing target. If this now says Completed, someone " +
                "has unified the two consumers' outcomes — see the KDoc above; that is not a task here.",
            ExecutionState.Failed,
            ended.state,
        )

        // Half two: WHY it differs, and the mechanical hold. The Android consumer ends `Completed`
        // because it can name the fact. The moment `ObservedFact` gains a value for "the thing is not
        // there", this assertion fails — which is precisely the edit this block has decided not to
        // make, and the reason this test is the guard rather than a comment.
        assertEquals(
            "ObservedFact must stay at A0's two values. A third value here means the finding was " +
                "'fixed' instead of recorded (spec §2 Approach A; owner instruction 2026-08-23).",
            listOf("APP_NOT_INSTALLED", "APP_AMBIGUOUS"),
            ObservedFact.entries.map { it.name },
        )
    }
```

- [ ] **Step 6: Run the miss-path and divergence tests and verify they pass**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*AgentLoopTest*"`

Expected: PASS, 6 tests.

- [ ] **Step 7: Mutate the divergence guard — prove it actually holds**

Confirm the guard is not decorative: add a third value to `ObservedFact` in
`domain/src/commonMain/.../tool/ToolInvocation.kt`, run, confirm **RED** in both `AgentLoopTest` and
`CoreVocabularyFreezeGuardTest`, then restore. One shell invocation, `trap … EXIT`:

```bash
cd /home/Suleiman/Sidr-launcher
F=domain/src/commonMain/kotlin/com/sidr/launcher/domain/tool/ToolInvocation.kt
cp "$F" /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/ToolInvocation.bak
trap 'cp /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/ToolInvocation.bak "$F"' EXIT
sed -i 's/enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS }/enum class ObservedFact { APP_NOT_INSTALLED, APP_AMBIGUOUS, TARGET_NOT_FOUND }/' "$F"
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :consumer:jvm:test :domain:jvmTest --tests "*AgentLoopTest*" --tests "*CoreVocabularyFreezeGuardTest*"
echo "EXIT=$?"
```

Expected: **exit 1**, both guards RED. Record the log. Verify with `git diff --stat` that the trap
restored the file.

- [ ] **Step 8: Commit**

```bash
git add consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/AgentLoopTest.kt
git commit -m "test(agentic-4.5/A0.5): the miss path, and the divergence that stays

A missing target fails the binding closed (UNRESOLVED_ARG_SOURCE) and ends
Failed, where the Android consumer names the fact and ends Completed. Same
reality, two outcomes, because ObservedFact is a closed Android-shaped enum.

Owner instruction 2026-08-23: the asymmetry is a recorded finding owned by A1',
not a defect, and unifying the outcomes is not a task in this block. Held by a
test rather than a paragraph — the guard asserts ObservedFact's two values, so
a 'fix' goes RED. Mutation-verified.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 8: The console driver, and surviving process death

**Files:**
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt`
- Create: `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/Main.kt`
- Test: `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarnessTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 3–6.
- Produces: `class ConsoleHarness(root: Path, out: (String) -> Unit, ask: () -> String?)` with
  `suspend fun run(goalText: String): ExecutionState`; `fun main(args: Array<String>)`.

- [ ] **Step 1: Write the failing test**

Create `consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarnessTest.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.domain.agent.ExecutionState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConsoleHarnessTest {

    @get:Rule val temp = TemporaryFolder()

    private val printed = mutableListOf<String>()

    private fun harness(answers: MutableList<String>) = ConsoleHarness(
        root = temp.root.toPath(),
        out = { printed += it },
        ask = { answers.removeFirstOrNull() },
    )

    @Test
    fun `a granted run completes and prints the whole trace`() = runTest {
        temp.newFile("stale.lock")
        val state = harness(mutableListOf("y")).run("remove stale.lock")

        assertEquals(ExecutionState.Completed, state)
        val log = printed.joinToString("\n")
        assertTrue("the plan is announced", log.contains("PlanCreated(stepCount=3)"))
        assertTrue("the gate is visible", log.contains("ConsentRequested"))
        assertTrue("the outcome is stated", log.contains("Completed"))
    }

    @Test
    fun `refusing at the prompt cancels`() = runTest {
        temp.newFile("stale.lock")
        val state = harness(mutableListOf("n")).run("remove stale.lock")
        assertEquals(ExecutionState.Cancelled, state)
    }

    @Test
    fun `an unreadable goal says so instead of guessing`() = runTest {
        val state = harness(mutableListOf()).run("what is the weather")
        assertEquals(ExecutionState.Blocked, state)
        assertTrue(printed.joinToString("\n").contains("No plan"))
    }

    /**
     * M-A1's "the session survives process death", on the second consumer. Answering nothing at the
     * prompt is this harness's stand-in for the process dying at the gate: the run returns with the
     * session persisted and unresolved. A **fresh** harness over the same directory must then find it
     * and present it as `Paused` — never resume it silently, because the person asked for this
     * minutes or days ago.
     */
    @Test
    fun `a session left at the gate is found by a fresh harness and offered, not resumed`() = runTest {
        temp.newFile("stale.lock")

        val first = harness(mutableListOf()).run("remove stale.lock")
        assertEquals(ExecutionState.AwaitingConsent, first)

        printed.clear()
        val resumed = harness(mutableListOf("y")).run("remove stale.lock")

        assertTrue("the fresh harness reports the pause", printed.joinToString("\n").contains("Paused"))
        assertTrue("it recorded SessionPaused, not a silent resume", printed.joinToString("\n").contains("SessionPaused"))
        assertEquals(ExecutionState.Completed, resumed)
    }

    /**
     * **The second shape of process death: the process died DURING a tool call** — spec §9, required
     * rather than optional since the spec was re-anchored to `a7f4755`.
     *
     * A0's finding F1 was that the engine's own "died mid-call" signal — `ToolInvoked(i)` with no
     * `ToolObserved(i)` — was unreadable on the only path that produces it, because the restore
     * transitions write `SessionPaused` and `SessionResumed` on top of it and the predicate was keyed
     * on the trace *tail*. The step was re-cleared, re-traced and re-run; a step whose consent had
     * already been granted ran twice. Android could reach that window only with `pm disable-user` plus
     * an on-device `force-stop` poll, 157-170 ms wide. **This consumer reaches it by seeding a file.**
     *
     * Note what is simulated and what is not. A live process cannot be asked to die, so the mid-call
     * *state* is seeded through the store — that is setup. The **transition under test** — find it,
     * offer it, continue — is walked by the harness itself, which is what the Global Constraints rule
     * about simulated paths demands. Seeding the state and then also hand-rolling the restore would
     * reproduce exactly the mistake that cost A0 three findings.
     */
    @Test
    fun `a session that died mid-call is offered, and the pending call runs once`() = runTest {
        temp.newFile("stale.lock")
        val store = JvmAgentSessionStore(temp.root.toPath().resolve(".sidr-agent/session.json"))
        val registry = SandboxToolSource()
        val executor = AgentExecutor(registry, SandboxToolExecutor(temp.root.toPath()))
        val goal = AgentGoal("remove stale.lock", GoalShape.Free("remove stale.lock"))

        StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
            .start(goal)
        val prepared = executor.prepare((store.active() as OperationResult.Success).value!!)
        store.save(prepared)
        assertTrue(
            "the seed must be mid-call, was ${prepared.trace.events.last()}",
            prepared.trace.events.last() is TraceEvent.ToolInvoked,
        )

        val state = harness(mutableListOf("y")).run("remove stale.lock")

        val log = printed.joinToString("\n")
        assertTrue("the fresh harness reports the pause", log.contains("Paused"))
        assertEquals(
            "one pending call is one ToolInvoked, however many times the process died:\n$log",
            1,
            Regex("ToolInvoked\\(0,").findAll(log).count(),
        )
        assertEquals(ExecutionState.Completed, state)
    }
}
```

The harness test needs these imports beside the ones above:

```kotlin
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.trace.TraceEvent
```

- [ ] **Step 2: Run it and verify it fails**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*ConsoleHarnessTest*"`

Expected: FAIL — `Unresolved reference: ConsoleHarness`.

- [ ] **Step 3: Write the harness**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm

import com.sidr.launcher.consumer.jvm.plan.FilePlanner
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionIdFactory
import com.sidr.launcher.consumer.jvm.store.JvmAgentSessionStore
import com.sidr.launcher.consumer.jvm.tool.SandboxToolExecutor
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.trace.TraceEvent
import java.nio.file.Path

/**
 * The second consumer's driver: a console harness, not a product and not a UI.
 *
 * **It never calls `ToolExecutor.invoke` itself.** It drives [AgentExecutor], which owns the one call
 * site below the consent checkpoint — the property `ToolExecutorCallSiteGuardTest` holds mechanically
 * once Task 9 extends it to this module. A second consumer must not become a second way to reach the
 * world.
 *
 * [out] and [ask] are injected so the loop is testable without a terminal; `main` supplies the real
 * ones. All user-facing text here is English developer output, and that is a recorded exception rather
 * than a gap: the strings-in-the-same-commit rule governs product surfaces, and this module ships none
 * (spec §13).
 */
class ConsoleHarness(
    root: Path,
    private val out: (String) -> Unit,
    private val ask: () -> String?,
) {
    private val registry = SandboxToolSource()
    private val store = JvmAgentSessionStore(root.resolve(".sidr-agent/session.json"))
    private val executor = AgentExecutor(registry, SandboxToolExecutor(root))

    // Named `runner`, not `run`: this class already has a `run` member, and `kotlin.run { }` is used
    // nowhere here precisely so no reader has to work out which `run` a call resolves to.
    private val runner = RunAgentSessionUseCase(executor, store)

    private var printed = 0

    suspend fun run(goalText: String): ExecutionState {
        val restored = restore()
        val session = restored ?: start(goalText) ?: return ExecutionState.Blocked

        val ran = when (val result = runner.run(session)) {
            is OperationResult.Failure -> {
                out("Store failure: ${result.error}")
                return ExecutionState.Failed
            }
            is OperationResult.Success -> result.value
        }

        printTrace(ran)
        if (ran.state != ExecutionState.AwaitingConsent) {
            out("Outcome: ${ran.state}")
            return ran.state
        }

        return gate(ran)
    }

    /**
     * A session that outlived its process is presented as `Paused` and **never** resumed silently —
     * `AgentSession.pausedForRestore` records `SessionPaused` so the trace stays 1:1 with reality,
     * including "the process died and we stopped here".
     */
    private suspend fun restore(): AgentSession? {
        val active = (store.active() as? OperationResult.Success)?.value ?: return null
        val paused = active.pausedForRestore()
        out("Found an unfinished session: ${paused.id.value} — Paused at step ${paused.cursor}.")
        printTrace(paused)
        store.save(paused)
        return paused.resumed()
    }

    private suspend fun start(goalText: String): AgentSession? {
        val goal = AgentGoal(text = goalText, shape = GoalShape.Free(goalText))
        val started = StartAgentSessionUseCase(FilePlanner(), store, JvmAgentSessionIdFactory(), registry)
        val id = when (val result = started.start(goal)) {
            is OperationResult.Failure -> { out("Store failure: ${result.error}"); return null }
            is OperationResult.Success -> result.value
        }
        if (id == null) {
            out("No plan for: \"$goalText\". This harness understands: remove <file name>")
            return null
        }
        return (store.active() as? OperationResult.Success)?.value
    }

    private suspend fun gate(session: AgentSession): ExecutionState {
        val step = session.cursor
        val descriptor = session.plan.steps.getOrNull(step)
        out("")
        out("Consent required for step $step: ${descriptor?.invocation?.id?.value} (risk ${descriptor?.risk}).")
        out("Proceed? [y/N]")

        // No answer available — stdin closed, or the person walked away. This is the harness's stand-in
        // for the process dying at the gate: return with the session persisted and unresolved, so a
        // later run finds it. Anything else would be the harness deciding on the person's behalf.
        val answer = ask()
        if (answer == null) {
            out("No answer given — the session stays at the gate and survives this process.")
            return ExecutionState.AwaitingConsent
        }
        val granted = answer.trim().lowercase() == "y"

        val outcome = ResolveConsentUseCase(store, runner).resolve(session.id, step, granted)
        val resolved = when (outcome) {
            is OperationResult.Failure -> {
                out("Store failure: ${outcome.error}")
                return ExecutionState.Failed
            }
            is OperationResult.Success -> outcome.value
        }

        if (resolved == null) {
            // The decision did not apply — the step was no longer awaiting one, which is exactly what
            // a second tap looks like. Reporting it is honest; re-asking would be the harness deciding
            // on the person's behalf.
            out("That decision no longer applies — the step was not awaiting one.")
            return ExecutionState.AwaitingConsent
        }

        printTrace(resolved)
        out("Outcome: ${resolved.state}")
        return resolved.state
    }

    private fun printTrace(session: AgentSession) {
        session.trace.events.drop(printed).forEach { out("  ${render(it)}") }
        printed = session.trace.events.size
    }

    private fun render(event: TraceEvent): String = when (event) {
        is TraceEvent.PlanCreated -> "PlanCreated(stepCount=${event.stepCount})"
        is TraceEvent.StepStarted -> "StepStarted(${event.index})"
        is TraceEvent.StepSkipped -> "StepSkipped(${event.index}, ${event.precondition})"
        is TraceEvent.StepRejected -> "StepRejected(${event.index}, ${event.reason})"
        is TraceEvent.ConsentRequested -> "ConsentRequested(${event.index}, ${event.reason})"
        is TraceEvent.ConsentResolved -> "ConsentResolved(${event.index}, granted=${event.granted})"
        is TraceEvent.ToolInvoked -> "ToolInvoked(${event.index}, ${event.toolId.value})"
        is TraceEvent.ToolObserved -> "ToolObserved(${event.index}, ${event.result})"
        TraceEvent.SessionPaused -> "SessionPaused"
        TraceEvent.SessionResumed -> "SessionResumed"
        is TraceEvent.SessionEnded -> "SessionEnded(${event.state})"
    }
}
```

- [ ] **Step 4: Write `main`**

Create `consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/Main.kt`:

```kotlin
package com.sidr.launcher.consumer.jvm

import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * The second consumer's entry point. Usage:
 *
 * ```
 * ./gradlew :consumer:jvm:run --args="<sandbox dir> remove <file name>"
 * ```
 *
 * Not a product and not a desktop application (Master Plan §3.1a, "не продукт и не десктопное
 * приложение, а консольный harness").
 */
fun main(args: Array<String>) {
    if (args.size < 2) {
        println("usage: <sandbox directory> <goal>   e.g.  /tmp/box remove stale.lock")
        return
    }
    val root = Paths.get(args.first()).toAbsolutePath()
    val goal = args.drop(1).joinToString(" ")

    val state = runBlocking {
        ConsoleHarness(root = root, out = ::println, ask = ::readlnOrNull).run(goal)
    }
    println("Final state: $state")
}
```

- [ ] **Step 5: Run the harness tests and verify they pass**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :consumer:jvm:test --tests "*ConsoleHarnessTest*"`

Expected: PASS, **5 tests** — the fifth is the died-mid-call demonstration the spec requires since it
was re-anchored to `a7f4755`.

**If the mid-call test fails with two `ToolInvoked(0, …)` lines, do not touch the test.** That is A0
finding F1 reappearing, and it means something in `AgentExecutor.midStepInvocation()` went back to
reading the trace *tail* instead of the last `ToolInvoked`. Stop and report: the engine is supposed to
run unmodified in this block, so a regression there is a finding about the core, not about the harness.

- [ ] **Step 6: Commit**

```bash
git add consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt \
  consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/Main.kt \
  consumer/jvm/src/test/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarnessTest.kt
git commit -m "feat(agentic-4.5/A0.5): console harness — the loop, driven from a terminal

Never calls ToolExecutor.invoke itself; it drives AgentExecutor, which owns the
one call site below the checkpoint. A session left at the gate is found by a
fresh harness, presented as Paused with SessionPaused in the trace, and never
resumed silently — M-A1's 'survives process death', on the second consumer.

Two shapes of that death, not one: at the gate, and DURING a tool call. The
second is the seam A0's finding F1 exposed, and this consumer reaches it by
seeding a file where Android needed pm disable-user plus an on-device poll
inside a 157-170 ms window.

English developer output, recorded as an exception rather than a gap: the
strings rule governs product surfaces and this module ships none.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 9: Extend the boundary to the second consumer (`F5`)

**Files:**
- Modify: `app/src/test/java/com/sidr/launcher/agent/ToolExecutorCallSiteGuardTest.kt:44-77, 128-148`
- Modify: `app/build.gradle.kts:319-327`

**Interfaces:**
- Consumes: `SandboxToolExecutor` (Task 4).
- Produces: no production code. Extends the mechanical boundary to cover the second consumer.

> **`AgentVocabularyGuardTest` is NOT touched by this task, and two facts about it are easy to
> misread.** It scans `domain/agent`, `domain/tool` **and `domain/trace`** — the third was added
> 2026-08-23 by A0 finding F7, because `CLAUDE.md` and the contract table have always called those
> three the portable engine while the guard only covered two. And it deliberately does **not** reach
> into `consumer/jvm`: what must stay free of action vocabulary and transports is the *engine*; a
> consumer may name whatever it likes, which is what being a consumer means. Extending it would
> misstate what the guard protects (spec §10).

- [ ] **Step 1: Add the scan root**

In `ToolExecutorCallSiteGuardTest.kt`, replace `productionRoots`:

```kotlin
    private val productionRoots: List<File> =
        kmpProductionRoots(File(repoRoot, "domain/src")) +
            listOf(
                "data/repository/src/main/java",
                "feature/launcher/src/main/java",
                "app/src/main/java",
                // A0.5 — the second consumer. Its `SandboxToolExecutor` is a second path to the world,
                // so it belongs inside this scan and not outside it: Master Plan §4's growth rule puts
                // boundaries on the first slice, never behind the second consumer. Declared as a `Test`
                // input in app/build.gradle.kts in the same commit, or the widened guard silently would
                // not re-run.
                "consumer/jvm/src/main/kotlin",
            ).map { File(repoRoot, it) }
```

- [ ] **Step 2: Update the holder list to four**

In the same file, replace the expectation in `the declared holders of a ToolExecutor are exactly the
known three`, **including its name**:

```kotlin
    /**
     * The scan the call-site count cannot do for itself. Asserted as a **sorted list, not a size**: a
     * holder that disappears has to be as red as one that appears, because a vanished holder means the
     * wiring moved and this guard's premise needs re-reading, not a quietly decremented number.
     *
     * The four, and why each is legitimate:
     *  - `AgentExecutor.kt` — the one holder, and the one call site, below the checkpoint;
     *  - `AgentProvidesModule.kt` — the Hilt binding and the `AgentExecutor` factory that passes it on;
     *  - `SandboxToolExecutor.kt` — A0.5's implementation, the second consumer's only path to the world;
     *  - `SystemIntentToolExecutor.kt` — the Android implementation, matched on its supertype line.
     *
     * Two implementations are now legitimate, and that changes nothing about the property this guard
     * holds: **one call site**, not one implementation. A second consumer may reach the world by its own
     * adapter; it may not reach it by its own call.
     */
    @Test
    fun `the declared holders of a ToolExecutor are exactly the known four`() {
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
                "SandboxToolExecutor.kt",
                "SystemIntentToolExecutor.kt",
            ),
            files,
        )
    }
```

Also update the class KDoc's line "exactly three files **declare** the type" to read "exactly four
files", and name `SandboxToolExecutor` beside the other three.

- [ ] **Step 3: Declare the new scan root as a Gradle input**

In `app/build.gradle.kts`, after the `launcherSources` declaration (line ~325):

```kotlin
    // A0.5 — ToolExecutorCallSiteGuardTest now scans the second consumer too. A test that reads files
    // outside its own source set is not an input Gradle can infer: without this line the task stays
    // UP-TO-DATE and the widened guard silently does not re-run. One named directory, not the repo-wide
    // widening that findings D1/D3/D8 park as an owner-level build trade-off.
    inputs.dir(rootProject.file("consumer/jvm/src/main/kotlin"))
        .withPropertyName("consumerJvmSources")
        .withPathSensitivity(PathSensitivity.RELATIVE)
```

- [ ] **Step 4: Run the guard and verify it passes**

Run: `./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 :app:testDebugUnitTest --tests "*ToolExecutorCallSiteGuardTest*"`

Expected: PASS, 4 tests.

- [ ] **Step 5: Mutation A — a second call site in the new module must be RED**

```bash
cd /home/Suleiman/Sidr-launcher
F=consumer/jvm/src/main/kotlin/com/sidr/launcher/consumer/jvm/ConsoleHarness.kt
cp "$F" /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/ConsoleHarness.bak
trap 'cp /tmp/claude-1000/-home-Suleiman-Sidr-launcher/45d1202b-cd50-42f7-a282-896db00616d7/scratchpad/ConsoleHarness.bak "$F"' EXIT
printf '\n// probe\nprivate val toolExecutor: Any? = null\nprivate suspend fun probe() { toolExecutor.invoke(Unit) }\n' >> "$F"
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :app:testDebugUnitTest --tests "*ToolExecutorCallSiteGuardTest*"
echo "EXIT=$?"
```

Expected: **exit 1**, `ToolExecutor.invoke must have exactly one call site`. This is the assertion that
proves the second consumer is genuinely inside the boundary and not merely adjacent to it. Record the
log; confirm the restore with `git diff --stat`.

- [ ] **Step 6: Mutation B — a fourth declared holder must be RED**

Same shape, but plant `class Probe : ToolExecutor` (a bare `: ToolExecutor` supertype line is enough —
the regex is `:\s*ToolExecutor\b`) in a new file under `consumer/jvm/src/main/kotlin`, run the guard,
expect **exit 1** on `the declared holders of a ToolExecutor are exactly the known four`, then delete
the file.

- [ ] **Step 7: Mutation C — prove the Gradle input declaration actually works**

This is the `m7` lesson and the only mutation of the three that is easy to fake. A mutation that shifts
`:consumer:jvm`'s compiled output re-triggers `:app:testDebugUnitTest` for an unrelated reason and
reports a false pass. The mutation must be **bytecode-identical**: add a comment line to
`SandboxToolExecutor.kt` **and delete one blank line** from the same file, so the compiled class is
byte-for-byte unchanged.

1. Remove the `consumerJvmSources` `inputs.dir` block from `app/build.gradle.kts`.
2. Run the guard once to populate the cache (green).
3. Apply the balanced mutation — planting a `toolExecutor.invoke(` inside a comment will not do; plant
   it as **code** in a way that is bytecode-neutral, e.g. rename an existing private val to
   `toolExecutor` and add the call, then remove an equal number of blank lines.
4. Re-run: with the inputs block removed, expect `:app:testDebugUnitTest` **FROM-CACHE / UP-TO-DATE**
   and exit **0** — a stale green.
5. Restore the inputs block, re-run with the same mutation in place: expect **RED**.
6. Revert the mutation.

Record all four outcomes. Step 4 green + step 5 red together are what prove the trap is closed; either
alone proves nothing.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/java/com/sidr/launcher/agent/ToolExecutorCallSiteGuardTest.kt app/build.gradle.kts
git commit -m "test(agentic-4.5/A0.5): the call-site boundary now covers the second consumer

Fifth scan root, fourth declared holder, and the call-site count stays at ONE:
a second consumer may reach the world by its own adapter, never by its own
call. One inputs.dir line so the widened guard actually re-runs — the
UP-TO-DATE trap, closed the same way A0 closed it.

Three mutations logged: a second call site RED, a fourth holder RED, and the
bytecode-identical m7-style pair that proves the input declaration is load-
bearing rather than decorative.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 10: Gate, ADR, document sync, commit proposal

**Files:**
- Modify: `ai-context/decisions.md` (append one ADR)
- Modify: `CLAUDE.md` (stage table, Shipped surface, Known debt, Contract → Owner module)
- Modify: `ai-context/current-status.md`
- Modify: `docs/superpowers/plans/2026-08-18-agentic-track-restart.md` (`§HANDOFF`)

- [ ] **Step 1: Run the full gate**

```bash
cd /home/Suleiman/Sidr-launcher
git status --short   # gradle-daemon-jvm.properties must be absent; gradle.properties stays modified
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :domain:jvmTest testDebugUnitTest assembleDebug :consumer:jvm:test
echo "EXIT=$?"
```

Expected: `BUILD SUCCESSFUL`, exit 0. **Read the real output; never pipe through `tail`.** Record the
task count and the test count. If anything is red, stop — the block is not closeable.

- [ ] **Step 2: Prove `core/ui` is untouched**

```bash
./gradlew --no-daemon -Porg.gradle.java.installations.paths=/home/Suleiman/jdks/jdk-17.0.19+10 \
  :core:ui:verifyRoborazziDebug
echo "EXIT=$?"
```

Expected: exit 0, task executed, no golden changes.

- [ ] **Step 3: Prove the Android surface diff is empty**

```bash
git diff --stat a7f4755..HEAD -- feature core data app
```

**The baseline is `a7f4755`, not `7442da4`.** `7442da4` closed A0; `a7f4755` is the A0 review round
that followed it, and it legitimately touched `feature/`, `data/` and `app/`. Diffing from the older
commit would attribute that block's changes to this one and the check would report a false widening.

Expected: **only** `app/build.gradle.kts` (one `inputs.dir` block) and
`app/src/test/java/com/sidr/launcher/agent/ToolExecutorCallSiteGuardTest.kt`. Any other file under
`feature/`, `core/` or `data/` means the block widened beyond its scope — stop and report.

- [ ] **Step 4: Write the ADR**

Append to `ai-context/decisions.md`, following the house style of the A0 ADR. It must contain, at
minimum:

- **Status line** — `CODE-GREEN`, with `DEVICE-ACCEPTED` marked **not applicable** rather than absent,
  and the reason (the block changes nothing on the phone; requiring acceptance would pretend it does).
- **The seven owner-resolved forks** and the two rejected approaches (B and C) with their reasons.
- **§3's factual corrections to the brief**, verbatim: the `jvm()` target has zero *product* consumers
  rather than zero consumers (`:core:testing` resolves `jvmApiElements`); the eight `java.*` imports sit
  outside the engine, which A0 grew by ~1085 lines without adding one. Include the `dependencyInsight`
  output from Task 1 Step 5 as the evidence.
- **The four §3.1a answers**, negative ones included, each with its address — spec §11.1–§11.4. Copy
  §11.3's finding **verbatim**, including the sentence "The user is still stopped", which is
  load-bearing: without it the finding reads as though durable actions slip past consent, which is
  false.
- **The §6.3 divergence**, as a recorded architectural finding owned by A1′, with the owner instruction
  of 2026-08-23 that it stays and is not to be unified, and a note that it is held by a test rather than
  by prose.
- **`B1`'s answer** (spec §12) and the note that `GoalShape.Free` cannot start a taxonomy.
- **Measured numbers, not estimates:** the mapper line count from Task 6 Step 7 beside
  `AgentSessionMappers`' own, as the reported cost of "one mapper per consumer"; the gate's task and
  test counts.
- **The mutation table** — every mutation from Tasks 4, 7 and 9 with its observed result.
- **What this block does NOT close:** no doctrine row; the A1 fork still open; `ObservedFact`,
  `CommandFailure`, `StepRationale`, `ArgType` all recorded against A1′; `DURABLE_EFFECT` and the
  execution-model layers against A4′.
- **Two things the A0 review round (`a7f4755`) hands this ADR, and they belong in §11.2's write-up.**
  First: eight defects were repaired inside `commonMain`'s engine and **not one** needed a platform
  branch, an `expect`/`actual`, or a `java.*` import — `:consumer:jvm` inherited every fix by existing.
  A core that can be repaired in eight places without either consumer noticing is portable in a way
  that "compiles for two targets" is not, and this block is the first place that claim is checkable.
  Second: risk now has a **third** owner beside the two adapters — the persisted plan, whose snapshot
  can disagree with the registry that outlived it — and the core resolves that conservatively with
  `maxOf` rather than picking a winner. Both go under question 3 (§11.3), which stays *renamed with an
  address*, not closed.

- [ ] **Step 5: Sync `CLAUDE.md`**

Four edits, and no history — `CLAUDE.md` is current state, rules and pointers only:

1. **Stage table** — the `4.5–7` row becomes a closed `4.5` row for A0.5 (`CODE-GREEN`, device
   acceptance not applicable), with A1′/A4′/A2/A3/A5/A6 still needing their own specs.
2. **Shipped surface** — a line for the second consumer: `:consumer:jvm`, three sandbox tools, its own
   planner and store, the same engine.
3. **Known debt** — add the four A1′-addressed vocabulary findings and note `DURABLE_EFFECT`'s
   reachability against A4′. Do **not** restate the whole ADR.
4. **Contract → Owner module** — one row: `SandboxToolSource` / `SandboxToolExecutor` / `FilePlanner` /
   `JvmAgentSessionStore` / `JvmAgentSessionIdFactory` → `consumer/jvm`.

- [ ] **Step 6: Sync `ai-context/current-status.md` and rewrite `§HANDOFF`**

`§HANDOFF` is written **by the closing session for the next one**. It must say: A0.5 is closed
`CODE-GREEN`; the next block is A1′ and it does **not** start automatically; the A1 fork is still
undecided and A0.5 deliberately left both branches equally cheap; the four vocabulary findings now
waiting at A1′'s door, with the recommendation from spec §11.2 (the two contracts that survived are the
two built as open value types) explicitly marked as an observation and **not** a decision; and the
standing instruction that the §6.3 divergence is not to be "fixed".

- [ ] **Step 7: Commit and propose to the owner**

```bash
git add ai-context/decisions.md CLAUDE.md ai-context/current-status.md \
  docs/superpowers/plans/2026-08-18-agentic-track-restart.md
git commit -m "docs(agentic-4.5/A0.5): ADR, document sync, HANDOFF — block CODE-GREEN

<summary of the gate result, the measured numbers and the four recorded answers>

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

Then **propose the commit series to the owner and stop.** The agent commits; the agent never pushes.
A1′ is not started (Master Plan §4 DoD: the next block is not begun automatically).

---

## Self-review notes

**Spec coverage.** §4 module → Task 1. §5 tools → Tasks 3–4. §6 plan and both paths → Tasks 5, 7.
§6.2's `maxOf(plan, registry)` gate → Task 7 Step 1 (the risk-agreement test). §6.3 divergence and the
owner instruction → Task 7 Steps 5, 7. §7 the two `commonMain` edits → Task 2.
§8 store and CAS → Task 6. §9 driver and **both** process-death shapes → Task 8 (died-at-the-gate) and
Task 7 Step 1 + Task 8 Step 1 (died-mid-call). §10 guards and mutations → Task 9.
§11.1–§11.4 the four answers → evidence produced in Tasks 3, 4, 6, 7, 8; written up in Task 10 Step 4.
§12 `B1` → Task 2 and Task 10 Step 4. §13 doctrine and localization → Task 10 Steps 4–5. §14
verification → Task 1 Step 5, Task 10 Steps 1–3. §16 work order → the task order here.

**Type consistency.** `SandboxToolIds` / `SandboxKeys` (Task 3) are used by Tasks 4, 5, 6, 7.
`SandboxToolSource()` and `SandboxToolExecutor(root: Path)` keep those exact constructor shapes in
Tasks 5–8. `JvmAgentSessionStore(file: Path)` takes the **session file**, not a directory — Task 8
passes `root.resolve(".sidr-agent/session.json")`. `GoalShape.Free(text)` (Task 2) is the only shape
Tasks 5–8 construct, and `SessionMapper.toDto` throws on any other, which is what keeps a wrongly-built
session from being silently persisted. `ConsoleHarness` names its `RunAgentSessionUseCase` property
`runner`, because the class already has a `run` member.

**Known soft spots, named rather than hidden.**
- Task 9 Step 7 is the hardest step in the plan to execute honestly. If a bytecode-identical mutation
  cannot be constructed, **say so and report the attempt** rather than substituting a naive mutation —
  a naive one reports a false pass, which is exactly what the `m7c` row of A0 caught.
- Task 6 is much larger than the others and could be split. It is kept whole because its deliverable —
  "does a file store honour the CAS contract" — is one reviewable question, and half a store is not
  independently testable.
- The two mid-call tests (Task 7 and Task 8) look like duplicates and are not. Task 7's drives the
  **use cases** and proves the engine performs the pending call once; Task 8's drives the **harness**
  and proves the product path — restore, offer, continue — reaches that engine behaviour. Keeping only
  the first would repeat the mistake that cost A0 three findings: a test that stands in for the path
  instead of walking it. Keeping only the second would leave the engine property provable only through
  a console driver.
