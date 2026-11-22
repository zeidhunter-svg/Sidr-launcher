package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeAliasStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.di.BridgeProvidesModule
import com.sidr.launcher.di.IntentProvidesModule
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.text.PlainTextFolding
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * **The two readers of aliases are built WITH the store they are given** (A4′ acceptance tail, task 3; owner
 * decision 3, K10). FastPath's launch ([IntentProvidesModule.provideIntentActionResolver]) and the route's step 1a
 * ([BridgeProvidesModule.provideRouteCommandUseCase]) take the alias store — and the route the app list — with a
 * Kotlin default (`AliasStore.NONE`, no list), so the hand-built calls of the older guards still compile. Hilt passes
 * every parameter, but a provider BODY that dropped one would compile, ship «no aliases» and leave every per-path test
 * green, since those build the use cases themselves. Read the `TextFoldingBindingGuardTest` way: by calling the
 * modules' own `@Provides` functions with a planted alias.
 */
class AliasBindingGuardTest {

    private val telegramPlus = InstalledApp("org.telegram.plus", "Plus Messenger", activityName = ".Main")
    private val apps = FakeInstalledAppsRepository().apply { appsToReturn = listOf(telegramPlus) }

    private suspend fun aliased(phrase: String): FakeAliasStore = FakeAliasStore().apply {
        upsert(Alias(phrase, AliasTarget.App(telegramPlus.packageName), createdAtEpochMs = 0L))
    }

    @Test
    fun `FastPath's resolver is built with the alias store it is given`() = runTest {
        val resolver = IntentProvidesModule.provideIntentActionResolver(apps, PlainTextFolding, aliased("tg"))

        assertEquals(
            OperationResult.Success(ExecutableAction.LaunchAppAction("org.telegram.plus", ".Main")),
            resolver.resolve(LauncherIntent.LaunchAppIntent("tg")),
        )
    }

    /**
     * Review round 1, Minor 3: step 1a spells `launch_app`'s two arguments as literals in `:domain` (`"query"`,
     * `"app"` — `SystemIntentToolSource.PIN_ARG` lives in `:data:repository`), and its plan is validated against the
     * registry it starts in. That registry is the PRODUCTION one here ([ProductionGraph.federation]), so a rename on
     * either side makes the plan unrunnable — step 1a answers `null`, the model is asked — and this test goes red,
     * instead of every per-path test passing over `FakeToolRegistry.withA0Tools()`'s copy of the names.
     */
    @Test
    fun `the route is built with the alias store and the app list it is given`() = runTest {
        var asked = 0
        val store = FakeAgentSessionStore()
        val route = BridgeProvidesModule.provideRouteCommandUseCase(
            handleUserCommand = HandleUserCommandUseCase(
                matcher = FakeIntentMatcher(),
                resolver = IntentActionResolver(apps),
                executor = FakeActionExecutor(),
                confidencePolicy = DefaultIntentConfidencePolicy(),
                recordingScope = CoroutineScope(Dispatchers.Unconfined),
            ),
            modelPlanner = ModelPlanner { asked++; ModelPlanning.Declined },
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
            startAgentSession = StartAgentSessionUseCase(
                TemplatePlanner(),
                store,
                object : AgentSessionIdFactory {
                    override fun newId() = AgentSessionId("alias-guard")
                },
                ProductionGraph.federation().registry,
            ),
            learnedPlans = LearnedPlans.NONE,
            aliases = aliased("tg"),
            installedApps = apps,
        )

        assertEquals(CommandOutcome.AgentSessionStarted(AgentSessionId("alias-guard")), route.route("tg"))
        assertEquals("the model is never asked for an alias", 0, asked)
        assertEquals(
            "the pinned launch, in the production registry's own argument names",
            listOf(
                ToolInvocation(
                    ToolIds.LAUNCH_APP,
                    mapOf("query" to ArgSource.Literal("tg"), "app" to ArgSource.Literal("org.telegram.plus")),
                ),
            ),
            store.saved.single().plan.steps.map { it.invocation },
        )
    }
}
