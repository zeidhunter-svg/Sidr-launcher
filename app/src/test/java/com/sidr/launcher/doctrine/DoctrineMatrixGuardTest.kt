package com.sidr.launcher.doctrine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Этап 3.2 (agentic track). Guard-the-guard for `docs/governing/sidr-doctrine-matrix-v1.0.md` — the
 * doctrine matrix extracted out of the Design System Master Plan so both tracks can own it.
 *
 * **What problem this solves.** The matrix's third column names a real test for every rule, or an
 * honest `<нет>` naming whose debt it is. That is what turns the document from a declaration into a
 * list of debts. But a hand-written column of test names decays silently: a class gets renamed, a
 * test gets deleted, a rule quietly claims coverage it never had, and the document keeps *reading*
 * as if the doctrine were enforced. Same failure mode, and same fix, as
 * [com.sidr.launcher.i18n.HardcodedUiTextGuardTest.scoped_roots_cover_every_ui_module] and
 * `LocaleCompletenessGuardTest.module_prefixes_cover_every_module_that_ships_strings`.
 *
 * **What it deliberately does NOT do:** it never fails on an empty `<нет>` cell. An empty cell is a
 * declared debt, not a violation — a document that cannot show debt is a document where debt gets
 * hidden. It fails only on a *broken claim*.
 *
 * Working directory is the module dir (`app`), so the repo root is `..` — same convention as the
 * i18n guards.
 */
class DoctrineMatrixGuardTest {

    private val repoRoot = File("..")
    private val matrixFile = File(repoRoot, "docs/governing/sidr-doctrine-matrix-v1.0.md")

    /**
     * The closed verification vocabulary, originally Master Plan §5.2. Duplicated here on purpose:
     * widening the doctrine's vocabulary must require editing a test, not just a table cell.
     * [declared_vocabulary_matches_this_test] proves the document and this set agree.
     */
    private val verificationVocabulary = setOf(
        "screenshot", "semantics", "arch-guard", "unit", "device", "manual",
    )

    /**
     * The eight principles, by ID prefix. A principle silently dropping out of the matrix during a
     * future edit is exactly the kind of loss extraction makes possible, so it is asserted.
     */
    private val principlePrefixes = setOf(
        "NYH", "AML", "MZN", "ILM", "ADL", "HYA", "SKN", "HMA",
    )

    private data class Rule(
        val id: String,
        val statement: String,
        val type: String,
        val evidence: String,
    )

    private val matrixText: String by lazy {
        assertTrue(
            "Doctrine matrix not found at ${matrixFile.path}. If it moved, this guard and every " +
                "`DOC-*` citation in ADRs move with it.",
            matrixFile.isFile,
        )
        matrixFile.readText()
    }

    /** Table rows are the only lines whose first cell is a backticked `DOC-…` id. */
    private val rules: List<Rule> by lazy {
        matrixText.lineSequence()
            .filter { it.trimStart().startsWith("| `DOC-") }
            .map { line ->
                val cells = line.trim().trim('|').split('|').map { it.trim() }
                Rule(
                    id = cells.getOrElse(0) { "" }.trim('`'),
                    statement = cells.getOrElse(1) { "" },
                    type = cells.getOrElse(2) { "" }.trim('`'),
                    evidence = cells.getOrElse(3) { "" },
                )
            }
            .toList()
    }

    /** Simple class name -> file, across every test source set in the repo. */
    private val testFilesByClassName: Map<String, File> by lazy {
        val pruned = setOf("build", ".git", ".gradle", ".codegraph", ".idea", "screenshots")
        repoRoot.walkTopDown()
            .onEnter { it.name !in pruned }
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                val path = file.invariantSeparatorsPath
                path.contains("/src/test/") ||
                    path.contains("/src/jvmTest/") ||
                    path.contains("/src/androidTest/")
            }
            .associateBy { it.nameWithoutExtension }
    }

    @Test fun matrix_has_rules() {
        assertTrue("No `DOC-*` rows parsed out of ${matrixFile.path}", rules.isNotEmpty())
    }

    @Test fun every_id_is_well_formed_and_unique() {
        val pattern = Regex("""^DOC-([A-Z]{3})-\d+$""")
        val malformed = rules.map { it.id }.filterNot { pattern.matches(it) }
        assertTrue("Rule ids must match DOC-<PREFIX>-<n>: $malformed", malformed.isEmpty())

        val unknownPrefix = rules.map { it.id }
            .mapNotNull { pattern.find(it)?.groupValues?.get(1) }
            .filterNot { it in principlePrefixes }
            .distinct()
        assertTrue(
            "Rule id uses a principle prefix that is not in the matrix's own key table: " +
                "$unknownPrefix (known: $principlePrefixes)",
            unknownPrefix.isEmpty(),
        )

        val duplicated = rules.map { it.id }.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        assertTrue(
            "Rule ids are cited by ADRs and tests - they must be unique and never reused: $duplicated",
            duplicated.isEmpty(),
        )
    }

    @Test fun every_type_is_from_the_closed_vocabulary() {
        val offenders = rules.filterNot { it.type in verificationVocabulary }
            .map { "${it.id} -> '${it.type}'" }
        assertTrue(
            "Verification type must come from the closed vocabulary $verificationVocabulary " +
                "(matrix §1): $offenders",
            offenders.isEmpty(),
        )
    }

    /**
     * Guard-the-guard on the vocabulary itself: the matrix declares the six types in its own table,
     * and this test hardcodes them. Widening the doctrine's vocabulary is a doctrinal change and has
     * to be a deliberate edit in both places, not a quiet new word in a table cell.
     */
    @Test fun declared_vocabulary_matches_this_test() {
        val section = matrixText.substringAfter("### Словарь типов проверки")
            .substringBefore("## 2.")
        val declared = Regex("""^\| `([a-z-]+)` \|""", RegexOption.MULTILINE)
            .findAll(section)
            .map { it.groupValues[1] }
            .toSet()
        assertEquals(
            "The matrix's declared verification vocabulary and this guard disagree. Widening the " +
                "vocabulary is a doctrine change (matrix §1) - update both, deliberately.",
            verificationVocabulary,
            declared,
        )
    }

    /**
     * The point of the whole exercise. A rule that claims `arch-guard` (or any other automated type)
     * and names a test class that does not exist in the repository is a rule that reads as enforced
     * and is not.
     */
    @Test fun every_named_test_exists_in_the_repository() {
        val reference = Regex("""`([A-Z][A-Za-z0-9_]*Test)(?:#([A-Za-z0-9_]+))?`""")
        val broken = mutableListOf<String>()

        rules.forEach { rule ->
            if (rule.evidence.contains(NO_EVIDENCE)) return@forEach
            val references = reference.findAll(rule.evidence).toList()
            if (references.isEmpty()) {
                broken += "${rule.id}: evidence cell names neither a test nor `$NO_EVIDENCE` " +
                    "('${rule.evidence}')"
                return@forEach
            }
            references.forEach { match ->
                val className = match.groupValues[1]
                val function = match.groupValues[2]
                val file = testFilesByClassName[className]
                if (file == null) {
                    broken += "${rule.id}: names `$className`, which exists in no test source set"
                } else if (function.isNotEmpty() && !file.readText().contains("fun $function(")) {
                    broken += "${rule.id}: `$className` has no `fun $function(` " +
                        "(${file.invariantSeparatorsPath})"
                }
            }
        }

        assertTrue(
            "Doctrine rules claim tests that do not exist. Either fix the reference or downgrade " +
                "the cell to `$NO_EVIDENCE` with a reason - an honest empty cell is allowed, a " +
                "false claim is not:\n" + broken.joinToString("\n"),
            broken.isEmpty(),
        )
    }

    /**
     * An empty cell is allowed; an *anonymous* empty cell is not. Every `<нет>` must say whose debt
     * it is (`← долг A4′`) or why it is not automatable (`← ручной чеклист §20`), so the matrix stays
     * a list of debts with addresses rather than a list of blanks.
     */
    @Test fun every_empty_cell_names_its_debt() {
        val anonymous = rules
            .filter { it.evidence.contains(NO_EVIDENCE) }
            .filter { it.evidence.substringAfter("←", "").isBlank() }
            .map { it.id }
        assertTrue(
            "`$NO_EVIDENCE` must be followed by `← <whose debt / why not automatable>`: $anonymous",
            anonymous.isEmpty(),
        )
    }

    @Test fun every_principle_still_has_at_least_one_rule() {
        val covered = rules.map { it.id.substringAfter("DOC-").substringBefore("-") }.toSet()
        val orphaned = principlePrefixes - covered
        assertTrue(
            "Principle(s) dropped out of the matrix entirely - extraction must not lose one: " +
                "$orphaned",
            orphaned.isEmpty(),
        )
    }

    /**
     * The matrix states its own rule count in prose. Losing a row to a bad merge is otherwise
     * invisible, and the count is what a reader trusts when skimming.
     */
    @Test fun declared_rule_count_matches_the_table() {
        val declared = Regex("""\*\*Правил: (\d+)\.\*\*""").find(matrixText)?.groupValues?.get(1)
        assertTrue("Matrix §3 must declare its rule count as `**Правил: N.**`", declared != null)
        assertEquals(
            "Declared rule count disagrees with the table - a rule was added or lost silently",
            declared!!.toInt(),
            rules.size,
        )
    }

    private companion object {
        /** The honest-empty marker used by the matrix, in its own language. */
        const val NO_EVIDENCE = "<нет>"
    }
}
