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
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.trace.ExecutionTrace
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ phase 0, Task 6. **What holds the one line where `everyStepExecuted()` meets
 * `anyStepHandedOff()`** — `AgentSessionSurface`'s `ExecutionState.Completed` branch.
 *
 * `everyStepExecuted()` has exactly one production call site, and it is inside a `@Composable`. A unit
 * test on the two predicates (`AgentSessionPresentationTest > a handed-off step is neither done nor a
 * whole plan`) holds each predicate and does not touch that line, so a mutation deleting
 * `&& !anyStepHandedOff()` would stay green there. This test drives the real surface through real
 * resources, the way [AgentSessionSurfaceProvenanceTest] holds `DOC-ILM-2`, and follows its shape:
 * Robolectric, `createComposeRule`, `SidrTheme`, and the default-locale (`en`) text its runner renders,
 * hardcoded rather than resolved.
 *
 * **Exact text, never `substring = true`.** `onNodeWithText` without it matches a node's WHOLE text, so
 * «Plan complete» cannot match «Plan finished, not every step ran» although both begin with «Plan» — and
 * in `ru` the pair also shares «выполнен». A substring match on either title is exactly what R14-43
 * warns about. The titles are `launcher_agent_completed_title` / `launcher_agent_completed_partial_title`
 * in `values/strings.xml`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AgentSessionSurfaceHandedOffTest {

    @get:Rule val compose = createComposeRule()

    private val partialTitle = "Plan finished, not every step ran"
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
     * complete» off the screen is `anyStepHandedOff()`.
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
     * `DOC-ILM-4` at the one place it is decided. A plan whose act was handed to the OS has not been
     * shown to have succeeded — a cancelled `uninstall_app` is exactly that — so the `Completed`
     * surface must carry the `Partial` title, not «План выполнен».
     *
     * This is a rendering property and a unit test cannot hold it: `everyStepExecuted()` has one
     * production call site and it is inside a `@Composable`. Same argument, same shape, as
     * [AgentSessionSurfaceProvenanceTest].
     */
    @Test
    fun `a completed plan with a handed-off step is shown as partial, not as done`() {
        val session = completedWithHandedOffStep()
        // Non-vacuity: if the fixture ever stopped satisfying `everyStepExecuted()`, the Partial title
        // would appear for that reason alone and this test would stop holding `anyStepHandedOff()`.
        assertTrue(
            "every step must have executed, or this test no longer isolates the handed-off clause",
            session.everyStepExecuted(),
        )

        render(session)

        compose.onNodeWithText(partialTitle).assertExists()
        compose.onNodeWithText(completedTitle).assertDoesNotExist()
    }
}
