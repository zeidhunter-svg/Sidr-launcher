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
 *  1. Launch verb prefix (open/launch/start) — produces LaunchAppIntent, even for keywords like
 *     "settings". Exception (AIL-2, Q2): when the verb argument is a high-confidence URL,
 *     "open <url>" opens the site instead of launching an app.
 *  1b. Install verb prefix (install) — Play Store search (AIL-2, Q3).
 *  2. Search verb prefix (search/find/google).
 *  3. Settings bare keywords (no verb).
 *  4. Simple command table.
 *  5. Bare URL / site (AIL-2, R6): high-confidence URL → OpenUrlIntent; domain-shaped but
 *     ambiguous → web SearchIntent; otherwise fall through. Never opens a guessed/malformed URL.
 *  6. Fallback → UnknownIntent.
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

    private fun classify(input: String): Classification {
        if (input.isEmpty()) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "empty input"),
                0.0f,
                "empty input",
            )
        }

        // 1. Launch verb: open/launch/start <app>
        val launchVerb = LAUNCH_VERBS.firstOrNull { input.startsWith("$it ") }
        if (launchVerb != null) {
            val appQuery = input.removePrefix("$launchVerb ").trim()
            if (appQuery.isEmpty()) {
                return Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "launch verb without app name"),
                    0.30f,
                    "launch verb '$launchVerb' with no query",
                )
            }
            // Q2: only a high-confidence URL diverts "open <x>" to the browser; anything else
            // (a plain name, or a domain-shaped ambiguous token) stays an app launch as before.
            val urlHit = UrlDetector.classify(appQuery)
            if (urlHit is UrlClassification.Url) {
                return Classification(
                    LauncherIntent.OpenUrlIntent(url = urlHit.url),
                    0.90f,
                    "launch verb '$launchVerb' → url '${urlHit.url}'",
                )
            }
            return Classification(
                LauncherIntent.LaunchAppIntent(displayNameQuery = appQuery),
                0.90f,
                "launch verb '$launchVerb' → query '$appQuery'",
            )
        }
        if (input in LAUNCH_VERBS) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "bare launch verb"),
                0.30f,
                "bare launch verb '$input'",
            )
        }

        // 1b. Install verb: install <app> → Play Store search (Q3 — "install" only).
        val installVerb = INSTALL_VERBS.firstOrNull { input.startsWith("$it ") }
        if (installVerb != null) {
            val appName = input.removePrefix("$installVerb ").trim()
            return if (appName.isNotEmpty()) {
                Classification(
                    LauncherIntent.PlayStoreSearchIntent(query = appName),
                    0.90f,
                    "install verb '$installVerb' → play-store query '$appName'",
                )
            } else {
                Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "install verb without app name"),
                    0.30f,
                    "install verb '$installVerb' with no query",
                )
            }
        }
        if (input in INSTALL_VERBS) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "bare install verb"),
                0.30f,
                "bare install verb '$input'",
            )
        }

        // 2. Search verb: search/find/google <query>
        val searchVerb = SEARCH_VERBS.firstOrNull { input.startsWith("$it ") }
        if (searchVerb != null) {
            val query = input.removePrefix("$searchVerb ").trim()
            return if (query.isNotEmpty()) {
                Classification(
                    LauncherIntent.SearchIntent(query = query, target = SearchTarget.WEB),
                    0.90f,
                    "search verb '$searchVerb' → query '$query'",
                )
            } else {
                Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "search verb without query"),
                    0.30f,
                    "search verb '$searchVerb' with no query",
                )
            }
        }

        // 3. Settings bare keywords (verb-free)
        if (input in SETTINGS_KEYWORDS) {
            return Classification(
                LauncherIntent.OpenSettingsIntent(),
                0.95f,
                "settings keyword '$input'",
            )
        }

        // 4. Simple command table
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

    private companion object {
        val LAUNCH_VERBS = setOf("open", "launch", "start")
        val INSTALL_VERBS = setOf("install")
        val SEARCH_VERBS = setOf("search", "find", "google")
        val SETTINGS_KEYWORDS = setOf("settings", "launcher settings")
        val SIMPLE_COMMANDS = mapOf(
            "assistant" to SimpleCommand.OPEN_ASSISTANT,
            "show assistant" to SimpleCommand.OPEN_ASSISTANT,
            "show apps" to SimpleCommand.SHOW_APPS,
            "show all apps" to SimpleCommand.SHOW_APPS,
            "clear" to SimpleCommand.CLEAR,
            "clear input" to SimpleCommand.CLEAR,
            "help" to SimpleCommand.HELP,
        )
    }
}
