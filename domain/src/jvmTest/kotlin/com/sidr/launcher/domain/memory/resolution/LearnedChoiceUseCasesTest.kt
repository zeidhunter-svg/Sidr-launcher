package com.sidr.launcher.domain.memory.resolution

import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.model.InstalledApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LearnedChoiceUseCasesTest {
    private val store = FakeResolutionPreferenceStore()
    private val apps = FakeInstalledAppsRepository()
    private val display = EvaluateLearnedChoiceDisplayStateUseCase(autoResolveStreakThreshold = 3)
    private fun app(p: String) = ResolvedTarget.App(p)
    private fun key(q: String) = CapabilityKey(ActionId("launch_app"), q)
    private fun pref(q: String, target: String, streak: Int) = ResolutionPreference(
        key(q), ResolutionContext.None, app(target), PreferenceEvidence(streak, streak, 0L),
        fingerprintOf(CandidateSet(listOf(app(target)))))

    @Before fun setup() { apps.appsToReturn = listOf(InstalledApp("com.a", "MyBank")) }

    @Test fun `observe surfaces installed target with label and Learning state`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertEquals(1, list.size)
        assertEquals("MyBank", list[0].targetLabel)
        assertEquals("com.a", list[0].targetPackageName)
        assertEquals(LearnedChoiceDisplayState.Learning(1, 3), list[0].displayState)
    }

    @Test fun `observe reads AutoReady for a confident installed target`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 5))
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertEquals(LearnedChoiceDisplayState.AutoReady, list[0].displayState)
    }

    @Test fun `observe excludes an uninstalled target`() = runTest {
        store.upsert(pref("news", "com.gone", streak = 5)) // com.gone not in appsToReturn
        val list = ObserveLearnedChoicesUseCase(store, apps, display).observe().first()
        assertTrue(list.isEmpty())
    }

    @Test fun `delete removes exactly one preference`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))
        DeleteLearnedChoiceUseCase(store).delete(key("bank"), ResolutionContext.None)
        assertTrue(store.observeAll().first().isEmpty())
    }

    @Test fun `prune deletes rows whose target is not installed`() = runTest {
        store.upsert(pref("bank", "com.a", streak = 1))     // installed → kept
        store.upsert(pref("news", "com.gone", streak = 5))  // absent → pruned
        PruneUnavailableLearnedChoicesUseCase(store, apps).prune()
        val remaining = store.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(app("com.a"), remaining[0].preferredTarget)
    }
}
