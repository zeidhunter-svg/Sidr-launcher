package com.sidr.launcher.data.repository.intent

import com.sidr.launcher.core.testing.FakeActionExecutor
import com.sidr.launcher.core.testing.FakeFeatureFlagRepository
import com.sidr.launcher.core.testing.FakeInstalledAppsRepository
import com.sidr.launcher.core.testing.FakeUsageHistoryRepository
import com.sidr.launcher.data.repository.agent.SystemIntentToolWorker
import com.sidr.launcher.domain.intent.ActionExecutionResult
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.intent.ExecutableAction
import com.sidr.launcher.domain.intent.ExecuteActionUseCase
import com.sidr.launcher.domain.intent.IntentActionResolver
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.preferences.FeatureFlagRepository
import com.sidr.launcher.domain.preferences.FeatureFlags
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * A4′ tail fix round, task 2 (phase 5): a launch is recorded in ONE place — this decorator over the
 * [com.sidr.launcher.domain.intent.ActionExecutor] — so a launch by command (through the agent engine) fills Home
 * favourites exactly as a grid tap does. The ViewModels no longer write usage.
 */
class UsageRecordingActionExecutorTest {

    private val delegate = FakeActionExecutor()
    private val usage = FakeUsageHistoryRepository()
    private val launch = ExecutableAction.LaunchAppAction(packageName = "org.telegram.messenger", activityName = "Main")

    private fun executor(
        flags: FeatureFlagRepository = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = true)),
        now: Long = 42_000L,
            ) = UsageRecordingActionExecutor(delegate = delegate, usage = usage, flags = flags, nowMs = { now })

    @Test
    fun `a successful launch is recorded`() = runTest {
        val result = executor().execute(launch)

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(listOf("org.telegram.messenger"), usage.recordedLaunches)
        assertEquals(42_000L, usage.getUsageRecords().first().single().lastUsedEpochMs)
    }

    @Test
    fun `a failed launch is not recorded`() = runTest {
        val failure = ActionExecutionResult.Failure(CommandFailure.CantOpenApp)
        delegate.resultToReturn = failure

        val result = executor().execute(launch)

        assertEquals(failure, result)
        assertTrue(usage.recordedLaunches.isEmpty())
    }

    @Test
    fun `with usage history off nothing is recorded`() = runTest {
        val result = executor(flags = FakeFeatureFlagRepository(FeatureFlags(usageHistoryEnabled = false))).execute(launch)

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(1, delegate.callCount)
        assertTrue(usage.recordedLaunches.isEmpty())
    }

    @Test
    fun `a non-launch action is not recorded`() = runTest {
        val result = executor().execute(ExecutableAction.OpenUrlAction("https://example.com"))

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(1, delegate.callCount)
        assertTrue(usage.recordedLaunches.isEmpty())
    }

    @Test
    fun `a failing record never changes the launch result`() = runTest {
        usage.errorToReturn = OperationError.UnknownError("db down")

        val result = executor().execute(launch)

        assertEquals(ActionExecutionResult.Success, result)
        assertEquals(1, delegate.callCount)
    }

    @Test
    fun `a flag read that throws never changes the launch result`() = runTest {
        val throwing = object : FeatureFlagRepository {
            override fun getFlags(): Flow<FeatureFlags> = flow { throw RuntimeException("flag read error") }
            override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> = OperationResult.Success(Unit)
        }

        val result = executor(flags = throwing).execute(launch)

        assertEquals(ActionExecutionResult.Success, result)
        assertTrue(usage.recordedLaunches.isEmpty())
    }

    @Test
    fun `a cancellation while recording is not swallowed`() = runTest {
        val cancelling = object : FeatureFlagRepository {
            override fun getFlags(): Flow<FeatureFlags> = flow { throw CancellationException("cancelled") }
            override suspend fun updateFlags(flags: FeatureFlags): OperationResult<Unit> = OperationResult.Success(Unit)
        }

        try {
            executor(flags = cancelling).execute(launch)
            fail("a CancellationException must propagate")
        } catch (_: CancellationException) {
            // expected
        }
    }

    /**
     * Focus 1 of the review: the grid tap reaches the executor once and the decorator writes once — the ViewModels
     * hold no usage repository to write a second row.
     */
    @Test
    fun `one launch through the executor is one recorded launch`() = runTest {
        val e = executor()

        e.execute(launch)

        assertEquals(1, delegate.callCount)
        assertEquals(listOf("org.telegram.messenger"), usage.recordedLaunches)
        assertEquals(1, usage.getUsageRecords().first().single().launchCount)
    }

    /**
     * The device evidence: a launch by command writes no `app_usage` row. The chain is the production one the agent
     * engine reaches — `SystemIntentToolWorker → ExecuteActionUseCase → IntentActionResolver → executor` — with the
     * decorator as the executor; only the platform launcher below it is faked.
     */
    @Test
    fun `a launch through the engine is recorded`() = runTest {
        val apps = FakeInstalledAppsRepository().apply {
            appsToReturn = listOf(InstalledApp(packageName = "org.telegram.messenger", label = "Telegram", activityName = "Main"))
        }
        val worker = SystemIntentToolWorker(
            ExecuteActionUseCase(resolver = IntentActionResolver(apps), executor = executor()),
        )

        val result = worker.invoke(ResolvedInvocation(ToolIds.LAUNCH_APP, mapOf("query" to "Telegram")))

        assertTrue("expected Effected, was $result", result is ToolResult.Effected)
        assertEquals(1, delegate.callCount)
        assertEquals(listOf("org.telegram.messenger"), usage.recordedLaunches)
    }
}
