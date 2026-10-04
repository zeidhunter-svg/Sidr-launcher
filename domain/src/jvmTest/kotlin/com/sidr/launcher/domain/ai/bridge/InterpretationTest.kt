package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The pick binds (spec §7.12.3 condition 2) — and a card with a pick still owed starts nothing. */
class InterpretationTest {

    private val pending = Interpretation.Act(
        goal = AgentGoal("удали pellix", GoalShape.Free("удали pellix")),
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(ToolId("uninstall_app"), mapOf("app_label" to ArgSource.Literal("pellix"))),
            risk = ActionRiskLevel.CONFIRM,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = emptyMap(),
        effect = ToolEffect.EXTERNAL,
        choice = AppChoice(
            arg = "app",
            mention = "pellix",
            candidates = listOf(
                InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix"),
                InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix"),
            ),
            learning = null,
        ),
    )

    @Test
    fun `a pick writes the package into the step and leaves nothing to choose`() {
        val bound = pending.choose("net.fixture.pellix.two")!!

        assertEquals(ArgSource.Literal("net.fixture.pellix.two"), bound.step.invocation.args["app"])
        assertEquals(ValueOrigin.Ours, bound.origins["app"])
        assertNull(bound.choice)
    }

    @Test
    fun `a package that was not offered changes nothing`() {
        assertNull(pending.choose("net.fixture.lumora"))
    }

    @Test
    fun `the plan exists only once nothing is left to choose`() {
        assertNull(pending.plan())
        assertEquals(ExecutionPlan(listOf(pending.choose("net.fixture.pellix.one")!!.step)), pending.choose("net.fixture.pellix.one")!!.plan())
    }
}
