package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionIds
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
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.LaunchSlotExtractor
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionDecision
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import javax.inject.Inject

/**
 * The bridge's [MentionBinder] on Android (spec §7.12.1, §7.12.3 condition 2; plan §0.2 B-3).
 *
 * For a tool with an app sort ([ToolArgumentSorts]) the model's word for the app
 * ([AuthoredToolDescriptions.MENTION_ARG]) leaves the model-facing name: the package goes into the
 * row's `arg` once known, the user's words into its `labelArg`. [AppTargetResolver.candidates] answers —
 * it never guesses and consults aliases; two candidates are a choice for the USER before any card.
 *
 * **Learned choices answer for `launch_app` only**, with FastPath's policy and the same key FORMULA — a
 * confident one binds without asking, a weak one is offered first. `uninstall_app` and `open_app_info`
 * never read them: consent learned for opening an app is not consent for removing it
 * (`AppTargetResolver`). The formula is FastPath's, the input is not: FastPath slots the whole command,
 * this binder the model's mention, and `LaunchSlotExtractor` strips English verbs only. So the two paths
 * share a learned choice only for an English verb-led command («open tessar» → `tessar` on both, the
 * model's mention being the user's word); a `ru`/`tr` command («открой tessar») keys FastPath on the
 * whole phrase, and a choice learned on one path is not seen by the other. That fails safe — the user
 * is asked again, nothing is guessed.
 *
 * Nothing here reaches the model: `RouteCommandUseCase` calls this after the model has answered, and
 * returns what it finds to the screen.
 */
class DeviceMentionBinder @Inject constructor(
    private val appTargets: AppTargetResolver,
    private val sorts: ToolArgumentSorts,
    private val preferences: ResolutionPreferenceStore,
    private val policy: ResolutionPreferencePolicy,
) : MentionBinder {

    override suspend fun bind(goal: AgentGoal, proposed: ProposedStep): Binding {
        val sort = sorts.appBindingFor(proposed.tool)
        val mention = proposed.args[AuthoredToolDescriptions.MENTION_ARG]
        if (sort == null || mention == null) {
            return Binding.Bound(act(goal, proposed, proposed.args, proposed.origins, choice = null))
        }
        val words = (proposed.args - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sort.labelArg?.let { it to mention })
        val wordOrigins = (proposed.origins - AuthoredToolDescriptions.MENTION_ARG) +
            listOfNotNull(sort.labelArg?.let { it to ValueOrigin.SaidBy(PrincipalId.USER) })

        return when (val found = appTargets.candidates(mention)) {
            AppCandidates.None -> Binding.NotFound(mention)
            AppCandidates.Unavailable -> Binding.Unavailable
            is AppCandidates.One -> Binding.Bound(
                act(goal, proposed, words + (sort.arg to found.app.packageName), wordOrigins + (sort.arg to ValueOrigin.Ours), choice = null),
            )
            is AppCandidates.Several -> Binding.Bound(several(goal, proposed, sort, mention, found.apps, words, wordOrigins))
        }
    }

    private suspend fun several(
        goal: AgentGoal,
        proposed: ProposedStep,
        sort: AppArgumentBinding,
        mention: String,
        apps: List<InstalledApp>,
        words: Map<String, String>,
        origins: Map<String, ValueOrigin>,
    ): Interpretation.Act {
        if (proposed.tool != ToolIds.LAUNCH_APP) {
            return act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, learning = null))
        }
        val candidates = CandidateSet(apps.map { ResolvedTarget.App(it.packageName) })
        val key = CapabilityKey(ActionIds.LAUNCH_APP, LaunchSlotExtractor.slotOf(CommandNormalizer.normalize(mention)))
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
                act(goal, proposed, words + (sort.arg to target), origins + (sort.arg to ValueOrigin.Ours), choice = null)
            }
            is ResolutionDecision.RankFirst -> {
                val first = when (val t = decision.target) { is ResolvedTarget.App -> t.packageName }
                act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps.sortedByDescending { it.packageName == first }, token))
            }
            ResolutionDecision.NoPreference -> act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))
            is ResolutionDecision.Stale -> {
                preferences.delete(key, ResolutionContext.None) // best-effort prune, as FastPath's decorator does
                act(goal, proposed, words, origins, AppChoice(sort.arg, mention, apps, token))
            }
        }
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
