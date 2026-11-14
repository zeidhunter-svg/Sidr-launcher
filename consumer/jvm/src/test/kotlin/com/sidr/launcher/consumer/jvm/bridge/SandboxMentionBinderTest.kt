package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.consumer.jvm.text.JdkTextFolding
import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.consumer.jvm.tool.SandboxToolSource
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.StepRef
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The JVM consumer's [com.sidr.launcher.domain.ai.bridge.MentionBinder] (A4′ phase 3b, Task 13; the plan's
 * finding S2): `find_file` requires a `root` the model cannot know, so the model is never asked for one —
 * the device binds it, after the answer.
 */
class SandboxMentionBinderTest {

    @get:Rule val temp = TemporaryFolder()

    @Test
    fun `the sandbox root is bound by the device and never asked of the model`() = runTest {
        val root = temp.root.toPath()

        // What the model sees of find_file: its name argument, and nothing about a root.
        assertEquals(
            listOf(SandboxToolDescriptions.NAME),
            SandboxToolDescriptions().describe(SandboxToolIds.FIND_FILE)?.args?.map { it.name },
        )

        val goal = AgentGoal("find orvelle.txt and delete it", GoalShape.Free("find orvelle.txt and delete it"))
        val user = ValueOrigin.SaidBy(PrincipalId.USER)
        val find = ProposedStep(
            tool = SandboxToolIds.FIND_FILE,
            args = mapOf(SandboxToolDescriptions.NAME to "orvelle.txt"),
            origins = mapOf(SandboxToolDescriptions.NAME to user),
            risk = ActionRiskLevel.SAFE,
            effect = ToolEffect.LOCAL,
        )
        val delete = ProposedStep(
            tool = SandboxToolIds.DELETE_FILE,
            args = emptyMap(),
            origins = emptyMap(),
            risk = ActionRiskLevel.DANGEROUS,
            effect = ToolEffect.LOCAL,
            refs = mapOf(SandboxKeys.PATH to StepRef(step = 0, key = SandboxKeys.RESOLVED_PATH)),
        )

        val binding = SandboxMentionBinder(root, SandboxFileSort(root, JdkTextFolding), SandboxToolSource())
            .bind(goal, listOf(find, delete))

        val sandbox = root.toRealPath().toString()
        assertEquals(
            Binding.Bound(
                Interpretation.Act(
                    goal = goal,
                    steps = listOf(
                        PlanStep(
                            0,
                            ToolInvocation(
                                SandboxToolIds.FIND_FILE,
                                mapOf(
                                    SandboxKeys.QUERY to ArgSource.Literal("orvelle.txt"),
                                    SandboxKeys.ROOT to ArgSource.Literal(sandbox),
                                ),
                            ),
                            ActionRiskLevel.SAFE,
                            StepPrecondition.None,
                            StepRationale.MODEL_PROPOSED,
                        ),
                        PlanStep(
                            1,
                            ToolInvocation(
                                SandboxToolIds.DELETE_FILE,
                                mapOf(SandboxKeys.PATH to ArgSource.FromStep(stepIndex = 0, key = SandboxKeys.RESOLVED_PATH)),
                            ),
                            ActionRiskLevel.DANGEROUS,
                            StepPrecondition.None,
                            StepRationale.MODEL_PROPOSED,
                        ),
                    ),
                    origins = mapOf(
                        0 to mapOf(SandboxKeys.QUERY to user, SandboxKeys.ROOT to ValueOrigin.Ours),
                        1 to emptyMap(),
                    ),
                    effects = mapOf(0 to ToolEffect.LOCAL, 1 to ToolEffect.LOCAL),
                    choices = emptyList(),
                ),
            ),
            binding,
        )
    }
}
