package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.junit4.createComposeRule
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * I18N-1 Task 13 fix round 1 (reviewer finding 2): a rendering-level test that resolves
 * [prayerProvenanceText] through real string resources and pins the exact resulting sentence.
 *
 * [PrayerSummaryMapperTest] covers the pure identifier plumbing (`toHomePrayerSummaryUi` producing
 * the right [PrayerProvenanceUi]) but, since I18N-1's refactor moved text resolution behind
 * `@Composable`/`sidrString`, nothing in that plain-JVM test can call [prayerProvenanceText] itself.
 * [MethodLabelCoverageTest] only asserts every catalog method has *some* non-null label resource - a
 * swapped mapping (e.g. `"MWL" -> R.string.launcher_prayer_method_egyptian`) would pass every existing
 * test while rendering the wrong prayer-method name on the DS-6B provenance line. This test closes
 * that gap: it drives [prayerProvenanceText] end to end (`methodLabelResId` -> `sidrString` -> the
 * `LOCAL CALC · %1$s · %2$s` frame) and asserts the exact byte-for-byte sentence, mirroring
 * `SidrStringsTest`'s `createComposeRule` + direct-capture pattern in `core/ui`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrayerProvenanceTextTest {

    @get:Rule val compose = createComposeRule()

    @Test fun `resolves the full LOCAL CALC sentence for Umm al-Qura and Hanafi`() {
        var actual = ""
        val provenance = PrayerProvenanceUi(
            methodId = CalculationMethodId("UMM_AL_QURA"),
            madhab = Madhab.HANAFI,
        )

        compose.setContent { actual = prayerProvenanceText(provenance) }

        assertEquals("LOCAL CALC · Umm al-Qura University, Makkah · Hanafi", actual)
    }

    @Test fun `resolves the full LOCAL CALC sentence for MWL and Standard`() {
        var actual = ""
        val provenance = PrayerProvenanceUi(
            methodId = CalculationMethodId("MWL"),
            madhab = Madhab.STANDARD,
        )

        compose.setContent { actual = prayerProvenanceText(provenance) }

        assertEquals("LOCAL CALC · Muslim World League · Standard", actual)
    }
}
