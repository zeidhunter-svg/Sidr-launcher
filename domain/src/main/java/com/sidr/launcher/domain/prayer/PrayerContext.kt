package com.sidr.launcher.domain.prayer

/**
 * What Home may truthfully render about prayer times (DS-6B Task 3).
 *
 * "No schedule without provenance" (spec §2) is structural: [Available] carries a non-null
 * [PrayerScheduleProvenance] by type, its schedule can never have empty or unlabelled instants
 * ([PrayerDaySchedule] enforces exactly the five named prayers), and there is no third variant —
 * either a fully labelled schedule exists or the reason it doesn't is explicit.
 */
sealed interface PrayerContext {

    /** No truthful schedule can be shown; [reason] is explicit — never silently hidden (spec §2). */
    data class Unavailable(val reason: UnavailableReason) : PrayerContext

    /**
     * A schedule that may be rendered, always with [provenance] and [freshness].
     *
     * [nextPrayer] is the first of the five prayers still ahead of "now" in the **location**
     * timezone, or `null` when all of today's prayers have passed (the schedule holds no
     * tomorrow-instant, and highlighting FAJR against a past time would be untruthful).
     *
     * [locationTzId] is the IANA zone id the schedule was computed in (spec §6). The five
     * [PrayerInstant.epochMillis] values are absolute instants — formatting them as a wall-clock
     * time requires THIS zone, never the device zone, or a TZ CONFLICT would render wrong times.
     */
    data class Available(
        val schedule: PrayerDaySchedule,
        val provenance: PrayerScheduleProvenance,
        val freshness: Freshness,
        val timeZoneState: TimeZoneState,
        val nextPrayer: PrayerName?,
        val locationTzId: String,
    ) : PrayerContext {
        init {
            // Defensive restatement of the plan's hard rule: an Available context can never carry
            // an empty schedule. [PrayerDaySchedule] already makes this unrepresentable (it requires
            // exactly the five labelled prayers), so this guard is unreachable today — it exists so
            // the invariant survives any future loosening of the schedule model.
            require(schedule.instants.isNotEmpty()) {
                "PrayerContext.Available must carry a non-empty, labelled schedule."
            }
        }
    }
}
