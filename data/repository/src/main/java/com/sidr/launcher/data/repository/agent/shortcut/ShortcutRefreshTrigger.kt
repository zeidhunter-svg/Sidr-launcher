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
 * **Nothing this class does runs on the main thread, and unlike the first version of this KDoc that is
 * now checkable rather than contradicted four lines later.** Everything here — the registration call,
 * the first refresh, and every refresh a callback asks for — runs on the [CoroutineScope] given to
 * [start], which the composition root builds over the IO dispatcher; the `LauncherApps` query itself is
 * inside [ShortcutCatalog.refresh]'s own `withContext(ioDispatcher)`. This class holds no `Context` and
 * reaches no system service at all: registration is [ShortcutChangeObserver]'s.
 *
 * **One link in that sentence is an intent, not a measurement** (fix round 2, finding C).
 * [AndroidShortcutChangeObserver] registers over a `Handler` on a private `HandlerThread` rather than
 * on the main looper — but that a callback so registered is *delivered* there is the `Handler`/`Looper`
 * contract, i.e. documentation, and the measurement file has no row for it. Spec §3.1 forbids filling a
 * premise from documentation even when the documented answer seems obvious, so the claim is: delivery
 * off the main thread is **asked for** by the only mechanism the API offers. It is also the cheap half
 * either way — whatever thread a callback arrives on, the arm below does nothing there but `launch`.
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
