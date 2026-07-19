package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.result.OperationResult
import java.time.LocalDate

/**
 * Port: computes one day's schedule fully offline (DS-6B Task 3).
 *
 * The implementation (Task 4, `:data:prayer`) wraps a maintained calculation library — domain never
 * sees it (spec §10). [dateInLocationTz] is the civil date in the **location** timezone (spec §6);
 * the caller (not the calculator) owns the day-boundary decision. Method and madhab are always
 * passed explicitly — there is no default anywhere (spec §0.2). Never throws expected failures:
 * an unsupported method key, an unresolvable timezone, or any calculation error surfaces as
 * [OperationResult.Failure].
 */
interface PrayerCalculator {
    suspend fun calculate(
        location: PrayerLocation,
        methodId: CalculationMethodId,
        madhab: Madhab,
        dateInLocationTz: LocalDate,
    ): OperationResult<PrayerDaySchedule>
}
