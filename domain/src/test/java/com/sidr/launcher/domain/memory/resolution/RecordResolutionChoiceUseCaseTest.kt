package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordResolutionChoiceUseCaseTest {
    private val store = FakeResolutionPreferenceStore()
    private val useCase = RecordResolutionChoiceUseCase(store)
    private val key = CapabilityKey(ActionId("launch_app"), "bank")
    private fun app(p: String) = ResolvedTarget.App(p)
    private val candidates = CandidateSet(listOf(app("com.a"), app("com.b")))

    private suspend fun current() = (store.find(key, ResolutionContext.None) as
        com.sidr.launcher.domain.result.OperationResult.Success).value

    @Test fun `first choice creates streak 1`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        val p = current()!!
        assertEquals(app("com.a"), p.preferredTarget); assertEquals(1, p.evidence.streak)
        assertEquals(1, p.evidence.totalChoices)
    }

    @Test fun `same choice reinforces (streak++ , fingerprint updated)`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        val newer = CandidateSet(listOf(app("com.a"), app("com.b"), app("com.c")))
        useCase.record(key, ResolutionContext.None, app("com.a"), newer)
        val p = current()!!
        assertEquals(2, p.evidence.streak); assertEquals(2, p.evidence.totalChoices)
        assertEquals(fingerprintOf(newer), p.learnedInSetFingerprint)
    }

    @Test fun `different choice hard-switches (preferred=new, streak=1, total++)`() = runTest {
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        useCase.record(key, ResolutionContext.None, app("com.a"), candidates) // streak 2
        useCase.record(key, ResolutionContext.None, app("com.b"), candidates) // correction
        val p = current()!!
        assertEquals(app("com.b"), p.preferredTarget); assertEquals(1, p.evidence.streak)
        assertEquals(3, p.evidence.totalChoices)
    }

    @Test fun `empty query is never recorded`() = runTest {
        useCase.record(CapabilityKey(ActionId("launch_app"), "  "), ResolutionContext.None, app("com.a"), candidates)
        assertNull((store.find(CapabilityKey(ActionId("launch_app"), ""), ResolutionContext.None) as
            com.sidr.launcher.domain.result.OperationResult.Success).value)
    }

    @Test fun `store write failure returns Failure without throwing`() = runTest {
        store.failWrites = true
        val r = useCase.record(key, ResolutionContext.None, app("com.a"), candidates)
        assertTrue(r is com.sidr.launcher.domain.result.OperationResult.Failure)
    }
}
