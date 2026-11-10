package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.MentionBinder
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
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
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import javax.inject.Inject

/**
 * The bridge's [MentionBinder] on Android (spec §7.12.1, §7.12.3 condition 2; plan §0.2 B-3).
 *
 * For a tool with an app-sorted argument ([CatalogArgumentSorts.appArgOf]) the model's word for the app
 * ([AuthoredToolDescriptions.MENTION_ARG]) leaves the model-facing name: the package goes into the
 * row's argument once known, the user's words into its `labelArg`. [AppTargetResolver.candidates] answers —
 * it never guesses and consults aliases; two candidates are a choice for the USER before any card, and a
 * word FORM of a name (phase 3b, fork 3b-G: «миравину» for «Миравин») is a pick of that one app marked
 * [AppChoice.confirm] — «Вы имели в виду…?» — never a bind.
 *
 * **Learned choices answer for `launch_app` only**, with FastPath's policy — a confident one binds
 * without asking, a weak one is offered first — and for a confirmation exactly as for a choice between
 * several (B-3): the same key, the same read, so a form confirmed three times binds without asking.
 * `uninstall_app` and `open_app_info` neither carry a token nor read one: consent learned for opening an
 * app is not consent for removing it (`AppTargetResolver`). The key is [LearnedChoiceKeys.launch] of the
 * model's mention (B-4) — the app name, folded. Until FastPath keys on the name too (phase 3b, Task 7),
 * a `ru`/`tr` command («открой tessar») keys FastPath on the whole phrase, and a choice learned on one
 * path is not seen by the other. That fails safe — the user is asked again, nothing is guessed.
 *
 * **Every VALUE the model wrote is read before the card, too** (phase 3b, Task 6; fork 3b-C, B-5; the
 * plan's Review Focus 5). Each argument [CatalogArgumentSorts] declares with a value sort — `set_timer`'s
 * duration, `set_alarm`'s clock time, `open_url`'s address — goes through the resolver the sort registry
 * (`Map<ArgSort, SortResolver>`, `SortProvidesModule` in `:app`) holds for it, before the app mention is
 * bound: `HTTPS://…` reaches the card as `https://…`, and «полчасика» or an address with a space in it
 * is [Binding.NotUnderstood] — said to the user, never a card that fails after «Выполнить». The app sort's
 * registry entry is not read here: the mention keeps its own binding above, with learned choices.
 * **A named limit (controller ruling E6-R2):** the words [Binding.NotUnderstood] carries are what the
 * MODEL wrote for the argument, and «Не понял «…».» quotes them with no model mark — they may be the
 * user's words or the model's own («1.5 hours»). Nothing runs and nothing leaves; a model-marked variant
 * would be a sixth string key, which is the owner's decision.
 *
 * Nothing here reaches the model: `RouteCommandUseCase` calls this after the model has answered, and
 * returns what it finds to the screen.
 */
class DeviceMentionBinder @Inject constructor(
    private val appTargets: AppTargetResolver,
    private val sorts: CatalogArgumentSorts,
    private val resolvers: Map<ArgSort, @JvmSuppressWildcards SortResolver>,
    private val preferences: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
    private val folding: TextFolding,
) : MentionBinder {

    override suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding {
        // Every VALUE the model wrote for a sorted argument is read on the device first (A4′ phase 3b,
        // Task 6) — through the sort registry, before any card; see the class KDoc.
        val args = when (val values = valuesOf(proposed)) {
            is Values.Read -> values.args
            is Values.NotRead -> return values.binding
        }
        val sort = sorts.appArgOf(proposed.tool)
        val mention = args[AuthoredToolDescriptions.MENTION_ARG]
        if (sort == null || mention == null) {
            return Binding.Bound(act(goal, proposed, args, proposed.origins, choice = null))
        }
        val (appArg, sorted) = sort
        val words = (args - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sorted.labelArg?.let { it to mention })
        val wordOrigins = (proposed.origins - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sorted.labelArg?.let { it to ValueOrigin.SaidBy(PrincipalId.USER) })

        return when (val found = appTargets.candidates(mention)) {
            AppCandidates.None -> Binding.NotFound(mention)
            AppCandidates.Unavailable -> Binding.Unavailable
            is AppCandidates.One -> Binding.Bound(
                act(goal, proposed, words + (appArg to found.app.packageName), wordOrigins + (appArg to ValueOrigin.Ours), choice = null),
            )
            is AppCandidates.Several -> Binding.Bound(choice(goal, proposed, appArg, mention, found.apps, words, wordOrigins, confirm = false))
            is AppCandidates.Confirm -> Binding.Bound(choice(goal, proposed, appArg, mention, found.apps, words, wordOrigins, confirm = true))
        }
    }

    /** Several apps, or one word form to confirm ([confirm]): the user's pick, unless a learned launch choice answers. */
    private suspend fun choice(
        goal: AgentGoal,
        proposed: ProposedStep,
        appArg: String,
        mention: String,
        apps: List<InstalledApp>,
        words: Map<String, String>,
        origins: Map<String, ValueOrigin>,
        confirm: Boolean,
    ): Interpretation.Act {
        if (proposed.tool != ToolIds.LAUNCH_APP) {
            return act(goal, proposed, words, origins, AppChoice(appArg, mention, apps, learning = null, confirm = confirm))
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
                act(goal, proposed, words + (appArg to target), origins + (appArg to ValueOrigin.Ours), choice = null)
            }
            is ResolutionDecision.RankFirst -> {
                val first = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                act(goal, proposed, words, origins, AppChoice(appArg, mention, apps.sortedByDescending { it.packageName == first }, token, confirm))
            }
            ResolutionDecision.NoPreference -> act(goal, proposed, words, origins, AppChoice(appArg, mention, apps, token, confirm))
            is ResolutionDecision.Stale -> {
                preferences.delete(key, ResolutionContext.None) // best-effort prune, as FastPath's decorator does
                act(goal, proposed, words, origins, AppChoice(appArg, mention, apps, token, confirm))
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

    private fun act(
        goal: AgentGoal,
        proposed: ProposedStep,
        args: Map<String, String>,
        origins: Map<String, ValueOrigin>,
        choice: AppChoice?,
    ) = Interpretation.Act(
        goal = goal,
        step = PlanStep(
            index = 0,
            invocation = ToolInvocation(proposed.tool, args.mapValues { (_, value) -> ArgSource.Literal(value) }),
            risk = proposed.risk,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        ),
        origins = origins,
        effect = proposed.effect,
        choice = choice,
    )
}
