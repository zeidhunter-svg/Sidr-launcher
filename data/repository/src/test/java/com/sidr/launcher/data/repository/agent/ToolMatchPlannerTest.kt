package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.PlanningRequest
import com.sidr.launcher.domain.agent.PlanningResult
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.agent.UnresolvedTarget
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.sort.SortedArg
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
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
     * Every test that uses this planner names a tool with no `app` argument and no APP row in the
     * production [CatalogArgumentSorts] (`set_timer` and `set_alarm` carry DURATION and CLOCK_TIME rows
     * since phase 3b, which `appArgOf` does not answer for), so `appArgOf` returns `null` and resolution
     * must never be consulted at all — a decline here would turn those plans into `Unresolved` (since
     * A4′ phase 3b; `NoPlan` before it) and say so loudly.
     */
    private val planner =
        ToolMatchPlanner(ToolSelector(ToolVocabulary(), namesOf()), appTargetsOf(), CatalogArgumentSorts())

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
        val result = planner.plan(PlanningRequest(free("set a timer for 10 minutes")), registry)

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
        val result = planner.plan(PlanningRequest(free("system settings")), registry)

        val plan = (result as PlanningResult.Planned).plan
        assertEquals(1, plan.steps.size)
        assertEquals(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, plan.steps[0].invocation.id)
        assertEquals(emptyMap<String, ArgSource>(), plan.steps[0].invocation.args)
    }

    @Test
    fun `an unmatched goal is NoPlan, so routing falls through exactly as before`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(free("what is the weather")), registry))
    }

    @Test
    fun `a tool the registry does not have is NoPlan even when the words match`() = runTest {
        assertEquals(
            PlanningResult.NoPlan,
            planner.plan(PlanningRequest(free("set a timer for 10 minutes")), FakeToolRegistry(emptyList())),
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
        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(free("set a timer")), registry))
    }

    /**
     * The *argument* refusal. "set a timer for" triggers the entry exactly and leaves nothing after it,
     * so the vocabulary declines rather than reporting a blank duration: an agent that silently starts
     * a zero-length timer is worse than one that declines. Same reasoning as
     * `InvocationValidator.resolve`'s `UNRESOLVED_ARG_SOURCE`.
     */
    @Test
    fun `a trigger with nothing after it is NoPlan, never a blank argument`() = runTest {
        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(free("set a timer for")), registry))
        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(free("  set a timer for   ")), registry))
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
            planner.plan(PlanningRequest(free("set a timer for 10 minutes")), renamed),
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

        val result = planner.plan(PlanningRequest(free("set a timer for 10 minutes")), argless)

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

        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(goal), registry))
    }

    // ---------------------------------------------------------------------------------------------
    // Task 6b — an `app` argument is resolved HERE, above the consent checkpoint.
    //
    // The fixtures below are deliberately **synthetic and test-local**. `uninstall_app` is Task 7's
    // descriptor and its triggers are Task 10's, so neither is invented here; and nothing below
    // touches `ToolVocabulary.DEFAULT_ENTRIES`, because a production trigger is owner-visible and one
    // invented in a planner test could silently claim a verb FastPath already owns. What is under
    // test is the planner's treatment of an argument its tool DECLARES to carry an app — through a
    // [CatalogArgumentSorts] row ([appSorts]), since A4′ phase 0; before that, through the spelling
    // `app` alone, which is the convention the last two tests below pin as gone.
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

    /** The synthetic tool's own sort row — production data stays free of `test_app_tool`. */
    private val appSorts = CatalogArgumentSorts(
        mapOf(appTool to mapOf("app" to SortedArg(ArgSorts.APP, labelArg = "app_label"))),
    )

    /**
     * The shape R14-37 needs and [appSelector] cannot produce: a trigger that supplies NO `app`
     * (`argName = null`, so it matches only as the whole text). [appSelector] declares
     * `argName = "app"`, and `ToolVocabulary` refuses a bare trigger for such an entry — a goal
     * through it declines in the selector, before the resolution block the R14-37 test is about.
     * Measured 2026-09-25 on a copy of the tree: `appSelector.select("удали приложение")` is `null`;
     * this selector's `select("покажи приложение")` is `ToolMatch(test_app_tool, {})`, and the
     * pre-A4′ planner then returned `NoPlan` from `resolve("")` — R14-37 itself.
     */
    private val bareAppSelector = ToolSelector(
        ToolVocabulary(
            listOf(
                ToolVocabulary.Entry(
                    id = appTool,
                    prefixByLocale = mapOf("ru" to setOf("покажи приложение")),
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
     *
     * [optional] declares an `app` argument **not** required — the descriptor half of the R14-37
     * shape, which the helper's `required = name == "app"` could not express before A4′ phase 0.
     */
    private fun appToolDescriptor(vararg argNames: String, optional: Set<String> = emptySet()) =
        ToolDescriptor(
            id = appTool,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            argSchema = argNames.map { name ->
                ActionArg(name, required = name == "app" && name !in optional, description = "The app to act on")
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
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Telegram" to "org.telegram.messenger"), appSorts)

        val planned = planner.plan(
            PlanningRequest(free("удали приложение telegram")),
            registryWith(appToolDescriptor("app", "app_label")),
        )

        val args = (planned as PlanningResult.Planned).plan.steps.single().invocation.args
        assertEquals(ArgSource.Literal("org.telegram.messenger"), args["app"])
        assertEquals(ArgSource.Literal("telegram"), args["app_label"])
    }

    /**
     * The resolver **declines**, never "use the raw text". The decline has to land before a plan
     * exists: a plan is what a consent card is drawn from, so a plan built on an unresolved name would
     * ask the user to confirm an act whose target is not yet known. Since A4′ phase 3b (R14-39 closed)
     * the decline is [PlanningResult.Unresolved] with no candidates — answered on the device by step
     * 2b — where it used to be `NoPlan`, which fell through to the cloud model. Still no plan, so still
     * no consent card.
     *
     * The second half is the **non-vacuity** check, and it is here rather than in a comment: the very
     * same goal text, vocabulary, descriptor and registry *do* produce a plan once the resolver knows
     * the name. So this test cannot pass because nothing was recognised — the only difference between
     * its two halves is whether resolution succeeded.
     */
    @Test
    fun `an unresolvable app name is unresolved, never a plan, so nothing is ever consented to`() = runTest {
        val registry = registryWith(appToolDescriptor("app", "app_label"))

        val declined = ToolMatchPlanner(appSelector, appTargetsOf(), appSorts)
            .plan(PlanningRequest(free("удали приложение нечто")), registry)
        assertTrue("an unresolved name is answered on the device, not planned: $declined", declined is PlanningResult.Unresolved)
        assertEquals(emptyList<InstalledApp>(), (declined as PlanningResult.Unresolved).target.candidates)

        assertTrue(
            ToolMatchPlanner(appSelector, appTargetsOf("Нечто" to "com.example.nechto"), appSorts)
                .plan(PlanningRequest(free("удали приложение нечто")), registry) is PlanningResult.Planned,
        )
    }

    /** `app_label` is opt-in: a descriptor that does not declare it is not handed one. */
    @Test
    fun `a descriptor that declares only app gets the package and no label`() = runTest {
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Telegram" to "org.telegram.messenger"), appSorts)

        val planned = planner.plan(PlanningRequest(free("удали приложение telegram")), registryWith(appToolDescriptor("app")))

        val args = (planned as PlanningResult.Planned).plan.steps.single().invocation.args
        assertEquals(ArgSource.Literal("org.telegram.messenger"), args["app"])
        assertNull(args["app_label"])
    }

    /**
     * Resolution is keyed on a declared APP row: a tool with no APP row — `set_timer`'s only row in the
     * production [CatalogArgumentSorts] is its DURATION row, so `appArgOf` returns `null` — never reaches
     * the resolver, which is why the class-level `planner` can carry a resolver that declines everything
     * and still plan a timer.
     */
    @Test
    fun `a tool with no app argument is untouched by resolution`() = runTest {
        val planned = planner.plan(PlanningRequest(free("set a timer for 10 minutes")), registry)

        assertEquals(
            ArgSource.Literal("10 minutes"),
            (planned as PlanningResult.Planned).plan.steps.single().invocation.args["duration"],
        )
    }

    /**
     * **R14-37, closed for the shape that produced it.** The old planner keyed on the argument being
     * NAMED `app`, never on whether it was `required`: a descriptor declaring an OPTIONAL `app`
     * entered the branch anyway, `match.args["app"]` was missing, `raw` became `""`,
     * `resolve("")` returned `null` — the resolver's considered refusal — and the elvis returned
     * `NoPlan` for EVERY goal, forever, with the whole suite green. A tool registered, reachable by
     * its trigger, and dead.
     *
     * [bareAppSelector], not [appSelector]: this needs a trigger that supplies no `app` at all, and
     * [appSelector]'s cannot match without one.
     *
     * Two per-descriptor pins were shipped against the opposite direction (`uninstall_app` R14-35,
     * `open_app_info` phase 3b) and neither generalised. This is the generalisation.
     */
    @Test
    fun `an optional app argument the vocabulary did not supply does not kill the plan`() = runTest {
        val descriptor = appToolDescriptor("app", optional = setOf("app"))
        val planner = ToolMatchPlanner(bareAppSelector, appTargetsOf(), appSorts)

        val result = planner.plan(PlanningRequest(free("покажи приложение")), registryWith(descriptor))

        assertTrue("an absent OPTIONAL argument is not a reason to decline", result is PlanningResult.Planned)
        assertEquals(
            "absent means absent — not an empty string, not a guessed package",
            emptyMap<String, ArgSource>(),
            (result as PlanningResult.Planned).plan.steps.single().invocation.args,
        )
    }

    /**
     * The literal is gone: an argument named `app` on a tool with **no row** is passed through
     * untouched rather than resolved. Resolution is now something a tool DECLARES, not something its
     * spelling earns.
     */
    @Test
    fun `an app argument with no declared sort is not resolved`() = runTest {
        val planner = ToolMatchPlanner(
            appSelector,
            appTargetsOf("Telegram" to "org.telegram.messenger"),
            CatalogArgumentSorts(),
        )

        val result = planner.plan(
            PlanningRequest(free("удали приложение telegram")),
            registryWith(appToolDescriptor("app")),
        )

        val step = (result as PlanningResult.Planned).plan.steps.single()
        assertEquals(ArgSource.Literal("telegram"), step.invocation.args["app"])
    }

    // ---------------------------------------------------------------------------------------------
    // A4′ phase 3b, Task 5 — R14-39 closed. Once the vocabulary has claimed a target, the planner
    // answers it on the device: `Unresolved` (nothing found, several, a form or a window to confirm,
    // or a list that could not be read), never `NoPlan`, which step 2b hands to the cloud model. Only
    // a second goal in the target's words still goes to the model (B-15).
    //
    // Fixtures (R14-43): «Миравин», «Lumora», the two «Tessar», and «вельтрикс» — a word no fixture
    // labels. Every assertion is an equality over the whole answer.
    // ---------------------------------------------------------------------------------------------

    private val removeDescriptor = appToolDescriptor("app", "app_label")

    /** What the planner hands step 2b for [text]: the step without its target, the words typed for it. */
    private fun claimed(
        text: String,
        words: String,
        candidates: List<InstalledApp>,
        confirm: Boolean,
        unreadable: Boolean = false,
    ) = PlanningResult.Unresolved(
        UnresolvedTarget(
            goal = free(text),
            pending = PlanStep(
                index = 0,
                invocation = ToolInvocation(appTool, emptyMap()),
                risk = removeDescriptor.risk,
                precondition = StepPrecondition.None,
                rationale = StepRationale.GOAL_DIRECT,
            ),
            arg = "app",
            labelArg = "app_label",
            sort = ArgSorts.APP,
            words = words,
            candidates = candidates,
            confirm = confirm,
            unreadable = unreadable,
        ),
    )

    /**
     * The first assertion is the premise: the vocabulary claims the text — so `NoPlan` here would be
     * the R14-39 fall-through, not "nothing matched".
     */
    @Test
    fun `a claimed target that resolves to nothing is unresolved, not NoPlan`() = runTest {
        val text = "удали приложение вельтрикс"
        assertEquals(appTool, appSelector.select(text)?.id)

        val result = ToolMatchPlanner(appSelector, appTargetsOf("Lumora" to "net.fixture.lumora"), appSorts)
            .plan(PlanningRequest(free(text)), registryWith(removeDescriptor))

        assertEquals(claimed(text, "вельтрикс", emptyList(), confirm = false), result)
    }

    /**
     * Fork 3b-E as refined in plan §0.1: «a window equal to the whole slot → plan; a window shorter
     * than the slot → confirm». The extra word may be politeness or may name another app (phase 1's
     * F1-2: «lumora studio»), so a target found inside extra words is OFFERED — never bound, least of
     * all for an irreversible act. The control is the same name typed alone, which plans.
     */
    @Test
    fun `a target inside extra words is offered by windows, to confirm`() = runTest {
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Миравин" to "net.fixture.miravin"), appSorts)
        val text = "удали приложение миравин пожалуйста"

        val result = planner.plan(PlanningRequest(free(text)), registryWith(removeDescriptor))

        assertEquals(
            claimed(text, "миравин пожалуйста", listOf(InstalledApp("net.fixture.miravin", "Миравин")), confirm = true),
            result,
        )
        val whole = planner.plan(PlanningRequest(free("удали приложение миравин")), registryWith(removeDescriptor))
        assertEquals(
            ArgSource.Literal("net.fixture.miravin"),
            (whole as PlanningResult.Planned).plan.steps.single().invocation.args["app"],
        )
    }

    /** Controller ruling E5-P3: two exact answers are the user's choice — `Several`, never a guess, never `NoPlan`. */
    @Test
    fun `two candidates are an unresolved target with both`() = runTest {
        val tessars = listOf(InstalledApp("net.fixture.tessar.one", "Tessar"), InstalledApp("net.fixture.tessar.two", "Tessar"))
        val planner = ToolMatchPlanner(
            appSelector,
            appTargetsOf("Tessar" to "net.fixture.tessar.one", "Tessar" to "net.fixture.tessar.two"),
            appSorts,
        )
        val text = "удали приложение tessar"

        val result = planner.plan(PlanningRequest(free(text)), registryWith(removeDescriptor))

        assertEquals(claimed(text, "tessar", tessars, confirm = false), result)
    }

    /**
     * B-15: phase 1's `SecondGoal` check runs on the target's words BEFORE the windows. Without it the
     * window «lumora» would be offered and «проверь почту» silently dropped; the model, which plans
     * several steps, gets the command instead. The control — the same name with words that carry no
     * second goal — is what proves the windows would have found it.
     */
    @Test
    fun `a second goal in the slot goes to the model, not to windows`() = runTest {
        val planner = ToolMatchPlanner(appSelector, appTargetsOf("Lumora" to "net.fixture.lumora"), appSorts)
        val text = "удали приложение lumora и проверь почту"
        assertEquals(appTool, appSelector.select(text)?.id)

        assertEquals(PlanningResult.NoPlan, planner.plan(PlanningRequest(free(text)), registryWith(removeDescriptor)))

        val polite = "удали приложение lumora пожалуйста"
        assertEquals(
            claimed(polite, "lumora пожалуйста", listOf(InstalledApp("net.fixture.lumora", "Lumora")), confirm = true),
            planner.plan(PlanningRequest(free(polite)), registryWith(removeDescriptor)),
        )
    }

    /**
     * Controller ruling E5-P1: the app list could not be read. The device does not know whether the app
     * is there, so this is neither "not found" nor a reason to ask the model — it is an `Unresolved`
     * marked unreadable, which step 2b answers as a failure.
     */
    @Test
    fun `an unreadable app list is an unreadable unresolved target, never NoPlan`() = runTest {
        val unreadable = AppTargetResolver(
            apps = FakeInstalledAppsRepository().apply { errorToReturn = OperationError.UnknownError("unreadable") },
            aliases = FakeAliasStore(),
            folding = PlainTextFolding,
        )
        val text = "удали приложение lumora"

        val result = ToolMatchPlanner(appSelector, unreadable, appSorts).plan(PlanningRequest(free(text)), registryWith(removeDescriptor))

        assertEquals(claimed(text, "lumora", emptyList(), confirm = false, unreadable = true), result)
    }
}
