package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.prayer.SupportedPrayerMethods
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-drift guard (I18N-1 Task 13, mirrors `feature/prayer`'s `MethodLabelCoverageTest`).
 * [com.sidr.launcher.domain.prayer.CalculationMethodId] wraps a plain `String` key (a non-sealed
 * value class), so [methodLabelResId]'s `when` can never be made compiler-exhaustive the way an enum
 * `when` can. Without this test, a method added to [SupportedPrayerMethods.ALL] without a matching
 * branch in [methodLabelResId] would silently fall through to [PrayerSummaryMapper]'s private
 * `methodLabel()` `?:` fallback and render its raw, untranslated key (e.g. `"TURKEY"` instead of the
 * Diyanet label) on the Home prayer strip's most terminology-sensitive surface — nothing else in the
 * type system would catch it.
 *
 * Drives the loop off the real [SupportedPrayerMethods.ALL] catalog (not a hand-copied list), calls
 * the real [methodLabelResId] once per entry, and asserts on the real return value with a message
 * naming the offending key. [methodLabelResId] is deliberately a plain (non-`@Composable`) function
 * precisely so this can run as an ordinary JVM test with no Robolectric/Compose test rule.
 */
class MethodLabelCoverageTest {

    @Test fun `every supported prayer method has a locked label resource`() {
        assertTrue("expected a non-empty method catalog", SupportedPrayerMethods.ALL.isNotEmpty())

        SupportedPrayerMethods.ALL.forEach { method ->
            assertNotNull(
                "no methodLabelResId() branch for catalog method '${method.id.key}' " +
                    "(${method.displayLabel}) - add a launcher_prayer_method_* locked string and a " +
                    "matching branch in PrayerSummaryMapper.kt, or this method silently renders as " +
                    "its raw key",
                methodLabelResId(method.id),
            )
        }
    }
}
