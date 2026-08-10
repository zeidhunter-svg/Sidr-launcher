package com.sidr.launcher.core.ui.component

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.sidr.launcher.core.ui.theme.SidrTheme
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DS-10 accessibility + behaviour proofs for the assistant controls. These are the guards behind
 * plan Task 4's acceptance ("send works by button and IME; no send when prompt blank; no send while
 * streaming") and spec §8's disabled-state announcement rule.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SidrAssistantSemanticsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun blank_prompt_disables_send_and_says_why() {
        val sends = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrAssistantComposer(value = "", onValueChange = {}, onSend = { sends.incrementAndGet() })
            }
        }

        compose.onNodeWithContentDescription(SEND_EMPTY_DESCRIPTION).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription(SEND_EMPTY_DESCRIPTION).performClick()
        assertEquals(0, sends.get())
    }

    @Test fun streaming_disables_send_and_says_why() {
        val sends = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrAssistantComposer(
                    value = "already typed",
                    onValueChange = {},
                    onSend = { sends.incrementAndGet() },
                    sending = true,
                )
            }
        }

        compose.onNodeWithContentDescription(SEND_BUSY_DESCRIPTION).assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription(SEND_BUSY_DESCRIPTION).performClick()
        assertEquals(0, sends.get())
    }

    @Test fun typed_prompt_sends_from_the_button() {
        val sends = AtomicInteger(0)
        compose.setContent {
            SidrTheme(darkTheme = true) {
                SidrAssistantComposer(
                    value = "when is fajr",
                    onValueChange = {},
                    onSend = { sends.incrementAndGet() },
                )
            }
        }

        compose.onNodeWithContentDescription(SEND_READY_DESCRIPTION).assertIsEnabled().performClick()
        assertEquals(1, sends.get())
    }

    @Test fun ime_send_dispatches_once_typed_and_never_while_streaming() {
        val sends = AtomicInteger(0)
        compose.setContent {
            var text by remember { mutableStateOf("") }
            var streaming by remember { mutableStateOf(false) }
            SidrTheme(darkTheme = true) {
                SidrAssistantComposer(
                    value = text,
                    onValueChange = { text = it },
                    onSend = {
                        sends.incrementAndGet()
                        streaming = true
                    },
                    sending = streaming,
                )
            }
        }

        // Blank field: the IME action must not dispatch.
        compose.onNodeWithContentDescription("Message").performImeAction()
        assertEquals(0, sends.get())

        compose.onNodeWithContentDescription("Message").performTextInput("when is fajr")
        compose.onNodeWithContentDescription("Message").performImeAction()
        assertEquals(1, sends.get())

        // Now streaming — a second IME action is ignored.
        compose.onNodeWithContentDescription("Message").performImeAction()
        assertEquals(1, sends.get())
    }

    @Test fun streaming_indicator_is_quiet_and_labelled() {
        compose.setContent {
            SidrTheme(darkTheme = true) { SidrStreamingIndicator() }
        }

        compose.onNodeWithText("Replying…").assertIsDisplayed()
    }
}
