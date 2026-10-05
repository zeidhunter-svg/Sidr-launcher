package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Criterion 14 (spec §7.12.6): one model path. The router is gone from production code, and nothing the
 * model proposes that acts can bypass the engine — the one direct execution of a `LauncherAction` left in
 * the launcher is the web chip, which the USER points at a destination (spec §7.12.3: FastPath and the
 * route chips are not the model's path). Comments are stripped: history may name the router; code may not.
 *
 * **What "direct execution" means here, and what it does not** (Task 11 fix round 1, review m3). The
 * claim is about `LauncherAction`s executed through `ExecuteActionUseCase` — the path the deleted router's
 * confirm card used. It is NOT that the launcher executes nothing else directly: a tap on an app in the
 * grid, on a suggestion or on an ambiguity candidate, and FastPath's learned AutoLaunch, launch an app the
 * user (or the user's own learned choice) named, through `LauncherAppLaunch` and the `ActionExecutor` —
 * user-chosen app launches, not a model's step, and not scanned here.
 *
 * **What the stripping cannot see, named rather than implied absent** (Task 11, hole H7). The comment
 * stripper is two regexes, not a lexer. A block-comment opener (slash, star) inside a string literal —
 * a MIME type such as image/<star> — would be read as a comment start and blind the rest of that file
 * up to the next closer (star, slash); a line-comment opener (two slashes) inside a string, as in an
 * https address, blinds the rest of its line. (The markers are spelled out because Kotlin nests block
 * comments: written literally here they would open a comment inside this one.) No production string
 * literal contains a block-comment opener today. And only `src/main` and `src/commonMain` are scanned: a
 * KMP `src/jvmMain` or `src/androidMain` would be invisible — `:domain` has neither today (the D3/D8
 * family of the call-site guards).
 */
class OneModelPathGuardTest {

    // Canonical on purpose: `File("..")` is NAMED "..", and the hidden-directory filter below would skip
    // the root itself — a scan that reads nothing and passes (the control catches it, but should not have to).
    private val repoRoot = File("..").canonicalFile

    private fun strip(source: String): String =
        source.replace(Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL), "").replace(Regex("//[^\n]*"), "")

    private fun productionSources(): List<File> = repoRoot.walkTopDown()
        .onEnter { it.name != "build" && !it.name.startsWith(".") }
        .filter { it.isFile && it.extension == "kt" }
        .filter { val p = it.invariantSeparatorsPath; "/src/main/" in p || "/src/commonMain/" in p }
        .toList()

    @Test
    fun `no production source names the deleted router`() {
        val names = listOf(
            "CommandPlanner", "LlmCommandPlanner", "CatalogSchemaRenderer", "ProposalValidator", "ActionProposal",
            "PlanResult", "RoutedAction", "PendingRoutedAction", "RouterProvidesModule", "ACTION_CATALOG_SCHEMA",
        )
        val sources = productionSources()
        val hits = sources.flatMap { file ->
            val code = strip(file.readText())
            names.filter { Regex("\\b$it\\b").containsMatchIn(code) }.map { "${file.invariantSeparatorsPath}: $it" }
        }

        assertEquals(emptyList<String>(), hits)
        assertTrue("the scan must read the whole tree, not a corner of it (${sources.size})", sources.size > 300)
    }

    @Test
    fun `the launcher executes a LauncherAction directly in exactly one place, the web chip`() {
        val launcher = File(repoRoot, "feature/launcher/src/main")
        val calls = launcher.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            Regex("""executeAction\.execute\(""").findAll(strip(file.readText())).map { file.name }
        }.toList()

        assertEquals(listOf("LauncherCommandSession.kt"), calls)
        val session = File(launcher, "java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt").readText()
        val chip = session.substringAfter("fun submitWebSearch(").substringBefore("\n    fun ")
        assertTrue("the one call is the web chip's", chip.contains("executeAction.execute("))

        // Task 11 holes H6 and m3: the call regex above sees only the literal `executeAction.execute(` —
        // `executeAction.let { it.execute(…) }`, `executeAction::execute`, an alias or a helper slips past it,
        // and it reads one file's field name. So the scan is keyed on the TYPE instead, across every file of
        // `feature/launcher/src/main`:
        //  (1) every identifier DECLARED as an `ExecuteActionUseCase` (`name: ExecuteActionUseCase`) is found,
        //      whatever it is called and whichever class holds it, and every whole-word use of it in that
        //      file's stripped code is counted. The word boundary counts a use whatever follows it (`.`, `::`,
        //      `?.`, `)`, `,`) and keeps a longer identifier (`reexecuteAction`) from counting. Expected: the
        //      session declares it and calls it once (the chip, 2); the ViewModel declares it and hands it to
        //      the session (`executeAction = executeAction`, 3) — and no other file holds one;
        //  (2) the type's own name is counted per file too (import + declared type = 2 each), so an instance
        //      built inline, cast to, or aliased in a file that declares no identifier of the type is seen.
        // Keyed on the type rather than on two field names because a renamed field, or a third holder,
        // would leave a name-keyed count silently reading nothing.
        // Keyed by the path under `src/main`, not the bare file name: two classes of the same name in two
        // packages must not collapse into one entry.
        val sources = launcher.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(launcher).invariantSeparatorsPath to strip(it.readText()) }
        val sessionPath = "java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt"
        val viewModelPath = "java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt"
        val declared = Regex("""\b(\w+)\s*:\s*ExecuteActionUseCase\b""")
        val identifierUses = sources.mapNotNull { (name, code) ->
            val identifiers = declared.findAll(code).map { it.groupValues[1] }.toSet()
            if (identifiers.isEmpty()) {
                null
            } else {
                name to identifiers.sumOf { id -> Regex("""\b${Regex.escape(id)}\b""").findAll(code).count() }
            }
        }.toMap()
        assertEquals(
            "every use of an ExecuteActionUseCase-typed identifier: declared and called once in the session, " +
                "declared and handed over in the ViewModel",
            mapOf(sessionPath to 2, viewModelPath to 3),
            identifierUses,
        )
        val typeMentions = sources.mapValues { (_, code) -> Regex("""\bExecuteActionUseCase\b""").findAll(code).count() }
            .filterValues { it > 0 }
        assertEquals(
            "the type is imported and declared in these two files only",
            mapOf(sessionPath to 2, viewModelPath to 2),
            typeMentions,
        )
    }
}
