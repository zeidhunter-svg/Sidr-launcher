package com.sidr.launcher.data.prayer

import com.sidr.launcher.domain.prayer.CityIndex
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.text.Normalizer
import java.util.zip.GZIPInputStream
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.round
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [CityIndex] implementation over a bundled, gzipped, offline city list (DS-6B Task 5) — the
 * primary manual prayer-location path: **zero permissions, zero network at runtime**.
 *
 * [assetOpener] is the asset-opener seam (mirrors `ModelStore`'s `vocabOpener` in
 * `data/ai-local/.../ModelStore.kt`): a plain `() -> InputStream` with no `android.content.Context`
 * dependency, so `:data:prayer` stays Hilt/Android-context-free. Production wiring (an
 * `AssetManager`-backed opener) is DI work for a later task; JVM tests pass a
 * `FileInputStream`-backed opener reading the real generated asset.
 *
 * The asset is `tools/prayer/build_city_index.py`'s output
 * (`data/prayer/src/main/assets/prayer/cities.gz`, see `tools/prayer/README.md` for provenance/
 * license): a gzip stream, UTF-8 text, one `#`-prefixed CC-BY 4.0 attribution comment line followed
 * by pipe-separated data rows `asciiName|displayName|countryCode|lat2dp|lon2dp|tzId`.
 *
 * Parsing is **lazy** (nothing is read until the first [search]) and runs on [Dispatchers.Default]
 * off the caller's dispatcher; the parsed result is cached in memory after the first successful
 * parse (a [Mutex] serializes concurrent first-callers so the file is only ever parsed once).
 * Malformed individual rows are skipped, never thrown (a bad row must not break the whole index);
 * an unreadable/missing asset degrades to an empty index rather than a crash — [CancellationException]
 * is always re-thrown, never swallowed.
 *
 * **Defensive rounding:** every row's latitude/longitude is re-rounded to 2 decimal places via
 * `round(v * 100.0) / 100.0` before constructing a [PrayerLocation] — even though the asset already
 * stores 2dp-formatted text — because [PrayerLocation.init] requires
 * `round(v * 100.0) / 100.0 == v` bit-for-bit, and re-deriving the value through the exact formula
 * the contract checks against is the only way to *guarantee* that identity holds regardless of any
 * floating-point representation quirk in how the text was written.
 *
 * **Search:** case- and diacritic-insensitive. Both the query and each candidate's `asciiName` +
 * display label are run through [normalize] ([Normalizer.Form.NFD] decomposition, combining marks
 * (`\p{Mn}`) stripped, lowercased) before comparison, so a diacritic query (e.g. `"Kazán"`) matches
 * a plain-ASCII row (`"Kazan"`) and vice versa. Prefix matches on either field rank ahead of
 * contains-only matches; ties keep the asset's original (population-descending-ish, whatever
 * `tools/prayer/build_city_index.py` emitted) order, since [List.sortedBy] is stable. A blank query
 * (or a query that normalizes to blank, e.g. all-combining-marks) returns an empty list — a valid
 * "no match", never an error, matching the [CityIndex] contract.
 */
class BundledCityIndex(
    private val assetOpener: () -> InputStream,
) : CityIndex {

    /** One successfully parsed asset row, kept in memory. Pre-normalized for fast repeat search. */
    private data class ParsedCity(
        val normalizedAsciiName: String,
        val normalizedLabel: String,
        val location: PrayerLocation,
    )

    private val loadMutex = Mutex()

    @Volatile
    private var cachedCities: List<ParsedCity>? = null

    override suspend fun search(query: String, limit: Int): List<PrayerLocation> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isBlank()) return emptyList()

        return loadParsedCities()
            .asSequence()
            .mapNotNull { city -> rank(city, normalizedQuery)?.let { it to city } }
            .sortedBy { (rank, _) -> rank }
            .take(limit)
            .map { (_, city) -> city.location }
            .toList()
    }

    /**
     * Internal, same-module-test-visible: every city the asset successfully parsed into a
     * [PrayerLocation], with no query filter applied. Not part of the [CityIndex] contract — kept
     * `internal` rather than widening [search]'s public blank-query behaviour, purely so
     * `BundledCityIndexTest` can assert whole-asset invariants (row count, every `tzId` parseable)
     * directly against what the real production parser produced.
     */
    internal suspend fun allCities(): List<PrayerLocation> = loadParsedCities().map { it.location }

    private fun rank(city: ParsedCity, normalizedQuery: String): Int? = when {
        city.normalizedAsciiName.startsWith(normalizedQuery) ||
            city.normalizedLabel.startsWith(normalizedQuery) -> RANK_PREFIX

        city.normalizedAsciiName.contains(normalizedQuery) ||
            city.normalizedLabel.contains(normalizedQuery) -> RANK_CONTAINS

        else -> null
    }

    private suspend fun loadParsedCities(): List<ParsedCity> {
        cachedCities?.let { return it }
        return loadMutex.withLock {
            cachedCities ?: withContext(Dispatchers.Default) { parseAsset() }.also { cachedCities = it }
        }
    }

    private fun parseAsset(): List<ParsedCity> = try {
        GZIPInputStream(assetOpener()).use { gzip ->
            BufferedReader(InputStreamReader(gzip, Charsets.UTF_8)).use { reader ->
                reader.lineSequence()
                    .mapNotNull(::parseLine)
                    .toList()
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Missing/corrupt/unreadable asset degrades to an empty index rather than a crash — a
        // manual-location picker with no results is recoverable; a launcher crash is not.
        emptyList()
    }

    private fun parseLine(rawLine: String): ParsedCity? {
        val line = rawLine.trimEnd('\r')
        if (line.isBlank() || line.startsWith("#")) return null // CC-BY attribution header row

        val parts = line.split('|')
        if (parts.size != 6) return null // malformed row — skip, don't throw

        val asciiName = parts[0]
        val displayName = parts[1]
        // parts[2] = countryCode: not needed for search or PrayerLocation, column kept for the
        // asset's own documentation/debuggability (see tools/prayer/README.md).
        val tzId = parts[5]
        if (asciiName.isBlank() || displayName.isBlank() || tzId.isBlank()) return null

        val rawLat = parts[3].toDoubleOrNull() ?: return null
        val rawLon = parts[4].toDoubleOrNull() ?: return null
        val lat2dp = roundTo2dp(rawLat)
        val lon2dp = roundTo2dp(rawLon)
        if (lat2dp !in -90.0..90.0 || lon2dp !in -180.0..180.0) return null

        val location = try {
            PrayerLocation(
                label = displayName,
                lat2dp = lat2dp,
                lon2dp = lon2dp,
                tzId = tzId,
                source = PrayerLocationSource.CITY,
            )
        } catch (e: IllegalArgumentException) {
            null // a row that still fails the domain contract after defensive rounding — skip it
        } ?: return null

        return ParsedCity(
            normalizedAsciiName = normalize(asciiName),
            normalizedLabel = normalize(displayName),
            location = location,
        )
    }

    private companion object {
        const val RANK_PREFIX = 0
        const val RANK_CONTAINS = 1

        private val COMBINING_MARKS = Regex("\\p{Mn}+")

        /** Re-derives a 2dp value via the exact formula [PrayerLocation.init] checks against. */
        fun roundTo2dp(value: Double): Double = round(value * 100.0) / 100.0

        /** NFD-decompose, strip combining marks, lowercase — diacritic- and case-insensitive key. */
        fun normalize(s: String): String {
            val decomposed = Normalizer.normalize(s, Normalizer.Form.NFD)
            return COMBINING_MARKS.replace(decomposed, "").lowercase()
        }
    }
}
