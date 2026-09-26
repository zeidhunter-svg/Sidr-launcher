package com.sidr.launcher.data.repository.agent.shortcut

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The trigger's own behaviour, testable at all only because fix round 1 put `registerCallback` behind
 * [ShortcutChangeObserver] (finding 6). Before that this class called `getSystemService` inline and was
 * the one new Android touch in the `app_shortcut` adapter with no seam and no test — while every other
 * touch sat behind [ShortcutQuery] or [ShortcutLauncher] precisely so it could be tested.
 *
 * Nothing here needs Robolectric: both seams are `fun interface`s and the catalog is the production
 * [ShortcutCatalog] over a recording query, so the ordering assertions below read the real object's
 * real calls rather than a stub's.
 */
class ShortcutRefreshTriggerTest {

    /** Records both seams into one list, so *order between them* is assertable and not just counts. */
    private class Recorder {
        val events = mutableListOf<String>()
        var onChange: (() -> Unit)? = null

        fun catalog(dispatcher: kotlinx.coroutines.CoroutineDispatcher) = ShortcutCatalog(
            query = {
                events += "refresh"
                emptyList()
            },
            ioDispatcher = dispatcher,
        )

        fun observer(failing: Boolean = false) = ShortcutChangeObserver { callback ->
            events += "observe"
            if (failing) throw SecurityException("no HOME role")
            onChange = callback
        }
    }

    @Test
    fun `start registers before the first refresh`() = runTest {
        val recorder = Recorder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val trigger = ShortcutRefreshTrigger(recorder.observer(), recorder.catalog(dispatcher))

        trigger.start(CoroutineScope(dispatcher))

        assertEquals(listOf("observe", "refresh"), recorder.events)
    }

    @Test
    fun `a second start is a no-op so one process registers once`() = runTest {
        val recorder = Recorder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val trigger = ShortcutRefreshTrigger(recorder.observer(), recorder.catalog(dispatcher))
        val scope = CoroutineScope(dispatcher)

        trigger.start(scope)
        trigger.start(scope)

        assertEquals(listOf("observe", "refresh"), recorder.events)
    }

    /**
     * The containment the trigger exists to provide. `registerCallback`'s behaviour across the
     * `android.app.role.HOME` boundary has **no row** in the device-measurement file, and row 2 proves
     * the family is role-sensitive, so the call is wrapped rather than trusted — and a registration that
     * fails must still leave the first refresh to run, degrading to refresh-at-start-only rather than to
     * nothing at all.
     */
    @Test
    fun `an observer that throws still leaves the first refresh running`() = runTest {
        val recorder = Recorder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val trigger = ShortcutRefreshTrigger(
            recorder.observer(failing = true),
            recorder.catalog(dispatcher),
        )

        trigger.start(CoroutineScope(dispatcher))

        assertEquals(listOf("observe", "refresh"), recorder.events)
    }

    /**
     * Every callback arm collapses to the same action, so what the trigger must hold is that a reported
     * change re-reads the catalog — not which of `LauncherApps.Callback`'s six methods reported it. The
     * fan-in from six arms to one call is [AndroidShortcutChangeObserver]'s, and is held by
     * [AndroidShortcutChangeObserverTest].
     */
    @Test
    fun `a reported change refreshes the catalog again`() = runTest {
        val recorder = Recorder()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val trigger = ShortcutRefreshTrigger(recorder.observer(), recorder.catalog(dispatcher))

        trigger.start(CoroutineScope(dispatcher))
        recorder.onChange!!.invoke()
        recorder.onChange!!.invoke()

        assertEquals(listOf("observe", "refresh", "refresh", "refresh"), recorder.events)
    }
}
