package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
class LauncherViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val fakeRepo = FakeInstalledAppsRepository()
    private val fakeMatcher = FakeIntentMatcher()
    private val fakeExecutor = FakeActionExecutor()
    private val fakeUsageRepo = FakeUsageHistoryRepository()
    // Default: usageHistoryEnabled = true so existing recording tests remain valid.
    private val fakeFlagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))
    private val useCase = HandleUserCommandUseCase(
        matcher = fakeMatcher,
        resolver = IntentActionResolver(fakeRepo),
        executor = fakeExecutor,
        confidencePolicy = DefaultIntentConfidencePolicy(),
        // Share the test scheduler: the use case's fire-and-forget intent-match recording (null
        // history here → no-op) is flushed by the same advanceUntilIdle() the VM tests already use,
        // rather than relying on Dispatchers.Unconfined to run in-place (Block H, step H-c).
        recordingScope = CoroutineScope(testDispatcher + SupervisorJob()),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // Reset StateFlow-backed fakes BEFORE resetMain — mutating a MutableStateFlow
        // tries to dispatch to Main, which must still be the test dispatcher at that point.
        fakeRepo.reset()
        fakeMatcher.reset()
        fakeExecutor.reset()
        fakeUsageRepo.reset()
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        flagRepo: FeatureFlagRepository = fakeFlagRepo,
    ) = LauncherViewModel(
        installedAppsRepository = fakeRepo,
        handleUserCommand = useCase,
        actionExecutor = fakeExecutor,
        usageHistoryRepository = fakeUsageRepo,
        featureFlagRepository = flagRepo,
        ioDispatcher = testDispatcher,
    )

    // ── App list loading ───────────────────────────────────────────────────

    @Test
    fun `Loading before advanceUntilIdle then Success after for non-empty list`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.example.one", "One"),
                InstalledApp("com.example.two", "Two"),
            )
            val vm = buildViewModel()

            // init{} has queued loadApps() but it has not run yet
            assertTrue(
                "Expected Loading before advance, got ${vm.uiState.value}",
                vm.uiState.value is UiState.Loading,
            )

            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue("Expected Success after advance, got $state", state is UiState.Success)
            // No usage history → original order preserved
            assertEquals(fakeRepo.appsToReturn, (state as UiState.Success).data.apps)
        }

    @Test
    fun `empty apps list emits Empty state`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = emptyList()
        val vm = buildViewModel()

        advanceUntilIdle()

        assertTrue(
            "Expected Empty, got ${vm.uiState.value}",
            vm.uiState.value is UiState.Empty,
        )
    }

    @Test
    fun `repository is called exactly once on init`() = runTest(testDispatcher) {
        buildViewModel()
        advanceUntilIdle()

        assertEquals(1, fakeRepo.callCount)
    }

    // ── OperationError → UiError mapping ──────────────────────────────────

    @Test
    fun `PermissionDenied maps to UiError_Message`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.PermissionDenied("QUERY_ALL_PACKAGES")
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue(
            "Expected UiError.Message, got ${(state as UiState.Error).error}",
            state.error is UiError.Message,
        )
    }

    @Test
    fun `NetworkError maps to UiError_Network`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Network, (state as UiState.Error).error)
    }

    @Test
    fun `UnknownError maps to UiError_Unknown`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.UnknownError()
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Unknown, (state as UiState.Error).error)
    }

    @Test
    fun `AiUnavailable maps to UiError_Unknown`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.AiUnavailable
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertEquals(UiError.Unknown, (state as UiState.Error).error)
    }

    @Test
    fun `DeviceNotCapable maps to UiError_Message`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.DeviceNotCapable("nlu")
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue(
            "Expected UiError.Message, got ${(state as UiState.Error).error}",
            state.error is UiError.Message,
        )
    }

    // ── Recoverable errors + retry (Block H, H2) ───────────────────────────

    @Test
    fun `NetworkError is retryable`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue("NetworkError should be retryable", (state as UiState.Error).retryable)
    }

    @Test
    fun `UnknownError is retryable`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.UnknownError()
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertTrue("UnknownError should be retryable", (state as UiState.Error).retryable)
    }

    @Test
    fun `PermissionDenied is NOT retryable`() = runTest(testDispatcher) {
        // Proves the mapping, not just the happy path: granting (not a retry button) is the fix.
        fakeRepo.errorToReturn = OperationError.PermissionDenied("QUERY_ALL_PACKAGES")
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Error, got $state", state is UiState.Error)
        assertFalse("PermissionDenied must not be retryable", (state as UiState.Error).retryable)
    }

    @Test
    fun `retry after a failure shows Loading then Success without restart`() = runTest(testDispatcher) {
        // A repo whose first load fails (retryable) and whose retry parks on a gate, so Loading is
        // the settled state while the reload is in flight — letting us assert the transient cleanly.
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val gatedRepo = object : InstalledAppsRepository {
            override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> {
                calls++
                return if (calls == 1) {
                    OperationResult.Failure(OperationError.NetworkError(retryable = true))
                } else {
                    gate.await() // hold the reload so the screen settles on Loading
                    OperationResult.Success(listOf(InstalledApp("com.example.one", "One")))
                }
            }
        }
        val vm = LauncherViewModel(
            installedAppsRepository = gatedRepo,
            handleUserCommand = useCase,
            actionExecutor = fakeExecutor,
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = fakeFlagRepo,
            ioDispatcher = testDispatcher,
        )
        advanceUntilIdle()
        assertTrue("Expected Error after first load", vm.uiState.value is UiState.Error)

        // User taps Retry on the SAME ViewModel instance (no process/VM restart).
        vm.retry()
        advanceUntilIdle() // null→Loading is processed; the reload is parked on gate.await()

        // Resetting to null returns the combine to Loading — using the still-held usage records
        // without racing them into a stale Error/Success emission.
        assertTrue(
            "Expected Loading while the reload is in flight, got ${vm.uiState.value}",
            vm.uiState.value is UiState.Loading,
        )

        gate.complete(Unit) // release the reload
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("Expected Success after retry reload, got $state", state is UiState.Success)
        assertEquals(
            listOf(InstalledApp("com.example.one", "One")),
            (state as UiState.Success).data.apps,
        )
        assertEquals("Repo should be hit twice: initial load + retry", 2, calls)
    }

    // ── commandInput independence ──────────────────────────────────────────

    @Test
    fun `commandInput updates independently and uiState stays Empty`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = emptyList()
            val vm = buildViewModel()

            vm.onCommandChanged("x")   // does not touch uiState

            advanceUntilIdle()

            assertTrue(
                "Expected Empty, got ${vm.uiState.value}",
                vm.uiState.value is UiState.Empty,
            )
            assertEquals("x", vm.commandInput.value)
        }

    @Test
    fun `commandInput starts empty before any coroutines run`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        // commandInput is a plain StateFlow — no coroutine needed to initialise it
        assertEquals("", vm.commandInput.value)
    }

    // ── Command submission → outcome → feedback ────────────────────────────

    @Test
    fun `submitting a high-confidence launch executes and clears input`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("telegram")
            fakeMatcher.confidenceToReturn = 0.90f
            val vm = buildViewModel()
            vm.onCommandChanged("open telegram")

            vm.onCommandSubmitted("open telegram")
            advanceUntilIdle()

            assertEquals(1, fakeExecutor.callCount)
            assertTrue(fakeExecutor.executedActions.single() is ExecutableAction.LaunchAppAction)
            assertEquals("", vm.commandInput.value)
            assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        }

    @Test
    fun `submitting an unknown command shows a message and does not execute`() =
        runTest(testDispatcher) {
            fakeMatcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = "zzz")
            fakeMatcher.confidenceToReturn = 0.10f
            val vm = buildViewModel()
            vm.onCommandChanged("zzz")

            vm.onCommandSubmitted("zzz")
            advanceUntilIdle()

            assertEquals(0, fakeExecutor.callCount)
            assertTrue(vm.commandFeedback.value is CommandFeedback.Message)
            // unknown input is preserved so the user can edit it
            assertEquals("zzz", vm.commandInput.value)
        }

    @Test
    fun `ambiguous command surfaces candidates without executing`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Maps"),
            InstalledApp("com.b", "Maps"),
        )
        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
        fakeMatcher.confidenceToReturn = 0.90f
        val vm = buildViewModel()

        vm.onCommandSubmitted("open maps")
        advanceUntilIdle()

        assertEquals(0, fakeExecutor.callCount)
        val feedback = vm.commandFeedback.value
        assertTrue(feedback is CommandFeedback.Ambiguous)
        assertEquals(2, (feedback as CommandFeedback.Ambiguous).candidates.size)
    }

    // ── Tap-to-launch goes straight through the executor ───────────────────

    @Test
    fun `onAppClicked launches the tapped app directly via executor`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
        advanceUntilIdle()

        val action = fakeExecutor.executedActions.single()
        assertTrue(action is ExecutableAction.LaunchAppAction)
        assertEquals("org.telegram.messenger", (action as ExecutableAction.LaunchAppAction).packageName)
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
    }

    @Test
    fun `onAppClicked surfaces a message when the launch fails`() = runTest(testDispatcher) {
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure("Couldn't open that app.")
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("com.missing", "Missing"))
        advanceUntilIdle()

        val feedback = vm.commandFeedback.value
        assertTrue(feedback is CommandFeedback.Message)
        assertEquals("Couldn't open that app.", (feedback as CommandFeedback.Message).text)
    }

    // ── Usage-aware grid sort (F6) ─────────────────────────────────────────

    @Test
    fun `apps with usage history appear before apps without`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.example.a", "Alpha"),
            InstalledApp("com.example.b", "Beta"),
            InstalledApp("com.example.c", "Gamma"),
        )
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.example.c", lastUsedEpochMs = 1000L, launchCount = 3),
        ))
        val vm = buildViewModel()
        advanceUntilIdle()

        val apps = (vm.uiState.value as UiState.Success).data.apps
        assertEquals("com.example.c", apps[0].packageName) // has history → first
        // Alpha and Beta maintain their original relative order
        assertEquals("com.example.a", apps[1].packageName)
        assertEquals("com.example.b", apps[2].packageName)
    }

    @Test
    fun `apps with higher launch count rank above lower count`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.example.a", "Alpha"),
            InstalledApp("com.example.b", "Beta"),
        )
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.example.a", lastUsedEpochMs = 2000L, launchCount = 2),
            AppUsageRecord("com.example.b", lastUsedEpochMs = 3000L, launchCount = 5),
        ))
        val vm = buildViewModel()
        advanceUntilIdle()

        val apps = (vm.uiState.value as UiState.Success).data.apps
        assertEquals("com.example.b", apps[0].packageName) // higher count wins
        assertEquals("com.example.a", apps[1].packageName)
    }

    @Test
    fun `equal launch count uses recency as tiebreaker`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.example.a", "Alpha"),
            InstalledApp("com.example.b", "Beta"),
        )
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.example.a", lastUsedEpochMs = 1000L, launchCount = 3),
            AppUsageRecord("com.example.b", lastUsedEpochMs = 5000L, launchCount = 3),
        ))
        val vm = buildViewModel()
        advanceUntilIdle()

        val apps = (vm.uiState.value as UiState.Success).data.apps
        assertEquals("com.example.b", apps[0].packageName) // more recent wins
        assertEquals("com.example.a", apps[1].packageName)
    }

    @Test
    fun `no usage history — original order preserved`() = runTest(testDispatcher) {
        val appsInOrder = listOf(
            InstalledApp("com.example.a", "Alpha"),
            InstalledApp("com.example.b", "Beta"),
            InstalledApp("com.example.c", "Gamma"),
        )
        fakeRepo.appsToReturn = appsInOrder
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(appsInOrder, (vm.uiState.value as UiState.Success).data.apps)
    }

    // ── Usage recording (F6) ───────────────────────────────────────────────

    @Test
    fun `successful tap-to-launch records usage for the launched package`() =
        runTest(testDispatcher) {
            val vm = buildViewModel()

            vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
            advanceUntilIdle()

            assertEquals(listOf("org.telegram.messenger"), fakeUsageRepo.recordedLaunches)
        }

    @Test
    fun `failed tap-to-launch does not record usage`() = runTest(testDispatcher) {
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure("Couldn't open.")
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("com.missing", "Missing"))
        advanceUntilIdle()

        assertTrue(fakeUsageRepo.recordedLaunches.isEmpty())
    }

    @Test
    fun `usage recording failure does not affect launch feedback`() = runTest(testDispatcher) {
        fakeUsageRepo.errorToReturn = OperationError.UnknownError("db down")
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
        advanceUntilIdle()

        // Launch succeeds; feedback is None despite the history write failing
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        assertEquals(1, fakeExecutor.callCount)
    }

    // ── Feature-flag gate for usage recording (Block F remediation) ───────────

    @Test
    fun `onAppClicked does not record usage when usageHistoryEnabled is false`() =
        runTest(testDispatcher) {
            val vm = buildViewModel(
                flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false))
            )

            vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
            advanceUntilIdle()

            // Launch still happens; only the history write is suppressed.
            assertEquals(1, fakeExecutor.callCount)
            assertTrue(
                "No usage recording expected when flag is false",
                fakeUsageRepo.recordedLaunches.isEmpty(),
            )
        }

    @Test
    fun `onAppClicked records usage when usageHistoryEnabled is true`() =
        runTest(testDispatcher) {
            val vm = buildViewModel(
                flagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))
            )

            vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
            advanceUntilIdle()

            assertEquals(listOf("org.telegram.messenger"), fakeUsageRepo.recordedLaunches)
        }

    @Test
    fun `recordUsage swallows non-cancellation error from getFlags`() =
        runTest(testDispatcher) {
            val throwingFlagRepo = object : FeatureFlagRepository {
                override fun getFlags(): Flow<FeatureFlags> =
                    flow { throw RuntimeException("flag read error") }
                override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> =
                    OperationResult.Success(Unit)
            }
            val vm = buildViewModel(flagRepo = throwingFlagRepo)

            vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
            advanceUntilIdle()

            // Launch still happened; the flag-read error is swallowed by the catch-all.
            assertEquals(1, fakeExecutor.callCount)
            assertEquals(CommandFeedback.None, vm.commandFeedback.value)
            // No usage recorded — flag could not be read.
            assertTrue(fakeUsageRepo.recordedLaunches.isEmpty())
        }
}
