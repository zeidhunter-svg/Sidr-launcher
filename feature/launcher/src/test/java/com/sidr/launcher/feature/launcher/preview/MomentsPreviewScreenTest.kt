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
 * Vision MVP Task 12: MomentsPreviewScreen is a non-functional design preview of the Result /
 * Partial / Error interaction moments. This test only checks the three status-chip markers that
 * distinguish the sample moments from each other; every button on this screen is an inert no-op.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MomentsPreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun result_completed_chip_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                MomentsPreviewScreen(onBack = {})
            }
        }
        compose.onNodeWithText("✓ COMPLETED").assertIsDisplayed()
    }

    @Test fun partial_chip_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                MomentsPreviewScreen(onBack = {})
            }
        }
        compose.onNodeWithText("! PARTIALLY COMPLETED").performScrollTo().assertIsDisplayed()
    }

    @Test fun error_chip_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                MomentsPreviewScreen(onBack = {})
            }
        }
        compose.onNodeWithText("✕ ACTION FAILED").performScrollTo().assertIsDisplayed()
    }
}
