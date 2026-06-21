package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.IntentMatcher
import com.sidr.launcher.domain.intent.LauncherIntent

/**
 * Configurable fake [IntentMatcher] for unit-testing use cases without the real rule engine.
 * Lets a test drive any intent + confidence (including medium/low values the rule matcher
 * never actually emits). Not wired into any Hilt graph — use directly in tests.
 */
class FakeIntentMatcher : IntentMatcher {

    /** When set, [match] returns this verbatim and ignores [intentToReturn] / [confidenceToReturn]. */
    var resultToReturn: IntentMatchResult? = null

    /** Best-candidate intent used when [resultToReturn] is null. */
    var intentToReturn: LauncherIntent =
        LauncherIntent.UnknownIntent(originalInput = "", reason = "fake default")

    /** Best-candidate confidence used when [resultToReturn] is null. */
    var confidenceToReturn: Float = 0.0f

    /** Normalized inputs passed to [match], in call order. */
    val receivedInputs = mutableListOf<String>()

    val callCount: Int get() = receivedInputs.size

    override suspend fun match(normalizedInput: String): IntentMatchResult {
        receivedInputs += normalizedInput
        return resultToReturn ?: IntentMatchResult(
            normalizedInput = normalizedInput,
            best = IntentCandidate(intent = intentToReturn, confidence = confidenceToReturn),
        )
    }

    fun reset() {
        resultToReturn = null
        intentToReturn = LauncherIntent.UnknownIntent(originalInput = "", reason = "fake default")
        confidenceToReturn = 0.0f
        receivedInputs.clear()
    }
}
