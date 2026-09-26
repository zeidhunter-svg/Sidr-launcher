package com.sidr.launcher.domain.action

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsentPolicyTest {

    @Test
    fun `SAFE runs without consent and everything above it stops`() {
        assertEquals(false, requiresConsent(ActionRiskLevel.SAFE))
        assertEquals(true, requiresConsent(ActionRiskLevel.CONFIRM))
        assertEquals(true, requiresConsent(ActionRiskLevel.DANGEROUS))
    }

    /**
     * The wake-up test, and the reason `DOC-ADL-1` is a latent defect rather than a missing
     * abstraction. Before this block four sites decided gating in two spellings — `>= CONFIRM` in the
     * agent, `!= SAFE` in the model, memory and UI paths. With three levels the two coincide, so the
     * defect is invisible today and arrives the day a level is inserted below CONFIRM (`D11`).
     *
     * Written against the enum's own ordering so it needs no new level to be meaningful: every level
     * that is not the lowest must require consent, whatever the enum grows to.
     *
     * Honest limit: with exactly three levels declared today, `requiresConsent` and the old spelling
     * `risk >= ActionRiskLevel.CONFIRM` agree on every input this test exercises, so this test alone
     * cannot tell the two apart — it only starts to discriminate them the day a level is inserted below
     * `CONFIRM`. What actually closes `DOC-ADL-1` today is that there is now exactly one call site for
     * the predicate, not that this test proves it.
     */
    @Test
    fun `every level above the lowest requires consent, whatever levels exist`() {
        val levels = ActionRiskLevel.entries
        val lowest = levels.first()

        assertEquals(false, requiresConsent(lowest))
        levels.drop(1).forEach { level ->
            assertEquals("$level must require consent", true, requiresConsent(level))
        }
    }
}
