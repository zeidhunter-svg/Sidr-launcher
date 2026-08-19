package com.sidr.launcher.domain.intent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntentConfidencePolicyTest {

    private val policy = DefaultIntentConfidencePolicy()

    // --- default thresholds ---

    @Test fun `default autoExecuteThreshold is 0_85`() =
        assertEquals(0.85f, policy.autoExecuteThreshold)

    @Test fun `default suggestThreshold is 0_50`() =
        assertEquals(0.50f, policy.suggestThreshold)

    // --- shouldAutoExecute ---

    @Test fun `shouldAutoExecute true at exactly 0_85`() =
        assertTrue(policy.shouldAutoExecute(0.85f))

    @Test fun `shouldAutoExecute true above threshold`() =
        assertTrue(policy.shouldAutoExecute(0.90f))

    @Test fun `shouldAutoExecute true at 1_0`() =
        assertTrue(policy.shouldAutoExecute(1.0f))

    @Test fun `shouldAutoExecute false just below threshold`() =
        assertFalse(policy.shouldAutoExecute(0.84f))

    @Test fun `shouldAutoExecute false at zero`() =
        assertFalse(policy.shouldAutoExecute(0.0f))

    // --- shouldSuggest ---

    @Test fun `shouldSuggest true at exactly 0_50`() =
        assertTrue(policy.shouldSuggest(0.50f))

    @Test fun `shouldSuggest true in medium range`() =
        assertTrue(policy.shouldSuggest(0.70f))

    @Test fun `shouldSuggest false just below threshold`() =
        assertFalse(policy.shouldSuggest(0.49f))

    // --- isLowConfidence ---

    @Test fun `isLowConfidence true below suggest threshold`() =
        assertTrue(policy.isLowConfidence(0.49f))

    @Test fun `isLowConfidence true at zero`() =
        assertTrue(policy.isLowConfidence(0.0f))

    @Test fun `isLowConfidence false at suggest threshold`() =
        assertFalse(policy.isLowConfidence(0.50f))

    @Test fun `isLowConfidence false above threshold`() =
        assertFalse(policy.isLowConfidence(0.90f))

    // --- custom thresholds override defaults ---

    @Test fun `custom thresholds work independently`() {
        val custom = DefaultIntentConfidencePolicy(
            autoExecuteThreshold = 0.95f,
            suggestThreshold = 0.60f,
        )
        assertTrue(custom.shouldAutoExecute(0.95f))
        assertFalse(custom.shouldAutoExecute(0.94f))
        assertTrue(custom.shouldSuggest(0.60f))
        assertFalse(custom.shouldSuggest(0.59f))
        assertTrue(custom.isLowConfidence(0.59f))
        assertFalse(custom.isLowConfidence(0.60f))
    }
}
