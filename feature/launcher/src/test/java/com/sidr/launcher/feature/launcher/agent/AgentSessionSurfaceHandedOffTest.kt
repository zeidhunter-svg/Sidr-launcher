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
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import com.sidr.launcher.domain.trace.TraceEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 0, Task 6, retargeted by the final review's fix wave (controller ruling R17). **What holds
 * the one `when` where `everyStepExecuted()` meets `anyStepHandedOff()`** — `AgentSessionSurface`'s
 * `ExecutionState.Completed` branch, which picks one of THREE titles, in this order:
 *  1. not every step executed → the Partial title («Plan finished, not every step ran»);
 *  2. every step executed, at least one handed off → the handed-off title («Plan finished — the
 *     outcome is with the system»);
 *  3. otherwise → «Plan complete».
 *
 * Task 6 shipped a two-way join (`everyStepExecuted() && !anyStepHandedOff()`) that sent case 2 to the
 * Partial title, whose body says a step «did not run» — directly above a step list that says the step
 * was «handed to the system». Every `uninstall_app` session is case 2, so the surface contradicted
 * itself on exactly the tool this phase exists to stop lying about.
 *
 * `everyStepExecuted()` has exactly one production call site, and it is inside a `@Composable`. A unit
 * test on the two predicates (`AgentSessionPresentationTest > a handed-off step is neither done nor a
 * whole plan`) holds each predicate and does not touch that `when`, so a mutation reordering or deleting
 * one of its arms would stay green there. This test drives the real surface through real resources,
 * the way [AgentSessionSurfaceProvenanceTest] holds `DOC-ILM-2`, and follows its shape: Robolectric,
 * `createComposeRule`, `SidrTheme`, and the default-locale (`en`) text its runner renders, hardcoded
 * rather than resolved.
 *
 * **Exact text, never `substring = true`.** `onNodeWithText` without it matches a node's WHOLE text. The
 * three titles are NOT substring-disjoint — the Partial and the handed-off title both begin with «Plan
 * finished», all three with «Plan», and in `ru` the first two share «План пройден» — so a substring
 * match on any of them is exactly what R14-43 warns about, and the whole-text match is what keeps the
 * three assertions apart. The titles are `launcher_agent_completed_title` /
 * `launcher_agent_completed_partial_title` / `launcher_agent_completed_handed_off_title` in
 * `values/strings.xml`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceHandedOffTest {

    @get:Rule val compose = createComposeRule()

    private val partialTitle = "Plan finished, not every step ran"
    private val handedOffTitle = "Plan finished — the outcome is with the system"
    private val completedTitle = "Plan complete"

    /**
     * `Tier0ToolIds.UNINSTALL_APP` lives in `:data:repository`, which `:feature:launcher` must not
     * depend on (no feature -> data edge), so the id is written out here exactly as the production
     * mapping writes it.
     */
    private val handedOffTool = ToolId("uninstall_app")

    /**
     * The real shape of the defect: a one-step `uninstall_app` plan whose step ran — the OS dialog was
     * raised — and whose outcome the launcher cannot see, because the user may have cancelled it. The
     * session closed `Completed` and every step executed, so the ONLY thing that can keep «Plan
     * complete» off the screen is `anyStepHandedOff()` — and, since every step DID execute, the Partial
     * «not every step ran» would be false about it too.
     *
     * The goal text, the label and the package share no word with each other, with either title, or
     * with the fixtures of [AgentSessionSurfaceProvenanceTest] (R14-43) — not because this test asserts
     * on them, but so that no later assertion on this fixture inherits a collision. (The goal avoids
     * «убери», which would contain the neighbour's «убер».)
     */
    private fun completedWithHandedOffStep(): AgentSession {
        val goal = AgentGoal("удали записную книжку", GoalShape.Free("удали записную книжку"))
        return AgentSession(
            id = AgentSessionId("s-handed-off"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            handedOffTool,
                            mapOf(
                                "app" to ArgSource.Literal("org.fixture.inkpad"),
                                "app_label" to ArgSource.Literal("Quill"),
                            ),
                        ),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
            cursor = 1,
            state = ExecutionState.Completed,
            observations = mapOf(0 to ToolResult.HandedOff()),
            consents = mapOf(0 to true),
            trace = ExecutionTrace(emptyList()),
        )
    }

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

    /**
     * `DOC-ILM-4` at the one place it is decided, from both sides. A plan whose act was handed to the OS
     * has not been shown to have succeeded — a cancelled `uninstall_app` is exactly that — so the
     * `Completed` surface must not carry «Plan complete». And every one of its steps DID run, so it
     * must not carry «Plan finished, not every step ran» either: that title's body says a step «did not
     * run», directly above a step list that says «handed to the system». Its own title says the one
     * thing that is true — the plan was gone through, and the outcome is with the system.
     *
     * This is a rendering property and a unit test cannot hold it: `everyStepExecuted()` has one
     * production call site and it is inside a `@Composable`. Same argument, same shape, as
     * [AgentSessionSurfaceProvenanceTest].
     */
    @Test
    fun `a completed plan with a handed-off step says the outcome is with the system`() {
        val session = completedWithHandedOffStep()
        // Non-vacuity: if the fixture ever stopped satisfying `everyStepExecuted()`, the Partial title
        // would appear for that reason alone and this test would stop holding `anyStepHandedOff()`.
        assertTrue(
            "every step must have executed, or this test no longer isolates the handed-off clause",
            session.everyStepExecuted(),
        )

        render(session)

        compose.onNodeWithText(handedOffTitle).assertExists()
        compose.onNodeWithText(completedTitle).assertDoesNotExist()
        compose.onNodeWithText(partialTitle).assertDoesNotExist()
    }

    /**
     * A0's two-step plan in the shape the wall-clock budget can now produce: `launch_app` ran past its
     * deadline and was recorded `HandedOff`, so the store step's precondition — "the previous step
     * observed APP_NOT_INSTALLED" — did not hold and the store step was skipped. The session closed
     * `Completed`, with one step handed off AND one step that did not run.
     *
     * Built by hand in `TemplatePlanner`'s own A0 shape (step 1 binds `resolved_query` from step 0,
     * exactly as the planner writes it) rather than planned, so the observation and the skip can be
     * set directly. The goal and its query («найди калимбу» / «калимба») appear nowhere else in this
     * file or in [AgentSessionSurfaceProvenanceTest], and, being Cyrillic, share no substring with any
     * of the three English titles asserted on below (R14-43). The tool ids are A0's own — necessarily
     * the same as the neighbour's A0 fixture — and nothing asserts on them.
     */
    private fun completedWithSkippedAndHandedOffSteps(): AgentSession {
        val goal = AgentGoal("найди калимбу", GoalShape.AppNotInstalled("калимба"))
        return AgentSession(
            id = AgentSessionId("s-branch-order"),
            goal = goal,
            plan = ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            ToolId("launch_app"),
                            mapOf("query" to ArgSource.Literal("калимба")),
                        ),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(
                            ToolId("play_store_search"),
                            mapOf("query" to ArgSource.FromStep(0, "resolved_query")),
                        ),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED),
                        rationale = StepRationale.APP_NOT_INSTALLED_FALLBACK,
                    ),
                ),
            ),
            cursor = 2,
            state = ExecutionState.Completed,
            observations = mapOf(0 to ToolResult.HandedOff()),
            consents = emptyMap(),
            trace = ExecutionTrace(
                listOf(
                    TraceEvent.StepSkipped(1, StepPrecondition.PreviousStepObserved(ObservedFact.APP_NOT_INSTALLED)),
                ),
            ),
        )
    }

    /**
     * **The branch ORDER, pinned.** A step that did not run makes a plan partial whatever else happened
     * in it, so «not every step ran» is asked FIRST — before the handed-off title, which would otherwise
     * hide the skipped step behind a claim that only the outcome is out of sight. A surface that asked
     * `anyStepHandedOff()` first would show the handed-off title here, and this test goes red.
     */
    @Test
    fun `a step that did not run outranks a handed-off step`() {
        val session = completedWithSkippedAndHandedOffSteps()
        // Non-vacuity, both ways: the fixture must sit where the two arms DISAGREE, or it pins no order.
        assertFalse("a step must not have run, or the Partial arm is not in play", session.everyStepExecuted())
        assertTrue("a step must be handed off, or the handed-off arm is not in play", session.anyStepHandedOff())

        render(session)

        compose.onNodeWithText(partialTitle).assertExists()
        compose.onNodeWithText(handedOffTitle).assertDoesNotExist()
        compose.onNodeWithText(completedTitle).assertDoesNotExist()
    }
}
