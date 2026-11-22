package com.sidr.launcher.feature.launcher

import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.FormPart
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakePrayerCalculator
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.core.testing.FakePrayerScheduleCache
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.NoOpIntentMatcher
import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.PendingChoice
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.agent.UnresolvedTarget
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
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.SearchTarget
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerContext
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.prayer.UnavailableReason
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabels
import com.sidr.launcher.feature.launcher.agent.StepProvenance
import com.sidr.launcher.feature.launcher.agent.anyStepActed
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // A4′ phase 4, Task 7: what «Выполнить» teaches and «Забыть» forgets. Empty by default, fresh per test.
    private val fakeLearnedPlanStore = FakeLearnedPlanStore()

    // DS-6B Task 9: parity-only default — an unconfigured prayer use case over fresh fakes, so every
    // pre-Task-9 test (which never touches prayerContext) is unaffected. Tests that care about prayer
    // behaviour build and pass their own [GetPrayerContextUseCase] via [buildViewModel]'s parameter.
    private val defaultGetPrayerContext = GetPrayerContextUseCase(
        FakePrayerPreferencesRepository(),
        FakePrayerScheduleCache(),
        FakePrayerCalculator(),
        Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("UTC")),
    )
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

    // Task 11 / A0 made `startAgentSession` a required collaborator of RouteCommandUseCase, and Task
    // 12 made the four agent ports required by LauncherViewModel. One in-memory fixture serves both,
    // and it is deliberately INERT for every pre-existing test: the store starts empty, so
    // `restoreOnStart()` finds nothing, and TemplatePlanner only produces a plan for a decided
    // NoAppFound, which none of the fixtures below ever produces. Every existing assertion therefore
    // still exercises what it was written to exercise.
    private val fakeAgentStore = FakeAgentSessionStore()
    private val agentIds = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("test-agent-${++n}")
    }
    private val agentRegistry = FakeToolRegistry.withA0Tools()
    private val startAgentSession = StartAgentSessionUseCase(
        planner = TemplatePlanner(),
        store = fakeAgentStore,
        ids = agentIds,
        registry = agentRegistry,
    )
    private val runAgentSession = RunAgentSessionUseCase(
        executor = AgentExecutor(agentRegistry, FakeToolExecutor(emptyList()), RuntimeBudget.Default),
        store = fakeAgentStore,
    )

    // AIL-4: the VM routes through RouteCommandUseCase. Этап 4.0 re-pointed this fixture at the new
    // default posture — local-only OFF, a provider configured, online — with a model planner that
    // declines (FakeModelPlanner's default; A4′ phase 3a). That combination yields the FastPath outcome
    // byte-for-byte for everything FastPath decides (and, since task 5 of the acceptance tail, «понял, но так пока
    // не умею» for a command it left undecided), so every existing VM test keeps testing what it was written to
    // test: the VM's CommandOutcome -> CommandFeedback mapping, not the gate. Leaving the provider unconfigured
    // would instead make every unknown command surface UnderstandingNeedsProvider and quietly turn
    // this whole suite into a test of the gate. The gate itself is proven in RouteCommandUseCaseTest.
    private val routeUseCase = RouteCommandUseCase(
        handleUserCommand = useCase,
        modelPlanner = FakeModelPlanner(),
        binder = FakeMentionBinder(),
        featureFlagRepository = fakeFlagRepo,
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(),
        startAgentSession = startAgentSession,
    )

    // The web chip's act, through the same resolver/executor path (the router's confirm card that also
    // used it was deleted in A4′ phase 3a).
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
        getPrayerContext: GetPrayerContextUseCase = defaultGetPrayerContext,
        // Defaulted to the shared fixture, so every pre-existing caller is byte-for-byte unchanged.
        // Only the agent-exit tests below override it, to script what the plan's tools observe and so
        // decide which surface the user is looking at when they leave it.
        runAgent: RunAgentSessionUseCase = runAgentSession,
        // A4′ phase 3a, Task 8: a test may route through its own RouteCommandUseCase (Task 9 scripts it).
        route: CommandRouteStep = CommandRouteStep { routeUseCase.route(it) },
        // A4′ phase 3b, Task 11 fix round 1: the binder a remainder card is bound by, for a test that reads it.
        mentionBinder: FakeMentionBinder = FakeMentionBinder(),
        // A4′ phase 3b, Task 12 review fix: how «Отменить» undoes a step, for the one test that reads the
        // undo review. Defaulted to `undoOver`'s own default, so every other caller is byte-for-byte unchanged.
        undoInverses: ToolInverses = noInverses,
    ) = LauncherViewModel(
        installedAppsRepository = fakeRepo,
        resolveCommand = aliasAwareResolveCommand(
            route = route,
            resolutionStore = resolutionStore,
            actionCatalog = actionCatalog,
        ),
        recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
        recordLearnedPlan = RecordLearnedPlanUseCase(fakeLearnedPlanStore),
        forgetLearnedPlan = ForgetLearnedPlanUseCase(fakeLearnedPlanStore),
        executeAction = executeAction,
        actionExecutor = fakeExecutor,
        usageHistoryRepository = fakeUsageRepo,
        featureFlagRepository = flagRepo,
        userPreferencesRepository = prefsRepo,
        suggestionEngine = suggestionEngine,
        suggestionsCacheRepository = suggestionsCacheRepository,
        speechInputSource = fakeSpeech,
        connectivityChecker = connectivity,
        getPrayerContext = getPrayerContext,
        runAgentSession = runAgent,
        resolveAgentConsent = ResolveConsentUseCase(fakeAgentStore, runAgent),
        cancelAgentSession = CancelAgentSessionUseCase(fakeAgentStore),
        resumeAgentSession = resumeOver(fakeAgentStore, runAgent, agentRegistry),
        undoAgentSession = undoOver(fakeAgentStore, agentRegistry, undoInverses),
        agentSessionStore = fakeAgentStore,
        startAgentSession = startAgentSession,
        mentionBinder = mentionBinder,
        toolRegistry = agentRegistry,
        // A1″ Task 8: no tool in this fixture has a data-authored name, so the map is empty
        // and every step line renders exactly as it did before the dynamic arm existed.
        dynamicToolLabels = DynamicToolLabels { emptyMap() },
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
     * Builds a ViewModel over the REAL route (A4′ phase 3a) whose model planner answers [answer], bound
     * by the fake binder exactly as the model wrote it. The rule matcher is forced to Unknown so the
     * model is always asked (step 7).
     */
    private fun buildBridgeViewModel(
        answer: ModelPlanning,
        online: Boolean = true,
        resolutionStore: FakeResolutionPreferenceStore = fakeResolutionStore,
        // A4′ acceptance tail, task 4: what the started session's tools observe, for the tests of a session that
        // starts without a card. Defaulted to the shared fixture, so every earlier caller is unchanged.
        runAgent: RunAgentSessionUseCase = runAgentSession,
    ): LauncherViewModel {
        fakeMatcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = "nl command", reason = "x")
        fakeMatcher.confidenceToReturn = 0.0f
        val route = RouteCommandUseCase(
            handleUserCommand = useCase,
            modelPlanner = FakeModelPlanner(answer),
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
            // Этап 4.0: the gate refuses to consult the planner unless a provider is configured
            // (fork F4), so the fixture has to configure one to exercise the model path.
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = online),
            startAgentSession = startAgentSession,
        )
        return LauncherViewModel(
            installedAppsRepository = fakeRepo,
            resolveCommand = aliasAwareResolveCommand(
                route = CommandRouteStep { route.route(it) },
                resolutionStore = resolutionStore,
            ),
            recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
            recordLearnedPlan = RecordLearnedPlanUseCase(fakeLearnedPlanStore),
            forgetLearnedPlan = ForgetLearnedPlanUseCase(fakeLearnedPlanStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = fakeFlagRepo,
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = FakeSuggestionEngine(),
            suggestionsCacheRepository = FakeSuggestionsCacheRepository(),
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            getPrayerContext = defaultGetPrayerContext,
            runAgentSession = runAgent,
            resolveAgentConsent = ResolveConsentUseCase(fakeAgentStore, runAgent),
            cancelAgentSession = CancelAgentSessionUseCase(fakeAgentStore),
            resumeAgentSession = resumeOver(fakeAgentStore, runAgent, agentRegistry),
            undoAgentSession = undoOver(fakeAgentStore, agentRegistry),
            agentSessionStore = fakeAgentStore,
            startAgentSession = startAgentSession,
            mentionBinder = FakeMentionBinder(),
            toolRegistry = agentRegistry,
            // A1″ Task 8: no tool in this fixture has a data-authored name, so the map is empty
            // and every step line renders exactly as it did before the dynamic arm existed.
            dynamicToolLabels = DynamicToolLabels { emptyMap() },
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

    // I18N-1 Fix round 1: structurally analogous to AppDrawerViewModel's twin — same
    // InstalledAppsRepository failure taxonomy, same UiState<T>-fixed-to-UiError constraint, same
    // ErrorState render path in LauncherScreen.kt. appListErrorDetail is the parallel typed channel;
    // uiState's own UiError.Message text/retryability above is left byte-identical on purpose.
    @Test
    fun `PermissionDenied exposes the typed AppDrawerError on appListErrorDetail`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.PermissionDenied("QUERY_ALL_PACKAGES")
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(AppDrawerError.PermissionDenied("QUERY_ALL_PACKAGES"), vm.appListErrorDetail.value)
    }

    @Test
    fun `DeviceNotCapable exposes the typed AppDrawerError on appListErrorDetail`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.DeviceNotCapable("nlu")
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(AppDrawerError.DeviceNotCapable("nlu"), vm.appListErrorDetail.value)
    }

    @Test
    fun `appListErrorDetail is null for a NetworkError failure`() = runTest(testDispatcher) {
        fakeRepo.errorToReturn = OperationError.NetworkError(retryable = true)
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(null, vm.appListErrorDetail.value)
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
            recordLearnedPlan = RecordLearnedPlanUseCase(fakeLearnedPlanStore),
            forgetLearnedPlan = ForgetLearnedPlanUseCase(fakeLearnedPlanStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = fakeFlagRepo,
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = FakeSuggestionEngine(),
            suggestionsCacheRepository = FakeSuggestionsCacheRepository(),
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            getPrayerContext = defaultGetPrayerContext,
            runAgentSession = runAgentSession,
            resolveAgentConsent = ResolveConsentUseCase(fakeAgentStore, runAgentSession),
            cancelAgentSession = CancelAgentSessionUseCase(fakeAgentStore),
            resumeAgentSession = resumeOver(fakeAgentStore, runAgentSession, agentRegistry),
            undoAgentSession = undoOver(fakeAgentStore, agentRegistry),
            agentSessionStore = fakeAgentStore,
            startAgentSession = startAgentSession,
            mentionBinder = FakeMentionBinder(),
            toolRegistry = agentRegistry,
            // A1″ Task 8: no tool in this fixture has a data-authored name, so the map is empty
            // and every step line renders exactly as it did before the dynamic arm existed.
            dynamicToolLabels = DynamicToolLabels { emptyMap() },
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
        vm1.onCommandSubmitted("") // Empty outcome → sets a transient feedback EmptyInput hint
        advanceUntilIdle()
        assertTrue(
            "Sanity: feedback should be set on vm1",
            vm1.commandFeedback.value is CommandFeedback.EmptyInput,
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
            // The REAL router runs here (`routeUseCase`: local-only off, a provider, online, a model that declines):
            // task 5 of the tail plan — the model read the command and found no tool, so the line says that, not
            // «unknown command».
            assertEquals(CommandFeedback.Domain(CommandMessage.UnderstoodNoAction), vm.commandFeedback.value)
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
            recordLearnedPlan = RecordLearnedPlanUseCase(fakeLearnedPlanStore),
            forgetLearnedPlan = ForgetLearnedPlanUseCase(fakeLearnedPlanStore),
            executeAction = executeAction,
            actionExecutor = fakeExecutor,
            usageHistoryRepository = fakeUsageRepo,
            featureFlagRepository = FakeFeatureFlagRepository(
                FeatureFlags(aiSuggestionsEnabled = true, usageHistoryEnabled = true),
            ),
            userPreferencesRepository = fakePrefsRepo,
            suggestionEngine = gatedEngine,
            suggestionsCacheRepository = cacheRepo,
            speechInputSource = fakeSpeech,
            connectivityChecker = FakeConnectivityChecker(),
            getPrayerContext = defaultGetPrayerContext,
            runAgentSession = runAgentSession,
            resolveAgentConsent = ResolveConsentUseCase(fakeAgentStore, runAgentSession),
            cancelAgentSession = CancelAgentSessionUseCase(fakeAgentStore),
            resumeAgentSession = resumeOver(fakeAgentStore, runAgentSession, agentRegistry),
            undoAgentSession = undoOver(fakeAgentStore, agentRegistry),
            agentSessionStore = fakeAgentStore,
            startAgentSession = startAgentSession,
            mentionBinder = FakeMentionBinder(),
            toolRegistry = agentRegistry,
            // A1″ Task 8: no tool in this fixture has a data-authored name, so the map is empty
            // and every step line renders exactly as it did before the dynamic arm existed.
            dynamicToolLabels = DynamicToolLabels { emptyMap() },
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
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
        val vm = buildViewModel()

        vm.onAppClicked(InstalledApp("com.missing", "Missing"))
        advanceUntilIdle()

        val feedback = vm.commandFeedback.value
        assertTrue(feedback is CommandFeedback.Failure)
        assertEquals(CommandFailure.CantOpenApp, (feedback as CommandFeedback.Failure).failure)
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
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
        val vm = buildViewModel()
        vm.onAppClicked(InstalledApp("com.missing", "Missing"))
        advanceUntilIdle()
        assertTrue(vm.commandFeedback.value is CommandFeedback.Failure)
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
        fakeExecutor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
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
        assertTrue(vm.commandFeedback.value is CommandFeedback.VoiceError)
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
        val feedback = vm.commandFeedback.value
        assertTrue(feedback is CommandFeedback.VoiceError)
        assertEquals(SpeechRecognitionError.PERMISSION_DENIED, (feedback as CommandFeedback.VoiceError).error)
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

    // ── Chip dispatch (AIL-3, Task 5) ──────────────────────────────────────
    // A4′ phase 1: the WEB chip is an explicit destination, so it dispatches the act itself
    // (ExecuteActionUseCase, the path the deleted router's confirm() used) and never asks the IntentMatcher — which since
    // phase 1 withdraws a search whose query carries a second goal, and would refuse the user's own
    // choice. The SITE chip still submits through the command pipeline.

    @Test
    fun `web chip opens a web search for the buffer without consulting the matcher`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged("cats")
        vm.submitWebSearch("cats")
        advanceUntilIdle()
        assertEquals(
            listOf(ExecutableAction.OpenSearchAction(query = "cats", target = SearchTarget.WEB)),
            fakeExecutor.executedActions,
        )
        assertEquals(emptyList<String>(), fakeMatcher.receivedInputs)
        // Executed → applyOutcome clears the buffer, exactly as a confirmed or typed search does.
        assertEquals("", vm.commandInput.value)
    }

    @Test
    fun `web chip drops a leading search verb instead of searching for it`() = runTest(testDispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.submitWebSearch("search cats")
        advanceUntilIdle()
        assertEquals(
            listOf(ExecutableAction.OpenSearchAction(query = "cats", target = SearchTarget.WEB)),
            fakeExecutor.executedActions,
        )
        assertEquals(emptyList<String>(), fakeMatcher.receivedInputs)
    }

    @Test
    fun `web chip opens a web search for a buffer carrying a second goal`() = runTest(testDispatcher) {
        // The shape FastPath's search arm withdraws since A4′ phase 1 («… и закажи такси»). Tapping WEB
        // is the user choosing the browser for exactly this text, so it is searched as it stands.
        val buffer = "билеты в корванск и закажи такси"
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.onCommandChanged(buffer)
        vm.submitWebSearch(buffer)
        advanceUntilIdle()
        assertEquals(
            listOf(ExecutableAction.OpenSearchAction(query = buffer, target = SearchTarget.WEB)),
            fakeExecutor.executedActions,
        )
        assertEquals(emptyList<String>(), fakeMatcher.receivedInputs)
    }

    @Test
    fun `web chip after an ambiguous submit drops the learning token so a later tap does not record`() =
        runTest(testDispatcher) {
            // Parity with the pre-phase-1 chip, which was a fresh submit and so always replaced the
            // pending token. The chip no longer submits; without its own drop, the token armed by the
            // ambiguity below would survive the search and a later grid tap on a former candidate
            // would be recorded as the user's answer to an ambiguity they walked away from.
            fakeRepo.appsToReturn = listOf(
                InstalledApp("com.a", "Maps"),
                InstalledApp("com.b", "Maps"),
            )
            fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("maps")
            fakeMatcher.confidenceToReturn = 0.90f
            val vm = buildViewModel()

            vm.onCommandSubmitted("open maps")
            advanceUntilIdle()
            assertTrue(vm.commandFeedback.value is CommandFeedback.Ambiguous)

            // The fake answers every text alike; reset it so no matcher answer can re-arm the token —
            // only the chip's own handling decides what happens to it.
            fakeMatcher.reset()
            vm.submitWebSearch("open maps")
            advanceUntilIdle()

            vm.onAppClicked(InstalledApp("com.a", "Maps"))
            advanceUntilIdle()

            assertEquals(emptyList<ResolutionPreference>(), fakeResolutionStore.observeAll().first())
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
    // buildViewModel()/buildBridgeViewModel() call above), ResolveCommandWithPreferenceUseCase.resolve()
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
            // A4′ acceptance tail, task 5: the REAL router no longer hands back `Unknown` once a model is reachable
            // (a decline is «понял, но так пока не умею»), so this fixture feeds the decorator the `Unknown` it
            // answers — the fallback path `ResolveCommandWithAliasUseCase`'s KDoc names. The alias read by the router
            // itself (step 1a) is `RouteCommandUseCaseTest`'s and `AliasBindingGuardTest`'s.
            val vm = buildViewModel(route = CommandRouteStep { CommandOutcome.Unknown("Work   Chat") })
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

    // ── Prayer context (DS-6B Task 9) ───────────────────────────────────────

    private fun istanbulPrayerLocation() = PrayerLocation(
        label = "Istanbul",
        lat2dp = 41.01,
        lon2dp = 28.98,
        tzId = "Europe/Istanbul",
        source = PrayerLocationSource.CITY,
    )

    /** Fajr 04:30, Dhuhr 13:10, Asr 17:05, Maghrib 20:35, Isha 22:15 local on 2026-07-13, Istanbul. */
    private fun istanbulPrayerSchedule(): PrayerDaySchedule {
        val date = LocalDate.of(2026, 7, 13)
        val zone = ZoneId.of("Europe/Istanbul")
        fun epoch(hour: Int, minute: Int) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
        return PrayerDaySchedule(
            dateInLocationTz = date,
            instants = listOf(
                PrayerInstant(PrayerName.FAJR, epoch(4, 30)),
                PrayerInstant(PrayerName.DHUHR, epoch(13, 10)),
                PrayerInstant(PrayerName.ASR, epoch(17, 5)),
                PrayerInstant(PrayerName.MAGHRIB, epoch(20, 35)),
                PrayerInstant(PrayerName.ISHA, epoch(22, 15)),
            ),
        )
    }

    /** 13:00 local Istanbul on 2026-07-13 — Dhuhr (13:10) is the next prayer. */
    private fun istanbulNoonClock(): Clock =
        Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("Europe/Istanbul"))

    /**
     * Starts a real subscriber on [LauncherViewModel.prayerContext] — required to start the
     * `WhileSubscribed` sharing coroutine (the same collection the screen's
     * `collectAsStateWithLifecycle` performs in production), returning a scope to cancel afterwards.
     *
     * Deliberately NOT `backgroundScope` (`TestScope`'s built-in background-job scope): empirically
     * reproduced (in an isolated `stateIn`/`WhileSubscribed` case, independent of this VM) that a
     * `backgroundScope.launch { ... }` issued *after* a prior `advanceUntilIdle()` call in the same
     * `runTest` never actually gets dispatched by a later `advanceUntilIdle()` — the collector body
     * never runs. A plain scope on the same [testDispatcher] does not have this problem.
     */
    private fun subscribeToPrayerContext(vm: LauncherViewModel): CoroutineScope {
        val scope = CoroutineScope(testDispatcher + Job())
        scope.launch { vm.prayerContext.collect {} }
        return scope
    }

    @Test
    fun `prayerContext maps through to Available with the correct nextPrayer once subscribed`() =
        runTest(testDispatcher) {
            val prefs = FakePrayerPreferencesRepository(
                initial = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbulPrayerLocation()),
            )
            val calculator = FakePrayerCalculator().apply {
                resultToReturn = OperationResult.Success(istanbulPrayerSchedule())
            }
            val vm = buildViewModel(
                getPrayerContext = GetPrayerContextUseCase(
                    prefs, FakePrayerScheduleCache(), calculator, istanbulNoonClock(),
                ),
            )

            val collector = subscribeToPrayerContext(vm)
            advanceUntilIdle()

            val available = vm.prayerContext.value as PrayerContext.Available
            assertEquals(PrayerName.DHUHR, available.nextPrayer)
            assertEquals("Europe/Istanbul", available.locationTzId)
            collector.cancel()
        }

    @Test
    fun `no calculation runs before anything subscribes to prayerContext`() = runTest(testDispatcher) {
        val prefs = FakePrayerPreferencesRepository(
            initial = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbulPrayerLocation()),
        )
        val calculator = FakePrayerCalculator().apply {
            resultToReturn = OperationResult.Success(istanbulPrayerSchedule())
        }
        val vm = buildViewModel(
            getPrayerContext = GetPrayerContextUseCase(
                prefs, FakePrayerScheduleCache(), calculator, istanbulNoonClock(),
            ),
        )

        // Drive every other startup path (app list load, suggestions, connectivity, etc.) to
        // completion. prayerContext must stay untouched — nobody has collected it yet.
        advanceUntilIdle()

        assertEquals(
            "construction + startup must perform zero PrayerCalculator calls",
            0,
            calculator.callCount,
        )

        // Sanity check: the flow really is wired — subscribing does eventually calculate.
        val collector = subscribeToPrayerContext(vm)
        advanceUntilIdle()
        assertTrue("expected a calculation once subscribed", calculator.callCount >= 1)
        collector.cancel()
    }

    @Test
    fun `prayerContext is Unavailable NOT_CONFIGURED with no prayer setup`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            getPrayerContext = GetPrayerContextUseCase(
                FakePrayerPreferencesRepository(),
                FakePrayerScheduleCache(),
                FakePrayerCalculator(),
                istanbulNoonClock(),
            ),
        )

        val collector = subscribeToPrayerContext(vm)
        advanceUntilIdle()

        assertEquals(
            PrayerContext.Unavailable(UnavailableReason.NOT_CONFIGURED),
            vm.prayerContext.value,
        )
        collector.cancel()
    }

    /**
     * Task 10 review, finding 1 — the **projection** half of the provenance supply chain.
     *
     * `AgentSessionSurfaceProvenanceTest` holds the rendering from the surface inward and
     * `LauncherScreenAgentProvenanceTest` holds the whole chain through the screen. This one is
     * narrower on purpose: it says which link broke. An `agentToolProvenance` that drops a registered
     * tool, or that loses `level`/`effect` on the way through, fails here by name instead of surfacing
     * three layers away as "a line did not render".
     *
     * Both sides are read off [agentRegistry] rather than written down — the expectation is the
     * registry's own descriptors, so a registry whose tools changed cannot leave this test agreeing
     * with a stale constant (the shape of review finding 2).
     */
    @Test
    fun `agentToolProvenance projects every registered tool's level and effect`() = runTest(testDispatcher) {
        val vm = buildViewModel()

        val provenance = vm.agentToolProvenance

        assertEquals(
            "every registered tool must be projected, or its steps disclose nothing",
            agentRegistry.all().map { it.id }.toSet(),
            provenance.keys,
        )
        agentRegistry.all().forEach { descriptor ->
            assertEquals(
                "provenance for ${descriptor.id.value} must carry the registry's own level/effect",
                StepProvenance(descriptor.level, descriptor.effect),
                provenance[descriptor.id],
            )
        }
        assertTrue(
            "the A0 fixture must declare at least one EXTERNAL tool, or this asserts nothing",
            agentRegistry.all().any { it.effect == ToolEffect.EXTERNAL },
        )
    }

    // ── Leaving the agent surface (owner device acceptance, 2026-09-10) ─────────────────────────
    //
    // The defect this section exists for: the surface went away and the launcher did not come back.
    // `dismissAgentSession` deleted the session and stopped there, so the typed command stayed in the
    // buffer — and "search overtakes" is a pure function of that buffer (`LauncherCommandSession
    // .liveResults`), so the home body (Shahada, date line, prayer strip) never returned. Only a
    // force-stop cleared it. A test that asserted only "the session was deleted" is exactly what let
    // it ship, so every assertion below is on state a user can see.

    /**
     * The A0 shape a user actually reaches: FastPath understands «открой убер», finds no such app, and
     * `RouteCommandUseCase` step (2) starts the two-step plan. [toolResults] scripts what the plan's
     * tools observe, which is what decides which surface the user is left looking at — a terminal one
     * carrying «закрыть», or the consent gate carrying «Отмена».
     */
    private fun agentViewModel(toolResults: List<ToolResult>): LauncherViewModel {
        // A non-empty app list keeps uiState Success, so `inputResults.active == false` really does
        // mean "the home body is what renders" and not "the Empty state is".
        fakeRepo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))
        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent(displayNameQuery = "убер")
        fakeMatcher.confidenceToReturn = 0.95f
        return buildViewModel(
            runAgent = RunAgentSessionUseCase(
                executor = AgentExecutor(
                    agentRegistry,
                    FakeToolExecutor(toolResults),
                    RuntimeBudget.Default,
                ),
                store = fakeAgentStore,
            ),
        )
    }

    /** Types and submits the command, exactly as the screen does. */
    private fun LauncherViewModel.submitAgentCommand(text: String = "открой убер") {
        onCommandChanged(text)
        onCommandSubmitted(text)
    }

    /**
     * What the screen actually draws: `AgentSessionSurface` returns before rendering anything when
     * the session has no title, and that is exactly one shape — `Cancelled` before any step acted
     * (`title()`, which reads the same [anyStepActed] this helper reads). So "no surface" is two shapes,
     * not one — a null session (dismiss) and a session cancelled before any step acted (a refused
     * consent after, at most, a look). A `Cancelled` session in which a step acted draws its finished
     * card (A4′ phase 3b, Task 12 — phase 2's N2), so it counts as visible here, as on the screen.
     */
    private fun LauncherViewModel.agentSurfaceVisible(): Boolean {
        val session = agentSessionState.value ?: return false
        return session.state != ExecutionState.Cancelled || session.anyStepActed()
    }

    @Test
    fun `dismissing the agent surface returns the launcher to its resting home state`() =
        runTest(testDispatcher) {
            val vm = agentViewModel(toolResults = emptyList())
            vm.submitAgentCommand()
            advanceUntilIdle()

            assertNotNull(
                "the fixture must reach the agent surface, or this test asserts nothing",
                vm.agentSessionState.value,
            )
            assertTrue("the surface must be on screen before it is dismissed", vm.agentSurfaceVisible())
            assertTrue(
                "the typed command stays in the buffer while the surface is up (applyOutcome)",
                vm.commandInput.value.isNotEmpty(),
            )
            assertTrue("home is overtaken while the command stands", vm.inputResults.value.active)

            vm.dismissAgentSession()
            advanceUntilIdle()

            assertNull("the session is deleted", vm.agentSessionState.value)
            assertEquals("the command buffer is cleared", "", vm.commandInput.value)
            assertFalse(
                "the home body must be drawn again — this is the assertion the defect would have failed",
                vm.inputResults.value.active,
            )
            assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        }

    @Test
    fun `dismissing twice leaves the launcher at rest`() = runTest(testDispatcher) {
        val vm = agentViewModel(toolResults = emptyList())
        vm.submitAgentCommand()
        advanceUntilIdle()

        vm.dismissAgentSession()
        advanceUntilIdle()
        vm.dismissAgentSession()
        advanceUntilIdle()

        assertNull(vm.agentSessionState.value)
        assertEquals("", vm.commandInput.value)
        assertFalse(vm.inputResults.value.active)
        assertFalse("a second dismiss must not re-arm the consent spinner", vm.agentConfirming.value)
    }

    /**
     * The second way out, and the one the owner reached through «Отмена». A refused consent ends the
     * session `Cancelled`; here it is refused before any step acted (step 0 only looked — `Observed`,
     * «no such app»), and a plan cancelled before any step acted is what `AgentSessionSurface` renders
     * as nothing at all — so the surface leaves the screen down this path too, and the launcher owes the
     * user the same return home. A refusal AFTER a step acted keeps its stopped card instead (A4′ phase
     * 3b, Task 12 — `refusing a step after an earlier one acted keeps the stopped card with its undo`).
     */
    @Test
    fun `refusing consent returns the launcher to its resting home state`() = runTest(testDispatcher) {
        val vm = agentViewModel(
            toolResults = listOf(
                ToolResult.Observed(
                    ObservedFact.APP_NOT_INSTALLED,
                    ToolOutput(mapOf("resolved_query" to "убер")),
                ),
            ),
        )
        vm.submitAgentCommand()
        advanceUntilIdle()

        val awaiting = vm.agentSessionState.value
        assertNotNull("the fixture must reach the consent gate", awaiting)
        assertEquals(
            "the fixture must stop at the consent checkpoint, or this test asserts nothing",
            ExecutionState.AwaitingConsent,
            awaiting!!.state,
        )
        assertTrue(vm.inputResults.value.active)

        vm.denyAgentStep(stepIndex = 1)
        advanceUntilIdle()

        assertFalse("a plan refused before any step acted draws no surface", vm.agentSurfaceVisible())
        assertEquals("the command buffer is cleared", "", vm.commandInput.value)
        assertFalse(
            "the home body must be drawn again after «Отмена» too",
            vm.inputResults.value.active,
        )
    }

    /**
     * A4′ phase 3b, Task 12 review fix I1 — phase 2's N2 on the launcher's deny path, through the real
     * collaborators: a submitted command starts a two-step plan, the real `LauncherAgentSession` attaches
     * and runs it through the real engine, step 0 (a `SAFE`, undoable `launcher_memory` alias) acts, and
     * step 1 (a `CONFIRM` uninstall) stops at its consent card. «Отмена» there is
     * [LauncherViewModel.denyAgentStep]. The stopped card must stay on screen — the session `Cancelled`, not dropped — with
     * «Отменить»'s review holding the alias's inverse, while the buffer is cleared as on every refusal.
     *
     * The plan is built by this test's route, not by a planner: no Android planner produces an undoable
     * step inside a multi-step plan today (`DOC-HMA-3`'s narrowed remainder, controller ruling R12-2). So
     * this holds what the launcher does on that shape, not that a user can reach it. `memory tool` ids are
     * literals for the reason `AgentSessionSurfaceUndoTest` gives.
     */
    @Test
    fun `refusing a step after an earlier one acted keeps the stopped card with its undo`() = runTest(testDispatcher) {
        val setAlias = ToolId("set_app_alias")
        val forgetAlias = ToolId("forget_app_alias")
        val uninstall = ToolId("uninstall_app")
        val registry = FakeToolRegistry(
            listOf(
                ToolDescriptor(
                    id = setAlias,
                    level = ToolLevels.LAUNCHER_MEMORY,
                    effect = ToolEffect.LOCAL,
                    argSchema = listOf("app", "app_label", "phrase").map { ActionArg(it, description = "fixture") },
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = forgetAlias,
                    level = ToolLevels.LAUNCHER_MEMORY,
                    effect = ToolEffect.LOCAL,
                    argSchema = listOf(ActionArg("phrase", description = "fixture")),
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = uninstall,
                    level = ToolLevels.SYSTEM_INTENT,
                    effect = ToolEffect.EXTERNAL,
                    argSchema = listOf("app", "app_label").map { ActionArg(it, description = "fixture") },
                    risk = ActionRiskLevel.CONFIRM,
                    durability = ToolDurability.DURABLE,
                ),
            ),
        )
        val inverses = ToolInverses { forward, _ ->
            if (forward.id == setAlias) {
                Undo.By(ToolInvocation(forgetAlias, mapOf("phrase" to ArgSource.Literal(forward.args.getValue("phrase")))))
            } else {
                Undo.NotUndoable
            }
        }
        val goal = AgentGoal("назови лумору вельтрикс и сотри орвелле", GoalShape.Free("назови лумору вельтрикс и сотри орвелле"))
        val plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(
                        setAlias,
                        mapOf(
                            "app" to ArgSource.Literal("net.fixture.quill"),
                            "app_label" to ArgSource.Literal("Lumora"),
                            "phrase" to ArgSource.Literal("вельтрикс"),
                        ),
                    ),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
                PlanStep(
                    index = 1,
                    invocation = ToolInvocation(
                        uninstall,
                        mapOf("app" to ArgSource.Literal("net.fixture.orvelle"), "app_label" to ArgSource.Literal("Orvelle")),
                    ),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
            ),
        )
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val start = StartAgentSessionUseCase(TemplatePlanner(), fakeAgentStore, agentIds, registry)
        val vm = buildViewModel(
            runAgent = RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), fakeAgentStore),
            route = CommandRouteStep {
                val started = (start.startPlanned(goal, plan) as OperationResult.Success).value
                CommandOutcome.AgentSessionStarted(requireNotNull(started) { "the fixture plan must validate" }.id)
            },
            undoInverses = inverses,
        )
        vm.submitAgentCommand(goal.text)
        advanceUntilIdle()

        val awaiting = vm.agentSessionState.value
        assertEquals("the fixture must stop at step 1's consent card", ExecutionState.AwaitingConsent, awaiting?.state)
        assertEquals("the consent card stands at step 1", 1, awaiting!!.cursor)
        assertTrue("step 0 acted before the refusal, or this is the control's shape", awaiting.observations[0] is ToolResult.Effected)
        assertTrue("the typed command stands until the refusal", vm.commandInput.value.isNotEmpty())

        vm.denyAgentStep(stepIndex = 1)
        advanceUntilIdle()

        val stopped = vm.agentSessionState.value
        assertNotNull("the stopped card's session stays on screen", stopped)
        assertEquals(ExecutionState.Cancelled, stopped!!.state)
        assertTrue("the screen draws it", vm.agentSurfaceVisible())
        assertEquals(
            "«Отменить» on the stopped card undoes the alias step 0 set",
            listOf(StepUndo(0, Undo.By(ToolInvocation(forgetAlias, mapOf("phrase" to ArgSource.Literal("вельтрикс")))))),
            vm.agentUndoReview.value,
        )
        assertEquals("the command buffer is cleared", "", vm.commandInput.value)
        assertEquals("the refused uninstall never ran", listOf(setAlias), tools.invocations.map { it.id })
    }

    /** A one-step store search the MODEL proposed, stopped at its consent card, the process killed. */
    private fun pausedModelPlanForVm() = AgentSession(
        id = AgentSessionId("m1"),
        goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
            ),
        ),
        cursor = 0,
        state = ExecutionState.Paused,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(
            listOf(
                TraceEvent.PlanCreated(1),
                TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL)),
                TraceEvent.SessionPaused,
            ),
        ),
    )

    /**
     * Spec §7.12.3 condition 4: the route's gates are met again. Local-only switched on while the plan
     * sat paused — «Продолжить» now says so honestly instead of acting on the model's old reading.
     */
    @Test
    fun `continuing a model plan after local-only was switched on says so honestly`() = runTest(testDispatcher) {
        fakeAgentStore.save(pausedModelPlanForVm())
        val localOnlyRoute = RouteCommandUseCase(
            handleUserCommand = useCase,
            modelPlanner = FakeModelPlanner(),
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = true)),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(),
            startAgentSession = startAgentSession,
        )
        val vm = buildViewModel(route = CommandRouteStep { localOnlyRoute.route(it) })
        advanceUntilIdle()

        vm.continueAgentSession()
        advanceUntilIdle()

        assertEquals(CommandFeedback.Domain(CommandMessage.UnderstandingLocalOnly), vm.commandFeedback.value)
        assertEquals(null, fakeAgentStore.activeOrNull)
    }

    /**
     * A4′ phase 3b, Task 11 fix round 1 (controller ruling E11-R2; fork 3b-A, B-10) — the launcher's OWN
     * dispatch of a remainder, end to end through the real collaborators: «Продолжить» on a paused plan the
     * model wrote whose first step acted → `Resumption.NeedsCard` → `LauncherAgentSession`'s `onRemainder` →
     * `LauncherCommandSession.showRemainder` → the binder's `rebind` → the interpretation card. The route
     * under it is a REAL `RouteCommandUseCase` whose rule matcher is forced undecided, a provider configured,
     * online — so any submit of the goal's words would reach its model planner; the wrapper counts every
     * command the route is handed. The card must be the remainder's (the search, re-indexed to step 0, as the
     * binder bound it), nothing may reach the route or the model, and the stale row must be gone.
     */
    @Test
    fun `continuing an acted model plan shows its remainder card and asks the model nothing`() = runTest(testDispatcher) {
        val goal = AgentGoal("открой lumora и найди ostravel lectures", GoalShape.Free("открой lumora и найди ostravel lectures"))
        val webSearch = ToolId("web_search")
        fun modelStep(index: Int, tool: ToolId, vararg args: Pair<String, String>) = PlanStep(
            index = index,
            invocation = ToolInvocation(tool, args.associate { (name, value) -> name to ArgSource.Literal(value) }),
            risk = ActionRiskLevel.SAFE,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        )
        fakeAgentStore.save(
            AgentSession(
                id = AgentSessionId("m-acted"),
                goal = goal,
                plan = ExecutionPlan(
                    listOf(
                        modelStep(0, ToolIds.LAUNCH_APP, "app" to "net.fixture.lumora", "query" to "lumora"),
                        modelStep(1, webSearch, "query" to "ostravel lectures"),
                    ),
                ),
                cursor = 1,
                state = ExecutionState.Paused,
                observations = mapOf(0 to ToolResult.Effected()),
                consents = emptyMap(),
                trace = ExecutionTrace(
                    listOf(
                        TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                        TraceEvent.ToolObserved(0, ToolResult.Effected()), TraceEvent.SessionPaused,
                    ),
                ),
            ),
        )
        fakeMatcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = goal.text, reason = "x")
        fakeMatcher.confidenceToReturn = 0.0f
        val model = FakeModelPlanner()
        val bridgeRoute = RouteCommandUseCase(
            handleUserCommand = useCase,
            modelPlanner = model,
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
            startAgentSession = startAgentSession,
        )
        val routed = mutableListOf<String>()
        val binder = FakeMentionBinder()
        val vm = buildViewModel(route = CommandRouteStep { text -> routed += text; bridgeRoute.route(text) }, mentionBinder = binder)
        advanceUntilIdle()
        check(vm.agentSessionState.value?.state == ExecutionState.Paused) { "seed expected Paused, was ${vm.agentSessionState.value?.state}" }

        vm.continueAgentSession()
        advanceUntilIdle()

        assertEquals(
            "the remainder's card, as the binder bound it",
            Interpretation.Act(
                goal = goal,
                steps = listOf(modelStep(0, webSearch, "query" to "ostravel lectures")),
                origins = mapOf(0 to mapOf("query" to ValueOrigin.ModelGenerated)),
                effects = mapOf(0 to ToolEffect.EXTERNAL),
                choices = emptyList(),
            ),
            vm.interpretation.value,
        )
        assertEquals("the remainder was bound once, on the device", 1, binder.rebindCallCount)
        assertEquals("nothing reached the route", emptyList<String>(), routed)
        assertEquals("the model was not asked", 0, model.planCallCount)
        assertEquals("the stale row is gone", null, fakeAgentStore.activeOrNull)
        assertNull("the agent card gave way to the remainder card", vm.agentSessionState.value)
    }

    // ── A4′ phase 3a: the interpretation card (spec §7.12.3) ─────────────────────────────

    private fun storeStepReading() = Interpretation.Act(
        goal = AgentGoal("найди lumora в магазине", GoalShape.Free("найди lumora в магазине")),
        steps = listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("lumora"))),
                risk = ActionRiskLevel.CONFIRM,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
        ),
        origins = mapOf(0 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER))),
        effects = mapOf(0 to ToolEffect.EXTERNAL),
        choices = emptyList(),
    )

    private val tessarOne = InstalledApp("net.fixture.tessar.one", "Tessar")
    private val tessarTwo = InstalledApp("net.fixture.tessar.two", "Tessar")

    private fun launchPickReading(): Interpretation.Act {
        val candidates = CandidateSet(listOf(ResolvedTarget.App(tessarOne.packageName), ResolvedTarget.App(tessarTwo.packageName)))
        return Interpretation.Act(
            goal = AgentGoal("открой tessar", GoalShape.Free("открой tessar")),
            steps = listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("tessar"))),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
            ),
            origins = mapOf(0 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER))),
            effects = mapOf(0 to ToolEffect.EXTERNAL),
            choices = listOf(
                PendingChoice(
                    step = 0,
                    choice = AppChoice(
                        arg = "app",
                        mention = "tessar",
                        candidates = listOf(tessarOne, tessarTwo),
                        learning = ResolutionLearningToken(
                            capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                            context = ResolutionContext.None,
                            candidateSet = candidates,
                            fingerprint = fingerprintOf(candidates),
                            isAppAmbiguityFlow = true,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun answering(outcome: CommandOutcome) = buildViewModel(route = CommandRouteStep { outcome })

    /** Criterion 15, the surface half: the model's reading waits on the card; nothing has started. */
    @Test
    fun `a model's interpretation waits on the card and nothing runs`() = runTest(testDispatcher) {
        val reading = storeStepReading()
        val vm = answering(CommandOutcome.Interpreted(reading))

        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        assertEquals(reading, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
        assertTrue(fakeExecutor.executedActions.isEmpty())
        // advanceUntilIdle has run virtual time well past the 300 ms threshold: a wait left armed after
        // the answer arrived would have fired by now.
        assertFalse("a quick answer never shows the wait", vm.thinking.value)
    }

    /** «Выполнить» starts a session THROUGH THE ENGINE, which gates the CONFIRM step like any other. */
    @Test
    fun `Run starts the session through the engine and the card leaves`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(null, vm.interpretation.value)
        assertEquals(ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
        assertTrue(fakeExecutor.executedActions.isEmpty())
    }

    @Test
    fun `dismissing the card starts nothing`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.dismissInterpretation()
        advanceUntilIdle()

        assertEquals(null, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Criterion 17: a pick binds the step's app — it launches nothing and starts nothing. */
    @Test
    fun `a pick binds the app and launches nothing`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(launchPickReading()))
        vm.onCommandSubmitted("открой tessar")
        advanceUntilIdle()

        vm.chooseInterpretationApp("net.fixture.tessar.two")
        advanceUntilIdle()

        val bound = vm.interpretation.value as Interpretation.Act
        assertEquals(emptyList<PendingChoice>(), bound.choices)
        assertEquals(ArgSource.Literal("net.fixture.tessar.two"), bound.steps.single().invocation.args["app"])
        assertTrue(fakeExecutor.executedActions.isEmpty())
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Plan §0.2 B-3: the learned choice is recorded at «Выполнить» — the launch act — not at the pick. */
    @Test
    fun `Run after a pick records the learned choice once`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(launchPickReading()))
        vm.onCommandSubmitted("открой tessar")
        advanceUntilIdle()
        vm.chooseInterpretationApp("net.fixture.tessar.two")
        advanceUntilIdle()
        assertTrue("nothing is learned at the pick", fakeResolutionStore.observeAll().first().isEmpty())

        vm.runInterpretation()
        advanceUntilIdle()

        val learned = fakeResolutionStore.observeAll().first().single()
        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), learned.capabilityKey)
        assertEquals(ResolvedTarget.App("net.fixture.tessar.two"), learned.preferredTarget)
    }

    /** Review Focus 2: the card describes the old text; an edit abandons it. */
    @Test
    fun `editing the command dismisses the interpretation`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.onCommandChanged("найди что-то другое")

        assertEquals(null, vm.interpretation.value)
    }

    /** Review Focus 1: two taps, one session. */
    @Test
    fun `a second Run tap while the first is still starting starts one session`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        vm.runInterpretation()
        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(1, fakeAgentStore.saved.map { it.id }.distinct().size)
    }

    /** Criterion 19: the wait is visible after a moment, and cancelling it applies nothing. */
    @Test
    fun `the wait shows after a moment and cancelling it applies nothing`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                CommandOutcome.Interpreted(storeStepReading())
            },
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceTimeBy(299)
        runCurrent()
        assertFalse("no wait shown at 299 ms of a slow answer", vm.thinking.value)
        advanceTimeBy(2)
        runCurrent()
        assertTrue(vm.thinking.value)

        vm.cancelThinking()
        advanceUntilIdle()

        assertFalse(vm.thinking.value)
        assertEquals(null, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    /** Review Focus 3 and condition 3: the question arrives prefilled and intact, unsent. */
    @Test
    fun `a question with reserved characters reaches the assistant intact`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.OpenAssistant("почему 2+2=4? & #1"))
        val event = async { vm.navigationEvents.first() }

        vm.onCommandSubmitted("почему 2+2=4? & #1")
        advanceUntilIdle()

        assertEquals(
            NavigationEvent.NavigateTo(
                "assistant?prompt=%D0%BF%D0%BE%D1%87%D0%B5%D0%BC%D1%83%202%2B2%3D4%3F%20%26%20%231",
            ),
            event.await(),
        )
    }

    /** Plan §0.2 B-9: a navigation the model read is shown first, like everything it reads. */
    @Test
    fun `a navigation reading opens Sidr's settings only on Run`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.LAUNCHER_SETTINGS)))
        vm.onCommandSubmitted("открой настройки лаунчера")
        advanceUntilIdle()
        val event = async { vm.navigationEvents.first() }
        runCurrent()
        assertFalse("nothing navigated before Run", event.isCompleted)

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(NavigationEvent.NavigateTo(Routes.Settings.ROUTE), event.await())
        assertEquals("the card leaves once its screen opens", null, vm.interpretation.value)
    }

    /**
     * Final review A1: the card for the model's reading of the app grid promises the app grid. Home draws
     * favourites only — the full list is the drawer's own route — so «Выполнить» must navigate there;
     * clearing the field alone leaves the user on Home with the card gone and no grid.
     */
    @Test
    fun `a navigation reading of the app grid opens the drawer only on Run`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(Interpretation.Navigate(SidrDestination.APP_GRID)))
        // A plain scope on the test dispatcher, not `backgroundScope` — see subscribeToPrayerContext.
        val navigations = mutableListOf<NavigationEvent>()
        val collector = CoroutineScope(testDispatcher + Job())
        collector.launch { vm.navigationEvents.collect { navigations += it } }
        vm.onCommandSubmitted("покажи все приложения")
        advanceUntilIdle()
        assertEquals("nothing navigated before Run", emptyList<NavigationEvent>(), navigations)

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(
            "Run opens the drawer, and only the drawer",
            listOf<NavigationEvent>(NavigationEvent.NavigateTo(Routes.AppDrawer.ROUTE)),
            navigations,
        )
        assertEquals("the card leaves once its screen opens", null, vm.interpretation.value)
        collector.cancel()
    }

    /**
     * Fork 3b-F: FastPath's «apps» promises the app grid too, and Home draws favourites only — the full list
     * is the drawer's own route. Until Task 7 FastPath's `ShowApps` only cleared the field (3a's final review
     * A1 fixed the bridge's card alone); now the one outcome opens the drawer, exactly once, on every path —
     * the card's «Выполнить» above goes through the same branch.
     */
    @Test
    fun `FastPath show apps opens the app grid`() = runTest(testDispatcher) {
        fakeMatcher.intentToReturn = LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS)
        fakeMatcher.confidenceToReturn = 0.95f
        val vm = buildViewModel()
        // A plain scope on the test dispatcher, not `backgroundScope` — see subscribeToPrayerContext.
        val navigations = mutableListOf<NavigationEvent>()
        val collector = CoroutineScope(testDispatcher + Job())
        collector.launch { vm.navigationEvents.collect { navigations += it } }

        vm.onCommandChanged("show apps")
        vm.onCommandSubmitted("show apps")
        advanceUntilIdle()

        assertEquals(listOf<NavigationEvent>(NavigationEvent.NavigateTo(Routes.AppDrawer.ROUTE)), navigations)
        assertEquals("", vm.commandInput.value)
        assertEquals(CommandFeedback.None, vm.commandFeedback.value)
        assertEquals(0, fakeExecutor.callCount)
        collector.cancel()
    }

    /**
     * Forks 3b-D/3b-G on FastPath (B-2, B-7): «миравину» is a form of the installed «Миравин», so FastPath
     * asks «Вы имели в виду «Миравин»?» over a one-app list — nothing runs before the tap, and the tap
     * LAUNCHES, because the act it confirms is a launch. Controller ruling E4-P7/E5-P6: the did-you-mean
     * title is for ONE offered app only; the control — the edge pair «Плинта» / «Плинт», both offered for
     * «плинту» — keeps the list's own header. The pair's labels share a substring on purpose; every value
     * is compared by equality.
     */
    @Test
    fun `a FastPath word form shows did-you-mean`() = runTest(testDispatcher) {
        val miravin = InstalledApp("net.fixture.miravin", "Миравин")
        val plinta = InstalledApp("net.fixture.plinta", "Плинта")
        val plint = InstalledApp("net.fixture.wexdor", "Плинт")
        fakeRepo.appsToReturn = listOf(InstalledApp("net.fixture.lumora", "Lumora"), miravin, plinta, plint)
        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("миравину")
        fakeMatcher.confidenceToReturn = 0.90f
        val vm = buildViewModel()

        vm.onCommandSubmitted("открой миравину")
        advanceUntilIdle()

        val offered = vm.commandFeedback.value
        assertEquals(CommandFeedback.Ambiguous(listOf(miravin), confirm = true), offered)
        assertEquals(FeedbackText(R.string.launcher_interpretation_did_you_mean, listOf("Миравин")), feedbackText(offered))
        assertEquals("nothing runs before the tap", 0, fakeExecutor.callCount)

        vm.onAppClicked(miravin)
        advanceUntilIdle()
        assertEquals(listOf<ExecutableAction>(ExecutableAction.LaunchAppAction("net.fixture.miravin")), fakeExecutor.executedActions)
        // B-3 (review M1): the confirming tap is recorded exactly like a pick among several — one row, keyed
        // on the name typed (`LearnedChoiceKeys.launch("миравину", …)`), pointing at the confirmed app.
        assertEquals(
            listOf(CapabilityKey(ActionIds.LAUNCH_APP, "миравину") to ResolvedTarget.App("net.fixture.miravin")),
            fakeResolutionStore.observeAll().first().map { it.capabilityKey to it.preferredTarget },
        )

        fakeMatcher.intentToReturn = LauncherIntent.LaunchAppIntent("плинту")
        vm.onCommandSubmitted("открой плинту")
        advanceUntilIdle()

        val pair = vm.commandFeedback.value
        assertEquals(CommandFeedback.Ambiguous(listOf(plinta, plint), confirm = true), pair)
        assertEquals(FeedbackText(R.string.launcher_feedback_ambiguous), feedbackText(pair))
    }

    /** «Отмена» on a pick card learns nothing — not even from a later tap on a former candidate. */
    @Test
    fun `a dismissed pick card leaves nothing to learn from a later tap`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(launchPickReading()))
        vm.onCommandSubmitted("открой tessar")
        advanceUntilIdle()

        vm.dismissInterpretation()
        vm.onAppClicked(tessarTwo)
        advanceUntilIdle()

        assertEquals("the tap itself launched", 1, fakeExecutor.executedActions.size)
        assertTrue("nothing is learned from a dismissed card", fakeResolutionStore.observeAll().first().isEmpty())
    }

    // ── A4′ phase 3b, Task 10 (B-19): picks owed in step order, each learned under its own mention ──────

    private val pellixOne = InstalledApp("net.fixture.pellix.one", "Pellix")
    private val pellixTwo = InstalledApp("net.fixture.pellix.two", "Pellix")

    /** A launch step as the binder writes it: the user's words in `query` — runnable once its app is bound. */
    private fun launchStep(index: Int, words: String) = PlanStep(
        index = index,
        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal(words))),
        risk = ActionRiskLevel.SAFE,
        precondition = StepPrecondition.None,
        rationale = StepRationale.MODEL_PROPOSED,
    )

    /** A launch pick owed on [step], carrying ITS OWN learning token keyed on [mention] (B-19, the binder's half). */
    private fun launchPick(step: Int, mention: String, vararg apps: InstalledApp): PendingChoice {
        val candidates = CandidateSet(apps.map { ResolvedTarget.App(it.packageName) })
        return PendingChoice(
            step = step,
            choice = AppChoice(
                arg = "app",
                mention = mention,
                candidates = apps.toList(),
                learning = ResolutionLearningToken(
                    capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, mention),
                    context = ResolutionContext.None,
                    candidateSet = candidates,
                    fingerprint = fingerprintOf(candidates),
                    isAppAmbiguityFlow = true,
                ),
            ),
        )
    }

    private fun severalPicksReading(goal: String, steps: List<PlanStep>, choices: List<PendingChoice>) = Interpretation.Act(
        goal = AgentGoal(goal, GoalShape.Free(goal)),
        steps = steps,
        origins = steps.associate { step -> step.index to step.invocation.args.keys.associateWith { ValueOrigin.SaidBy(PrincipalId.USER) } },
        effects = steps.associate { it.index to ToolEffect.EXTERNAL },
        choices = choices,
    )

    private fun tessarAndPellixReading() = severalPicksReading(
        goal = "открой tessar и pellix",
        steps = listOf(launchStep(0, "tessar"), launchStep(1, "pellix")),
        choices = listOf(launchPick(0, "tessar", tessarOne, tessarTwo), launchPick(1, "pellix", pellixOne, pellixTwo)),
    )

    private suspend fun learnedRows() =
        fakeResolutionStore.observeAll().first().map { it.capabilityKey to it.preferredTarget }

    /**
     * Review Focus 3 (fork 3b-B). Picks are owed one after the other — the second is the head only once the
     * first is answered — and «Выполнить» starts nothing while any is owed. Each launch step carries its own
     * `query`, so the plan Run is refused here WOULD be runnable if forced (`FakeToolRegistry`'s `launch_app`
     * requires `query` only — the E9-M1 lesson): «nothing starts» is the gate's doing, not the registry's.
     * The second scenario dismisses at the SECOND pick: the card goes, nothing starts, nothing is learned.
     */
    @Test
    fun `two picks are owed one after the other and Run waits for both`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(tessarAndPellixReading()))

        // Dismissed at the second pick.
        vm.onCommandSubmitted("открой tessar и pellix")
        advanceUntilIdle()
        vm.chooseInterpretationApp(tessarTwo.packageName)
        advanceUntilIdle()
        assertEquals(
            "after the first pick the second is the head",
            listOf(1),
            (vm.interpretation.value as Interpretation.Act).choices.map { it.step },
        )
        vm.dismissInterpretation()
        advanceUntilIdle()
        assertEquals("dismissed at the second pick, the card is gone", null, vm.interpretation.value)
        assertTrue("dismissed at the second pick, nothing starts", fakeAgentStore.saved.isEmpty())
        assertEquals("dismissed at the second pick, nothing is learned", emptyList<Any>(), learnedRows())

        // Run waits for both.
        vm.onCommandSubmitted("открой tessar и pellix")
        advanceUntilIdle()
        vm.chooseInterpretationApp(tessarTwo.packageName)
        advanceUntilIdle()
        vm.runInterpretation()
        advanceUntilIdle()
        assertTrue("Run with a pick still owed starts nothing", fakeAgentStore.saved.isEmpty())
        assertEquals(
            "Run with a pick still owed leaves the card up, the second pick still owed",
            listOf(1),
            (vm.interpretation.value as Interpretation.Act).choices.map { it.step },
        )

        vm.chooseInterpretationApp(pellixOne.packageName)
        advanceUntilIdle()
        vm.runInterpretation()
        advanceUntilIdle()
        assertEquals("after both picks Run starts one session", 1, fakeAgentStore.saved.map { it.id }.distinct().size)
        assertEquals(
            "both steps bound, each to its own pick",
            listOf(
                ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("tessar"), "app" to ArgSource.Literal(tessarTwo.packageName))),
                ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("pellix"), "app" to ArgSource.Literal(pellixOne.packageName))),
            ),
            fakeAgentStore.saved.first().plan.steps.map { it.invocation },
        )
    }

    /** Controller ruling E10-P2 (B-19): two picks over different apps — each recorded under its own mention, in step order. */
    @Test
    fun `each pick is learned under its own mention, in step order`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(tessarAndPellixReading()))
        vm.onCommandSubmitted("открой tessar и pellix")
        advanceUntilIdle()
        vm.chooseInterpretationApp(tessarTwo.packageName)
        vm.chooseInterpretationApp(pellixOne.packageName)
        advanceUntilIdle()
        assertEquals("nothing is learned at the picks", emptyList<Any>(), learnedRows())

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CapabilityKey(ActionIds.LAUNCH_APP, "tessar") to ResolvedTarget.App(tessarTwo.packageName),
                CapabilityKey(ActionIds.LAUNCH_APP, "pellix") to ResolvedTarget.App(pellixOne.packageName),
            ),
            learnedRows(),
        )
    }

    /**
     * Controller ruling E10-P2 (B-19): two mentions over the SAME two apps — «tessar» and the Cyrillic «тессар»
     * (different scripts, no shared substring). One token for both would record the second pick under the
     * first mention's key; each pick is learned under its own.
     */
    @Test
    fun `picks over the same apps are learned each under its own mention`() = runTest(testDispatcher) {
        val vm = answering(
            CommandOutcome.Interpreted(
                severalPicksReading(
                    goal = "открой tessar и тессар",
                    steps = listOf(launchStep(0, "tessar"), launchStep(1, "тессар")),
                    choices = listOf(launchPick(0, "tessar", tessarOne, tessarTwo), launchPick(1, "тессар", tessarOne, tessarTwo)),
                ),
            ),
        )
        vm.onCommandSubmitted("открой tessar и тессар")
        advanceUntilIdle()
        vm.chooseInterpretationApp(tessarTwo.packageName)
        vm.chooseInterpretationApp(tessarOne.packageName)
        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(
            listOf(
                CapabilityKey(ActionIds.LAUNCH_APP, "tessar") to ResolvedTarget.App(tessarTwo.packageName),
                CapabilityKey(ActionIds.LAUNCH_APP, "тессар") to ResolvedTarget.App(tessarOne.packageName),
            ),
            learnedRows(),
        )
    }

    /**
     * Controller ruling E10-P2 (B-19): a head `uninstall_app` pick has no learning token (`learning = null` —
     * a removal is not a launch). It must not stand in for the later launch pick's token: that pick is learned,
     * and it is the only row.
     */
    @Test
    fun `a head uninstall pick leaves the later launch's pick learned`() = runTest(testDispatcher) {
        val uninstall = PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolId("uninstall_app"), mapOf("app_label" to ArgSource.Literal("tessar"))),
            risk = ActionRiskLevel.CONFIRM,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        )
        val vm = answering(
            CommandOutcome.Interpreted(
                severalPicksReading(
                    goal = "удали tessar и открой pellix",
                    steps = listOf(uninstall, launchStep(1, "pellix")),
                    choices = listOf(
                        PendingChoice(step = 0, choice = AppChoice(arg = "app", mention = "tessar", candidates = listOf(tessarOne, tessarTwo), learning = null)),
                        launchPick(1, "pellix", pellixOne, pellixTwo),
                    ),
                ),
            ),
        )
        vm.onCommandSubmitted("удали tessar и открой pellix")
        advanceUntilIdle()
        vm.chooseInterpretationApp(tessarOne.packageName)
        vm.chooseInterpretationApp(pellixTwo.packageName)
        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(
            listOf(CapabilityKey(ActionIds.LAUNCH_APP, "pellix") to ResolvedTarget.App(pellixTwo.packageName)),
            learnedRows(),
        )
    }

    /** FastPath's own «assistant» carries no question: the assistant opens empty, on its bare route. */
    @Test
    fun `an assistant command without a question opens the bare assistant route`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.OpenAssistant(null))
        val event = async { vm.navigationEvents.first() }

        vm.onCommandSubmitted("assistant")
        advanceUntilIdle()

        assertEquals(NavigationEvent.NavigateTo(Routes.Assistant.ROUTE), event.await())
    }

    /**
     * A command submitted while the model is still thinking about an earlier one supersedes it: the
     * earlier answer, arriving later, must not put a card up over the newer command's outcome.
     */
    @Test
    fun `a newer command supersedes a slow answer still on its way`() = runTest(testDispatcher) {
        val vm = buildViewModel(
            route = CommandRouteStep { text ->
                if (text == "найди lumora в магазине") {
                    delay(10_000)
                    CommandOutcome.Interpreted(storeStepReading())
                } else {
                    CommandOutcome.LowConfidence
                }
            },
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("the first command is still waiting on the model", vm.thinking.value)

        vm.onCommandSubmitted("найди что-то другое")
        advanceUntilIdle()

        assertEquals(null, vm.interpretation.value)
        assertEquals(CommandFeedback.LowConfidence, vm.commandFeedback.value)
    }

    /**
     * Review Focus 2, in flight (Task 9 review I1): an edit made while the model is still thinking
     * abandons the command. Its late answer would otherwise put up a card for text the user has already
     * left — one tap from running on a SAFE step.
     */
    @Test
    fun `editing the command during the wait abandons it`() = runTest(testDispatcher) {
        var answered = false
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                answered = true
                CommandOutcome.Interpreted(storeStepReading())
            },
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("the command is still waiting on the model", vm.thinking.value)

        vm.onCommandChanged("найди что-то другое")
        advanceUntilIdle()

        assertEquals("no card for the abandoned text", null, vm.interpretation.value)
        assertFalse(vm.thinking.value)
        assertFalse("the model call itself was cancelled, not merely ignored", answered)
        assertEquals("the edit stands", "найди что-то другое", vm.commandInput.value)
    }

    /** Task 9 review m1: dictating a new command abandons the card standing for the old one, as typing does. */
    @Test
    fun `dictating a new command dismisses the standing interpretation`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(storeStepReading()))
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()
        assertEquals("the card is up before the dictation", storeStepReading(), vm.interpretation.value)

        fakeSpeech.scriptedStates = listOf(
            SpeechRecognitionState.Ready,
            SpeechRecognitionState.Partial("открой tess"),
        )
        vm.startVoiceInput()
        advanceUntilIdle()

        assertEquals("открой tess", vm.commandInput.value)
        assertEquals(null, vm.interpretation.value)
    }

    /**
     * Final review M1 (Review Focus 2's class): a recognizer may deliver a Final with no Partial before
     * it. That is still a new command, so the card standing for the old one goes at the Final — not when
     * the new command's answer lands, which for the model is seconds later, the old card one tap from
     * running all that while. The dictated text is still the command that runs.
     */
    @Test
    fun `a dictated Final with no Partial dismisses the standing interpretation before its answer lands`() =
        runTest(testDispatcher) {
            val asked = mutableListOf<String>()
            val vm = buildViewModel(
                route = CommandRouteStep { text ->
                    asked += text
                    if (text == "найди lumora в магазине") {
                        CommandOutcome.Interpreted(storeStepReading())
                    } else {
                        delay(10_000)
                        CommandOutcome.LowConfidence
                    }
                },
            )
            vm.onCommandSubmitted("найди lumora в магазине")
            advanceUntilIdle()
            assertEquals("the card is up before the dictation", storeStepReading(), vm.interpretation.value)

            fakeSpeech.scriptedStates = listOf(
                SpeechRecognitionState.Ready,
                SpeechRecognitionState.Final("открой vemblic"),
            )
            vm.startVoiceInput()
            advanceTimeBy(1_000)
            runCurrent()
            assertTrue("the dictated command is still waiting on its answer", vm.thinking.value)

            assertEquals("no card for the old command while the new one waits", null, vm.interpretation.value)

            advanceUntilIdle()
            assertEquals("the dictated text is the command that ran", listOf("найди lumora в магазине", "открой vemblic"), asked)
            assertEquals(CommandFeedback.LowConfidence, vm.commandFeedback.value)
        }

    /**
     * End to end through the real route: the model's reading waits on the card; «Выполнить» starts it.
     *
     * **Since the D3 second amendment (task 4 of the tail plan) the store search's text is the MODEL's** — the same
     * search in the user's words would start at once (`a model session started without a card shows the model mark`)
     * — so the card is owed, and the property under test is unchanged. A session the user started from the card
     * carries no model mark: the card already showed how the command was understood.
     */
    @Test
    fun `a model plan through the real route waits on the card, and Run starts an engine session`() = runTest(testDispatcher) {
        val vm = buildBridgeViewModel(
            answer = ModelPlanning.Proposed(
                listOf(
                    ProposedStep(
                        tool = ToolIds.PLAY_STORE_SEARCH,
                        args = mapOf("query" to "lumora"),
                        origins = mapOf("query" to ValueOrigin.ModelGenerated),
                        risk = ActionRiskLevel.CONFIRM,
                        effect = ToolEffect.EXTERNAL,
                    ),
                ),
            ),
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()
        assertTrue(vm.interpretation.value is Interpretation.Act)
        assertTrue("nothing started before «Выполнить»", fakeAgentStore.saved.isEmpty())

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals(ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
        assertFalse("a session started from the card carries no model mark", vm.agentProposedByModel.value)
    }

    /**
     * Task 9 review m2, ruled into Task 11: the WEB chip is the user choosing the browser while the model
     * is still thinking about the command. Its slow answer — the assistant with a question, which would
     * navigate away from the search the user chose — must not land: the command is abandoned and the
     * model call with it, and the wait goes at the tap.
     */
    @Test
    fun `the web chip supersedes a slow answer still on its way`() = runTest(testDispatcher) {
        var answered = false
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                answered = true
                CommandOutcome.OpenAssistant("почему небо синее")
            },
        )
        val navigation = async { vm.navigationEvents.first() }
        vm.onCommandSubmitted("почему небо синее")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("the command is still waiting on the model", vm.thinking.value)

        vm.submitWebSearch("ostravel lectures")
        runCurrent()
        assertFalse("the wait goes at the tap, not when the abandoned answer would have come", vm.thinking.value)
        advanceUntilIdle()

        assertEquals(
            "the chip's own search ran",
            listOf(ExecutableAction.OpenSearchAction(query = "ostravel lectures", target = SearchTarget.WEB)),
            fakeExecutor.executedActions,
        )
        assertFalse("the model call itself was cancelled, not merely ignored", answered)
        assertFalse("the abandoned answer navigated nowhere", navigation.isCompleted)
        navigation.cancel()
    }

    /**
     * Task 9 review m2, ruled into Task 11: a tap on an app while the model is still thinking opens that
     * app — and the slow answer, a card for the abandoned command, must not stand over it afterwards.
     */
    @Test
    fun `a tap on an app supersedes a slow answer still on its way`() = runTest(testDispatcher) {
        var answered = false
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                answered = true
                CommandOutcome.Interpreted(storeStepReading())
            },
        )
        vm.onCommandSubmitted("найди lumora в магазине")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("the command is still waiting on the model", vm.thinking.value)

        vm.onAppClicked(InstalledApp("net.fixture.vemblic", "Vemblic"))
        runCurrent()
        assertFalse("the wait goes at the tap, not when the abandoned answer would have come", vm.thinking.value)
        advanceUntilIdle()

        assertEquals(
            "the tap's own launch ran",
            listOf(ExecutableAction.LaunchAppAction("net.fixture.vemblic", null)),
            fakeExecutor.executedActions,
        )
        assertFalse("the model call itself was cancelled, not merely ignored", answered)
        assertEquals("no card for the abandoned command", null, vm.interpretation.value)
    }

    /**
     * Task 11 ruling on hole H8 (the rest of Task 9 review m2): a suggestion that opens a screen while the
     * model is still thinking is the user going somewhere else. The slow answer — the assistant with a
     * question — must not navigate away from the screen the user chose; the wait goes at the tap.
     */
    @Test
    fun `a suggestion that opens a screen supersedes a slow answer still on its way`() = runTest(testDispatcher) {
        var answered = false
        val vm = buildViewModel(
            route = CommandRouteStep {
                delay(10_000)
                answered = true
                CommandOutcome.OpenAssistant("почему небо синее")
            },
        )
        // A plain scope on the test dispatcher, not `backgroundScope` — see subscribeToPrayerContext.
        val navigations = mutableListOf<NavigationEvent>()
        val collector = CoroutineScope(testDispatcher + Job())
        collector.launch { vm.navigationEvents.collect { navigations += it } }
        vm.onCommandSubmitted("почему небо синее")
        advanceTimeBy(1_000)
        runCurrent()
        assertTrue("the command is still waiting on the model", vm.thinking.value)

        vm.onSuggestionClicked(
            Suggestion(label = "Settings", actionId = Routes.Settings.ROUTE, source = SuggestionSource.TIME_OF_DAY, score = 1.0),
        )
        runCurrent()
        assertFalse("the wait goes at the tap, not when the abandoned answer would have come", vm.thinking.value)
        advanceUntilIdle()

        assertEquals(
            "only the screen the user chose",
            listOf<NavigationEvent>(NavigationEvent.NavigateTo(Routes.Settings.ROUTE)),
            navigations,
        )
        assertFalse("the model call itself was cancelled, not merely ignored", answered)
        collector.cancel()
    }

    // ── A4′ phase 3b, Task 5: a 2b target pick — R14-39 closed (plan §0.2 B-7) ──────────────────

    /**
     * A pending `launch_app` step — the one tool in [agentRegistry] that declares an `app` argument —
     * whose target is the user's to pick. The step carries the fixture risk `CONFIRM` so the engine stops
     * at consent and nothing is invoked, as `uninstall_app`'s would; which tool it is, is incidental.
     */
    private fun tessarTarget(command: String = "удали приложение tessar") = UnresolvedTarget(
        goal = AgentGoal(command, GoalShape.Free(command)),
        pending = PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP), ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.GOAL_DIRECT),
        arg = "app",
        labelArg = "query",
        sort = ArgSorts.APP,
        words = "tessar",
        candidates = listOf(tessarOne, tessarTwo),
        confirm = false,
    )

    /** A model that answers only after ten seconds: a command that reached it would show the wait at 300 ms. */
    private class SlowModel : ModelPlanner {
        var asked = 0
            private set

        override suspend fun plan(command: String): ModelPlanning {
            asked++
            delay(10_000)
            return ModelPlanning.Declined
        }
    }

    /**
     * The REAL route over a deterministic planner that claims exactly [target]'s goal, as
     * `ToolMatchPlanner` claims a tool's text — so the outcome the surface receives is the one step 2b
     * produces, and a 2b that fell through would reach [model].
     */
    private fun claimingRoute(target: UnresolvedTarget, model: ModelPlanner, localOnly: Boolean = false): RouteCommandUseCase {
        val claims = object : Planner {
            override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult =
                if (request.goal == target.goal) PlanningResult.Unresolved(target) else PlanningResult.NoPlan
        }
        return RouteCommandUseCase(
            handleUserCommand = HandleUserCommandUseCase(
                matcher = NoOpIntentMatcher(),
                resolver = IntentActionResolver(fakeRepo),
                executor = fakeExecutor,
                confidencePolicy = DefaultIntentConfidencePolicy(),
                recordingScope = CoroutineScope(testDispatcher + SupervisorJob()),
            ),
            modelPlanner = model,
            binder = FakeMentionBinder(),
            // The route's own flags: local-only for a test that must not reach the model (controller ruling K2).
            featureFlagRepository = if (localOnly) FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true, localOnlyMode = true)) else fakeFlagRepo,
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(),
            startAgentSession = StartAgentSessionUseCase(claims, fakeAgentStore, agentIds, agentRegistry),
        )
    }

    /**
     * Spec criterion 15, second half; B-7: a 2b pick is the device's own question — never an
     * interpretation card, never FastPath's launch list. The tap binds the package and the user's words
     * through the domain's one binding function and starts the session directly, through the engine,
     * which stops the `CONFIRM` step at its consent card.
     */
    @Test
    fun `a 2b target pick starts the agent session directly`() = runTest(testDispatcher) {
        val target = tessarTarget()
        val model = SlowModel()
        val vm = buildViewModel(route = CommandRouteStep { claimingRoute(target, model).route(it) })

        vm.onCommandSubmitted("удали приложение tessar")
        advanceUntilIdle()

        assertEquals(target, vm.targetPick.value)
        assertEquals("a 2b pick is not an interpretation card", null, vm.interpretation.value)
        assertTrue("nothing starts before the tap", fakeAgentStore.saved.isEmpty())

        vm.chooseTarget("net.fixture.tessar.two")
        advanceUntilIdle()

        assertEquals(null, vm.targetPick.value)
        assertEquals("the tap starts the session with no card in between", null, vm.interpretation.value)
        assertEquals(target.goal, fakeAgentStore.saved.first().goal)
        assertEquals(
            ToolInvocation(
                ToolIds.LAUNCH_APP,
                mapOf("app" to ArgSource.Literal("net.fixture.tessar.two"), "query" to ArgSource.Literal("tessar")),
            ),
            fakeAgentStore.saved.first().plan.steps.single().invocation,
        )
        assertEquals(ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
        assertEquals("the model was never asked", 0, model.asked)
        assertTrue(fakeExecutor.executedActions.isEmpty())
    }

    /**
     * R14-39 closed, seen from the surface: a target nothing on the device answers to reads «нет такого
     * приложения» in the user's words at once. Had step 2b fallen through, [SlowModel] would still be
     * thinking a second later — the wait would be up and the line empty.
     *
     * **In local-only mode since controller ruling K2** (A4′ acceptance tail, task 2; review I1): online, with a
     * provider, an empty app target is asked of the model first, so the wait is the model's there; without the
     * model the device answers at once, as before.
     */
    @Test
    fun `a 2b target that is not found reads no such app with no wait`() = runTest(testDispatcher) {
        val target = tessarTarget("удали приложение вельтрикс").copy(words = "вельтрикс", candidates = emptyList())
        val model = SlowModel()
        val vm = buildViewModel(route = CommandRouteStep { claimingRoute(target, model, localOnly = true).route(it) })

        vm.onCommandSubmitted("удали приложение вельтрикс")
        advanceTimeBy(1_000)

        assertEquals(CommandFeedback.Domain(CommandMessage.NoAppFound("вельтрикс")), vm.commandFeedback.value)
        assertFalse("the device answered — there was nothing to wait for", vm.thinking.value)
        assertEquals(0, model.asked)
        assertEquals(null, vm.targetPick.value)
        assertEquals(null, vm.interpretation.value)
        assertTrue(fakeAgentStore.saved.isEmpty())
    }

    // ── A4′ phase 4, Task 7: «Выполнить» teaches; a learned session keeps its mark (plan §0.2 B-7, B-8, B-10) ──

    private val webSearchTool = ToolId("web_search")

    /** «поищи …» → `web_search(query = slot 0)`: what «поищи орвелин» teaches (the phase's fixture set). */
    private val searchForm = CommandForm(listOf(FormPart.Word("поищи"), FormPart.Slot))
    private val searchShape = LearnedPlanShape(
        searchForm,
        PlanTemplate(listOf(TemplateStep(webSearchTool, mapOf("query" to TemplateArg.Slot(0))))),
    )

    /** The bound reading of one `SAFE` web search — of the user's own word by default. */
    private fun searchReading(
        command: String = "поищи орвелин",
        query: String = "орвелин",
        origin: ValueOrigin = ValueOrigin.SaidBy(PrincipalId.USER),
    ) = Interpretation.Act(
        goal = AgentGoal(command, GoalShape.Free(command)),
        steps = listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(webSearchTool, mapOf("query" to ArgSource.Literal(query))),
                risk = ActionRiskLevel.SAFE,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
        ),
        origins = mapOf(0 to mapOf("query" to origin)),
        effects = mapOf(0 to ToolEffect.EXTERNAL),
        choices = emptyList(),
    )

    /**
     * B-8, the launcher's half: «Выполнить» on a fresh reading records what it teaches — after the session
     * started, never at the card — and a second tap on the same card records nothing more: the card, and
     * what it would teach, are already down.
     */
    @Test
    fun `Run on a fresh model card records what it teaches once the session started`() = runTest(testDispatcher) {
        val vm = answering(CommandOutcome.Interpreted(searchReading(), learnable = searchShape))
        vm.onCommandSubmitted("поищи орвелин")
        advanceUntilIdle()
        assertEquals("nothing is learned at the card", emptyMap<String, LearnedPlan>(), fakeLearnedPlanStore.rows)

        vm.runInterpretation()
        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals("the session started", listOf(searchReading().goal), fakeAgentStore.saved.map { it.goal }.distinct())
        assertEquals(setOf("поищи …"), fakeLearnedPlanStore.rows.keys)
        assertEquals(searchShape, fakeLearnedPlanStore.rows["поищи …"]?.shape)
        assertEquals("one Run, one confirmation", 1, fakeLearnedPlanStore.rows["поищи …"]?.streak)
    }

    /**
     * B-8: nothing is learned from a card that did not run. Three cards, each carrying a lesson a Run would
     * record: one dismissed; one whose «Выполнить» could not start its session (the store refused the save);
     * and a fresh card that a REMAINDER card replaced (phase 3b B-10 — a remainder is not a fresh reading and
     * teaches nothing; its Run starts a session and must not record the lesson of the card it replaced).
     */
    @Test
    fun `a dismissed card, a session that did not start and a remainder card record nothing`() = runTest(testDispatcher) {
        val remainderGoal = AgentGoal("открой lumora и найди ostravel lectures", GoalShape.Free("открой lumora и найди ostravel lectures"))
        fun modelStep(index: Int, tool: ToolId, vararg args: Pair<String, String>) = PlanStep(
            index = index,
            invocation = ToolInvocation(tool, args.associate { (name, value) -> name to ArgSource.Literal(value) }),
            risk = ActionRiskLevel.SAFE,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        )
        // A plan the model wrote whose first step acted, paused by a process death: «Продолжить» on it brings
        // its remainder back as the interpretation card (`Resumption.NeedsCard`).
        fakeAgentStore.save(
            AgentSession(
                id = AgentSessionId("m-acted"),
                goal = remainderGoal,
                plan = ExecutionPlan(
                    listOf(
                        modelStep(0, ToolIds.LAUNCH_APP, "app" to "net.fixture.lumora", "query" to "lumora"),
                        modelStep(1, webSearchTool, "query" to "ostravel lectures"),
                    ),
                ),
                cursor = 1,
                state = ExecutionState.Paused,
                observations = mapOf(0 to ToolResult.Effected()),
                consents = emptyMap(),
                trace = ExecutionTrace(
                    listOf(
                        TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP),
                        TraceEvent.ToolObserved(0, ToolResult.Effected()), TraceEvent.SessionPaused,
                    ),
                ),
            ),
        )
        val vm = buildViewModel(route = CommandRouteStep { CommandOutcome.Interpreted(searchReading(), learnable = searchShape) })
        advanceUntilIdle()
        check(vm.agentSessionState.value?.state == ExecutionState.Paused) { "seed expected Paused, was ${vm.agentSessionState.value?.state}" }

        // (1) Dismissed.
        vm.onCommandSubmitted("поищи орвелин")
        advanceUntilIdle()
        assertEquals(searchReading(), vm.interpretation.value)
        vm.dismissInterpretation()
        advanceUntilIdle()
        assertEquals("a dismissed card teaches nothing", emptyMap<String, LearnedPlan>(), fakeLearnedPlanStore.rows)

        // (2) «Выполнить», and the session does not start.
        vm.onCommandSubmitted("поищи орвелин")
        advanceUntilIdle()
        fakeAgentStore.failNextSave = true
        vm.runInterpretation()
        advanceUntilIdle()
        assertEquals("the start failed", CommandFeedback.Failure(CommandFailure.Generic), vm.commandFeedback.value)
        assertEquals("a session that did not start teaches nothing", emptyMap<String, LearnedPlan>(), fakeLearnedPlanStore.rows)

        // (3) A fresh card, replaced by a remainder card, and the remainder run.
        vm.onCommandSubmitted("поищи орвелин")
        advanceUntilIdle()
        vm.continueAgentSession()
        advanceUntilIdle()
        assertEquals("the remainder's card stands", remainderGoal, (vm.interpretation.value as Interpretation.Act).goal)
        vm.runInterpretation()
        advanceUntilIdle()
        assertEquals("the remainder's session started", remainderGoal, fakeAgentStore.saved.last().goal)
        assertEquals("a remainder card teaches nothing", emptyMap<String, LearnedPlan>(), fakeLearnedPlanStore.rows)
    }

    /**
     * B-7 (fork 4-5; review S4): a learned command that still needed the card — here its web search sends the
     * model's own words off the phone — keeps its mark through «Выполнить»: the session on screen says it is a
     * learned command. Nothing is recorded: a replay card carries nothing to teach.
     */
    @Test
    fun `a learned command that needed the card keeps its mark after Run`() = runTest(testDispatcher) {
        val form = CommandForm.parse("поищи что нибудь про орвелин")
        val reading = searchReading(command = "поищи что-нибудь про орвелин", query = "орвелин история", origin = ValueOrigin.ModelGenerated)
        val vm = answering(CommandOutcome.Interpreted(reading, learned = form))
        vm.onCommandSubmitted("поищи что-нибудь про орвелин")
        advanceUntilIdle()

        vm.runInterpretation()
        advanceUntilIdle()

        assertEquals("the learned session is the one on screen", reading.goal, vm.agentSessionState.value?.goal)
        assertEquals(form, vm.agentLearnedCommand.value)
        assertEquals("a replay card teaches nothing", emptyMap<String, LearnedPlan>(), fakeLearnedPlanStore.rows)
    }

    // ── A4′ acceptance tail, task 4: one Sidr card — a fresh reading with nothing to show starts at once ──────────────

    /** One step the model proposed in the user's own words — no pick, no model text going out: no card is owed. */
    private fun usersOwn(tool: ToolId, arg: String, value: String, risk: ActionRiskLevel) = ModelPlanning.Proposed(
        listOf(ProposedStep(tool, mapOf(arg to value), mapOf(arg to ValueOrigin.SaidBy(PrincipalId.USER)), risk, ToolEffect.EXTERNAL)),
    )

    /** The engine over [registry], answering each call in turn with [results] — the run a started session gets. */
    private fun runOver(registry: ToolRegistry, tools: FakeToolExecutor) =
        RunAgentSessionUseCase(AgentExecutor(registry, tools, RuntimeBudget.Default), fakeAgentStore)

    /**
     * D3, second amendment + controller ruling K4, the launcher's half — end to end through the REAL route: a fresh
     * reading with nothing to show (a store search in the user's own words) starts at once, no card is drawn, and the
     * session's card carries the model's mark. The step is `CONFIRM`: the engine stops it at its consent card, so the
     * session stays on screen with the mark, and nothing is executed. The mark goes with the session.
     */
    @Test
    fun `a model session started without a card shows the model mark`() = runTest(testDispatcher) {
        val vm = buildBridgeViewModel(answer = usersOwn(ToolIds.PLAY_STORE_SEARCH, "query", "lumora", ActionRiskLevel.CONFIRM))

        vm.onCommandSubmitted("найди lumora в магазине")
        advanceUntilIdle()

        assertNull("no interpretation card was drawn", vm.interpretation.value)
        assertEquals("the engine stopped the risky step at its consent card", ExecutionState.AwaitingConsent, vm.agentSessionState.value?.state)
        assertTrue("the session's card says the model proposed it", vm.agentProposedByModel.value)
        assertTrue("nothing was executed", fakeExecutor.executedActions.isEmpty())

        vm.dismissAgentSession()
        advanceUntilIdle()

        assertNull(vm.agentSessionState.value)
        assertFalse("the mark goes with its session", vm.agentProposedByModel.value)
    }

    /**
     * Owner answer I12 «простая — закрывается сама»: a session of ONE `SAFE` step that acted (`Effected`), asked no
     * consent and has nothing to undo takes its card off Home when it finishes — as FastPath's `Executed` leaves
     * nothing — and the launcher is put back at rest (the buffer cleared, so the home body returns; the 2026-09-10
     * device finding). Here a web search the model read in the user's words, started at once through the real route.
     */
    @Test
    fun `a simple finished session closes itself`() = runTest(testDispatcher) {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected()))
        val vm = buildBridgeViewModel(
            answer = usersOwn(ToolId("web_search"), "query", "ostravel lectures", ActionRiskLevel.SAFE),
            runAgent = runOver(agentRegistry, tools),
        )

        vm.onCommandSubmitted("найди ostravel lectures")
        advanceUntilIdle()

        assertEquals("the search ran through the engine", listOf(ToolId("web_search")), tools.invocations.map { it.id })
        assertNull("the finished simple session took its card down", vm.agentSessionState.value)
        assertFalse(vm.agentProposedByModel.value)
        assertEquals("the launcher is back at rest", "", vm.commandInput.value)
        assertNull(vm.interpretation.value)
    }

    /** I12: a single `SAFE` step that can be undone keeps its card — «Отменить» is on it. */
    @Test
    fun `a finished session with an undo stays`() = runTest(testDispatcher) {
        val setAlias = ToolId("set_app_alias")
        val forgetAlias = ToolId("forget_app_alias")
        val registry = FakeToolRegistry(
            listOf(
                ToolDescriptor(
                    id = setAlias,
                    level = ToolLevels.LAUNCHER_MEMORY,
                    effect = ToolEffect.LOCAL,
                    argSchema = listOf("app", "phrase").map { ActionArg(it, description = "fixture") },
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = forgetAlias,
                    level = ToolLevels.LAUNCHER_MEMORY,
                    effect = ToolEffect.LOCAL,
                    argSchema = listOf(ActionArg("phrase", description = "fixture")),
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
            ),
        )
        val inverses = ToolInverses { forward, _ ->
            if (forward.id == setAlias) {
                Undo.By(ToolInvocation(forgetAlias, mapOf("phrase" to ArgSource.Literal(forward.args.getValue("phrase")))))
            } else {
                Undo.NotUndoable
            }
        }
        val goal = AgentGoal("назови лумору вельтрикс", GoalShape.Free("назови лумору вельтрикс"))
        val plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(
                        setAlias,
                        mapOf("app" to ArgSource.Literal("net.fixture.quill"), "phrase" to ArgSource.Literal("вельтрикс")),
                    ),
                    risk = ActionRiskLevel.SAFE,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.MODEL_PROPOSED,
                ),
            ),
        )
        val start = StartAgentSessionUseCase(TemplatePlanner(), fakeAgentStore, agentIds, registry)
        val vm = buildViewModel(
            runAgent = runOver(registry, FakeToolExecutor(listOf(ToolResult.Effected()))),
            route = CommandRouteStep {
                val started = requireNotNull((start.startPlanned(goal, plan) as OperationResult.Success).value) { "the fixture plan must validate" }
                CommandOutcome.AgentSessionStarted(started.id, proposedByModel = true)
            },
            undoInverses = inverses,
        )

        vm.submitAgentCommand(goal.text)
        advanceUntilIdle()

        assertEquals(ExecutionState.Completed, vm.agentSessionState.value?.state)
        assertTrue("«Отменить» has something to undo", vm.agentUndoReview.value.any { it.undo is Undo.By })
        assertTrue("the card keeps its mark", vm.agentProposedByModel.value)
    }

    /** I12: a plan of two `SAFE` steps that both acted keeps its card — only a single step closes itself. */
    @Test
    fun `a finished plan of two steps stays`() = runTest(testDispatcher) {
        val tools = FakeToolExecutor(listOf(ToolResult.Effected(), ToolResult.Effected()))
        val vm = buildBridgeViewModel(
            answer = ModelPlanning.Proposed(
                listOf("ostravel lectures", "vorandel maps").map { query ->
                    ProposedStep(ToolId("web_search"), mapOf("query" to query), mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)), ActionRiskLevel.SAFE, ToolEffect.EXTERNAL)
                },
            ),
            runAgent = runOver(agentRegistry, tools),
        )

        vm.onCommandSubmitted("найди ostravel lectures и vorandel maps")
        advanceUntilIdle()

        assertEquals("both steps ran", 2, tools.invocations.size)
        assertEquals(ExecutionState.Completed, vm.agentSessionState.value?.state)
        assertTrue(vm.agentProposedByModel.value)
    }

    /**
     * I12, read strictly: a single `SAFE` step that did NOT act — it looked, and found the app gone
     * (`APP_NOT_INSTALLED`, `Observed`) — keeps its card: closing it would hide that nothing opened. Named in the task
     * report (the owner's rule says «по завершении»; the session is `Completed`, the act is not).
     */
    @Test
    fun `a finished session that only looked stays`() = runTest(testDispatcher) {
        val tools = FakeToolExecutor(listOf(ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "vorandel")))))
        val vm = buildBridgeViewModel(
            answer = usersOwn(ToolIds.LAUNCH_APP, "query", "vorandel", ActionRiskLevel.SAFE),
            runAgent = runOver(agentRegistry, tools),
        )

        vm.onCommandSubmitted("открой vorandel")
        advanceUntilIdle()

        assertEquals(1, tools.invocations.size)
        assertEquals(ExecutionState.Completed, vm.agentSessionState.value?.state)
    }
}
