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
 *
 * **Two scans, because one of them fails open on its own.** The call-site scan matches the literal
 * receiver spelling `toolExecutor.invoke(`; a second call reached through any other property name —
 * `executor.invoke(resolved)` — is invisible to it, and matching `\.invoke\(` on any receiver instead
 * would drown in `Function0.invoke` and every unrelated `operator invoke` in the tree. So the gap is
 * closed one step earlier, at the **declaration**: a new call site needs a new holder of the type, and
 * holders are few and declarative. `the declared holders of a ToolExecutor are exactly the known three`
 * pins that set, so an injected `private val executor: ToolExecutor` is red before it is ever called.
 *
 * **What is actually enforced, stated plainly** (the KDoc on `ToolExecutor` used to claim more, and
 * this list said "below the checkpoint" until the A0 Task 14 review, 2026-08-22, pointed out that
 * nothing here reads a position):
 *  - exactly one call spelled `toolExecutor.invoke(`, in the **file** `AgentExecutor.kt`. Where in that
 *    file it sits is NOT checked: moving it into `prepare` above the consent checkpoint keeps all four
 *    assertions green. "Below the checkpoint" is held behaviourally by `AgentExecutorTest`, not here;
 *  - exactly three files that declare the type at all — the holder (`AgentExecutor`), the DI module
 *    (`AgentProvidesModule`), and — since A1′ collapsed the former two adapters into `ToolWorker`s
 *    behind a dispatcher — the **one** implementation, `ToolFederation`. The second hop those former
 *    adapters now sit behind has its own sibling guard, `ToolWorkerCallSiteGuardTest`.
 *
 * Both scans run over comment-stripped text ([stripComments]), so documentation that spells a call or
 * a type in prose cannot turn the guard red on correct code. That stripping's own false-negative
 * direction is named on [stripComments] itself: it can hide text from a scan, never invent a hit.
 *
 * The roots it walks are declared as `Test` inputs in `app/build.gradle.kts`, which is what keeps the
 * task from staying UP-TO-DATE when only a scanned source changes; that build file records, per root,
 * which of those declarations is actually load-bearing and which is defence in depth. What
 * "the roots" are, and what they still miss, is on [productionRoots] rather than implied here.
 */
class ToolExecutorCallSiteGuardTest {

    private val repoRoot = File("..")

    /**
     * `:domain`'s roots are **derived** ([kmpProductionRoots]) and the other four are hard-coded.
     *
     * The asymmetry is deliberate and measured, not an oversight. `:domain` is
     * `kotlin.multiplatform` + `com.android.library`: a holder planted at `domain/src/main/java` or
     * `domain/src/androidMain/kotlin` compiles into the shipped artifact, and the old
     * `domain/src/commonMain/kotlin` entry saw neither. This is the guard that mechanically holds the
     * consent boundary, so a known fail-open direction in it is not something to leave for later — a
     * second holder is exactly how a second call site arrives. Three of the other four are
     * single-variant android modules whose production Kotlin is `src/main/java`; the fourth,
     * `consumer/jvm`, is a plain `kotlin.jvm` module laid out at `src/main/kotlin`.
     *
     * **Named, not closed:** those three android roots still miss `src/main/kotlin`, the hard-coded
     * `consumer/jvm` root misses the `src/main/java` that the `kotlin.jvm` plugin also compiles, and
     * no module outside these five is scanned at all. Widening *that* needs the `Test` inputs
     * declarations in `app/build.gradle.kts` widened in step, or the guard gains reach it cannot
     * re-run for; it is an owner-level build trade-off parked in `§HANDOFF`. The same staleness caveat
     * already applies to the derived `:domain` roots: only `domain/src/commonMain/kotlin` is a
     * declared input.
     */
    private val productionRoots: List<File> =
        kmpProductionRoots(File(repoRoot, "domain/src")) +
            listOf(
                "data/repository/src/main/java",
                "feature/launcher/src/main/java",
                "app/src/main/java",
                // A0.5 — the second consumer. Its `SandboxToolWorker` is a second path to the world,
                // so it belongs inside this scan and not outside it: Master Plan §4's growth rule puts
                // boundaries on the first slice, never behind the second consumer. Declared as a `Test`
                // input in app/build.gradle.kts in the same commit — defence in depth rather than the
                // load-bearing mechanism, since the repo-wide `**/src/main/**/*.kt` tree declared there
                // already covers this directory today; that build file records which declaration is
                // which, and why this one is declared anyway.
                "consumer/jvm/src/main/kotlin",
            ).map { File(repoRoot, it) }

    /**
     * A declaration of the type: a constructor/parameter/property type, a return type, or a supertype
     * list entry. `\b` is what keeps a name merely *containing* `ToolExecutor` from matching as that
     * name — pre-A1′, `impl: SystemIntentToolExecutor` had `S…` right after `:\s*`, not `ToolExecutor`,
     * so only that same file's own `) : ToolExecutor {` supertype line matched. Today's live example is
     * `ToolFederation.kt`'s `val executor: ToolExecutor = object : ToolExecutor {` — both matches, one
     * file.
     */
    private val declaresToolExecutor = Regex(""":\s*ToolExecutor\b""")

    private fun productionSources(): List<File> =
        productionRoots.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }

    @Test
    fun `scanned roots all exist`() {
        // A guard whose walk finds nothing passes vacuously — the exact failure Этап 2 found in three
        // privacy guards. Assert the roots first. Since :domain's are derived, the derivation itself
        // can come back empty, which is the same failure one step earlier.
        assertEquals(
            "no :domain source root was derived — the module layout moved and this guard went blind " +
                "to the module that declares and calls ToolExecutor",
            true,
            kmpProductionRoots(File(repoRoot, "domain/src")).isNotEmpty(),
        )
        productionRoots.forEach { root ->
            assertEquals("missing scan root: $root", true, root.isDirectory)
        }
    }

    @Test
    fun `there is exactly one call site of ToolExecutor invoke in production code`() {
        val hits = productionSources()
            .flatMap { file ->
                stripComments(file.readText())
                    .lines()
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
        val files = productionSources()
            .filter { stripComments(it.readText()).contains("toolExecutor.invoke(") }
            .map { it.name }

        assertEquals(listOf("AgentExecutor.kt"), files)
    }

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
}
