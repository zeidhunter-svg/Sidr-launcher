package com.sidr.launcher.data.prayer

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.data.DateComponents
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerCalculator
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException
import com.batoulapps.adhan2.Madhab as AdhanMadhab

/**
 * [PrayerCalculator] implementation over adhan2 — the Kotlin port of "adhan"
 * (`com.batoulapps.adhan:adhan2:0.0.5`, MIT licensed; see
 * [AdhanPrayerCalculatorGoldenTest] for the full license/version record). DS-6B Task 4.
 *
 * This is the only file in the codebase that imports adhan2 — domain never sees it (spec §10).
 *
 * [dateInLocationTz] is used directly to build adhan2's civil [DateComponents] (year/month/day) —
 * never re-derived from any device clock or system-default timezone. adhan2's own astronomical
 * calculation is timezone-agnostic (it only needs coordinates + a civil year/month/day to pick the
 * correct day-of-year); the caller is responsible for interpreting the returned [PrayerInstant]s'
 * `epochMillis` through the location's own timezone.
 *
 * Never throws an expected failure: an unsupported [methodId], or any adhan2 calculation failure
 * (e.g. an unreachable schedule at extreme polar latitudes), maps to [OperationResult.Failure].
 * [CancellationException] is always re-thrown, never swallowed.
 */
class AdhanPrayerCalculator : PrayerCalculator {

    override suspend fun calculate(
        location: PrayerLocation,
        methodId: CalculationMethodId,
        madhab: Madhab,
        dateInLocationTz: LocalDate,
    ): OperationResult<PrayerDaySchedule> {
        val method = METHOD_MAP[methodId.key]
            ?: return OperationResult.Failure(
                OperationError.UnknownError("Unsupported calculation method key: ${methodId.key}"),
            )

        return try {
            val params = method.parameters.copy(madhab = madhab.toAdhanMadhab())
            val coordinates = Coordinates(location.lat2dp, location.lon2dp)
            val dateComponents = DateComponents(
                dateInLocationTz.year,
                dateInLocationTz.monthValue,
                dateInLocationTz.dayOfMonth,
            )

            val prayerTimes = PrayerTimes(coordinates, dateComponents, params)

            val instants = PrayerName.FIVE_PRAYERS.map { name ->
                PrayerInstant(name, prayerTimes.instantFor(name).toEpochMilliseconds())
            }
            val sunrise = PrayerInstant(PrayerName.SUNRISE, prayerTimes.sunrise.toEpochMilliseconds())

            OperationResult.Success(
                PrayerDaySchedule(
                    dateInLocationTz = dateInLocationTz,
                    instants = instants,
                    sunrise = sunrise,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OperationResult.Failure(
                OperationError.UnknownError(e.message ?: "adhan2 calculation failed for method ${methodId.key}"),
            )
        }
    }

    private fun Madhab.toAdhanMadhab(): AdhanMadhab = when (this) {
        Madhab.STANDARD -> AdhanMadhab.SHAFI
        Madhab.HANAFI -> AdhanMadhab.HANAFI
    }

    private fun PrayerTimes.instantFor(name: PrayerName) = when (name) {
        PrayerName.FAJR -> fajr
        PrayerName.DHUHR -> dhuhr
        PrayerName.ASR -> asr
        PrayerName.MAGHRIB -> maghrib
        PrayerName.ISHA -> isha
        PrayerName.SUNRISE -> sunrise
    }

    private companion object {
        /**
         * [CalculationMethodId.key] -> adhan2 [CalculationMethod] (DS-6B Task 8 — expanded from the
         * Task 4 minimum set to the full standard catalog in
         * [com.sidr.launcher.domain.prayer.SupportedPrayerMethods]). Every key here MUST cover every
         * [com.sidr.launcher.domain.prayer.SupportedPrayerMethods.ALL] entry — enforced by
         * `AdhanPrayerCalculatorSupportedMethodsTest`. Every named adhan2 `CalculationMethod` is
         * mapped except `OTHER` (generic/uncalibrated bucket, never offered as a user choice).
         */
        val METHOD_MAP: Map<String, CalculationMethod> = mapOf(
            "MWL" to CalculationMethod.MUSLIM_WORLD_LEAGUE,
            "EGYPTIAN" to CalculationMethod.EGYPTIAN,
            "KARACHI" to CalculationMethod.KARACHI,
            "UMM_AL_QURA" to CalculationMethod.UMM_AL_QURA,
            "DUBAI" to CalculationMethod.DUBAI,
            "MOON_SIGHTING_COMMITTEE" to CalculationMethod.MOON_SIGHTING_COMMITTEE,
            "NORTH_AMERICA" to CalculationMethod.NORTH_AMERICA,
            "KUWAIT" to CalculationMethod.KUWAIT,
            "QATAR" to CalculationMethod.QATAR,
            "SINGAPORE" to CalculationMethod.SINGAPORE,
            "TURKEY" to CalculationMethod.TURKEY,
        )
    }
}
