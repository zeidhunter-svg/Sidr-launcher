package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
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
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.core.testing.FakeUserPreferencesRepository
import com.sidr.launcher.core.ui.theme.SidrTheme
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
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.result.OperationResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DS-6B Task 9: proves the Home prayer strip's "truthful or absent" contract at the screen level —
 * nothing renders while the prayer context is `Unavailable(NOT_CONFIGURED)`, and the real
 * [com.sidr.launcher.core.ui.component.SidrPrayerSummary] strip renders once it is `Available`.
 * Mirrors the Vision MVP preview screens' Robolectric compose-test harness (`ActivityPreviewScreenTest`
 * et al.); unlike those, [LauncherScreen] needs a real [LauncherViewModel] (accepted as an explicit
 * param here, bypassing `hiltViewModel()`) wired over the same `:core:testing` fakes
 * `LauncherViewModelTest` uses. [UnconfinedTestDispatcher] drives every coroutine (app-list load,
 * suggestions, and — the crux of this test — the prayer context's `WhileSubscribed` collection once
 * Compose's `collectAsStateWithLifecycle` subscribes) eagerly, with no manual pumping needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LauncherScreenPrayerStripTest {

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

    private fun buildViewModel(getPrayerContext: GetPrayerContextUseCase): LauncherViewModel {
        val fakeRepo = FakeInstalledAppsRepository()
        val fakeMatcher = FakeIntentMatcher()
        val fakeExecutor = FakeActionExecutor()
        val useCase = HandleUserCommandUseCase(
            matcher = fakeMatcher,
            resolver = IntentActionResolver(fakeRepo),
            executor = fakeExecutor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(dispatcher + SupervisorJob()),
        )
        val routeUseCase = RouteCommandUseCase(
            handleUserCommand = useCase,
            planner = FakeCommandPlanner(),
            catalog = FakeActionCatalog(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            connectivityChecker = FakeConnectivityChecker(),
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
            getPrayerContext = getPrayerContext,
            ioDispatcher = dispatcher,
            applicationScope = CoroutineScope(dispatcher + SupervisorJob()),
            savedStateHandle = SavedStateHandle(),
        )
    }

    @Test
    fun `no prayer strip node when the context is not configured`() {
        val vm = buildViewModel(
            getPrayerContext = GetPrayerContextUseCase(
                FakePrayerPreferencesRepository(),
                FakePrayerScheduleCache(),
                FakePrayerCalculator(),
                Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("UTC")),
            ),
        )

        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        // SidrPrayerSummary always sets a "prayer times, ..." semantics contentDescription on its root;
        // its absence proves HomePrayerStrip rendered nothing — not even an empty placeholder strip
        // (NOT_CONFIGURED maps to `null`, never to a schedule-less summary).
        compose.onNodeWithContentDescription("prayer times", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the prayer strip renders once the context is Available`() {
        val prefs = FakePrayerPreferencesRepository(
            initial = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbulPrayerLocation()),
        )
        val calculator = FakePrayerCalculator().apply {
            resultToReturn = OperationResult.Success(istanbulPrayerSchedule())
        }
        val vm = buildViewModel(
            getPrayerContext = GetPrayerContextUseCase(
                prefs,
                FakePrayerScheduleCache(),
                calculator,
                Clock.fixed(Instant.parse("2026-07-13T10:00:00Z"), ZoneId.of("Europe/Istanbul")),
            ),
        )

        compose.setContent {
            SidrTheme(darkTheme = true) {
                LauncherScreen(viewModel = vm)
            }
        }
        compose.waitForIdle()

        // Sanity check for the test above: proves the "nothing rendered" result is because
        // NOT_CONFIGURED truthfully maps to nothing, not because the strip never renders at all.
        compose.onNodeWithContentDescription("prayer times", substring = true).assertIsDisplayed()
    }

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
}
