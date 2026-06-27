package com.sidr.launcher.domain.ai.local

import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeModelAvailabilityRepository
import com.sidr.launcher.core.testing.NoOpIntentMatcher
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.IntentCandidate
import com.sidr.launcher.domain.intent.IntentMatchResult
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.MatcherSource
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Block-O contract properties that aren't covered by [LocalInferenceGateTest]:
 *
 *  1. [NoOpIntentMatcher] always returns a result that [DefaultIntentConfidencePolicy.isLowConfidence]
 *     considers low — it must never "win" over a real rule result.
 *  2. A scripted [FakeIntentMatcher] can produce [MatcherSource.NLU], proving the NLU source
 *     rides the existing [IntentMatcher] port with no new contract.
 *  3. [FakeModelAvailabilityRepository] round-trips [markAvailable] → [availability] emits
 *     [ModelAvailability.Available], confirming fake behaviour for gate-flip tests in Block R.
 */
class LocalNluContractsTest {

    private val policy = DefaultIntentConfidencePolicy()
    private val modelId = ModelId("nlu-intent-v1")

    // ─── NoOpIntentMatcher ────────────────────────────────────────────────────────────────────────

    @Test fun `NoOpIntentMatcher result is always low confidence`() = runTest {
        val noop = NoOpIntentMatcher()
        val result = noop.match("open telegram")
        assertTrue(
            "Expected isLowConfidence=true for confidence ${result.best.confidence}",
            policy.isLowConfidence(result.best.confidence),
        )
    }

    @Test fun `NoOpIntentMatcher confidence is zero`() = runTest {
        val noop = NoOpIntentMatcher()
        assertEquals(0f, noop.match("anything").best.confidence)
    }

    @Test fun `NoOpIntentMatcher does not auto-execute or suggest`() = runTest {
        val noop = NoOpIntentMatcher()
        val confidence = noop.match("open settings").best.confidence
        assertFalse(policy.shouldAutoExecute(confidence))
        assertFalse(policy.shouldSuggest(confidence))
    }

    // ─── FakeIntentMatcher with NLU source ───────────────────────────────────────────────────────

    @Test fun `FakeIntentMatcher can return source NLU — proves NLU rides the existing port`() = runTest {
        val fake = FakeIntentMatcher()
        fake.resultToReturn = IntentMatchResult(
            normalizedInput = "fire up the camera",
            best = IntentCandidate(
                intent = LauncherIntent.LaunchAppIntent(displayNameQuery = "camera"),
                confidence = 0.91f,
            ),
            source = MatcherSource.NLU,
        )

        val result = fake.match("fire up the camera")
        assertEquals(MatcherSource.NLU, result.source)
        assertEquals(0.91f, result.best.confidence)
        assertTrue(policy.shouldAutoExecute(result.best.confidence))
    }

    // ─── FakeModelAvailabilityRepository ─────────────────────────────────────────────────────────

    @Test fun `FakeModelAvailabilityRepository markAvailable flips availability to Available`() = runTest {
        val repo = FakeModelAvailabilityRepository()
        assertEquals(ModelAvailability.Unverified, repo.availability(modelId).first())

        val result = repo.markAvailable(modelId)
        assertTrue(result is OperationResult.Success)
        assertEquals(ModelAvailability.Available, repo.availability(modelId).first())
        assertTrue(repo.markAvailableCalls.contains(modelId))
    }

    @Test fun `FakeModelAvailabilityRepository markMissing flips availability to Missing`() = runTest {
        val repo = FakeModelAvailabilityRepository()
        repo.markAvailable(modelId)

        val result = repo.markMissing(modelId)
        assertTrue(result is OperationResult.Success)
        assertEquals(ModelAvailability.Missing, repo.availability(modelId).first())
    }

    @Test fun `FakeModelAvailabilityRepository returns Failure when errorToReturn is set`() = runTest {
        val repo = FakeModelAvailabilityRepository()
        repo.errorToReturn = com.sidr.launcher.domain.result.OperationError.UnknownError("write failed")

        val result = repo.markAvailable(modelId)
        assertTrue(result is OperationResult.Failure)
        // availability should remain Unverified since write failed
        assertEquals(ModelAvailability.Unverified, repo.availability(modelId).first())
    }

    @Test fun `FakeModelAvailabilityRepository tracks separate state per ModelId`() = runTest {
        val repo = FakeModelAvailabilityRepository()
        val idA = ModelId("model-a")
        val idB = ModelId("model-b")

        repo.markAvailable(idA)
        assertEquals(ModelAvailability.Available, repo.availability(idA).first())
        assertEquals(ModelAvailability.Unverified, repo.availability(idB).first())
    }
}
