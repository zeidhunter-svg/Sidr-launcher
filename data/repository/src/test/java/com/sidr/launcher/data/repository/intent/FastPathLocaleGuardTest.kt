package com.sidr.launcher.data.repository.intent

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
}
