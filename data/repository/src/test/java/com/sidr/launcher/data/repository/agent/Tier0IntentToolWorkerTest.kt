package com.sidr.launcher.data.repository.agent

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
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

    private class FakePresence(private val granted: Set<String>) : PermissionPresence {
        override fun isGranted(permission: String): Boolean = permission in granted
    }

    /**
     * Task 3 made this worker re-check its permissions immediately before dispatch, so every
     * construction now states which presence it runs under. **This fixture is load-bearing, not a
     * convenience.** Every test below it is about duration parsing or about the exception backstop,
     * and a presence that granted nothing would make all of them fail at the new precondition instead
     * — passing on their `Failed`/`launched.isEmpty()` assertions while proving nothing about the
     * thing they name. Granting everything keeps the precondition out of their way. A presence that
     * withholds belongs only to the two tests whose subject *is* the precondition.
     */
    private val grantsEverything = PermissionPresence { true }

    @Test
    fun `an unparseable duration fails closed and never issues an intent`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "soon")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `ten minutes becomes six hundred seconds and the clock UI is not skipped`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

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
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

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
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minecraft")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero with no unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero minutes fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a five-digit amount fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "12345")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a unit word with no leading number fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an unrecognised unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 fortnights")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `trailing junk after a valid unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes now")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an accepted russian minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 минут")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `an accepted turkish minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), grantsEverything)

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 dakika")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    /**
     * Final whole-branch review, finding 1 (CRITICAL). `ContextIntentLauncher.launch` called
     * `context.startActivity` bare and **nothing above it catches**: `AgentExecutor.perform`'s one call
     * site has no `try`, `RunAgentSessionUseCase.run` has none, and `LauncherAgentSession.attach` runs
     * on `viewModelScope` with no `CoroutineExceptionHandler`. On a device with no activity registered
     * for `AlarmClock.ACTION_SET_TIMER` — an AOSP or ROM build, or Deskclock disabled — a typed
     * "set a timer for 10 minutes" reaches a one-step **SAFE** plan, so no consent gate stops it, and
     * the home-screen process dies.
     *
     * This survived because Task 6 tested the worker against a launcher that cannot throw, and
     * `ContextIntentLauncher` had no tests at all. The catch is [Tier0IntentToolWorker]'s, not the
     * launcher's — see the class KDoc for which contract that makes load-bearing.
     */
    @Test
    fun `a missing timer activity fails the tool result instead of propagating`() = runTest {
        val worker = Tier0IntentToolWorker(
            ThrowingIntentLauncher { ActivityNotFoundException("no timer app") },
            ToolPermissionCatalog(),
            grantsEverything,
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
    }

    /** `SecurityException` has the same shape and the same fix — the pair `AndroidActionExecutor` catches. */
    @Test
    fun `a refused timer launch fails the tool result instead of propagating`() = runTest {
        val worker = Tier0IntentToolWorker(
            ThrowingIntentLauncher { SecurityException("not allowed") },
            ToolPermissionCatalog(),
            grantsEverything,
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
    }

    /**
     * The zero-argument tool takes the same launch path, so it needs the same proof: the catch has to
     * cover every intent this worker issues, not only the one whose argument is parsed.
     */
    @Test
    fun `a missing settings activity fails the tool result instead of propagating`() = runTest {
        val worker = Tier0IntentToolWorker(
            ThrowingIntentLauncher { ActivityNotFoundException("no settings app") },
            ToolPermissionCatalog(),
            grantsEverything,
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.OPEN_SYSTEM_SETTINGS, emptyMap()))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
    }

    /**
     * [ContextIntentLauncher]'s first test, and the reason it needed one beyond finding 1's catch:
     * `startActivity` on an **application** context without `FLAG_ACTIVITY_NEW_TASK` throws
     * `AndroidRuntimeException` — a third crash mode, and one the worker's catch deliberately does
     * NOT cover, because it is a wiring error rather than a state of the device. The flag is what makes
     * that branch unreachable, and until now nothing pinned it: this class was reachable only through
     * the graph, and every worker test used a fake launcher instead.
     *
     * Asserted through the shadow's recorded intent rather than by expecting no throw, so removing the
     * flag turns this RED on the flag itself rather than on a Robolectric leniency that may change.
     */
    @Test
    fun `the real launcher adds NEW_TASK, without which an application context cannot start an activity`() {
        val context = ApplicationProvider.getApplicationContext<Application>()

        ContextIntentLauncher(context).launch(Intent(Settings.ACTION_SETTINGS))

        val started = shadowOf(context).nextStartedActivity
        assertEquals(Settings.ACTION_SETTINGS, started.action)
        assertEquals(
            Intent.FLAG_ACTIVITY_NEW_TASK,
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK,
        )
    }

    /**
     * The narrower window Task 2's source filter cannot cover: state can move between the snapshot the
     * source answered from and the call.
     *
     * **What this proves, and for which refusal mode.** `set_timer` is refused *framework-side* —
     * `ActivityTaskManager` throws `SecurityException` before dispatch (row 27) — so on this tool the
     * catch would also produce `Failed` and the precondition is the earlier of two working signals.
     * What the test pins is that the step is `Failed` **and the launcher is never reached at all**,
     * which the catch alone cannot give. That second half is what a *responder-side* tool will depend
     * on entirely: there `startActivity` returns normally and throws nothing whether the responder
     * refuses or acts (row 32, `ACTION_DELETE`), so nothing after the fact can tell the two apart.
     */
    @Test
    fun `an ungranted permission fails the step and never reaches the launcher`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker =
            Tier0IntentToolWorker(FakeIntentLauncher(launched), ToolPermissionCatalog(), FakePresence(emptySet()))

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "5 minutes")))

        assertTrue(result is ToolResult.Failed)
        assertEquals(
            "Row 32's invisible-afterwards refusal is ACTION_DELETE's, not this tool's: an " +
                "alarm-permission intent is refused framework-side and DOES throw SecurityException " +
                "(row 27), so for set_timer the catch would answer too and this precondition is the " +
                "earlier of two working signals — while for a responder-side tool it is the only " +
                "signal there is. Either way a step that could not run must not report Effected, " +
                "because DOC-ILM-3 requires the trace to be 1:1 with reality.",
            0,
            launched.size,
        )
    }

    /** The other half: the precondition declines nothing it should not, so a held permission still runs. */
    @Test
    fun `a granted permission still dispatches`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = Tier0IntentToolWorker(
            FakeIntentLauncher(launched),
            ToolPermissionCatalog(),
            FakePresence(setOf("com.android.alarm.permission.SET_ALARM")),
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "5 minutes")))

        assertTrue(result is ToolResult.Effected)
        assertEquals(1, launched.size)
    }
}

/** The seam as the world can actually behave: `startActivity` throws and the worker must absorb it. */
private class ThrowingIntentLauncher(private val thrown: () -> RuntimeException) : IntentLauncher {
    override fun launch(intent: Intent): Nothing = throw thrown()
}

private class FakeIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}
