package com.sidr.launcher.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Criterion 14 (spec §7.12.6): one model path. The router is gone from production code, and nothing the
 * model proposes that acts can bypass the engine — the one direct execution left in the launcher is the
 * web chip, which the USER points at a destination (spec §7.12.3: FastPath and the route chips are not
 * the model's path). Comments are stripped: history may name the router; code may not.
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
    fun `the launcher executes an action directly in exactly one place, the web chip`() {
        val launcher = File(repoRoot, "feature/launcher/src/main")
        val calls = launcher.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            Regex("""executeAction\.execute\(""").findAll(strip(file.readText())).map { file.name }
        }.toList()

        assertEquals(listOf("LauncherCommandSession.kt"), calls)
        val session = File(launcher, "java/com/sidr/launcher/feature/launcher/LauncherCommandSession.kt").readText()
        val chip = session.substringAfter("fun submitWebSearch(").substringBefore("\n    fun ")
        assertTrue("the one call is the web chip's", chip.contains("executeAction.execute("))

        // Task 11 ruling on hole H6: the call regex above sees only the literal `executeAction.execute(`.
        // A second path written `executeAction.let { it.execute(…) }`, `executeAction::execute`, or by
        // handing the field to a helper would slip past it. So the FIELD is counted instead, as a whole
        // word in the comment-stripped code: its declaration and the chip's one call, nothing else. The word
        // boundary makes every use count whatever follows it (`.`, `::`, `?.`, `)`, `,`) and keeps a longer
        // identifier from counting (`reexecuteAction`); the type `ExecuteActionUseCase` differs in case.
        val fieldUses = Regex("""\bexecuteAction\b""").findAll(strip(session)).count()
        assertEquals("`executeAction`: the declaration and the web chip's one call", 2, fieldUses)
    }
}
