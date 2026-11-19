package com.sidr.launcher.feature.launcher.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.FormPart
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 4, Task 7 (plan §0.2 B-10): the card of a session a learned command started says «Выученная команда»
 * and offers «Забыть»; the card of any other session says neither. The same session is drawn both ways — the
 * mark is the only input that changes — so the line and the button cannot come from the session itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceLearnedTest {

    @get:Rule val compose = createComposeRule()

    private val search = PlanStep(
        0,
        ToolInvocation(ToolId("web_search"), mapOf("query" to ArgSource.Literal("глимсар"))),
        ActionRiskLevel.SAFE,
        StepPrecondition.None,
        StepRationale.MODEL_PROPOSED,
    )

    private val session = AgentSession(
        id = AgentSessionId("s-learned-card"),
        goal = AgentGoal("поищи глимсар", GoalShape.Free("поищи глимсар")),
        plan = ExecutionPlan(listOf(search)),
        cursor = 1,
        state = ExecutionState.Completed,
        observations = mapOf(0 to ToolResult.Effected()),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `the learned line and Forget are drawn only for a learned session`() {
        var learned by mutableStateOf<CommandForm?>(null)
        var forgets = 0
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = session,
                    toolProvenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                    learned = learned,
                    onForgetLearned = { forgets++ },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Learned command").assertDoesNotExist()
        compose.onNodeWithText("Forget").assertDoesNotExist()

        learned = CommandForm(listOf(FormPart.Word("поищи"), FormPart.Slot))
        compose.waitForIdle()

        compose.onNodeWithText("Learned command").assertExists()
        compose.onNodeWithText("Forget").performClick()
        assertEquals("«Forget» is what it calls", 1, forgets)
    }
}
