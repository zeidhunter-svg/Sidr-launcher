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
import org.junit.Assert.assertFalse
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

    /**
     * Task 7 gave this worker a fourth constructor argument — our own package name, for the
     * self-uninstall refusal — so the construction moved here rather than being repeated at every
     * call site.
     *
     * Two things it deliberately does **not** hide. The presence stays an explicit argument, because
     * which fixture a test runs under is load-bearing rather than incidental (see [grantsEverything]).
     * And the catalog is the **production** [ToolPermissionCatalog], not a fake: the permission
     * precondition these tests exercise is only meaningful against the real rows, and a fake catalog
     * here would make every one of them agree with itself.
     */
    private fun workerWith(launcher: IntentLauncher, presence: PermissionPresence) =
        Tier0IntentToolWorker(launcher, ToolPermissionCatalog(), presence, OWN_PACKAGE)

    @Test
    fun `an unparseable duration fails closed and never issues an intent`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "soon")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `ten minutes becomes six hundred seconds and the clock UI is not skipped`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

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
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

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
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minecraft")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero with no unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `zero minutes fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "0 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a five-digit amount fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "12345")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `a unit word with no leading number fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an unrecognised unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 fortnights")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `trailing junk after a valid unit fails closed`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes now")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(emptyList<Intent>(), launched)
    }

    @Test
    fun `an accepted russian minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 минут")))

        assertEquals(600, launched.single().getIntExtra(AlarmClock.EXTRA_LENGTH, -1))
    }

    @Test
    fun `an accepted turkish minute form resolves to the right number of seconds`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

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
        val worker = workerWith(
            ThrowingIntentLauncher { ActivityNotFoundException("no timer app") },
            grantsEverything,
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "10 minutes")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
    }

    /** `SecurityException` has the same shape and the same fix — the pair `AndroidActionExecutor` catches. */
    @Test
    fun `a refused timer launch fails the tool result instead of propagating`() = runTest {
        val worker = workerWith(
            ThrowingIntentLauncher { SecurityException("not allowed") },
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
        val worker = workerWith(
            ThrowingIntentLauncher { ActivityNotFoundException("no settings app") },
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
            workerWith(FakeIntentLauncher(launched), FakePresence(emptySet()))

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
        val worker = workerWith(
            FakeIntentLauncher(launched),
            FakePresence(setOf("com.android.alarm.permission.SET_ALARM")),
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_TIMER, mapOf("duration" to "5 minutes")))

        assertTrue(result is ToolResult.Effected)
        assertEquals(1, launched.size)
    }

    /**
     * Task 5. `set_alarm` shares `set_timer`'s permission (`com.android.alarm.permission.SET_ALARM`,
     * row 27) but a different intent and a different argument shape: a clock time, not a duration.
     * `EXTRA_SKIP_UI = false` is asserted for the same reason it is on `set_timer` — row 13/29 measured
     * that `ACTION_SET_ALARM` creates the alarm **already enabled** regardless of this flag, so it is
     * not "prefilled but not sent"; the flag only governs whether the responder draws its own UI over an
     * act that has already happened.
     */
    @Test
    fun `set_alarm sends hour and minutes and does not skip the responder's UI`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(
            FakeIntentLauncher(launched),
            FakePresence(ALARM_GRANTED),
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_ALARM, mapOf("time" to "7:30")))

        assertTrue(result is ToolResult.Effected)
        val intent = launched.single()
        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(7, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(30, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertFalse(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, true))
    }

    /**
     * Bounded token, `H:MM`/`HH:MM` only (spec §7.1) — never a guess, never a default. An agent that
     * silently guesses a clock time is worse than one that declines and says so, the same reasoning
     * `parseSeconds` already carries for durations.
     */
    @Test
    fun `an unparseable time declines instead of guessing`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(
            FakeIntentLauncher(launched),
            FakePresence(ALARM_GRANTED),
        )

        listOf("", "tomorrow", "25:00", "7:75", "7", "7:3o").forEach { raw ->
            val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.SET_ALARM, mapOf("time" to raw)))
            assertTrue("accepted a time it should decline: '$raw'", result is ToolResult.Failed)
        }
        assertEquals(0, launched.size)
    }

    /**
     * Task 7 (A1″ Phase 3a) — the track's first `CONFIRM` tool, and the first one whose argument is a
     * **package name resolved before the consent gate** rather than text this worker reads.
     *
     * The permission is granted by name rather than by [grantsEverything], so the assertion also
     * covers the catalog row: a tool registered without one is withheld by the source and refused by
     * the precondition, and a test run under "everything is granted" could not tell that apart.
     *
     * `Uri.fromParts` rather than `Uri.parse` is the shape `Tier0IntentProbe` fired on the SM-A325F
     * when rows 16/28/32 were measured, so what ships is byte-identical to what was measured.
     */
    @Test
    fun `a package dispatches ACTION_DELETE for exactly that package`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), FakePresence(DELETE_GRANTED))

        val result = worker.invoke(
            ResolvedInvocation(
                Tier0ToolIds.UNINSTALL_APP,
                mapOf("app" to "org.telegram.messenger", "app_label" to "telegram"),
            ),
        )

        // Task 5 (A4′ phase 0): uninstall_app no longer claims Effected for a raised dialog whose
        // outcome it cannot see (A1″ acceptance finding (a)) — it reports HandedOff. This assertion
        // changed from `result is ToolResult.Effected`.
        assertTrue(result is ToolResult.HandedOff)
        val intent = launched.single()
        assertEquals(Intent.ACTION_DELETE, intent.action)
        assertEquals("package:org.telegram.messenger", intent.data.toString())
    }

    /**
     * Condition 4 of the owner's 2026-09-18 ruling: the agent never removes the launcher it is running
     * inside, because uninstalling Sidr mid-session kills the surface the session is running on.
     *
     * The package is **injected** rather than read from a `Context` inside the worker, which is what
     * makes this refusal assertable here at all instead of behind Robolectric. The presence grants
     * everything on purpose: the precondition is then out of the way, so the only thing that can
     * produce `Failed` is the refusal this test is named after.
     */
    @Test
    fun `uninstalling our own package is refused and nothing is dispatched`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to OWN_PACKAGE)))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(
            "uninstalling the launcher kills the surface running the session, so this must never " +
                "reach the launcher seam at all",
            0,
            launched.size,
        )
    }

    /**
     * A blank target declines instead of dispatching, the same fail-closed reading [parseSeconds] and
     * `parseClockTime` already carry: `Intent(ACTION_DELETE, "package:")` names no package, so the
     * call cannot remove anything and that is known before it is made. An agent that fired an
     * uninstall intent at nothing would, if the dispatch returned normally, report `HandedOff`
     * ("outcome unknown" — `Effected` until A4′ phase 0, Task 5) for an outcome already known; the
     * `Failed` asserted below is the answer `DOC-ILM-3` requires.
     *
     * It is a second line of defence rather than the only one: `ToolMatchPlanner` declines with
     * `NoPlan` when `AppTargetResolver` cannot resolve a name, so a blank should never reach here.
     */
    @Test
    fun `a blank package declines and nothing is dispatched`() = runTest {
        val launched = mutableListOf<Intent>()
        val worker = workerWith(FakeIntentLauncher(launched), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "")))

        assertEquals(ToolResult.Failed(CommandFailure.Generic), result)
        assertEquals(0, launched.size)
    }

    /**
     * Task 5 (A4′ phase 0). A1″ acceptance finding (a): `startActivity(ACTION_DELETE)` returns the
     * instant the OS dialog is raised and returns identically whether the user confirms, cancels, or
     * the responder refuses silently (rows 16/28/32) — so `Effected` here was a claim the worker had
     * no way to make. `HandedOff` is the claim it can make.
     */
    @Test
    fun `uninstall_app hands off — it does not claim the app was removed`() = runTest {
        val worker = workerWith(FakeIntentLauncher(mutableListOf()), grantsEverything)

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "com.marlin.notes")))

        assertTrue("a raised dialog is not a removal", result is ToolResult.HandedOff)
    }

    /**
     * The non-vacuity half, and it is the half that matters: `Effected` is a lie for exactly ONE tool
     * in this worker. The eleven navigating tools really do open their screen — `startActivity`
     * returning IS the effect — and `set_alarm`/`set_timer` really do create the alarm (rows 13/29). A
     * fix that downgraded them would trade one wrong word for fourteen.
     */
    @Test
    fun `every other tool in this worker still reports Effected`() = runTest {
        val worker = workerWith(FakeIntentLauncher(mutableListOf()), grantsEverything)

        val stillEffecting = listOf(
            Tier0ToolIds.SET_TIMER to mapOf("duration" to "5 minutes"),
            Tier0ToolIds.SET_ALARM to mapOf("time" to "7:30"),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyMap(),
            Tier0ToolIds.SHOW_ALARMS to emptyMap(),
            Tier0ToolIds.OPEN_CAMERA to emptyMap(),
            Tier0ToolIds.OPEN_WIFI_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_SOUND_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to emptyMap(),
            Tier0ToolIds.OPEN_APP_INFO to mapOf("app" to "com.marlin.notes"),
        )

        stillEffecting.forEach { (id, args) ->
            assertTrue(
                "$id performs its act — downgrading it would trade one wrong word for fourteen",
                worker.invoke(ResolvedInvocation(id, args)) is ToolResult.Effected,
            )
        }
    }

    /** `HandedOff` must not have eaten `Failed`: a dialog that never appeared is a failure we CAN see. */
    @Test
    fun `an uninstall that could not be dispatched is Failed, not handed off`() = runTest {
        val worker = workerWith(
            ThrowingIntentLauncher { ActivityNotFoundException("no uninstaller") },
            grantsEverything,
        )

        val result = worker.invoke(ResolvedInvocation(Tier0ToolIds.UNINSTALL_APP, mapOf("app" to "com.marlin.notes")))

        assertTrue(result is ToolResult.Failed)
    }

    private companion object {
        val ALARM_GRANTED = setOf("com.android.alarm.permission.SET_ALARM")

        /** Task 7: what `uninstall_app`'s catalog row names, granted by name rather than wholesale. */
        val DELETE_GRANTED = setOf("android.permission.REQUEST_DELETE_PACKAGES")

        /**
         * Stands in for the real `context.packageName` the graph injects. Its *value* is irrelevant to
         * every test but the self-uninstall refusal, whose whole subject is that the worker compares
         * its target against this string and stops.
         */
        const val OWN_PACKAGE = "com.sidr.launcher"
    }
}

/** The seam as the world can actually behave: `startActivity` throws and the worker must absorb it. */
private class ThrowingIntentLauncher(private val thrown: () -> RuntimeException) : IntentLauncher {
    override fun launch(intent: Intent): Nothing = throw thrown()
}

private class FakeIntentLauncher(private val record: MutableList<Intent>) : IntentLauncher {
    override fun launch(intent: Intent) { record += intent }
}
