package com.sidr.launcher.feature.launcher.agent

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ acceptance tail, task 4 (controller ruling K4; `DOC-NYH-1` «показывает, как поняла» — after, not before): the
 * card of a session a FRESH model reading started without the interpretation card says «Предложено моделью»; the card
 * of any other session does not. The same session is drawn both ways — the flag is the only input that changes — so
 * the line cannot come from the session itself. Rendered with real resources (`en`).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceModelMarkTest {

    @get:Rule val compose = createComposeRule()

    private val searches = listOf("ostravel lectures", "vorandel maps").mapIndexed { index, query ->
        PlanStep(
            index,
            ToolInvocation(ToolId("web_search"), mapOf("query" to ArgSource.Literal(query))),
            ActionRiskLevel.SAFE,
            StepPrecondition.None,
            StepRationale.MODEL_PROPOSED,
        )
    }

    private val session = AgentSession(
        id = AgentSessionId("s-model-card"),
        goal = AgentGoal("поищи ostravel lectures и vorandel maps", GoalShape.Free("поищи ostravel lectures и vorandel maps")),
        plan = ExecutionPlan(searches),
        cursor = 2,
        state = ExecutionState.Completed,
        observations = mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected()),
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    @Test
    fun `the model's mark is drawn only for a session the model proposed`() {
        var proposedByModel by mutableStateOf(false)
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
                    proposedByModel = proposedByModel,
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Suggested by the model").assertDoesNotExist()

        proposedByModel = true
        compose.waitForIdle()

        compose.onNodeWithText("Suggested by the model").assertExists()
    }
}
