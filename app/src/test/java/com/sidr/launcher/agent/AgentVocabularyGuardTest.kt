package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The A1 fork, held open mechanically.** `domain/agent`, `domain/tool` and `domain/trace` are the
 * portable engine.
 * If a single reference to the action vocabulary — `LauncherAction`, `ActionCatalog`, `ActionId` — or
 * to a transport — `GenerativeAiEngine`, `CommandPlanner`, Ktor — grows inside them, then A1' can no
 * longer choose "a parallel tool vocabulary" without first unpicking the engine, and the fork CLAUDE.md
 * calls deliberately undecided would have been decided by drift rather than by a decision.
 *
 * Three types are **explicitly allowed** and are the reuse the A0 spec sanctioned: `ActionArg`,
 * `ActionRiskLevel` and `PermissionFeature` (see `ToolDescriptor`). They are value shapes, not the
 * vocabulary — a tool declares an arg schema and a risk level; it does not name an action.
 *
 * **Comments are not code, and this guard has to know the difference.** Four of the forbidden terms
 * already appear in these two directories today, every one of them inside KDoc that exists precisely
 * to explain the boundary (`AgentExecutor`'s "the engine knows nothing of `LauncherAction`…",
 * `ToolId`/`ToolRegistry` on why the ids are repeated rather than imported). A naive substring scan
 * would go red on correct code and the only ways out would be rewording honest documentation or
 * carrying an exemption list — both worse than the problem.
 * `raw sources do contain forbidden terms…` below is what keeps the stripping from turning into a
 * vacuous pass.
 *
 * **An import-only scan would not be enough.** `ExecutionPlan.kt` writes
 * `com.sidr.launcher.domain.tool.ToolRegistry` fully qualified and inline, with no import — proof that
 * a type can be referenced in this codebase without ever appearing in an import list. So the scan runs
 * over all non-comment text, and `the scan catches an import and a fully qualified reference alike`
 * pins both shapes.
 *
 * Working directory is the module dir, so the repo root is `..` — same convention as the i18n and
 * doctrine guards. The scanned root is declared as a `Test` input in `app/build.gradle.kts`; :domain's
 * segment is `commonMain`, so it matches none of the existing repo-wide `src/main` declarations and
 * without that line this guard silently would not re-run.
 *
 * **Residual, named — the input declaration is narrower than the scan.** That declaration is
 * `domain/src/commonMain/kotlin` specifically, while [engineRoots] now derives every non-test source
 * set. A violation planted in `domain/src/main/java` or `domain/src/androidMain/kotlin` would be
 * *scanned* but might not *re-trigger* the task, so `:app:testDebugUnitTest` could serve a stale green
 * — the same staleness trap the declaration was added to close. Widening it is an owner-level call on
 * the Gradle inputs block, parked in `§HANDOFF`; whoever takes it adds `domain/src` as the input.
 */
class AgentVocabularyGuardTest {

    private val repoRoot = File("..")

    private val domainSrc = File(repoRoot, "domain/src")

    /**
     * **Derived, not hard-coded — `:domain` is KMP.** What is enforced, precisely: `domain/agent`,
     * `domain/tool` and `domain/trace` under **every non-test source set** of `:domain`, in both its
     * `kotlin/` and `java/` directories.
     *
     * `trace` was added 2026-08-23 (review finding F7). The spec named two packages and this guard
     * scanned two, while `CLAUDE.md` and the contract table have always called **three** of them the
     * portable engine — and `TraceEvent` is the one type in the set that carries another type's value
     * in a field, so it is the likeliest place for the action vocabulary to arrive by accident. See [kmpProductionRoots] for which directories this module's compilers
     * actually read, measured rather than assumed, and for the limitations of deriving this from a
     * filesystem convention.
     *
     * Two earlier versions of this list were narrower than the claim above them, which is the defect
     * this guard exists to prevent, in the guard itself:
     *  - before Task 13's F1-F5 round: hard-coded to `domain/src/commonMain/...`, with nothing telling
     *    a reader that any other source set was unscanned;
     *  - after it: `name.endsWith("Main")` and `kotlin/` only, above a KDoc sentence promising a new
     *    source set "is scanned the day it appears". `domain/src/main/java` is compiled into this
     *    module's android artifact and `"main"` is not a `…Main` name, so that sentence was false.
     *
     * **Test source sets stay out, and that is not cosmetic.** `domain/src/jvmTest/kotlin/.../agent`
     * legitimately imports `LauncherAction` and `FakeCommandPlanner` (`AgentEgressSentinelGuardTest`)
     * and `ActionIds` (`ToolIdsTest`) — as *code*, not comments — so scanning it turns this guard red
     * on correct code. What is forbidden is the engine **depending** on the action vocabulary; a test
     * is allowed to name both sides of a boundary in order to prove they stay apart.
     *
     * `scanned roots all exist and are not empty` is what stops this derivation from silently coming
     * back empty.
     */
    private val engineRoots: List<File> =
        listOf("agent", "tool", "trace")
            .flatMap { pkg -> kmpProductionRoots(domainSrc, "com/sidr/launcher/domain/$pkg") }

    /**
     * The action vocabulary and the two transports. Note what is NOT here: `ActionArg`,
     * `ActionRiskLevel` and `PermissionFeature` are the sanctioned reuse and must keep compiling.
     */
    private val forbidden = listOf(
        "LauncherAction",
        "ExecutableAction",
        "ActionCatalog",
        "ActionId",
        "GenerativeAiEngine",
        "CommandPlanner",
        "io.ktor",
        "HttpClient",
    )

    private fun engineFiles(): List<File> =
        engineRoots.flatMap { it.walkTopDown().filter { f -> f.isFile && f.extension == "kt" } }

    private data class Hit(val file: String, val line: Int, val term: String, val text: String) {
        override fun toString() = "$file:$line: [$term] $text"
    }

    private fun hitsIn(file: String, source: String): List<Hit> =
        stripComments(source).lines().withIndex().flatMap { (i, line) ->
            forbidden.filter { line.contains(it) }.map { Hit(file, i + 1, it, line.trim()) }
        }

    @Test
    fun `scanned roots all exist and are not empty`() {
        // `walkTopDown()` over a nonexistent directory yields nothing, throws nothing and asserts
        // nothing — the vacuous-guard shape Этап 2 found in three privacy guards. Since the roots are
        // now derived, the derivation itself can go empty, which is the same failure one step earlier.
        assertTrue("domain/src is not where this guard expects it: $domainSrc", domainSrc.isDirectory)
        assertTrue(
            "no engine root was derived under $domainSrc — the module layout moved and this guard " +
                "went vacuous",
            engineRoots.isNotEmpty(),
        )
        engineRoots.forEach { root ->
            assertTrue("missing scan root: $root", root.isDirectory)
        }
        val names = engineFiles().map { it.name }.toSet()
        assertTrue(
            "the engine scan found no sources — the roots moved and this guard went vacuous: $names",
            names.containsAll(
                setOf("AgentExecutor.kt", "ExecutionPlan.kt", "ToolRegistry.kt", "ToolExecutor.kt", "TraceEvent.kt"),
            ),
        )
    }

    @Test
    fun `the engine names no action vocabulary and no transport`() {
        val hits = engineFiles().flatMap { hitsIn(it.path, it.readText()) }

        assertEquals(
            "domain/agent and domain/tool must not reference the action vocabulary or a transport — " +
                "that edge is what keeps the A1 fork (parallel vocabulary vs. evolving ActionCatalog " +
                "in place) genuinely open. ActionArg / ActionRiskLevel / PermissionFeature are the " +
                "sanctioned reuse and are deliberately absent from the forbidden list. Found:\n" +
                hits.joinToString("\n"),
            emptyList<Hit>(),
            hits,
        )
    }

    /**
     * Guard-the-guard, half one. If someone reworded the KDoc that mentions these terms, the test
     * above would keep passing for a *different* reason — because the terms had gone — and the
     * comment-stripping would no longer be exercised by anything. This fails in that case.
     */
    @Test
    fun `raw sources do contain forbidden terms so the clean result is the stripping working`() {
        val raw = engineFiles().flatMap { file ->
            file.readText().lines().withIndex().flatMap { (i, line) ->
                forbidden.filter { line.contains(it) }.map { Hit(file.name, i + 1, it, line.trim()) }
            }
        }

        assertTrue(
            "No forbidden term appears anywhere in domain/agent or domain/tool, not even in a " +
                "comment. That may be fine, but it means `the engine names no action vocabulary` " +
                "now passes without the comment-stripping doing any work — re-point this guard-the-" +
                "guard at whatever documents the boundary now, or delete it deliberately.",
            raw.isNotEmpty(),
        )
    }

    /**
     * Guard-the-guard, half two: the stripping ignores comments *without* going blind to code. Run on
     * synthetic source so it holds whatever production happens to look like today, and so a reader can
     * see in one screen exactly which shapes are caught and which are not.
     */
    @Test
    fun `the scan catches an import and a fully qualified reference alike`() {
        val onlyComments = """
            package com.sidr.launcher.domain.agent

            /**
             * The engine knows nothing of `LauncherAction`, `ExecutableAction` or `ActionCatalog`.
             */
            // ActionId lives in the action package, and CommandPlanner is a different port.
            /* io.ktor and HttpClient are the transports this must never grow. */
            class Sample
        """.trimIndent()
        assertEquals(emptyList<Hit>(), hitsIn("Comments.kt", onlyComments))

        val realImport = """
            package com.sidr.launcher.domain.agent

            import com.sidr.launcher.domain.action.LauncherAction

            class Sample
        """.trimIndent()
        assertEquals(listOf("LauncherAction"), hitsIn("Import.kt", realImport).map { it.term })

        // The shape an import-only scan misses. `ExecutionPlan.kt` genuinely writes a type this way.
        val fullyQualified = """
            package com.sidr.launcher.domain.agent

            interface Sample {
                fun run(catalog: com.sidr.launcher.domain.action.ActionCatalog)
            }
        """.trimIndent()
        assertEquals(listOf("ActionCatalog"), hitsIn("Qualified.kt", fullyQualified).map { it.term })

        // Code on the line that closes a KDoc block is still code.
        val afterComment = """
            /** Doc mentioning ActionId. */ val x: com.sidr.launcher.domain.action.ActionId? = null
        """.trimIndent()
        assertEquals(listOf("ActionId"), hitsIn("Trailing.kt", afterComment).map { it.term })

        // The sanctioned reuse must not trip anything.
        val sanctioned = """
            import com.sidr.launcher.domain.action.ActionArg
            import com.sidr.launcher.domain.action.ActionRiskLevel
            import com.sidr.launcher.domain.permission.PermissionFeature
        """.trimIndent()
        assertEquals(emptyList<Hit>(), hitsIn("Sanctioned.kt", sanctioned))
    }
}
