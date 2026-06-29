package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayeredIntentMatcherTest {

    private val policy = DefaultIntentConfidencePolicy()

    private fun layered(primary: FakeIntentMatcher, secondary: FakeIntentMatcher) =
        LayeredIntentMatcher(primary = primary, secondary = secondary, policy = policy)

    private fun ruleResult(input: String, intent: LauncherIntent, confidence: Float) =
        IntentMatchResult(
            normalizedInput = input,
            best = IntentCandidate(intent = intent, confidence = confidence),
            source = MatcherSource.RULE_BASED,
        )

    /** The classifier's "no answer" escape: source NLU, confidence 0, UnknownIntent. */
    private fun nluEscape(input: String) = IntentMatchResult(
        normalizedInput = input,
        best = IntentCandidate(
            intent = LauncherIntent.UnknownIntent(originalInput = input, reason = "gate_off"),
            confidence = 0f,
        ),
        source = MatcherSource.NLU,
    )

    private fun nluAnswer(input: String, intent: LauncherIntent, rawConfidence: Float) =
        IntentMatchResult(
            normalizedInput = input,
            best = IntentCandidate(intent = intent, confidence = rawConfidence),
            source = MatcherSource.NLU,
        )

    // --- R4: fast path (the headline) ---

    @Test fun `confident rule returns verbatim and NLU is never consulted`() = runTest {
        val rule = FakeIntentMatcher().apply {
            resultToReturn = ruleResult("open telegram", LauncherIntent.LaunchAppIntent("telegram"), 0.90f)
        }
        val nlu = FakeIntentMatcher()

        val result = layered(rule, nlu).match("open telegram")

        assertEquals(rule.resultToReturn, result)
        assertEquals("NLU must not be consulted on a confident rule", 0, nlu.callCount)
    }

    @Test fun `rule exactly at suggest threshold is not low-confidence so NLU is skipped`() = runTest {
        // isLowConfidence is `< suggestThreshold`, so 0.50 is NOT low → fast path.
        val rule = FakeIntentMatcher().apply {
            resultToReturn = ruleResult("x", LauncherIntent.UnknownIntent("x"), 0.50f)
        }
        val nlu = FakeIntentMatcher()

        val result = layered(rule, nlu).match("x")

        assertEquals(0.50f, result.best.confidence)
        assertEquals(0, nlu.callCount)
    }

    // --- R4: layering escape -> rule ---

    @Test fun `low rule plus escaping NLU yields the rule result`() = runTest {
        val ruleAnswer = ruleResult("fire up cam", LauncherIntent.UnknownIntent("fire up cam"), 0.10f)
        val rule = FakeIntentMatcher().apply { resultToReturn = ruleAnswer }
        val nlu = FakeIntentMatcher().apply { resultToReturn = nluEscape("fire up cam") }

        val result = layered(rule, nlu).match("fire up cam")

        assertEquals(ruleAnswer, result)
        assertEquals(MatcherSource.RULE_BASED, result.source)
        assertEquals(1, nlu.callCount)
    }

    // --- R4: layering answer (calibrated) ---

    @Test fun `low rule plus confident NLU wins with calibrated suggest-band confidence`() = runTest {
        val rule = FakeIntentMatcher().apply {
            resultToReturn = ruleResult("fire up the camera", LauncherIntent.UnknownIntent("fire up the camera"), 0.10f)
        }
        val nlu = FakeIntentMatcher().apply {
            resultToReturn = nluAnswer("fire up the camera", LauncherIntent.LaunchAppIntent("the camera"), 0.95f)
        }

        val result = layered(rule, nlu).match("fire up the camera")

        assertEquals(MatcherSource.NLU, result.source)
        assertTrue("NLU intent surfaces", result.best.intent is LauncherIntent.LaunchAppIntent)
        // raw 0.95 calibrated into [0.50, 0.85): Suggests, never auto-executes.
        assertTrue(policy.shouldSuggest(result.best.confidence))
        assertFalse(policy.shouldAutoExecute(result.best.confidence))
    }

    @Test fun `NLU at the confidence floor still wins and maps to the suggest threshold`() = runTest {
        val rule = FakeIntentMatcher().apply {
            resultToReturn = ruleResult("q", LauncherIntent.UnknownIntent("q"), 0.10f)
        }
        val nlu = FakeIntentMatcher().apply {
            resultToReturn = nluAnswer("q", LauncherIntent.SimpleCommandIntent(
                com.sidr.launcher.domain.intent.SimpleCommand.HELP), 0.60f)
        }

        val result = layered(rule, nlu).match("q")

        assertEquals(MatcherSource.NLU, result.source)
        assertEquals(0.50f, result.best.confidence, 0.0005f)
    }

    // --- R4: no-model parity (the ship-safe guarantee) ---

    @Test fun `with an escaping NLU the layer reproduces rule-only outcomes across the Phase-3 set`() = runTest {
        val ruleMatcher = RuleBasedIntentMatcher()
        // primary = real rule matcher; secondary = always-escaping (simulates no model present).
        val escapingNlu = object : com.sidr.launcher.domain.intent.IntentMatcher {
            override suspend fun match(normalizedInput: String) = nluEscape(normalizedInput)
        }
        val layered = LayeredIntentMatcher(primary = ruleMatcher, secondary = escapingNlu, policy = policy)

        val commands = listOf(
            "open telegram", "launch whatsapp", "search cats", "google kotlin",
            "settings", "show apps", "help", "clear", "open", "", "zzz nonsense",
        )
        for (cmd in commands) {
            val expected = ruleMatcher.match(cmd)
            val actual = layered.match(cmd)
            assertEquals("parity for '$cmd'", expected, actual)
        }
    }
}
