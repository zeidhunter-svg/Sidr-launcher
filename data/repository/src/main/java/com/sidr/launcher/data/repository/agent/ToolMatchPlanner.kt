package com.sidr.launcher.data.repository.agent

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
import javax.inject.Inject

/**
 * **One generic arm, not a taxonomy** (spec §8.4). A recognised tool becomes a one-step plan, whatever
 * the tool is — so every tool `A1"` added cost one vocabulary entry and **zero** planner branches.
 * (The number is deliberately not written: this line said "twelve tools" and was already false when
 * phase 3b shipped the eleven navigating ones — R14-41.)
 * The rejected alternative was one `GoalShape` value and one arm per tool, which is linear per tool
 * and contradicts in code the claim this whole block exists to establish.
 *
 * `GoalShape` gains nothing: this plans the `Free` value A0.5 already added, which until now answered
 * `NoPlan` on Android. `StepRationale` gains nothing either — a one-step plan is `GOAL_DIRECT`, and
 * the surface names the tool rather than assuming a launch.
 *
 * **Risk comes from the registry, never from here.** The planner records what the registry declares at
 * plan time, and `AgentExecutor` gates on `maxOf(step.risk, registry.find(...)?.risk ?: DANGEROUS)`
 * (A0 finding F2), so a tool that becomes riskier between planning and execution still stops.
 *
 * **It recognises; it does not validate the value.** [ToolSelector] hands the remainder over
 * **normalized** — lower-cased and whitespace-collapsed, never the raw span; see `ToolVocabulary`'s
 * KDoc for the free-text limitation that implies — and this planner only checks that a required
 * argument is *present*. Whether "10 minutes" is
 * a readable duration is the worker's question, and a tool whose value cannot be read fails closed
 * there (`Tier0IntentToolWorker.parseSeconds`). The consequence, named rather than implied: a goal like
 * "timer for the meeting" produces a plan that fails at the worker instead of falling through to the
 * model. Closing that needs an argument type richer than `ArgType.STRING` — the A0.5 finding spec
 * §10.4 already records — not a second parser here.
 *
 * **An argument the tool DECLARES to carry an app is resolved to a package HERE, above the consent
 * checkpoint** (review finding C4, owner decision 2026-09-18; fork F5, «consent fires before argument
 * binding»). Which argument that is, if any, is read from [ToolArgumentSorts] — never from the
 * argument's spelling (A4′ phase 0, spec §3 0.4). `AgentExecutor` evaluates `checkpointFor` before it
 * ever reaches `toolExecutor.invoke`, so anything a **worker** resolves is resolved *after* the user
 * has already consented: the card for «удали telegram»
 * could then only echo the words typed, while the package that would actually be uninstalled did not
 * yet exist — and on a device carrying two Telegram-like labels the user would confirm one thing and
 * get another. Resolving in the planner is what makes the plan, and therefore the card, name the target
 * that will be acted on. A resolution failure is [PlanningResult.NoPlan]: the decline happens *before*
 * a plan exists, so no consent card is ever drawn for a name that could not be resolved.
 * [AppTargetResolver.resolve] returns `null` to **decline**, never "fall back to the raw text" —
 * treating it otherwise would re-open precisely the guess it refuses to make. This does not close F5 in
 * general; it closes it for the arguments this planner can resolve deterministically.
 *
 * **The sort is a catalog row, not a string convention** (A4′ phase 0). Until then a descriptor opted
 * in by declaring an argument literally named `app` — `const val APP_ARG`, a sort implemented by
 * comparing strings, which keyed on the name and never on `required` (R14-37: an OPTIONAL `app` the
 * vocabulary did not supply answered `NoPlan` for every goal, with the suite green). Now a tool opts in
 * only through its row in [ToolArgumentSorts]: [AppArgumentBinding.arg] names the argument to resolve,
 * and [AppArgumentBinding.labelArg], when the row names one **and** the descriptor declares it, receives
 * the raw text the user typed, so a surface can show both what was said and what it resolved to. Three
 * consequences, stated rather than discovered later:
 *  - a tool with **no row** is passed through untouched — an argument named `app` earns no resolution
 *    by its spelling, and a missing row resolves nothing rather than guessing;
 *  - a declared argument that is **optional** and that the vocabulary did not supply stays absent:
 *    nothing to resolve, nothing to decline (a **required** one never arrives blank — the
 *    required-argument check has already declined);
 *  - a descriptor must declare its label argument as **not required**, because the vocabulary never
 *    supplies it (this planner does) while the required-argument check runs *before* resolution.
 *
 * What holds the rows to the descriptors is a test, not the type system: `ToolArgumentSortGuardTest`
 * checks both directions over the production authored sources — every tool declaring an argument named
 * `app` has a row (the bridge from the old convention, which is why that test still reads the name), and
 * no row names an argument its tool does not declare. A typed argument kind — the one thing that would
 * make this a contract rather than a catalog — is the `ArgType` debt (spec §7.5); `F6` stays in force
 * with its review appointed for phase 3 (spec §7.8), and `ArgType` is deliberately **not** widened here.
 *
 * **Selection now also covers third-party names.** [ToolSelector] (Task 10) chooses between our
 * authored, localized vocabulary and a dynamic `app_shortcut` name, declining rather than guessing when
 * neither or both claim the text. This planner deliberately learns nothing about that distinction: a
 * [ToolMatch] arrives the same shape whichever source produced it, and the one-step plan built from it
 * cannot say which kind of tool it names.
 */
class ToolMatchPlanner @Inject constructor(
    private val selector: ToolSelector,
    private val appTargets: AppTargetResolver,
    private val sorts: ToolArgumentSorts,
) : Planner {

    override suspend fun plan(request: PlanningRequest, registry: ToolRegistry): PlanningResult {
        // Exhaustive with no `else`: a third GoalShape must make a deliberate decision here.
        val text = when (val shape = request.goal.shape) {
            is GoalShape.Free -> shape.text
            is GoalShape.AppNotInstalled -> return PlanningResult.NoPlan
        }

        val match = selector.select(text) ?: return PlanningResult.NoPlan
        val descriptor = registry.find(match.id) ?: return PlanningResult.NoPlan

        // Every required argument must have a non-blank value. Nothing is defaulted or substituted:
        // an agent that silently runs a half-filled tool is worse than one that declines. The
        // vocabulary already refuses a trigger with nothing after it, so what this catches is the
        // vocabulary and the tool disagreeing about the argument's NAME — nothing pins those together.
        val required = descriptor.argSchema.filter { it.required }.map { it.name }
        if (required.any { match.args[it].isNullOrBlank() }) return PlanningResult.NoPlan

        // An app name becomes a package HERE — above the consent checkpoint, see the class KDoc.
        //
        // **What decides is the tool's DECLARED sort, not the argument's spelling** (A4' phase 0,
        // spec §3 0.4). `const val APP_ARG = "app"` was a definition of a sort implemented by
        // comparing strings, and R14-37 is the failure mode that produced: it keyed on the NAME and
        // never on `required`, so a descriptor declaring an OPTIONAL `app` returned `NoPlan` for
        // every goal, forever, with a green suite. Both halves are fixed here — the sort is
        // declared, and an absent optional value is a value that was not needed.
        val binding = sorts.appBindingFor(descriptor.id)
        val appArg = binding?.arg?.let { name -> descriptor.argSchema.firstOrNull { it.name == name } }

        val resolvedArgs = buildMap<String, String> {
            putAll(match.args)
            if (binding != null && appArg != null) {
                val raw = match.args[binding.arg].orEmpty()
                if (raw.isBlank() && !appArg.required) {
                    // Declared, optional, and the vocabulary supplied nothing. There is nothing to
                    // resolve and nothing to decline: an absent optional argument is absent.
                    remove(binding.arg)
                } else {
                    val target = appTargets.resolve(raw) ?: return PlanningResult.NoPlan
                    put(binding.arg, target)
                    binding.labelArg
                        ?.takeIf { label -> descriptor.argSchema.any { it.name == label } }
                        ?.let { put(it, raw) }
                }
            }
        }

        // An argument the schema does not declare is dropped rather than passed: `InvocationValidator`
        // would reject the whole invocation as UNDECLARED_ARG, and a plan that cannot validate is
        // worse than no plan.
        val args = resolvedArgs
            .filterKeys { key -> descriptor.argSchema.any { it.name == key } }
            .mapValues { (_, value) -> ArgSource.Literal(value) }

        return PlanningResult.Planned(
            ExecutionPlan(
                listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(descriptor.id, args),
                        risk = descriptor.risk,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.GOAL_DIRECT,
                    ),
                ),
            ),
        )
    }
}
