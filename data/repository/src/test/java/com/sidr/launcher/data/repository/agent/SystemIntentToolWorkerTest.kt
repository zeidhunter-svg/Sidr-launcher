package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ObservedFact
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `SYSTEM_INTENT` tier: the agent's only path to the world runs through the **unchanged**
 * `ExecuteActionUseCase -> IntentActionResolver -> ActionExecutor` chain. No new executor surface.
 *
 * The load-bearing mapping is `Message(NoAppFound) -> ToolResult.Observed(APP_NOT_INSTALLED)`: what the
 * command pipeline treats as a dead end becomes the observation step 1's precondition reads.
 *
 * `launch_app` reports `resolved_query` on **every** result it can carry an output on (F6, Task 5b) —
 * `Effected` included, not only the `APP_NOT_INSTALLED` branch, because an output is a property of the
 * tool and not of the branch it happened to take.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SystemIntentToolWorkerTest {

    private val apps = FakeInstalledAppsRepository()
    private val actionExecutor = FakeActionExecutor()

    private fun executor() = SystemIntentToolWorker(
        ExecuteActionUseCase(IntentActionResolver(apps), actionExecutor),
    )

    @Test
    fun `launching a missing app is an observation, not a failure, and reports the resolved query`() = runTest {
        apps.appsToReturn = emptyList()

        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(
            ToolResult.Observed(ObservedFact.APP_NOT_INSTALLED, ToolOutput(mapOf("resolved_query" to "убер"))),
            result,
        )
    }

    @Test
    fun `launching an installed app effects it and still reports the resolved query`() = runTest {
        apps.appsToReturn = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))

        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер"))), result)
    }

    @Test
    fun `two apps with the same label are an ambiguity observation that also reports the resolved query`() = runTest {
        apps.appsToReturn = listOf(
            InstalledApp(packageName = "com.a", label = "убер", activityName = "Main"),
            InstalledApp(packageName = "com.b", label = "убер", activityName = "Main"),
        )

        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertEquals(
            ToolResult.Observed(ObservedFact.APP_AMBIGUOUS, ToolOutput(mapOf("resolved_query" to "убер"))),
            result,
        )
    }

    @Test
    fun `the store search effects and declares no output`() = runTest {
        val result = executor().invoke(ResolvedInvocation(ToolIds.PLAY_STORE_SEARCH, mapOf("query" to "убер")))

        assertEquals(ToolResult.Effected(ToolOutput()), result)
    }

    /**
     * The two fail-closed guards get one test each, and each is handed arguments that make it the
     * **only** guard standing between the invocation and the action path. The earlier single test gave
     * an unregistered id no `query` at all, so the blank-query check answered first and the
     * `when (invocation.id)` branch it was named for was never reached — replacing that branch with a
     * fall-through to `LaunchApp` left the whole suite green.
     */
    @Test
    fun `an unregistered tool id fails closed and never reaches the action path`() = runTest {
        apps.appsToReturn = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))

        val result = executor().invoke(ResolvedInvocation(ToolId("open_settings"), mapOf("query" to "убер")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, actionExecutor.executedActions.size)
    }

    @Test
    fun `a blank query fails closed and never reaches the action path`() = runTest {
        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "   ")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(0, actionExecutor.executedActions.size)
    }

    /**
     * `resolved_query` is what the launch **resolved against**, not the argument as it arrived: the
     * action is built from the trimmed value, so a later step binding to this output searches for
     * exactly the string that failed to match, character for character.
     */
    @Test
    fun `the reported resolved query is the value the launch actually resolved against`() = runTest {
        apps.appsToReturn = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))

        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "  убер  ")))

        assertEquals(ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер"))), result)
    }

    @Test
    fun `an executor failure is carried through as a safe failure`() = runTest {
        apps.appsToReturn = listOf(InstalledApp(packageName = "com.uber", label = "убер", activityName = "Main"))
        actionExecutor.resultToReturn = ActionExecutionResult.Failure(CommandFailure.Generic)

        val result = executor().invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер")))

        assertTrue(result is ToolResult.Failed)
    }

    /**
     * A4′ phase 3a, Task 1 (spec §7.12.3): the router's two actions with an executor become tools. The
     * worker reads each tool's OWN argument — `query` for a search, `url` for an address — through the
     * same unchanged `ExecuteActionUseCase` chain the launch has always used.
     */
    @Test
    fun `a web search effects with the query it was given and declares no output`() = runTest {
        val result = executor().invoke(
            ResolvedInvocation(InAppToolIds.WEB_SEARCH, mapOf("query" to "ostravel lectures")),
        )

        assertEquals(ToolResult.Effected(), result)
        val search = actionExecutor.executedActions.single() as ExecutableAction.OpenSearchAction
        assertEquals("ostravel lectures", search.query)
    }

    @Test
    fun `opening a url opens that address, never a search for it`() = runTest {
        val url = "https://quillmere.example/path?q=a&b=c"

        val result = executor().invoke(ResolvedInvocation(InAppToolIds.OPEN_URL, mapOf("url" to url)))

        assertEquals(ToolResult.Effected(), result)
        assertEquals(listOf(ExecutableAction.OpenUrlAction(url)), actionExecutor.executedActions)
    }

    @Test
    fun `a blank url fails closed and never reaches the action path`() = runTest {
        val result = executor().invoke(ResolvedInvocation(InAppToolIds.OPEN_URL, mapOf("url" to "  ")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertTrue(actionExecutor.executedActions.isEmpty())
    }

    /**
     * Final review A3: the address is the MODEL's, and `ACTION_VIEW` opens whatever app claims a scheme —
     * a payment or a deep link, not a page. FastPath's `UrlDetector` never sees this path, so the worker,
     * below the consent card, is where the web-only rule holds: anything but `http`/`https` fails closed
     * and nothing is opened. Each form here is refused by the scheme check alone — none is blank.
     */
    @Test
    fun `a url that is not http or https fails closed and opens nothing`() = runTest {
        listOf("quillpay://ostravel", "quillpay:ostravel", "ostravel.example/lectures").forEach { url ->
            val result = executor().invoke(ResolvedInvocation(InAppToolIds.OPEN_URL, mapOf("url" to url)))

            assertEquals("'$url' must fail closed", ToolResult.Failed(CommandFailure.Generic), result)
        }
        assertTrue("nothing reached the action path", actionExecutor.executedActions.isEmpty())
    }

    @Test
    fun `a pinned launch opens the pinned package even when two apps share the label`() = runTest {
        apps.appsToReturn = listOf(
            InstalledApp(packageName = "com.a", label = "убер", activityName = "Main"),
            InstalledApp(packageName = "com.b", label = "убер", activityName = "Main"),
        )

        val result = executor().invoke(
            ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "убер", "app" to "com.b")),
        )

        assertEquals(ToolResult.Effected(ToolOutput(mapOf("resolved_query" to "убер"))), result)
        assertEquals(listOf(ExecutableAction.LaunchAppAction("com.b", "Main")), actionExecutor.executedActions)
    }
}
