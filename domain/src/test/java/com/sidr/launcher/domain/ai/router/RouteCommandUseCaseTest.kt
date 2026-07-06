package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeCommandPlanner
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.intent.CommandOutcome
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.intent.LauncherIntent
import com.sidr.launcher.domain.intent.SimpleCommand
import com.sidr.launcher.domain.preferences.FeatureFlags
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AIL-4 routing composition (Forks R1/R2/R4). The critical guards: **router-off / confident-rule /
 * offline ⇒ the planner is never consulted and the rule outcome is returned byte-for-byte**, and a
 * planner proposal is surfaced as a non-executing [CommandOutcome.RoutedAction].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RouteCommandUseCaseTest {

    private val matcher = FakeIntentMatcher()
    private val appsRepo = FakeInstalledAppsRepository()
    private val executor = FakeActionExecutor()
    private val planner = FakeCommandPlanner()
    private val connectivity = FakeConnectivityChecker(initiallyOnline = true)

    private val catalog: ActionCatalog = object : ActionCatalog {
        private val list = listOf(
            ActionDescriptor(
                id = ActionIds.OPEN_URL,
                title = "Open link",
                description = "Open a web address",
                category = ActionCategory.WEB,
                risk = ActionRiskLevel.CONFIRM,
                argSchema = listOf(ActionArg("url", description = "url")),
            ),
            ActionDescriptor(
                id = ActionIds.SHOW_APPS,
                title = "All apps",
                description = "Show the app grid",
                category = ActionCategory.SYSTEM,
                risk = ActionRiskLevel.SAFE,
            ),
        )
        override fun all(): List<ActionDescriptor> = list
        override fun descriptor(id: ActionId): ActionDescriptor? = list.firstOrNull { it.id == id }
    }

    private fun useCase(routerEnabled: Boolean): RouteCommandUseCase = RouteCommandUseCase(
        handleUserCommand = HandleUserCommandUseCase(
            matcher = matcher,
            resolver = IntentActionResolver(appsRepo),
            executor = executor,
            confidencePolicy = DefaultIntentConfidencePolicy(),
            recordingScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()),
        ),
        planner = planner,
        catalog = catalog,
        featureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(llmRouterEnabled = routerEnabled)),
        connectivityChecker = connectivity,
    )

    private fun driveUnknown() {
        matcher.intentToReturn = LauncherIntent.UnknownIntent(originalInput = "do a barrel roll", reason = "x")
        matcher.confidenceToReturn = 0.0f
    }

    private fun driveConfidentShowApps() {
        matcher.intentToReturn = LauncherIntent.SimpleCommandIntent(SimpleCommand.SHOW_APPS)
        matcher.confidenceToReturn = 0.99f
    }

    @Test
    fun `router off - returns rule outcome and never consults planner (parity)`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.9f)

        val outcome = useCase(routerEnabled = false).route("do a barrel roll")

        assertEquals(CommandOutcome.Unknown("do a barrel roll"), outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `router on but rule confident - planner never consulted (R1)`() = runTest {
        driveConfidentShowApps()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.9f)

        val outcome = useCase(routerEnabled = true).route("apps")

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `router on and offline - keeps rule outcome, planner never consulted`() = runTest {
        driveUnknown()
        connectivity.online = false
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.9f)

        val outcome = useCase(routerEnabled = true).route("do a barrel roll")

        assertEquals(CommandOutcome.Unknown("do a barrel roll"), outcome)
        assertEquals(0, planner.planCallCount)
    }

    @Test
    fun `router on, low confidence, planner NoPlan - keeps rule outcome`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.NoPlan

        val outcome = useCase(routerEnabled = true).route("do a barrel roll")

        assertEquals(CommandOutcome.Unknown("do a barrel roll"), outcome)
        assertEquals(1, planner.planCallCount)
    }

    @Test
    fun `router on - CONFIRM-risk proposal surfaces as needsConfirmation RoutedAction`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.8f)

        val outcome = useCase(routerEnabled = true).route("go to x.test")

        assertEquals(
            CommandOutcome.RoutedAction(LauncherAction.OpenUrl("https://x.test"), 0.8f, needsConfirmation = true),
            outcome,
        )
        assertEquals("go to x.test", planner.lastCommand)
    }

    @Test
    fun `router on - SAFE proposal surfaces as one-tap RoutedAction (no confirm)`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.RoutedAction(LauncherAction.ShowApps, 0.7f)

        val outcome = useCase(routerEnabled = true).route("show me everything")

        assertEquals(
            CommandOutcome.RoutedAction(LauncherAction.ShowApps, 0.7f, needsConfirmation = false),
            outcome,
        )
    }

    @Test
    fun `router on - Clarify becomes a Message`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.Clarify("Which app did you mean?")

        val outcome = useCase(routerEnabled = true).route("open the thing")

        assertEquals(CommandOutcome.Message("Which app did you mean?"), outcome)
    }

    @Test
    fun `rule path runs exactly once even when planner is consulted`() = runTest {
        driveUnknown()
        planner.resultToReturn = PlanResult.NoPlan

        useCase(routerEnabled = true).route("do a barrel roll")

        // handle() → matcher.match() exactly once: the router wraps, never double-runs the rule path.
        assertEquals(1, matcher.callCount)
    }
}
