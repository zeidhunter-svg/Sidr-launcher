package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PendingChoice
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertNotNull

/**
 * The learned-plan tests' shared fixtures (A4′ phase 4, Tasks 3 and 4), so the replay's own tests
 * (`LearnedPlanReplayTest`) and the route's (`RouteCommandUseCaseTest`, step 2c) learn, pin and bind by one rule —
 * extracted from Task 3's test, not forked. The registry and descriptions are [BridgeFixtures.learnedRegistry] and
 * [BridgeFixtures.learnedDescriptions]. Every app is invented (R14-43).
 */
internal object LearnedPlanFixtures {

    val USER: ValueOrigin = ValueOrigin.SaidBy(PrincipalId.USER)

    /** A step the model proposed, every argument the user's own words — what `learn` turns into slots. */
    fun said(tool: ToolId, vararg args: Pair<String, String>): ProposedStep = proposed(tool, USER, *args)

    /** A step the model proposed, every argument the model's own text — what `learn` keeps as a constant. */
    fun modelText(tool: ToolId, vararg args: Pair<String, String>): ProposedStep = proposed(tool, ValueOrigin.ModelGenerated, *args)

    private fun proposed(tool: ToolId, origin: ValueOrigin, vararg args: Pair<String, String>) = ProposedStep(
        tool = tool,
        args = mapOf(*args),
        origins = args.associate { (name, _) -> name to origin },
        risk = BridgeFixtures.learnedRegistry.find(tool)!!.risk,
        effect = ToolEffect.EXTERNAL,
    )

    /** Learns [command]'s reading as a confirmed reading would teach it, and stores it with [streak]. */
    suspend fun LearnedPlanStore.pin(command: String, vararg steps: ProposedStep, streak: Int = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD) {
        val shape = LearnedForms.learn(command, steps.toList(), PlainTextFolding)
        assertNotNull("«$command» was expected to be learned", shape)
        upsert(LearnedPlan(shape!!, streak = streak, confirmations = streak, confirmedAtEpochMs = 0L))
    }

    /**
     * The fake binder's rule — `DeviceMentionBinder`'s mention branch reduced to the fixtures' one app-mention tool,
     * `uninstall_app`: the mention under `app` is looked up through [appsNamed] (exactly as typed); one app binds its
     * package into `app` (origin [ValueOrigin.Ours]), several owe a pick on `app` ([AppChoice], no learning token —
     * `uninstall_app`'s pick carries none), none is [Binding.NotFound] for the whole reading. The user's words go
     * into `app_label`, as the device writes them. A step with no `app` is bound as the model wrote it.
     */
    fun bindApps(goal: AgentGoal, steps: List<ProposedStep>, appsNamed: (String) -> List<InstalledApp>): Binding {
        val planSteps = mutableListOf<PlanStep>()
        val origins = mutableMapOf<Int, Map<String, ValueOrigin>>()
        val choices = mutableListOf<PendingChoice>()
        for ((index, step) in steps.withIndex()) {
            val mention = step.args["app"]
            if (mention == null) {
                planSteps += step.toPlanStep(index, step.args)
                origins[index] = step.origins
                continue
            }
            val words = step.args - "app" + ("app_label" to mention)
            val wordOrigins = step.origins - "app" + ("app_label" to USER)
            val apps = appsNamed(mention)
            when (apps.size) {
                0 -> return Binding.NotFound(mention)
                1 -> {
                    planSteps += step.toPlanStep(index, words + ("app" to apps.single().packageName))
                    origins[index] = wordOrigins + ("app" to ValueOrigin.Ours)
                }
                else -> {
                    planSteps += step.toPlanStep(index, words)
                    origins[index] = wordOrigins
                    choices += PendingChoice(index, AppChoice("app", mention, apps, learning = null))
                }
            }
        }
        return Binding.Bound(
            Interpretation.Act(
                goal = goal,
                steps = planSteps,
                origins = origins,
                effects = steps.withIndex().associate { (index, step) -> index to step.effect },
                choices = choices,
            ),
        )
    }
}
