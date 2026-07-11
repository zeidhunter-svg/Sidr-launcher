package com.sidr.launcher.feature.launcher.preview

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vision MVP Task 9: AgentsPreviewScreen is a non-functional design preview of a single sample
 * agent card. This test only checks the two load-bearing markers that keep the preview honest:
 * the PREVIEW banner heading, and the sample agent's title text.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentsPreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun preview_banner_heading_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentsPreviewScreen()
            }
        }
        compose.onNodeWithText(
            "PREVIEW — this screen is a design of what's coming; it isn't live yet.",
        ).assertIsDisplayed()
    }

    @Test fun sample_agent_title_is_displayed() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentsPreviewScreen()
            }
        }
        compose.onNodeWithText("Research agent").assertIsDisplayed()
    }
}
