package com.sidr.launcher.domain.agent

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolutionResult
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult

/**
 * The remainder of a plan that acted (A4′ phase 3b, Task 11; fork 3b-A «nothing leaves», plan §0.2 B-10)
 * — the first consumer of [PlanningRequest.priorObservations] and of [PlanningRequest.previous]. It reads
 * [PlanningRequest.previous] over what that attempt recorded and answers:
 *  - [PlanningResult.Planned] with the steps AFTER the last step whose result is [ToolResult.Effected] or
 *    [ToolResult.HandedOff] (all of them when none acted), re-indexed from 0 — `ExecutionPlan`'s
 *    invariant — with their tool, risk, precondition and rationale (`MODEL_PROPOSED`) kept. A step that
 *    only LOOKED after the last act is in the remainder: looking again cannot do anything twice. The plan
 *    is EMPTY when the last step itself acted — nothing is left, which is not "no plan" (controller
 *    ruling E11-P6);
 *  - a reference ([ArgSource.FromStep]) into a step at or before the cut — a finished step, not run
 *    again — becomes the value that step recorded, as an [ArgSource.Literal], resolved by
 *    [InvocationValidator.resolve], the engine's own binding; a reference into a step inside the
 *    remainder moves with it, by the cut (controller ruling E11-P2);
 *  - [PlanningResult.NoPlan] when there is no previous plan, when it is an undo ([Compensation.isUndo]:
 *    its goal is the ORIGINAL goal, §6.4), or when a reference into a finished step has no value — the
 *    remainder cannot be written honestly, and a guessed or blank value would be worse than none.
 *
 * **It asks no model and reads no registry** (`DOC-ADL-3`; [plan]'s `registry` is the port's, unread): a
 * remainder is cut from a plan the user already approved; the device re-binds its apps before the card
 * (`MentionBinder.rebind`), and `StartAgentSessionUseCase.startPlanned` validates it against the registry
 * at «Выполнить», as every plan. Whether a session's remainder is asked for at all — a model plan, not an
 * undo, no call in flight — is `ResumeAgentSessionUseCase`'s rule, not this class's. It is NOT one of the
 * composition root's `CompositePlanner` planners (step 2b's and the resume's fresh look), and must not be:
 * it answers only a request that carries a previous plan.
 */
class RemainderPlanner : Planner {

    override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult = remainderOf(request)

    /** [plan] without the port's suspension — pure, so a surface can ask whether a remainder exists. */
    fun remainderOf(request: PlanningRequest): PlanningResult {
        val previous = request.previous ?: return PlanningResult.NoPlan
        if (Compensation.isUndo(previous)) return PlanningResult.NoPlan
        val observations = request.priorObservations
        val cut = previous.steps.indexOfLast { observations[it.index].acted() }
        val shift = cut + 1
        val steps = previous.steps.drop(shift).map { step ->
            val args = step.invocation.args.mapValues { (name, source) ->
                when (source) {
                    is ArgSource.Literal -> source
                    is ArgSource.FromStep ->
                        if (source.stepIndex <= cut) {
                            valueOf(step, name, source, observations) ?: return PlanningResult.NoPlan
                        } else {
                            ArgSource.FromStep(source.stepIndex - shift, source.key)
                        }
                }
            }
            step.copy(index = step.index - shift, invocation = step.invocation.copy(args = args))
        }
        return PlanningResult.Planned(ExecutionPlan(steps))
    }

    /** The finished step's recorded value for one argument, through the engine's own binding; `null` if none. */
    private fun valueOf(
        step: PlanStep,
        name: String,
        source: ArgSource.FromStep,
        observations: Map<Int, ToolResult>,
    ): ArgSource.Literal? =
        when (val resolved = InvocationValidator.resolve(ToolInvocation(step.invocation.id, mapOf(name to source)), observations)) {
            is ResolutionResult.Resolved -> ArgSource.Literal(resolved.invocation.args.getValue(name))
            is ResolutionResult.Rejected -> null
        }

    private fun ToolResult?.acted(): Boolean = this is ToolResult.Effected || this is ToolResult.HandedOff
}
