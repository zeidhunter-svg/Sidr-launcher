package com.sidr.launcher.feature.prayer

import com.sidr.launcher.core.common.navigation.NavigationEvent
import com.sidr.launcher.core.common.navigation.Routes
import com.sidr.launcher.core.testing.FakePrayerCalculator
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.core.testing.FakePrayerScheduleCache
import com.sidr.launcher.domain.prayer.GetPrayerContextUseCase
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [PrayerDetailViewModel] JVM tests. The regression these pin: the detail screen's Method / Madhab /
 * Location rows used to emit the SAME argument-less `prayer_settings` route, so all three landed on
 * the top of the one long setup page — where the 11-row calculation-method list fills the whole
 * viewport and madhab/location sit below the fold. Every row therefore *looked* like "the method
 * picker". Each row must now carry its own [PrayerSettingsSection].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrayerDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var preferences: FakePrayerPreferencesRepository
    private lateinit var calculator: FakePrayerCalculator
    private lateinit var scheduleCache: FakePrayerScheduleCache

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        preferences = FakePrayerPreferencesRepository()
        calculator = FakePrayerCalculator()
        scheduleCache = FakePrayerScheduleCache()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(): PrayerDetailViewModel = PrayerDetailViewModel(
        preferences = preferences,
        getPrayerContext = GetPrayerContextUseCase(
            preferences = preferences,
            cache = scheduleCache,
            calculator = calculator,
            clock = Clock.fixed(Instant.parse("2026-08-07T10:00:00Z"), ZoneOffset.UTC),
        ),
    )

    @Test
    fun `method madhab and location rows each navigate to their own section`() = runTest(dispatcher) {
        val vm = buildViewModel()
        val routes = mutableListOf<String>()
        val job = launch {
            vm.navigationEvents.collect { event ->
                routes += (event as NavigationEvent.NavigateTo).route
            }
        }

        vm.openSettings(PrayerSettingsSection.METHOD)
        vm.openSettings(PrayerSettingsSection.MADHAB)
        vm.openSettings(PrayerSettingsSection.LOCATION)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(3, routes.size)
        // The bug in one assertion: three different rows must not produce one identical route.
        assertEquals(3, routes.toSet().size)
        assertEquals(Routes.PrayerSettings.routeFor(PrayerSettingsSection.METHOD.name), routes[0])
        assertEquals(Routes.PrayerSettings.routeFor(PrayerSettingsSection.MADHAB.name), routes[1])
        assertEquals(Routes.PrayerSettings.routeFor(PrayerSettingsSection.LOCATION.name), routes[2])
    }

    @Test
    fun `a null section opens the whole setup page`() = runTest(dispatcher) {
        val vm = buildViewModel()
        val routes = mutableListOf<String>()
        val job = launch {
            vm.navigationEvents.collect { event ->
                routes += (event as NavigationEvent.NavigateTo).route
            }
        }

        vm.openSettings(null)
        dispatcher.scheduler.advanceUntilIdle()
        job.cancel()

        assertEquals(listOf(Routes.PrayerSettings.ROUTE), routes)
    }

    @Test
    fun `every section route is matched by the pattern registered in the NavHost`() {
        // `prayer_settings?section={section}` must keep matching the bare route too, so the
        // Settings entry point ("Prayer times" row) still opens the full setup page.
        val pattern = Routes.PrayerSettings.ROUTE_WITH_ARG
        assertTrue(pattern.startsWith("${Routes.PrayerSettings.ROUTE}?"))
        assertTrue(pattern.contains("{${Routes.PrayerSettings.ARG_SECTION}}"))
        PrayerSettingsSection.entries.forEach { section ->
            assertEquals(
                "${Routes.PrayerSettings.ROUTE}?${Routes.PrayerSettings.ARG_SECTION}=${section.name}",
                Routes.PrayerSettings.routeFor(section.name),
            )
        }
    }
}
