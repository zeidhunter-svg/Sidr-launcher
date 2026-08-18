package com.sidr.launcher.data.prayer

import androidx.datastore.preferences.core.stringPreferencesKey
import com.sidr.launcher.domain.prayer.CachedPrayerSchedule
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerAuthority
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerScheduleProvenance
import com.sidr.launcher.domain.result.OperationResult
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * DS-6B Task 6 — [PrayerScheduleCacheImpl] round-trip + "no schedule without provenance" tests.
 * Pure JVM, same temp-file DataStore pattern as the sibling preferences repo test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PrayerScheduleCacheImplTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun file(): File = File(tmpFolder.root, "prayer.preferences_pb")

    private val turkeySetupMethod = CalculationMethodId("TURKEY")

    private fun scheduleFor(date: LocalDate, includeSunrise: Boolean = true) = PrayerDaySchedule(
        dateInLocationTz = date,
        instants = listOf(
            PrayerInstant(PrayerName.FAJR, 1_000L),
            PrayerInstant(PrayerName.DHUHR, 2_000L),
            PrayerInstant(PrayerName.ASR, 3_000L),
            PrayerInstant(PrayerName.MAGHRIB, 4_000L),
            PrayerInstant(PrayerName.ISHA, 5_000L),
        ),
        sunrise = if (includeSunrise) PrayerInstant(PrayerName.SUNRISE, 1_500L) else null,
    )

    private fun provenanceWith(label: String, source: PrayerLocationSource = PrayerLocationSource.CITY) =
        PrayerScheduleProvenance(
            authority = PrayerAuthority.LOCAL_CALC,
            methodId = turkeySetupMethod,
            madhab = Madhab.STANDARD,
            locationLabel = label,
            computedAtMillis = 42_000L,
            locationSource = source,
        )

    @Test
    fun `read returns Success null when no cache exists`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cache = PrayerScheduleCacheImpl(createTestDataStore(file(), scope), dispatcher)

        val result = cache.read()

        assertTrue(result is OperationResult.Success)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `write then read round-trips schedule with provenance including a special-char label`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cache = PrayerScheduleCacheImpl(createTestDataStore(file(), scope), dispatcher)

        val entry = CachedPrayerSchedule(
            schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
            // Space + non-ASCII (Turkish İ, comma) — must round-trip losslessly.
            provenance = provenanceWith("İstanbul, Türkiye"),
        )
        val writeResult = cache.write(entry)

        assertTrue(writeResult is OperationResult.Success)
        val readResult = cache.read()
        assertTrue(readResult is OperationResult.Success)
        assertEquals(entry, (readResult as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `round-trip without a sunrise instant still succeeds`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cache = PrayerScheduleCacheImpl(createTestDataStore(file(), scope), dispatcher)

        val entry = CachedPrayerSchedule(
            schedule = scheduleFor(LocalDate.of(2026, 8, 8), includeSunrise = false),
            provenance = provenanceWith("Cairo"),
        )
        cache.write(entry)

        assertEquals(entry, (cache.read() as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `schedule survives a simulated reopen`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val target = file()
        val entry = CachedPrayerSchedule(
            schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
            provenance = provenanceWith("Tokyo"),
        )

        val writeScope = CoroutineScope(dispatcher + Job())
        PrayerScheduleCacheImpl(createTestDataStore(target, writeScope), dispatcher).write(entry)
        writeScope.cancel()

        val readScope = CoroutineScope(dispatcher + Job())
        val reopened = PrayerScheduleCacheImpl(createTestDataStore(target, readScope), dispatcher)
        assertEquals(entry, (reopened.read() as OperationResult.Success).value)
        readScope.cancel()
    }

    @Test
    fun `clear resets the cache to null`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val cache = PrayerScheduleCacheImpl(createTestDataStore(file(), scope), dispatcher)

        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceWith("Mecca"),
            ),
        )
        assertTrue((cache.read() as OperationResult.Success).value != null)

        val result = cache.clear()

        assertTrue(result is OperationResult.Success)
        assertNull((cache.read() as OperationResult.Success).value)
        scope.cancel()
    }

    // ── "no schedule without provenance" — missing/partial persisted fields → Success(null) ─────

    @Test
    fun `missing provenance key alone yields no cache, not a partial one`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val store = createTestDataStore(file(), scope)
        val cache = PrayerScheduleCacheImpl(store, dispatcher)

        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceWith("Amman"),
            ),
        )
        // Directly corrupt the store: drop only the provenance key, leaving date+times intact.
        store.updateData { it.toMutablePreferences().apply { remove(stringPreferencesKey("prayer_sched_provenance")) } }

        val result = cache.read()
        assertTrue(result is OperationResult.Success)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `provenance JSON written before I18N-2 - no locationSource key - decodes with a CITY default`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val store = createTestDataStore(file(), scope)
        val cache = PrayerScheduleCacheImpl(store, dispatcher)

        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceWith("Amman"),
            ),
        )
        // Overwrite with a hand-crafted pre-I18N-2 provenance payload: no locationSource key at
        // all, simulating a real cache entry persisted before this field existed. `write()` above
        // is only there to get a valid schedule/date pair on disk; this line replaces just the
        // provenance the way the "missing key" test above replaces just the schedule.
        store.updateData {
            it.toMutablePreferences().apply {
                this[stringPreferencesKey("prayer_sched_provenance")] =
                    """{"authority":"LOCAL_CALC","methodId":"TURKEY","madhab":"STANDARD",""" +
                    """"locationLabel":"Amman","computedAtMillis":42000}"""
            }
        }

        val result = cache.read()
        assertTrue(result is OperationResult.Success)
        val cached = (result as OperationResult.Success).value
        assertEquals(PrayerLocationSource.CITY, cached?.provenance?.locationSource)
    }

    @Test
    fun `malformed times JSON yields no cache`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val store = createTestDataStore(file(), scope)
        val cache = PrayerScheduleCacheImpl(store, dispatcher)

        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceWith("Amman"),
            ),
        )
        store.updateData {
            it.toMutablePreferences().apply {
                this[stringPreferencesKey("prayer_sched_times")] = "{not valid json"
            }
        }

        val result = cache.read()
        assertTrue(result is OperationResult.Success)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `an incomplete instant list (not exactly the five prayers) yields no cache`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val store = createTestDataStore(file(), scope)
        val cache = PrayerScheduleCacheImpl(store, dispatcher)

        cache.write(
            CachedPrayerSchedule(
                schedule = scheduleFor(LocalDate.of(2026, 8, 8)),
                provenance = provenanceWith("Amman"),
            ),
        )
        // Hand-craft a times payload with only 3 of the 5 required prayers.
        store.updateData {
            it.toMutablePreferences().apply {
                this[stringPreferencesKey("prayer_sched_times")] =
                    """{"instants":[{"name":"FAJR","epochMillis":1},{"name":"DHUHR","epochMillis":2},{"name":"ASR","epochMillis":3}],"sunrise":null}"""
            }
        }

        val result = cache.read()
        assertTrue(result is OperationResult.Success)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }

    @Test
    fun `read tolerates an IOException from the underlying store as no cache`() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        // Correct `.preferences_pb` extension so the failure is a real I/O one, not an eager
        // extension-validation IllegalStateException from PreferenceDataStoreFactory itself.
        val badFile = File(tmpFolder.root, "not_a_real_datastore_file.preferences_pb")
        badFile.mkdirs()
        val cache = PrayerScheduleCacheImpl(createTestDataStore(badFile, scope), dispatcher)

        val result = cache.read()

        assertTrue(result is OperationResult.Success)
        assertNull((result as OperationResult.Success).value)
        scope.cancel()
    }
}
