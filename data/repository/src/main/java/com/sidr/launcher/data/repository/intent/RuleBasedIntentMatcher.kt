package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand

/**
 * Rule-based implementation of [IntentMatcher]. Android-free — no Context or PackageManager.
 * Expects already-normalized input (lowercase, trimmed, spaces collapsed via CommandNormalizer).
 *
 * Rule priority:
 *  1. Launch verb prefix (open/launch/start) — always produces LaunchAppIntent, even for
 *     keywords like "settings". Documented choice: verb overrides keyword; "open settings"
 *     resolves as a launch attempt, not OpenSettingsIntent. Thresholds to refine in Block D.
 *  2. Search verb prefix (search/find/google).
 *  3. Settings bare keywords (no verb).
 *  4. Simple command table.
 *  5. Fallback → UnknownIntent.
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
            return if (appQuery.isNotEmpty()) {
                Classification(
                    LauncherIntent.LaunchAppIntent(displayNameQuery = appQuery),
                    0.90f,
                    "launch verb '$launchVerb' → query '$appQuery'",
                )
            } else {
                Classification(
                    LauncherIntent.UnknownIntent(originalInput = input, reason = "launch verb without app name"),
                    0.30f,
                    "launch verb '$launchVerb' with no query",
                )
            }
        }
        if (input in LAUNCH_VERBS) {
            return Classification(
                LauncherIntent.UnknownIntent(originalInput = input, reason = "bare launch verb"),
                0.30f,
                "bare launch verb '$input'",
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

        // 5. Fallback
        return Classification(
            LauncherIntent.UnknownIntent(originalInput = input, reason = "no rule matched"),
            0.10f,
            "no rule matched for '$input'",
        )
    }

    private companion object {
        val LAUNCH_VERBS = setOf("open", "launch", "start")
        val SEARCH_VERBS = setOf("search", "find", "google")
        val SETTINGS_KEYWORDS = setOf("settings", "launcher settings")
        val SIMPLE_COMMANDS = mapOf(
            "show apps" to SimpleCommand.SHOW_APPS,
            "show all apps" to SimpleCommand.SHOW_APPS,
            "clear" to SimpleCommand.CLEAR,
            "clear input" to SimpleCommand.CLEAR,
            "help" to SimpleCommand.HELP,
        )
    }
}
