package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultResolutionPreferencePolicyTest {
    private val policy = DefaultResolutionPreferencePolicy(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun set(vararg p: String) = CandidateSet(p.map { app(it) })
    private fun pref(target: String, streak: Int, learnedSet: CandidateSet) = ResolutionPreference(
        capabilityKey = key, context = ResolutionContext.None, preferredTarget = app(target),
        evidence = PreferenceEvidence(streak, streak, 0L), learnedInSetFingerprint = fingerprintOf(learnedSet),
    )

    @Test fun `null preference is NoPreference`() {
        assertEquals(ResolutionDecision.NoPreference,
            policy.decide(null, set("com.a", "com.b"), ActionRiskLevel.SAFE))
    }

    @Test fun `target not in candidates is Stale`() {
        val p = pref("com.gone", streak = 5, learnedSet = set("com.gone", "com.b"))
        val d = policy.decide(p, set("com.a", "com.b"), ActionRiskLevel.SAFE)
        assertTrue(d is ResolutionDecision.Stale)
    }

    @Test fun `confident + SAFE + same fingerprint is AutoResolve`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", streak = 3, learnedSet = s), s, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.AutoResolve(app("com.a")), d)
    }

    @Test fun `below threshold is RankFirst`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", streak = 2, learnedSet = s), s, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }

    @Test fun `exactly K is confident (boundary)`() {
        val s = set("com.a", "com.b")
        assertTrue(policy.decide(pref("com.a", 3, s), s, ActionRiskLevel.SAFE) is ResolutionDecision.AutoResolve)
        assertTrue(policy.decide(pref("com.a", 2, s), s, ActionRiskLevel.SAFE) is ResolutionDecision.RankFirst)
    }

    @Test fun `confident but CONFIRM risk is RankFirst never AutoResolve`() {
        val s = set("com.a", "com.b")
        val d = policy.decide(pref("com.a", 9, s), s, ActionRiskLevel.CONFIRM)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }

    @Test fun `confident + SAFE set but DANGEROUS risk is RankFirst never AutoResolve`() {
        val s = set("com.a", "com.b")   // confident streak + same fingerprint: only risk blocks auto
        val d = policy.decide(pref("com.a", 9, s), s, ActionRiskLevel.DANGEROUS)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }

    @Test fun `confident + SAFE but fingerprint changed is RankFirst`() {
        val learned = set("com.a", "com.b")
        val current = set("com.a", "com.c")   // set changed → demote, don't erase
        val d = policy.decide(pref("com.a", 9, learned), current, ActionRiskLevel.SAFE)
        assertEquals(ResolutionDecision.RankFirst(app("com.a")), d)
    }
}
