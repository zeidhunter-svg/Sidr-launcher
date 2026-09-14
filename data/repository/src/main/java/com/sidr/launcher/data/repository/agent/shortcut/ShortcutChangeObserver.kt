package com.sidr.launcher.data.repository.agent.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.HandlerThread
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The seam onto `LauncherApps.registerCallback`: "tell me when the shortcut set may have changed".
 *
 * It exists for the same reason [ShortcutQuery] and [ShortcutLauncher] do — every Android touch in this
 * adapter sits behind a port so the behaviour around it is testable without a device. Fix round 1
 * (finding 6) added this one: `ShortcutRefreshTrigger` called `getSystemService` inline, which made it
 * the only production file in the adapter with no tests at all.
 *
 * **An implementation may throw**, and containment is [ShortcutRefreshTrigger]'s — the same division
 * [ShortcutQuery] documents against [ShortcutCatalog]. A seam that swallowed would make "registered" and
 * "silently not registered" indistinguishable, and the trigger's degradation depends on telling them
 * apart.
 */
fun interface ShortcutChangeObserver {

    /**
     * Register [onChange], to be invoked whenever Android reports a package or shortcut change.
     *
     * [onChange] must be cheap and must not block: it is invoked on whatever thread the implementation
     * chose for delivery. [AndroidShortcutChangeObserver] delivers on a private [HandlerThread] — never
     * the main looper — and the trigger's own handler does nothing but launch a coroutine.
     */
    fun observe(onChange: () -> Unit)
}

/**
 * `registerCallback`, delivered on a thread this class owns.
 *
 * **Delivery is on a private [HandlerThread], and that is a correctness decision rather than a
 * preference.** The first version of this code passed `Handler(Looper.getMainLooper())` and the prose
 * around it claimed the path never touched the main thread — false, since every package and shortcut
 * change was then delivered onto the main looper of a launcher whose home screen is the thing being
 * recomposed there. Owning the looper removes the claim instead of documenting it.
 *
 * It also removes an **unmeasured premise**. An earlier KDoc asserted what `registerCallback`'s
 * no-handler overload does with the calling thread's looper; that came from documentation, and the
 * block's rule (spec §3.1) forbids an Android premise sourced that way. Supplying the handler
 * explicitly means nothing has to be claimed about the overload that is not used.
 *
 * **What is still unmeasured, contained rather than assumed.**
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` has rows for
 * `hasShortcutHostPermission`, `getShortcuts` and `startShortcut` (1, 2, 6, 10) and **no row for
 * `registerCallback`**. Row 2 shows the family is role-sensitive — `getShortcuts` throws
 * `SecurityException` when Sidr does not hold `android.app.role.HOME` — so a throw here is a real
 * possibility of unknown shape. This class therefore does not catch: it lets the call fail and
 * [ShortcutRefreshTrigger] degrade to refresh-at-start-only, which is safe because a failed refresh
 * leaves the snapshot **empty** rather than stale. Addressed to the next device round, like row 12.
 */
@Singleton
class AndroidShortcutChangeObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShortcutChangeObserver {

    override fun observe(onChange: () -> Unit) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: error("LAUNCHER_APPS_SERVICE unavailable")

        // Started before the registration and stopped again if it fails, so a refused registration
        // does not leave a live thread behind for the life of the process.
        val thread = HandlerThread(THREAD_NAME).apply { start() }
        val registration = runCatching {
            launcherApps.registerCallback(ShortcutChangeCallback(onChange), Handler(thread.looper))
        }
        if (registration.isFailure) thread.quitSafely()
        registration.getOrThrow()
    }

    private companion object {
        const val THREAD_NAME = "sidr-shortcut-observer"
    }
}

/**
 * Six arms, one answer — "look again".
 *
 * [ShortcutCatalog] replaces its snapshot wholesale and keeps no per-package state, so there is nothing
 * for an individual arm to do differently. Overriding all six rather than only `onShortcutsChanged` is
 * deliberate: an app that is removed, disabled or made unavailable takes its shortcuts with it without
 * ever reporting a shortcut change.
 *
 * It is a named class rather than an anonymous object so the fan-in is reachable from a test —
 * `AndroidShortcutChangeObserverTest` invokes all six and counts one action each. The registration
 * around it stays untestable off-device and is measured there instead.
 */
internal class ShortcutChangeCallback(
    private val onChange: () -> Unit,
) : LauncherApps.Callback() {

    override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange()

    override fun onPackageAdded(packageName: String, user: UserHandle) = onChange()

    override fun onPackageChanged(packageName: String, user: UserHandle) = onChange()

    override fun onPackagesAvailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = onChange()

    override fun onPackagesUnavailable(
        packageNames: Array<out String>,
        user: UserHandle,
        replacing: Boolean,
    ) = onChange()

    override fun onShortcutsChanged(
        packageName: String,
        shortcuts: MutableList<ShortcutInfo>,
        user: UserHandle,
    ) = onChange()
}
