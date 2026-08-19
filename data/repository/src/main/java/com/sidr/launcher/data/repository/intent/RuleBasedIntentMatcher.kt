package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.intent.UrlClassification
import com.sidr.launcher.domain.intent.UrlDetector

/**
 * Rule-based implementation of [IntentMatcher]. Android-free — no Context or PackageManager.
 * Expects already-normalized input (lowercase, trimmed, spaces collapsed via CommandNormalizer).
 *
 * Rule priority:
 *  1. Launch verb (en/ru prefix "<verb> <app>"; tr suffix "<app> <verb>" — Turkish is SOV, e.g.
 *     "telegramı aç") — produces LaunchAppIntent, even for keywords like "settings". Exception
 *     (AIL-2, Q2): when the verb argument is a high-confidence URL, "open <url>" opens the site
 *     instead of launching an app.
 *  1b. Install verb (en/ru prefix, tr suffix) — Play Store search (AIL-2, Q3).
 *  2. Search verb (en/ru prefix, tr suffix).
 *  3. Settings bare keywords (no verb).
 *  4. Simple command table.
 *  5. Bare URL / site (AIL-2, R6): high-confidence URL → OpenUrlIntent; domain-shaped but
 *     ambiguous → web SearchIntent; otherwise fall through. Never opens a guessed/malformed URL.
 *  6. Fallback → UnknownIntent.
 *
 * Locale forms (agentic restart plan, Этап 0.2): FastPath is a latency optimization, not a filter
 * on understanding — a locale silently missing a verb form here means a router-off/offline user of
 * that locale gets "Unknown command" for a phrasing that should just work. The vocabulary stays
 * Kotlin constants (not `res/values` — this is not UI text and `:data:repository` has no resource
 * access); [FastPathLocaleGuardTest] enforces every set below carries a non-empty form for every
 * supported locale.
 *
 * Turkish word order is SOV (verb-final), unlike English/Russian SVO, so verb forms are keyed by
 * position as well as locale — [VerbForms.prefixByLocale] vs [VerbForms.suffixByLocale] — not just
 * locale. Turkish noun-case suffixes (e.g. the accusative "-ı" in "telegramı") are deliberately
 * **not** stripped from the extracted app-name query: that is full morphological analysis, out of
 * this stage's scope. A query still carrying a case suffix may not exact-match an installed app's
 * label in [com.sidr.launcher.domain.intent.IntentActionResolver] — a known limitation, not a
 * silently assumed fix.
 */
class RuleBasedIntentMatcher : IntentMatcher {

    override suspend fun match(normalizedInput: String): IntentMatchResult {
        val (intent, confidence, reason) = classify(normalizedInput)
        return IntentMatchResult(
            normalizedInput = normalizedInput,
            best = IntentCandidate(intent = intent, confidence = confidence, debugReason = reason),
            source = MatcherSource.RULE_BASED,
        )
    }

    private data class Classification(
        val intent: LauncherIntent,
        val confidence: Float,
        val reason: String,
    )

    /**
     * Locale-tagged verb forms for one grammatical role (launch/install/search).
     * [prefixByLocale] matches "<verb> <rest>" (English/Russian, SVO);
     * [suffixByLocale] matches "<rest> <verb>" (Turkish, SOV).
     */
    data class VerbForms(
        val prefixByLocale: Map<String, Set<String>>,
        val suffixByLocale: Map<String, Set<String>> = emptyMap(),
    ) {
        val allPrefixForms: Set<String> = prefixByLocale.values.flatten().toSet()
        val allSuffixForms: Set<String> = suffixByLocale.values.flatten().toSet()
        val allForms: Set<String> = allPrefixForms + allSuffixForms
    }

    private fun classify(input: String): Classification {
        if (input.isEmpty()) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "empty input"),
                0.0f,
                "empty input",
            )
        }

        // 1. Launch verb: "<verb> <app>" (en/ru) or "<app> <verb>" (tr, SOV).
        val launchPrefix = LAUNCH_VERBS.allPrefixForms.firstOrNull { input.startsWith("$it ") }
        val launchSuffix = if (launchPrefix == null) {
            LAUNCH_VERBS.allSuffixForms.firstOrNull { input.endsWith(" $it") }
        } else null
        if (launchPrefix != null || launchSuffix != null) {
            val verb = launchPrefix ?: launchSuffix!!
            val appQuery = if (launchPrefix != null) {
                input.removePrefix("$verb ").trim()
            } else {
                input.removeSuffix(" $verb").trim()
            }
            if (appQuery.isEmpty()) {
                return Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "launch verb without app name"),
                    0.30f,
                    "launch verb '$verb' with no query",
                )
            }
            // Q2: only a high-confidence URL diverts "open <x>" to the browser; anything else
            // (a plain name, or a domain-shaped ambiguous token) stays an app launch as before.
            val urlHit = UrlDetector.classify(appQuery)
            if (urlHit is UrlClassification.Url) {
                return Classification(
                    LauncherIntent.OpenUrlIntent(url = urlHit.url),
                    0.90f,
                    "launch verb '$verb' → url '${urlHit.url}'",
                )
            }
            return Classification(
                LauncherIntent.LaunchAppIntent(displayNameQuery = appQuery),
                0.90f,
                "launch verb '$verb' → query '$appQuery'",
            )
        }
        if (input in LAUNCH_VERBS.allForms) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "bare launch verb"),
                0.30f,
                "bare launch verb '$input'",
            )
        }

        // 1b. Install verb: "<verb> <app>" (en/ru) or "<app> <verb>" (tr) → Play Store search
        //     (AIL-2, Q3 — "install" only).
        val installPrefix = INSTALL_VERBS.allPrefixForms.firstOrNull { input.startsWith("$it ") }
        val installSuffix = if (installPrefix == null) {
            INSTALL_VERBS.allSuffixForms.firstOrNull { input.endsWith(" $it") }
        } else null
        if (installPrefix != null || installSuffix != null) {
            val verb = installPrefix ?: installSuffix!!
            val appName = if (installPrefix != null) {
                input.removePrefix("$verb ").trim()
            } else {
                input.removeSuffix(" $verb").trim()
            }
            return if (appName.isNotEmpty()) {
                Classification(
                    LauncherIntent.PlayStoreSearchIntent(query = appName),
                    0.90f,
                    "install verb '$verb' → play-store query '$appName'",
                )
            } else {
                Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "install verb without app name"),
                    0.30f,
                    "install verb '$verb' with no query",
                )
            }
        }
        if (input in INSTALL_VERBS.allForms) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "bare install verb"),
                0.30f,
                "bare install verb '$input'",
            )
        }

        // 2. Search verb: "<verb> <query>" (en/ru) or "<query> <verb>" (tr).
        val searchPrefix = SEARCH_VERBS.allPrefixForms.firstOrNull { input.startsWith("$it ") }
        val searchSuffix = if (searchPrefix == null) {
            SEARCH_VERBS.allSuffixForms.firstOrNull { input.endsWith(" $it") }
        } else null
        if (searchPrefix != null || searchSuffix != null) {
            val verb = searchPrefix ?: searchSuffix!!
            val query = if (searchPrefix != null) {
                input.removePrefix("$verb ").trim()
            } else {
                input.removeSuffix(" $verb").trim()
            }
            return if (query.isNotEmpty()) {
                Classification(
                    LauncherIntent.SearchIntent(query = query, target = SearchTarget.WEB),
                    0.90f,
                    "search verb '$verb' → query '$query'",
                )
            } else {
                Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "search verb without query"),
                    0.30f,
                    "search verb '$verb' with no query",
                )
            }
        }

        // 3. Settings bare keywords (verb-free; en/ru/tr forms).
        if (input in SETTINGS_KEYWORDS) {
            return Classification(
                LauncherIntent.OpenSettingsIntent(),
                0.95f,
                "settings keyword '$input'",
            )
        }

        // 4. Simple command table (en/ru/tr forms).
        val simpleCommand = SIMPLE_COMMANDS[input]
        if (simpleCommand != null) {
            return Classification(
                LauncherIntent.SimpleCommandIntent(simpleCommand),
                0.95f,
                "simple command '$input'",
            )
        }

        // 5. Bare URL / site (no verb). High-confidence URL → open; domain-shaped but ambiguous
        //    (unknown TLD, punycode/IDN, or a case-sensitive query) → web search, never a silent open
        //    (R6). A non-URL token yields UrlClassification.None and falls through to Unknown.
        when (val urlHit = UrlDetector.classify(input)) {
            is UrlClassification.Url -> return Classification(
                LauncherIntent.OpenUrlIntent(url = urlHit.url),
                0.90f,
                "bare url '${urlHit.url}'",
            )
            is UrlClassification.SearchFallback -> return Classification(
                LauncherIntent.SearchIntent(query = urlHit.term, target = SearchTarget.WEB),
                0.90f,
                "ambiguous url '${urlHit.term}' → web search",
            )
            UrlClassification.None -> Unit
        }

        // 6. Fallback
        return Classification(
            LauncherIntent.UnknownIntent(originalInput = input, reason = "no rule matched"),
            0.10f,
            "no rule matched for '$input'",
        )
    }

    /**
     * Public (not `private`) so [FastPathLocaleGuardTest] can read the vocabulary directly —
     * mirrors the `OutboundContextPolicy` / `PreferencesKeys.ALL_KEY_NAMES` guard-tested-inventory
     * precedent.
     */
    companion object {
        val LAUNCH_VERBS = VerbForms(
            prefixByLocale = mapOf(
                "en" to setOf("open", "launch", "start"),
                "ru" to setOf("открой", "открыть", "запусти", "запустить"),
            ),
            suffixByLocale = mapOf(
                "tr" to setOf("aç"),
            ),
        )
        val INSTALL_VERBS = VerbForms(
            prefixByLocale = mapOf(
                "en" to setOf("install"),
                "ru" to setOf("установи", "установить"),
            ),
            suffixByLocale = mapOf(
                "tr" to setOf("kur"),
            ),
        )
        val SEARCH_VERBS = VerbForms(
            prefixByLocale = mapOf(
                "en" to setOf("search", "find", "google"),
                "ru" to setOf("найди", "найти"),
            ),
            suffixByLocale = mapOf(
                "tr" to setOf("ara"),
            ),
        )
        val SETTINGS_KEYWORDS_BY_LOCALE: Map<String, Set<String>> = mapOf(
            "en" to setOf("settings", "launcher settings"),
            "ru" to setOf("настройки", "настройки лаунчера"),
            "tr" to setOf("ayarlar", "başlatıcı ayarları"),
        )
        private val SETTINGS_KEYWORDS: Set<String> = SETTINGS_KEYWORDS_BY_LOCALE.values.flatten().toSet()

        val SIMPLE_COMMANDS_BY_LOCALE: Map<String, Map<String, SimpleCommand>> = mapOf(
            "en" to mapOf(
                "assistant" to SimpleCommand.OPEN_ASSISTANT,
                "show assistant" to SimpleCommand.OPEN_ASSISTANT,
                "show apps" to SimpleCommand.SHOW_APPS,
                "show all apps" to SimpleCommand.SHOW_APPS,
                "clear" to SimpleCommand.CLEAR,
                "clear input" to SimpleCommand.CLEAR,
                "help" to SimpleCommand.HELP,
            ),
            "ru" to mapOf(
                "ассистент" to SimpleCommand.OPEN_ASSISTANT,
                "показать ассистента" to SimpleCommand.OPEN_ASSISTANT,
                "показать приложения" to SimpleCommand.SHOW_APPS,
                "показать все приложения" to SimpleCommand.SHOW_APPS,
                "очистить" to SimpleCommand.CLEAR,
                "очистить поле" to SimpleCommand.CLEAR,
                "помощь" to SimpleCommand.HELP,
            ),
            "tr" to mapOf(
                "asistan" to SimpleCommand.OPEN_ASSISTANT,
                "asistanı göster" to SimpleCommand.OPEN_ASSISTANT,
                "uygulamaları göster" to SimpleCommand.SHOW_APPS,
                "tüm uygulamaları göster" to SimpleCommand.SHOW_APPS,
                "temizle" to SimpleCommand.CLEAR,
                "girişi temizle" to SimpleCommand.CLEAR,
                "yardım" to SimpleCommand.HELP,
            ),
        )
        private val SIMPLE_COMMANDS: Map<String, SimpleCommand> = SIMPLE_COMMANDS_BY_LOCALE.values
            .flatMap { it.entries }
            .associate { it.key to it.value }
    }
}
