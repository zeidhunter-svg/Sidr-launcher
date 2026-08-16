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
 * any `:data:*` module: those layers are governed instead by spec §3.5's closed typed-message contract
 * (delivered by Task 11) - an exact contract, not a heuristic scan. Keeping this guard's own scan
 * limited to §3.1's module list, rather than also scanning the data layer for stray literals this guard
 * has no mandate over, is a controller ruling for this task, not itself a §3.6 citation: §3.6's
 * "out of scope, do not widen" bullet is about `Collator`-based sorting / locale-safe matching in
 * `InstalledAppsRepositoryImpl` - a data-layer *behaviour* change, not this guard's scan boundary.
 * [scoped_roots_cover_every_ui_module] guards this list itself against silently going stale (a module
 * rename or a new UI module neither in `scopedRoots` nor in a reasoned `excludedComposableRoots` entry).
 *
 * **Known blind spot:** a literal split across a ktlint line-wrap - `text =` on one line, the string
 * itself (`"Hello world",`) starting the next - is invisible to this line-by-line scan. Long strings are
 * naturally wrapped that way, so treat this as a known gap, not evidence the barrier is exhaustive.
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

    /**
     * Included Gradle modules that DO contain `@Composable` UI but are deliberately excluded from
     * [scopedRoots] (F4, fix round) - the guard-the-guard's own escape hatch, same spirit as
     * [exemptions]. Empty today: every Composable-bearing included module is in [scopedRoots]
     * ([scoped_roots_cover_every_ui_module] proves this by walking `settings.gradle.kts`). A future
     * module that legitimately ships Compose UI outside barrier-1's mandate (e.g. a debug-only tooling
     * module) would be named here with a reason instead of silently passing.
     */
    private val excludedComposableRoots = emptyMap<String, String>()

    /** Text sinks: a literal on one of these lines is user-facing until proven otherwise.
     *  `onClickLabel =` was added alongside `contentDescription =` - both carry accessibility text and
     *  the two frequently sit one line apart describing the same affordance. Matched case-insensitively
     *  (F3, fix round): a case-sensitive match missed the exact idiom this task's own `.semantics{}` fix
     *  forces at every such call site - `val revealLabel = "..."` contains "Label =", not "label =". */
    private val sinks = listOf(
        "text =", "label =", "title =", "contentDescription =", "placeholder =",
        "description =", "consequence =", "evidence =", "provenance =",
        "confirmLabel =", "secondaryLabel =", "executeLabel =", "onClickLabel =",
        "SidrText(", "Text(", "SidrSurfaceAction(",
    )

    /**
     * Owner-approved exemptions (spec §3.2). Each entry states WHY, so the list stays a decision
     * record rather than a place to hide new offences.
     *
     * Two categories, both owner-approved 2026-08-10:
     * 1. **PREVIEW mock-up screens** (~58 literals) - `PREVIEW`-badged, non-functional mock-ups the
     *    A-stage replaces wholesale.
     * 2. **The hidden dev console** in `LauncherViewModel.kt` (~12 literals: the 7-tap wordmark arm,
     *    the `//dev-mode` toggle, `outcomeSummary` labels) - a debug surface, not product;
     *    `outcomeSummary` values are pipeline state names, more useful stable than translated. F3's
     *    case-insensitive sink match additionally catches `RISK_CONFIRM_LABEL = "CONFIRM"` in the same
     *    file - spec §7.1 enumerated locked vocabulary (the DF-4 gate-risk chip, deliberately identical
     *    in every locale), not a miss to extract: `riskLabel` is born in this ViewModel, so resolving it
     *    to a resource needs the full spec §8 typed-value refactor through `PendingRoutedAction` +
     *    `ConfirmActionCard` + their tests, well outside a fix round. One exemption entry covers both
     *    reasons for this one file.
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
        "feature/launcher/src/main/java/com/sidr/launcher/feature/launcher/LauncherViewModel.kt"
            to "spec §3.2's second owner-approved exemption: the hidden dev console (7-tap arm, " +
            "//dev-mode toggle, outcomeSummary labels, ~12 literals), a debug surface not product; " +
            "PLUS RISK_CONFIRM_LABEL = \"CONFIRM\" (spec §7.1 enumerated locked vocabulary, the DF-4 " +
            "gate-risk chip - extracting it needs the full spec §8 typed-value refactor, out of scope " +
            "for a fix round). Caught only after F3's case-insensitive sink match; one entry, two reasons.",
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
                    if (!sinks.any { line.contains(it, ignoreCase = true) }) return@forEach

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

    /**
     * Guard-the-guard (F4, fix round). [scopedRoots] is a hand-written list; a module rename
     * (`feature/settings` -> `feature/settings_ui`) or a new UI module (e.g. an A-stage
     * `:feature:tasks`) would silently drop out of [no_hardcoded_user_text_in_composables]'s walk with
     * no failure at all. This test parses `settings.gradle.kts` for every included module and fails if
     * any module that actually contains a `@Composable` declaration under `src/main` is neither in
     * [scopedRoots] nor named (with a reason) in [excludedComposableRoots].
     */
    @Test fun scoped_roots_cover_every_ui_module() {
        val repoRoot = File("..")
        val settingsFile = File(repoRoot, "settings.gradle.kts")
        val includePattern = Regex("""include\(":([^"]+)"\)""")
        val includedModules = includePattern.findAll(settingsFile.readText())
            .map { it.groupValues[1].replace(':', '/') }
            .toList()

        val unscoped = includedModules.filter { modulePath ->
            modulePath !in scopedRoots && modulePath !in excludedComposableRoots
        }.filter { modulePath ->
            val mainDir = File(repoRoot, "$modulePath/src/main")
            mainDir.isDirectory &&
                mainDir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .any { it.readText().contains("@Composable") }
        }

        assertTrue(
            "Module(s) with @Composable UI are not covered by `scopedRoots` and are not named in " +
                "`excludedComposableRoots` with a reason - HardcodedUiTextGuardTest would silently skip " +
                "them (spec §3.1): $unscoped",
            unscoped.isEmpty(),
        )
    }
}
