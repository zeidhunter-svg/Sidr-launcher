package com.sidr.launcher.domain.prayer

import com.sidr.launcher.core.testing.FakePrayerCalculator
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.core.testing.FakePrayerScheduleCache
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.ISTANBUL_ZONE
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.NOW
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.TODAY_ISTANBUL
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.TOKYO_ZONE
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.provenanceFor
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.scheduleFor
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.tokyoLocation
import com.sidr.launcher.domain.prayer.PrayerTestFixtures.turkeySetup
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The full DS-6B Task 3 orchestration contract for [GetPrayerContextUseCase]: unavailable
 * branches, cache-first emission, recompute outcomes, next-prayer + day-boundary derivation in
 * the location timezone, and timezone-conflict surfacing. The device timezone comes from the
 * injected [Clock] — every test pins it explicitly.
 */
class GetPrayerContextUseCaseTest {

    private val prefs = FakePrayerPreferencesRepository()
    private val cache = FakePrayerScheduleCache()
    private val calculator = FakePrayerCalculator()

    private fun useCase(clock: Clock = Clock.fixed(NOW, ISTANBUL_ZONE)) =
        GetPrayerContextUseCase(prefs, cache, calculator, clock)

    private fun scriptCalcSuccess(schedule: PrayerDaySchedule = scheduleFor(TODAY_ISTANBUL)) {
        calculator.resultToReturn = OperationResult.Success(schedule)
    }

    private fun scriptCalcFailure() {
        calculator.resultToReturn =
            OperationResult.Failure(OperationError.UnknownError("calc failed"))
    }

    // ── Unavailable branches ───────────────────────────────────────────────────

    @Test
    fun `no setup emits Unavailable NOT_CONFIGURED and never consults the calculator`() = runTest {
        val contexts = useCase().get().toList()

        assertEquals(
            listOf<PrayerContext>(PrayerContext.Unavailable(UnavailableReason.NOT_CONFIGURED)),
            contexts,
        )
        assertEquals(0, calculator.callCount)
    }

    @Test
    fun `setup without location emits Unavailable LOCATION_MISSING`() = runTest {
        prefs.saveSetup(turkeySetup(location = null))

        val contexts = useCase().get().toList()

        assertEquals(
            listOf<PrayerContext>(PrayerContext.Unavailable(UnavailableReason.LOCATION_MISSING)),
            contexts,
        )
        assertEquals(0, calculator.callCount)
    }

    @Test
    fun `calculation failure without cache emits Unavailable CALCULATION_FAILED`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcFailure()

        val contexts = useCase().get().toList()

        assertEquals(
            listOf<PrayerContext>(PrayerContext.Unavailable(UnavailableReason.CALCULATION_FAILED)),
            contexts,
        )
    }

    @Test
    fun `unresolvable location tzId emits Unavailable CALCULATION_FAILED without calculating`() = runTest {
        prefs.saveSetup(turkeySetup(location = PrayerTestFixtures.istanbulLocation().copy(tzId = "Not/AZone")))
        scriptCalcSuccess()

        val contexts = useCase().get().toList()

        assertEquals(
            listOf<PrayerContext>(PrayerContext.Unavailable(UnavailableReason.CALCULATION_FAILED)),
            contexts,
        )
        assertEquals(0, calculator.callCount)
    }

    // ── Fresh compute (no cache) ───────────────────────────────────────────────

    @Test
    fun `calc success without cache emits single VERIFIED_CURRENT with LOCAL_CALC provenance`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        val schedule = scheduleFor(TODAY_ISTANBUL)
        scriptCalcSuccess(schedule)

        val contexts = useCase().get().toList()

        assertEquals(1, contexts.size)
        val available = contexts.single() as PrayerContext.Available
        assertEquals(schedule, available.schedule)
        assertEquals(Freshness.VERIFIED_CURRENT, available.freshness)
        assertEquals(
            PrayerScheduleProvenance(
                authority = PrayerAuthority.LOCAL_CALC,
                methodId = setup.methodId,
                madhab = setup.madhab,
                locationLabel = "Istanbul",
                computedAtMillis = NOW.toEpochMilli(),
            ),
            available.provenance,
        )
    }

    @Test
    fun `successful recompute writes the labelled schedule to the cache`() = runTest {
        prefs.saveSetup(turkeySetup())
        val schedule = scheduleFor(TODAY_ISTANBUL)
        scriptCalcSuccess(schedule)

        useCase().get().toList()

        assertEquals(1, cache.writeCount)
        assertEquals(schedule, cache.stored?.schedule)
        assertEquals(PrayerAuthority.LOCAL_CALC, cache.stored?.provenance?.authority)
    }

    @Test
    fun `cache write failure does not affect the emitted VERIFIED_CURRENT context`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()
        cache.writeErrorToReturn = OperationError.UnknownError("disk full")

        val contexts = useCase().get().toList()

        val available = contexts.single() as PrayerContext.Available
        assertEquals(Freshness.VERIFIED_CURRENT, available.freshness)
    }

    @Test
    fun `cache read failure is treated as no cache`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()
        cache.stored = CachedPrayerSchedule(scheduleFor(TODAY_ISTANBUL), provenanceFor(turkeySetup()))
        cache.readErrorToReturn = OperationError.UnknownError("corrupt")

        val contexts = useCase().get().toList()

        // No CACHED_FRESH first emission — the unreadable cache is ignored, not trusted.
        assertEquals(1, contexts.size)
        assertEquals(Freshness.VERIFIED_CURRENT, (contexts.single() as PrayerContext.Available).freshness)
    }

    // ── Cache-first emission ───────────────────────────────────────────────────

    @Test
    fun `same-day same-setup cache emits CACHED_FRESH immediately then VERIFIED_CURRENT`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        val cachedSchedule = scheduleFor(TODAY_ISTANBUL)
        val cachedProvenance = provenanceFor(setup)
        cache.stored = CachedPrayerSchedule(cachedSchedule, cachedProvenance)
        scriptCalcSuccess(scheduleFor(TODAY_ISTANBUL))

        val contexts = useCase().get().toList()

        assertEquals(2, contexts.size)
        val first = contexts[0] as PrayerContext.Available
        assertEquals(Freshness.CACHED_FRESH, first.freshness)
        assertEquals(cachedSchedule, first.schedule)
        assertEquals(cachedProvenance, first.provenance)
        val second = contexts[1] as PrayerContext.Available
        assertEquals(Freshness.VERIFIED_CURRENT, second.freshness)
    }

    @Test
    fun `same-day cache with recompute failure emits CACHED_FRESH then CACHED_STALE`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        val cachedSchedule = scheduleFor(TODAY_ISTANBUL)
        cache.stored = CachedPrayerSchedule(cachedSchedule, provenanceFor(setup))
        scriptCalcFailure()

        val contexts = useCase().get().toList()

        assertEquals(
            listOf(Freshness.CACHED_FRESH, Freshness.CACHED_STALE),
            contexts.map { (it as PrayerContext.Available).freshness },
        )
        // Stale still shows the labelled cached schedule — never silently hidden (spec §2).
        assertEquals(cachedSchedule, (contexts[1] as PrayerContext.Available).schedule)
    }

    @Test
    fun `previous-day cache with recompute failure emits only CACHED_STALE`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        val yesterday = TODAY_ISTANBUL.minusDays(1)
        cache.stored = CachedPrayerSchedule(scheduleFor(yesterday), provenanceFor(setup))
        scriptCalcFailure()

        val contexts = useCase().get().toList()

        assertEquals(1, contexts.size)
        val available = contexts.single() as PrayerContext.Available
        assertEquals(Freshness.CACHED_STALE, available.freshness)
        assertEquals(yesterday, available.schedule.dateInLocationTz)
    }

    @Test
    fun `previous-day cache with recompute success emits only VERIFIED_CURRENT`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        cache.stored = CachedPrayerSchedule(scheduleFor(TODAY_ISTANBUL.minusDays(1)), provenanceFor(setup))
        scriptCalcSuccess()

        val contexts = useCase().get().toList()

        assertEquals(1, contexts.size)
        assertEquals(Freshness.VERIFIED_CURRENT, (contexts.single() as PrayerContext.Available).freshness)
    }

    @Test
    fun `setup-mismatched cache is not usable - recompute failure emits CALCULATION_FAILED`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        // Cached under a different method: showing it would contradict the user's current choice.
        val otherProvenance = provenanceFor(setup).copy(methodId = CalculationMethodId("MWL"))
        cache.stored = CachedPrayerSchedule(scheduleFor(TODAY_ISTANBUL), otherProvenance)
        scriptCalcFailure()

        val contexts = useCase().get().toList()

        assertEquals(
            listOf<PrayerContext>(PrayerContext.Unavailable(UnavailableReason.CALCULATION_FAILED)),
            contexts,
        )
    }

    // ── Next-prayer derivation ─────────────────────────────────────────────────

    @Test
    fun `nextPrayer is the first future prayer in the location timezone`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess() // NOW = 13:00 local; Dhuhr 13:10 is next.

        val available = useCase().get().toList().single() as PrayerContext.Available

        assertEquals(PrayerName.DHUHR, available.nextPrayer)
    }

    @Test
    fun `nextPrayer is null after Isha - no untruthful highlight of a past time`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()
        // 23:30 local Istanbul, still 2026-07-13 — every prayer of the day has passed.
        val lateClock = Clock.fixed(Instant.parse("2026-07-13T20:30:00Z"), ISTANBUL_ZONE)

        val available = useCase(lateClock).get().toList().single() as PrayerContext.Available

        assertNull(available.nextPrayer)
    }

    // ── Location-timezone day boundary + timezone conflict ────────────────────

    @Test
    fun `day boundary uses the location timezone not the device timezone`() = runTest {
        val setup = turkeySetup(location = tokyoLocation())
        prefs.saveSetup(setup)
        // Device on UTC, 2026-07-13T22:00Z — in Tokyo it is already 2026-07-14.
        val clock = Clock.fixed(Instant.parse("2026-07-13T22:00:00Z"), ZoneId.of("UTC"))
        val tokyoToday = LocalDate.of(2026, 7, 14)
        cache.stored = CachedPrayerSchedule(
            scheduleFor(tokyoToday, TOKYO_ZONE),
            provenanceFor(setup),
        )
        scriptCalcSuccess(scheduleFor(tokyoToday, TOKYO_ZONE))

        val contexts = useCase(clock).get().toList()

        // Calculator was asked for Tokyo's civil date, not the device's.
        assertEquals(tokyoToday, calculator.receivedRequests.single().dateInLocationTz)
        // And the Tokyo-dated cache counts as same-day fresh even though the device date differs.
        assertEquals(Freshness.CACHED_FRESH, (contexts[0] as PrayerContext.Available).freshness)
    }

    @Test
    fun `timeZoneState is CONFLICT when the device zone differs from the location tzId`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()
        val deviceOnUtc = Clock.fixed(NOW, ZoneId.of("UTC"))

        val available = useCase(deviceOnUtc).get().toList().single() as PrayerContext.Available

        assertEquals(TimeZoneState.CONFLICT, available.timeZoneState)
    }

    @Test
    fun `timeZoneState is MATCHES_DEVICE when the device zone equals the location tzId`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()

        val available = useCase().get().toList().single() as PrayerContext.Available

        assertEquals(TimeZoneState.MATCHES_DEVICE, available.timeZoneState)
    }

    // ── locationTzId (Task 9 Step 0 correctness fix) ───────────────────────────

    @Test
    fun `freshly computed Available carries the location tzId, not the device tzId`() = runTest {
        prefs.saveSetup(turkeySetup())
        scriptCalcSuccess()
        val deviceOnUtc = Clock.fixed(NOW, ZoneId.of("UTC"))

        val available = useCase(deviceOnUtc).get().toList().single() as PrayerContext.Available

        assertEquals("Europe/Istanbul", available.locationTzId)
    }

    @Test
    fun `cached Available (fresh and stale) also carries the location tzId`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        val cachedSchedule = scheduleFor(TODAY_ISTANBUL)
        cache.stored = CachedPrayerSchedule(cachedSchedule, provenanceFor(setup))
        scriptCalcSuccess(scheduleFor(TODAY_ISTANBUL))

        val contexts = useCase().get().toList()

        assertEquals(2, contexts.size)
        contexts.forEach { context ->
            assertEquals("Europe/Istanbul", (context as PrayerContext.Available).locationTzId)
        }
    }

    @Test
    fun `every Available emission carries provenance by construction`() = runTest {
        val setup = turkeySetup()
        prefs.saveSetup(setup)
        cache.stored = CachedPrayerSchedule(scheduleFor(TODAY_ISTANBUL), provenanceFor(setup))
        scriptCalcSuccess()

        val contexts = useCase().get().toList()

        assertTrue(contexts.isNotEmpty())
        contexts.forEach { context ->
            val available = context as PrayerContext.Available
            // Non-null by type; this asserts the label survived end-to-end.
            assertEquals("Istanbul", available.provenance.locationLabel)
        }
    }
}
