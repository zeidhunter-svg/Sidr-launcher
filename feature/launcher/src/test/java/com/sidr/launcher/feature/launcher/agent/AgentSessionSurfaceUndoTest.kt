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

    private fun session(
        state: ExecutionState,
        steps: List<PlanStep>,
        observations: Map<Int, ToolResult>,
        goalText: String = "назови вельтрикс",
    ) = AgentSession(
        id = AgentSessionId("s-undo-card"),
        goal = AgentGoal(goalText, GoalShape.Free(goalText)),
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

    // --- A4′ phase 3b, Task 12: phase 2's deferred card tests (N2, T6 m5) --------------------------------

    /** A `CONFIRM` step the user can refuse: the uninstall's line names the label AND the package. */
    private fun uninstallStep(index: Int) = PlanStep(
        index,
        ToolInvocation(ToolId("uninstall_app"), mapOf("app" to ArgSource.Literal("net.fixture.orvelle"), "app_label" to ArgSource.Literal("Orvelle"))),
        ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.GOAL_DIRECT,
    )

    /**
     * N2, answered «partial» (plan §0.2 B-11, `DOC-ILM-4`; the owner may overturn). The user refused the
     * uninstall at its consent card AFTER the alias step had acted: the session ends `Cancelled`, and the
     * card no longer vanishes. It is a finished card — titled «Plan stopped» (controller ruling R12-1: the
     * `Blocked` title, WITHOUT the `Blocked` body, which says Sidr stopped it — here the user did), the
     * step lines saying what acted and what never ran, and «Undo» wired to what can be undone.
     *
     * The control comes first, on the same screen: the same plan refused before anything acted still
     * draws nothing — the ordinary command surface is what to look at then. The goal's words share nothing
     * with any expected line, so a line that fell back to the goal cannot satisfy an assertion here.
     */
    @Test
    fun `a plan cancelled after a step acted shows what acted and offers undo`() {
        val steps = listOf(aliasStep(0), uninstallStep(1))
        val refusedBeforeActing = session(ExecutionState.Cancelled, steps, emptyMap(), goalText = "дай имя и сотри")
            .copy(cursor = 0, consents = mapOf(0 to false))
        val refusedAfterActing = session(ExecutionState.Cancelled, steps, mapOf(0 to ToolResult.Effected()), goalText = "дай имя и сотри")
            .copy(cursor = 1, consents = mapOf(1 to false))
        var shown by mutableStateOf(refusedBeforeActing)
        var review by mutableStateOf(emptyList<StepUndo>())
        var undone = false
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = shown,
                    toolProvenance = emptyMap(),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                    undoReview = review,
                    onUndo = { undone = true },
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Plan stopped").assertDoesNotExist()

        shown = refusedAfterActing
        review = listOf(StepUndo(0, byUndo))
        compose.waitForIdle()

        compose.onNodeWithText("Plan stopped").assertExists()
        compose.onNodeWithText("Sidr stopped this plan because it could no longer follow it safely. Nothing further will run.").assertDoesNotExist()
        compose.onNodeWithText("Call “Lumora” “вельтрикс” — done").assertExists()
        compose.onNodeWithText("Remove Orvelle (net.fixture.orvelle) — not started").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertTrue("Undo on the cancelled card calls the undo", undone)
    }

    /** T6 m5 (phase 2's final review): the `Blocked` card's «Undo» is not only drawn — its tap is the undo. */
    @Test
    fun `a blocked plan's undo is wired`() {
        var undone = false
        render(
            session(ExecutionState.Blocked, listOf(aliasStep(0), choiceStep(1)), mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Failed(CommandFailure.Generic))),
            listOf(StepUndo(0, byUndo)),
            onUndo = { undone = true },
        )

        compose.onNodeWithText("Undo").performClick()

        assertTrue(undone)
    }

    /**
     * T6 m5 (phase 2's final review): the `Failed` card names, in its «next» line, the step it cannot
     * undo — beside the «Undo» that covers the rest, so the button's reach is never implied larger than it
     * is (`B14`). Named against the plan's wording `a failed plan with nothing undoable…`: with NOTHING
     * undoable there is no «Undo» and, by design, no line (the line qualifies the button), so the shape
     * that holds `next = notUndoable` is a failed plan with one step it cannot undo.
     */
    @Test
    fun `a failed plan with a step it cannot undo says what cannot be undone`() {
        render(
            session(
                ExecutionState.Failed,
                listOf(aliasStep(0), choiceStep(1), uninstallStep(2)),
                mapOf(0 to ToolResult.Effected(), 1 to ToolResult.Effected(), 2 to ToolResult.Failed(CommandFailure.Generic)),
            ),
            listOf(StepUndo(1, Undo.NotUndoable), StepUndo(0, byUndo)),
        )

        compose.onNodeWithText("Undo").assertExists()
        compose.onNodeWithText("Can’t be undone here: Forget the learned choice for “лумора”").assertExists()
    }
}
