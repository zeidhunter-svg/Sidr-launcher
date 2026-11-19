package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PendingChoice
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.toPlanStep
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.bindApps
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.pin
import com.sidr.launcher.domain.memory.plan.LearnedPlanFixtures.said
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 4, Task 3 (plan §0.2 B-4, B-5, B-7): a learned command is replayed as step 7 without the model —
 * its pinned form takes the command's words, the template is filled, and the filled answer goes through
 * **the same** `BridgeValidator` (against today's offering) and `MentionBinder` as the model's reading. Anything
 * but a bound reading is a miss, and a miss writes nothing.
 *
 * The registry and descriptions are the bridge fixtures' learned-plan set ([BridgeFixtures.learnedRegistry]);
 * the binder is the shared [FakeMentionBinder], scripted with [LearnedPlanFixtures.bindApps] (the rule the route's
 * step-2c tests share, Task 4) to bind «Lumora» and «Vemblic» to their fixture packages and nothing else — and to
 * forget one, as a phone forgets an app it removed. Fixtures (R14-43): every app is invented; assertions compare by
 * equality.
 */
class LearnedPlanReplayTest {

    private val folding = PlainTextFolding
    private val store = FakeLearnedPlanStore()

    /** What the fake binder can bind: a mention, exactly as the user typed it, to a package. */
    private val installed = mutableMapOf("Lumora" to LUMORA, "Vemblic" to VEMBLIC)
    private val binder = FakeMentionBinder(
        binding = { goal, steps -> bindApps(goal, steps) { mention -> listOfNotNull(installed[mention]?.let { InstalledApp(it, mention) }) } },
    )

    private fun plans(levels: Set<ToolLevel> = BridgeFilter.ANDROID_OFFERED_LEVELS) = DefaultLearnedPlans(
        store = store,
        registry = BridgeFixtures.learnedRegistry,
        descriptions = BridgeFixtures.learnedDescriptions,
        levels = levels,
        binder = binder,
        folding = folding,
    )

    @Test
    fun `a pinned form replays through the validator and the binder with the new words`() = runBlocking {
        store.pin("сотри lumora", said(UNINSTALL_APP, "app" to "lumora"))
        val plans = plans()

        val first = bound(plans.replay("сотри Lumora"))
        assertEquals(ArgSource.Literal(LUMORA), first.act.steps.single().invocation.args["app"])

        // The same instance, another word in the slot: bound again, never the act it bound before.
        val second = bound(plans.replay("сотри Vemblic"))
        assertEquals(ArgSource.Literal(VEMBLIC), second.act.steps.single().invocation.args["app"])
        assertEquals("сотри …", second.form.key)
        assertEquals(AgentGoal(text = "сотри Vemblic", shape = GoalShape.Free("сотри Vemblic")), second.act.goal)
        assertEquals(2, binder.bindCallCount)
    }

    @Test
    fun `a form below the threshold does not replay`() = runBlocking {
        store.pin("сотри lumora", said(UNINSTALL_APP, "app" to "lumora"), streak = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD - 1)

        assertEquals(Replay.Miss, plans().replay("сотри Vemblic"))
    }

    @Test
    fun `a tool no longer offered misses and the entry stays`() = runBlocking {
        store.pin("сотри lumora", said(UNINSTALL_APP, "app" to "lumora"))
        val before = store.rows

        // `uninstall_app` is `system_intent`: an offering of `in_app` alone no longer holds it.
        assertEquals(Replay.Miss, plans(levels = setOf(ToolLevels.IN_APP)).replay("сотри Vemblic"))
        assertEquals(before, store.rows)
    }

    @Test
    fun `a slot the binder cannot bind misses`() = runBlocking {
        store.pin("сотри lumora", said(UNINSTALL_APP, "app" to "lumora"))
        val plans = plans()

        // Review Focus 1: the words fit the form, but they name nothing the device can bind.
        assertEquals(Replay.Miss, plans.replay("сотри все фотографии"))

        // Review Focus 3: the app is gone — the same words, bound again, now find nothing.
        bound(plans.replay("сотри Vemblic"))
        installed.remove("Vemblic")
        assertEquals(Replay.Miss, plans.replay("сотри Vemblic"))
    }

    @Test
    fun `two pinned forms that fit the same command miss`() = runBlocking {
        store.pin("найди книгу в магазине", said(WEB_SEARCH, "query" to "книгу"), said(WEB_SEARCH, "query" to "магазине"))
        store.pin("найди книгу в центре", said(WEB_SEARCH, "query" to "центре"))
        assertEquals(setOf("найди … в …", "найди книгу в …"), store.rows.keys)

        assertEquals(Replay.Miss, plans().replay("найди книгу в магазине"))
    }

    @Test
    fun `an unreadable store misses`() = runBlocking {
        store.pin("сотри lumora", said(UNINSTALL_APP, "app" to "lumora"))
        store.failReads = true

        assertEquals(Replay.Miss, plans().replay("сотри Vemblic"))
    }

    @Test
    fun `a card is needed for an owed pick or model text going out, not for the user's words going out`() {
        val pickOwed = Interpretation.Act(
            goal = goal("сотри vemblic"),
            steps = listOf(planStep(UNINSTALL_APP, "app_label" to "vemblic")),
            origins = mapOf(0 to mapOf("app_label" to USER)),
            effects = mapOf(0 to ToolEffect.EXTERNAL),
            choices = listOf(
                PendingChoice(0, AppChoice("app", "vemblic", listOf(InstalledApp(VEMBLIC, "Vemblic")), learning = null, confirm = true)),
            ),
        )
        assertTrue(pickOwed.needsCard())

        assertTrue(search("поищи что-нибудь про орвелин", "орвелин история", ValueOrigin.ModelGenerated, ToolEffect.EXTERNAL).needsCard())
        assertFalse(search("поищи орвелин", "орвелин", USER, ToolEffect.EXTERNAL).needsCard())
        // Model text that stays on the phone is not «going out»: nothing for the card to show.
        assertFalse(search("поищи что-нибудь про орвелин", "орвелин история", ValueOrigin.ModelGenerated, ToolEffect.LOCAL).needsCard())
    }

    // --- fixtures ---------------------------------------------------------------------------------------

    private fun bound(replay: Replay): Replay.Bound {
        assertTrue("expected a bound replay, was $replay", replay is Replay.Bound)
        return replay as Replay.Bound
    }

    private fun goal(command: String) = AgentGoal(text = command, shape = GoalShape.Free(command))

    private fun search(command: String, query: String, origin: ValueOrigin, effect: ToolEffect): Interpretation.Act {
        val step = ProposedStep(WEB_SEARCH, mapOf("query" to query), mapOf("query" to origin), ActionRiskLevel.SAFE, effect)
        return Interpretation.Act(
            goal = goal(command),
            steps = listOf(step.toPlanStep(0, step.args)),
            origins = mapOf(0 to step.origins),
            effects = mapOf(0 to effect),
            choices = emptyList(),
        )
    }

    /** A bound step at position 0 whose arguments are [words], as literals. */
    private fun planStep(tool: ToolId, vararg words: Pair<String, String>): PlanStep {
        val step = ProposedStep(tool, mapOf(*words), emptyMap(), ActionRiskLevel.CONFIRM, ToolEffect.EXTERNAL)
        return step.toPlanStep(0, step.args)
    }

    private companion object {
        val UNINSTALL_APP = ToolId("uninstall_app")
        val WEB_SEARCH = ToolId("web_search")
        val USER = ValueOrigin.SaidBy(PrincipalId.USER)
        const val LUMORA = "net.fixture.lumora"
        const val VEMBLIC = "net.fixture.vemblic"
    }
}
