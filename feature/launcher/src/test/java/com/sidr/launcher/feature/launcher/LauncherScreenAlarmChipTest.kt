package com.sidr.launcher.feature.launcher

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeNextAlarmSource
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
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CancelAgentSessionUseCase
import com.sidr.launcher.domain.agent.ResolveConsentUseCase
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.alarm.NextAlarm
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.ResolveCommandWithAliasUseCase
import com.sidr.launcher.domain.memory.alias.ResolvedCommandStep
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.resolution.CommandRouteStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.feature.launcher.agent.DynamicToolLabels
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
 * A4′ acceptance tail, task 8 (owner ruling 5: а; K13): the next-alarm chip's PLACE on Home, by semantics — Home has
 * no goldens and gets none. The chip stands at the very start of the date line, the date stays centred on that line
 * and never runs under the chip (plan review M9), and the whole line — chip with it — hides while the user types, as
 * the date line always has (`HomeDateLine`'s `visible`). The real [LauncherScreen] over a real [LauncherViewModel]
 * wired from `:core:testing` fakes, as `LauncherScreenPrayerStripTest` builds it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScreenAlarmChipTest {

    @get:Rule val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the chip sits at the start of the date line and hides while typing`() {
        val vm = buildViewModel(FakeNextAlarmSource(NextAlarm(ALARM_AT)))
        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        val time = DateFormat.getTimeFormat(context).format(Date(ALARM_AT))
        val chip = compose.onNodeWithContentDescription("Next alarm: $time").assertIsDisplayed()
        compose.onNodeWithText("⏰ $time").assertIsDisplayed()

        val line = compose.onNodeWithTag(HOME_DATE_LINE_TEST_TAG).getBoundsInRoot()
        val chipBounds = chip.getBoundsInRoot()
        val date = compose.onNodeWithText(" · ", substring = true).getBoundsInRoot()
        val tolerance = 1.dp

        assertTrue("the chip starts where the date line starts: $chipBounds in $line", abs(chipBounds.left - line.left) <= tolerance)
        assertTrue("the chip is ON the date line: $chipBounds in $line", chipBounds.top >= line.top - tolerance && chipBounds.bottom <= line.bottom + tolerance)
        assertTrue("the date never runs under the chip: $date vs $chipBounds", date.left >= chipBounds.right - tolerance)
        assertTrue(
            "the date stays centred on its line: $date in $line",
            abs((date.left + date.right) / 2 - (line.left + line.right) / 2) <= tolerance,
        )

        vm.onCommandChanged("telegram")
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Next alarm:", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(HOME_DATE_LINE_TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun `no alarm - no chip, the date line as before`() {
        val vm = buildViewModel(FakeNextAlarmSource())
        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Next alarm:", substring = true).assertDoesNotExist()
        compose.onNodeWithText(" · ", substring = true).assertIsDisplayed()
    }

    private fun buildViewModel(nextAlarms: FakeNextAlarmSource): LauncherViewModel {
        val fakeRepo = FakeInstalledAppsRepository()
        val fakeExecutor = FakeActionExecutor()
        val useCase = HandleUserCommandUseCase(
            matcher = FakeIntentMatcher(),
            resolver = IntentActionResolver(fakeRepo),
            executor = fakeExecutor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        // The same inert agent fixture as LauncherScreenPrayerStripTest: an empty store, nothing restored.
        val agentStore = FakeAgentSessionStore()
        val agentRegistry = FakeToolRegistry.withA0Tools()
        val agentIds = object : AgentSessionIdFactory {
            private var n = 0
            override fun newId() = AgentSessionId("test-agent-${++n}")
        }
        val runAgent = RunAgentSessionUseCase(
            executor = AgentExecutor(agentRegistry, FakeToolExecutor(emptyList()), RuntimeBudget.Default),
            store = agentStore,
        )
        val startAgentSession = StartAgentSessionUseCase(TemplatePlanner(), agentStore, agentIds, agentRegistry)
        val routeUseCase = RouteCommandUseCase(
            handleUserCommand = useCase,
            modelPlanner = FakeModelPlanner(),
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(),
            startAgentSession = startAgentSession,
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
        val learnedPlanStore = FakeLearnedPlanStore()
        return LauncherViewModel(
            installedAppsRepository = fakeRepo,
            resolveCommand = resolveCommand,
            recordResolutionChoice = RecordResolutionChoiceUseCase(resolutionStore),
            recordLearnedPlan = RecordLearnedPlanUseCase(learnedPlanStore),
            forgetLearnedPlan = ForgetLearnedPlanUseCase(learnedPlanStore),
            executeAction = ExecuteActionUseCase(resolver = IntentActionResolver(fakeRepo), executor = fakeExecutor),
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
            undoAgentSession = undoOver(agentStore, agentRegistry),
            agentSessionStore = agentStore,
            startAgentSession = startAgentSession,
            mentionBinder = FakeMentionBinder(),
            toolRegistry = agentRegistry,
            dynamicToolLabels = DynamicToolLabels { emptyMap() },
            modelAskedEvents = ModelAskedEvents(),
            nextAlarmSource = nextAlarms,
            ioDispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    private fun abs(d: Dp): Dp = if (d < 0.dp) -d else d

    private companion object {
        const val ALARM_AT = 1_790_000_000_000L
    }
}
