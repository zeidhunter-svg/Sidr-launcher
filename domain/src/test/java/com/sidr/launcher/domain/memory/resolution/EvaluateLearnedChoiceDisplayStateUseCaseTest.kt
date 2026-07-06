package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionRiskLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class EvaluateLearnedChoiceDisplayStateUseCaseTest {
    private val useCase = EvaluateLearnedChoiceDisplayStateUseCase(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private fun set(vararg p: String) = CandidateSet(p.map { app(it) })
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun pref(streak: Int, learned: CandidateSet, target: String = "com.a") = ResolutionPreference(
        key, ResolutionContext.None, app(target), PreferenceEvidence(streak, streak, 0L), fingerprintOf(learned))

    @Test fun `not installed is Unavailable`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Unavailable,
            useCase.evaluate(pref(9, s), s, ActionRiskLevel.SAFE, targetInstalled = false))
    }

    @Test fun `below threshold is Learning`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Learning(2, 3),
            useCase.evaluate(pref(2, s), s, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident but fingerprint changed is NeedsReconfirm`() {
        val learned = set("com.a", "com.b"); val current = set("com.a", "com.c")
        assertEquals(LearnedChoiceDisplayState.NeedsReconfirm,
            useCase.evaluate(pref(9, learned), current, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident + safe + same set + installed is Auto`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.Auto,
            useCase.evaluate(pref(9, s), s, ActionRiskLevel.SAFE, targetInstalled = true))
    }

    @Test fun `confident + safe + installed but set unknown is AutoReady`() {
        val s = set("com.a", "com.b")
        assertEquals(LearnedChoiceDisplayState.AutoReady,
            useCase.evaluate(pref(9, s), currentCandidates = null, ActionRiskLevel.SAFE, targetInstalled = true))
    }
}
