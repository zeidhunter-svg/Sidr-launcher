package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeValidator
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.Verdict
import com.sidr.launcher.domain.ai.bridge.modelTextGoingOut
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolRegistry

/**
 * What the learned-plan cache made of a command (A4′ phase 4, plan §0.2 B-4, B-5): a reading bound on this
 * device, from the one pinned [CommandForm] the command fits — or nothing. There is no third answer: the cache
 * never says «нет такого приложения» or «не понял»; whatever it cannot bind is a [Miss], and the command goes
 * on down the chain exactly as if nothing were learned (E6-R1: a deterministic miss is not a filter on
 * understanding).
 */
sealed interface Replay {
    data object Miss : Replay
    data class Bound(val act: Interpretation.Act, val form: CommandForm) : Replay
}

/**
 * The learned-plan cache (spec §8) as the route sees it: [replay] before the model is asked (step 2c), and
 * [learnable] — what a fresh model reading would teach — beside step 7's card. [NONE] replays nothing and
 * teaches nothing; it reads no store.
 */
interface LearnedPlans {
    suspend fun replay(command: String): Replay
    fun learnable(command: String, steps: List<ProposedStep>): LearnedPlanShape?

    companion object {
        val NONE: LearnedPlans = object : LearnedPlans {
            override suspend fun replay(command: String): Replay = Replay.Miss
            override fun learnable(command: String, steps: List<ProposedStep>): LearnedPlanShape? = null
        }
    }
}

/**
 * **Replay is step 7 without the model** (B-4). The pinned form the command fits takes the command's words
 * ([LearnedForms.match]); the template is filled into the answer the model would have written
 * ([LearnedForms.fill]); that answer is judged by the same [BridgeValidator] step 7's planner uses — against
 * **today's** offering (`BridgeFilter.offeringOf(registry, levels, descriptions)`), the registry, **this**
 * command and the device's [folding] — and the steps it admits are bound by the same [MentionBinder], under the
 * same goal step 7 builds. Risk and effect are therefore today's registry's, and every package today's binder's:
 * the template holds no package (spec §8.2), so an app the plan was learned on and since removed binds nothing.
 *
 * **Anything but a bound reading is a miss** (B-5): an unreadable store (it fails open to step 7, named beside
 * A1′ residual (3)); no pinned form, or more than one, fitting the command; a validator rejection (a tool no
 * longer offered, a word no longer derivable); a binding that is not [Binding.Bound]. A miss writes nothing:
 * a stale entry is kept for the day its tool returns (B-9), and forgetting is the user's.
 *
 * Nothing here is cached between calls — every replay reads the store, validates and binds again.
 *
 * **A throw is not caught**, exactly as step 7 catches none: `RouteCommandUseCase` calls the binder bare, and
 * `BridgeModelPlanner` calls the validator bare; a store reports its failures as [OperationResult.Failure],
 * which is a miss here. A [kotlinx.coroutines.CancellationException] therefore always propagates.
 */
class DefaultLearnedPlans(
    private val store: LearnedPlanStore,
    private val registry: ToolRegistry,
    private val descriptions: ToolDescriptions,
    private val levels: Set<ToolLevel>,
    private val binder: MentionBinder,
    private val folding: TextFolding,
) : LearnedPlans {

    override suspend fun replay(command: String): Replay {
        val trimmed = command.trim()
        val plans = when (val read = store.all()) {
            is OperationResult.Success -> read.value
            is OperationResult.Failure -> return Replay.Miss
        }
        // Exactly one PINNED form must fit (B-5): a form below the threshold is not consulted at all, and two
        // that fit would make the cache choose between two understandings — that is the model's.
        val (plan, slots) = plans
            .filter { it.pinned }
            .mapNotNull { plan -> LearnedForms.match(plan.shape.form, trimmed, folding)?.let { slots -> plan to slots } }
            .singleOrNull()
            ?: return Replay.Miss

        val offering = BridgeFilter.offeringOf(registry, levels, descriptions)
        val verdict = BridgeValidator.validate(LearnedForms.fill(plan.shape.template, slots), offering, registry, trimmed, folding)
        val proposed = (verdict as? Verdict.Valid)?.planning as? ModelPlanning.Proposed ?: return Replay.Miss

        // The goal step 7 builds (`RouteCommandUseCase`, step 7) — the binder cannot tell a replay from a reading.
        val goal = AgentGoal(text = trimmed, shape = GoalShape.Free(trimmed))
        return when (val binding = binder.bind(goal, proposed.steps)) {
            is Binding.Bound -> Replay.Bound(binding.interpretation, plan.shape.form)
            is Binding.NotFound, is Binding.NotUnderstood, Binding.Unavailable -> Replay.Miss
        }
    }

    override fun learnable(command: String, steps: List<ProposedStep>): LearnedPlanShape? =
        LearnedForms.learn(command, steps, folding)
}

/**
 * Whether a bound reading needs the interpretation card before it runs (B-7): a pick is owed, or some step carries
 * the model's own text out of the phone — [modelTextGoingOut], the ONE function the card's `ModelValues` draws from
 * too (`feature/launcher`, `InterpretationSurface`, `DOC-MZN-2`), so «the card shows model text» and «the card is
 * needed for model text» are the same reading by construction: per step, keyed on its index, an
 * [ToolEffect.EXTERNAL] effect and a [ValueOrigin.ModelGenerated] origin whose argument is an [ArgSource.Literal] —
 * except the words that name the step's app ([Interpretation.Act.appWords], controller ruling T2-R2): the package the
 * device bound reaches the world, not the model's name for it. The user's own words going out need no card; they are
 * theirs.
 *
 * **One rule for two callers** (A4′ acceptance tail, task 4 — D3, second amendment): a learned command replayed at
 * step 2c and a FRESH model reading at step 7 start at once exactly when this is `false`; either way the engine's
 * consent card for a risky step is unchanged (`requiresConsent(risk)`, `DOC-ADL-1`).
 */
fun Interpretation.Act.needsCard(): Boolean =
    choices.isNotEmpty() || steps.any { step -> modelTextGoingOut(step).isNotEmpty() }
