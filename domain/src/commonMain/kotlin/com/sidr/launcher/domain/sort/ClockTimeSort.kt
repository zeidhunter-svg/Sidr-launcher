package com.sidr.launcher.domain.sort

/**
 * The clock-time sort (A4′ phase 3b, B-5): «7:30», «19:45».
 *
 * **It validates; it does not rewrite** — [DurationSort]'s rule: [resolver] answers the words as written
 * or [SortResolution.Unreadable], and the worker reads them with the same [timeOf] after «Выполнить».
 * [timeOf] is the worker's former private `parseClockTime`, moved verbatim.
 */
object ClockTimeSort {

    /**
     * `H:MM` or `HH:MM` only — a bounded token (spec §7.1), never a guess, never a default. The
     * vocabulary hands this string over unparsed, exactly like [DurationSort.secondsOf]'s duration; there
     * is no free-text reading to normalize away here, only a fixed clock-time shape to accept or decline.
     */
    fun timeOf(words: String): Pair<Int, Int>? {
        val match = Regex("""^(\d{1,2}):(\d{2})$""").find(words.trim()) ?: return null
        val hour = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].toIntOrNull() ?: return null
        return if (hour in 0..23 && minute in 0..59) hour to minute else null
    }

    /** Validates, never rewrites (B-5). */
    val resolver: SortResolver = SortResolver { words ->
        if (timeOf(words) != null) SortResolution.Resolved(words) else SortResolution.Unreadable
    }
}
