package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeSuggestionEngine
import com.sidr.launcher.core.testing.FakeSuggestionsCacheRepository
import com.sidr.launcher.domain.repository.InstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeSpeechInputSource
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.core.testing.FakeUserPreferencesRepository
import com.sidr.launcher.domain.preferences.CachedSuggestion
import com.sidr.launcher.domain.preferences.UserPreferences
import com.sidr.launcher.domain.preferences.UserPreferencesRepository
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.domain.voice.SpeechRecognitionState
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.preferences.SuggestionsCacheRepository
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.suggestions.Suggestion
import com.sidr.launcher.domain.suggestions.SuggestionEngine
import com.sidr.launcher.domain.suggestions.SuggestionSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.ai.router.PlanResult
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.permission.PermissionFeature
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.async
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
    private val fakeSpeech = FakeSpeechInputSource()
    private val fakePrefsRepo = FakeUserPreferencesRepository()
    // Default: usageHistoryEnabled = true so existing recording tests remain valid.
    private val fakeFlagRepo = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true))
    // S2-1 Task 11: empty by default — every existing (pre-S2-1) assertion is exercised against an
    // empty store, proving no-preference parity. A fresh instance per test (JUnit4 instantiates the
    // test class per @Test method), so tests never leak preferences into one another.
    private val fakeResolutionStore = FakeResolutionPreferenceStore()
    private val fakeAliasStore = FakeAliasStore()
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

    // AIL-4: the VM now routes through RouteCommandUseCase. With the router flag OFF (fakeFlagRepo's
    // default), route() returns the unchanged HandleUserCommandUseCase outcome byte-for-byte, so every
    // existing VM test holds. Router-on behavior is covered in the domain RouteCommandUseCaseTest.
    private val routeUseCase = RouteCommandUseCase(
        handleUserCommand = useCase,
        planner = FakeCommandPlanner(),
        catalog = FakeActionCatalog(),
        featureFlagRepository = fakeFlagRepo,
        connectivityChecker = FakeConnectivityChecker(),
    )

    // AIL-5: executes a confirmed router-proposed action through the same resolver/executor path.
    private val executeAction = ExecuteActionUseCase(
        resolver = IntentActionResolver(fakeRepo),
        executor = fakeExecutor,
    )

    private fun aliasAwareResolveCommand(
        route: CommandRouteStep,
        resolutionStore: FakeResolutionPreferenceStore = fakeResolutionStore,
        actionCatalog: FakeActionCatalog = FakeActionCatalog(),
        aliasStore: FakeAliasStore = fakeAliasStore,
        installedApps: InstalledAppsRepository = fakeRepo,
    ): ResolveCommandWithAliasUseCase {
        val preferenceResolver = ResolveCommandWithPreferenceUseCase(
            route = route,
            store = resolutionStore,
            policy = DefaultResolutionPreferencePolicy(),
            catalog = actionCatalog,
        )
        return ResolveCommandWithAliasUseCase(
            inner = ResolvedCommandStep { rawInput -> preferenceResolver.resolve(rawInput) },
            store = aliasStore,
            installedApps = installedApps,
        )
    }

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
        fakeSpeech.reset()
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        flagRepo: FeatureFlagRepository = fakeFlagRepo,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        suggestionEngine: SuggestionEngine = FakeSuggestionEngine(),
        suggestionsCacheRepository: SuggestionsCacheRepository = FakeSuggestionsCacheRepository(),
        prefsRepo: UserPreferencesRepository = fakePrefsRepo,
        actionCatalog: FakeActionCatalog = FakeActionCatalog(),
        connectivity: FakeConnectivityChecker = FakeConnectivityChecker(),
        resolutionStore: FakeResolutionPreferenceStore = fakeResolutionStore,
    ) = LauncherViewModel(
        installedAppsRepository = fakeRepo,
        resolveCommand = aliasAwareResolveCommand(
            route = CommandRouteStep { routeUseCase.route(it) },
            resolutionStore = resolutionStore,
            actionCatalog = actionCatalog,
        ),
        recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
        executeAction = executeAction,
        actionExecutor = fakeExecutor,
        actionCatalog = actionCatalog,
        usageHistoryRepository = fakeUsageRepo,
        featureFlagRepository = flagRepo,
        userPreferencesRepository = prefsRepo,
        suggestionEngine = suggestionEngine,
        suggestionsCacheRepository = suggestionsCacheRepository,
        speechInputSource = fakeSpeech,
        connectivityChecker = connectivity,
        ioDispatcher = testDispatcher,
        applicationScope = CoroutineScope(testDispatcher + SupervisorJob()),
        savedStateHandle = savedStateHandle,
    )

    @Test
    fun `isOnline reflects the connectivity checker snapshot`() = runTest {
        val offline = buildViewModel(connectivity = FakeConnectivityChecker(initiallyOnline = false))
        assertFalse(offline.isOnline.value)
        val online = buildViewModel(connectivity = FakeConnectivityChecker(initiallyOnline = true))
        assertTrue(online.isOnline.value)
    }

    /**
     * Builds a router-ON ViewModel whose planner returns [plannerResult]. [catalog] is shared by the
     * route use case (risk → needsConfirmation) and the VM (permissionGate lookup), matching production
     * where both read the same [DefaultActionCatalog]. The rule matcher is forced to Unknown so the
     * planner is always consulted (AIL-4 trigger).
     */
    private fun buildRouterViewModel(
        plannerResult: PlanResult,
        catalog: FakeActionCatalog = FakeActionCatalog(),
        online: Boolean = true,
        resolutionStore: FakeResolutionPreferenceStore = fakeResolutionStore,
    ): LauncherViewModel {
        fakeMatcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = "nl command", reason = "x")
        fakeMatcher.confidenceToReturn = 0.0f
        val router = RouteCommandUseCase(
            handleUserCommand = useCase,
            planner = FakeCommandPlanner(resultToReturn = plannerResult),
            catalog = catalog,
            featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(llmRouterEnabled = true)),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = online),
        )
        return LauncherViewModel(
            installedAppsRepository = fakeRepo,
            resolveCommand = aliasAwareResolveCommand(
                route = CommandRouteStep { router.route(it) },
                resolutionStore = resolutionStore,
                actionCatalog = catalog,
            ),
            recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            actionCatalog = catalog,
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = fakeFlagRepo,
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = FakeSuggestionEngine(),
            suggestionsCacheRepository = FakeSuggestionsCacheRepository(),
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            ioDispatcher = testDispatcher,
            applicationScope = CoroutineScope(testDispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    private fun safeDescriptor(id: com.sidr.launcher.domain.action.ActionId) = ActionDescriptor(
        id = id,
        title = "t",
        description = "d",
        category = ActionCategory.WEB,
        risk = ActionRiskLevel.SAFE,
    )

    // ── AIL-5 confirmation & safety gating ──────────────────────────────────

    @Test
    fun `a CONFIRM router proposal surfaces a confirm card and never auto-executes`() =
        runTest(testDispatcher) {
            // Empty catalog ⇒ needsConfirmation = true (fail-safe): the card is required.
            val vm = buildRouterViewModel(
                plannerResult = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.com"), 0.9f),
            )

            vm.onCommandSubmitted("go to x")
            advanceUntilIdle()

            val pending = vm.pendingRoutedAction.value
            assertTrue("expected a pending confirm card, got $pending", pending != null)
            assertTrue("expected requiresConfirmation", pending!!.requiresConfirmation)
            // R4: nothing executed until the user confirms.
            assertTrue(fakeExecutor.executedActions.isEmpty())
        }

    @Test
    fun `a SAFE router proposal surfaces a one-tap pending action, not a card`() =
        runTest(testDispatcher) {
            val catalog = FakeActionCatalog(listOf(safeDescriptor(ActionIds.WEB_SEARCH)))
            val vm = buildRouterViewModel(
                plannerResult = PlanResult.RoutedAction(LauncherAction.WebSearch("weather"), 0.8f),
                catalog = catalog,
            )

            vm.onCommandSubmitted("what's the weather")
            advanceUntilIdle()

            val pending = vm.pendingRoutedAction.value
            assertTrue("expected a pending one-tap action, got $pending", pending != null)
            assertFalse("SAFE proposal must not require a card", pending!!.requiresConfirmation)
            // R4: still never silently executed — the user must tap.
            assertTrue(fakeExecutor.executedActions.isEmpty())
        }

    @Test
    fun `confirming a pending action executes it and clears the card and input`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
            val vm = buildRouterViewModel(
                plannerResult = PlanResult.RoutedAction(LauncherAction.LaunchApp("Telegram"), 0.9f),
            )
            vm.onCommandSubmitted("fire up telegram")
            advanceUntilIdle()
            assertTrue(vm.pendingRoutedAction.value != null)

            vm.confirmRoutedAction()
            advanceUntilIdle()

            assertEquals(
                ExecutableAction.LaunchAppAction("org.telegram.messenger", null),
                fakeExecutor.executedActions.single(),
            )
            assertEquals(null, vm.pendingRoutedAction.value)
            assertEquals("", vm.commandInput.value)
        }

    @Test
    fun `cancelling a pending action clears it without executing`() = runTest(testDispatcher) {
        val vm = buildRouterViewModel(
            plannerResult = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.com"), 0.9f),
        )
        vm.onCommandSubmitted("go to x")
        advanceUntilIdle()
        assertTrue(vm.pendingRoutedAction.value != null)

        vm.cancelRoutedAction()
        advanceUntilIdle()

        assertEquals(null, vm.pendingRoutedAction.value)
        assertTrue(fakeExecutor.executedActions.isEmpty())
    }

    @Test
    fun `a pending confirmation carries the descriptor permission gate`() = runTest(testDispatcher) {
        val gatedDescriptor = ActionDescriptor(
            id = ActionIds.OPEN_URL,
            title = "t",
            description = "d",
            category = ActionCategory.WEB,
            risk = ActionRiskLevel.CONFIRM,
            permissionGate = PermissionFeature.VOICE_INPUT,
        )
        val vm = buildRouterViewModel(
            plannerResult = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.com"), 0.9f),
            catalog = FakeActionCatalog(listOf(gatedDescriptor)),
        )

        vm.onCommandSubmitted("go to x")
        advanceUntilIdle()

        assertEquals(PermissionFeature.VOICE_INPUT, vm.pendingRoutedAction.value?.permissionGate)
    }

    // ── App list loading ───────────────────────────────────────────────────

    @Test
    fun `home shell paints before app list then Success after for non-empty list`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.example.one", "One"),
                InstalledApp("com.example.two", "Two"),
            )
            val vm = buildViewModel()

            // init{} has queued loadApps() but it has not run yet; home still paints immediately.
            val initial = vm.uiState.value
            assertTrue(
                "Expected cache-first home shell before advance, got $initial",
                initial is UiState.Success,
            )
            assertTrue((initial as UiState.Success).data.apps.isEmpty())
            assertTrue(initial.data.favorites.isEmpty())

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
    fun `retry after a failure keeps home shell visible then Success without restart`() = runTest(testDispatcher) {
        // A repo whose first load fails (retryable) and whose retry parks on a gate, so the cache-first
        // home shell is the settled state while the reload is in flight — no full-screen spinner.
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
            resolveCommand = aliasAwareResolveCommand(
                route = CommandRouteStep { routeUseCase.route(it) },
                installedApps = gatedRepo,
            ),
            recordResolutionChoice = RecordResolutionChoiceUseCase(fakeResolutionStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            actionCatalog = FakeActionCatalog(),
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = fakeFlagRepo,
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = FakeSuggestionEngine(),
            suggestionsCacheRepository = FakeSuggestionsCacheRepository(),
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            ioDispatcher = testDispatcher,
            applicationScope = CoroutineScope(testDispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
        advanceUntilIdle()
        assertTrue("Expected Error after first load", vm.uiState.value is UiState.Error)

        // User taps Retry on the SAME ViewModel instance (no process/VM restart).
        vm.retry()
        advanceUntilIdle() // null state is processed; the reload is parked on gate.await()

        // Resetting to null clears the stale Error but keeps the home shell visible while reloading.
        val reloadingState = vm.uiState.value
        assertTrue(
            "Expected cache-first home shell while the reload is in flight, got $reloadingState",
            reloadingState is UiState.Success,
        )
        assertTrue((reloadingState as UiState.Success).data.apps.isEmpty())

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

    @Test
    fun `double-tap retry hits the repo once and resolves cleanly`() = runTest(testDispatcher) {
        // First load fails (retryable).
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()
        assertTrue("Expected Error after first load", vm.uiState.value is UiState.Error)
        assertEquals("Initial load is one hit", 1, fakeRepo.callCount)

        // Failure clears; the user double-taps Retry before the scheduler runs the reload.
        fakeRepo.errorToReturn = null
        fakeRepo.appsToReturn = listOf(InstalledApp("com.example.one", "One"))
        vm.retry()
        vm.retry()

        advanceUntilIdle()

        // The second retry cancels the first's not-yet-started load: exactly one reload runs.
        val state = vm.uiState.value
        assertTrue("Expected Success, got $state", state is UiState.Success)
        assertEquals(
            listOf(InstalledApp("com.example.one", "One")),
            (state as UiState.Success).data.apps,
        )
        assertEquals("Double-tap retry must hit the repo once for the retry, not twice", 2, fakeRepo.callCount)
    }

    // ── Process-death restoration (Block H, H3) ────────────────────────────

    @Test
    fun `typed command input is restored after process death`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm1 = buildViewModel(savedStateHandle = handle)
        vm1.onCommandChanged("open te")
        advanceUntilIdle()

        // Simulate process death: a fresh ViewModel restored from the SAME SavedStateHandle.
        val vm2 = buildViewModel(savedStateHandle = handle)
        advanceUntilIdle()

        assertEquals("open te", vm2.commandInput.value)
    }

    @Test
    fun `command feedback is NOT restored after process death`() = runTest(testDispatcher) {
        val handle = SavedStateHandle()
        val vm1 = buildViewModel(savedStateHandle = handle)
        vm1.onCommandSubmitted("") // Empty outcome → sets a transient feedback Message
        advanceUntilIdle()
        assertTrue(
            "Sanity: feedback should be set on vm1",
            vm1.commandFeedback.value is CommandFeedback.Message,
        )

        // A relaunched VM restores input but must NOT resurrect the ephemeral last-command result.
        val vm2 = buildViewModel(savedStateHandle = handle)
        advanceUntilIdle()

        assertEquals(CommandFeedback.None, vm2.commandFeedback.value)
    }

    @Test
    fun `clearing input persists through SavedStateHandle`() = runTest(testDispatcher) {
        // Post-migration: the clear-on-Executed path must write through the handle, not a stale field.
        val handle = SavedStateHandle()
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("telegram")
        fakeMatcher.confidenceToReturn = 0.90f
        val vm = buildViewModel(savedStateHandle = handle)
        vm.onCommandChanged("open telegram")
        assertEquals("open telegram", vm.commandInput.value)

        vm.onCommandSubmitted("open telegram") // Executed → clears input
        advanceUntilIdle()
        assertEquals("", vm.commandInput.value)

        // The clear was written to the handle: a VM restored from it also starts empty.
        val vmRestored = buildViewModel(savedStateHandle = handle)
        advanceUntilIdle()
        assertEquals("", vmRestored.commandInput.value)
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

    // ── Suggestions (Phase 7, Block W-lite) ───────────────────────────────

    @Test
    fun `cached package suggestions wait for app list while route suggestions can paint immediately`() =
        runTest(testDispatcher) {
        val appListGate = CompletableDeferred<Unit>()
        var appListCalls = 0
        val gatedRepo = object : InstalledAppsRepository {
            override suspend fun getInstalledApps(): OperationResult<List<InstalledApp>> {
                appListCalls++
                appListGate.await()
                return OperationResult.Success(listOf(InstalledApp("com.cached", "Cached App")))
            }
        }
        val cacheRepo = FakeSuggestionsCacheRepository(
            initial = listOf(
                CachedSuggestion(label = "Cached label", actionId = "com.cached"),
                CachedSuggestion(label = "Assistant", actionId = Routes.Assistant.ROUTE),
            ),
        )
        val gatedEngine = object : SuggestionEngine {
            private val state = MutableStateFlow<List<Suggestion>>(emptyList())
            val refreshStarted = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()

            override fun suggestions(): Flow<List<Suggestion>> = state.asStateFlow()

            override suspend fun refresh(): OperationResult<List<Suggestion>> {
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                return OperationResult.Success(emptyList())
            }
        }
        val vm = LauncherViewModel(
            installedAppsRepository = gatedRepo,
            resolveCommand = aliasAwareResolveCommand(
                route = CommandRouteStep { routeUseCase.route(it) },
                installedApps = gatedRepo,
            ),
            recordResolutionChoice = RecordResolutionChoiceUseCase(fakeResolutionStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            actionCatalog = FakeActionCatalog(),
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = gatedEngine,
            suggestionsCacheRepository = cacheRepo,
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            ioDispatcher = testDispatcher,
            applicationScope = CoroutineScope(testDispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )

        advanceUntilIdle()

        val firstPaint = vm.uiState.value as UiState.Success
        assertEquals("App list load should be in flight", 1, appListCalls)
        assertTrue("Apps are filled only after PackageManager returns", firstPaint.data.apps.isEmpty())
        assertEquals(listOf("Assistant"), firstPaint.data.suggestions.map { it.label })

        appListGate.complete(Unit)
        advanceUntilIdle()

        val afterApps = vm.uiState.value as UiState.Success
        assertEquals(
            listOf("Cached App", "Assistant"),
            afterApps.data.suggestions.map { it.label },
        )

        gatedEngine.releaseRefresh.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `cached suggestions first-paint then fresh engine result supersedes without merge`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.cached", "Cached App"),
            InstalledApp("com.fresh", "Fresh App"),
        )
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
        )
        val cacheRepo = FakeSuggestionsCacheRepository(
            initial = listOf(CachedSuggestion(label = "com.cached", actionId = "com.cached")),
        )
        val freshSuggestions = listOf(
            Suggestion(
                label = "com.fresh",
                actionId = "com.fresh",
                source = SuggestionSource.RECENT_USAGE,
                score = 1.0,
            ),
        )
        val gatedEngine = object : SuggestionEngine {
            private val state = MutableStateFlow<List<Suggestion>>(emptyList())

            var refreshCount: Int = 0
                private set

            val refreshStarted = CompletableDeferred<Unit>()
            val releaseRefresh = CompletableDeferred<Unit>()

            override fun suggestions(): Flow<List<Suggestion>> = state.asStateFlow()

            override suspend fun refresh(): OperationResult<List<Suggestion>> {
                refreshCount++
                refreshStarted.complete(Unit)
                releaseRefresh.await()
                state.value = freshSuggestions
                return OperationResult.Success(freshSuggestions)
            }
        }
        val vm = buildViewModel(
            flagRepo = flagRepo,
            suggestionEngine = gatedEngine,
            suggestionsCacheRepository = cacheRepo,
        )

        gatedEngine.refreshStarted.await()
        advanceUntilIdle()

        val cachedState = vm.uiState.value as UiState.Success
        assertEquals(listOf("Cached App"), cachedState.data.suggestions.map { it.label })

        gatedEngine.releaseRefresh.complete(Unit)
        advanceUntilIdle()

        val freshState = vm.uiState.value as UiState.Success
        assertEquals(1, gatedEngine.refreshCount)
        assertEquals(listOf("Fresh App"), freshState.data.suggestions.map { it.label })
        assertEquals(listOf("com.fresh"), freshState.data.suggestions.map { it.actionId })
    }

    @Test
    fun `ai suggestions flag off leaves suggestions empty and skips refresh`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("com.example.one", "One"))
        val cacheRepo = FakeSuggestionsCacheRepository(
            initial = listOf(CachedSuggestion(label = "Stale", actionId = "com.stale")),
        )
        val suggestionEngine = FakeSuggestionEngine().apply {
            refreshResult = OperationResult.Success(
                listOf(
                    Suggestion(
                        label = "Fresh",
                        actionId = "com.fresh",
                        source = SuggestionSource.RECENT_USAGE,
                        score = 1.0,
                    ),
                ),
            )
        }
        val vm = buildViewModel(
            flagRepo = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true),
            ),
            suggestionEngine = suggestionEngine,
            suggestionsCacheRepository = cacheRepo,
        )

        advanceUntilIdle()

        val state = vm.uiState.value as UiState.Success
        assertTrue(state.data.suggestions.isEmpty())
        assertEquals(0, suggestionEngine.refreshCount)
    }

    @Test
    fun `enabling ai suggestions after init restores cache then refreshes live`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.cached", "Cached App"),
            InstalledApp("com.fresh", "Fresh App"),
        )
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true),
        )
        val cacheRepo = FakeSuggestionsCacheRepository(
            initial = listOf(CachedSuggestion(label = "com.cached", actionId = "com.cached")),
        )
        val freshSuggestions = listOf(
            Suggestion(
                label = "com.fresh",
                actionId = "com.fresh",
                source = SuggestionSource.RECENT_USAGE,
                score = 1.0,
            ),
        )
        val suggestionEngine = FakeSuggestionEngine().apply {
            refreshResult = OperationResult.Success(freshSuggestions)
        }
        val vm = buildViewModel(
            flagRepo = flagRepo,
            suggestionEngine = suggestionEngine,
            suggestionsCacheRepository = cacheRepo,
        )

        advanceUntilIdle()
        assertTrue((vm.uiState.value as UiState.Success).data.suggestions.isEmpty())

        flagRepo.updateFlags(FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true))
        advanceUntilIdle()

        val state = vm.uiState.value as UiState.Success
        assertEquals(1, suggestionEngine.refreshCount)
        assertEquals(listOf("Fresh App"), state.data.suggestions.map { it.label })
    }

    @Test
    fun `disabling ai suggestions after they rendered clears them`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("com.fresh", "Fresh App"))
        val flagRepo = FakeFeatureFlagRepository(
            FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
        )
        val suggestionEngine = FakeSuggestionEngine().apply {
            refreshResult = OperationResult.Success(
                listOf(
                    Suggestion(
                        label = "com.fresh",
                        actionId = "com.fresh",
                        source = SuggestionSource.RECENT_USAGE,
                        score = 1.0,
                    ),
                ),
            )
        }
        val vm = buildViewModel(
            flagRepo = flagRepo,
            suggestionEngine = suggestionEngine,
        )

        advanceUntilIdle()
        assertEquals(listOf("Fresh App"), (vm.uiState.value as UiState.Success).data.suggestions.map { it.label })

        flagRepo.updateFlags(FeatureFlags(aiSuggestionsEnabled = false, usageHistoryEnabled = true))
        advanceUntilIdle()

        assertTrue((vm.uiState.value as UiState.Success).data.suggestions.isEmpty())
    }

    @Test
    fun `suggestions filter unlaunchable package actions while keeping installed apps and routes`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.installed", "Installed App"),
            )
            val suggestions = listOf(
                Suggestion(
                    label = "Clock",
                    actionId = "com.android.deskclock",
                    source = SuggestionSource.TIME_OF_DAY,
                    score = 1.0,
                ),
                Suggestion(
                    label = "com.installed",
                    actionId = "com.installed",
                    source = SuggestionSource.RECENT_USAGE,
                    score = 0.8,
                ),
                Suggestion(
                    label = "Assistant",
                    actionId = Routes.Assistant.ROUTE,
                    source = SuggestionSource.TIME_OF_DAY,
                    score = 0.4,
                ),
            )
            val suggestionEngine = FakeSuggestionEngine().apply {
                refreshResult = OperationResult.Success(suggestions)
            }
            val vm = buildViewModel(
                flagRepo = FakeFeatureFlagRepository(
                    FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
                ),
                suggestionEngine = suggestionEngine,
            )

            advanceUntilIdle()

            val state = vm.uiState.value as UiState.Success
            assertEquals(
                listOf("com.installed", Routes.Assistant.ROUTE),
                state.data.suggestions.map { it.actionId },
            )
            assertEquals(
                listOf("Installed App", "Assistant"),
                state.data.suggestions.map { it.label },
            )
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

    @Test
    fun `package suggestion tap reuses app-click launch path`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp(
                packageName = "org.telegram.messenger",
                label = "Telegram",
                activityName = "org.telegram.messenger.MainActivity",
            ),
        )
        val vm = buildViewModel(
            flagRepo = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
        )
        advanceUntilIdle()

        vm.onSuggestionClicked(
            Suggestion(
                label = "Telegram",
                actionId = "org.telegram.messenger",
                source = SuggestionSource.RECENT_USAGE,
                score = 1.0,
            ),
        )
        advanceUntilIdle()

        val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
        assertEquals("org.telegram.messenger", action.packageName)
        assertEquals("org.telegram.messenger.MainActivity", action.activityName)
    }

    @Test
    fun `route suggestion tap emits existing navigation event`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(InstalledApp("com.example.one", "One"))
        val vm = buildViewModel(
            flagRepo = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
        )
        advanceUntilIdle()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.onSuggestionClicked(
            Suggestion(
                label = "Assistant",
                actionId = Routes.Assistant.ROUTE,
                source = SuggestionSource.TIME_OF_DAY,
                score = 1.0,
            ),
        )

        assertEquals(
            NavigationEvent.NavigateTo(Routes.Assistant.ROUTE),
            eventDeferred.await(),
        )
    }

    @Test
    fun `route suggestion tap clears stale feedback without launching`() = runTest(testDispatcher) {
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure("Couldn't open that app.")
        val vm = buildViewModel()
        vm.onAppClicked(InstalledApp("com.missing", "Missing"))
        advanceUntilIdle()
        assertTrue(vm.commandFeedback.value is CommandFeedback.Message)
        fakeExecutor.reset()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.onSuggestionClicked(
            Suggestion(
                label = "Settings",
                actionId = Routes.Settings.ROUTE,
                source = SuggestionSource.TIME_OF_DAY,
                score = 1.0,
            ),
        )

        assertEquals(NavigationEvent.NavigateTo(Routes.Settings.ROUTE), eventDeferred.await())
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        assertEquals(0, fakeExecutor.callCount)
    }

    @Test
    fun `unknown package suggestion tap launches actionId without activity fallback`() =
        runTest(testDispatcher) {
            val vm = buildViewModel(
                flagRepo = FakeFeatureFlagRepository(
                    FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
                ),
            )
            advanceUntilIdle()

            vm.onSuggestionClicked(
                Suggestion(
                    label = "Direct Package",
                    actionId = "com.direct.package",
                    source = SuggestionSource.RECENT_USAGE,
                    score = 1.0,
                ),
            )
            advanceUntilIdle()

            val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
            assertEquals("com.direct.package", action.packageName)
            assertEquals(null, action.activityName)
            assertEquals(listOf("com.direct.package"), fakeUsageRepo.recordedLaunches)
        }

    @Test
    fun `package suggestion tap respects usage history gate`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp(
                packageName = "org.telegram.messenger",
                label = "Telegram",
                activityName = "org.telegram.messenger.MainActivity",
            ),
        )
        val vm = buildViewModel(
            flagRepo = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = false),
            ),
        )
        advanceUntilIdle()

        vm.onSuggestionClicked(
            Suggestion(
                label = "Telegram",
                actionId = "org.telegram.messenger",
                source = SuggestionSource.RECENT_USAGE,
                score = 1.0,
            ),
        )
        advanceUntilIdle()

        val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
        assertEquals("org.telegram.messenger", action.packageName)
        assertEquals("org.telegram.messenger.MainActivity", action.activityName)
        assertTrue(fakeUsageRepo.recordedLaunches.isEmpty())
    }

    @Test
    fun `open settings outcome clears input and emits settings navigation`() = runTest(testDispatcher) {
        fakeMatcher.intentToReturn = LauncherIntent.OpenSettingsIntent()
        fakeMatcher.confidenceToReturn = 0.95f
        val vm = buildViewModel()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.onCommandChanged("settings")
        vm.onCommandSubmitted("settings")
        advanceUntilIdle()

        assertEquals("", vm.commandInput.value)
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        assertEquals(NavigationEvent.NavigateTo(Routes.Settings.ROUTE), eventDeferred.await())
        assertEquals(0, fakeExecutor.callCount)
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

    // ── Favorites derivation (Block X2) ────────────────────────────────────

    @Test
    fun `favorites are the top-N most-used apps in usage order`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Alpha"),
            InstalledApp("com.b", "Beta"),
            InstalledApp("com.c", "Gamma"),
        )
        // Usage records arrive most-used-first (repository contract): b, then c, then a.
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.b", lastUsedEpochMs = 3000L, launchCount = 9),
            AppUsageRecord("com.c", lastUsedEpochMs = 2000L, launchCount = 5),
            AppUsageRecord("com.a", lastUsedEpochMs = 1000L, launchCount = 1),
        ))
        val vm = buildViewModel()
        advanceUntilIdle()

        val favorites = (vm.uiState.value as UiState.Success).data.favorites
        assertEquals(
            listOf("com.b", "com.c", "com.a"),
            favorites.map { it.packageName },
        )
    }

    @Test
    fun `favorites exclude usage records for uninstalled apps`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Alpha"),
            InstalledApp("com.c", "Gamma"),
        )
        // com.b has usage history but is no longer installed → must not appear in favorites.
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.b", lastUsedEpochMs = 3000L, launchCount = 9),
            AppUsageRecord("com.a", lastUsedEpochMs = 2000L, launchCount = 5),
            AppUsageRecord("com.c", lastUsedEpochMs = 1000L, launchCount = 1),
        ))
        val vm = buildViewModel()
        advanceUntilIdle()

        val favorites = (vm.uiState.value as UiState.Success).data.favorites
        assertEquals(listOf("com.a", "com.c"), favorites.map { it.packageName })
    }

    @Test
    fun `favorites are capped at FAVORITES_COUNT`() = runTest(testDispatcher) {
        val apps = (0 until 10).map { InstalledApp("com.app$it", "App $it") }
        fakeRepo.appsToReturn = apps
        fakeUsageRepo.setRecords(
            apps.mapIndexed { i, app ->
                AppUsageRecord(app.packageName, lastUsedEpochMs = (10 - i).toLong(), launchCount = 10 - i)
            },
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        val favorites = (vm.uiState.value as UiState.Success).data.favorites
        assertEquals("Favorites must be capped at 8", 8, favorites.size)
        // The 8 most-used (first 8 records) are kept, in order.
        assertEquals(
            (0 until 8).map { "com.app$it" },
            favorites.map { it.packageName },
        )
    }

    @Test
    fun `favorites honour a custom favoritesCount preference`() = runTest(testDispatcher) {
        val apps = (0 until 10).map { InstalledApp("com.app$it", "App $it") }
        fakeRepo.appsToReturn = apps
        fakeUsageRepo.setRecords(
            apps.mapIndexed { i, app ->
                AppUsageRecord(app.packageName, lastUsedEpochMs = (10 - i).toLong(), launchCount = 10 - i)
            },
        )
        val vm = buildViewModel(
            prefsRepo = FakeUserPreferencesRepository(UserPreferences(favoritesCount = 4)),
        )
        advanceUntilIdle()

        val favorites = (vm.uiState.value as UiState.Success).data.favorites
        assertEquals("Custom favoritesCount must cap the row", 4, favorites.size)
        assertEquals((0 until 4).map { "com.app$it" }, favorites.map { it.packageName })
    }

    @Test
    fun `favorites are empty when favoritesCount preference is zero`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Alpha"),
            InstalledApp("com.b", "Beta"),
        )
        fakeUsageRepo.setRecords(listOf(
            AppUsageRecord("com.a", lastUsedEpochMs = 2000L, launchCount = 5),
            AppUsageRecord("com.b", lastUsedEpochMs = 1000L, launchCount = 3),
        ))
        val vm = buildViewModel(
            prefsRepo = FakeUserPreferencesRepository(UserPreferences(favoritesCount = 0)),
        )
        advanceUntilIdle()

        val state = vm.uiState.value as UiState.Success
        assertTrue(state.data.favorites.isEmpty())
        assertEquals(listOf("com.a", "com.b"), state.data.apps.map { it.packageName })
    }

    @Test
    fun `favorites react to favoritesCount preference changes without reloading apps`() =
        runTest(testDispatcher) {
            val apps = (0 until 4).map { InstalledApp("com.app$it", "App $it") }
            fakeRepo.appsToReturn = apps
            fakeUsageRepo.setRecords(
                apps.mapIndexed { i, app ->
                    AppUsageRecord(app.packageName, lastUsedEpochMs = (4 - i).toLong(), launchCount = 4 - i)
                },
            )
            val prefsRepo = FakeUserPreferencesRepository(UserPreferences(favoritesCount = 3))
            val vm = buildViewModel(prefsRepo = prefsRepo)
            advanceUntilIdle()
            assertEquals(1, fakeRepo.callCount)
            assertEquals(
                (0 until 3).map { "com.app$it" },
                (vm.uiState.value as UiState.Success).data.favorites.map { it.packageName },
            )

            prefsRepo.updatePreferences(UserPreferences(favoritesCount = 1))
            advanceUntilIdle()

            assertEquals("Changing the count must not re-query PackageManager", 1, fakeRepo.callCount)
            assertEquals(
                listOf("com.app0"),
                (vm.uiState.value as UiState.Success).data.favorites.map { it.packageName },
            )
        }

    @Test
    fun `no usage history yields empty favorites`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp("com.a", "Alpha"),
            InstalledApp("com.b", "Beta"),
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value as UiState.Success
        assertTrue("Fresh install → no favorites", state.data.favorites.isEmpty())
        // The full app list still loads (drawer + suggestion resolution depend on it).
        assertEquals(2, state.data.apps.size)
    }

    // ── Top-bar / All-apps navigation (Block X2) ───────────────────────────

    @Test
    fun `settings top-bar icon emits settings navigation`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.navigateTo(Routes.Settings.ROUTE)

        assertEquals(NavigationEvent.NavigateTo(Routes.Settings.ROUTE), eventDeferred.await())
    }

    @Test
    fun `assistant top-bar icon emits assistant navigation`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.navigateTo(Routes.Assistant.ROUTE)

        assertEquals(NavigationEvent.NavigateTo(Routes.Assistant.ROUTE), eventDeferred.await())
    }

    @Test
    fun `all apps affordance emits app drawer navigation`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        val eventDeferred = async { vm.navigationEvents.first() }

        vm.navigateTo(Routes.AppDrawer.ROUTE)

        assertEquals(NavigationEvent.NavigateTo(Routes.AppDrawer.ROUTE), eventDeferred.await())
    }

    // ── Voice input (Block T) ──────────────────────────────────────────────

    @Test
    fun `voice partials stream into commandInput`() = runTest(testDispatcher) {
        fakeSpeech.scriptedStates = listOf(
            SpeechRecognitionState.Ready,
            SpeechRecognitionState.Partial("open te"),
        )
        val vm = buildViewModel()

        vm.startVoiceInput()
        advanceUntilIdle()

        assertEquals("open te", vm.commandInput.value)
        assertEquals(0, fakeExecutor.callCount) // no Final yet → nothing submitted
    }

    @Test
    fun `voice final submits through the unchanged command path and executes`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("telegram")
            fakeMatcher.confidenceToReturn = 0.90f
            fakeSpeech.scriptSuccess(partials = listOf("open", "open tele"), finalText = "open telegram")
            val vm = buildViewModel()

            vm.startVoiceInput()
            advanceUntilIdle()

            // Final rode the same path as keyboard submit: executed + input cleared (Executed).
            assertEquals(1, fakeExecutor.callCount)
            assertTrue(fakeExecutor.executedActions.single() is ExecutableAction.LaunchAppAction)
            assertEquals("", vm.commandInput.value)
            // The recognized text fed the matcher (normalized) exactly as keyboard text would.
            assertTrue("open telegram" in fakeMatcher.receivedInputs)
        }

    @Test
    fun `voice unavailable degrades to a message and never listens`() = runTest(testDispatcher) {
        fakeSpeech.available = false
        val vm = buildViewModel()

        assertFalse(vm.isVoiceInputAvailable)

        vm.startVoiceInput()
        advanceUntilIdle()

        assertEquals(0, fakeExecutor.callCount)
        assertTrue(vm.commandFeedback.value is CommandFeedback.Message)
        // The keyboard path is unaffected — a normal type-then-submit launch still executes.
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("telegram")
        fakeMatcher.confidenceToReturn = 0.90f
        vm.onCommandChanged("open telegram")
        vm.onCommandSubmitted("open telegram")
        advanceUntilIdle()
        assertEquals(1, fakeExecutor.callCount)
    }

    @Test
    fun `voice error surfaces a message and does not execute`() = runTest(testDispatcher) {
        fakeSpeech.scriptError(SpeechRecognitionError.PERMISSION_DENIED)
        val vm = buildViewModel()

        vm.startVoiceInput()
        advanceUntilIdle()

        assertEquals(0, fakeExecutor.callCount)
        assertTrue(vm.commandFeedback.value is CommandFeedback.Message)
    }

    // ── Voice/mic user toggle (Block X6) ───────────────────────────────────

    @Test
    fun `showMic is true when recognizer available and mic pref enabled`() = runTest(testDispatcher) {
        fakeSpeech.available = true
        val vm = buildViewModel(
            prefsRepo = FakeUserPreferencesRepository(UserPreferences(micInputEnabled = true)),
        )
        advanceUntilIdle()

        assertTrue(vm.showMic.value)
    }

    @Test
    fun `showMic is false when mic pref disabled even if recognizer available`() = runTest(testDispatcher) {
        fakeSpeech.available = true
        val vm = buildViewModel(
            prefsRepo = FakeUserPreferencesRepository(UserPreferences(micInputEnabled = false)),
        )
        advanceUntilIdle()

        assertFalse(vm.showMic.value)
    }

    @Test
    fun `startVoiceInput is a no-op when mic input is disabled`() = runTest(testDispatcher) {
        // Recognizer is available, but the user disabled voice in Settings: a stale mic tap must not
        // start recognition, submit anything, or surface a message.
        fakeSpeech.available = true
        fakeSpeech.scriptSuccess(partials = listOf("open"), finalText = "open telegram")
        val vm = buildViewModel(
            prefsRepo = FakeUserPreferencesRepository(UserPreferences(micInputEnabled = false)),
        )
        advanceUntilIdle()

        vm.startVoiceInput()
        advanceUntilIdle()

        assertEquals(0, fakeExecutor.callCount)
        assertEquals("", vm.commandInput.value)
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
    }

    // ── Universal-input live results (AIL-3, Task 4) ───────────────────────

    @Test
    // Note: FakeInstalledAppsRepository seeds via `appsToReturn`; FakeIntentMatcher records the
    // *normalized* (trim+collapse+lowercase-ROOT) inputs in `receivedInputs` — assert against those.
    fun `typing surfaces app matches and web+ask chips`() = runTest(testDispatcher) {
        fakeRepo.appsToReturn = listOf(
            InstalledApp(packageName = "org.telegram.messenger", label = "Telegram", activityName = null),
            InstalledApp(packageName = "com.maps", label = "Maps", activityName = null),
        )
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("tele")
        advanceUntilIdle()
        val results = vm.inputResults.value
        assertTrue(results.active)
        assertEquals(listOf("Telegram"), results.appMatches.map { it.label })
        assertEquals(listOf(RouteChipKind.WEB, RouteChipKind.ASK), results.chips)
    }

    @Test
    fun `typing a safe url adds the site chip`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("github.com")
        advanceUntilIdle()
        assertEquals(listOf(RouteChipKind.WEB, RouteChipKind.ASK, RouteChipKind.SITE), vm.inputResults.value.chips)
    }

    @Test
    fun `clearing the buffer returns empty inactive results`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("tele")
        advanceUntilIdle()
        vm.onCommandChanged("")
        advanceUntilIdle()
        val results = vm.inputResults.value
        assertFalse(results.active)
        assertTrue(results.appMatches.isEmpty())
        assertTrue(results.chips.isEmpty())
    }

    // ── Chip dispatch through the unchanged pipeline (AIL-3, Task 5) ───────

    @Test
    fun `web chip routes a search command through the unchanged pipeline`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("cats")
        vm.submitWebSearch("cats")
        advanceUntilIdle()
        // FakeIntentMatcher records the normalized command text it was asked to match.
        assertEquals("search cats", fakeMatcher.receivedInputs.last())
    }

    @Test
    fun `web chip does not double-prefix an already-search-prefixed buffer`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.submitWebSearch("search cats")
        advanceUntilIdle()
        assertEquals("search cats", fakeMatcher.receivedInputs.last())
    }

    @Test
    fun `site chip submits the raw url through the unchanged pipeline`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.submitSite("github.com")
        advanceUntilIdle()
        assertEquals("github.com", fakeMatcher.receivedInputs.last())
    }

    // ── Dev-mode Command console (AIL-3, Task 6) ───────────────────────────

    @Test
    fun `dev sentinel is inert until armed then toggles the console`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        // Un-armed: passes through to the pipeline (matched as a normal, unknown command).
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertFalse(vm.devConsoleOn.value)
        assertEquals("//dev-mode", fakeMatcher.receivedInputs.last())

        // Arm, then toggle on; the sentinel must NOT reach the matcher this time.
        fakeMatcher.reset()
        vm.armDevMode()
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertTrue(vm.devConsoleOn.value)
        assertTrue(fakeMatcher.receivedInputs.isEmpty())

        // Toggle off.
        vm.onCommandSubmitted("//dev-mode")
        advanceUntilIdle()
        assertFalse(vm.devConsoleOn.value)
    }

    @Test
    fun `console records submitted commands only while on`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandSubmitted("open telegram")
        advanceUntilIdle()
        assertTrue(vm.consoleLines.value.isEmpty()) // off → no transcript

        vm.armDevMode()
        vm.onCommandSubmitted("//dev-mode")   // on
        vm.onCommandSubmitted("open telegram")
        advanceUntilIdle()
        assertEquals(listOf("open telegram"), vm.consoleLines.value.map { it.command })
    }

    // ── S2-1 Task 11: learned-resolution decorator + record-on-choice wiring ──────────────────
    // Parity is the hard requirement: with an empty fakeResolutionStore (the default for every
    // buildViewModel()/buildRouterViewModel() call above), ResolveCommandWithPreferenceUseCase.resolve()
    // always returns Outcome(originalOutcome, null) or Outcome(originalOutcome, token) for an ambiguous
    // list — never AutoLaunch — so every assertion in this file above holds byte-for-byte. These tests
    // exercise the NEW behavior: token-set-on-ambiguity, record-on-candidate-tap, no-record-otherwise,
    // and the confident-preference auto-launch directive.

    @Test
    fun `ambiguous submit sets a learning token that a later candidate tap records`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.a", "Maps"),
                InstalledApp("com.b", "Maps"),
            )
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
            fakeMatcher.confidenceToReturn = 0.90f
            val vm = buildViewModel()

            vm.onCommandSubmitted("open maps")
            advanceUntilIdle()
            // Sanity: parity — the ambiguity list itself renders exactly as before, nothing recorded yet.
            assertTrue(vm.commandFeedback.value is CommandFeedback.Ambiguous)
            assertTrue(fakeResolutionStore.observeAll().first().isEmpty())

            vm.onAppClicked(InstalledApp("com.a", "Maps"))
            advanceUntilIdle()

            val stored = fakeResolutionStore.observeAll().first()
            assertEquals(1, stored.size)
            assertEquals(ResolvedTarget.App("com.a"), stored.single().preferredTarget)
            assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "maps"), stored.single().capabilityKey)
        }

    @Test
    fun `clearing the input after an ambiguous submit drops the token so a later tap does not record`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.a", "Maps"),
                InstalledApp("com.b", "Maps"),
            )
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
            fakeMatcher.confidenceToReturn = 0.90f
            val vm = buildViewModel()

            vm.onCommandSubmitted("open maps")
            advanceUntilIdle()

            // User abandons the ambiguity by clearing the field — NO new submit.
            vm.onCommandChanged("")
            advanceUntilIdle()

            // A later tap of a FORMER candidate (grid or suggestion — both funnel here) must not record.
            vm.onAppClicked(InstalledApp("com.a", "Maps"))
            advanceUntilIdle()

            assertTrue(fakeResolutionStore.observeAll().first().isEmpty())
        }

    @Test
    fun `grid tap with no pending token does not record a choice`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
        advanceUntilIdle()

        assertEquals(1, fakeExecutor.callCount) // the launch still happens
        assertTrue(fakeResolutionStore.observeAll().first().isEmpty())
    }

    @Test
    fun `candidate tap on an app the ambiguity list never offered does not record`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.a", "Maps"),
                InstalledApp("com.b", "Maps"),
            )
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
            fakeMatcher.confidenceToReturn = 0.90f
            val vm = buildViewModel()

            vm.onCommandSubmitted("open maps")
            advanceUntilIdle()

            vm.onAppClicked(InstalledApp("org.telegram.messenger", "Telegram"))
            advanceUntilIdle()

            assertTrue(fakeResolutionStore.observeAll().first().isEmpty())
        }

    @Test
    fun `alias hit after unknown launches the declared app directly`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp(
                    packageName = "com.telegram",
                    label = "Telegram",
                    activityName = "com.telegram.MainActivity",
                ),
            )
            fakeMatcher.intentToReturn = LauncherIntent.UnknownIntent(
                originalInput = "work chat",
                reason = "no rule match",
            )
            fakeMatcher.confidenceToReturn = 0.10f
            fakeAliasStore.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 1L))
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.onCommandChanged("Work   Chat")

            vm.onCommandSubmitted("Work   Chat")
            advanceUntilIdle()

            val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
            assertEquals("com.telegram", action.packageName)
            assertEquals("com.telegram.MainActivity", action.activityName)
            assertEquals("", vm.commandInput.value)
            assertEquals(CommandFeedback.None, vm.commandFeedback.value)
            assertTrue(fakeResolutionStore.observeAll().first().isEmpty())
        }

    @Test
    fun `a confident learned preference auto-launches the preferred app and clears input`() =
        runTest(testDispatcher) {
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.a", "Maps"),
                InstalledApp("com.b", "Maps"),
            )
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
            fakeMatcher.confidenceToReturn = 0.90f
            // LAUNCH_APP must be SAFE-risk for AutoResolve eligibility (DefaultActionCatalog agrees).
            val catalog = FakeActionCatalog(listOf(safeDescriptor(ActionIds.LAUNCH_APP)))
            val candidateSet = CandidateSet(listOf(ResolvedTarget.App("com.a"), ResolvedTarget.App("com.b")))
            fakeResolutionStore.upsert(
                ResolutionPreference(
                    capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "maps"),
                    context = ResolutionContext.None,
                    preferredTarget = ResolvedTarget.App("com.a"),
                    evidence = PreferenceEvidence(streak = 3, totalChoices = 3, lastChosenAtEpochMs = 0L),
                    learnedInSetFingerprint = fingerprintOf(candidateSet),
                ),
            )
            val vm = buildViewModel(actionCatalog = catalog)

            vm.onCommandSubmitted("open maps")
            advanceUntilIdle()

            val action = fakeExecutor.executedActions.single() as ExecutableAction.LaunchAppAction
            assertEquals("com.a", action.packageName)
            assertEquals("", vm.commandInput.value)
            // Auto-launch never sets a pending learning token (it is not the app-ambiguity confirm flow).
            vm.onAppClicked(InstalledApp("com.b", "Maps"))
            advanceUntilIdle()
            assertEquals(
                "auto-launch must not itself record a second preference",
                1,
                fakeResolutionStore.observeAll().first().size,
            )
        }
}
