package com.sidr.launcher.domain.suggestions

/**
 * Port: pure ranking policy over suggestion candidates (Phase 7, Block S).
 *
 * Synchronous and side-effect-free — it reorders, dedups, and bounds; it never does I/O. The shipping
 * implementation is [HeuristicSuggestionRanker]; the optional ONNX semantic re-rank (Block V) is a
 * **decorator** over this port that falls back to the heuristic order when the model/gate is off
 * (Fork F7-5).
 */
interface SuggestionRanker {
    /** Ordered (best-first), deduplicated, bounded result for the given [context]. */
    fun rank(candidates: List<Suggestion>, context: SuggestionContext): List<Suggestion>
}

/**
 * Pure, deterministic heuristic ranker — the shipping policy that needs no model and runs on every
 * device (Fork F7-5).
 *
 * Weighting (all values heuristic and tunable — the *order contract* is what tests pin, not the exact
 * constants):
 *  - **provider score** — the candidate's [Suggestion.score], in which usage providers (Block U) have
 *    already folded recency + frequency;
 *  - **× per-source weight** ([sourceWeight]) — a coarse trust prior per [SuggestionSource];
 *  - **× time-of-day prior** ([timePrior]) — boosts time-relevant sources for the current
 *    [SuggestionContext.timeOfDay].
 *
 * Then **dedup by `actionId`** keeping the highest-weighted candidate, **sort by weighted score
 * descending** with a deterministic tie-break (`label`, then `actionId`), and **bound to [maxResults]**.
 *
 * @param maxResults hard cap on the returned list. Defaults to [DEFAULT_MAX_SUGGESTIONS], which mirrors
 *   the data-layer `MAX_CACHED_SUGGESTIONS` (= 5); the cache repository still enforces its own cap on
 *   write, so this is the engine-side bound, not the storage bound.
 */
class HeuristicSuggestionRanker(
    private val maxResults: Int = DEFAULT_MAX_SUGGESTIONS,
) : SuggestionRanker {

    override fun rank(
        candidates: List<Suggestion>,
        context: SuggestionContext,
    ): List<Suggestion> {
        if (candidates.isEmpty()) return emptyList()

        fun weight(s: Suggestion): Double =
            s.score * sourceWeight(s.source) * timePrior(s.source, context.timeOfDay)

        return candidates
            // Dedup by actionId, keeping the highest-weighted candidate for each target.
            .groupBy { it.actionId }
            .map { (_, group) -> group.maxByOrNull(::weight)!! }
            // Best-first, with a deterministic tie-break so the result is stable for tests/UI.
            .sortedWith(
                compareByDescending<Suggestion> { weight(it) }
                    .thenBy { it.label }
                    .thenBy { it.actionId },
            )
            .take(maxResults.coerceAtLeast(0))
    }

    /** Coarse per-source trust prior. Total over [SuggestionSource] (a new source must be added here). */
    private fun sourceWeight(source: SuggestionSource): Double = when (source) {
        SuggestionSource.CALENDAR -> 1.10
        SuggestionSource.RECENT_USAGE -> 1.00
        SuggestionSource.SEMANTIC -> 1.00
        SuggestionSource.FREQUENT_USAGE -> 0.95
        SuggestionSource.LOCATION -> 0.85
        SuggestionSource.TIME_OF_DAY -> 0.80
    }

    /**
     * Time-of-day prior. Most sources are time-neutral (1.0). Time-of-day suggestions are emitted for
     * the current bucket by their provider, so they are always boosted. Calendar matters most in the
     * working part of the day and least at night.
     */
    private fun timePrior(source: SuggestionSource, timeOfDay: TimeOfDay): Double = when (source) {
        SuggestionSource.TIME_OF_DAY -> 1.30
        SuggestionSource.CALENDAR -> when (timeOfDay) {
            TimeOfDay.MORNING, TimeOfDay.WORK -> 1.20
            TimeOfDay.EVENING -> 1.00
            TimeOfDay.NIGHT -> 0.80
        }
        else -> 1.00
    }

    companion object {
        /** Mirrors the data-layer `MAX_CACHED_SUGGESTIONS` (= 5); the cache repo enforces its own cap. */
        const val DEFAULT_MAX_SUGGESTIONS: Int = 5
    }
}
