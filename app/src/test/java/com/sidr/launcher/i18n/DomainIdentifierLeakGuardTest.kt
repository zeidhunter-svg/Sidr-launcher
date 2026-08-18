package com.sidr.launcher.i18n

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * I18N-2 barrier 4. Fails the build when a raw domain identifier expression - `.name` (an enum's
 * Kotlin literal, e.g. `"FAJR"`), `.toString()`, `.key` (an opaque value-class's backing field), or
 * `<Something>Id.value` - is assigned directly to a display sink instead of going through a
 * `sidrString(R.string.…)` resolver.
 *
 * **Why this exists.** I18N-1's three barriers (`HardcodedUiTextGuardTest`,
 * `StringSeamGuardTest`, `LocaleCompletenessGuardTest`) all assume the offending value is a STRING
 * LITERAL or a missing/incomplete resource. None of them can catch the shape of bug this closes: a
 * real one, found during I18N-1's own device smoke and fixed in I18N-2 -
 * `PrayerSummaryMapper.kt` used to build `SidrPrayerTimeUi(name = name.name, ...)`, feeding a
 * [com.sidr.launcher.domain.prayer.PrayerName] enum's raw Kotlin name straight into Home's per-cell
 * TalkBack `contentDescription`. The locked English resource happened to equal the enum name
 * (`"FAJR"`), so every existing barrier - and a human reading the English build - saw nothing wrong;
 * only `ru`/`tr` announced literal, untranslated English. A resource existed and was correctly
 * translated; it just was never called. This barrier's actual target is the *next* occurrence of the
 * same class of bug, not this one specifically (already fixed) - `A1`'s planned `ToolId`/`ToolTier`/
 * `ToolEffect`/`ActionCategory` vocabulary are exactly this shape of risk if any of them ever reach a
 * UI sink directly.
 *
 * **Heuristic, and deliberately narrow.** Like barrier 1, this catches regression, not absence. The
 * identifier-leak pattern must appear as the WHOLE right-hand side immediately after the sink's `=` -
 * `name = name.name,` matches, but `name = prayerNameLabel(it.name),` does not, because the RHS there
 * starts with a function call, not a bare property chain. This is a deliberate precision trade-off:
 * matching `.name`/`.toString()`/`.key` anywhere on a sink line (not just at the RHS start) would
 * false-positive constantly, most heavily on `.toString()` used for ordinary number-to-text
 * formatting (`label = count.toString()`) - a real, common, harmless idiom this barrier is not trying
 * to police. Narrowing to "the RHS's first token is the leak itself" is what keeps the barrier usably
 * quiet while still catching the exact shape of the bug it was written for. It inherits
 * [HardcodedUiTextGuardTest]'s same ktlint-line-wrap blind spot (a leak whose RHS starts on the next
 * line is invisible to this line-by-line scan).
 *
 * **Own `scopedRoots`, deliberately not shared with [HardcodedUiTextGuardTest].** Same convention as
 * [LocaleCompletenessGuardTest]'s independent `modulePrefixes`: each barrier proves its own module
 * coverage rather than depending on another test class's `private` list, so the two can drift and
 * still each self-detect the drift via their own guard-the-guard test.
 *
 * Working directory is the module dir (`app`), so the repo root is `..`.
 */
class DomainIdentifierLeakGuardTest {

    /** In-scope module roots, relative to the repo root - identical to [HardcodedUiTextGuardTest]'s
     *  `scopedRoots` today (spec §3.1's UI modules); kept as an independent copy, see the class kdoc. */
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

    /** Display-field sinks: a bare identifier-chain leak assigned to one of these is user-facing
     *  until proven otherwise. Matched with a word boundary + `(?!=)` so `it.name == name` (a
     *  comparison, extremely common) is never mistaken for `name = ...` (an assignment). */
    private val sinkRegexes = listOf(
        "text", "label", "title", "name", "value", "contentDescription", "placeholder",
        "description", "provenance", "confirmLabel", "secondaryLabel", "executeLabel", "onClickLabel",
    ).map { word -> Regex("""\b$word\s*=(?!=)""") }

    /** The four raw-identifier shapes named in the class kdoc, each anchored to the START of the
     *  trimmed remainder after a sink's `=` - see the class kdoc for why the anchor matters. */
    private val leakPatterns = listOf(
        Regex("""^[A-Za-z_][A-Za-z0-9_.]*\.name\b"""),
        Regex("""^[A-Za-z_][A-Za-z0-9_.]*\.toString\(\)"""),
        Regex("""^[A-Za-z_][A-Za-z0-9_.]*\.key\b"""),
        Regex("""^[A-Za-z_][A-Za-z0-9_.]*Id\.value\b"""),
    )

    /**
     * Owner-approved exemptions. Each entry states WHY, so the list stays a decision record rather
     * than a place to hide new offences (same convention as [HardcodedUiTextGuardTest.exemptions]).
     *
     * Both entries below predate this barrier and are spec-sanctioned locked vocabulary (spec §7.1's
     * risk/status chips - `CONFIRM`/`EXTERNAL`/`DESTRUCTIVE`, deliberately identical in every locale,
     * the same precedent already recorded for `LauncherViewModel.kt`'s `RISK_CONFIRM_LABEL`), not
     * unnoticed leaks: `SidrActionProposalTone`/`SidrResultTone`'s enum `.name` IS the intended
     * display text.
     */
    private val exemptions = listOf(
        "core/ui/src/main/java/com/sidr/launcher/core/ui/component/SidrActionSafety.kt" to
            "SidrRiskChip/SidrStatusChip render tone.name.uppercase() by design - spec §7.1 locked " +
            "risk/status vocabulary (CONFIRM/EXTERNAL/DESTRUCTIVE etc.), deliberately identical in " +
            "every locale, same precedent as RISK_CONFIRM_LABEL.",
        "feature/settings/src/main/java/com/sidr/launcher/feature/settings/SettingsScreen.kt" to
            "label = count.toString() renders a plain Int from FAVORITES_COUNT_OPTIONS as a chip " +
            "label (\"3\", \"5\", …) - ordinary number-to-text formatting, not a domain identifier; " +
            "digits are the same in every supported locale.",
    )

    @Test fun no_raw_domain_identifier_assigned_to_a_display_sink() {
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
                val exemptionReason = exemptions.firstOrNull { it.first == relative }?.second

                file.readLines().forEach { raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*")) return@forEach

                    val leaks = sinkRegexes.any { sinkRegex ->
                        val match = sinkRegex.find(line) ?: return@any false
                        val remainder = line.substring(match.range.last + 1).trimStart()
                        leakPatterns.any { it.containsMatchIn(remainder) }
                    }
                    if (leaks && exemptionReason == null) offenders += "$relative: $line"
                }
            }

        assertTrue(
            "A raw domain identifier (.name / .toString() / .key / <Id>.value) is assigned directly " +
                "to a display sink - it will render as an untranslated Kotlin literal instead of " +
                "going through sidrString(R.string.…) (I18N-2 barrier 4). If a site is genuinely " +
                "exempt (locked vocabulary, spec §7.1), add it to `exemptions` WITH a reason:\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty(),
        )
    }

    @Test fun every_exemption_states_a_reason() {
        val blank = exemptions.filter { it.second.isBlank() }.map { it.first }
        assertTrue("Exemptions must carry a reason: $blank", blank.isEmpty())
    }

    /**
     * Guard-the-guard, same shape as [HardcodedUiTextGuardTest.scoped_roots_cover_every_ui_module]:
     * a module rename or a new UI module would otherwise silently drop out of
     * [no_raw_domain_identifier_assigned_to_a_display_sink]'s walk with no failure at all.
     */
    @Test fun scoped_roots_cover_every_ui_module() {
        val repoRoot = File("..")
        val settingsFile = File(repoRoot, "settings.gradle.kts")
        val includePattern = Regex("""include\(":([^"]+)"\)""")
        val includedModules = includePattern.findAll(settingsFile.readText())
            .map { it.groupValues[1].replace(':', '/') }
            .toList()

        val unscoped = includedModules.filter { modulePath ->
            modulePath !in scopedRoots
        }.filter { modulePath ->
            val mainDir = File(repoRoot, "$modulePath/src/main")
            mainDir.isDirectory &&
                mainDir.walkTopDown()
                    .filter { it.isFile && it.extension == "kt" }
                    .any { it.readText().contains("@Composable") }
        }

        assertTrue(
            "Module(s) with @Composable UI are not covered by `scopedRoots` - " +
                "DomainIdentifierLeakGuardTest would silently skip them: $unscoped",
            unscoped.isEmpty(),
        )
    }
}
