package com.sidr.launcher.feature.launcher.agent

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
import com.sidr.launcher.domain.agent.StepUndo
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 2, owner fork F2-3.3 — `B14`'s «что откатить» as an action on the same card. The card
 * offers «Undo» only when something can be undone, and — when only part can — names what cannot, so
 * the button's reach is never implied larger than it is. On Android today the mixed case is reachable
 * only through phase 3's plans (plan §0.3 P8); it is rendered here from a constructed session.
 *
 * `memory tool` ids are written as literals: `MemoryToolIds` lives in `:data:repository`, which this
 * module must not depend on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceUndoTest {

    @get:Rule val compose = createComposeRule()

    private val setAlias = ToolId("set_app_alias")
    private val forgetChoice = ToolId("forget_learned_choice")
    private val byUndo = Undo.By(ToolInvocation(ToolId("forget_app_alias"), mapOf("phrase" to ArgSource.Literal("вельтрикс"))))

    private fun aliasStep(index: Int) = PlanStep(
        index,
        ToolInvocation(setAlias, mapOf("app" to ArgSource.Literal("net.fixture.quill"), "app_label" to ArgSource.Literal("Lumora"), "phrase" to ArgSource.Literal("вельтрикс"))),
        ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    private fun choiceStep(index: Int) = PlanStep(
        index, ToolInvocation(forgetChoice, mapOf("phrase" to ArgSource.Literal("лумора"))),
        ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    private fun session(state: ExecutionState, steps: List<PlanStep>, observations: Map<Int, ToolResult>) = AgentSession(
        id = AgentSessionId("s-undo-card"),
        goal = AgentGoal("назови вельтрикс", GoalShape.Free("назови вельтрикс")),
        plan = ExecutionPlan(steps),
        cursor = steps.size,
        state = state,
        observations = observations,
        consents = emptyMap(),
        trace = ExecutionTrace(emptyList()),
    )

    private fun render(session: AgentSession, review: List<StepUndo>, onUndo: () -> Unit = {}) {
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
                    undoReview = review,
                    onUndo = onUndo,
                )
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `a finished plan with something to undo offers Undo, and Undo is what it calls`() {
        var undone = false
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0)), mapOf(0 to ToolResult.Effected())),
            listOf(StepUndo(0, byUndo)),
            onUndo = { undone = true },
        )

        compose.onNodeWithText("Undo").performClick()

        assertTrue(undone)
    }

    @Test
    fun `nothing to undo means no Undo`() {
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0)), mapOf(0 to ToolResult.Effected())),
            listOf(StepUndo(0, Undo.NothingToUndo)),
        )

        compose.onNodeWithText("Undo").assertDoesNotExist()
    }

    @Test
    fun `when only part can be undone the card names what cannot`() {
        render(
            session(ExecutionState.Completed, listOf(aliasStep(0), choiceStep(1)), mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected())),
            listOf(StepUndo(1, Undo.NotUndoable), StepUndo(0, byUndo)),
        )

        compose.onNodeWithText("Undo").assertExists()
        compose.onNodeWithText("Can’t be undone here: Forget the learned choice for “лумора”").assertExists()
    }

    @Test
    fun `a plan that failed part-way offers Undo too`() {
        render(
            session(ExecutionState.Failed, listOf(aliasStep(0), choiceStep(1)), mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Failed(CommandFailure.Generic))),
            listOf(StepUndo(0, byUndo)),
        )

        compose.onNodeWithText("Undo").assertExists()
    }
}
