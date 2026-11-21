package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionCatalog
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.intent.RuleBasedIntentMatcher
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.ExecutionPlan
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Binding
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PendingChoice
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
import com.sidr.launcher.domain.memory.resolution.ResolutionLearningToken
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
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
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
    private val pellixOne = InstalledApp(packageName = "net.fixture.pellix.one", label = "Pellix")
    private val pellixTwo = InstalledApp(packageName = "net.fixture.pellix.two", label = "Pellix")

    private fun proposed(
        tool: ToolId,
        vararg args: Pair<String, String>,
        risk: ActionRiskLevel = ActionRiskLevel.SAFE,
        effect: ToolEffect = ToolEffect.EXTERNAL,
    ) =
        ProposedStep(
            tool = tool,
            args = mapOf(*args),
            origins = args.associate { (name, _) -> name to ValueOrigin.SaidBy(PrincipalId.USER) },
            risk = risk,
            effect = effect,
        )

    /**
     * The production shape of the two Android sources a remainder's steps come from (`AgentProvidesModule`'s
     * first two adapters), seen through the federation — what `rebind` reads each step's effect from. Nothing
     * here invokes a tool.
     */
    private val registry: ToolRegistry = ToolFederation(
        listOf(ToolLevels.IN_APP to SystemIntentToolSource(DefaultActionCatalog()), ToolLevels.SYSTEM_INTENT to Tier0IntentToolSource(ToolPermissionCatalog()) { true })
            .map { (level, source) ->
                ToolAdapter(level, source, object : ToolWorker {
                    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult = error("a binder invokes nothing")
                })
            },
    ).registry

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
            registry,
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
        val act = binder(listOf(lumora)).bind(goal, listOf(proposed(InAppToolIds.WEB_SEARCH, "query" to "ostravel lectures"))).act()

        assertEquals(mapOf("query" to ArgSource.Literal("ostravel lectures")), act.steps.single().invocation.args)
        assertEquals(StepRationale.MODEL_PROPOSED, act.steps.single().rationale)
        assertEquals(emptyList<PendingChoice>(), act.choices)
    }

    @Test
    fun `one candidate binds the package and keeps the user's words where the tool shows them`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, listOf(proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "lumora", risk = ActionRiskLevel.CONFIRM))).act()

        assertEquals(
            mapOf("app" to ArgSource.Literal("net.fixture.lumora"), "app_label" to ArgSource.Literal("lumora")),
            act.steps.single().invocation.args,
        )
        assertEquals(
            mapOf("app" to ValueOrigin.Ours, "app_label" to ValueOrigin.SaidBy(PrincipalId.USER)),
            act.origins.getValue(0),
        )
    }

    @Test
    fun `launch_app is pinned to the package and keeps the words in query`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "телега"))).act()

        assertEquals(
            mapOf("query" to ArgSource.Literal("телега"), "app" to ArgSource.Literal("net.fixture.zantor")),
            act.steps.single().invocation.args,
        )
    }

    @Test
    fun `open_app_info takes the package only`() = runTest {
        val act = binder(listOf(lumora)).bind(goal, listOf(proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "lumora"))).act()

        assertEquals(mapOf("app" to ArgSource.Literal("net.fixture.lumora")), act.steps.single().invocation.args)
    }

    /**
     * Owner decision 2026-11-17 (the bridge's half of narrow entry (a)): «lumora» names «Lumora+» only after
     * folding — for `uninstall_app`, whose act cannot be undone, that is a one-app pick marked to confirm and
     * nothing is bound; for `open_app_info` it binds as before. An exact-as-typed mention still binds the
     * uninstall (`one candidate binds the package and keeps the user's words where the tool shows them`).
     */
    @Test
    fun `a mention exact only after folding is a pick for uninstall and a bind for app info`() = runTest {
        val lumoraPlus = InstalledApp(packageName = "net.fixture.qelvarn", label = "Lumora+")

        val uninstall = binder(listOf(lumoraPlus))
            .bind(goal, listOf(proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "lumora", risk = ActionRiskLevel.CONFIRM))).act()
        assertEquals(listOf(lumoraPlus), uninstall.choices.single().choice.candidates)
        assertTrue(uninstall.choices.single().choice.confirm)
        assertNull(uninstall.steps.single().invocation.args["app"])

        val info = binder(listOf(lumoraPlus)).bind(goal, listOf(proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "lumora"))).act()
        assertEquals(mapOf("app" to ArgSource.Literal("net.fixture.qelvarn")), info.steps.single().invocation.args)
        assertEquals(emptyList<PendingChoice>(), info.choices)
    }

    /** Criterion 17 (binder half): two candidates are a choice BEFORE any plan, and nothing is bound yet. */
    @Test
    fun `two candidates give a choice before any plan, and nothing is bound yet`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "tessar"))).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choices.single().choice.candidates)
        assertEquals("app", act.choices.single().choice.arg)
        assertNull(act.steps.single().invocation.args["app"])
    }

    /** `launch_app` keeps learned choices (the same act — a launch): a confident one binds without asking. */
    @Test
    fun `launch_app's confident learned choice binds without asking`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "tessar"))).act()

        assertEquals(emptyList<PendingChoice>(), act.choices)
        assertEquals(ArgSource.Literal("net.fixture.tessar.two"), act.steps.single().invocation.args["app"])
    }

    @Test
    fun `a weaker learned choice is offered first, still as a choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 1))
            .bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "tessar"))).act()

        assertEquals(listOf(tessarTwo, tessarOne), act.choices.single().choice.candidates)
    }

    /** `AppTargetResolver`'s KDoc: consent learned for OPENING is not consent for REMOVING. */
    @Test
    fun `uninstall never reads a learned choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo), store = learnedTessarTwo(streak = 3))
            .bind(goal, listOf(proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "tessar", risk = ActionRiskLevel.CONFIRM))).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choices.single().choice.candidates)
        assertNull(act.choices.single().choice.learning)
    }

    /**
     * Rewritten in phase 3b (fork 3b-G) at constant count: 3a's limit — «телегу» is not the alias «телега»,
     * so not found — is gone. A form of an alias is now offered for the user's confirmation, never bound;
     * a word nothing on the device names is still not found, never guessed.
     */
    @Test
    fun `a word form of an alias is offered to confirm, and a word nothing names is not found`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "телегу"))).act()

        assertEquals(listOf(zantor), act.choices.single().choice.candidates)
        assertTrue(act.choices.single().choice.confirm)
        assertNull(act.steps.single().invocation.args["app"])

        val result = binder(listOf(zantor)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "вельтрикс")))
        assertEquals(Binding.NotFound("вельтрикс"), result)
    }

    /** Fork 3b-G: «миравину» is a form of the label «Миравин» — a one-app pick the user confirms; nothing is bound yet. */
    @Test
    fun `a word form becomes a one-app pick marked to confirm`() = runTest {
        val act = binder(listOf(lumora, miravin)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "миравину"))).act()

        assertEquals(listOf(miravin), act.choices.single().choice.candidates)
        assertEquals("app", act.choices.single().choice.arg)
        assertTrue(act.choices.single().choice.confirm)
        assertNull(act.steps.single().invocation.args["app"])
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

        val offered = binder(listOf(zantor)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "телегу"))).act()
        assertEquals(key, offered.choices.single().choice.learning!!.capabilityKey)
        assertEquals(CandidateSet(listOf(ResolvedTarget.App("net.fixture.zantor"))), offered.choices.single().choice.learning!!.candidateSet)

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
        val learned = binder(listOf(zantor), store = confirmedThrice).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "телегу"))).act()
        assertEquals(emptyList<PendingChoice>(), learned.choices)
        assertEquals(ArgSource.Literal("net.fixture.zantor"), learned.steps.single().invocation.args["app"])

        val uninstall = binder(listOf(zantor), store = confirmedThrice)
            .bind(goal, listOf(proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "телегу", risk = ActionRiskLevel.CONFIRM))).act()
        assertEquals(listOf(zantor), uninstall.choices.single().choice.candidates)
        assertTrue(uninstall.choices.single().choice.confirm)
        assertNull(uninstall.choices.single().choice.learning)
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
            .bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "tessar"))).act()

        assertEquals(CapabilityKey(ActionIds.LAUNCH_APP, "tessar"), written)
        assertEquals(listOf(written), store.found)
        assertEquals(listOf(tessarTwo, tessarOne), act.choices.single().choice.candidates)
    }

    @Test
    fun `an app list that cannot be read is unavailable, not "not found"`() = runTest {
        val result = binder(emptyList(), appsFail = true).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "lumora")))

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

            val one = binder(listOf(lumora)).bind(goal, listOf(proposed(id, name to "lumora"))).act()
            val several = binder(listOf(tessarOne, tessarTwo)).bind(goal, listOf(proposed(id, name to "tessar"))).act()

            one.steps.single().invocation.args == expected(lumora.packageName, "lumora") &&
                several.choose(tessarTwo.packageName)?.steps?.single()?.invocation?.args == expected(tessarTwo.packageName, "tessar")
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
        val result = binder(listOf(lumora)).bind(goal, listOf(proposed(Tier0ToolIds.SET_TIMER, "duration" to "полчасика")))

        assertEquals(Binding.NotUnderstood("полчасика"), result)

        val readable = binder(listOf(lumora)).bind(goal, listOf(proposed(Tier0ToolIds.SET_TIMER, "duration" to "10 dakika"))).act()
        assertEquals(mapOf("duration" to ArgSource.Literal("10 dakika")), readable.steps.single().invocation.args)
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
        val normalised = binder(listOf(lumora)).bind(goal, listOf(proposed(InAppToolIds.OPEN_URL, "url" to "HTTPS://Quillmere.example/Path"))).act()
        assertEquals(mapOf("url" to ArgSource.Literal("https://Quillmere.example/Path")), normalised.steps.single().invocation.args)

        val refused = binder(listOf(lumora)).bind(goal, listOf(proposed(InAppToolIds.OPEN_URL, "url" to "https://quillmere.example/a b")))
        assertEquals(Binding.NotUnderstood("https://quillmere.example/a b"), refused)
    }

    // --- A4′ phase 3b, Task 9: a reading of several steps is bound whole, before the card ---

    /**
     * Fork 3b-B, §7.12.3 condition 2, B-19 (the binder's half): every step's app mention is bound before
     * the card, each exactly as a one-step reading is. Step 0's one «Lumora» is pinned at once, with the
     * user's words where `uninstall_app` shows them; step 1's two «Pellix» and step 2's two «Tessar» are
     * two picks owed — in STEP ORDER, each on its own step and each carrying its own launch's learning
     * token (keyed `LearnedChoiceKeys.launch` of its own mention: «pellix», «tessar»). Fix round 1
     * (finding I1, ruling E9-R1): with one pick owed, an order or a shared token was never seen. Origins
     * and effects are each step's own — step 1 is planted `LOCAL` between two `EXTERNAL` steps so that a
     * mix-up would show. Compared whole, by equality.
     */
    @Test
    fun `each step's app mention is bound before the card`() = runTest {
        val act = binder(listOf(lumora, pellixOne, pellixTwo, tessarOne, tessarTwo)).bind(
            goal,
            listOf(
                proposed(Tier0ToolIds.UNINSTALL_APP, "app" to "lumora", risk = ActionRiskLevel.CONFIRM),
                proposed(ToolIds.LAUNCH_APP, "app" to "pellix", effect = ToolEffect.LOCAL),
                proposed(ToolIds.LAUNCH_APP, "app" to "tessar"),
            ),
        ).act()

        val pellixes = CandidateSet(listOf(ResolvedTarget.App("net.fixture.pellix.one"), ResolvedTarget.App("net.fixture.pellix.two")))
        val tessars = CandidateSet(listOf(ResolvedTarget.App("net.fixture.tessar.one"), ResolvedTarget.App("net.fixture.tessar.two")))
        assertEquals(
            Interpretation.Act(
                goal = goal,
                steps = listOf(
                    PlanStep(
                        index = 0,
                        invocation = ToolInvocation(
                            Tier0ToolIds.UNINSTALL_APP,
                            mapOf("app" to ArgSource.Literal("net.fixture.lumora"), "app_label" to ArgSource.Literal("lumora")),
                        ),
                        risk = ActionRiskLevel.CONFIRM,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                    PlanStep(
                        index = 1,
                        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("pellix"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                    PlanStep(
                        index = 2,
                        invocation = ToolInvocation(ToolIds.LAUNCH_APP, mapOf("query" to ArgSource.Literal("tessar"))),
                        risk = ActionRiskLevel.SAFE,
                        precondition = StepPrecondition.None,
                        rationale = StepRationale.MODEL_PROPOSED,
                    ),
                ),
                origins = mapOf(
                    0 to mapOf("app" to ValueOrigin.Ours, "app_label" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    1 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                    2 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                ),
                effects = mapOf(0 to ToolEffect.EXTERNAL, 1 to ToolEffect.LOCAL, 2 to ToolEffect.EXTERNAL),
                choices = listOf(
                    PendingChoice(
                        step = 1,
                        choice = AppChoice(
                            arg = "app",
                            mention = "pellix",
                            candidates = listOf(pellixOne, pellixTwo),
                            learning = ResolutionLearningToken(
                                capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "pellix"),
                                context = ResolutionContext.None,
                                candidateSet = pellixes,
                                fingerprint = fingerprintOf(pellixes),
                                isAppAmbiguityFlow = true,
                            ),
                        ),
                    ),
                    PendingChoice(
                        step = 2,
                        choice = AppChoice(
                            arg = "app",
                            mention = "tessar",
                            candidates = listOf(tessarOne, tessarTwo),
                            learning = ResolutionLearningToken(
                                capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                                context = ResolutionContext.None,
                                candidateSet = tessars,
                                fingerprint = fingerprintOf(tessars),
                                isAppAmbiguityFlow = true,
                            ),
                        ),
                    ),
                ),
            ),
            act,
        )
    }

    /**
     * Controller ruling E9-P5: what the device cannot bind in ANY step answers for the whole reading —
     * there is no card for the steps that did bind. The first two unbound steps come SECOND, after a step
     * that binds on its own (the control): an app nothing on the device names is not found; a duration the
     * sort cannot read is not understood. Fix round 1 (finding M1, ruling E9-R3): when two steps cannot be
     * bound, the FIRST refusal answers — step 0's unknown app, not step 1's unreadable value.
     */
    @Test
    fun `one unknown app in any step means not found for the whole reading`() = runTest {
        val launch = proposed(ToolIds.LAUNCH_APP, "app" to "lumora")
        assertTrue("the control: the first step binds on its own", binder(listOf(lumora)).bind(goal, listOf(launch)) is Binding.Bound)

        assertEquals(
            "an app nothing names, in the second step",
            Binding.NotFound("вельтрикс"),
            binder(listOf(lumora)).bind(goal, listOf(launch, proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "вельтрикс"))),
        )
        assertEquals(
            "a duration the sort cannot read, in the second step",
            Binding.NotUnderstood("полчасика"),
            binder(listOf(lumora)).bind(goal, listOf(launch, proposed(Tier0ToolIds.SET_TIMER, "duration" to "полчасика"))),
        )
        assertEquals(
            "the first refusal answers: an unknown app in step 0 before an unreadable value in step 1",
            Binding.NotFound("вельтрикс"),
            binder(listOf(lumora)).bind(
                goal,
                listOf(
                    proposed(Tier0ToolIds.OPEN_APP_INFO, "app" to "вельтрикс"),
                    proposed(Tier0ToolIds.SET_TIMER, "duration" to "полчасика"),
                ),
            ),
        )
    }

    // --- A4′ acceptance tail, task 2 — amendment D1: an app name the model wrote, not the user ----------------

    /**
     * A step whose app mention the MODEL wrote — `BridgeValidator` found its words nowhere in the command and
     * admitted it as [ValueOrigin.ModelGenerated] (amendment D1: «открой клод» → «Claude»).
     */
    private fun modelWritten(tool: ToolId, mention: String, risk: ActionRiskLevel = ActionRiskLevel.SAFE) = ProposedStep(
        tool = tool,
        args = mapOf(AuthoredToolDescriptions.MENTION_ARG to mention),
        origins = mapOf(AuthoredToolDescriptions.MENTION_ARG to ValueOrigin.ModelGenerated),
        risk = risk,
        effect = ToolEffect.EXTERNAL,
    )

    private val claude = InstalledApp(packageName = "net.fixture.qandril", label = "Claude")

    /**
     * Amendment D1: a name the model wrote binds ONLY to an installed label that is exactly it (after the folding)
     * — the word forms that answer a user's own words («Claud» is a form of «Claude» by the edge rule) are never
     * read for the model's. «Claude» binds and launches; the edge «Claud» is not found, where the user's own
     * «Claud» would have been offered to confirm (the control). The words the tool shows are the model's, and
     * marked so.
     */
    @Test
    fun `a model-written app name binds only to an exact label`() = runTest {
        val exact = binder(listOf(claude)).bind(goal, listOf(modelWritten(ToolIds.LAUNCH_APP, "Claude"))).act()

        assertEquals(
            mapOf("query" to ArgSource.Literal("Claude"), "app" to ArgSource.Literal("net.fixture.qandril")),
            exact.steps.single().invocation.args,
        )
        assertEquals(mapOf("query" to ValueOrigin.ModelGenerated, "app" to ValueOrigin.Ours), exact.origins.getValue(0))
        assertEquals(emptyList<PendingChoice>(), exact.choices)

        assertEquals(Binding.NotFound("Claud"), binder(listOf(claude)).bind(goal, listOf(modelWritten(ToolIds.LAUNCH_APP, "Claud"))))

        val users = binder(listOf(claude)).bind(goal, listOf(proposed(ToolIds.LAUNCH_APP, "app" to "Claud"))).act()
        assertEquals("the control: the user's own edge is offered to confirm", listOf(claude), users.choices.single().choice.candidates)
        assertTrue(users.choices.single().choice.confirm)
    }

    /** An alias is the user's explicit name for an app: a model-written name that is exactly the alias binds to its app. */
    @Test
    fun `a model-written app name binds to an exact alias`() = runTest {
        val act = binder(listOf(zantor)).bind(goal, listOf(modelWritten(ToolIds.LAUNCH_APP, "телега"))).act()

        assertEquals(
            mapOf("query" to ArgSource.Literal("телега"), "app" to ArgSource.Literal("net.fixture.zantor")),
            act.steps.single().invocation.args,
        )
        assertEquals(emptyList<PendingChoice>(), act.choices)
        assertEquals(
            "a form of the alias is not read for the model's word",
            Binding.NotFound("телегу"),
            binder(listOf(zantor)).bind(goal, listOf(modelWritten(ToolIds.LAUNCH_APP, "телегу"))),
        )
    }

    /** Two installed apps answer the model's name exactly: the user picks, nothing is bound yet. */
    @Test
    fun `a model-written app name with two exact matches is a choice`() = runTest {
        val act = binder(listOf(tessarOne, tessarTwo)).bind(goal, listOf(modelWritten(ToolIds.LAUNCH_APP, "Tessar"))).act()

        assertEquals(listOf(tessarOne, tessarTwo), act.choices.single().choice.candidates)
        assertEquals(false, act.choices.single().choice.confirm)
        assertNull(act.steps.single().invocation.args["app"])
    }

    /**
     * Owner decision 2026-11-17 kept under amendment D1: a tool whose act cannot be undone binds only a name exact
     * AS THE USER TYPED IT, and a name the model wrote was not typed at all — so for `uninstall_app` even an exact
     * label is a one-app pick marked to confirm, never a bind. The control: the same name for `open_app_info`
     * (transient) binds.
     */
    @Test
    fun `a model-written app name for an irreversible tool is a pick to confirm, never a bind`() = runTest {
        val uninstall = binder(listOf(claude))
            .bind(goal, listOf(modelWritten(Tier0ToolIds.UNINSTALL_APP, "Claude", risk = ActionRiskLevel.CONFIRM))).act()

        assertEquals(listOf(claude), uninstall.choices.single().choice.candidates)
        assertTrue(uninstall.choices.single().choice.confirm)
        assertNull(uninstall.steps.single().invocation.args["app"])

        val info = binder(listOf(claude)).bind(goal, listOf(modelWritten(Tier0ToolIds.OPEN_APP_INFO, "Claude"))).act()
        assertEquals(mapOf("app" to ArgSource.Literal("net.fixture.qandril")), info.steps.single().invocation.args)
    }

    // --- A4′ phase 3b, Task 11 (fork 3b-A, B-10): the remainder of a model plan that acted, bound again ------

    private val orvane = "net.fixture.orvane"
    private val vemblic = InstalledApp(packageName = "net.fixture.vemblic", label = "Vemblic")

    private fun remainderStep(index: Int, tool: ToolId, vararg args: Pair<String, String>) = PlanStep(
        index = index,
        invocation = ToolInvocation(tool, args.associate { (name, value) -> name to ArgSource.Literal(value) }),
        risk = ActionRiskLevel.SAFE,
        precondition = StepPrecondition.None,
        rationale = StepRationale.MODEL_PROPOSED,
    )

    /**
     * Controller ruling E11-P4: a remainder's app is resolved AGAIN from the words the user typed, which the
     * step kept in `launch_app`'s `query` — through the same mention branch as a first binding, never the
     * package the step kept. Two cases, each compared whole:
     *  - the app those words named then (`net.fixture.orvane`) is gone, and they now name «Lumora»: bound to
     *    it, ours; the user's words stay the user's, and the second step's value — written by the model —
     *    is marked the model's (ruling E11-P1); each effect is the registry's;
     *  - the words now name two «Tessar»: a pick is owed before the card, carrying the launch's own learning
     *    token keyed on those words (B-19) — and the step keeps no package until it is made.
     */
    @Test
    fun `a remainder re-binds its app from the user's words`() = runTest {
        val one = binder(listOf(lumora)).rebind(
            goal,
            ExecutionPlan(
                listOf(
                    remainderStep(0, ToolIds.LAUNCH_APP, "app" to orvane, "query" to "lumora"),
                    remainderStep(1, InAppToolIds.WEB_SEARCH, "query" to "ostravel lectures"),
                ),
            ),
        )
        assertEquals(
            Binding.Bound(
                Interpretation.Act(
                    goal = goal,
                    steps = listOf(
                        remainderStep(0, ToolIds.LAUNCH_APP, "query" to "lumora", "app" to "net.fixture.lumora"),
                        remainderStep(1, InAppToolIds.WEB_SEARCH, "query" to "ostravel lectures"),
                    ),
                    origins = mapOf(
                        0 to mapOf("app" to ValueOrigin.Ours, "query" to ValueOrigin.SaidBy(PrincipalId.USER)),
                        1 to mapOf("query" to ValueOrigin.ModelGenerated),
                    ),
                    effects = mapOf(0 to ToolEffect.EXTERNAL, 1 to ToolEffect.EXTERNAL),
                    choices = emptyList(),
                ),
            ),
            one,
        )

        val two = binder(listOf(tessarOne, tessarTwo)).rebind(
            goal,
            ExecutionPlan(listOf(remainderStep(0, ToolIds.LAUNCH_APP, "app" to tessarOne.packageName, "query" to "tessar"))),
        )
        val tessars = CandidateSet(listOf(ResolvedTarget.App(tessarOne.packageName), ResolvedTarget.App(tessarTwo.packageName)))
        assertEquals(
            Binding.Bound(
                Interpretation.Act(
                    goal = goal,
                    steps = listOf(remainderStep(0, ToolIds.LAUNCH_APP, "query" to "tessar")),
                    origins = mapOf(0 to mapOf("query" to ValueOrigin.SaidBy(PrincipalId.USER))),
                    effects = mapOf(0 to ToolEffect.EXTERNAL),
                    choices = listOf(
                        PendingChoice(
                            step = 0,
                            choice = AppChoice(
                                arg = "app",
                                mention = "tessar",
                                candidates = listOf(tessarOne, tessarTwo),
                                learning = ResolutionLearningToken(
                                    capabilityKey = CapabilityKey(ActionIds.LAUNCH_APP, "tessar"),
                                    context = ResolutionContext.None,
                                    candidateSet = tessars,
                                    fingerprint = fingerprintOf(tessars),
                                    isAppAmbiguityFlow = true,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            two,
        )
    }

    /**
     * The plan's Review Focus 4: the app a paused plan named was removed meanwhile. The remainder says «нет
     * такого приложения» — from the user's words where the step kept them (`launch_app`), by its package where
     * it kept only that (`open_app_info`) — and never launches the stale package. The control: with both apps
     * installed, the same two steps bind.
     */
    @Test
    fun `a remainder whose app is gone is not found`() = runTest {
        val launch = ExecutionPlan(listOf(remainderStep(0, ToolIds.LAUNCH_APP, "app" to lumora.packageName, "query" to "lumora")))
        val info = ExecutionPlan(listOf(remainderStep(0, Tier0ToolIds.OPEN_APP_INFO, "app" to vemblic.packageName)))
        val both = binder(listOf(lumora, vemblic))
        assertTrue("the control: installed, the launch binds", both.rebind(goal, launch) is Binding.Bound)
        assertTrue("the control: installed, the app info binds", both.rebind(goal, info) is Binding.Bound)

        assertEquals(Binding.NotFound("lumora"), binder(listOf(zantor)).rebind(goal, launch))
        assertEquals(Binding.NotFound("net.fixture.vemblic"), binder(listOf(zantor)).rebind(goal, info))
    }
}
