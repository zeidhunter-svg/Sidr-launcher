package com.sidr.launcher.data.prayer

import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.SupportedPrayerMethods
import com.sidr.launcher.domain.result.OperationResult
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-drift guard (DS-6B Task 8): [SupportedPrayerMethods.ALL] is the ONLY catalog the UI is allowed
 * to offer at setup — this test proves [AdhanPrayerCalculator] accepts EVERY entry in it, for a real
 * city/date, so the UI can never offer a method the calculation adapter silently rejects (which would
 * otherwise surface only as a runtime `CALCULATION_FAILED`, never caught by a type system).
 *
 * Not a golden/precision test (see [AdhanPrayerCalculatorGoldenTest] for that) — only that every
 * catalog entry maps to a real adhan2 [com.batoulapps.adhan2.CalculationMethod] and produces a
 * successful, well-formed schedule.
 */
class AdhanPrayerCalculatorSupportedMethodsTest {

    private val calculator = AdhanPrayerCalculator()

    private val istanbul = PrayerLocation(
        label = "Istanbul",
        lat2dp = 41.01,
        lon2dp = 28.98,
        tzId = "Europe/Istanbul",
        source = PrayerLocationSource.CITY,
    )

    @Test
    fun `every supported prayer method produces a successful schedule`() = runTest {
        assertTrue("expected a non-empty method catalog", SupportedPrayerMethods.ALL.isNotEmpty())

        SupportedPrayerMethods.ALL.forEach { method ->
            val result = calculator.calculate(
                location = istanbul,
                methodId = method.id,
                madhab = Madhab.STANDARD,
                dateInLocationTz = LocalDate.of(2026, 8, 7),
            )
            assertTrue(
                "expected Success for catalog method ${method.id.key} (${method.displayLabel}) but was $result",
                result is OperationResult.Success,
            )
        }
    }

    @Test
    fun `every supported prayer method also works under the HANAFI madhab`() = runTest {
        SupportedPrayerMethods.ALL.forEach { method ->
            val result = calculator.calculate(
                location = istanbul,
                methodId = method.id,
                madhab = Madhab.HANAFI,
                dateInLocationTz = LocalDate.of(2026, 8, 7),
            )
            assertTrue(
                "expected Success for catalog method ${method.id.key} under HANAFI but was $result",
                result is OperationResult.Success,
            )
        }
    }
}
