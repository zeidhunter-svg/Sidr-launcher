package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.SecondGoal
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agentic restart plan (`docs/superpowers/plans/2026-08-18-agentic-track-restart.md`), Этап 0.2.
 *
 * FastPath is a latency optimization over the deterministic layer, not a filter on understanding
 * (CLAUDE.md Hard rules) — a locale silently missing a verb form here means a router-off/offline
 * user of that locale gets "Unknown command" for a phrasing that should just work, exactly the
 * bug this stage closes for `ru`/`tr`. Mirrors `LocaleCompletenessGuardTest`'s shape (fail the
 * build the moment a locale is missing a form), but reads [RuleBasedIntentMatcher]'s Kotlin
 * constants directly rather than parsing `res/values*` XML — this vocabulary is intentionally NOT
 * a UI string: `RuleBasedIntentMatcher` stays Android-free and `:data:repository` has no resource
 * access.
 */
class FastPathLocaleGuardTest {

    private val supportedLocales = listOf("en", "ru", "tr")

    @Test fun every_verb_form_set_covers_every_supported_locale() {
        val verbSets = mapOf(
            "LAUNCH_VERBS" to RuleBasedIntentMatcher.LAUNCH_VERBS,
            "INSTALL_VERBS" to RuleBasedIntentMatcher.INSTALL_VERBS,
            "SEARCH_VERBS" to RuleBasedIntentMatcher.SEARCH_VERBS,
        )
        val failures = mutableListOf<String>()
        verbSets.forEach { (name, forms) ->
            supportedLocales.forEach { locale ->
                val covered = forms.prefixByLocale[locale].orEmpty().isNotEmpty() ||
                    forms.suffixByLocale[locale].orEmpty().isNotEmpty()
                if (!covered) failures += "$name has no form for locale '$locale'"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun settings_keywords_cover_every_supported_locale() {
        val missing = supportedLocales.filter {
            RuleBasedIntentMatcher.SETTINGS_KEYWORDS_BY_LOCALE[it].orEmpty().isEmpty()
        }
        assertTrue("SETTINGS_KEYWORDS_BY_LOCALE missing locale(s): $missing", missing.isEmpty())
    }

    @Test fun simple_commands_cover_every_supported_locale() {
        val missing = supportedLocales.filter {
            RuleBasedIntentMatcher.SIMPLE_COMMANDS_BY_LOCALE[it].orEmpty().isEmpty()
        }
        assertTrue("SIMPLE_COMMANDS_BY_LOCALE missing locale(s): $missing", missing.isEmpty())
    }

    /**
     * A4′ phase 1: a clause led by one of FastPath's own command verbs is a second command by
     * definition, so every such verb is either one [SecondGoal] recognises on its side of a
     * conjunction (`en`/`ru` prefix verbs after it, `tr` suffix verbs before it) or is exempted HERE,
     * by name and with its reason. Adding a FastPath verb therefore forces a decision instead of
     * leaving a silent hole; and an exempted verb that crept back into the list is red too.
     */
    @Test fun every_fastpath_verb_is_a_second_goal_verb_or_named_as_an_exemption() {
        val exempt = mapOf(
            "открыть" to "infinitive — «как открыть и настроить …» is one query",
            "запустить" to "infinitive",
            "установить" to "infinitive — «как скачать и установить …» is one query",
            "найти" to "infinitive",
            "google" to "a noun inside a query far more often than a verb (\"apple and google\")",
        )
        val verbSets = listOf(
            RuleBasedIntentMatcher.LAUNCH_VERBS,
            RuleBasedIntentMatcher.INSTALL_VERBS,
            RuleBasedIntentMatcher.SEARCH_VERBS,
        )
        val after = SecondGoal.VERBS_AFTER_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
        val before = SecondGoal.VERBS_BEFORE_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
        val missing = verbSets.flatMap { it.allPrefixForms - after - exempt.keys } +
            verbSets.flatMap { it.allSuffixForms - before - exempt.keys }
        assertTrue("FastPath verbs SecondGoal neither knows nor exempts: $missing", missing.isEmpty())
        val readmitted = exempt.keys.filter { it in after || it in before }
        assertTrue("exempted verbs back in SecondGoal's lists: $readmitted", readmitted.isEmpty())
    }
}
