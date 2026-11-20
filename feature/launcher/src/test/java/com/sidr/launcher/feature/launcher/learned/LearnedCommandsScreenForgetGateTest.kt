package com.sidr.launcher.feature.launcher.learned

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 4, owner decision «T8: a» (2026-11-20): «Забыть» on a learned command asks first, as the sibling
 * memory screens do (`LearnedChoicesScreen`) — a destructive one-tap on a row the user is scrolling past is the
 * thing the gate exists for. Nothing is deleted until the gate's own «Forget» is tapped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnedCommandsScreenForgetGateTest {

    @get:Rule val compose = createComposeRule()

    private val form = "сотри ⟨…⟩"
    private val command = LearnedCommandUiModel(
        form = form,
        steps = listOf(
            PlanStep(
                0,
                ToolInvocation(ToolId("delete_file"), mapOf("name" to ArgSource.Literal("⟨…⟩"))),
                ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.MODEL_PROPOSED,
            ),
        ),
        streak = 3,
        threshold = 3,
        pinned = true,
    )

    private val forgotten = mutableListOf<String>()

    private fun show() {
        compose.setContent {
            SidrTheme {
                LearnedCommandsContent(
                    uiState = LearnedCommandsUiState(commands = listOf(command), isLoading = false),
                    onBack = {},
                    onRetry = {},
                    onForget = { forgotten += it },
                )
            }
        }
    }

    private fun rowForget() = compose.onNodeWithContentDescription("Forget learned preference $form")

    /** The gate's own confirm: the «Forget» button that is not the row's (the row's carries a description). */
    private fun gateForget() = compose.onNode(
        hasText("Forget") and hasClickAction() and !hasContentDescription("Forget learned preference $form"),
    )

    @Test
    fun `forget asks first and deletes nothing until confirmed`() {
        show()

        rowForget().performClick()

        compose.onNodeWithText("Forget learned command?").assertExists()
        assertEquals("the tap alone must not delete", emptyList<String>(), forgotten)

        gateForget().performClick()

        assertEquals(listOf(form), forgotten)
        compose.onNodeWithText("Forget learned command?").assertDoesNotExist()
    }

    @Test
    fun `cancelling the gate keeps the learned command`() {
        show()

        rowForget().performClick()
        compose.onNodeWithText("Forget learned command?").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(emptyList<String>(), forgotten)
        compose.onNodeWithText("Forget learned command?").assertDoesNotExist()
        rowForget().assertExists()
    }
}
