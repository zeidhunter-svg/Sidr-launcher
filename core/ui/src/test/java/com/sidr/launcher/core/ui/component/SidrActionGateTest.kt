package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
 * DS-3 Action Gate tests (spec §7: Cancel calls only cancel, Confirm calls exactly once, confirming
 * disables both, consequence always present, URL wraps).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrActionGateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun consequence_is_always_visible() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.ExternalHandoff,
                    title = "Open website",
                    consequence = "This will open the URL in your browser.",
                    confirmLabel = "Continue",
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("This will open the URL in your browser.").assertIsDisplayed()
    }

    @Test fun target_url_is_visible() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.ExternalHandoff,
                    title = "Open",
                    consequence = "Opens the URL.",
                    target = "https://github.com",
                    confirmLabel = "Continue",
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("https://github.com").assertIsDisplayed()
    }

    @Test fun cancel_fires_once_and_confirm_does_not_fire() {
        val cancelClicks = AtomicInteger(0)
        val confirmClicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.Confirmation,
                    title = "Run",
                    consequence = "Executes the action.",
                    confirmLabel = "Confirm",
                    onConfirm = { confirmClicks.incrementAndGet() },
                    onCancel = { cancelClicks.incrementAndGet() },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(1, cancelClicks.get())
        assertEquals(0, confirmClicks.get())
    }

    @Test fun confirm_fires_exactly_once() {
        val confirmClicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.Confirmation,
                    title = "Run",
                    consequence = "Executes the action.",
                    confirmLabel = "Confirm",
                    onConfirm = { confirmClicks.incrementAndGet() },
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirmClicks.get())
    }

    @Test fun double_confirm_click_is_ignored_after_first_dispatch() {
        val confirmClicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.Confirmation,
                    title = "Run",
                    consequence = "Executes the action.",
                    confirmLabel = "Confirm",
                    onConfirm = { confirmClicks.incrementAndGet() },
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirmClicks.get())
    }

    @Test fun risk_label_is_visible() {
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.ExternalHandoff,
                    title = "Open",
                    consequence = "Opens the URL.",
                    confirmLabel = "Continue",
                    onConfirm = {},
                    onCancel = {},
                )
            }
        }
        compose.onNodeWithText("EXTERNAL").assertIsDisplayed()
    }

    @Test fun confirming_disables_both_controls() {
        val confirmClicks = AtomicInteger(0)
        val cancelClicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrActionGate(
                    type = SidrActionGateType.Confirmation,
                    title = "Run",
                    consequence = "Executes the action.",
                    confirmLabel = "Confirm",
                    onConfirm = { confirmClicks.incrementAndGet() },
                    onCancel = { cancelClicks.incrementAndGet() },
                    confirming = true,
                )
            }
        }
        // While confirming, both controls are disabled — clicking does nothing.
        compose.onNodeWithText("Confirm").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0, confirmClicks.get())
        assertEquals(0, cancelClicks.get())
    }
}
