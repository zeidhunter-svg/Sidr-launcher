package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.result.OperationResult

/**
 * A cached schedule is never unlabelled: the schedule and the provenance it was computed under
 * travel together by type (spec §2 — "no schedule without provenance" holds for cache reads too).
 */
data class CachedPrayerSchedule(
    val schedule: PrayerDaySchedule,
    val provenance: PrayerScheduleProvenance,
)

/**
 * Port: the last computed schedule + its provenance (DS-6B Task 3). Exists only for instant
 * first-frame render (spec §0.6); freshness is decided by the use case, not the cache.
 * [read] returning `Success(null)` = no cache yet; failures are values, never thrown.
 */
interface PrayerScheduleCache {
    suspend fun read(): OperationResult<CachedPrayerSchedule?>

    suspend fun write(entry: CachedPrayerSchedule): OperationResult<Unit>

    suspend fun clear(): OperationResult<Unit>
}
