package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.prayer.CachedPrayerSchedule
import com.sidr.launcher.domain.prayer.PrayerScheduleCache
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult

/**
 * In-memory fake [PrayerScheduleCache] (DS-6B Task 3). Seed [stored] to simulate an existing
 * cache; flip [readErrorToReturn]/[writeErrorToReturn] to script failures. Not wired into any
 * Hilt graph — use directly in unit tests.
 */
class FakePrayerScheduleCache(
    var stored: CachedPrayerSchedule? = null,
) : PrayerScheduleCache {

    /** When non-null, [read] returns [OperationResult.Failure] with this error. */
    var readErrorToReturn: OperationError? = null

    /** When non-null, [write] returns [OperationResult.Failure] and stores nothing. */
    var writeErrorToReturn: OperationError? = null

    var writeCount: Int = 0
        private set

    override suspend fun read(): OperationResult<CachedPrayerSchedule?> {
        val error = readErrorToReturn
        return if (error != null) OperationResult.Failure(error) else OperationResult.Success(stored)
    }

    override suspend fun write(entry: CachedPrayerSchedule): OperationResult<Unit> {
        writeCount++
        val error = writeErrorToReturn
        return if (error != null) {
            OperationResult.Failure(error)
        } else {
            stored = entry
            OperationResult.Success(Unit)
        }
    }

    override suspend fun clear(): OperationResult<Unit> {
        stored = null
        return OperationResult.Success(Unit)
    }
}
