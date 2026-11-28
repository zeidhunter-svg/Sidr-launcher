package com.sidr.launcher.feature.launcher.agent

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
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
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    /**
     * Owner ruling 7 (K5): the finished-plan card's close and undo buttons are one size. At the default font scale
     * both short labels sit on the button's minimum width (58dp), so the proof needs labels that outgrow it: under
     * `ru` — the owner's language, «Закрыть» and «Отменить» differ in length — at font scale 1.5 their natural
     * widths differ, so equal widths prove the two buttons share the width.
     */
    @Test
    @Config(qualifiers = "ru")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `a finished plan's close and undo buttons are the same width`() {
        val density = Density(density = 1f, fontScale = 1.5f)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density) {
                SidrTheme(darkTheme = true) {
                    AgentSessionSurface(
                        session = session(ExecutionState.Completed, listOf(aliasStep(0)), mapOf(0 to ToolResult.Effected())),
                        toolProvenance = emptyMap(),
                        dynamicLabels = emptyMap(),
                        confirming = false,
                        onConfirm = {},
                        onDeny = {},
                        onContinue = {},
                        onDismiss = {},
                        undoReview = listOf(StepUndo(0, byUndo)),
                        onUndo = {},
                    )
                }
            }
        }
        compose.waitForIdle()

        val close = compose.onNodeWithText("Закрыть").getUnclippedBoundsInRoot()
        val undo = compose.onNodeWithText("Отменить").getUnclippedBoundsInRoot()

        assertEquals((close.right - close.left).value, (undo.right - undo.left).value, 1f)
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
     * uninstall — step 1, a `CONFIRM` step — at its consent card AFTER the alias step 0 had acted: the
     * session ends `Cancelled`, and the card no longer vanishes. It is a finished card — titled «Plan
     * stopped» (controller ruling R12-1: the `Blocked` title, WITHOUT the `Blocked` body, which says Sidr
     * stopped it — here the user did), the step lines saying what acted and what never ran, «Dismiss», and
     * «Undo» wired to what can be undone.
     *
     * The control comes first, on the same screen, and is the shape a user really reaches (A0's own plan,
     * review fix M3): step 0 `launch_app` only LOOKED (`Observed`, «no such app») and the user refused step
     * 1, the `CONFIRM` store search, at its consent card. A look is not an act, so that `Cancelled` session
     * draws nothing at all — neither the title nor the card's «Dismiss»; the ordinary command surface is
     * what to look at then. The treatment asserts «Dismiss» IS drawn on its card, so the control's absence
     * is of a label the card really carries. Every asserted string is matched whole (exact text, never a
     * substring), and none contains either goal's text or the control's query, so a line that fell back to
     * the goal cannot satisfy one.
     */
    @Test
    fun `a plan cancelled after a step acted shows what acted and offers undo`() {
        val lookThenStore = listOf(
            PlanStep(0, ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("ностра"))), ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.GOAL_DIRECT),
            PlanStep(1, ToolInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to ArgSource.FromStep(0, "resolved_query"))), ActionRiskLevel.CONFIRM, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED), StepRationale.APP_NOT_INSTALLED_FALLBACK),
        )
        val looked = ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "ностра")))
        val refusedAfterLooking = session(ExecutionState.Cancelled, lookThenStore, mapOf(0 to looked))
            .copy(goal = AgentGoal("открой ностра", GoalShape.AppNotInstalled("ностра")), cursor = 1, consents = mapOf(1 to false))
        val steps = listOf(aliasStep(0), uninstallStep(1))
        val refusedAfterActing = session(ExecutionState.Cancelled, steps, mapOf(0 to ToolResult.Effected()), goalText = "дай имя и сотри")
            .copy(cursor = 1, consents = mapOf(1 to false))
        var shown by mutableStateOf(refusedAfterLooking)
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
        compose.onNodeWithText("Dismiss").assertDoesNotExist()

        shown = refusedAfterActing
        review = listOf(StepUndo(0, byUndo))
        compose.waitForIdle()

        compose.onNodeWithText("Plan stopped").assertExists()
        compose.onNodeWithText("Dismiss").assertExists()
        compose.onNodeWithText("Sidr stopped this plan because it could no longer follow it safely. Nothing further will run.").assertDoesNotExist()
        compose.onNodeWithText("1. Call “Lumora” “вельтрикс” — done").assertExists()
        compose.onNodeWithText("2. Remove Orvelle (net.fixture.orvelle) — not started").assertExists()
        compose.onNodeWithText("Undo").performClick()
        assertTrue("Undo on the cancelled card calls the undo", undone)
    }

    /**
     * Phase 3b close, final review F1 — the N2 card a user can actually reach on Android today: a model plan's
     * `launch_app` handed its act to the system (`HandedOff` — it left the launcher and may have happened),
     * then the user refused step 1, the `CONFIRM` uninstall, at its consent card. The test above covers an
     * `Effected` act; this is the `HandedOff` half of `anyStepActed`, which nothing else draws (the
     * ViewModel's own visibility helper calls `anyStepActed` and would agree with any regression of it).
     * The card is drawn — «Plan stopped», both step lines, and each `EXTERNAL` step's provenance (the Task 12
     * review's named gap M2) — and, with nothing that can be undone, no «Undo». Strings are matched whole;
     * no expected value shares a substring with another (R14-43).
     */
    @Test
    fun `a plan cancelled after a handed-off step shows its stopped card without undo`() {
        val launch = PlanStep(
            0,
            ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("Vemblic"), "app" to ArgSource.Literal("net.fixture.vemblic"))),
            ActionRiskLevel.SAFE, StepPrecondition.None, StepRationale.MODEL_PROPOSED,
        )
        val refused = session(ExecutionState.Cancelled, listOf(launch, uninstallStep(1)), mapOf(0 to ToolResult.HandedOff()), goalText = "запусти и сотри")
            .copy(cursor = 1, consents = mapOf(1 to false))
        compose.setContent {
            SidrTheme(darkTheme = true) {
                AgentSessionSurface(
                    session = refused,
                    toolProvenance = mapOf(
                        ToolIds.LAUNCH_APP to StepProvenance(ToolLevels.IN_APP, ToolEffect.EXTERNAL),
                        ToolId("uninstall_app") to StepProvenance(ToolLevels.SYSTEM_INTENT, ToolEffect.EXTERNAL),
                    ),
                    dynamicLabels = emptyMap(),
                    confirming = false,
                    onConfirm = {},
                    onDeny = {},
                    onContinue = {},
                    onDismiss = {},
                    undoReview = listOf(StepUndo(0, Undo.NotUndoable)),
                    onUndo = {},
                )
            }
        }
        compose.waitForIdle()

        compose.onNodeWithText("Plan stopped").assertExists()
        compose.onNodeWithText("Dismiss").assertExists()
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithText("1. Open Vemblic (net.fixture.vemblic) — handed to the system").assertExists()
        compose.onNodeWithText("2. Remove Orvelle (net.fixture.orvelle) — not started").assertExists()
        compose.onNodeWithContentDescription("source SIDR · EXTERNAL").assertExists()
        compose.onNodeWithContentDescription("source SYSTEM INTENT · EXTERNAL").assertExists()
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
