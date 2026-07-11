package com.sidr.launcher.feature.launcher.preview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import com.sidr.launcher.core.ui.theme.SidrTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Vision MVP Task 11: TerminalPreviewScreen looks like a Python REPL but is a **non-functional**
 * design preview — no interpreter exists anywhere in this codebase. This test suite guards the
 * screen's one hard safety property: no matter what is typed and submitted, the screen must never
 * fabricate command output.
 *
 * [terminal_never_produces_output] is the literal test specified by the Task 11 brief and only
 * checks the initial render (banner + prompt visible). It does not itself type or submit anything,
 * so it cannot alone prove the "no fabricated output" property — [typing_and_submitting_produces_no_output]
 * exercises the actual interaction (type a sample command, invoke submit, assert the transcript
 * area gained zero children and the typed text is no longer displayed anywhere).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TerminalPreviewScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun terminal_never_produces_output() {
        compose.setContent { SidrTheme(darkTheme = true) { TerminalPreviewScreen() } }
        compose.onNodeWithText("PREVIEW — this screen is a design of what's coming; it isn't live yet.")
            .assertIsDisplayed()
        // typing + submit must NOT add any transcript output (no fake REPL result)
        compose.onNodeWithText(">>>").assertIsDisplayed()
        // (no assertion of any computed output — there must be none)
    }

    @Test fun typing_and_submitting_produces_no_output() {
        compose.setContent { SidrTheme(darkTheme = true) { TerminalPreviewScreen() } }

        // Transcript starts empty.
        compose.onNodeWithTag(TERMINAL_TRANSCRIPT_TEST_TAG).onChildren().assertCountEquals(0)

        val sampleCommand = "print(1 + 1)"
        compose.onNode(hasSetTextAction()).performTextInput(sampleCommand)
        compose.onNodeWithText(sampleCommand).assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performImeAction()

        // The field is cleared on submit — the typed command is no longer displayed anywhere...
        compose.onNodeWithText(sampleCommand).assertDoesNotExist()
        // ...and, critically, the transcript gained no children: no echoed command, no computed
        // result, no interpreter output of any kind.
        compose.onNodeWithTag(TERMINAL_TRANSCRIPT_TEST_TAG).onChildren().assertCountEquals(0)
    }
}
