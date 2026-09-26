package com.sidr.launcher.data.repository.agent.shortcut

import android.os.Process
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The fan-in [AndroidShortcutChangeObserver] installs: six `LauncherApps.Callback` arms, one action.
 *
 * Only the fan-in is tested, and that is the honest boundary. `registerCallback` itself is a binder
 * call whose behaviour across the `android.app.role.HOME` role has **no row** in
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md`; it is contained by
 * `ShortcutRefreshTrigger` and addressed to the next device round, not faked here. What *is* ordinary
 * Kotlin — that every arm means "look again", so a package being removed is as much a reason to re-read
 * as a shortcut changing — is exactly what a unit test can hold, and it is the part a future edit could
 * silently drop.
 *
 * Robolectric, because `LauncherApps.Callback` is an abstract Android class and its constructor is a
 * stub under the plain android.jar.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AndroidShortcutChangeObserverTest {

    @Test
    fun `every callback arm asks for exactly one refresh`() {
        var changes = 0
        val callback = ShortcutChangeCallback { changes++ }
        val user = Process.myUserHandle()

        callback.onPackageAdded("com.a", user)
        callback.onPackageRemoved("com.a", user)
        callback.onPackageChanged("com.a", user)
        callback.onPackagesAvailable(arrayOf("com.a"), user, false)
        callback.onPackagesUnavailable(arrayOf("com.a"), user, false)
        callback.onShortcutsChanged("com.a", mutableListOf(), user)

        assertEquals(
            "Each arm of LauncherApps.Callback means 'the snapshot may be stale' and must cost exactly " +
                "one re-read — an arm that stops calling back leaves the catalog silently frozen for " +
                "that class of change.",
            6,
            changes,
        )
    }
}
