package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.tool.ToolId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * A1' Task 8 — recognition, and the three ways it is deliberately narrow: it normalizes through the
 * launcher's one normalizer, it respects word boundaries, and it refuses an ambiguous text rather than
 * picking a winner.
 */
class ToolVocabularyTest {

    private val vocabulary = ToolVocabulary()
    private val defaultLocale: Locale = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `a trigger is recognised and the rest of the line is handed over verbatim`() {
        assertEquals(
            ToolMatch(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")),
            vocabulary.match("set a timer for 10 minutes"),
        )
    }

    @Test
    fun `a zero-argument trigger matches on its own and carries no arguments`() {
        assertEquals(
            ToolMatch(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, emptyMap()),
            vocabulary.match("system settings"),
        )
    }

    /**
     * `CommandNormalizer` collapses internal whitespace; a hand-rolled `trim().lowercase()` does not,
     * so this text would miss for a reason no user could see. Ruling R14 — the vocabulary shares the
     * launcher's one normalizer rather than owning a second.
     */
    @Test
    fun `doubled spaces inside the trigger still match`() {
        assertEquals(
            ToolMatch(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")),
            vocabulary.match("  Set  a   timer  for   10   minutes "),
        )
    }

    /**
     * The locale half of R14. `CommandNormalizer` lowercases with `Locale.ROOT` **deliberately**: on a
     * Turkish device `Locale.getDefault()` maps "I" to the dotless "ı", and every trigger containing an
     * `i` would stop matching uppercase input. Setting the default locale is what makes the choice
     * observable at all.
     *
     * **What it discriminates, measured rather than assumed:** it goes RED against
     * `lowercase(Locale.getDefault())` and stays GREEN against Kotlin's bare `String.lowercase()`,
     * which is *already* `Locale.ROOT`. So this guards against someone re-localizing the normalization,
     * not against dropping `CommandNormalizer` — that is what the whitespace test above catches.
     */
    @Test
    fun `uppercase input still matches with a Turkish default locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr"))

        assertEquals(
            ToolMatch(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")),
            vocabulary.match("TIMER FOR 10 MINUTES"),
        )
    }

    @Test
    fun `blank text matches nothing`() {
        assertEquals(null, vocabulary.match(""))
        assertEquals(null, vocabulary.match("   "))
    }

    /**
     * A trigger must end on a word boundary. Without that, "timer for" swallows "timer format" and the
     * agent plans a timer of duration "mat" — recognised, planned, and only failing three layers later
     * in the worker's duration parse. Mirrors `RuleBasedIntentMatcher`, which matches `"$verb "`.
     */
    @Test
    fun `a trigger that runs into the next word is not a match`() {
        assertEquals(null, vocabulary.match("timer format"))
    }

    /**
     * A zero-argument tool takes no text, so trailing words mean the user meant something else. Letting
     * them through would silently discard them and open system settings for
     * "system settings for my car" — the broadest possible reading of a command, chosen in silence.
     * Declining costs nothing: routing falls through to the model exactly as it did before.
     */
    @Test
    fun `a zero-argument trigger with words after it is not a match`() {
        assertEquals(null, vocabulary.match("system settings for my car"))
    }

    /**
     * The fail-closed branch, exercised on a purpose-built pair because the two shipping entries cannot
     * collide (see [ToolVocabulary.match]'s KDoc). Two entries claiming one text is a vocabulary defect,
     * not a ranking problem, so it yields nothing rather than a guess — the same choice the federation
     * makes for a colliding id.
     */
    @Test
    fun `a text two entries both claim yields no match rather than a guess`() {
        val colliding = ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = ToolId("alpha"),
                    prefixByLocale = mapOf("en" to setOf("do the thing")),
                ),
                ToolVocabulary.Entry(
                    id = ToolId("beta"),
                    prefixByLocale = mapOf("en" to setOf("do the thing")),
                ),
            ),
        )

        assertEquals(null, colliding.match("do the thing"))
    }

    /** The most specific trigger wins when one entry declares two that both fit. */
    @Test
    fun `the longest matching trigger of an entry wins`() {
        val nested = ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = ToolId("alpha"),
                    prefixByLocale = mapOf("en" to setOf("remind", "remind me to")),
                    argName = "what",
                ),
            ),
        )

        assertEquals(
            ToolMatch(ToolId("alpha"), mapOf("what" to "call mum")),
            nested.match("remind me to call mum"),
        )
    }

    /**
     * Pins the deliberate short-circuit in `matchIn`: a prefix hit is final, refusal included.
     *
     * "set timer" matches the prefix exactly and leaves no duration, so the entry declines — and the
     * suffix "timer" is **not** then tried against the same text. Chaining them would match, reading
     * the trigger's own word "set" as the duration; refusing for a reason that can be stated beats
     * matching for one that cannot. Chaining is a defensible alternative, but adopting it must be a
     * change that deletes this test on purpose rather than an accident that discovers it.
     */
    @Test
    fun `a declined prefix does not fall through to the suffix`() {
        val bothShapes = ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = ToolId("alpha"),
                    prefixByLocale = mapOf("en" to setOf("set timer")),
                    suffixByLocale = mapOf("tr" to setOf("timer")),
                    argName = "duration",
                ),
            ),
        )

        assertEquals(null, bothShapes.match("set timer"))
    }
}
