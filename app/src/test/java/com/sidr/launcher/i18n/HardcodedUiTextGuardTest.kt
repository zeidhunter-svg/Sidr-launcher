package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I18N-1 barrier 1 (spec §10.1). Fails the build when a new user-facing literal is hardcoded in a
 * Composable instead of going through `sidrString(R.string.…)`.
 *
 * Heuristic by necessity (this project is 100% Compose and Lint's HardcodedText only reads XML
 * layouts, which do not exist here). It catches regression; it does not prove absence - that is why
 * `StringSeamGuardTest` exists alongside it as an exact check.
 *
 * **Scope, and why it stops there:** the walk covers only the spec §3.1 in-scope module roots -
 * `core/ui`, `feature/launcher`, `feature/settings`, `feature/prayer`, `feature/permission_education`,
 * `feature/assistant`, `feature/suggestions`, and `app`. It deliberately does NOT walk `:domain` or
 * any `:data:*` module: those layers are governed instead by spec §3.5's closed typed-message list
 * (delivered by Task 11) - an exact contract, not a heuristic scan - and spec §3.6 forbids widening
 * this heuristic into the data layer. A literal sitting in, say, `:data:repository` is a different,
 * out-of-scope finding, not a miss this barrier is responsible for catching.
 *
 * Working directory is the module dir (`app`), so the repo root is `..`.
 */
class HardcodedUiTextGuardTest {

    /** In-scope module roots, relative to the repo root (spec §3.1). */
    private val scopedRoots = listOf(
        "core/ui",
        "feature/launcher",
        "feature/settings",
        "feature/prayer",
        "feature/permission_education",
        "feature/assistant",
        "feature/suggestions",
        "app",
    )

    /** Text sinks: a literal on one of these lines is user-facing until proven otherwise.
     *  `onClickLabel =` was added alongside `contentDescription =` - both carry accessibility text and
     *  the two frequently sit one line apart describing the same affordance. */
    private val sinks = listOf(
        "text =", "label =", "title =", "contentDescription =", "placeholder =",
        "description =", "consequence =", "evidence =", "provenance =",
        "confirmLabel =", "secondaryLabel =", "executeLabel =", "onClickLabel =",
        "SidrText(", "Text(", "SidrSurfaceAction(",
    )

    /**
     * Owner-approved exemptions (spec §3.2). Each entry states WHY, so the list stays a decision
     * record rather than a place to hide new offences.
     */
    private val exemptions = mapOf(
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/TasksPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/AgentsPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/ActivityPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/TerminalPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/preview/MomentsPreviewScreen.kt"
            to "PREVIEW mock-up, replaced wholesale by the A-stage",
    )

    /** A literal is only interesting if it contains a letter run: `[ %1$s ]` and `> ` are decoration. */
    private val literalPattern = Regex("\"([^\"\\\\]|\\\\.){2,}\"")

    /** Kotlin string-template interpolations: `${...}` and bare `$identifier`. Their identifiers
     *  contain letters, which would otherwise trip [hasLetter] on purely decorative literals such as
     *  `"[ ${item.label} ]"`, `"> $commandLine"`, or `"$NEXT_MARKER ${prayer.time}"` - stripped before
     *  the letter test so the interpolated variable name is never mistaken for the literal's own text. */
    private val interpolationPattern = Regex("""\$\{[^}]*\}|\$[A-Za-z_][A-Za-z0-9_]*""")

    /** True iff, after stripping interpolations, the literal has a run of at least 2 letters - a lone
     *  glyph or symbol (e.g. `"•"`, `"]"`, `">"`) is decoration, not text. */
    private fun hasLetter(s: String): Boolean {
        val stripped = interpolationPattern.replace(s, "")
        var run = 0
        for (c in stripped) {
            if (c.isLetter()) {
                run++
                if (run >= 2) return true
            } else {
                run = 0
            }
        }
        return false
    }

    @Test fun no_hardcoded_user_text_in_composables() {
        val repoRoot = File("..")
        val offenders = mutableListOf<String>()

        scopedRoots
            .map { File(repoRoot, it) }
            .filter { it.isDirectory }
            .flatMap { root -> root.walkTopDown().toList() }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("/src/main/") && !it.path.contains("/build/") }
            .forEach { file ->
                val relative = file.relativeTo(repoRoot).path.replace(File.separatorChar, '/')
                if (exemptions.containsKey(relative)) return@forEach

                var previewDepth = 0
                var inPreview = false
                file.readLines().forEach { raw ->
                    val line = raw.trim()

                    // Skip @Preview function bodies: gallery fixtures are not product copy (spec §3.3).
                    if (line.startsWith("@Preview")) { inPreview = true; previewDepth = 0 }
                    if (inPreview) {
                        previewDepth += line.count { it == '{' } - line.count { it == '}' }
                        if (previewDepth <= 0 && line.contains("}")) inPreview = false
                        return@forEach
                    }
                    if (line.startsWith("//") || line.startsWith("*")) return@forEach
                    if (!sinks.any { line.contains(it) }) return@forEach

                    literalPattern.findAll(line)
                        .map { it.value.trim('"') }
                        .filter { hasLetter(it) }
                        .forEach { offenders += "$relative: $line" }
                }
            }

        assertTrue(
            "Hardcoded user-facing text must go through sidrString(R.string.…) (spec §10.1). " +
                "If a site is genuinely exempt, add it to `exemptions` WITH a reason:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun every_exemption_states_a_reason() {
        val blank = exemptions.filterValues { it.isBlank() }.keys
        assertTrue("Exemptions must carry a reason: $blank", blank.isEmpty())
    }
}
