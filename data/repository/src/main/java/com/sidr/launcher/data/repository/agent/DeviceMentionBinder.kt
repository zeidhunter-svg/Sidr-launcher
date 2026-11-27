package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.PendingChoice
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.StepRef
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceKeys
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionDecision
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.sort.ArgSort
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.SortResolution
import com.sidr.launcher.domain.sort.SortResolver
import com.sidr.launcher.domain.text.TextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * The bridge's [MentionBinder] on Android (spec §7.12.1, §7.12.3 condition 2; plan §0.2 B-3).
 *
 * For a tool with an app-sorted argument ([CatalogArgumentSorts.appArgOf]) the model's word for the app
 * ([AuthoredToolDescriptions.MENTION_ARG]) leaves the model-facing name: the package goes into the
 * row's argument once known, the user's words into its `labelArg`. [AppTargetResolver.candidates] answers —
 * it never guesses and consults aliases; two candidates are a choice for the USER before any card, and a
 * word FORM of a name (phase 3b, fork 3b-G: «миравину» for «Миравин») is a pick of that one app marked
 * [AppChoice.confirm] — «Вы имели в виду…?» — never a bind. So is a name exact only after folding when the
 * tool's act cannot be undone (`uninstall_app`; owner decision 2026-11-17, [forIrreversible]).
 *
 * **A name the MODEL wrote** (amendment D1, ADR «2026-11-22 — D1, поправка: имя приложения, исправленное
 * моделью») — the mention `BridgeValidator` admitted as [ValueOrigin.ModelGenerated] because the user's words
 * do not contain it («Claude» for «открой клод») — binds only to an installed label that is exactly it after the
 * folding, or to an alias exactly ([AppTargetResolver.exactly]): one such app binds, several are the user's
 * pick, none is [Binding.NotFound]. No word form is read for the model's word. The words the tool shows keep the
 * model's mark, and are recorded as the step's app words ([Interpretation.Act.appWords]; controller ruling T2-R2, A4′
 * acceptance tail task 4): they name the app this binding turns into a package, and the package — not the words —
 * reaches the world, so `needsCard()` and the card's model values do not count them («открой клод» → «Claude» starts
 * at once, the owner's C1). For an irreversible tool even an exact answer is a pick to confirm (the user did not type
 * the name). [rebind] reads a remainder's kept words as the user's, as before: the plan on disk keeps no origin.
 *
 * **Learned choices answer for `launch_app` only**, with FastPath's policy — a confident one binds
 * without asking, a weak one is offered first — and for a confirmation exactly as for a choice between
 * several (B-3): the same key, the same read, so a form confirmed three times binds without asking.
 * `uninstall_app` and `open_app_info` neither carry a token nor read one: consent learned for opening an
 * app is not consent for removing it (`AppTargetResolver`). The key is [LearnedChoiceKeys.launch] of the
 * model's mention (B-4) — the app name, folded — the same key FastPath writes for the name it extracted
 * (phase 3b, Task 7): a choice learned for «открой tessar» on FastPath is the one read here for the
 * mention «tessar» (`DeviceMentionBinderTest > a choice learned by FastPath for «открой tessar» is the one
 * the bridge reads for «tessar»`).
 *
 * **Every VALUE the model wrote is read before the card, too** (phase 3b, Task 6; fork 3b-C, B-5; the
 * plan's Review Focus 5). Each argument [CatalogArgumentSorts] declares with a value sort — `set_timer`'s
 * duration, `set_alarm`'s clock time, `open_url`'s address — goes through the resolver the sort registry
 * (`Map<ArgSort, SortResolver>`, `SortProvidesModule` in `:app`) holds for it, before the app mention is
 * bound: `HTTPS://…` reaches the card as `https://…`, and «полчасика», an address with a space in it, or one
 * whose host is not plain ASCII (`UrlSort`'s host check, owner ruling on batch 1 question 2, 2026-11-27 — a
 * homograph «https://аpple.com», punycode, userinfo)
 * is [Binding.NotUnderstood] — said to the user, never a card that fails after «Выполнить». The app sort's
 * registry entry is not read here: the mention keeps its own binding above, with learned choices.
 * **A named limit (controller ruling E6-R2):** the words [Binding.NotUnderstood] carries are what the
 * MODEL wrote for the argument, and «Не понял «…».» quotes them with no model mark — they may be the
 * user's words or the model's own («1.5 hours»). Nothing runs and nothing leaves; a model-marked variant
 * would be a sixth string key, which is the owner's decision.
 *
 * **A reading of several steps is bound whole** (phase 3b Task 9; fork 3b-B, §7.12.3 condition 2): each
 * step in plan order, exactly as one step is bound above — values first, then the app mention, learned
 * choices for `launch_app` only — and the first step that is not bound ([Binding.NotFound],
 * [Binding.NotUnderstood], [Binding.Unavailable]) is the answer for the whole reading: no card for the
 * steps that did bind. Otherwise one [Interpretation.Act] carries every step, its origins and effect keyed
 * by the step's position, and every owed pick as a [PendingChoice] in step order — each launch pick with
 * its own learning token (B-19, the binder's half). The plan's steps are built by the domain's one
 * conversion, [toPlanStep], so a reference the model wrote reaches the plan as `ArgSource.FromStep`; on
 * Android no authored tool describes an output yet (B-8), so none arrives here today.
 *
 * **The remainder of a model plan that acted is bound AGAIN before its card** ([rebind]; A4′ phase 3b,
 * Task 11, fork 3b-A, B-10) — after a pause or a failure the apps it named may be gone. Each app argument is
 * re-resolved from the user's words the step kept in its `labelArg` (`launch_app`'s `query`,
 * `uninstall_app`'s `app_label`), through the SAME mention branch as [bind] — candidates, learned choices
 * for `launch_app` only, a pick with its own token (controller ruling E11-P4); a step that kept the package
 * only (`open_app_info`) is checked against the installed list instead. A package no longer installed is
 * [Binding.NotFound] («нет такого приложения»), never launched stale. Origins (controller ruling E11-P1):
 * the re-bound package is [ValueOrigin.Ours], its words [ValueOrigin.SaidBy] the user, as in [bind]; every
 * other literal — a value the model wrote, or one a finished step recorded — is [ValueOrigin.ModelGenerated],
 * so an `EXTERNAL` step shows it (over-shown, never under-shown). Effects are the CURRENT [registry]'s; a
 * step whose tool is no longer registered answers [Binding.Unavailable] for the whole remainder. Value
 * arguments are NOT re-read: they were read before the first card and are not observations of the world.
 *
 * Nothing here reaches the model: `RouteCommandUseCase` calls this after the model has answered, and
 * returns what it finds to the screen; the remainder card is reached from the launcher's resume, with no
 * model asked.
 */
class DeviceMentionBinder @Inject constructor(
    private val appTargets: AppTargetResolver,
    private val sorts: CatalogArgumentSorts,
    private val resolvers: Map<ArgSort, @JvmSuppressWildcards SortResolver>,
    private val preferences: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
    private val folding: TextFolding,
    // A4′ phase 3b, Task 11 (ruling E11-P1): a remainder's effects are the current registry's.
    private val registry: ToolRegistry,
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

    /** The remainder of a model plan that acted, bound again — see the class KDoc. */
    override suspend fun rebind(goal: AgentGoal, plan: ExecutionPlan): Binding {
        val bound = plan.steps.mapIndexed { index, planned ->
            when (val again = rebindStep(index, planned)) {
                is StepBinding.Bound -> again
                is StepBinding.Refused -> return again.binding
            }
        }
        return Binding.Bound(act(goal, bound))
    }

    /** One card for every step bound, in plan order, every owed pick in step order (B-19). */
    private fun act(goal: AgentGoal, bound: List<StepBinding.Bound>) = Interpretation.Act(
        goal = goal,
        steps = bound.map { it.step },
        origins = bound.associate { it.step.index to it.origins },
        effects = bound.associate { it.step.index to it.effect },
        choices = bound.mapNotNull { step -> step.choice?.let { PendingChoice(step.step.index, it) } },
        appWords = bound.mapNotNull { step -> step.appWords?.let { step.step.index to it } }.toMap(),
    )

    /** One step at plan position [index], bound exactly as a one-step reading always was. */
    private suspend fun bindStep(index: Int, proposed: ProposedStep): StepBinding {
        // Every VALUE the model wrote for a sorted argument is read on the device first (A4′ phase 3b,
        // Task 6) — through the sort registry, before any card; see the class KDoc.
        val args = when (val values = valuesOf(proposed)) {
            is Values.Read -> values.args
            is Values.NotRead -> return StepBinding.Refused(values.binding)
        }
        // Amendment D1: the validator labels an app mention the user did not type as the model's.
        val modelWritten = proposed.origins[AuthoredToolDescriptions.MENTION_ARG] == ValueOrigin.ModelGenerated
        return bindMention(index, proposed, args, modelWritten)
    }

    /**
     * One step of a remainder at plan position [index], bound again (controller rulings E11-P1, E11-P4): the
     * step is read back into the shape the model's step had before [bind] — the user's words under the
     * mention, no package — and goes through [bindMention], the one mention branch; a step that kept no
     * words for its app has its package checked against the installed list.
     */
    private suspend fun rebindStep(index: Int, planned: PlanStep): StepBinding {
        val tool = planned.invocation.id
        val effect = registry.find(tool)?.effect ?: return StepBinding.Refused(Binding.Unavailable)
        val literals = planned.invocation.args.mapNotNull { (name, source) -> (source as? ArgSource.Literal)?.let { name to it.value } }.toMap()
        val refs = planned.invocation.args.mapNotNull { (name, source) -> (source as? ArgSource.FromStep)?.let { name to StepRef(it.stepIndex, it.key) } }.toMap()
        fun asProposed(args: Map<String, String>) = ProposedStep(
            tool = tool,
            args = args,
            // E11-P1: every literal is the model's until the mention branch says otherwise (the app: ours;
            // its words: the user's) — a finished step's recorded value included.
            origins = args.mapValues { ValueOrigin.ModelGenerated },
            risk = planned.risk,
            effect = effect,
            refs = refs,
        )
        val kept = asProposed(literals)
        val (appArg, sorted) = sorts.appArgOf(tool) ?: return step(index, kept, literals, kept.origins, choice = null)
        val label = sorted.labelArg
        val words = label?.let { literals[it] }
        if (label != null && words != null) {
            val again = asProposed(literals - appArg - label + (AuthoredToolDescriptions.MENTION_ARG to words))
            // The kept words are read as the user's (E11-P4), as before amendment D1: the plan on disk keeps no origin.
            return bindMention(index, again, again.args, modelWritten = false)
        }
        val packageName = literals[appArg] ?: return step(index, kept, literals, kept.origins, choice = null)
        return when (appTargets.stillInstalled(packageName)) {
            true -> step(index, kept, literals, kept.origins + (appArg to ValueOrigin.Ours), choice = null)
            false -> StepBinding.Refused(Binding.NotFound(packageName))
            null -> StepBinding.Refused(Binding.Unavailable)
        }
    }

    /**
     * The mention branch — the ONE place an app mention becomes a package, a pick, «not found» or
     * «unavailable», for [bind] and [rebind] alike: [args] are the step's model-facing arguments, its app
     * (if it has one) under [AuthoredToolDescriptions.MENTION_ARG] — in the user's words, or, when
     * [modelWritten] (amendment D1), in the MODEL's: then only an exact label or alias answers
     * ([AppTargetResolver.exactly] — no word forms of the model's word), and the words the tool shows are
     * marked the model's.
     */
    private suspend fun bindMention(index: Int, proposed: ProposedStep, args: Map<String, String>, modelWritten: Boolean): StepBinding {
        val sort = sorts.appArgOf(proposed.tool)
        val mention = args[AuthoredToolDescriptions.MENTION_ARG]
        if (sort == null || mention == null) {
            return step(index, proposed, args, proposed.origins, choice = null)
        }
        val (appArg, sorted) = sort
        val words = (args - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sorted.labelArg?.let { it to mention })
        val said = if (modelWritten) ValueOrigin.ModelGenerated else ValueOrigin.SaidBy(PrincipalId.USER)
        val wordOrigins = (proposed.origins - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sorted.labelArg?.let { it to said })
        // Controller ruling T2-R2 (A4′ acceptance tail, task 4): the model's words for the app are the words NAMING the
        // step's app — bound here to a package (or picked by the user), never sent anywhere — so they are marked as the
        // step's app words, which `needsCard()` and the card's model values do not count. The user's own words need no
        // mark: they are theirs, and never counted.
        val appWords = sorted.labelArg?.takeIf { modelWritten }

        // Owner decision 2026-11-17: an irreversible tool binds only a name exact as typed (`forIrreversible`);
        // a tool the registry does not know is treated as irreversible — the stricter reading. A name the model
        // wrote was not typed at all, so for such a tool it is a pick even when it is exact (`exactly`).
        val irreversible = registry.find(proposed.tool)?.durability.isIrreversible()
        val candidates = if (modelWritten) appTargets.exactly(mention) else appTargets.candidates(mention)
        return when (val found = candidates.forIrreversible(irreversible)) {
            AppCandidates.None -> StepBinding.Refused(Binding.NotFound(mention))
            AppCandidates.Unavailable -> StepBinding.Refused(Binding.Unavailable)
            is AppCandidates.One ->
                step(index, proposed, words + (appArg to found.app.packageName), wordOrigins + (appArg to ValueOrigin.Ours), choice = null, appWords = appWords)
            is AppCandidates.Several -> choice(index, proposed, appArg, mention, found.apps, words, wordOrigins, confirm = false, appWords = appWords)
            is AppCandidates.Confirm -> choice(index, proposed, appArg, mention, found.apps, words, wordOrigins, confirm = true, appWords = appWords)
        }
    }

    /** Several apps, or one word form to confirm ([confirm]): the user's pick, unless a learned launch choice answers. */
    private suspend fun choice(
        index: Int,
        proposed: ProposedStep,
        appArg: String,
        mention: String,
        apps: List<InstalledApp>,
        words: Map<String, String>,
        origins: Map<String, ValueOrigin>,
        confirm: Boolean,
        appWords: String?,
    ): StepBinding.Bound {
        if (proposed.tool != ToolIds.LAUNCH_APP) {
            return step(index, proposed, words, origins, AppChoice(appArg, mention, apps, learning = null, confirm = confirm), appWords)
        }
        val candidates = CandidateSet(apps.map { ResolvedTarget.App(it.packageName) })
        val key = LearnedChoiceKeys.launch(mention, folding)
        val token = ResolutionLearningToken(
            capabilityKey = key,
            context = ResolutionContext.None,
            candidateSet = candidates,
            fingerprint = fingerprintOf(candidates),
            isAppAmbiguityFlow = true,
        )
        val preference = (preferences.find(key, ResolutionContext.None) as? OperationResult.Success)?.value
        return when (val decision = policy.decide(preference, candidates, proposed.risk)) {
            is ResolutionDecision.AutoResolve -> {
                val target = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                step(index, proposed, words + (appArg to target), origins + (appArg to ValueOrigin.Ours), choice = null, appWords = appWords)
            }
            is ResolutionDecision.RankFirst -> {
                val first = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                step(index, proposed, words, origins, AppChoice(appArg, mention, apps.sortedByDescending { it.packageName == first }, token, confirm), appWords)
            }
            ResolutionDecision.NoPreference -> step(index, proposed, words, origins, AppChoice(appArg, mention, apps, token, confirm), appWords)
            is ResolutionDecision.Stale -> {
                preferences.delete(key, ResolutionContext.None) // best-effort prune, as FastPath's decorator does
                step(index, proposed, words, origins, AppChoice(appArg, mention, apps, token, confirm), appWords)
            }
        }
    }

    /**
     * Each argument of the proposed tool that carries a VALUE sort, read through that sort's registered
     * [SortResolver] (A4′ phase 3b, Task 6; fork 3b-C, B-5). The model writes these under the TOOL's own
     * argument names (`AuthoredToolDescriptions`: `duration`, `time`, `url`), so they are read where they
     * stand. The app sort is skipped — the mention has its own binding, with learned choices, below — and
     * so is a value the model left absent or blank: nothing to read — a skip that is unreachable at the
     * bridge today, because `BridgeValidator` trims every value, drops a blank optional one and rejects a
     * blank required one as `SHAPE` before this runs.
     *
     * [SortResolution.Resolved] replaces the words with the sort's value: the words themselves for a
     * duration or a clock time, the normalised address for a URL (scheme lower-cased, the rest kept), so
     * the card shows what will run. Unreadable words are [Binding.NotUnderstood] carrying exactly what
     * reached this class for that argument — the model's value as `BridgeValidator` passed it, i.e.
     * already trimmed, otherwise unchanged — the user is told, the model is not (this class has no port
     * to it); so is [SortResolution.NotFound], which no value sort produces today and whose app-only answer
     * [Binding.NotFound] would be wrong here. A sort with no resolver registered, the device unable to ask
     * ([SortResolution.Unavailable]), or a [SortResolution.Choice] — which [AppChoice] (`InstalledApp`)
     * cannot carry, and no value sort produces — fail closed as [Binding.Unavailable]: a generic failure,
     * never the model's words passed on unread. Origins are unchanged: a normalised address keeps the
     * origin the model's words had (only its scheme's case differs).
     */
    private suspend fun valuesOf(proposed: ProposedStep): Values {
        var read = proposed.args
        for ((name, sorted) in sorts.all()[proposed.tool].orEmpty()) {
            if (sorted.sort == ArgSorts.APP) continue
            val words = read[name]
            if (words.isNullOrBlank()) continue
            when (val resolution = resolvers[sorted.sort]?.resolve(words) ?: SortResolution.Unavailable) {
                is SortResolution.Resolved -> read = read + (name to resolution.value)
                SortResolution.Unreadable, SortResolution.NotFound -> return Values.NotRead(Binding.NotUnderstood(words))
                SortResolution.Unavailable, is SortResolution.Choice -> return Values.NotRead(Binding.Unavailable)
            }
        }
        return Values.Read(read)
    }

    /** [valuesOf]'s answer: every value read, or the binding for the first one that was not. */
    private sealed interface Values {
        data class Read(val args: Map<String, String>) : Values
        data class NotRead(val binding: Binding) : Values
    }

    /**
     * [bindStep]'s answer: the step bound (a pick may still be owed on it), or the reading's answer for all of it.
     * [appWords] — the step's label argument when the MODEL wrote the app's name (ruling T2-R2), else `null`.
     */
    private sealed interface StepBinding {
        data class Bound(
            val step: PlanStep,
            val origins: Map<String, ValueOrigin>,
            val effect: ToolEffect,
            val choice: AppChoice?,
            val appWords: String? = null,
        ) : StepBinding
        data class Refused(val binding: Binding) : StepBinding
    }

    /** The plan's step through the domain's one conversion ([toPlanStep], controller ruling E9-P3). */
    private fun step(
        index: Int,
        proposed: ProposedStep,
        words: Map<String, String>,
        origins: Map<String, ValueOrigin>,
        choice: AppChoice?,
        appWords: String? = null,
    ) = StepBinding.Bound(proposed.toPlanStep(index, words), origins, proposed.effect, choice, appWords)
}
