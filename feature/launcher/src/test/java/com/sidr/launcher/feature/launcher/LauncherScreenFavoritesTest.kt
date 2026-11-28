package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.SavedStateHandle
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
import com.sidr.launcher.core.common.UiState
import com.sidr.launcher.domain.history.AppUsageRecord
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.UserPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A4′ acceptance tail, task 12 (owner ruling 15; K13): Home's favourites header and the picker it opens, by semantics —
 * Home has no goldens and gets none. The real [LauncherScreen] over a real [LauncherViewModel] wired from
 * `:core:testing` fakes, as `LauncherScreenPrayerStripTest` builds it. The time is a fixed clock, so freshness is exact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LauncherScreenFavoritesTest {

    @get:Rule val compose = createComposeRule()

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val apps = listOf(
        InstalledApp("com.alpha", "Alpha"),
        InstalledApp("com.beta", "Beta"),
        InstalledApp("com.gamma", "Gamma"),
    )

    private fun show(vm: LauncherViewModel) {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()
    }

    /** Home's header: the section label is drawn in capitals, and Home scrolls as one column. */
    private fun header() = compose.onNodeWithText("FAVORITES").performScrollTo()

    /** The picker's row for [label] — Home's own tile for the same app is not it. */
    private fun pickerRow(label: String) =
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag(FAVORITES_PICKER_TEST_TAG)))

    private fun favoritesOf(vm: LauncherViewModel): List<String> =
        (vm.uiState.value as UiState.Success).data.favorites.map { it.packageName }

    @Test
    fun `the header is drawn with no favourites and opens the picker`() {
        val vm = buildViewModel(apps, records = emptyList(), prefsRepo = FakeUserPreferencesRepository())
        show(vm)

        compose.onNodeWithTag(FAVORITES_PICKER_TEST_TAG).assertDoesNotExist()
        // The tap target is at least the 48 dp minimum.
        header().assertIsDisplayed().assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp).performClick()
        compose.waitForIdle()

        compose.onNodeWithTag(FAVORITES_PICKER_TEST_TAG).assertIsDisplayed()
        compose.onNodeWithText("Pinned apps come first", substring = true).assertIsDisplayed()
        // Every installed app is a row of the picker.
        apps.forEach { pickerRow(it.label).assertIsDisplayed() }
    }

    @Test
    fun `switching a row on pins the app and it appears on Home`() {
        val prefsRepo = FakeUserPreferencesRepository()
        val vm = buildViewModel(apps, records = emptyList(), prefsRepo = prefsRepo)
        show(vm)

        header().performClick()
        compose.waitForIdle()
        pickerRow("Beta").performClick()
        compose.waitForIdle()

        assertEquals(listOf("com.beta"), runBlocking { prefsRepo.getPreferences().first().pinnedFavorites })
        assertEquals(listOf("com.beta"), favoritesOf(vm))

        // Switching it off again unpins.
        pickerRow("Beta").performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), runBlocking { prefsRepo.getPreferences().first().pinnedFavorites })
    }

    @Test
    fun `an automatically placed app has the remove action and a pinned one does not`() {
        val prefsRepo = FakeUserPreferencesRepository(UserPreferences(pinnedFavorites = listOf("com.gamma")))
        val records = listOf(AppUsageRecord("com.alpha", lastUsedEpochMs = NOW - 1, launchCount = 3))
        val vm = buildViewModel(apps, records, prefsRepo)
        show(vm)

        header().performClick()
        compose.waitForIdle()

        // Only Alpha was placed by use; Gamma is pinned and Beta is not on Home at all.
        compose.onAllNodesWithText("Remove from Home").assertCountEquals(1)
        compose.onNodeWithText("Remove from Home").performClick()
        compose.waitForIdle()

        assertEquals(setOf("com.alpha"), runBlocking { prefsRepo.getPreferences().first().hiddenFavorites })
        compose.onAllNodesWithText("Remove from Home").assertCountEquals(0)
        assertEquals(listOf("com.gamma"), favoritesOf(vm))
    }

    private fun buildViewModel(
        apps: List<InstalledApp>,
        records: List<AppUsageRecord>,
        prefsRepo: FakeUserPreferencesRepository,
    ): LauncherViewModel {
        val fakeRepo = FakeInstalledAppsRepository().apply { appsToReturn = apps }
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
            usageHistoryRepository = FakeUsageHistoryRepository().apply { setRecords(records) },
            featureFlagRepository = FakeFeatureFlagRepository(),
            userPreferencesRepository = prefsRepo,
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
            clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("UTC")),
            ioDispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    private companion object {
        const val NOW = 1_790_000_000_000L
    }
}
