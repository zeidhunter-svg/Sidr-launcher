package com.sidr.launcher.feature.launcher.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vision MVP Task 10: ActivityPreviewScreen is a non-functional design preview of a sample
 * activity timeline. This test only checks the two load-bearing markers that keep the preview
 * honest: the PREVIEW banner heading, and the verbatim
 * "EPHEMERAL BY DEFAULT · OPT-IN PERSIST" copy that tells the user nothing on this screen is a
 * real, persisted activity log.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActivityPreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preview_banner_heading_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                ActivityPreviewScreen()
            }
        }
        compose.onNodeWithText(
            "PREVIEW — this screen is a design of what's coming; it isn't live yet.",
        ).assertIsDisplayed()
    }

    @Test fun ephemeral_by_default_copy_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                ActivityPreviewScreen()
            }
        }
        // The screen is a long verticalScroll Column; scroll the target into view first (Robolectric's
        // default test window is short, so lower sections start out laid out but off-screen).
        compose.onNodeWithText("EPHEMERAL BY DEFAULT · OPT-IN PERSIST").performScrollTo().assertIsDisplayed()
    }
}
