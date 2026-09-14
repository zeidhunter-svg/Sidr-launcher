package com.sidr.launcher.data.repository.agent.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * When [ShortcutCatalog] is asked to look again: once at launcher start, and thereafter whenever
 * Android says a package or a shortcut changed.
 *
 * **Where this is started from, and why it is not where the task brief expected.** The brief said to
 * put the registration "in the same Android-facing place that already owns app-list refresh". Read
 * against the tree, no such place exists: the app list is loaded by `LauncherAppList.load()` from
 * `LauncherViewModel`'s `init` — a feature-layer class with no `Context`, no lifecycle of its own and
 * no package observer — and **nothing in this repository observes package changes at all** (there is no
 * `LauncherApps.registerCallback`, no `ACTION_PACKAGE_ADDED` receiver; the app list is simply re-read on
 * the next load). Rather than invent a lifecycle owner, this reuses the one that already exists:
 * `SidrLauncherApp.onCreate`, which already launches one app-scoped job on `@ApplicationScope`
 * (`suggestionsWorkScheduler.ensureScheduled()`). This class is the second.
 *
 * **Nothing here runs on the main thread.** [start] does no work of its own beyond a coroutine launch;
 * the registration binder call and every refresh run on the scope it is given, which the composition
 * root builds over the IO dispatcher. Callbacks are *delivered* on the main looper (see [register]) and
 * do nothing there but launch — the `LauncherApps` query itself is inside
 * [ShortcutCatalog.refresh]'s own `withContext(ioDispatcher)`.
 *
 * **An unmeasured Android premise, contained rather than assumed** (block rule, spec §3.1). The
 * device-measurement file records what `hasShortcutHostPermission`, `getShortcuts` and `startShortcut`
 * do across the `android.app.role.HOME` boundary (rows 1, 2, 6, 10). It has **no row for
 * `registerCallback`**, and that family is demonstrably role-sensitive — row 2 is a `SecurityException`
 * from `getShortcuts` on a device where Sidr is not the home app. So this class does not claim that
 * registration succeeds without the role: it contains the call, and degrades to *refresh-at-start-only*
 * if it does not. That degradation is correct rather than merely safe, because a refresh that fails
 * leaves the snapshot **empty** (`ShortcutCatalog`'s stated choice), so nothing stale is ever
 * advertised. What it costs is freshness: a user who grants the HOME role after launch would, in that
 * case, see shortcuts only from the next process start. Addressed to the next device round with `adb`
 * access, the same way row 12 is.
 */
@Singleton
class ShortcutRefreshTrigger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalog: ShortcutCatalog,
) {

    /** One process, one registration. `onCreate` runs once, so this is belt-and-braces, not a fix. */
    private val started = AtomicBoolean(false)

    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            register(scope)
            catalog.refresh()
        }
    }

    /**
     * Registration is ordered **before** the first refresh so there is no window in which a change goes
     * unobserved; a callback that arrives mid-refresh costs one redundant re-read, which is the cheaper
     * of the two errors.
     *
     * The [Handler] is explicit rather than defaulted: the no-handler overload builds one on the
     * *calling* thread's looper, and this runs on an IO thread, which has none.
     */
    private fun register(scope: CoroutineScope) {
        runCatching {
            val launcherApps =
                context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return
            launcherApps.registerCallback(ChangeCallback(scope), Handler(Looper.getMainLooper()))
        }
    }

    /**
     * Every arm is the same answer — "look again" — because [ShortcutCatalog] replaces its snapshot
     * wholesale and has no per-package state to update. Overriding them individually rather than
     * reacting only to `onShortcutsChanged` is deliberate: an app that is removed, disabled or made
     * unavailable takes its shortcuts with it without ever reporting a shortcut change.
     */
    private inner class ChangeCallback(private val scope: CoroutineScope) : LauncherApps.Callback() {

        override fun onPackageRemoved(packageName: String, user: UserHandle) = refresh()

        override fun onPackageAdded(packageName: String, user: UserHandle) = refresh()

        override fun onPackageChanged(packageName: String, user: UserHandle) = refresh()

        override fun onPackagesAvailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = refresh()

        override fun onPackagesUnavailable(
            packageNames: Array<out String>,
            user: UserHandle,
            replacing: Boolean,
        ) = refresh()

        override fun onShortcutsChanged(
            packageName: String,
            shortcuts: MutableList<ShortcutInfo>,
            user: UserHandle,
        ) = refresh()

        private fun refresh() {
            scope.launch { catalog.refresh() }
        }
    }
}
