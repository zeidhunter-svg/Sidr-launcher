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

    /**
     * Fix round 1 (controller ruling R11): a `startsWith` unit match let a multi-word tail get read as
     * its first word — `"1 min 30 sec"` silently became 60 seconds, not 90. The fix requires the token
     * after the digits to be a single word, so any embedded whitespace fails closed instead.
     */
    @Test
    fun `a duration with a second number-unit pair fails closed rather than reading only the first word`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "1 min 30 sec")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    /**
     * Fix round 1: a `startsWith` unit match let `"minecraft"` match the `"min"` prefix and silently
     * start a real 600-second timer from nonsense. Exact (`==`) matching against the unit list closes
     * this: an unlisted word is declined, not guessed.
     */
    @Test
    fun `an unlisted word that merely starts with a unit prefix fails closed rather than matching it`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minecraft")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero with no unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero minutes fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a five-digit amount fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "12345")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a unit word with no leading number fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an unrecognised unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 fortnights")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `trailing junk after a valid unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes now")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an accepted russian minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 минут")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `an accepted turkish minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched))

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 dakika")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }
}

private class FakeIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}
