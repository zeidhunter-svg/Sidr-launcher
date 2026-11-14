package com.sidr.launcher.feature.launcher.agent

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
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, owner fork F2-3.2. The paused card promised «Sidr picks it up where it stopped», which
 * is false for a plan that will be planned afresh. The body is chosen by `AgentSession.replansOnResume`
 * — the SAME predicate `ResumeAgentSessionUseCase` acts on — so the card cannot say one thing while
 * the button does another. The two bodies share their first words, so they are matched whole.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfacePausedTest {

    @get:Rule val compose = createComposeRule()

    private val continuesBody =
        "You left before this plan finished. Continue and Sidr picks it up where it stopped; dismiss it and nothing further runs."
    private val replansBody =
        "You left before this plan finished, and things may have changed since. Continue and Sidr looks at the plan afresh; dismiss it and nothing further runs."

    private val plan = ExecutionPlan(
        listOf(
            PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("вельтрикс"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
            PlanStep(1, ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))), ActionRiskLevel.CONFIRM, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED), StepRationale.APP_NOT_INSTALLED_FALLBACK),
        ),
    )

    private fun paused(cursor: Int, observations: Map<Int, ToolResult>, trace: List<TraceEvent>) = AgentSession(
        id = AgentSessionId("s-paused"),
        goal = AgentGoal("открой вельтрикс", GoalShape.AppNotInstalled("вельтрикс")),
        plan = plan,
        cursor = cursor,
        state = ExecutionState.Paused,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(trace),
    )

    private fun render(session: AgentSession) {
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
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a paused plan that has only looked says it will look again`() {
        val looked = ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "вельтрикс")))
        render(
            paused(
                cursor = 1,
                observations = mapOf(0 to looked),
                trace = listOf(TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP), TraceEvent.ToolObserved(0, looked), TraceEvent.SessionPaused),
            ),
        )

        compose.onNodeWithText(replansBody).assertExists()
        compose.onNodeWithText(continuesBody).assertDoesNotExist()
    }

    @Test
    fun `a paused plan with a call in flight says it will pick up where it stopped`() {
        render(
            paused(
                cursor = 0,
                observations = emptyMap(),
                trace = listOf(TraceEvent.PlanCreated(2), TraceEvent.StepStarted(0), TraceEvent.ToolInvoked(0, ToolIds.LAUNCH_APP), TraceEvent.SessionPaused),
            ),
        )

        compose.onNodeWithText(continuesBody).assertExists()
        compose.onNodeWithText(replansBody).assertDoesNotExist()
    }
}
