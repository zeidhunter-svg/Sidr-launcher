package com.sidr.launcher.feature.launcher

import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
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
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.intent.CommandMessage
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.ModelUnavailableReason
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 3a, Task 10: **the screen half of the interpretation card.** `InterpretationSurfaceTest`
 * renders the two areas in isolation, so it cannot see the twelve lines of [LauncherScreen] that put
 * them on screen and connect them to the ViewModel; this class starts one layer further out, at the
 * real screen over a real [LauncherViewModel] (the fixture is [LauncherScreenAgentProvenanceTest]'s),
 * with a stub only at the model's side of the route (`CommandRouteStep`).
 *
 * Three tests rather than one because each needs a different state of the same command session — a
 * card up, a model call still pending, a refusal on the feedback line — and a single ViewModel cannot
 * hold them together without a script of three commands whose failure would not say which link broke.
 *
 * Tall window, same reason as [LauncherScreenAgentProvenanceTest]'s undo test: Home's column is taller
 * than Robolectric's default root, and a click on a zero-height target does nothing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp")
class LauncherScreenInterpretationTest {

    @get:Rule val compose = createComposeRule()

    private val scheduler = TestCoroutineScheduler()
    private val dispatcher = UnconfinedTestDispatcher(scheduler)
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

    private fun reading() = Interpretation.Act(
        goal = AgentGoal("kelmora goal", GoalShape.Free("kelmora goal")),
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.Literal("ostravel lectures"))),
            risk = ActionRiskLevel.CONFIRM,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
        effect = ToolEffect.EXTERNAL,
        choice = null,
    )

    private fun show(route: CommandRouteStep): LauncherViewModel {
        val vm = buildViewModel(route)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()
        return vm
    }

    /** Wiring 1: the card is drawn from the ViewModel's flow, and its Run reaches `runInterpretation`. */
    @Test
    fun `an interpretation on the screen is drawn, and Run starts the session`() {
        val vm = show(CommandRouteStep { CommandOutcome.Interpreted(reading()) })

        vm.onCommandSubmitted("kelmora goal")
        compose.waitForIdle()
        compose.onNodeWithText("Here is how I understood it").assertExists()
        compose.onNodeWithText("Find ostravel lectures in the app store").assertExists()
        assertTrue("drawing the card starts nothing", agentStore.saved.isEmpty())

        compose.onNodeWithText("Run").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("Here is how I understood it").assertDoesNotExist()
        assertEquals("Run reached the engine and saved one session", 1, agentStore.saved.map { it.id }.distinct().size)
    }

    /** Wiring 2: while a command waits on the model the wait is drawn, and its «Cancel» cancels it. */
    @Test
    fun `while the model is thinking the wait is drawn, and Cancel takes it down`() {
        val vm = show(CommandRouteStep { awaitCancellation() })

        vm.onCommandSubmitted("kelmora goal")
        compose.waitForIdle()
        compose.onNodeWithText("Working out what you meant…").assertDoesNotExist()

        scheduler.advanceTimeBy(301)
        scheduler.runCurrent()
        compose.waitForIdle()
        compose.onNodeWithText("Working out what you meant…").assertExists()

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Working out what you meant…").assertDoesNotExist()
    }

    /** Wiring 3: a missing or refused key puts its sentence AND the provider button on the screen. */
    @Test
    fun `a missing or refused key shows its sentence and the provider setup button`() {
        val vm = show(
            CommandRouteStep {
                CommandOutcome.Message(CommandMessage.UnderstandingModelUnavailable(ModelUnavailableReason.CREDENTIALS))
            },
        )

        vm.onCommandSubmitted("kelmora goal")
        compose.waitForIdle()

        compose.onNodeWithText("The AI provider key is missing or was not accepted.").assertExists()
        compose.onNodeWithText("Set up provider").assertExists()
    }

    /**
     * The clarify question is the MODEL's text, and the sentence around it says so — the line reads as
     * its question, not as the launcher speaking. Compared whole and literally (English, the default
     * test locale): a value resolved from the same resource would pass a resource reverted to bare `%1$s`.
     */
    @Test
    fun `a model question on the feedback line is marked as the model's`() {
        val vm = show(CommandRouteStep { CommandOutcome.Message(CommandMessage.Verbatim("почему небо синее")) })

        vm.onCommandSubmitted("kelmora goal")
        compose.waitForIdle()

        compose.onNodeWithText("The model asks: почему небо синее").assertExists()
    }

    /**
     * Task 11 fix round 1 (review m2): the ASK chip is the user taking the command to the assistant while
     * the model is still reading it. The chip's own navigation happens and the command is abandoned — its
     * slow answer (the assistant with the question) would otherwise queue a SECOND navigation, which fires
     * into the assistant again the next time Home collects its events. Driven through the real screen,
     * because the chip's wiring lives in [LauncherScreen], not in the ViewModel.
     */
    @Test
    fun `the ASK chip abandons a command still waiting on the model`() {
        var answered = false
        val vm = show(
            CommandRouteStep {
                delay(10_000)
                answered = true
                CommandOutcome.OpenAssistant("почему небо синее")
            },
        )
        val navigations = mutableListOf<NavigationEvent>()
        val collector = CoroutineScope(dispatcher + Job())
        collector.launch { vm.navigationEvents.collect { navigations += it } }
        vm.onCommandChanged("почему небо синее")
        vm.onCommandSubmitted("почему небо синее")
        scheduler.advanceTimeBy(1_000)
        scheduler.runCurrent()
        compose.waitForIdle()
        assertTrue("the command is still waiting on the model", vm.thinking.value)

        compose.onNodeWithText("ASK").performClick()
        compose.waitForIdle()
        assertFalse("the wait goes at the tap, not when the abandoned answer would have come", vm.thinking.value)

        scheduler.advanceTimeBy(20_000)
        scheduler.runCurrent()
        compose.waitForIdle()

        assertEquals(
            "only the chip's own navigation",
            listOf<NavigationEvent>(NavigationEvent.NavigateTo(Routes.Assistant.routeFor(Uri.encode("почему небо синее")))),
            navigations,
        )
        assertFalse("the model call itself was cancelled, not merely ignored", answered)
        collector.cancel()
    }

    private fun buildViewModel(route: CommandRouteStep): LauncherViewModel {
        val fakeRepo = FakeInstalledAppsRepository()
        val fakeExecutor = FakeActionExecutor()
        val agentIds = object : AgentSessionIdFactory {
            private var n = 0
            override fun newId() = AgentSessionId("test-agent-${++n}")
        }
        val runAgent = RunAgentSessionUseCase(
            executor = AgentExecutor(agentRegistry, FakeToolExecutor(emptyList()), RuntimeBudget.Default),
            store = agentStore,
        )
        val startAgentSession = StartAgentSessionUseCase(
            planner = TemplatePlanner(),
            store = agentStore,
            ids = agentIds,
            registry = agentRegistry,
        )
        val resolutionStore = FakeResolutionPreferenceStore()
        val preferenceResolver = ResolveCommandWithPreferenceUseCase(
            route = route,
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
            resumeAgentSession = resumeOver(agentStore, runAgent, agentRegistry),
            undoAgentSession = undoOver(agentStore, agentRegistry, noInverses),
            agentSessionStore = agentStore,
            startAgentSession = startAgentSession,
            toolRegistry = agentRegistry,
            dynamicToolLabels = com.sidr.launcher.feature.launcher.agent.DynamicToolLabels { emptyMap() },
            ioDispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }
}
