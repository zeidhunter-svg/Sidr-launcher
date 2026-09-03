package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakePrayerCalculator
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.core.testing.FakePrayerScheduleCache
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.FakeSpeechInputSource
import com.sidr.launcher.core.testing.FakeSuggestionEngine
import com.sidr.launcher.core.testing.FakeSuggestionsCacheRepository
import com.sidr.launcher.core.testing.FakeToolExecutor
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.core.testing.FakeUserPreferencesRepository
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.trace.ExecutionTrace
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task 10 review, finding 1. **The link that actually delivers provenance to a user.**
 *
 * `AgentSessionSurfaceProvenanceTest` proves the disclosure renders *from [AgentSessionSurface]
 * inward*, i.e. given a populated `toolProvenance` map. It cannot see where that map comes from, and
 * the map is exactly the part no test held: deleting the `toolProvenance = viewModel.agentToolProvenance`
 * argument at `LauncherScreen`'s call site (or emptying [LauncherViewModel.agentToolProvenance]'s
 * projection of the registry) left the whole 1285-test suite green while no user saw a line.
 *
 * This test therefore starts one layer further out — at [LauncherScreen] over a real
 * [LauncherViewModel] — so the chain `ToolRegistry -> agentToolProvenance -> LauncherScreen ->
 * AgentSessionSurface -> rendered line` is held end to end by one assertion. The fixture is
 * [LauncherScreenPrayerStripTest]'s, which is itself [LauncherViewModelTest]'s; the only additions are
 * a store seeded with a restorable session and a registry whose tools are `EXTERNAL`.
 *
 * **Why a session already `Paused`.** `LauncherAgentSession.restoreOnStart` presents a restored
 * session as `Paused` and, for one already `Paused`, hands it back untouched — so the surface is on
 * screen with no command typed and no engine run, and the plan list (which is where the per-step
 * disclosure lives) is drawn. `AwaitingConsent` would exercise the consent card's own line instead;
 * that half is covered by `AgentSessionSurfaceProvenanceTest` and is not the link under test here.
 *
 * **Why the A0 plan and not a hand-built one.** The plan comes from the real [TemplatePlanner] over
 * [FakeToolRegistry.withA0Tools] — the same registry instance the ViewModel is given — so the expected
 * count of disclosure lines is a function of the tools the registry actually declares, not of a map
 * this test wrote for itself (the sibling of review finding 2).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScreenAgentProvenanceTest {

    @get:Rule val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    private val agentRegistry = FakeToolRegistry.withA0Tools()
    private val agentStore = FakeAgentSessionStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** The A0 two-step plan, already `Paused`, exactly as a session that outlived its process reads. */
    private suspend fun seedPausedA0Session(): AgentSession {
        val goal = AgentGoal("открой убер", GoalShape.AppNotInstalled("убер"))
        val planned = TemplatePlanner().plan(goal, agentRegistry)
        check(planned is PlanningResult.Planned)
        val session = AgentSession(
            id = AgentSessionId("restored-1"),
            goal = goal,
            plan = planned.plan,
            cursor = 0,
            state = ExecutionState.Paused,
            observations = emptyMap(),
            consents = emptyMap(),
            trace = ExecutionTrace(emptyList()),
        )
        agentStore.save(session)
        return session
    }

    private fun buildViewModel(): LauncherViewModel {
        val fakeRepo = FakeInstalledAppsRepository()
        val fakeExecutor = FakeActionExecutor()
        val useCase = HandleUserCommandUseCase(
            matcher = FakeIntentMatcher(),
            resolver = IntentActionResolver(fakeRepo),
            executor = fakeExecutor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        val agentIds = object : AgentSessionIdFactory {
            private var n = 0
            override fun newId() = AgentSessionId("test-agent-${++n}")
        }
        val runAgent = RunAgentSessionUseCase(
            executor = AgentExecutor(agentRegistry, FakeToolExecutor(emptyList()), RuntimeBudget.Default),
            store = agentStore,
        )
        val routeUseCase = RouteCommandUseCase(
            handleUserCommand = useCase,
            planner = FakeCommandPlanner(),
            catalog = FakeActionCatalog(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(),
            startAgentSession = StartAgentSessionUseCase(
                planner = TemplatePlanner(),
                store = agentStore,
                ids = agentIds,
                registry = agentRegistry,
            ),
        )
        val resolutionStore = FakeResolutionPreferenceStore()
        val preferenceResolver = ResolveCommandWithPreferenceUseCase(
            route = CommandRouteStep { routeUseCase.route(it) },
            store = resolutionStore,
            policy = DefaultResolutionPreferencePolicy(),
            catalog = FakeActionCatalog(),
        )
        val resolveCommand = ResolveCommandWithAliasUseCase(
            inner = ResolvedCommandStep { rawInput -> preferenceResolver.resolve(rawInput) },
            store = FakeAliasStore(),
            installedApps = fakeRepo,
        )
        return LauncherViewModel(
            installedAppsRepository = fakeRepo,
            resolveCommand = resolveCommand,
            recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
            executeAction = ExecuteActionUseCase(
                resolver = IntentActionResolver(fakeRepo),
                executor = fakeExecutor,
            ),
            actionExecutor = fakeExecutor,
            actionCatalog = FakeActionCatalog(),
            usageHistoryRepository = FakeUsageHistoryRepository(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            userPreferencesRepository = FakeUserPreferencesRepository(),
            suggestionEngine = FakeSuggestionEngine(),
            suggestionsCacheRepository = FakeSuggestionsCacheRepository(),
            speechInputSource = FakeSpeechInputSource(),
            connectivityChecker = FakeConnectivityChecker(),
            getPrayerContext = GetPrayerContextUseCase(
                FakePrayerPreferencesRepository(),
                FakePrayerScheduleCache(),
                FakePrayerCalculator(),
                Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("UTC")),
            ),
            runAgentSession = runAgent,
            resolveAgentConsent = ResolveConsentUseCase(agentStore, runAgent),
            cancelAgentSession = CancelAgentSessionUseCase(agentStore),
            agentSessionStore = agentStore,
            toolRegistry = agentRegistry,
            ioDispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    /**
     * The whole supply chain in one assertion: both A0 tools are `EXTERNAL` in the registry the screen's
     * ViewModel holds, so both restored plan rows must carry the disclosure line under them.
     *
     * The expected count is **derived from the registry**, not written down: it is the number of plan
     * steps whose tool the registry declares `EXTERNAL`, which is the same predicate
     * `provenanceLabelFor` applies. A registry whose A0 tools stopped being `EXTERNAL` would lower the
     * expectation *and* the rendering together — which is why the count alone would not be enough, and
     * `assertTrue` below refuses a vacuous zero.
     */
    @Test
    fun `the screen supplies the surface with the registry's provenance, so a restored plan discloses it`() {
        val session = runBlocking { seedPausedA0Session() }
        val expected = session.plan.steps.count { step ->
            agentRegistry.find(step.invocation.id)?.effect == ToolEffect.EXTERNAL
        }
        assertTrue(
            "the fixture must contain at least one EXTERNAL step, or this test asserts nothing",
            expected > 0,
        )
        val vm = buildViewModel()

        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        // "SIDR · EXTERNAL" is launcher_tool_level_in_app: both A0 descriptors are IN_APP + EXTERNAL.
        compose.onAllNodesWithContentDescription("source SIDR · EXTERNAL")
            .assertCountEquals(expected)
    }
}
