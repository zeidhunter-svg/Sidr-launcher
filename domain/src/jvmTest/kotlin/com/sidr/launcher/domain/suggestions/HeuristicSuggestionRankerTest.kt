package com.sidr.launcher.domain.suggestions

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Table-driven tests for the pure [HeuristicSuggestionRanker] (Block S, S3/S6): recency, frequency,
 * time-of-day prior, dedup-by-actionId, bound-to-N, determinism, empty.
 */
class HeuristicSuggestionRankerTest {

    private val ranker = HeuristicSuggestionRanker()
    private val ctxWork = SuggestionContext(TimeOfDay.WORK, nowEpochMs = 0L)

    private fun s(label: String, id: String, source: SuggestionSource, score: Double) =
        Suggestion(label = label, actionId = id, source = source, score = score)

    private fun ids(list: List<Suggestion>) = list.map { it.actionId }

    @Test
    fun `recency - higher-scored recent-usage ranks first`() {
        val high = s("A", "a", SuggestionSource.RECENT_USAGE, 10.0)
        val low = s("B", "b", SuggestionSource.RECENT_USAGE, 5.0)

        assertEquals(listOf("a", "b"), ids(ranker.rank(listOf(low, high), ctxWork)))
    }

    @Test
    fun `frequency - higher-scored frequent-usage ranks first`() {
        val high = s("F1", "f1", SuggestionSource.FREQUENT_USAGE, 8.0)
        val low = s("F2", "f2", SuggestionSource.FREQUENT_USAGE, 3.0)

        assertEquals(listOf("f1", "f2"), ids(ranker.rank(listOf(low, high), ctxWork)))
    }

    @Test
    fun `time prior - calendar outranks equal-score usage during work, loses at night`() {
        val cal = s("Cal", "cal", SuggestionSource.CALENDAR, 5.0)
        val rec = s("Rec", "rec", SuggestionSource.RECENT_USAGE, 5.0)

        // WORK: 5 * 1.10 * 1.20 = 6.6 > 5.0
        assertEquals(listOf("cal", "rec"), ids(ranker.rank(listOf(rec, cal), ctxWork)))

        // NIGHT: 5 * 1.10 * 0.80 = 4.4 < 5.0 → order flips with context only.
        val ctxNight = SuggestionContext(TimeOfDay.NIGHT, nowEpochMs = 0L)
        assertEquals(listOf("rec", "cal"), ids(ranker.rank(listOf(rec, cal), ctxNight)))
    }

    @Test
    fun `dedup - same actionId collapses to the highest-weighted candidate`() {
        val lo = s("D-lo", "dup", SuggestionSource.RECENT_USAGE, 3.0)
        val hi = s("D-hi", "dup", SuggestionSource.RECENT_USAGE, 9.0)

        val result = ranker.rank(listOf(lo, hi), ctxWork)

        assertEquals(1, result.size)
        assertEquals("D-hi", result.single().label)
    }

    @Test
    fun `bound - result is capped at maxResults, keeping the top weighted`() {
        val candidates = (1..7).map { s("L$it", "id$it", SuggestionSource.RECENT_USAGE, it.toDouble()) }

        // Default cap = 5 → top scores 7,6,5,4,3.
        assertEquals(listOf("id7", "id6", "id5", "id4", "id3"), ids(ranker.rank(candidates, ctxWork)))

        // Custom cap = 2 → top scores 7,6.
        assertEquals(listOf("id7", "id6"), ids(HeuristicSuggestionRanker(maxResults = 2).rank(candidates, ctxWork)))
    }

    @Test
    fun `determinism - equal weights tie-break by label then actionId`() {
        val zeta = s("Zeta", "z", SuggestionSource.RECENT_USAGE, 5.0)
        val alpha = s("Alpha", "a", SuggestionSource.RECENT_USAGE, 5.0)

        assertEquals(listOf("a", "z"), ids(ranker.rank(listOf(zeta, alpha), ctxWork)))
    }

    @Test
    fun `empty - empty candidates yield empty result`() {
        assertEquals(emptyList<Suggestion>(), ranker.rank(emptyList(), ctxWork))
    }
}
