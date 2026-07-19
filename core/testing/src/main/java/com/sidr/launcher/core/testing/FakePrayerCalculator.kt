package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerCalculator
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.time.LocalDate

/**
 * Scripted fake [PrayerCalculator] (DS-6B Task 3). Returns [resultToReturn] verbatim and records
 * every call in [receivedRequests]. Defaults to failure so a test must script success explicitly —
 * no accidental plausible schedule. Not wired into any Hilt graph — use directly in unit tests.
 */
class FakePrayerCalculator : PrayerCalculator {

    data class Request(
        val location: PrayerLocation,
        val methodId: CalculationMethodId,
        val madhab: Madhab,
        val dateInLocationTz: LocalDate,
    )

    var resultToReturn: OperationResult<PrayerDaySchedule> =
        OperationResult.Failure(OperationError.UnknownError("FakePrayerCalculator not scripted"))

    val receivedRequests = mutableListOf<Request>()

    val callCount: Int get() = receivedRequests.size

    override suspend fun calculate(
        location: PrayerLocation,
        methodId: CalculationMethodId,
        madhab: Madhab,
        dateInLocationTz: LocalDate,
    ): OperationResult<PrayerDaySchedule> {
        receivedRequests += Request(location, methodId, madhab, dateInLocationTz)
        return resultToReturn
    }
}
