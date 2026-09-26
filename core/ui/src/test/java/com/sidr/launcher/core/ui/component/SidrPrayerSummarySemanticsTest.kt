package com.sidr.launcher.core.ui.component

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DS-6B Task 7 semantics tests (spec §8): the next prayer is distinguishable by label/marker text, not
 * colour alone; [SidrPrayerSummary] is clickable with `Role.Button` only when `onOpenDetails` is
 * supplied; calm vs. degraded statuses map to the intended [SidrStatus] tokens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrPrayerSummarySemanticsTest {
    @get:Rule val compose = createComposeRule()

    private val schedule = listOf(
        SidrPrayerTimeUi(name = "Fajr", time = "05:12"),
        SidrPrayerTimeUi(name = "Asr", time = "15:42", isNext = true),
    )

    @Test fun next_prayer_is_distinguishable_by_label_and_marker_not_colour() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrayerSummary(
                    prayers = schedule,
                    status = SidrPrayerSummaryStatus.VerifiedCurrent,
                    provenance = "astronomical authority",
                )
            }
        }
        // The strip shows times only; the next cell still carries a marker glyph in its visible text and
        // an explicit "Next" content description, and each cell names its prayer via content description —
        // so which time is which, and which is next, survives in greyscale/TalkBack (never colour alone).
        compose.onNodeWithText("▸ 15:42").assertIsDisplayed()
        compose.onNodeWithContentDescription("Next ASR 15:42").assertIsDisplayed()
        compose.onNodeWithText("05:12").assertIsDisplayed()
        compose.onNodeWithContentDescription("FAJR 05:12").assertIsDisplayed()
    }

    @Test fun strip_is_not_clickable_when_on_open_details_is_null() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrayerSummary(
                    prayers = emptyList(),
                    status = SidrPrayerSummaryStatus.NoData,
                    provenance = "",
                )
            }
        }
        compose.onNodeWithContentDescription("NO DATA", substring = true).assertHasNoClickAction()
    }

    @Test fun strip_is_a_button_when_on_open_details_is_supplied() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrayerSummary(
                    prayers = emptyList(),
                    status = SidrPrayerSummaryStatus.NoData,
                    provenance = "",
                    onOpenDetails = {},
                )
            }
        }
        compose.onNodeWithContentDescription("NO DATA", substring = true)
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
    }

    @Test fun calm_statuses_stay_off_the_alarming_tokens() {
        assertEquals(SidrStatus.SUCCESS, SidrPrayerSummaryStatus.VerifiedCurrent.statusToken())
        assertEquals(SidrStatus.INFO, SidrPrayerSummaryStatus.CachedFresh.statusToken())
        assertEquals(SidrStatus.INFO, SidrPrayerSummaryStatus.ManualLocation.statusToken())
        assertEquals(SidrStatus.INFO, SidrPrayerSummaryStatus.Updating.statusToken())
    }

    @Test fun degraded_statuses_use_attention_or_danger() {
        assertEquals(SidrStatus.ATTENTION, SidrPrayerSummaryStatus.CachedStale.statusToken())
        assertEquals(SidrStatus.DANGER, SidrPrayerSummaryStatus.CalculationFailed.statusToken())
        assertEquals(SidrStatus.ATTENTION, SidrPrayerSummaryStatus.AuthorityUnavailable.statusToken())
        assertEquals(SidrStatus.ATTENTION, SidrPrayerSummaryStatus.LocationUnavailable.statusToken())
        assertEquals(SidrStatus.ATTENTION, SidrPrayerSummaryStatus.MethodRequired.statusToken())
    }
}
