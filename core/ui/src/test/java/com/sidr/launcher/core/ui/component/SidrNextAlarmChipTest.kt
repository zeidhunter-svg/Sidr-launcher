package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * A4′ acceptance tail, task 8: the Home date line's next-alarm chip. Its visible text and its spoken description are
 * the CALLER's (the feature layer formats the time and picks the strings); the chip is one button that says the
 * description, not the glyph, and taps once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrNextAlarmChipTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shows_its_text_and_is_one_button_named_by_its_description() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrNextAlarmChip(text = "⏰ 07:30", contentDescription = "Next alarm: 07:30", onClick = {})
            }
        }
        compose.onNodeWithText("⏰ 07:30", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Next alarm: 07:30").assertIsDisplayed().assertHasClickAction()
    }

    @Test fun a_tap_fires_once() {
        val taps = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrNextAlarmChip(text = "⏰ 07:30", contentDescription = "Next alarm: 07:30", onClick = { taps.incrementAndGet() })
            }
        }
        compose.onNodeWithContentDescription("Next alarm: 07:30").performClick()
        assertEquals(1, taps.get())
    }
}
