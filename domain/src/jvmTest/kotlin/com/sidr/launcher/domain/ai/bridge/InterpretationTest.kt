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

/**
 * The pick binds (spec §7.12.3 condition 2) — and a card with a pick still owed starts nothing. Since
 * phase 3b Task 9 a reading carries several steps (fork 3b-B): picks are owed in step order, and the plan
 * is built from the model's steps by one conversion, references included (controller ruling E9-P3).
 */
class InterpretationTest {

    private val pending = Interpretation.Act(
        goal = AgentGoal("удали pellix", GoalShape.Free("удали pellix")),
        steps = listOf(
            PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolId("uninstall_app"), mapOf("app_label" to ArgSource.Literal("pellix"))),
                risk = ActionRiskLevel.CONFIRM,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
        ),
        origins = emptyMap(),
        effects = mapOf(0 to ToolEffect.EXTERNAL),
        choices = listOf(
            PendingChoice(
                step = 0,
                choice = AppChoice(
                    arg = "app",
                    mention = "pellix",
                    candidates = listOf(
                        InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix"),
                        InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix"),
                    ),
                    learning = null,
                ),
            ),
        ),
    )

    @Test
    fun `a pick writes the package into the step and leaves nothing to choose`() {
        val bound = pending.choose("net.fixture.pellix.two")!!

        assertEquals(ArgSource.Literal("net.fixture.pellix.two"), bound.steps.single().invocation.args["app"])
        assertEquals(ValueOrigin.Ours, bound.origins.getValue(0)["app"])
        assertEquals(emptyList<PendingChoice>(), bound.choices)
    }

    @Test
    fun `a package that was not offered changes nothing`() {
        assertNull(pending.choose("net.fixture.lumora"))
    }

    @Test
    fun `the plan exists only once nothing is left to choose`() {
        assertNull(pending.plan())
        assertEquals(ExecutionPlan(pending.choose("net.fixture.pellix.one")!!.steps), pending.choose("net.fixture.pellix.one")!!.plan())
    }

    // --- A4′ phase 3b, Task 9: a reading of several steps ---------------------------------------

    private val tessarOne = InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar")
    private val tessarTwo = InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar")
    private val pellixChoice = pending.choices.single().choice
    private val tessarChoice = AppChoice(arg = "app", mention = "tessar", candidates = listOf(tessarOne, tessarTwo), learning = null)
    private val said = ValueOrigin.SaidBy(PrincipalId.USER)

    private fun uninstallStep(vararg args: Pair<String, ArgSource>) = PlanStep(
        index = 0,
        invocation = ToolInvocation(ToolId("uninstall_app"), mapOf(*args)),
        risk = ActionRiskLevel.CONFIRM,
        precondition = StepPrecondition.None,
        rationale = StepRationale.MODEL_PROPOSED,
    )

    private fun launchStep(vararg args: Pair<String, ArgSource>) = PlanStep(
        index = 1,
        invocation = ToolInvocation(ToolId("launch_app"), mapOf(*args)),
        risk = ActionRiskLevel.SAFE,
        precondition = StepPrecondition.None,
        rationale = StepRationale.MODEL_PROPOSED,
    )

    /** Step 0 asks which «Pellix» to remove, step 1 which «Tessar» to open: two picks owed, in step order. */
    private val twoOwed = Interpretation.Act(
        goal = AgentGoal("удали pellix и открой tessar", GoalShape.Free("удали pellix и открой tessar")),
        steps = listOf(
            uninstallStep("app_label" to ArgSource.Literal("pellix")),
            launchStep("query" to ArgSource.Literal("tessar")),
        ),
        origins = mapOf(0 to mapOf("app_label" to said), 1 to mapOf("query" to said)),
        effects = mapOf(0 to ToolEffect.EXTERNAL, 1 to ToolEffect.EXTERNAL),
        choices = listOf(PendingChoice(step = 0, choice = pellixChoice), PendingChoice(step = 1, choice = tessarChoice)),
    )

    /**
     * Ruling «choose answers the HEAD»: while step 0's pick is owed, a candidate of step 1's is no answer;
     * each pick writes into ITS step, marks that step's origin ours, and leaves the next pick at the head.
     */
    @Test
    fun `picks are owed one at a time, in step order`() {
        assertNull("a candidate of the second pick is no answer while the first is owed", twoOwed.choose(tessarTwo.packageName))

        val first = twoOwed.choose("net.fixture.pellix.two")
        assertEquals(
            "the first pick binds step 0 only, and step 1's pick is now the head",
            Interpretation.Act(
                goal = twoOwed.goal,
                steps = listOf(
                    uninstallStep("app_label" to ArgSource.Literal("pellix"), "app" to ArgSource.Literal("net.fixture.pellix.two")),
                    launchStep("query" to ArgSource.Literal("tessar")),
                ),
                origins = mapOf(0 to mapOf("app_label" to said, "app" to ValueOrigin.Ours), 1 to mapOf("query" to said)),
                effects = twoOwed.effects,
                choices = listOf(PendingChoice(step = 1, choice = tessarChoice)),
            ),
            first,
        )

        assertEquals(
            "the second pick binds step 1, and nothing is owed",
            Interpretation.Act(
                goal = twoOwed.goal,
                steps = listOf(
                    uninstallStep("app_label" to ArgSource.Literal("pellix"), "app" to ArgSource.Literal("net.fixture.pellix.two")),
                    launchStep("query" to ArgSource.Literal("tessar"), "app" to ArgSource.Literal("net.fixture.tessar.one")),
                ),
                origins = mapOf(
                    0 to mapOf("app_label" to said, "app" to ValueOrigin.Ours),
                    1 to mapOf("query" to said, "app" to ValueOrigin.Ours),
                ),
                effects = twoOwed.effects,
                choices = emptyList(),
            ),
            first!!.choose(tessarOne.packageName),
        )
    }

    /** Two picks owed, then one, then none: only the last has a plan, and it carries both picks. */
    @Test
    fun `the plan exists only when no pick is owed`() {
        assertNull("two picks owed", twoOwed.plan())

        val first = twoOwed.choose("net.fixture.pellix.one")!!
        assertNull("one pick still owed, on the second step", first.plan())

        assertEquals(
            ExecutionPlan(
                listOf(
                    uninstallStep("app_label" to ArgSource.Literal("pellix"), "app" to ArgSource.Literal("net.fixture.pellix.one")),
                    launchStep("query" to ArgSource.Literal("tessar"), "app" to ArgSource.Literal("net.fixture.tessar.two")),
                ),
            ),
            first.choose(tessarTwo.packageName)!!.plan(),
        )
    }

    /**
     * Controller ruling E9-P3, B-18: the one conversion of a model step into the plan's step. Its index is
     * its position; the device's bound words — not the model's own (step 0's differ on purpose) — become
     * literals; a reference becomes [ArgSource.FromStep] on the earlier step's output, which the engine
     * resolves at run time; the rest is the step's own risk, no precondition, `MODEL_PROPOSED`.
     */
    @Test
    fun `a reference becomes a FromStep binding in the plan`() {
        val sieve = ProposedStep(
            tool = ToolId("fixture_sieve"),
            args = mapOf("name" to "lumora"),
            origins = mapOf("name" to said),
            risk = ActionRiskLevel.SAFE,
            effect = ToolEffect.LOCAL,
        )
        val mortar = ProposedStep(
            tool = ToolId("fixture_mortar"),
            args = mapOf("label" to "ostravel"),
            origins = mapOf("label" to said),
            risk = ActionRiskLevel.CONFIRM,
            effect = ToolEffect.EXTERNAL,
            refs = mapOf("target" to StepRef(step = 0, key = "kept")),
        )
        val reading = Interpretation.Act(
            goal = AgentGoal("fixture chain", GoalShape.Free("fixture chain")),
            steps = listOf(sieve.toPlanStep(0, mapOf("item" to "net.fixture.lumora")), mortar.toPlanStep(1, mortar.args)),
            origins = mapOf(0 to sieve.origins, 1 to mortar.origins),
            effects = mapOf(0 to sieve.effect, 1 to mortar.effect),
            choices = emptyList(),
        )

        assertEquals(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(ToolId("fixture_sieve"), mapOf("item" to ArgSource.Literal("net.fixture.lumora"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(
                            ToolId("fixture_mortar"),
                            mapOf("label" to ArgSource.Literal("ostravel"), "target" to ArgSource.FromStep(stepIndex = 0, key = "kept")),
                        ),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
            ),
            reading.plan(),
        )
    }
}
