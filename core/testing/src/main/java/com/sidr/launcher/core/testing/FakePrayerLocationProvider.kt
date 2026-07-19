package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationProvider
import com.sidr.launcher.domain.result.OperationResult

/**
 * Scripted fake [PrayerLocationProvider] (DS-6B Task 3). Returns [resultToReturn] verbatim;
 * defaults to `Success(null)` = no fix available. Not wired into any Hilt graph — use directly
 * in unit tests.
 */
class FakePrayerLocationProvider : PrayerLocationProvider {

    var resultToReturn: OperationResult<PrayerLocation?> = OperationResult.Success(null)

    var callCount: Int = 0
        private set

    override suspend fun currentLocation(): OperationResult<PrayerLocation?> {
        callCount++
        return resultToReturn
    }
}
