package com.sidr.launcher.domain.sort

/**
 * The duration sort (A4′ phase 3b, B-5): «10 minutes», «10 минут», «10 dakika», «2 saat» — and, since the
 * owner's decision of 2026-11-17, «полчаса», «полтора часа» and their `en`/`tr` neighbours ([PHRASES]).
 *
 * **It validates; it does not rewrite.** [resolver] answers [SortResolution.Resolved] with the words
 * as written, or [SortResolution.Unreadable]; the worker (`Tier0IntentToolWorker`) reads the same
 * words with the same [secondsOf] after «Выполнить», so a persisted plan and its step line are unchanged
 * by this sort's existence.
 *
 * [secondsOf] is the worker's former private `parseSeconds`, **moved verbatim** — its unit sets, the
 * exact-token rule and the four-digit cap included — so that a duration the device accepts before the
 * card is exactly the duration the worker can run. `Tier0IntentToolWorkerTest` still drives the worker
 * through it and [DurationSortTest] reads it directly; together they prove the move.
 */
object DurationSort {

    /**
     * One of the whole [PHRASES], or a leading integer plus an optional unit word. Returns `null` for anything it cannot read, and the
     * caller fails closed on that: an agent that silently starts a zero-length timer is worse than one
     * that declines and says so. Same reasoning as `InvocationValidator.resolve`'s refusal to bind a
     * blank.
     *
     * **The unit token is matched exactly, never by prefix** (A1′ fix round 1, controller ruling R11). A
     * `startsWith` match let `"1 min 30 sec"` silently read as 60 seconds instead of 90, and let
     * `"10 minecraft"` silently start a real 600-second timer from nonsense — the worse of the two
     * failures this KDoc already names, because it does not decline, it lies. The token left after the
     * digits must therefore be a single word — any whitespace or digit in it fails closed rather than
     * being read as "the first word matched" — and that word is compared with `==` against an explicit,
     * intentionally narrow list per unit. An unlisted-but-valid inflection is declined, not guessed.
     *
     * Unit words are read here rather than in `ToolVocabulary` because they belong to reading the
     * **value**, not to recognising the tool; the vocabulary hands this string over **unparsed** — it
     * never reads the value for meaning. Not *verbatim*, though: the vocabulary matches on
     * `CommandNormalizer`-normalized text, so what arrives from step 2b is already lower-cased and
     * whitespace-collapsed. Harmless for a duration — the trim/lowercase below is then redundant rather
     * than wrong — but see `ToolVocabulary.Entry` for why a free-text argument will need the raw span.
     */
    fun secondsOf(words: String): Int? {
        val text = words.trim().lowercase()
        PHRASES[text]?.let { return it }
        val digits = text.takeWhile { it.isDigit() }
        val amount = digits.toIntOrNull() ?: return null
        if (amount <= 0 || digits.length > 4) return null
        val unit = text.drop(digits.length).trim()
        val multiplier = when {
            unit.isEmpty() -> MINUTE
            unit.any { it.isWhitespace() || it.isDigit() } -> return null
            unit in SECOND_FORMS -> 1
            unit in MINUTE_FORMS -> MINUTE
            unit in HOUR_FORMS -> MINUTE * 60
            else -> return null
        }
        return amount * multiplier
    }

    /** Validates, never rewrites (B-5): the words themselves resolve, or they are unreadable. */
    val resolver: SortResolver = SortResolver { words ->
        if (secondsOf(words) != null) SortResolution.Resolved(words) else SortResolution.Unreadable
    }

    private const val MINUTE = 60

    /**
     * Owner decision 2026-11-17: a half hour and an hour and a half, said without a number — WHOLE phrases,
     * compared exactly like the unit words (no prefix, no trailing words: B-15 for value slots rests on that).
     * The list is controller ruling P4-R1; «yarim saat» is there only because the invariant `lowercase()` turns
     * a typed «YARIM» into it. A diminutive («полчасика») is not a phrase here — the model reads it online.
     */
    private val PHRASES: Map<String, Int> = mapOf(
        "полчаса" to 30 * MINUTE,
        "полтора часа" to 90 * MINUTE,
        "half an hour" to 30 * MINUTE,
        "half hour" to 30 * MINUTE,
        "an hour and a half" to 90 * MINUTE,
        "one and a half hours" to 90 * MINUTE,
        "yarım saat" to 30 * MINUTE,
        "yarim saat" to 30 * MINUTE,
        "bir buçuk saat" to 90 * MINUTE,
    )
    private val SECOND_FORMS = setOf("sec", "secs", "second", "seconds", "сек", "секунда", "секунды", "секунд", "saniye")
    private val MINUTE_FORMS = setOf("min", "mins", "minute", "minutes", "мин", "минута", "минуты", "минут", "dakika", "dk")
    private val HOUR_FORMS = setOf("hour", "hours", "hr", "hrs", "час", "часа", "часов", "saat")
}
