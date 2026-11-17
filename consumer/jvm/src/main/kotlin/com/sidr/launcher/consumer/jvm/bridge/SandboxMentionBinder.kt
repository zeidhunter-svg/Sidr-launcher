package com.sidr.launcher.consumer.jvm.bridge

import com.sidr.launcher.consumer.jvm.tool.SandboxKeys
import com.sidr.launcher.consumer.jvm.tool.SandboxToolIds
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.StepRef
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.ArgumentSorts
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolRegistry
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * The JVM consumer's [MentionBinder] (A4′ phase 3b, Task 13) — the shape of Android's `DeviceMentionBinder`
 * (a reading bound whole, in plan order; the first step that does not bind answers for all of them; each
 * plan step built by the domain's one conversion, [toPlanStep]), over the sandbox instead of the app list.
 * Nothing it finds goes back to the model: the harness calls it after the model has answered.
 *
 * Per tool, the model's arguments ([SandboxToolDescriptions]) become the tool's:
 *  - `find_file` — the model's [SandboxToolDescriptions.NAME] becomes the tool's `query`, keeping the
 *    model's origin for it (the user's words: the validator checked them derivable); `root` is **bound by
 *    the device** — the sandbox root's real path, [ValueOrigin.Ours] — and never asked of the model;
 *  - every argument [sorts] declares a FILE (`delete_file`'s and `rename_file`'s `path`), given as WORDS, is
 *    resolved through [files] (`SandboxFileSort`): one file binds its path ([ValueOrigin.Ours]); no file is
 *    [Binding.NotFound] (the words, said to the user); unreadable words are [Binding.NotUnderstood];
 *  - a reference the model wrote stays a reference ([ProposedStep.refs] → `ArgSource.FromStep`), which the
 *    engine resolves at run time — and, when the step it names produced nothing, refuses
 *    (`UNRESOLVED_ARG_SOURCE`) rather than act;
 *  - every other argument passes as the model's words, with the validator's origin.
 *
 * **A named limit: a file choice fails closed.** [SortResolution.Choice] — two files of one name, only a
 * word form of one, or (for `delete_file`, owner decision 2026-11-17) one named exactly only after folding —
 * is [Binding.Unavailable] here, as a value sort's choice is on Android: the pick a card
 * owes ([com.sidr.launcher.domain.ai.bridge.AppChoice]) carries installed apps, and this binder does not
 * dress a file as one. Nothing runs; the user is told the reading could not be bound. A file pick is a
 * domain type this phase does not add.
 *
 * [rebind] (Task 11's port) re-binds a remainder against the CURRENT sandbox: `find_file`'s `root` again; a
 * literal FILE path kept only if it is still a regular file inside the sandbox, else [Binding.NotFound];
 * effects from the current [registry], an unregistered tool [Binding.Unavailable] for the whole remainder;
 * every literal not re-bound is [ValueOrigin.ModelGenerated] (controller ruling E11-P1). **Unreachable on
 * this consumer today and held by no test:** the harness has no remainder card — its restore continues a
 * persisted session through the engine — so nothing here calls [rebind].
 */
class SandboxMentionBinder(
    private val root: Path,
    private val files: SortResolver,
    private val registry: ToolRegistry,
    private val sorts: ArgumentSorts = SandboxArgumentSorts,
) : MentionBinder {

    override suspend fun bind(goal: AgentGoal, steps: List<ProposedStep>): Binding {
        val bound = steps.mapIndexed { index, proposed ->
            when (val step = bindStep(index, proposed)) {
                is StepBinding.Bound -> step
                is StepBinding.Refused -> return step.binding
            }
        }
        return Binding.Bound(act(goal, bound))
    }

    override suspend fun rebind(goal: AgentGoal, plan: ExecutionPlan): Binding {
        val bound = plan.steps.mapIndexed { index, planned ->
            when (val again = rebindStep(index, planned)) {
                is StepBinding.Bound -> again
                is StepBinding.Refused -> return again.binding
            }
        }
        return Binding.Bound(act(goal, bound))
    }

    /** One card for every step bound, in plan order. No pick is ever owed here (see the class KDoc). */
    private fun act(goal: AgentGoal, bound: List<StepBinding.Bound>) = Interpretation.Act(
        goal = goal,
        steps = bound.map { it.step },
        origins = bound.associate { it.step.index to it.origins },
        effects = bound.associate { it.step.index to it.effect },
        choices = emptyList(),
    )

    private suspend fun bindStep(index: Int, proposed: ProposedStep): StepBinding {
        val words = LinkedHashMap<String, String>()
        val origins = LinkedHashMap<String, ValueOrigin>()
        val fileArgs = fileArgsOf(proposed) ?: return StepBinding.Refused(Binding.Unavailable)

        if (proposed.tool == SandboxToolIds.FIND_FILE) {
            proposed.args[SandboxToolDescriptions.NAME]?.let { name ->
                words[SandboxKeys.QUERY] = name
                origins[SandboxKeys.QUERY] = proposed.origins[SandboxToolDescriptions.NAME] ?: ValueOrigin.ModelGenerated
            }
            words[SandboxKeys.ROOT] = sandboxRoot() ?: return StepBinding.Refused(Binding.Unavailable)
            origins[SandboxKeys.ROOT] = ValueOrigin.Ours
            return step(index, proposed, words, origins)
        }

        for ((name, value) in proposed.args) {
            if (name !in fileArgs) {
                words[name] = value
                origins[name] = proposed.origins[name] ?: ValueOrigin.ModelGenerated
                continue
            }
            // Owner decision 2026-11-17 («1: a»): an irreversible tool (`delete_file`) binds only a name exact as
            // written; a fold-only match is a choice, which this binder fails closed (the class KDoc).
            val irreversible = registry.find(proposed.tool)?.durability != ToolDurability.TRANSIENT
            when (val resolution = files.resolve(value, irreversible)) {
                is SortResolution.Resolved -> {
                    words[name] = resolution.value
                    origins[name] = ValueOrigin.Ours
                }
                SortResolution.NotFound -> return StepBinding.Refused(Binding.NotFound(value))
                SortResolution.Unreadable -> return StepBinding.Refused(Binding.NotUnderstood(value))
                is SortResolution.Choice, SortResolution.Unavailable -> return StepBinding.Refused(Binding.Unavailable)
            }
        }
        return step(index, proposed, words, origins)
    }

    private fun rebindStep(index: Int, planned: PlanStep): StepBinding {
        val tool = planned.invocation.id
        val effect = registry.find(tool)?.effect ?: return StepBinding.Refused(Binding.Unavailable)
        val literals = planned.invocation.args.mapNotNull { (name, source) -> (source as? ArgSource.Literal)?.let { name to it.value } }.toMap()
        val refs = planned.invocation.args.mapNotNull { (name, source) -> (source as? ArgSource.FromStep)?.let { name to StepRef(it.stepIndex, it.key) } }.toMap()
        val words = LinkedHashMap(literals)
        val origins = LinkedHashMap<String, ValueOrigin>(literals.mapValues { ValueOrigin.ModelGenerated })

        if (tool == SandboxToolIds.FIND_FILE) {
            words[SandboxKeys.ROOT] = sandboxRoot() ?: return StepBinding.Refused(Binding.Unavailable)
            origins[SandboxKeys.ROOT] = ValueOrigin.Ours
        }
        for (name in sorts.all()[tool].orEmpty().keys) {
            val path = literals[name] ?: continue
            when (stillThere(path)) {
                true -> origins[name] = ValueOrigin.Ours
                false -> return StepBinding.Refused(Binding.NotFound(path))
                null -> return StepBinding.Refused(Binding.Unavailable)
            }
        }
        val proposed = ProposedStep(tool, words, origins, planned.risk, effect, refs)
        return step(index, proposed, words, origins)
    }

    /** The arguments of [proposed]'s tool declared a FILE; `null` when one carries a sort this binder cannot read. */
    private fun fileArgsOf(proposed: ProposedStep): Set<String>? {
        val declared = sorts.all()[proposed.tool].orEmpty()
        if (declared.values.any { it.sort != ArgSorts.FILE }) return null
        return declared.keys
    }

    /** The sandbox root's real path — what `workspace_info` reports and `find_file` searches — or `null`. */
    private fun sandboxRoot(): String? =
        runCatching { root.toAbsolutePath().normalize().toRealPath().toString() }.getOrNull()

    /** Whether [path] is still a regular file inside the sandbox; `null` = the sandbox could not be read. */
    private fun stillThere(path: String): Boolean? = runCatching {
        val base = root.toAbsolutePath().normalize().toRealPath()
        val file = Path.of(path).toAbsolutePath().normalize()
        file.startsWith(base) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
    }.getOrNull()

    private fun step(index: Int, proposed: ProposedStep, words: Map<String, String>, origins: Map<String, ValueOrigin>) =
        StepBinding.Bound(proposed.toPlanStep(index, words), origins, proposed.effect)

    private sealed interface StepBinding {
        data class Bound(val step: PlanStep, val origins: Map<String, ValueOrigin>, val effect: ToolEffect) : StepBinding
        data class Refused(val binding: Binding) : StepBinding
    }
}
