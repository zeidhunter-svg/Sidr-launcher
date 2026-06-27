package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource

/**
 * No-op [IntentMatcher] that always returns the lowest-confidence [IntentMatchResult].
 *
 * Models the gate-off NLU secondary so [LayeredIntentMatcher] (Block R) collapses to the rule
 * result without consulting an ONNX session. Useful in unit tests and as the production binding
 * on LOW_END / missing-model devices (Block R DI wiring).
 *
 * Deliberately uses [MatcherSource.RULE_BASED] (the default) rather than [MatcherSource.NLU]:
 * a no-op result that "wins" by NLU source would misreport the pipeline's decision.
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
