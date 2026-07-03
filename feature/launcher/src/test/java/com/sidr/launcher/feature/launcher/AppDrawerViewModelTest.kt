package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppDrawerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakeInstalledAppsRepository()
    private val fakeExecutor = FakeActionExecutor()
    private val fakeUsageRepo = FakeUsageHistoryRepository()
    private val fakeFlagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        fakeRepo.reset()
        fakeExecutor.reset()
        fakeUsageRepo.reset()
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        flagRepo: FeatureFlagRepository = fakeFlagRepo,
    ) = AppDrawerViewModel(
        installedAppsRepository = fakeRepo,
        actionExecutor = fakeExecutor,
        usageHistoryRepository = fakeUsageRepo,
        featureFlagRepository = flagRepo,
        ioDispatcher = testDispatcher,
    )

    @Test
    fun `Loading before advance then Success sections after`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("p.b", "Beta"), InstalledApp("p.a", "Alpha"))
        val vm = buildViewModel()

        assertTrue(vm.uiState.value is UiState.Loading)

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success, got $state", state is UiState.Success)
        // Alphabetical grouping regardless of load order.
        assertEquals(listOf("A", "B"), (state as UiState.Success).data.sections.map { it.letter })
    }

    @Test
    fun `empty apps list emits Empty`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = emptyList()
        val vm = buildViewModel()

        advanceUntilIdle()

        assertTrue(vm.uiState.value is UiState.Empty)
    }

    @Test
    fun `NetworkError maps to retryable Error`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue((state as UiState.Error).retryable)
    }

    @Test
    fun `PermissionDenied maps to non-retryable Error`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.PermissionDenied("QUERY_ALL_PACKAGES")
        val vm = buildViewModel()

        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertFalse((state as UiState.Error).retryable)
    }

    @Test
    fun `retry reloads apps`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()
        assertEquals(1, fakeRepo.callCount)

        fakeRepo.errorToReturn = null
        fakeRepo.appsToReturn = listOf(InstalledApp("p.a", "Alpha"))
        vm.retry()
        advanceUntilIdle()

        assertEquals(2, fakeRepo.callCount)
        assertTrue(vm.uiState.value is UiState.Success)
    }

    @Test
    fun `onAppClicked launches through executor and records usage`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onAppClicked(InstalledApp("com.example.app", "Example", activityName = "Main"))
        advanceUntilIdle()

        assertEquals(1, fakeExecutor.callCount)
        val action = fakeExecutor.executedActions.single()
        assertTrue(action is ExecutableAction.LaunchAppAction)
        assertEquals("com.example.app", (action as ExecutableAction.LaunchAppAction).packageName)
        assertEquals(listOf("com.example.app"), fakeUsageRepo.recordedLaunches)
    }

    @Test
    fun `usage is not recorded when the flag is disabled`() = runTest(testDispatcher) {
        val vm = buildViewModel(flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false)))
        advanceUntilIdle()

        vm.onAppClicked(InstalledApp("com.example.app", "Example"))
        advanceUntilIdle()

        assertEquals(1, fakeExecutor.callCount)
        assertTrue(fakeUsageRepo.recordedLaunches.isEmpty())
    }

    @Test
    fun `usage is not recorded when the launch fails`() = runTest(testDispatcher) {
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure("boom")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onAppClicked(InstalledApp("com.example.app", "Example"))
        advanceUntilIdle()

        assertEquals(1, fakeExecutor.callCount)
        assertTrue(fakeUsageRepo.recordedLaunches.isEmpty())
    }

    @Test
    fun `navigateBack emits NavigateBack`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.navigateBack()

        assertEquals(NavigationEvent.NavigateBack, vm.navigationEvents.first())
    }

    // ── Live filter (Block X4) ──────────────────────────────────────────────

    @Test
    fun `query filters the sections live`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("p.tel", "Telegram"),
            InstalledApp("p.cal", "Calendar"),
            InstalledApp("p.set", "Settings"),
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("cal")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success, got $state", state is UiState.Success)
        val labels = (state as UiState.Success).data.sections.flatMap { it.apps }.map { it.label }
        assertEquals(listOf("Calendar"), labels)
    }

    @Test
    fun `clearing the query restores the full list`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("p.a", "Alpha"), InstalledApp("p.b", "Beta"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("alp")
        advanceUntilIdle()
        assertEquals(listOf("A"), (vm.uiState.value as UiState.Success).data.sections.map { it.letter })

        vm.onQueryChanged("")
        advanceUntilIdle()
        assertEquals(listOf("A", "B"), (vm.uiState.value as UiState.Success).data.sections.map { it.letter })
    }

    @Test
    fun `query matching nothing emits Empty`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("p.a", "Alpha"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("zzz")
        advanceUntilIdle()

        assertTrue(vm.uiState.value is UiState.Empty)
    }

    @Test
    fun `onQuerySubmitted launches the top filtered match`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("p.calc", "Calculator"),
            InstalledApp("p.cal", "Calendar"),
            InstalledApp("p.maps", "Maps"),
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("cal")
        vm.onQuerySubmitted()
        advanceUntilIdle()

        assertEquals(1, fakeExecutor.callCount)
        val action = fakeExecutor.executedActions.single()
        assertTrue(action is ExecutableAction.LaunchAppAction)
        // Top match = first alphabetical section entry ("Calculator" < "Calendar").
        assertEquals("p.calc", (action as ExecutableAction.LaunchAppAction).packageName)
        assertEquals(listOf("p.calc"), fakeUsageRepo.recordedLaunches)
    }

    @Test
    fun `onQuerySubmitted is a no-op when nothing matches`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("p.a", "Alpha"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onQueryChanged("zzz")
        vm.onQuerySubmitted()
        advanceUntilIdle()

        assertEquals(0, fakeExecutor.callCount)
    }
}
