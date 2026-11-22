package com.sidr.launcher.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeAgentSessionStore
import com.sidr.launcher.core.testing.FakeConnectivityChecker
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeIntentMatcher
import com.sidr.launcher.core.testing.FakeMentionBinder
import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.core.testing.configuredProvider
import com.sidr.launcher.di.BridgeProvidesModule
import com.sidr.launcher.domain.agent.AgentSessionId
import com.sidr.launcher.domain.agent.AgentSessionIdFactory
import com.sidr.launcher.domain.agent.StartAgentSessionUseCase
import com.sidr.launcher.domain.agent.TemplatePlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanner
import com.sidr.launcher.domain.ai.bridge.ModelPlanning
import com.sidr.launcher.domain.intent.DefaultIntentConfidencePolicy
import com.sidr.launcher.domain.intent.HandleUserCommandUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.memory.plan.LearnedPlans
import com.sidr.launcher.feature.launcher.ModelAskedEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * **The route asks the model under the eyes of the one signal the launcher watches** (A4′ acceptance tail, task 6;
 * controller ruling K9). «Разбираю команду…» is shown on [ModelAskedEvents] — the `@Singleton` the launcher's
 * ViewModel injects — and the route raises it through the domain port, which `:app` binds to that same instance
 * ([BridgeProvidesModule.provideModelAskedSignal]). [BridgeProvidesModule.provideRouteCommandUseCase] takes the port
 * with a Kotlin default (`ModelAskedSignal.NONE`), so the hand-built calls of the older guards still compile; Hilt
 * passes every parameter, but a provider BODY that dropped it would compile, ship «no wait, ever» and leave every
 * per-path test green, since those build the route themselves. Read the [AliasBindingGuardTest] way: by calling the
 * module's own `@Provides` functions, and watching the launcher's side of the signal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ModelAskedSignalBindingGuardTest {

    @Test
    fun `the port the route is given is the launcher's own events`() {
        val events = ModelAskedEvents()

        assertSame(events, BridgeProvidesModule.provideModelAskedSignal(events))
    }

    @Test
    fun `the route is built with the signal it is given, and raises it right before the model`() = runTest {
        val log = mutableListOf<String>()
        val events = ModelAskedEvents()
        val watcher = CoroutineScope(UnconfinedTestDispatcher(testScheduler)).launch {
            events.events.collect { log += "signal" }
        }
        val route = BridgeProvidesModule.provideRouteCommandUseCase(
            // FastPath decides nothing (the fake matcher's default `Unknown`), so the command goes to step 7.
            handleUserCommand = HandleUserCommandUseCase(
                matcher = FakeIntentMatcher(),
                resolver = IntentActionResolver(FakeInstalledAppsRepository()),
                executor = FakeActionExecutor(),
                confidencePolicy = DefaultIntentConfidencePolicy(),
                recordingScope = CoroutineScope(Dispatchers.Unconfined),
            ),
            modelPlanner = ModelPlanner {
                log += "model"
                ModelPlanning.Declined
            },
            binder = FakeMentionBinder(),
            featureFlagRepository = FakeFeatureFlagRepository(),
            providerConfigRepository = configuredProvider(),
            connectivityChecker = FakeConnectivityChecker(initiallyOnline = true),
            startAgentSession = StartAgentSessionUseCase(
                TemplatePlanner(),
                FakeAgentSessionStore(),
                object : AgentSessionIdFactory {
                    override fun newId() = AgentSessionId("model-asked-guard")
                },
                FakeToolRegistry.withA0Tools(),
            ),
            learnedPlans = LearnedPlans.NONE,
            modelAsked = BridgeProvidesModule.provideModelAskedSignal(events),
        )

        route.route("сделай что-нибудь хорошее")

        assertEquals("the launcher saw the signal, once, before the model was asked", listOf("signal", "model"), log)
        watcher.cancel()
    }
}
