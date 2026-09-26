package com.sidr.launcher.feature.prayer

import androidx.lifecycle.SavedStateHandle
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakeCityIndex
import com.sidr.launcher.core.testing.FakePrayerCalculator
import com.sidr.launcher.core.testing.FakePrayerLocationProvider
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.core.testing.FakePrayerScheduleCache
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * DS-6B Task 8 — [PrayerSettingsViewModel] JVM tests over the [com.sidr.launcher.core.testing] fakes.
 * Covers exactly the acceptance-relevant behaviours: save requires method AND madhab (location
 * optional), clear returns to `NOT_CONFIGURED`, a device-location result is stored already-rounded
 * with `source = DEVICE`, a device-location failure/no-fix leaves an existing setup untouched, and —
 * structurally, by inspection of the constructor/imports below — the VM never touches any permission
 * API (no [com.sidr.launcher.domain.permission.PermissionChecker] dependency exists to test against).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrayerSettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var preferences: FakePrayerPreferencesRepository
    private lateinit var cityIndex: FakeCityIndex
    private lateinit var locationProvider: FakePrayerLocationProvider
    private lateinit var calculator: FakePrayerCalculator
    private lateinit var scheduleCache: FakePrayerScheduleCache

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = FakePrayerPreferencesRepository()
        cityIndex = FakeCityIndex()
        locationProvider = FakePrayerLocationProvider()
        calculator = FakePrayerCalculator()
        scheduleCache = FakePrayerScheduleCache()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(section: String? = null): PrayerSettingsViewModel = PrayerSettingsViewModel(
        savedStateHandle = SavedStateHandle(
            section?.let { mapOf(Routes.PrayerSettings.ARG_SECTION to it) } ?: emptyMap(),
        ),
        preferences = preferences,
        cityIndex = cityIndex,
        getPrayerContext = GetPrayerContextUseCase(
            preferences = preferences,
            cache = scheduleCache,
            calculator = calculator,
            clock = Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC),
        ),
        locationProvider = locationProvider,
    )

    /** [PrayerPreferencesRepository.setup] is declared as plain `Flow` (no `.value` accessor); the
     *  fake backs it with a `StateFlow` internally, so a single [first] read always returns the
     *  latest emitted value without suspending indefinitely. */
    private suspend fun currentSetup() = preferences.setup().first()

    private fun istanbul(source: PrayerLocationSource = PrayerLocationSource.CITY) = PrayerLocation(
        label = "Istanbul",
        lat2dp = 41.01,
        lon2dp = 28.98,
        tzId = "Europe/Istanbul",
        source = source,
    )

    // ── save requires method AND madhab; location is optional ──────────────────────────────────

    @Test
    fun `selecting only a method does not persist a setup`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.onMethodSelected(CalculationMethodId("MWL"))
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(currentSetup())
        assertEquals(0, preferences.writeCount)
    }

    @Test
    fun `selecting only a madhab does not persist a setup`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.onMadhabSelected(Madhab.HANAFI)
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(currentSetup())
        assertEquals(0, preferences.writeCount)
    }

    @Test
    fun `selecting both method and madhab persists a setup with a null location`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.onMethodSelected(CalculationMethodId("MWL"))
        vm.onMadhabSelected(Madhab.HANAFI)
        dispatcher.scheduler.advanceUntilIdle()

        val saved = currentSetup()
        assertEquals(CalculationMethodId("MWL"), saved?.methodId)
        assertEquals(Madhab.HANAFI, saved?.madhab)
        assertNull(saved?.location)
    }

    @Test
    fun `explicit saveSetup persists once method and madhab are both selected`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.onMethodSelected(CalculationMethodId("TURKEY"))
        vm.onMadhabSelected(Madhab.STANDARD)
        vm.saveSetup()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = currentSetup()
        assertEquals(CalculationMethodId("TURKEY"), saved?.methodId)
        assertEquals(Madhab.STANDARD, saved?.madhab)
    }

    @Test
    fun `selecting a city after method and madhab saves the full setup with a location`() = runTest(dispatcher) {
        val vm = buildViewModel()

        vm.onMethodSelected(CalculationMethodId("MWL"))
        vm.onMadhabSelected(Madhab.STANDARD)
        vm.onCitySelected(istanbul())
        dispatcher.scheduler.advanceUntilIdle()

        val saved = currentSetup()
        assertEquals(istanbul(), saved?.location)
    }

    // ── clear → NOT_CONFIGURED ──────────────────────────────────────────────────────────────────

    @Test
    fun `clearSetup returns the setup to NOT_CONFIGURED`() = runTest(dispatcher) {
        preferences = FakePrayerPreferencesRepository(
            initial = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbul()),
        )
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        assertTrue(vm.uiState.value.isConfigured)

        vm.clearSetup()
        dispatcher.scheduler.advanceUntilIdle()

        assertNull(currentSetup())
        assertFalse(vm.uiState.value.isConfigured)
        assertNull(vm.uiState.value.selectedMethod)
        assertNull(vm.uiState.value.selectedMadhab)
        assertNull(vm.uiState.value.location)
    }

    @Test
    fun `clearLocation preserves method and madhab but drops the location`() = runTest(dispatcher) {
        preferences = FakePrayerPreferencesRepository(
            initial = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbul()),
        )
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()

        vm.clearLocation()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = currentSetup()
        assertEquals(CalculationMethodId("MWL"), saved?.methodId)
        assertEquals(Madhab.STANDARD, saved?.madhab)
        assertNull(saved?.location)
        assertNull(vm.uiState.value.location)
    }

    // ── device-location Success stores the rounded location as source = DEVICE ─────────────────

    @Test
    fun `device location success saves the already-rounded location with source DEVICE`() = runTest(dispatcher) {
        val deviceLocation = istanbul(source = PrayerLocationSource.DEVICE)
        locationProvider.resultToReturn = OperationResult.Success(deviceLocation)
        val vm = buildViewModel()

        vm.onMethodSelected(CalculationMethodId("MWL"))
        vm.onMadhabSelected(Madhab.STANDARD)
        vm.useDeviceLocation()
        dispatcher.scheduler.advanceUntilIdle()

        val saved = currentSetup()
        assertEquals(deviceLocation, saved?.location)
        assertEquals(PrayerLocationSource.DEVICE, saved?.location?.source)
        assertEquals(1, locationProvider.callCount)
    }

    // ── device-location Failure leaves setup usable (city path intact) ─────────────────────────

    @Test
    fun `device location failure leaves an existing city setup untouched`() = runTest(dispatcher) {
        val existing = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbul())
        preferences = FakePrayerPreferencesRepository(initial = existing)
        locationProvider.resultToReturn =
            OperationResult.Failure(OperationError.UnknownError("device location read failed"))
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val writesBefore = preferences.writeCount

        vm.useDeviceLocation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(existing, currentSetup())
        assertEquals(writesBefore, preferences.writeCount)
        assertEquals(existing.location, vm.uiState.value.location)
        assertFalse(vm.uiState.value.isResolvingDeviceLocation)
        assertEquals("Couldn't read device location.", vm.uiState.value.statusMessage)
    }

    @Test
    fun `device location success with no fix leaves an existing city setup untouched`() = runTest(dispatcher) {
        val existing = PrayerSetup(CalculationMethodId("MWL"), Madhab.STANDARD, istanbul())
        preferences = FakePrayerPreferencesRepository(initial = existing)
        locationProvider.resultToReturn = OperationResult.Success(null)
        val vm = buildViewModel()
        dispatcher.scheduler.advanceUntilIdle()
        val writesBefore = preferences.writeCount

        vm.useDeviceLocation()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(existing, currentSetup())
        assertEquals(writesBefore, preferences.writeCount)
        assertEquals(existing.location, vm.uiState.value.location)
    }

    // ── city search ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `city search query is forwarded to the CityIndex port and results are exposed`() = runTest(dispatcher) {
        cityIndex.cities = listOf(istanbul())
        val vm = buildViewModel()

        vm.onCitySearchQueryChanged("istan")
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("istan"), cityIndex.receivedQueries)
        assertEquals(listOf(istanbul()), vm.uiState.value.citySearchResults)
    }

    // ── section targeting (deep link from the detail screen's Method/Madhab/Location rows) ─────

    @Test
    fun `an absent section arg keeps the whole setup page`() = runTest(dispatcher) {
        val vm = buildViewModel(section = null)

        assertNull(vm.uiState.value.section)
    }

    @Test
    fun `each section arg is exposed verbatim so the screen renders only that section`() =
        runTest(dispatcher) {
            PrayerSettingsSection.entries.forEach { expected ->
                assertEquals(expected, buildViewModel(section = expected.name).uiState.value.section)
            }
        }

    @Test
    fun `an unknown section arg degrades to the whole setup page`() = runTest(dispatcher) {
        val vm = buildViewModel(section = "NOT_A_SECTION")

        assertNull(vm.uiState.value.section)
    }

    @Test
    fun `section targeting does not change what a selection persists`() = runTest(dispatcher) {
        val vm = buildViewModel(section = PrayerSettingsSection.MADHAB.name)

        vm.onMethodSelected(CalculationMethodId("MWL"))
        vm.onMadhabSelected(Madhab.HANAFI)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(PrayerSetup(CalculationMethodId("MWL"), Madhab.HANAFI, null), currentSetup())
    }
}
