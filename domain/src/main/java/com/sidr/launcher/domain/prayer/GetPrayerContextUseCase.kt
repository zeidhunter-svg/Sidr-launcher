package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.result.OperationResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

/**
 * Pure orchestration of the truthful prayer context (DS-6B Task 3).
 *
 * Contract (plan + spec §0.6/§6):
 * - no setup → `Unavailable(NOT_CONFIGURED)`; setup without location → `Unavailable(LOCATION_MISSING)`
 * - cache same-day (location tz) + same-setup → emit `CACHED_FRESH` immediately, then recompute →
 *   `VERIFIED_CURRENT`
 * - recompute failure with a usable (same-setup) cache → `CACHED_STALE`; without one →
 *   `Unavailable(CALCULATION_FAILED)`
 * - day boundary and `nextPrayer` are derived in the **location** timezone
 * - `timeZoneState = CONFLICT` when the device zone (from the injected [clock]) ≠ the location tzId
 *
 * A cache is *usable* only when its provenance matches the CURRENT setup (authority, method,
 * madhab, location label): times computed under settings the user has since changed are never
 * shown, stale-labelled or otherwise. An unreadable cache is treated as absent, and a cache-write
 * failure never affects the emitted context — persistence is best-effort, truth is not.
 *
 * [get] is a **cold** flow (the `DefaultGenerativeRouter` precedent): each collection snapshots
 * the setup and emits one or two [PrayerContext] values, then completes. Expected failures are
 * values ([PrayerContext.Unavailable]), never exceptions.
 *
 * [clock] is the single injected time/zone source so device timezone and "now" are testable —
 * production passes `Clock.systemDefaultZone()`.
 */
class GetPrayerContextUseCase(
    private val preferences: PrayerPreferencesRepository,
    private val cache: PrayerScheduleCache,
    private val calculator: PrayerCalculator,
    private val clock: Clock,
) {
    fun get(): Flow<PrayerContext> = flow {
        val setup = preferences.setup().firstOrNull()
        if (setup == null) {
            emit(PrayerContext.Unavailable(UnavailableReason.NOT_CONFIGURED))
            return@flow
        }
        val location = setup.location
        if (location == null) {
            emit(PrayerContext.Unavailable(UnavailableReason.LOCATION_MISSING))
            return@flow
        }
        // An unresolvable zone id means no day boundary can be derived truthfully — fail visibly
        // (spec §2) rather than fall back to the device zone.
        val locationZone = runCatching { ZoneId.of(location.tzId) }.getOrNull()
        if (locationZone == null) {
            emit(PrayerContext.Unavailable(UnavailableReason.CALCULATION_FAILED))
            return@flow
        }

        val now = clock.instant()
        val today = LocalDate.ofInstant(now, locationZone)
        val timeZoneState =
            if (clock.zone == locationZone) TimeZoneState.MATCHES_DEVICE else TimeZoneState.CONFLICT

        // Unreadable cache = no cache; a cached schedule is only usable under the current setup.
        val cached = (cache.read() as? OperationResult.Success)?.value
        val usableCache = cached?.takeIf { matchesSetup(it.provenance, setup, location) }

        if (usableCache != null && usableCache.schedule.dateInLocationTz == today) {
            emit(usableCache.toAvailable(Freshness.CACHED_FRESH, timeZoneState, now))
        }

        when (val result = calculator.calculate(location, setup.methodId, setup.madhab, today)) {
            is OperationResult.Success -> {
                val provenance = PrayerScheduleProvenance(
                    authority = PrayerAuthority.LOCAL_CALC,
                    methodId = setup.methodId,
                    madhab = setup.madhab,
                    locationLabel = location.label,
                    computedAtMillis = now.toEpochMilli(),
                )
                // Best-effort persist for the next first frame; a write failure never taints the
                // freshly verified context.
                cache.write(CachedPrayerSchedule(result.value, provenance))
                emit(
                    PrayerContext.Available(
                        schedule = result.value,
                        provenance = provenance,
                        freshness = Freshness.VERIFIED_CURRENT,
                        timeZoneState = timeZoneState,
                        nextPrayer = nextPrayer(result.value, now),
                    ),
                )
            }

            is OperationResult.Failure ->
                if (usableCache != null) {
                    emit(usableCache.toAvailable(Freshness.CACHED_STALE, timeZoneState, now))
                } else {
                    emit(PrayerContext.Unavailable(UnavailableReason.CALCULATION_FAILED))
                }
        }
    }

    /** Same-setup check: v1 provenance is always local calculation under the current choices. */
    private fun matchesSetup(
        provenance: PrayerScheduleProvenance,
        setup: PrayerSetup,
        location: PrayerLocation,
    ): Boolean = provenance.authority == PrayerAuthority.LOCAL_CALC &&
        provenance.methodId == setup.methodId &&
        provenance.madhab == setup.madhab &&
        provenance.locationLabel == location.label

    private fun CachedPrayerSchedule.toAvailable(
        freshness: Freshness,
        timeZoneState: TimeZoneState,
        now: Instant,
    ): PrayerContext.Available = PrayerContext.Available(
        schedule = schedule,
        provenance = provenance,
        freshness = freshness,
        timeZoneState = timeZoneState,
        nextPrayer = nextPrayer(schedule, now),
    )

    /**
     * First of the five prayers still ahead of [now]; `null` once all of today's prayers have
     * passed (no tomorrow-instant exists — highlighting FAJR against a past time would lie).
     * Sunrise never participates: it lives outside [PrayerDaySchedule.instants] by construction.
     */
    private fun nextPrayer(schedule: PrayerDaySchedule, now: Instant): PrayerName? {
        val nowMillis = now.toEpochMilli()
        return schedule.instants
            .filter { it.epochMillis > nowMillis }
            .minByOrNull { it.epochMillis }
            ?.name
    }
}
