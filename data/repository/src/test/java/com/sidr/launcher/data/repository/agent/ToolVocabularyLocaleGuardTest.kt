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

        ToolVocabulary().entries.forEach { entry ->
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

        ToolVocabulary().entries.forEach { entry ->
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

        ToolVocabulary().entries.forEach { entry ->
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
}
