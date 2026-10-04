package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.ProposedStep
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.choose
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Binding on the device (spec §7.12.1, §7.12.3 condition 2): the model's words for an app become a
 * package here, before any card, or the user is asked — never a guess, and never a report to the
 * model. Learned choices answer for `launch_app` only. Fixtures are invented (R14-43).
 */
class DeviceMentionBinderTest {

    private val goal = AgentGoal("fixture command", GoalShape.Free("fixture command"))
    private val lumora = InstalledApp(packageName = "net.fixture.lumora", label = "Lumora")
    private val zantor = InstalledApp(packageName = "net.fixture.zantor", label = "Zantor")
    private val tessarOne = InstalledApp(packageName = "net.fixture.tessar.one", label = "Tessar")
    private val tessarTwo = InstalledApp(packageName = "net.fixture.tessar.two", label = "Tessar")

    private fun proposed(tool: ToolId, vararg args: Pair<String, String>, risk: ActionRiskLevel = ActionRiskLevel.SAFE) =
        ProposedStep(
            tool = tool,
            args = mapOf(*args),
            origins = args.associate { (name, _) -> name to ValueOrigin.SaidBy(PrincipalId.USER) },
            risk = risk,
            effect = ToolEffect.EXTERNAL,
        )

    private fun binder(
        apps: List<InstalledApp>,
        store: FakeResolutionPreferenceStore = FakeResolutionPreferenceStore(),
        appsFail: Boolean = false,
    ): DeviceMentionBinder {
        val repo = FakeInstalledAppsRepository().apply {
            appsToReturn = apps
            if (appsFail) errorToReturn = OperationError.UnknownError("io")
        }
        val aliases = FakeAliasStore().apply {
            runBlocking { upsert(Alias("телега", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L)) }
        }
        return DeviceMentionBinder(AppTargetResolver(repo, aliases), ToolArgumentSorts(), store, DefaultResolutionPreferencePolicy())
    }

    private fun Binding.act(): Interpretation.Act = (this as Binding.Bound).interpretation

    private fun learnedTessarTwo(streak: Int): FakeResolutionPreferenceStore {
        val candidates = CandidateSet(listOf(ResolvedTarget.App(tessarOne.packageName), ResolvedTarget.App(tessarTwo.packageName)))
        return FakeResolutionPreferenceStore().apply {
            runBlocking {
                upsert(
                    ResolutionPreference(
                        capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                        context = ResolutionContext.None,
                        preferredTarget = ResolvedTarget.App(tessarTwo.packageName),
                        evidence = PreferenceEvidence(streak = streak, totalChoices = streak, lastChosenAtEpochMs = 1L),
                        learnedInSetFingerprint = fingerprintOf(candidates),
                    ),
                )
            }
        }
    }

    @Test
    fun `a step without an app mention is bound as the model wrote it`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(InAppToolIds.WEB_SEARCH, "query" to "ostravel lectures")).act()

        assertEquals(mapOf("query" to ArgSource.Literal("ostravel lectures")), act.step.invocation.args)
        assertEquals(StepRationale.MODEL_PROPOSED, act.step.rationale)
        assertNull(act.choice)
    }

    @Test
    fun `one candidate binds the package and keeps the user's words where the tool shows them`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "lumora", risk = ActionRiskLevel.CONFIRM)).act()

        assertEquals(
            mapOf("app" to ArgSource.Literal("net.fixture.lumora"), "app_label" to ArgSource.Literal("lumora")),
            act.step.invocation.args,
        )
        assertEquals(
            mapOf("app" to ValueOrigin.Ours, "app_label" to ValueOrigin.SaidBy(PrincipalId.USER)),
            act.origins,
        )
    }

    @Test
    fun `launch_app is pinned to the package and keeps the words in query`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телега")).act()

        assertEquals(
            mapOf("query" to ArgSource.Literal("телега"), "app" to ArgSource.Literal("net.fixture.zantor")),
            act.step.invocation.args,
        )
    }

    @Test
    fun `open_app_info takes the package only`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "lumora")).act()

        assertEquals(mapOf("app" to ArgSource.Literal("net.fixture.lumora")), act.step.invocation.args)
    }

    /** Criterion 17 (binder half): two candidates are a choice BEFORE any plan, and nothing is bound yet. */
    @Test
    fun `two candidates give a choice before any plan, and nothing is bound yet`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choice!!.candidates)
        assertEquals("app", act.choice!!.arg)
        assertNull(act.step.invocation.args["app"])
    }

    /** `launch_app` keeps learned choices (the same act — a launch): a confident one binds without asking. */
    @Test
    fun `launch_app's confident learned choice binds without asking`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertNull(act.choice)
        assertEquals(ArgSource.Literal("net.fixture.tessar.two"), act.step.invocation.args["app"])
    }

    @Test
    fun `a weaker learned choice is offered first, still as a choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 1))
            .bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertEquals(listOf(tessarTwo, tessarOne), act.choice!!.candidates)
    }

    /** `AppTargetResolver`'s KDoc: consent learned for OPENING is not consent for REMOVING. */
    @Test
    fun `uninstall never reads a learned choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "tessar", risk = ActionRiskLevel.CONFIRM)).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choice!!.candidates)
        assertNull(act.choice!!.learning)
    }

    /** Review Focus 5 and the limit spec §7.12.1 names: «телегу» is not the alias «телега». */
    @Test
    fun `a word form that matches nothing is not found, never guessed`() = runTest {
        val result = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телегу"))

        assertEquals(Binding.NotFound("телегу"), result)
    }

    @Test
    fun `an app list that cannot be read is unavailable, not "not found"`() = runTest {
        val result = binder(emptyList(), appsFail = true).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "lumora"))

        assertEquals(Binding.Unavailable, result)
    }

    /**
     * The model is asked for an app under the names our descriptions give it, and the binder reads the
     * name [AuthoredToolDescriptions.MENTION_ARG] of a tool [ToolArgumentSorts] has a row for. Were the
     * two to diverge, the binder would pass the step through unbound — the tool offered and dead, every
     * per-tool test green. Read over the PRODUCTION descriptions and sorts, not a list in this file.
     *
     * Behavioural rather than a comparison of names, which `AuthoredToolDescriptionsTest > an app-sort
     * tool takes exactly one mention, under the name the binder reads` already makes: each mention is
     * bound under the description's OWN name, and the package must land where the tool's sort row takes
     * it — at once for one candidate, and through the user's pick for two. The expected maps are built
     * from the sort row and compared by equality (R14-43).
     */
    @Test
    fun `every app mention the model is asked for is one the binder reads, and every bindable tool asks for one`() = runTest {
        val sorts = ToolArgumentSorts()
        val described = AuthoredToolDescriptions().rows()

        val silent = described.filter { (id, description) -> sorts.appBindingFor(id) != null && description.args.none { it.mention } }
        assertEquals(emptySet<ToolId>(), silent.keys)

        val mentions = described.flatMap { (id, description) -> description.args.filter { it.mention }.map { id to it.name } }
        // The control: "nothing unread" must not mean "nothing read".
        assertTrue(mentions.isNotEmpty())

        val unread = mentions.filterNot { (id, name) ->
            val sort = sorts.appBindingFor(id) ?: return@filterNot false
            fun expected(packageName: String, words: String) =
                mapOf(sort.arg to ArgSource.Literal(packageName)) + listOfNotNull(sort.labelArg?.let { it to ArgSource.Literal(words) })

            val one = binder(listOf(lumora)).bind(goal, proposed(id, name to "lumora")).act()
            val several = binder(listOf(tessarOne, tessarTwo)).bind(goal, proposed(id, name to "tessar")).act()

            one.step.invocation.args == expected(lumora.packageName, "lumora") &&
                several.choose(tessarTwo.packageName)?.step?.invocation?.args == expected(tessarTwo.packageName, "tessar")
        }
        assertEquals(emptyList<Pair<ToolId, String>>(), unread)
    }
}
