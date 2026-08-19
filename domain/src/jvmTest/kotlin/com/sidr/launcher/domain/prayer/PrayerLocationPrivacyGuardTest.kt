package com.sidr.launcher.domain.prayer

import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.domain.ai.AiRequest
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.ai.OutboundContextPolicy.AllowedContext
import com.sidr.launcher.domain.ai.PromptContextBuilder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Task 10 (DS-6B) privacy gate: proves prayer LOCATION (city label + coordinates) can **never**
 * reach an [AiRequest], a cloud/LLM prompt, analytics, or a log.
 *
 * Mirrors three existing precedents:
 *  - `AliasPrivacyScopeGuardTest` — allow-list byte-for-byte pin + `repoRoot()` source-scan for a
 *    "no path to the generative/outbound layer" proof.
 *  - `AiRequestGuardTest`/`OutboundSecretLeakGuardTest` — the outbound allow-list + [PromptContextBuilder]
 *    sentinel pattern, and the reflection-free hand-synced field-inventory drift check.
 *  - `SuggestionProviderPrivacyGuardTest`/`LocationSuggestionProvider` tests — "plant a REAL sensitive
 *    value, run the REAL code, assert it doesn't leak."
 *
 * Six guards, in the order of the task brief:
 *  1. [OutboundContextPolicy.ALLOWED] is byte-for-byte the pre-DS-6B 4-value set (zero new categories).
 *  2. [AiRequest]'s real declared fields carry nothing prayer/location-shaped.
 *  3. A REAL planted [PrayerLocation] (Kazan, 55.79/49.12 — the same published coordinate already used
 *     in `AdhanPrayerCalculatorGoldenTest`) saved into "prayer prefs" never reaches [PromptContextBuilder]'s
 *     output at both the domain-field level and the outbound-body (`toString()`) level, with a
 *     regression guard proving the scan isn't just censoring the user's own words.
 *  4. Source confinement: `com.batoulapps`/`adhan2` imports are confined to `data/prayer`'s calculator;
 *     prayer's own modules never import `android.location` directly, and the repo-wide set of
 *     `android.location` importers is pinned to exactly the two known, already-reviewed readers.
 *  5. Prayer (`domain/prayer`, `data/prayer`, `feature/prayer`) has no reference at all to the
 *     generative/outbound layer.
 *  6. Zero `Log.`/`println` calls exist anywhere in prayer's production sources.
 */
class PrayerLocationPrivacyGuardTest {

    // ---- Guard 1: outbound allow-list unchanged ------------------------------------------------

    @Test
    fun `guard 1 - prayer adds zero outbound context categories, allow-list stays the pre-DS-6B 4-value set`() {
        // If DS-6B had wired prayer location into any outbound category (even a new, seemingly
        // innocuous one), this set would grow past 4 and this assertion would fail closed.
        assertEquals(
            setOf(
                AllowedContext.USER_COMMAND,
                AllowedContext.STATIC_SYSTEM_PROMPT,
                AllowedContext.GENERATION_LIMITS,
                AllowedContext.ACTION_CATALOG_SCHEMA,
            ),
            OutboundContextPolicy.ALLOWED,
        )
    }

    // ---- Guard 2: no prayer-shaped field on AiRequest ----------------------------------------

    @Test
    fun `guard 2 - AiRequest's real declared fields carry nothing prayer or location shaped`() {
        // Reflects over AiRequest's ACTUAL declared instance fields (the AiRequestGuardTest drift-check
        // pattern) rather than trusting a hand-maintained list — so a prayer-shaped field added to
        // AiRequest anywhere (even without updating OUTBOUND_FIELD_NAMES) is caught here directly.
        val declaredFieldNames = AiRequest::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }

        val prayerLocationTerms = listOf(
            "prayer", "location", "lat", "lon", "coord", "city", "tz", "madhab", "method",
        )
        val violations = declaredFieldNames.flatMap { field ->
            prayerLocationTerms
                .filter { term -> field.contains(term, ignoreCase = true) }
                .map { term -> field to term }
        }
        assertTrue(
            "AiRequest gained a prayer/location-shaped field:\n" +
                violations.joinToString("\n") { (field, term) -> "  field \"$field\" contains \"$term\"" },
            violations.isEmpty(),
        )

        // Sanity check the scan itself isn't vacuous: AiRequest must still have its known, real fields.
        assertEquals(setOf("messages", "system", "maxOutputTokens", "model", "stopSequences"), declaredFieldNames.toSet())
    }

    // ---- Guard 3: outbound sentinel — a REAL planted PrayerLocation never reaches AiRequest -----

    @Test
    fun `guard 3a - a real planted prayer location in prayer prefs never reaches AiRequest fields (domain level)`() = runTest {
        val (planted, request) = buildRequestWithPlantedLocationInPrefs()

        sentinelsOf(planted).forEach { sentinel ->
            assertFalse(
                "planted prayer sentinel \"$sentinel\" leaked into AiRequest.messages",
                request.messages.single().content.contains(sentinel),
            )
            assertFalse(
                "planted prayer sentinel \"$sentinel\" leaked into AiRequest.system",
                (request.system ?: "").contains(sentinel),
            )
        }
    }

    @Test
    fun `guard 3b - a real planted prayer location never reaches the outbound body (toString level)`() = runTest {
        val (planted, request) = buildRequestWithPlantedLocationInPrefs()

        // request.toString() is a full field dump of every outbound field (the same proxy
        // OutboundSecretLeakGuardTest uses for "the rendered outbound surface") — the closest
        // domain-level stand-in for the real serialized HTTP body, which prayer has no path to at all
        // (see guard 5). If ANY field ever carried the planted value, this single check would catch it.
        val outboundBody = request.toString()
        sentinelsOf(planted).forEach { sentinel ->
            assertFalse(
                "planted prayer sentinel \"$sentinel\" leaked into the outbound AiRequest body: $outboundBody",
                outboundBody.contains(sentinel),
            )
        }
    }

    @Test
    fun `guard 3c - regression guard, a city name legitimately typed by the user survives verbatim`() {
        // Mirrors AiRequestGuardTest's "keep calendar in a user command" trick: proves the guard scans
        // STRUCTURE (a planted object never passed to the builder), not the user's own typed words —
        // an over-eager filter that censored user content would be a real bug, not real privacy.
        val command = "open Kazan"
        val request = PromptContextBuilder().build(command)
        assertEquals(command, request.messages.single().content)
        assertTrue(
            "user command must be preserved verbatim; the sentinel value is legal when the USER typed it",
            request.messages.single().content.contains("Kazan"),
        )
    }

    /**
     * Plants a REAL [PrayerLocation] (Kazan — 55.79/49.12, the exact published coordinate already used
     * in `AdhanPrayerCalculatorGoldenTest`) into a [FakePrayerPreferencesRepository] ("prayer prefs"),
     * reads it back through the real port read path, then builds an [AiRequest] from an UNRELATED
     * command that never mentions it. Returns the planted location + the built request so both the
     * domain-level and outbound-body-level assertions run against the exact same real construction.
     */
    private suspend fun buildRequestWithPlantedLocationInPrefs(): Pair<PrayerLocation, AiRequest> {
        val plantedLocation = PrayerLocation(
            label = "Kazan",
            lat2dp = 55.79,
            lon2dp = 49.12,
            tzId = "Europe/Moscow",
            source = PrayerLocationSource.CITY,
        )
        val plantedSetup = PrayerSetup(
            methodId = CalculationMethodId("MWL"),
            madhab = Madhab.STANDARD,
            location = plantedLocation,
        )

        val prefs = FakePrayerPreferencesRepository()
        prefs.saveSetup(plantedSetup)
        val readBack = requireNotNull(prefs.setup().first()?.location) {
            "planted location must round-trip through the fake prefs repository"
        }

        // An UNRELATED command — proves the builder's single string-only input parameter has no seam
        // through which prayer prefs could be smuggled in, even by an attacker who controls the prefs.
        val request = PromptContextBuilder().build("open telegram")
        return readBack to request
    }

    private fun sentinelsOf(location: PrayerLocation): List<String> = listOf(
        location.label,
        location.lat2dp.toString(),
        location.lon2dp.toString(),
        location.tzId,
    )

    // ---- Guard 4: source confinement -----------------------------------------------------------

    @Test
    fun `guard 4a - com-batoulapps and adhan2 imports are confined to data-prayer's calculator`() {
        val root = repoRoot()
        val hits = allSrcMainKotlinFiles(root)
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter { it.contains("com.batoulapps") || it.contains("adhan2") }
                    .map { line -> file.relativeTo(root).path to line.trim() }
            }

        val allowedFile = "data/prayer/src/main/java/com/sidr/launcher/data/prayer/AdhanPrayerCalculator.kt"
        val offenders = hits.filterNot { (path, _) -> path == allowedFile }
        assertTrue(
            "adhan2/com.batoulapps import found outside $allowedFile:\n" +
                offenders.joinToString("\n") { (path, line) -> "  $path: $line" },
            offenders.isEmpty(),
        )
        // Non-vacuous: the calculator itself must actually import it, proving the scan really executes
        // (a broken path filter that matched nothing would otherwise pass this test vacuously).
        assertTrue(
            "expected AdhanPrayerCalculator.kt to import adhan2 - the scan matched nothing at all",
            hits.any { (path, _) -> path == allowedFile },
        )
    }

    @Test
    fun `guard 4b - prayer's own domain, data, and feature modules never import android-location directly`() {
        val root = repoRoot()
        val prayerDirs = listOf(
            File(root, "domain/src/commonMain/kotlin/com/sidr/launcher/domain/prayer"),
            File(root, "data/prayer/src/main/java/com/sidr/launcher/data/prayer"),
            File(root, "feature/prayer/src/main/java/com/sidr/launcher/feature/prayer"),
        )
        prayerDirs.forEach { dir -> assertTrue("expected prayer source dir to exist: ${dir.path}", dir.isDirectory) }
        val scannedFiles = prayerDirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" } }
        assertTrue(
            "the scan walked zero .kt files - the guard would vacuously pass; check the directory paths",
            scannedFiles.isNotEmpty(),
        )

        val hits = prayerDirs.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines()
                        .filter { it.trimStart().startsWith("import ") }
                        .filter { it.contains("android.location") }
                        .map { line -> "${file.relativeTo(root).path}: ${line.trim()}" }
                }
        }
        assertTrue(
            "prayer's own domain/data/feature source must never import android.location directly " +
                "(only the confined AndroidPrayerLocationProvider in :core:android may read it):\n" +
                hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    @Test
    fun `guard 4c - android-location usage repo-wide is confined to exactly the two known reviewed readers`() {
        // DEVIATION FROM THE TASK BRIEF, recorded here rather than weakened: the brief assumes
        // android.location already appears in NO src/main except :core:android, but the pre-existing
        // Phase-7/Block-U `LocationSuggestionProvider` (data/repository, unrelated to prayer, out of
        // this task's scope to touch) has legitimately read android.location since before DS-6B existed.
        // Rather than write a check that would immediately false-fail on that unrelated, already-shipped
        // file, this pins the exact set of readers repo-wide: a genuinely NEW third reader anywhere in
        // the app fails this test closed, exactly as the brief intends ("a new location use anywhere
        // else fails the test") — while guard 4b above proves the stronger, prayer-specific claim: the
        // prayer feature's OWN modules never touch android.location at all.
        val root = repoRoot()
        val hits = allSrcMainKotlinFiles(root)
            .flatMap { file ->
                file.readLines()
                    .filter { it.trimStart().startsWith("import ") }
                    .filter { it.contains("android.location") }
                    .map { file.relativeTo(root).path }
            }
            .toSet()

        val prayerProvider =
            "core/android/src/main/java/com/sidr/launcher/core/android/prayer/AndroidPrayerLocationProvider.kt"
        val knownPreexistingReader =
            "data/repository/src/main/java/com/sidr/launcher/data/repository/suggestions/LocationSuggestionProvider.kt"

        assertEquals(
            "android.location import sites drifted from the known, reviewed set — either a new " +
                "location-reading path appeared somewhere in the app, or a known one vanished:",
            setOf(prayerProvider, knownPreexistingReader),
            hits,
        )
    }

    // ---- Guard 5: no prayer -> outbound/generative edge --------------------------------------

    @Test
    fun `guard 5 - prayer has no outbound or generative dependency in domain, data, or feature`() {
        val root = repoRoot()
        val prayerSourceDirs = listOf(
            File(root, "domain/src/commonMain/kotlin/com/sidr/launcher/domain/prayer"),
            File(root, "data/prayer/src/main/java/com/sidr/launcher/data/prayer"),
            File(root, "feature/prayer/src/main/java/com/sidr/launcher/feature/prayer"),
        )
        prayerSourceDirs.forEach { dir -> assertTrue("expected prayer source dir to exist: ${dir.path}", dir.isDirectory) }
        val scannedFiles = prayerSourceDirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" } }
        assertTrue(
            "the scan walked zero .kt files - the guard would vacuously pass; check the directory paths",
            scannedFiles.isNotEmpty(),
        )

        val forbiddenTerms = listOf(
            "AiRequest",
            "PromptContextBuilder",
            "GenerativeAiEngine",
            "GenerateReplyUseCase",
            "CommandPlanner",
            "CatalogSchemaRenderer",
            "com.sidr.launcher.domain.ai",
        )

        val violations = prayerSourceDirs.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    val text = file.readText()
                    forbiddenTerms
                        .filter { term -> text.contains(term) }
                        .map { term -> "${file.relativeTo(root).path} contains $term" }
                }
        }

        assertTrue(
            "Prayer must stay local-only; a path to the generative/outbound layer was found:\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    // ---- Guard 6: log audit ---------------------------------------------------------------------

    @Test
    fun `guard 6 - zero Log or println calls exist anywhere in prayer's production sources`() {
        // Chosen rule (the brief's "simplest, airtight" option): assert ZERO Log./println statements
        // exist at all in prayer's production source trees, rather than trying to distinguish
        // "benign" logging from "leaky" logging line-by-line. This is airtight (any future log call,
        // location-bearing or not, fails the build) and matches the current reality: these three trees
        // contain no logging calls today.
        val root = repoRoot()
        val prayerSourceDirs = listOf(
            File(root, "data/prayer/src/main"),
            File(root, "feature/prayer/src/main"),
            File(root, "core/android/src/main/java/com/sidr/launcher/core/android/prayer"),
        )
        prayerSourceDirs.forEach { dir -> assertTrue("expected prayer source dir to exist: ${dir.path}", dir.isDirectory) }
        val scannedFiles = prayerSourceDirs.flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" } }
        assertTrue(
            "the scan walked zero .kt files - the guard would vacuously pass; check the directory paths",
            scannedFiles.isNotEmpty(),
        )

        val hits = prayerSourceDirs.flatMap { dir ->
            dir.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .flatMap { file ->
                    file.readLines().mapIndexedNotNull { index, line ->
                        if (line.contains("Log.") || line.contains("println")) {
                            "${file.relativeTo(root).path}:${index + 1}: ${line.trim()}"
                        } else {
                            null
                        }
                    }
                }
        }
        assertTrue(
            "prayer production source must never log (chosen rule: zero Log./println calls at all):\n" +
                hits.joinToString("\n"),
            hits.isEmpty(),
        )
    }

    // ---- shared helpers -------------------------------------------------------------------------

    private fun allSrcMainKotlinFiles(root: File): List<File> =
        root.walkTopDown()
            .onEnter { dir -> dir.name != "build" && dir.name != ".git" && dir.name != ".gradle" }
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.path.contains("${File.separator}src${File.separator}main${File.separator}") }
            .toList()

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "Could not locate repo root from ${System.getProperty("user.dir")}" }
    }
}
