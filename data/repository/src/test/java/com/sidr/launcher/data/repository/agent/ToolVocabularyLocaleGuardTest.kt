package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.intent.CommandNormalizer
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A1' Task 8, modelled on [com.sidr.launcher.data.repository.intent.FastPathLocaleGuardTest] and placed
 * beside its subject for the same reason that one sits beside `RuleBasedIntentMatcher`.
 *
 * A tool recognised only in English is a capability the other two locales silently do not have — the
 * same bug Этап 0.2 closed for FastPath's verbs, in the layer that decides whether a *registered* tool
 * is reachable at all. CLAUDE.md's hard rule ("`en`/`ru`/`tr` ship in the same commit as the feature")
 * is a completeness claim about user-reachable capability, not only about `strings.xml`.
 *
 * The guard reads the Kotlin constants directly rather than `res/values*` XML, because this vocabulary
 * is deliberately not UI text: `:data:repository` has no resource access and the vocabulary must stay
 * Android-free.
 *
 * It checks *coverage*, not position: a locale may carry its forms as prefixes (English/Russian SVO, and
 * verbless noun phrases in any language) or as suffixes (Turkish SOV), and either satisfies the locale.
 */
class ToolVocabularyLocaleGuardTest {

    private val supportedLocales = listOf("en", "ru", "tr")

    @Test
    fun `every tool entry carries a non-empty trigger for en, ru and tr`() {
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            supportedLocales.forEach { locale ->
                val covered = entry.prefixByLocale[locale].orEmpty().isNotEmpty() ||
                    entry.suffixByLocale[locale].orEmpty().isNotEmpty()
                if (!covered) {
                    failures += "tool '${entry.id.value}' has no trigger for locale '$locale': a tool " +
                        "recognised only in some locales is a capability the others silently lack"
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * A locale key mapped to an empty set is worse than a missing key: it reads as "covered" to anyone
     * skimming the table while recognising nothing.
     */
    @Test
    fun `no declared locale maps to an empty form set`() {
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            (entry.prefixByLocale + entry.suffixByLocale).forEach { (locale, forms) ->
                if (forms.isEmpty()) failures += "tool '${entry.id.value}' declares locale '$locale' with no forms"
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * Matching runs on `CommandNormalizer`-normalized text, so a trigger that is not itself already in
     * that form — uppercase, padded, or carrying a doubled space — can never match anything. Blank is
     * the degenerate case of the same rule.
     */
    @Test
    fun `every trigger is already in the normalizer's own canonical form`() {
        val failures = mutableListOf<String>()

        guardedEntries().forEach { entry ->
            (entry.prefixByLocale + entry.suffixByLocale).forEach { (locale, forms) ->
                forms.forEach { form ->
                    if (form.isBlank() || form != CommandNormalizer.normalize(form)) {
                        failures += "tool '${entry.id.value}' locale '$locale' has a trigger '$form' that " +
                            "normalized input can never equal (expected '${CommandNormalizer.normalize(form)}')"
                    }
                }
            }
        }

        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /**
     * The entry table — returned only **after** proving there is something to loop over.
     *
     * Every assertion in this class is a `forEach` over the table, so an empty or truncated table makes
     * the whole guard pass while checking nothing: delete the vocabulary and the locale claim becomes
     * green over zero tools. A guard that passes when there is nothing to check is this repository's
     * recorded failure mode, so the floor is asserted **here** rather than in one extra `@Test` — that
     * way no individual test in this class can be vacuous, including one added later by someone who
     * never read this comment.
     *
     * The floor is *containment*, deliberately: it names the two tools A1' shipped, because "a
     * registered tool is reachable" is this block's own success criterion and a table that still has
     * entries but has lost `set_timer` is as broken as an empty one. It is **not** equality against the
     * table, and it names no trigger strings — `A1"`'s twelfth entry and any ordinary rewording of a
     * trigger must stay green, or this becomes a guard nobody keeps. It is also not derived from
     * `Tier0IntentToolSource`: a future tool reached only by A4''s model planner is entitled to have no
     * deterministic trigger, and that is not this guard's business.
     */
    private fun guardedEntries(): List<ToolVocabulary.Entry> {
        val entries = ToolVocabulary().entries
        val missing = REQUIRED_TOOLS.filterNot { required -> entries.any { it.id == required } }
        assertTrue(
            "ToolVocabulary has no entry for ${missing.joinToString { it.value }} " +
                "(the table holds ${entries.size} entr${if (entries.size == 1) "y" else "ies"}). " +
                "Every assertion in this guard loops over that table, so it would otherwise pass by " +
                "having nothing to check. These two tools are the reachability A1' shipped: an entry " +
                "removed here makes the tool unreachable from text while it stays happily registered.",
            missing.isEmpty(),
        )
        return entries
    }

    private companion object {
        /** The floor, never the ceiling — see [guardedEntries]. */
        val REQUIRED_TOOLS = listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS)
    }
}
