package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrPreviewTest {
    @get:Rule val compose = createComposeRule()

    @Test fun badge_shows_preview_label() {
        compose.setContent { SidrTheme(darkTheme = true) { SidrPreviewBadge() } }
        compose.onNodeWithText("PREVIEW").assertIsDisplayed()
    }

    @Test fun banner_shows_not_live_copy() {
        compose.setContent { SidrTheme(darkTheme = true) { SidrPreviewBanner() } }
        compose.onNodeWithText("PREVIEW — this screen is a design of what's coming; it isn't live yet.")
            .assertIsDisplayed()
    }
}
