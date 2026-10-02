package com.sidr.launcher.consumer.jvm.plan

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.Planner
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * The second consumer's planner — deterministic, and behind the **same** [Planner] port `TemplatePlanner`
 * sits behind.
 *
 * That is the point of the block that reusing `TemplatePlanner` could not have made: `AgentExecutor`
 * takes no planner at all, and `StartAgentSessionUseCase` takes one by constructor, so a second
 * consumer supplies its own with **zero** change to the engine. `TemplatePlanner` hard-codes
 * `ToolIds.LAUNCH_APP`/`PLAY_STORE_SEARCH`, so reusing it would have forced this consumer to name its
 * tools after Android families — a costume, not a proof (spec §2, `F4`).
 *
 * **Reading the goal is deliberately trivial.** Two verbs, then the rest of the line. Not a parser, not
 * a matcher, not an understanding layer — anything richer would be building this consumer's own
 * FastPath, which is neither this block's subject nor in its budget. A goal it cannot read is
 * [PlanningResult.NoPlan].
 *
 * Like `TemplatePlanner`, it **names no destination**: risk, schema and identity all come from the
 * registry, so the tools can change without the planner changing.
 */
class FilePlanner : Planner {

    override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
        val text = textOf(request.goal) ?: return PlanningResult.NoPlan

        val rename = renameTargetsOf(text)
        if (rename != null) return planRename(rename.first, rename.second, registry)

        val target = removeTargetOf(text) ?: return PlanningResult.NoPlan
        return planRemove(target, registry)
    }

    private fun planRemove(target: String, registry: ToolRegistry): PlanningResult {
        val info = registry.find(SandboxToolIds.WORKSPACE_INFO) ?: return PlanningResult.NoPlan
        val find = registry.find(SandboxToolIds.FIND_FILE) ?: return PlanningResult.NoPlan
        val delete = registry.find(SandboxToolIds.DELETE_FILE) ?: return PlanningResult.NoPlan

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(info.id),
                        risk = info.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(
                            find.id,
                            mapOf(
                                SandboxKeys.QUERY to ArgSource.Literal(target),
                                SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
                            ),
                        ),
                        risk = find.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 2,
                        invocation = ToolInvocation(
                            delete.id,
                            mapOf(SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH)),
                        ),
                        risk = delete.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }

    /** `[workspace_info, find_file(query = a, root ← step 0), rename_file(path ← step 1, new_name = b)]`. */
    private fun planRename(from: String, to: String, registry: ToolRegistry): PlanningResult {
        val info = registry.find(SandboxToolIds.WORKSPACE_INFO) ?: return PlanningResult.NoPlan
        val find = registry.find(SandboxToolIds.FIND_FILE) ?: return PlanningResult.NoPlan
        val rename = registry.find(SandboxToolIds.RENAME_FILE) ?: return PlanningResult.NoPlan

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(info.id),
                        risk = info.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(
                            find.id,
                            mapOf(
                                SandboxKeys.QUERY to ArgSource.Literal(from),
                                SandboxKeys.ROOT to ArgSource.FromStep(0, SandboxKeys.ROOT),
                            ),
                        ),
                        risk = find.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                    PlanStep(
                        index = 2,
                        invocation = ToolInvocation(
                            rename.id,
                            mapOf(
                                SandboxKeys.PATH to ArgSource.FromStep(1, SandboxKeys.RESOLVED_PATH),
                                SandboxKeys.NEW_NAME to ArgSource.Literal(to),
                            ),
                        ),
                        risk = rename.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }

    private fun textOf(goal: AgentGoal): String? = when (val shape = goal.shape) {
        is GoalShape.Free -> shape.text.trim()
        // The Android shape belongs to `TemplatePlanner`. Answering NoPlan rather than guessing is
        // what keeps the two planners from quietly overlapping.
        is GoalShape.AppNotInstalled -> null
    }

    private fun removeTargetOf(text: String): String? {
        val prefix = "$VERB "
        if (!text.startsWith(prefix)) return null
        return text.removePrefix(prefix).trim().takeIf { it.isNotEmpty() }
    }

    /** `rename <a> to <b>`, both trimmed and non-blank, or `null` when the text does not match. */
    private fun renameTargetsOf(text: String): Pair<String, String>? {
        val (from, to) = Regex("^$RENAME_VERB (.+?) to (.+)$").matchEntire(text)?.destructured
            ?.let { (from, to) -> from.trim() to to.trim() }
            ?: return null
        if (from.isBlank() || to.isBlank()) return null
        return from to to
    }

    private companion object {
        const val VERB = "remove"
        const val RENAME_VERB = "rename"
    }
}
