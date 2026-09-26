package com.sidr.launcher.core.ui.primitive

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrimitiveSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun provenance_exposes_composed_description_not_glyphs() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrProvenanceLine(source = "local", details = listOf("14 ms"))
            }
        }
        compose.onNodeWithContentDescription("source LOCAL, 14 MS").assertIsDisplayed()
    }

    @Test fun status_marker_label_is_readable() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrStatusMarker(status = SidrStatus.SUCCESS, label = "LOCAL")
            }
        }
        compose.onNodeWithText("LOCAL").assertIsDisplayed()
    }
}
