package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * §5.A calibration boundary tests, against the **verified** thresholds
 * (suggest 0.50 / autoExecute 0.85) and the NLU floor 0.60.
 */
class NluConfidenceCalibratorTest {

    private val policy = DefaultIntentConfidencePolicy()
    private val calibrator = NluConfidenceCalibrator(policy)

    @Test fun `floor maps to exactly the suggest threshold`() =
        assertEquals(0.50f, calibrator.calibrate(0.60f), DELTA)

    @Test fun `midpoint raw maps into the middle of the band`() {
        // frac = (0.80 - 0.60) / 0.40 = 0.5 -> 0.50 + 0.5 * 0.35 = 0.675
        assertEquals(0.675f, calibrator.calibrate(0.80f), DELTA)
    }

    @Test fun `top of range stays strictly below the auto-execute threshold`() {
        val top = calibrator.calibrate(1.0f)
        assertEquals(0.84f, top, DELTA) // 0.85 - AUTO_EXECUTE_MARGIN
        assertTrue("must be < autoExecuteThreshold", top < policy.autoExecuteThreshold)
    }

    @Test fun `raw below the floor clamps to the suggest threshold`() {
        assertEquals(0.50f, calibrator.calibrate(0.0f), DELTA)
        assertEquals(0.50f, calibrator.calibrate(0.30f), DELTA)
    }

    @Test fun `every calibrated value lands in the suggest band and never auto-executes`() {
        var raw = 0.0f
        while (raw <= 1.0f) {
            val c = calibrator.calibrate(raw)
            assertTrue("clears suggest: raw=$raw -> $c", policy.shouldSuggest(c))
            assertFalse("never auto-executes: raw=$raw -> $c", policy.shouldAutoExecute(c))
            raw += 0.05f
        }
    }

    @Test fun `rejects inverted thresholds`() {
        try {
            NluConfidenceCalibrator(suggestThreshold = 0.9f, autoExecuteThreshold = 0.5f)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    private companion object {
        const val DELTA = 0.0005f
    }
}
