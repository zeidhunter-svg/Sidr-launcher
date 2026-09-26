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
 * Vision MVP Task 8: TasksPreviewScreen is a non-functional design preview of the future
 * intent → plan → execution → result flow. This test only checks the two load-bearing markers
 * that keep the preview honest: the PREVIEW banner heading, and the verbatim
 * "PLAN ≠ EXECUTION · nothing runs yet" copy that tells the user nothing on this screen executes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TasksPreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preview_banner_heading_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                TasksPreviewScreen()
            }
        }
        compose.onNodeWithText(
            "PREVIEW — this screen is a design of what's coming; it isn't live yet.",
        ).assertIsDisplayed()
    }

    @Test fun plan_not_execution_copy_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                TasksPreviewScreen()
            }
        }
        // The screen is a long verticalScroll Column; scroll the target into view first (Robolectric's
        // default test window is short, so lower sections start out laid out but off-screen).
        compose.onNodeWithText("PLAN ≠ EXECUTION · nothing runs yet").performScrollTo().assertIsDisplayed()
    }
}
