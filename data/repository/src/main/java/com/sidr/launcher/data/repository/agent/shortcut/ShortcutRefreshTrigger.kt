package com.sidr.launcher.data.repository.agent.shortcut

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
 * no package observer — and **nothing in this repository observed package changes at all** before A1″
 * (no `LauncherApps.registerCallback`, no `ACTION_PACKAGE_ADDED` receiver; the app list is simply
 * re-read on the next load). Rather than invent a lifecycle owner, this reuses the one that already
 * exists: `SidrLauncherApp.onCreate`, which already launches one app-scoped job on `@ApplicationScope`
 * (`suggestionsWorkScheduler.ensureScheduled()`). This class is the second.
 *
 * **Nothing on this path runs on the main thread, and since fix round 1 that is true rather than
 * claimed.** This class holds no `Context` and reaches no system service: registration is
 * [ShortcutChangeObserver]'s, and [AndroidShortcutChangeObserver] delivers callbacks on a private
 * `HandlerThread` it owns, never the main looper. Everything here — the registration call, the first
 * refresh, and every refresh a callback asks for — runs on the [CoroutineScope] given to [start], which
 * the composition root builds over the IO dispatcher; the `LauncherApps` query itself is inside
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
 *
 * All four behaviours below — one-shot idempotence, register-before-refresh ordering, containment of
 * the unmeasured call, and a reported change re-reading the catalog — are held by
 * `ShortcutRefreshTriggerTest`.
 */
@Singleton
class ShortcutRefreshTrigger @Inject constructor(
    private val observer: ShortcutChangeObserver,
    private val catalog: ShortcutCatalog,
) {

    /** One process, one registration. `onCreate` runs once, so this is belt-and-braces, not a fix. */
    private val started = AtomicBoolean(false)

    /**
     * Registration is ordered **before** the first refresh so there is no window in which a change goes
     * unobserved; a callback that arrives mid-refresh costs one redundant re-read, which is the cheaper
     * of the two errors.
     *
     * The `runCatching` is the containment the KDoc above describes, and it wraps the registration
     * **only**: a registration that fails must still leave the first refresh to run.
     */
    fun start(scope: CoroutineScope) {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            runCatching {
                observer.observe { scope.launch { catalog.refresh() } }
            }
            catalog.refresh()
        }
    }
}
