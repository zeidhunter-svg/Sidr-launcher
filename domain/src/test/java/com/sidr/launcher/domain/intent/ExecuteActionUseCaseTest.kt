package com.sidr.launcher.domain.intent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.result.OperationError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AIL-5: executing a confirmed router-proposed [LauncherAction]. The use case maps each family to a
 * [LauncherIntent], resolves it through the **unchanged** [IntentActionResolver], and executes the
 * side-effecting result through the [ActionExecutor] — the same proven path the rule pipeline uses.
 * Navigation/UI-only families never touch the executor. It never throws.
 */
class ExecuteActionUseCaseTest {

    private val repo = FakeInstalledAppsRepository()
    private val executor = FakeActionExecutor()
    private val useCase = ExecuteActionUseCase(
        resolver = IntentActionResolver(repo),
        executor = executor,
    )

    @Test
    fun `LaunchApp with a single match executes and reports Executed`() = runTest {
        repo.appsToReturn = listOf(InstalledApp("org.telegram.messenger", "Telegram"))

        val outcome = useCase.execute(LauncherAction.LaunchApp("Telegram"))

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(1, executor.callCount)
        assertEquals(
            ExecutableAction.LaunchAppAction("org.telegram.messenger", null),
            executor.executedActions.single(),
        )
    }

    @Test
    fun `LaunchApp with no match reports a not-found Message and never executes`() = runTest {
        repo.appsToReturn = emptyList()

        val outcome = useCase.execute(LauncherAction.LaunchApp("Nope"))

        assertTrue("expected Message, got $outcome", outcome is CommandOutcome.Message)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun `LaunchApp with two matches reports NeedsConfirmation and never executes`() = runTest {
        val a = InstalledApp("com.a.maps", "Maps")
        val b = InstalledApp("com.b.maps", "Maps")
        repo.appsToReturn = listOf(a, b)

        val outcome = useCase.execute(LauncherAction.LaunchApp("Maps"))

        assertTrue("expected NeedsConfirmation, got $outcome", outcome is CommandOutcome.NeedsConfirmation)
        assertEquals(listOf(a, b), (outcome as CommandOutcome.NeedsConfirmation).candidates)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun `WebSearch executes an OpenSearchAction`() = runTest {
        val outcome = useCase.execute(LauncherAction.WebSearch("weather"))

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(
            ExecutableAction.OpenSearchAction("weather", SearchTarget.WEB),
            executor.executedActions.single(),
        )
    }

    @Test
    fun `OpenUrl executes an OpenUrlAction`() = runTest {
        val outcome = useCase.execute(LauncherAction.OpenUrl("https://example.com"))

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(
            ExecutableAction.OpenUrlAction("https://example.com"),
            executor.executedActions.single(),
        )
    }

    @Test
    fun `PlayStoreSearch executes a PlayStoreSearchAction`() = runTest {
        val outcome = useCase.execute(LauncherAction.PlayStoreSearch("signal"))

        assertEquals(CommandOutcome.Executed, outcome)
        assertEquals(
            ExecutableAction.PlayStoreSearchAction("signal"),
            executor.executedActions.single(),
        )
    }

    @Test
    fun `OpenSettings navigates without touching the executor`() = runTest {
        val outcome = useCase.execute(LauncherAction.OpenSettings)

        assertEquals(CommandOutcome.OpenSettings, outcome)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun `OpenAssistant navigates without touching the executor`() = runTest {
        val outcome = useCase.execute(LauncherAction.OpenAssistant("hi"))

        assertEquals(CommandOutcome.OpenAssistant, outcome)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun `ShowApps routes to the app grid without touching the executor`() = runTest {
        val outcome = useCase.execute(LauncherAction.ShowApps)

        assertEquals(CommandOutcome.ShowApps, outcome)
        assertTrue(executor.executedActions.isEmpty())
    }

    @Test
    fun `an execution failure maps to Failed with the executor's safe message`() = runTest {
        executor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.CantOpenUrl)

        val outcome = useCase.execute(LauncherAction.OpenUrl("https://example.com"))

        assertEquals(CommandOutcome.Failed(CommandFailure.CantOpenUrl), outcome)
    }

    @Test
    fun `a resolver repository failure maps to Failed and never executes`() = runTest {
        repo.errorToReturn = OperationError.UnknownError("io")

        val outcome = useCase.execute(LauncherAction.LaunchApp("Telegram"))

        assertTrue("expected Failed, got $outcome", outcome is CommandOutcome.Failed)
        assertTrue(executor.executedActions.isEmpty())
    }
}
