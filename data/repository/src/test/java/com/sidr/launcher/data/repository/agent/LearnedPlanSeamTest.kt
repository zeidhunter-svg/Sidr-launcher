package com.sidr.launcher.data.repository.agent

import android.app.Application
import android.content.Intent
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeModelPlanner
import com.sidr.launcher.core.testing.FakeResolutionPreferenceStore
import com.sidr.launcher.core.testing.NoOpIntentMatcher
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.data.repository.agent.memory.MemoryToolWorker
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutLauncher
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolWorker
import com.sidr.launcher.data.repository.db.LearnedPlanStoreImpl
import com.sidr.launcher.data.repository.db.SidrDatabase
import com.sidr.launcher.data.repository.text.IcuTextFolding
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentExecutor
import com.sidr.launcher.domain.agent.AgentSession
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.CompositePlanner
import com.sidr.launcher.domain.agent.ConsentReason
import com.sidr.launcher.domain.agent.ExecutionState
import com.sidr.launcher.domain.agent.RunAgentSessionUseCase
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import com.sidr.launcher.domain.ai.bridge.BridgeRendering
import com.sidr.launcher.domain.ai.bridge.BridgeValidator
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.ai.bridge.RawAnswer
import com.sidr.launcher.domain.ai.bridge.RawArg
import com.sidr.launcher.domain.ai.bridge.RawStep
import com.sidr.launcher.domain.ai.bridge.Verdict
import com.sidr.launcher.domain.ai.router.RouteCommandUseCase
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.alias.DeleteAliasUseCase
import com.sidr.launcher.domain.memory.alias.SaveAliasUseCase
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.DefaultLearnedPlans
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.memory.resolution.DefaultResolutionPreferencePolicy
import com.sidr.launcher.domain.memory.resolution.DeleteLearnedChoiceUseCase
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import com.sidr.launcher.domain.trace.TraceEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **The learned-plan cache, end to end** (A4′ phase 4, Task 5; spec §8, §10 criterion 9; plan §0.2 B-4…B-9, Review
 * Focus 5). One seam, over production objects: the production [RouteCommandUseCase] with step 2c's
 * [DefaultLearnedPlans] over [LearnedPlanStoreImpl] on an in-memory Room database, the production
 * [BridgeValidator] (it reads the model's answer for step 7 and the filled template for the replay alike),
 * [DeviceMentionBinder], [AuthoredToolDescriptions], [IcuTextFolding] (the device's one folding,
 * `BridgeProvidesModule.provideTextFolding`), [BridgeFilter.ANDROID_OFFERED_LEVELS], and the engine exactly as
 * [AgentActingSeamTest] builds it — the four production adapters in `AgentProvidesModule`'s order, the
 * production 2b planner, [AgentExecutor] and [RoomAgentSessionStore] on the same database.
 *
 * Faked, and only these: FastPath's matcher (undecided, so the command reaches 2b), the model planner (the one
 * outbound seam — its answer is the production validator's verdict on a raw answer, so the steps carry exactly
 * the origins production gives them), the provider and connectivity, the installed-app list, the intent
 * launcher and the two memory stores. The launcher's «Выполнить» (Task 7) is stood in for by recording the
 * outcome's `learnable` with the production [RecordLearnedPlanUseCase].
 *
 * Fixtures are the phase's (R14-43): «Lumora», «Vemblic», «Miravin», and the learned command «сотри lumora» →
 * `uninstall_app(app = lumora)`. Packages occur in no command; assertions compare by equality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LearnedPlanSeamTest {

    private lateinit var db: SidrDatabase
    private lateinit var sessions: RoomAgentSessionStore
    private lateinit var learnedStore: LearnedPlanStoreImpl

    /** The one dependency of the route that can leave the device; its count is «was the model asked». */
    private val modelPlanner = FakeModelPlanner()

    /** Every intent the Tier-0 worker issues. Empty is an assertion: nothing reaches the world before consent. */
    private val launched = mutableListOf<Intent>()

    private val apps = FakeInstalledAppsRepository().apply {
        appsToReturn = listOf(
            InstalledApp(packageName = LUMORA_PACKAGE, label = "Lumora"),
            InstalledApp(packageName = VEMBLIC_PACKAGE, label = "Vemblic"),
            InstalledApp(packageName = MIRAVIN_PACKAGE, label = "Miravin"),
        )
    }

    /** One resolver, as production injects one: step 2b's planner and the bridge's binder read the same apps. */
    private val appTargets = AppTargetResolver(apps, FakeAliasStore(), IcuTextFolding)

    private val shortcutSource = ShortcutToolSource(ShortcutCatalog(query = { emptyList() }, ioDispatcher = UnconfinedTestDispatcher()))

    private val aliasStore = FakeAliasStore()

    /** `AgentProvidesModule.provideToolFederation`'s four adapters, in its order — [AgentActingSeamTest]'s. */
    private val federation = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), LearnedSeamRefusingWorker),
            ToolAdapter(
                ToolLevels.SYSTEM_INTENT,
                Tier0IntentToolSource(ToolPermissionCatalog()) { true },
                Tier0IntentToolWorker(
                    launcher = LearnedSeamIntentRecorder(launched),
                    catalog = ToolPermissionCatalog(),
                    presence = { true },
                    ownPackageName = OWN_PACKAGE,
                ),
            ),
            ToolAdapter(
                ToolLevels.APP_SHORTCUT,
                shortcutSource,
                ShortcutToolWorker(ShortcutLauncher { pkg, id -> error("no shortcut may run here: $pkg/$id") }),
            ),
            ToolAdapter(
                ToolLevels.LAUNCHER_MEMORY,
                MemoryToolSource(),
                MemoryToolWorker(
                    save = SaveAliasUseCase(aliasStore),
                    deleteAlias = DeleteAliasUseCase(aliasStore),
                    deleteChoice = DeleteLearnedChoiceUseCase(FakeResolutionPreferenceStore()),
                    aliases = aliasStore,
                    folding = PlainTextFolding,
                ),
            ),
        ),
    )
    private val registry = federation.registry
    private val descriptions = AuthoredToolDescriptions()

    private val binder = DeviceMentionBinder(
        appTargets,
        CatalogArgumentSorts(),
        sortResolversOf(appTargets),
        FakeResolutionPreferenceStore(),
        DefaultResolutionPreferencePolicy(),
        IcuTextFolding,
        registry,
    )

    private val ids = object : AgentSessionIdFactory {
        private var n = 0
        override fun newId() = AgentSessionId("s${++n}")
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        sessions = RoomAgentSessionStore(db.agentSessionDao(), Dispatchers.Unconfined) { 100L }
        learnedStore = LearnedPlanStoreImpl(db.learnedPlanDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** The production route, step 2c's cache in it — online, a provider configured: every step 7 condition holds. */
    private fun route() = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = NoOpIntentMatcher(),
            resolver = IntentActionResolver(FakeInstalledAppsRepository()),
            executor = FakeActionExecutor(),
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = CoroutineScope(SupervisorJob()),
        ),
        modelPlanner = modelPlanner,
        binder = binder,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(localOnlyMode = false)),
        providerConfigRepository = configuredProvider(),
        connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
        startAgentSession = StartAgentSessionUseCase(
            CompositePlanner(
                listOf(
                    TemplatePlanner(),
                    ToolMatchPlanner(ToolSelector(ToolVocabulary(), shortcutSource), appTargets, CatalogArgumentSorts(), sortResolversOf(appTargets)),
                ),
            ),
            sessions,
            ids,
            registry,
        ),
        learnedPlans = DefaultLearnedPlans(
            store = learnedStore,
            registry = registry,
            descriptions = descriptions,
            levels = BridgeFilter.ANDROID_OFFERED_LEVELS,
            binder = binder,
            folding = IcuTextFolding,
        ),
    )

    private fun runUseCase() = RunAgentSessionUseCase(
        executor = AgentExecutor(registry = registry, toolExecutor = federation.executor, budget = RuntimeBudget.Default),
        store = sessions,
    )

    /** What the model answers for [command] — `uninstall_app(app = [mention])` — as the production validator reads it. */
    private fun modelReadsUninstall(command: String, mention: String): ModelPlanning {
        val raw = RawAnswer(
            kind = BridgeRendering.KIND_PLAN,
            steps = listOf(RawStep(Tier0ToolIds.UNINSTALL_APP.value, mapOf("app" to RawArg.Text(mention)))),
            question = null,
            destination = null,
        )
        val offering = BridgeFilter.offeringOf(registry, BridgeFilter.ANDROID_OFFERED_LEVELS, descriptions)
        val verdict = BridgeValidator.validate(raw, offering, registry, command, IcuTextFolding)
        assertTrue("the premise: the production validator admits the model's answer, was $verdict", verdict is Verdict.Valid)
        return (verdict as Verdict.Valid).planning
    }

    /** What «сотри lumora» teaches (B-2): the form «сотри …», and the app argument as slot 0 — no package. */
    private val erase = LearnedPlanShape(
        CommandForm.parse("сотри …"),
        PlanTemplate(listOf(TemplateStep(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to TemplateArg.Slot(0))))),
    )

    /** Three step-7 readings of «сотри lumora», each recorded as the launcher's «Выполнить» will record it. */
    private suspend fun confirmEraseThreeTimes() {
        modelPlanner.answer = modelReadsUninstall(LEARNED_COMMAND, "lumora")
        repeat(3) { n ->
            val outcome = route().route(LEARNED_COMMAND)
            assertTrue("reading ${n + 1} must be a card, was $outcome", outcome is CommandOutcome.Interpreted)
            val learnable = (outcome as CommandOutcome.Interpreted).learnable
            assertEquals("reading ${n + 1} teaches the form and its template", erase, learnable)
            assertEquals(
                OperationResult.Success(Unit),
                RecordLearnedPlanUseCase(learnedStore).record(checkNotNull(learnable), nowEpochMs = 1_000L + n),
            )
        }
    }

    private fun uninstall(packageName: String, label: String) = ToolInvocation(
        Tier0ToolIds.UNINSTALL_APP,
        mapOf("app" to ArgSource.Literal(packageName), "app_label" to ArgSource.Literal(label)),
    )

    private suspend fun activeSession(): AgentSession =
        checkNotNull((sessions.active() as OperationResult.Success).value) { "a started session must be at rest in Room" }

    /**
     * Spec §10 criterion 9, the cache's whole promise: three confirmed readings pin the form, and the fourth
     * command that fits it — another app's name — is bound on the device and started, with its mark, while the
     * model is asked no fourth time. The started session names the package today's binder found, which no
     * command and no template holds.
     */
    @Test
    fun `three confirmed readings, then the fourth replays on the device and the model is not asked`() = runTest {
        confirmEraseThreeTimes()
        assertEquals("three readings, three asks", 3, modelPlanner.planCallCount)

        val fourth = route().route("сотри Vemblic")

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("s1"), learned = CommandForm.parse("сотри …")), fourth)
        assertEquals("the model is not asked a fourth time", 3, modelPlanner.planCallCount)
        assertEquals(uninstall(VEMBLIC_PACKAGE, "Vemblic"), activeSession().plan.steps.single().invocation)
        assertTrue("nothing reaches the world on a start: $launched", launched.isEmpty())
    }

    /**
     * Review Focus 5, the third leg of spec §10 criterion 9: a learned uninstall — no interpretation card — still
     * stops at the engine's consent card, naming the package, every time it is replayed. Each replay is a new
     * session run through the engine; each ends `AwaitingConsent` on `uninstall_app` at `CONFIRM`, with both
     * consent reasons, and nothing is handed to the uninstaller.
     */
    @Test
    fun `a learned uninstall stops at consent every time`() = runTest {
        confirmEraseThreeTimes()

        listOf("сотри Vemblic" to (VEMBLIC_PACKAGE to "Vemblic"), "сотри Miravin" to (MIRAVIN_PACKAGE to "Miravin"))
            .forEachIndexed { n, (command, app) ->
                val (packageName, label) = app
                val outcome = route().route(command)
                assertEquals(
                    CommandOutcome.AgentSessionStarted(AgentSessionId("s${n + 1}"), learned = CommandForm.parse("сотри …")),
                    outcome,
                )

                val run = runUseCase().run(activeSession())
                val session = (run as OperationResult.Success).value

                assertEquals("«$command»", ExecutionState.AwaitingConsent, session.state)
                val pending = session.plan.steps.single()
                assertEquals(uninstall(packageName, label), pending.invocation)
                assertEquals(ActionRiskLevel.CONFIRM, pending.risk)
                assertEquals(
                    listOf(TraceEvent.ConsentRequested(0, setOf(ConsentReason.RISK_LEVEL, ConsentReason.DURABLE_EFFECT))),
                    session.trace.events.filterIsInstance<TraceEvent.ConsentRequested>(),
                )
                assertTrue("nothing may reach the uninstaller before consent: $launched", launched.isEmpty())
            }
        assertEquals("the replays asked the model nothing", 3, modelPlanner.planCallCount)
    }

    private companion object {
        const val LEARNED_COMMAND = "сотри lumora"
        const val LUMORA_PACKAGE = "net.fixture.lumora"
        const val VEMBLIC_PACKAGE = "net.fixture.vemblic"
        const val MIRAVIN_PACKAGE = "net.fixture.miravin"
        const val OWN_PACKAGE = "net.ownward.shell"
    }
}

/** The `in_app` and `app_shortcut` adapters own nothing planned here; routing to one is a defect. */
private object LearnedSeamRefusingWorker : ToolWorker {
    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
        error("this adapter must not be asked to run ${invocation.id.value}")
}

/** `IntentLauncher` is a plain interface, not a `fun interface`, so the recorder is a class. */
private class LearnedSeamIntentRecorder(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) {
        record += intent
    }
}
