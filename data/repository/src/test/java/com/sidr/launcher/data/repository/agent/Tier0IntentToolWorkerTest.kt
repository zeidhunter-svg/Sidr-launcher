package com.sidr.launcher.data.repository.agent

import android.content.Intent
import android.provider.AlarmClock
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The `SYSTEM_INTENT` level's worker for the two Tier-0 tools: two Android intents that are not among
 * the frozen seven `ActionIds`, so this class is the whole of their execution — no `ExecuteActionUseCase`
 * involved.
 *
 * [FakeIntentLauncher] is the one seam an intent leaves through, so `parseSeconds` and the argument
 * wiring can be tested without a Robolectric activity. The class still runs under Robolectric because
 * it reads `Intent` extras, exactly like the other Android-touching tests in this package.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Tier0IntentToolWorkerTest {

    @Test
    fun `an unparseable duration fails closed and never issues an intent`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "soon")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `ten minutes becomes six hundred seconds and the clock UI is not skipped`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
        assertEquals(false, launched.single().getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }
}

private class FakeIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}
