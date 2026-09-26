package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent

/**
 * No-op [IntentMatcher] that always returns the lowest-confidence [IntentMatchResult]. Useful as a
 * scripted fallback in unit tests that need an [IntentMatcher] but don't exercise matching itself.
 */
class NoOpIntentMatcher : IntentMatcher {
    override suspend fun match(normalizedInput: String): IntentMatchResult = IntentMatchResult(
        normalizedInput = normalizedInput,
        best = IntentCandidate(
            intent = LauncherIntent.UnknownIntent(originalInput = normalizedInput, reason = "noop"),
            confidence = 0f,
        ),
    )
}
