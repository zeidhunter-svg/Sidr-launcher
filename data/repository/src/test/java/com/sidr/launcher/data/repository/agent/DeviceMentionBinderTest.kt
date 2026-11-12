package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
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
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.resolution.CandidateSet
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.RecordResolutionChoiceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolutionPreferenceStore
import com.sidr.launcher.domain.memory.resolution.ResolveCommandWithPreferenceUseCase
import com.sidr.launcher.domain.memory.resolution.ResolvedCommand
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.memory.resolution.fingerprintOf
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
    private val miravin = InstalledApp(packageName = "net.fixture.miravin", label = "Миравин")

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
        store: ResolutionPreferenceStore = FakeResolutionPreferenceStore(),
        appsFail: Boolean = false,
    ): DeviceMentionBinder {
        val repo = FakeInstalledAppsRepository().apply {
            appsToReturn = apps
            if (appsFail) errorToReturn = OperationError.UnknownError("io")
        }
        val aliases = FakeAliasStore().apply {
            runBlocking { upsert(Alias("телега", AliasTarget.App("net.fixture.zantor"), createdAtEpochMs = 0L)) }
        }
        val appTargets = AppTargetResolver(repo, aliases, PlainTextFolding)
        return DeviceMentionBinder(
            appTargets,
            CatalogArgumentSorts(),
            sortResolversOf(appTargets),
            store,
            DefaultResolutionPreferencePolicy(),
            PlainTextFolding,
        )
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

    /**
     * Rewritten in phase 3b (fork 3b-G) at constant count: 3a's limit — «телегу» is not the alias «телега»,
     * so not found — is gone. A form of an alias is now offered for the user's confirmation, never bound;
     * a word nothing on the device names is still not found, never guessed.
     */
    @Test
    fun `a word form of an alias is offered to confirm, and a word nothing names is not found`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телегу")).act()

        assertEquals(listOf(zantor), act.choice!!.candidates)
        assertTrue(act.choice!!.confirm)
        assertNull(act.step.invocation.args["app"])

        val result = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "вельтрикс"))
        assertEquals(Binding.NotFound("вельтрикс"), result)
    }

    /** Fork 3b-G: «миравину» is a form of the label «Миравин» — a one-app pick the user confirms; nothing is bound yet. */
    @Test
    fun `a word form becomes a one-app pick marked to confirm`() = runTest {
        val act = binder(listOf(lumora, miravin)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "миравину")).act()

        assertEquals(listOf(miravin), act.choice!!.candidates)
        assertEquals("app", act.choice!!.arg)
        assertTrue(act.choice!!.confirm)
        assertNull(act.step.invocation.args["app"])
    }

    /**
     * Plan §0.2 B-3/B-4: a confirmation is a learned choice, for `launch_app` only. The token is keyed on
     * the name (`LearnedChoiceKeys.launch`), and it is the key the store is READ by — a confident choice
     * learned under it binds without asking. `uninstall_app` neither carries a token nor reads one, even
     * with that confident choice in the store: consent learned for opening is not consent for removing.
     */
    @Test
    fun `a confirmed word form for launch carries a learning token keyed on the name, for uninstall none`() = runTest {
        val key = CapabilityKey(ActionIds.LAUNCH_APP, "телегу")

        val offered = binder(listOf(zantor)).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телегу")).act()
        assertEquals(key, offered.choice!!.learning!!.capabilityKey)
        assertEquals(CandidateSet(listOf(ResolvedTarget.App("net.fixture.zantor"))), offered.choice!!.learning!!.candidateSet)

        val confirmedThrice = FakeResolutionPreferenceStore().apply {
            runBlocking {
                upsert(
                    ResolutionPreference(
                        capabilityKey = key,
                        context = ResolutionContext.None,
                        preferredTarget = ResolvedTarget.App("net.fixture.zantor"),
                        evidence = PreferenceEvidence(streak = 3, totalChoices = 3, lastChosenAtEpochMs = 1L),
                        learnedInSetFingerprint = fingerprintOf(CandidateSet(listOf(ResolvedTarget.App("net.fixture.zantor")))),
                    ),
                )
            }
        }
        val learned = binder(listOf(zantor), store = confirmedThrice).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "телегу")).act()
        assertNull(learned.choice)
        assertEquals(ArgSource.Literal("net.fixture.zantor"), learned.step.invocation.args["app"])

        val uninstall = binder(listOf(zantor), store = confirmedThrice)
            .bind(goal, proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "телегу", risk = ActionRiskLevel.CONFIRM)).act()
        assertEquals(listOf(zantor), uninstall.choice!!.candidates)
        assertTrue(uninstall.choice!!.confirm)
        assertNull(uninstall.choice!!.learning)
    }

    /** A store that remembers every key read and written — the evidence the cross-path test compares. */
    private class RecordingPreferenceStore(
        private val inner: FakeResolutionPreferenceStore = FakeResolutionPreferenceStore(),
    ) : ResolutionPreferenceStore by inner {
        val found = mutableListOf<CapabilityKey>()
        val upserted = mutableListOf<CapabilityKey>()

        override suspend fun find(key: CapabilityKey, context: ResolutionContext) =
            inner.find(key, context).also { found += key }

        override suspend fun upsert(preference: ResolutionPreference) =
            inner.upsert(preference).also { upserted += preference.capabilityKey }
    }

    /**
     * Plan §0.2 B-4 across two paths, with every layer that decides the key real (plan review S9): FastPath
     * — the real [RuleBasedIntentMatcher] and resolver — answers «открой tessar» with a choice between the
     * two «Tessar»; the production decorator builds its learning token; the VM's own recording use case
     * stores the user's pick; and the binder, reading the model's mention «tessar», asks the store. One
     * recording store sees both, and the key the binder READS is the key FastPath WROTE. Before Task 7
     * FastPath keyed «открой tessar» and the bridge «tessar»: a choice learned on one path was never seen
     * by the other. `RouteCommandUseCase` sits between FastPath and the decorator in production and
     * returns this decided outcome unchanged. The last assertion is what the key is for: the pick FastPath
     * learned is the one the bridge offers first.
     */
    @Test
    fun `a choice learned by FastPath for «открой tessar» is the one the bridge reads for «tessar»`() = runTest {
        val store = RecordingPreferenceStore()
        val fastPath = HandleUserCommandUseCase(
            matcher = RuleBasedIntentMatcher(),
            resolver = IntentActionResolver(FakeInstalledAppsRepository().apply { appsToReturn = listOf(tessarOne, tessarTwo) }),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        )
        val decorator = ResolveCommandWithPreferenceUseCase(
            route = { fastPath.handle(it) },
            store = store,
            policy = DefaultResolutionPreferencePolicy(),
            catalog = FakeActionCatalog(
                listOf(ActionDescriptor(ActionIds.LAUNCH_APP, "Open app", "", ActionCategory.APP, ActionRiskLevel.SAFE)),
            ),
        )

        val token = (decorator.resolve("открой tessar") as ResolvedCommand.Outcome).learningToken!!
        RecordResolutionChoiceUseCase(store).record(
            token.capabilityKey, token.context, ResolvedTarget.App(tessarTwo.packageName), token.candidateSet,
        )
        val written = store.upserted.single()
        store.found.clear()
        val act = binder(listOf(tessarOne, tessarTwo), store = store)
            .bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "tessar")).act()

        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), written)
        assertEquals(listOf(written), store.found)
        assertEquals(listOf(tessarTwo, tessarOne), act.choice!!.candidates)
    }

    @Test
    fun `an app list that cannot be read is unavailable, not "not found"`() = runTest {
        val result = binder(emptyList(), appsFail = true).bind(goal, proposed(ToolIds.LAUNCH_APP, "app" to "lumora"))

        assertEquals(Binding.Unavailable, result)
    }

    /**
     * The model is asked for an app under the names our descriptions give it, and the binder reads the
     * name [AuthoredToolDescriptions.MENTION_ARG] of a tool [CatalogArgumentSorts] has an app row for. Were
     * the two to diverge, the binder would pass the step through unbound — the tool offered and dead, every
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
        val sorts = CatalogArgumentSorts()
        val described = AuthoredToolDescriptions().rows()

        val silent = described.filter { (id, description) -> sorts.appArgOf(id) != null && description.args.none { it.mention } }
        assertEquals(emptySet<ToolId>(), silent.keys)

        val mentions = described.flatMap { (id, description) -> description.args.filter { it.mention }.map { id to it.name } }
        // The control: "nothing unread" must not mean "nothing read".
        assertTrue(mentions.isNotEmpty())

        val unread = mentions.filterNot { (id, name) ->
            val (appArg, sort) = sorts.appArgOf(id) ?: return@filterNot false
            fun expected(packageName: String, words: String) =
                mapOf(appArg to ArgSource.Literal(packageName)) + listOfNotNull(sort.labelArg?.let { it to ArgSource.Literal(words) })

            val one = binder(listOf(lumora)).bind(goal, proposed(id, name to "lumora")).act()
            val several = binder(listOf(tessarOne, tessarTwo)).bind(goal, proposed(id, name to "tessar")).act()

            one.step.invocation.args == expected(lumora.packageName, "lumora") &&
                several.choose(tessarTwo.packageName)?.step?.invocation?.args == expected(tessarTwo.packageName, "tessar")
        }
        assertEquals(emptyList<Pair<ToolId, String>>(), unread)
    }

    // --- A4′ phase 3b, Task 6: every sorted argument the model wrote resolves here, before the card ---

    /**
     * B-5, fork 3b-C. The model wrote the user's «полчасика» as `set_timer`'s duration; no duration reads
     * it (`DurationSort`), and that is known BEFORE the card — not after «Выполнить», at the worker. The
     * answer is [Binding.NotUnderstood] carrying exactly the words written for that argument: an answer
     * for the user, not a report — this class holds no port to the model at all, and the route asks it
     * once (`RouteCommandUseCaseTest > a value the bridge's binding did not understand is said in the words
     * written for it…`). The control: a
     * duration the sort reads binds as written (the sort validates, it does not rewrite).
     */
    @Test
    fun `a duration the model wrote that the device cannot read is not understood, and nothing goes back to the model`() = runTest {
        val result = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.SET_TIMER, "duration" to "полчасика"))

        assertEquals(Binding.NotUnderstood("полчасика"), result)

        val readable = binder(listOf(lumora)).bind(goal, proposed(Tier0ToolIds.SET_TIMER, "duration" to "10 dakika")).act()
        assertEquals(mapOf("duration" to ArgSource.Literal("10 dakika")), readable.step.invocation.args)
    }

    /**
     * Review Focus 5 (plan, «a URL the model wrote with an upper-case scheme or a space inside»); closes
     * E3-F3's «before the card» half. Android's filter matching compares the scheme case-sensitively, so
     * `HTTPS://…` would reach no browser; the URL sort lower-cases the scheme ONLY — the host's case is
     * kept, by RFC 3986 — and the card shows the address that will be opened. A space inside is not an
     * address: refused before the card, in the words as they reach the binder (`BridgeValidator` has
     * already trimmed them; the binder neither trims nor normalises a refused value). Both compared by
     * equality over the whole answer.
     */
    @Test
    fun `a url the model wrote is normalised or refused before the card`() = runTest {
        val normalised = binder(listOf(lumora)).bind(goal, proposed(InAppToolIds.OPEN_URL, "url" to "HTTPS://Quillmere.example/Path")).act()
        assertEquals(mapOf("url" to ArgSource.Literal("https://Quillmere.example/Path")), normalised.step.invocation.args)

        val refused = binder(listOf(lumora)).bind(goal, proposed(InAppToolIds.OPEN_URL, "url" to "https://quillmere.example/a b"))
        assertEquals(Binding.NotUnderstood("https://quillmere.example/a b"), refused)
    }
}
