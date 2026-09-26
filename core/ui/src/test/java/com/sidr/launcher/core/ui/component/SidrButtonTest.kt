package com.sidr.launcher.core.ui.component

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * DS-3 button semantics + guard tests (spec §7: unit + semantics).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrButtonTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabled_button_does_not_fire_click() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrimaryButton("Continue", onClick = { clicks.incrementAndGet() }, enabled = false)
            }
        }
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithText("Continue").performClick()
        org.junit.Assert.assertEquals(0, clicks.get())
    }

    @Test fun enabled_button_fires_click_once() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrimaryButton("Continue", onClick = { clicks.incrementAndGet() })
            }
        }
        compose.onNodeWithText("Continue").assertIsEnabled()
        compose.onNodeWithText("Continue").performClick()
        org.junit.Assert.assertEquals(1, clicks.get())
    }

    @Test fun loading_button_does_not_fire_click() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrPrimaryButton("Saving", onClick = { clicks.incrementAndGet() }, loading = true)
            }
        }
        // Loading disables the button so duplicate taps are impossible.
        compose.onNodeWithText("Saving").assertIsNotEnabled()
        compose.onNodeWithText("Saving").performClick()
        org.junit.Assert.assertEquals(0, clicks.get())
    }

    @Test fun terminal_action_disabled_does_not_fire_click() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrTerminalAction("CONFIRM", onClick = { clicks.incrementAndGet() }, enabled = false)
            }
        }
        compose.onNodeWithText("[ CONFIRM ]").performClick()
        org.junit.Assert.assertEquals(0, clicks.get())
    }

    @Test fun terminal_action_enabled_fires_click_once() {
        val clicks = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrTerminalAction("CONFIRM", onClick = { clicks.incrementAndGet() })
            }
        }
        compose.onNodeWithText("[ CONFIRM ]").performClick()
        org.junit.Assert.assertEquals(1, clicks.get())
    }
}
