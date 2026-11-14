package com.sidr.launcher.feature.launcher.agent

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, Task 2 (owner fork F2-2). `DOC-HMA-3`'s first half reaches the user HERE: before an
 * irreversible step runs, the gate announces it as `DESTRUCTIVE` — a `SidrActionGateType` that already
 * existed (and tones the surface as a risk) but that no agent step could reach, because the checkpoint
 * carried one reason and risk always won. The chip labels are locked machine vocabulary
 * (`ui_gate_type_*`), identical in every locale, and share no substring with each other or with the
 * `Confirm` button (case differs), so a whole-text match separates all three.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionGateChipTest {

    @get:Rule val compose = createComposeRule()

    private fun awaiting(reasons: Set<ConsentReason>) = AgentSession(
        id = AgentSessionId("s-chip"),
        goal = AgentGoal("удали лумору", GoalShape.Free("удали лумору")),
        plan = ExecutionPlan(
            listOf(
                PlanStep(
                    index = 0,
                    invocation = ToolInvocation(
                        ToolId("uninstall_app"),
                        mapOf(
                            "app" to ArgSource.Literal("net.fixture.quill"),
                            "app_label" to ArgSource.Literal("Lumora"),
                        ),
                    ),
                    risk = ActionRiskLevel.CONFIRM,
                    precondition = StepPrecondition.None,
                    rationale = StepRationale.GOAL_DIRECT,
                ),
            ),
        ),
        cursor = 0,
        state = ExecutionState.AwaitingConsent,
        observations = emptyMap(),
        consents = emptyMap(),
        trace = ExecutionTrace(listOf(TraceEvent.ConsentRequested(0, reasons))),
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
    fun `a step that cannot be undone is announced as destructive before it runs`() {
        render(awaiting(setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT)))

        compose.onNodeWithText("DESTRUCTIVE").assertExists()
        compose.onNodeWithText("CONFIRM").assertDoesNotExist()
    }

    @Test
    fun `a step stopped for its risk alone is announced as a confirmation`() {
        render(awaiting(setOf(ConsentReason.RISK_LEVEL)))

        compose.onNodeWithText("CONFIRM").assertExists()
        compose.onNodeWithText("DESTRUCTIVE").assertDoesNotExist()
    }
}
