package com.sidr.launcher.data.prayer

import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerScheduleProvenance
import com.sidr.launcher.domain.prayer.CachedPrayerSchedule
import com.sidr.launcher.domain.prayer.PrayerAuthority
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.result.OperationResult
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * DS-6B Task 6 — [PrayerPreferencesRepositoryImpl] round-trip + I1 regression tests. Pure JVM (no
 * Robolectric), same temp-file DataStore pattern as `:data:repository`'s
 * `UserPreferencesRepositoryImplTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrayerPreferencesRepositoryImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "prayer.preferences_pb")

    private fun istanbul(label: String = "Istanbul") = PrayerLocation(
        label = label,
        lat2dp = 41.01,
        lon2dp = 28.98,
        tzId = "Europe/Istanbul",
        source = PrayerLocationSource.CITY,
    )

    private fun turkeySetup(location: PrayerLocation? = istanbul()) = PrayerSetup(
        methodId = CalculationMethodId("TURKEY"),
        madhab = Madhab.STANDARD,
        location = location,
    )

    private fun scheduleFor(date: LocalDate) = PrayerDaySchedule(
        dateInLocationTz = date,
        instants = listOf(
            PrayerInstant(PrayerName.FAJR, 1L),
            PrayerInstant(PrayerName.DHUHR, 2L),
            PrayerInstant(PrayerName.ASR, 3L),
            PrayerInstant(PrayerName.MAGHRIB, 4L),
            PrayerInstant(PrayerName.ISHA, 5L),
        ),
    )

    private fun provenanceFor(setup: PrayerSetup) = PrayerScheduleProvenance(
        authority = PrayerAuthority.LOCAL_CALC,
        methodId = setup.methodId,
        madhab = setup.madhab,
        locationLabel = setup.location!!.label,
        computedAtMillis = 100L,
        locationSource = setup.location!!.source,
    )

    @Test
    fun `setup is null when never configured`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PrayerPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        assertNull(repo.setup().first())
        scope.cancel()
    }

    @Test
    fun `setup with location round-trips within the same instance`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PrayerPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val setup = turkeySetup()
        val result = repo.saveSetup(setup)

        assertTrue(result is OperationResult.Success)
        assertEquals(setup, repo.setup().first())
        scope.cancel()
    }

    @Test
    fun `setup without a location round-trips as LOCATION_MISSING`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PrayerPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        val setup = turkeySetup(location = null)
        repo.saveSetup(setup)

        val roundTripped = repo.setup().first()
        assertEquals(setup, roundTripped)
        assertNull(roundTripped?.location)
        scope.cancel()
    }

    @Test
    fun `setup survives a simulated reopen`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val setup = turkeySetup(location = istanbul(label = "Ürgüp, Türkiye"))

        val writeScope = CoroutineScope(dispatcher + Job())
        PrayerPreferencesRepositoryImpl(createTestDataStore(target, writeScope), dispatcher)
            .saveSetup(setup)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = PrayerPreferencesRepositoryImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(setup, reopened.setup().first())
        readScope.cancel()
    }

    @Test
    fun `clearSetup resets setup to null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val repo = PrayerPreferencesRepositoryImpl(createTestDataStore(file(), scope), dispatcher)

        repo.saveSetup(turkeySetup())
        assertTrue(repo.setup().first() != null)

        val result = repo.clearSetup()

        assertTrue(result is OperationResult.Success)
        assertNull(repo.setup().first())
        scope.cancel()
    }

    // ── I1 regression: saveSetup atomically invalidates any previously cached schedule ─────────

    @Test
    fun `I1 - saveSetup with a same-label but different-coordinates location clears the schedule cache`() =
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val scope = CoroutineScope(dispatcher + Job())
            // Single shared DataStore instance — DataStore forbids two live instances over the same
            // file in one process, and both repository/cache impls persist to the SAME shared file.
            val store = createTestDataStore(file(), scope)
            val prefsRepo = PrayerPreferencesRepositoryImpl(store, dispatcher)
            val cache = PrayerScheduleCacheImpl(store, dispatcher)

            val l1 = PrayerLocation(
                label = "Springfield",
                lat2dp = 39.78,
                lon2dp = -89.65,
                tzId = "America/Chicago",
                source = PrayerLocationSource.CITY,
            )
            val setup1 = turkeySetup(location = l1)
            prefsRepo.saveSetup(setup1)
            val cached = CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceFor(setup1),
            )
            cache.write(cached)
            assertEquals(cached, (cache.read() as OperationResult.Success).value)

            // Same label, different coordinates/timezone — e.g. a device-location re-pick that
            // resolved to a different Springfield, or a manual re-pick at a slightly different fix.
            val l2 = PrayerLocation(
                label = "Springfield",
                lat2dp = 42.1,
                lon2dp = -72.59,
                tzId = "America/New_York",
                source = PrayerLocationSource.DEVICE,
            )
            prefsRepo.saveSetup(turkeySetup(location = l2))

            val afterResecond = cache.read()
            assertTrue(afterResecond is OperationResult.Success)
            assertNull((afterResecond as OperationResult.Success).value)
            scope.cancel()
        }

    @Test
    fun `I1 - clearSetup also clears the schedule cache`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val store = createTestDataStore(file(), scope)
        val prefsRepo = PrayerPreferencesRepositoryImpl(store, dispatcher)
        val cache = PrayerScheduleCacheImpl(store, dispatcher)

        val setup = turkeySetup()
        prefsRepo.saveSetup(setup)
        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceFor(setup),
            ),
        )
        assertTrue((cache.read() as OperationResult.Success).value != null)

        prefsRepo.clearSetup()

        assertNull((cache.read() as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `read tolerates an IOException from the underlying store as no setup`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        // A directory in place of the expected file makes DataStore's IO fail with IOException
        // (correct `.preferences_pb` extension so the failure is an I/O one, not an eager
        // extension-validation IllegalStateException from PreferenceDataStoreFactory itself).
        val badFile = File(tmpFolder.root, "not_a_real_datastore_file.preferences_pb")
        badFile.mkdirs()
        val repo = PrayerPreferencesRepositoryImpl(createTestDataStore(badFile, scope), dispatcher)

        assertNull(repo.setup().first())
        scope.cancel()
    }
}
