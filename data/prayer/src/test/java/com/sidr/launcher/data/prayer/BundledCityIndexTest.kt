package com.sidr.launcher.data.prayer

import com.sidr.launcher.domain.prayer.PrayerLocationSource
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.time.ZoneId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DS-6B Task 5 — [BundledCityIndex] against the REAL generated asset
 * (`data/prayer/src/main/assets/prayer/cities.gz`, built by `tools/prayer/build_city_index.py`).
 *
 * No Android, no network: [assetOpener] opens the file directly from disk via
 * [FileInputStream] — the same seam precedent as `ModelStore`'s `vocabOpener`
 * (`data/ai-local/.../ModelStore.kt`). `repoRoot()` walks up from `user.dir` to find
 * `settings.gradle.kts`, the same pattern used by `AliasPrivacyScopeGuardTest`
 * (`domain/src/test/.../AliasPrivacyScopeGuardTest.kt`), so this test is robust to whatever working
 * directory Gradle's `testDebugUnitTest` task happens to run from.
 */
class BundledCityIndexTest {

    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) {
            dir = dir.parentFile
        }
        return checkNotNull(dir) { "Could not locate repo root from ${System.getProperty("user.dir")}" }
    }

    private fun assetOpener(): () -> InputStream = {
        FileInputStream(File(repoRoot(), "data/prayer/src/main/assets/prayer/cities.gz"))
    }

    private fun newIndex(): BundledCityIndex = BundledCityIndex(assetOpener = assetOpener())

    @Test
    fun `searching istanbul finds at least one result with a valid tzId and source CITY`() = runTest {
        val results = newIndex().search("istanbul", limit = 10)

        assertTrue("expected at least one Istanbul match", results.isNotEmpty())
        val match = results.first()
        assertEquals(PrayerLocationSource.CITY, match.source)
        assertNotNull(ZoneId.of(match.tzId))
    }

    @Test
    fun `searching kazan finds at least one result with a valid tzId and source CITY`() = runTest {
        val results = newIndex().search("kazan", limit = 10)

        assertTrue("expected at least one Kazan match", results.isNotEmpty())
        val match = results.first()
        assertEquals(PrayerLocationSource.CITY, match.source)
        assertNotNull(ZoneId.of(match.tzId))
    }

    @Test
    fun `searching makkah finds at least one result with a valid tzId and source CITY`() = runTest {
        // GeoNames' own canonical name/asciiname for this city IS "Makkah" (not "Mecca") — see
        // tools/prayer/README.md.
        val results = newIndex().search("makkah", limit = 10)

        assertTrue("expected at least one Makkah match", results.isNotEmpty())
        val match = results.first()
        assertEquals(PrayerLocationSource.CITY, match.source)
        assertNotNull(ZoneId.of(match.tzId))
    }

    @Test
    fun `a diacritic query form still matches the plain ascii city`() = runTest {
        // "Kazán" (Latin small letter a with acute) NFD-decomposes to "Kaza" + combining acute + "n";
        // stripping combining marks + lowercasing must still land on "kazan", matching the asciiName
        // "Kazan" in the asset.
        val results = newIndex().search("Kazán", limit = 10)

        assertTrue("expected the diacritic query to still match a Kazan row", results.isNotEmpty())
        assertTrue(results.any { it.label.contains("Kazan", ignoreCase = true) })
    }

    @Test
    fun `a guaranteed miss returns an empty list, never an error`() = runTest {
        val results = newIndex().search("zzzzznonexistentcityxyzzzz999", limit = 10)

        assertEquals(emptyList<Any>(), results)
    }

    @Test
    fun `asset row count is within the 10k to 20k target band`() = runTest {
        val all = newIndex().allCities()

        assertTrue(
            "expected 10,000..20,000 rows, was ${all.size}",
            all.size in 10_000..20_000,
        )
    }

    @Test
    fun `every parsed row carries a tzId parseable by java time ZoneId`() = runTest {
        val all = newIndex().allCities()

        assertTrue("expected a non-empty asset", all.isNotEmpty())

        val invalid = all.filter { location ->
            runCatching { ZoneId.of(location.tzId) }.isFailure
        }

        assertTrue(
            "found ${invalid.size} row(s) with an unparseable tzId, e.g. " +
                invalid.take(5).joinToString { "${it.label}(${it.tzId})" },
            invalid.isEmpty(),
        )
    }

}
