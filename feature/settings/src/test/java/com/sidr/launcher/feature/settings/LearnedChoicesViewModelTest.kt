package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.EvaluateLearnedChoiceDisplayStateUseCase
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceDisplayState
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceView
import com.sidr.launcher.domain.memory.resolution.ObserveLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.PruneUnavailableLearnedChoicesUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearnedChoicesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `observe error surfaces retryable safe error without crashing`() = runTest(testDispatcher) {
        val vm = buildViewModel(store = ThrowingObserveStore())

        advanceUntilIdle()

        assertTrue(vm.uiState.value.choices.isEmpty())
        assertEquals("couldn't load learned choices · retry", vm.uiState.value.errorMessage)
        assertTrue(vm.uiState.value.canRetry)
    }

    @Test
    fun `delete removes learned choice through use case`() = runTest(testDispatcher) {
        val store = FakeResolutionPreferenceStore()
        val apps = installedApps("com.a" to "MyBank")
        store.upsert(pref(query = "bank", target = "com.a", streak = 1))
        val vm = buildViewModel(store = store, apps = apps)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.choices.size)
        vm.onDelete(vm.uiState.value.choices.single().stableId)
        advanceUntilIdle()

        assertTrue(store.observeAll().first().isEmpty())
        assertTrue(vm.uiState.value.choices.isEmpty())
    }

    @Test
    fun `load prunes unavailable learned choices`() = runTest(testDispatcher) {
        val store = FakeResolutionPreferenceStore()
        store.upsert(pref(query = "bank", target = "com.a", streak = 1))
        store.upsert(pref(query = "music", target = "com.gone", streak = 3))
        val vm = buildViewModel(store = store, apps = installedApps("com.a" to "MyBank"))

        advanceUntilIdle()

        val remaining = store.observeAll().first()
        assertEquals(1, remaining.size)
        assertEquals(ResolvedTarget.App("com.a"), remaining[0].preferredTarget)
        assertEquals(1, vm.uiState.value.choices.size)
    }

    @Test
    fun `bad rows are skipped while valid rows still render`() = runTest(testDispatcher) {
        val store = FakeResolutionPreferenceStore()
        store.upsert(pref(query = "", target = "com.a", streak = 1))
        store.upsert(pref(query = "bank", target = "com.a", streak = 1))
        val vm = buildViewModel(store = store, apps = installedApps("com.a" to "MyBank"))

        advanceUntilIdle()

        val choices = vm.uiState.value.choices
        assertEquals(1, choices.size)
        assertEquals("bank", choices[0].phrase)
        assertEquals(SidrMemoryStatus.Learning, choices[0].status)
        assertEquals("Learning from confirmed choices (1/3)", choices[0].evidence)
    }

    @Test
    fun `maps learned-choice display states to DS-7 memory status and evidence`() {
        val learning = view(LearnedChoiceDisplayState.Learning(1, 3)).toMemoryUiModel()
        val active = view(LearnedChoiceDisplayState.Auto).toMemoryUiModel()
        val ready = view(LearnedChoiceDisplayState.AutoReady).toMemoryUiModel()
        val reconfirm = view(LearnedChoiceDisplayState.NeedsReconfirm).toMemoryUiModel()
        val unavailable = view(LearnedChoiceDisplayState.Unavailable).toMemoryUiModel()

        assertEquals(SidrMemoryStatus.Learning, learning.status)
        assertEquals("Learning from confirmed choices (1/3)", learning.evidence)
        assertEquals(SidrMemoryStatus.Active, active.status)
        assertEquals("Based on confirmed choices", active.evidence)
        assertEquals(SidrMemoryStatus.Active, ready.status)
        assertEquals(SidrMemoryStatus.NeedsReconfirmation, reconfirm.status)
        assertEquals("Needs reconfirmation before auto-open", reconfirm.evidence)
        assertEquals(SidrMemoryStatus.Unavailable, unavailable.status)
        assertEquals("Target unavailable", unavailable.evidence)
    }

    private fun buildViewModel(
        store: ResolutionPreferenceStore = FakeResolutionPreferenceStore(),
        apps: FakeInstalledAppsRepository = installedApps("com.a" to "MyBank"),
    ): LearnedChoicesViewModel = LearnedChoicesViewModel(
        observeLearnedChoices = ObserveLearnedChoicesUseCase(
            store = store,
            installedApps = apps,
            displayState = EvaluateLearnedChoiceDisplayStateUseCase(autoResolveStreakThreshold = 3),
        ),
        deleteLearnedChoice = DeleteLearnedChoiceUseCase(store),
        pruneUnavailableLearnedChoices = PruneUnavailableLearnedChoicesUseCase(
            store = store,
            installedApps = apps,
        ),
        ioDispatcher = testDispatcher,
    )

    private fun installedApps(vararg apps: Pair<String, String>): FakeInstalledAppsRepository =
        FakeInstalledAppsRepository().apply {
            appsToReturn = apps.map { (packageName, label) -> InstalledApp(packageName, label) }
        }

    private fun key(query: String) = CapabilityKey(ActionId("launch_app"), query)

    private fun pref(query: String, target: String, streak: Int) = ResolutionPreference(
        capabilityKey = key(query),
        context = ResolutionContext.None,
        preferredTarget = ResolvedTarget.App(target),
        evidence = PreferenceEvidence(
            streak = streak,
            totalChoices = streak,
            lastChosenAtEpochMs = 0L,
        ),
        learnedInSetFingerprint = fingerprintOf(CandidateSet(listOf(ResolvedTarget.App(target)))),
    )

    private fun view(displayState: LearnedChoiceDisplayState) = LearnedChoiceView(
        capabilityKey = key("bank"),
        targetPackageName = "com.a",
        targetLabel = "MyBank",
        displayState = displayState,
    )

    private class ThrowingObserveStore : ResolutionPreferenceStore {
        override suspend fun find(
            key: CapabilityKey,
            context: ResolutionContext,
        ): OperationResult<ResolutionPreference?> = OperationResult.Success(null)

        override suspend fun upsert(preference: ResolutionPreference): OperationResult<Unit> =
            OperationResult.Success(Unit)

        override suspend fun delete(
            key: CapabilityKey,
            context: ResolutionContext,
        ): OperationResult<Unit> = OperationResult.Success(Unit)

        override fun observeAll(): Flow<List<ResolutionPreference>> = flow {
            throw IllegalStateException("dao down")
        }
    }
}
