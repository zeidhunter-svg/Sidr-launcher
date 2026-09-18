package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The planner never invokes anything; the federation just needs a worker to be constructible. */
private object NoopWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = ToolResult.Effected()
}

/**
 * A1' Task 8. **One generic arm, not a taxonomy** — every test here is about a tool the planner has
 * never heard of by name, reached through the vocabulary and the registry alone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ToolMatchPlannerTest {

    /**
     * Task 2 made `Tier0IntentToolSource` filter by held permission, so every construction of it now
     * states which presence it is read under. Grant-everything because **the subject here is not the
     * filter** — these tests plan and execute specific goals, and the permission gate is only
     * scenery they must not trip over. The filter's own tests are `Tier0IntentToolSourceTest`'s.
     */
    private val grantsEverything = PermissionPresence { true }

    /**
     * `appTargetsOf()` is a resolver over an **empty** installed list, so it declines every query.
     * Every test that uses this planner names a tool with no `app` argument, so resolution must never
     * be consulted at all — a decline here would turn those plans into `NoPlan` and say so loudly.
     */
    private val planner = ToolMatchPlanner(ToolSelector(ToolVocabulary(), namesOf()), appTargetsOf())

    /** The production shape: the Tier-0 source seen through the federation, not directly. */
    private val registry = ToolFederation(
        listOf(
            ToolAdapter(
                ToolLevels.SYSTEM_INTENT,
                Tier0IntentToolSource(ToolPermissionCatalog(), grantsEverything),
                NoopWorker,
            ),
        ),
    ).registry

    private fun free(text: String) = AgentGoal(text, GoalShape.Free(text))

    private fun registryWith(vararg descriptors: ToolDescriptor) = FakeToolRegistry(descriptors.toList())

    @Test
    fun `a matched tool becomes a one-step plan whose risk comes from the registry`() = runTest {
        val result = planner.plan(free("set a timer for 10 minutes"), registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(1, plan.steps.size)
        assertEquals(Tier0ToolIds.SET_TIMER, plan.steps[0].invocation.id)
        assertEquals(ArgSource.Literal("10 minutes"), plan.steps[0].invocation.args["duration"])
        assertEquals(ActionRiskLevel.SAFE, plan.steps[0].risk)
        assertEquals(StepRationale.GOAL_DIRECT, plan.steps[0].rationale)
        assertEquals(StepPrecondition.None, plan.steps[0].precondition)
    }

    @Test
    fun `a zero-argument tool plans a step that carries no arguments`() = runTest {
        val result = planner.plan(free("system settings"), registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(1, plan.steps.size)
        assertEquals(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, plan.steps[0].invocation.id)
        assertEquals(emptyMap<String, ArgSource>(), plan.steps[0].invocation.args)
    }

    @Test
    fun `an unmatched goal is NoPlan, so routing falls through exactly as before`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(free("what is the weather"), registry))
    }

    @Test
    fun `a tool the registry does not have is NoPlan even when the words match`() = runTest {
        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(free("set a timer for 10 minutes"), FakeToolRegistry(emptyList())),
        )
    }

    /**
     * The *recognition* refusal. "set a timer" completes no trigger — the `en` forms are
     * "set a timer for" / "set timer for" / "timer for" and none of them is a prefix of it — so nothing
     * is recognised and the planner never reaches the argument question. Named for what it actually
     * exercises: the same input was in this task's brief under the argument-refusal name, where it
     * would have passed for the wrong reason.
     */
    @Test
    fun `words that complete no trigger are not recognised at all`() = runTest {
        assertEquals(null, ToolVocabulary().match("set a timer"))
        assertEquals(PlanningResult.NoPlan, planner.plan(free("set a timer"), registry))
    }

    /**
     * The *argument* refusal. "set a timer for" triggers the entry exactly and leaves nothing after it,
     * so the vocabulary declines rather than reporting a blank duration: an agent that silently starts
     * a zero-length timer is worse than one that declines. Same reasoning as
     * `InvocationValidator.resolve`'s `UNRESOLVED_ARG_SOURCE`.
     */
    @Test
    fun `a trigger with nothing after it is NoPlan, never a blank argument`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(free("set a timer for"), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(free("  set a timer for   "), registry))
    }

    /**
     * The planner's own required-argument check, which the vocabulary cannot reach past: it fires when
     * the vocabulary and the tool disagree about the argument's *name*. Nothing pins those two together
     * today, so this is the branch that keeps a rename from producing a call with a missing argument.
     */
    @Test
    fun `a tool that renamed its required argument is NoPlan, not a call missing it`() = runTest {
        val renamed = registryWith(
            ToolDescriptor(
                id = Tier0ToolIds.SET_TIMER,
                level = ToolLevels.SYSTEM_INTENT,
                effect = ToolEffect.EXTERNAL,
                argSchema = listOf(ActionArg("length", description = "How long")),
                risk = ActionRiskLevel.SAFE,
                durability = ToolDurability.TRANSIENT,
            ),
        )

        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(free("set a timer for 10 minutes"), renamed),
        )
    }

    /**
     * An argument the schema does not declare is dropped rather than passed:
     * `InvocationValidator.validate` rejects the whole invocation as `UNDECLARED_ARG`, and a plan that
     * cannot validate is worse than a plan without the argument.
     */
    @Test
    fun `an argument the schema does not declare is dropped rather than passed`() = runTest {
        val argless = registryWith(
            ToolDescriptor(
                id = Tier0ToolIds.SET_TIMER,
                level = ToolLevels.SYSTEM_INTENT,
                effect = ToolEffect.EXTERNAL,
                risk = ActionRiskLevel.SAFE,
                durability = ToolDurability.TRANSIENT,
            ),
        )

        val result = planner.plan(free("set a timer for 10 minutes"), argless)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(emptyMap<String, ArgSource>(), plan.steps[0].invocation.args)
    }

    /**
     * `AppNotInstalled` belongs to `TemplatePlanner`. Answering `NoPlan` rather than reading the text
     * anyway is what keeps the two planners disjoint, which is what makes `CompositePlanner`'s order a
     * tie-break rather than a priority.
     */
    @Test
    fun `the app-not-installed shape is not this planner's business`() = runTest {
        val goal = AgentGoal("set a timer for 10 minutes", GoalShape.AppNotInstalled("timer"))

        assertEquals(PlanningResult.NoPlan, planner.plan(goal, registry))
    }

    // ---------------------------------------------------------------------------------------------
    // Task 6b — an `app` argument is resolved HERE, above the consent checkpoint.
    //
    // The fixtures below are deliberately **synthetic and test-local**. `uninstall_app` is Task 7's
    // descriptor and its triggers are Task 10's, so neither is invented here; and nothing below
    // touches `ToolVocabulary.DEFAULT_ENTRIES`, because a production trigger is owner-visible and one
    // invented in a planner test could silently claim a verb FastPath already owns. What is under
    // test is the planner's treatment of an argument **named** `app` — the string convention itself.
    // ---------------------------------------------------------------------------------------------

    /** Synthetic on purpose: no production `ToolId` is invented by this task. */
    private val appTool = ToolId("test_app_tool")

    /** A vocabulary of exactly one entry, built through the `internal` constructor its KDoc offers. */
    private val appSelector = ToolSelector(
        ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = appTool,
                    prefixByLocale = mapOf("ru" to setOf("удали приложение")),
                    argName = "app",
                ),
            ),
        ),
        namesOf(),
    )

    /**
     * `app_label` is declared **not required**, and that is a property of the design rather than a
     * convenience: the vocabulary never supplies it — the planner binds it *after* resolution — while
     * the required-argument check runs *before* resolution, so a descriptor that marked it required
     * would decline every goal it was ever offered.
     */
    private fun appToolDescriptor(vararg argNames: String) = ToolDescriptor(
        id = appTool,
        level = ToolLevels.SYSTEM_INTENT,
        effect = ToolEffect.EXTERNAL,
        argSchema = argNames.map { name ->
            ActionArg(name, required = name == "app", description = "The app to act on")
        },
        risk = ActionRiskLevel.CONFIRM,
        durability = ToolDurability.TRANSIENT,
    )

    /**
     * The consent card is drawn from the **plan**, and `AgentExecutor` evaluates `checkpointFor`
     * before it ever reaches `toolExecutor.invoke` — so a package resolved in a worker is resolved
     * after the user has already said yes. Binding the package here is what makes the card name the
     * thing that will actually be acted on; the raw text survives as `app_label` so the card can also
     * show what the user said.
     */
    @Test
    fun `an app argument is bound as a package, with the raw text kept as the label`() = runTest {
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Telegram" to "org.telegram.messenger"))

        val planned = planner.plan(
            free("удали приложение telegram"),
            registryWith(appToolDescriptor("app", "app_label")),
        )

        val args = (planned as PlanningResult.Planned).plan.steps.single().invocation.args
        assertEquals(ArgSource.Literal("org.telegram.messenger"), args["app"])
        assertEquals(ArgSource.Literal("telegram"), args["app_label"])
    }

    /**
     * `AppTargetResolver.resolve` returns `null` to **decline**, never "use the raw text". The decline
     * has to land before a plan exists: a plan is what a consent card is drawn from, so a plan built
     * on an unresolved name would ask the user to confirm an act whose target is not yet known.
     *
     * The second half is the **non-vacuity** check, and it is here rather than in a comment: the very
     * same goal text, vocabulary, descriptor and registry *do* produce a plan once the resolver knows
     * the name. So this test cannot pass because nothing was recognised — the only difference between
     * its two halves is whether resolution succeeded.
     */
    @Test
    fun `an unresolvable app name yields NoPlan, so nothing is ever consented to`() = runTest {
        val registry = registryWith(appToolDescriptor("app", "app_label"))

        assertEquals(
            PlanningResult.NoPlan,
            ToolMatchPlanner(appSelector, appTargetsOf()).plan(free("удали приложение нечто"), registry),
        )

        assertTrue(
            ToolMatchPlanner(appSelector, appTargetsOf("Нечто" to "com.example.nechto"))
                .plan(free("удали приложение нечто"), registry) is PlanningResult.Planned,
        )
    }

    /** `app_label` is opt-in: a descriptor that does not declare it is not handed one. */
    @Test
    fun `a descriptor that declares only app gets the package and no label`() = runTest {
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Telegram" to "org.telegram.messenger"))

        val planned = planner.plan(free("удали приложение telegram"), registryWith(appToolDescriptor("app")))

        val args = (planned as PlanningResult.Planned).plan.steps.single().invocation.args
        assertEquals(ArgSource.Literal("org.telegram.messenger"), args["app"])
        assertNull(args["app_label"])
    }

    /**
     * The convention is keyed on the argument's **name**: a tool that declares no `app` never reaches
     * the resolver, which is why the class-level `planner` can carry a resolver that declines
     * everything and still plan a timer.
     */
    @Test
    fun `a tool with no app argument is untouched by resolution`() = runTest {
        val planned = planner.plan(free("set a timer for 10 minutes"), registry)

        assertEquals(
            ArgSource.Literal("10 minutes"),
            (planned as PlanningResult.Planned).plan.steps.single().invocation.args["duration"],
        )
    }
}
