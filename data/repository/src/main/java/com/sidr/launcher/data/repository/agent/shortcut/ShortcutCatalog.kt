package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The snapshot the `app_shortcut` adapter reads. The actual `LauncherApps` call lives behind
 * [ShortcutQuery] — this class never touches `LauncherApps` itself, by construction.
 *
 * **Why a snapshot and not a live read.** `ToolRegistry.all()` is documented read-only and side-effect
 * free, and it is called by guards and by the presentation mapper (`InvocationValidator` calls only
 * `find()`, not `all()`). Putting a binder call behind that contract would hide I/O behind a
 * synchronous, pure-looking signature. So the source returns a list it was *given*, and refreshing is
 * this class's explicit, suspending job.
 *
 * **Degradation is a requirement, not politeness.** [refresh] never throws: a device where Sidr is not
 * the default home yields an empty tool set, which means the registry advertises nothing it cannot
 * run. The opposite failure — advertising a tool that always fails — is what cost A1′ its first
 * acceptance run. That same fail-closed preference means a **failed** refresh discards any
 * previously-good snapshot rather than keeping it: [current] returns `emptyList()` after a refresh
 * whose [ShortcutQuery] call threw, even if an earlier refresh had succeeded. This is a deliberate
 * choice, not an oversight — contrast `SuggestionEngineImpl.refresh(): OperationResult<…>` elsewhere in
 * this module, which keeps the last good value on failure; this catalog does not, because a stale-but-
 * still-advertised shortcut is a tool that may no longer run.
 */
@Singleton
class ShortcutCatalog @Inject constructor(
    private val query: ShortcutQuery,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    @Volatile
    private var snapshot: List<AppShortcut> = emptyList()

    fun current(): List<AppShortcut> = snapshot

    suspend fun refresh() {
        snapshot = withContext(ioDispatcher) {
            runCatching { query.shortcuts() }.getOrElse { emptyList() }
        }
    }
}

/**
 * The Android seam, so [ShortcutCatalog] is testable without Robolectric.
 *
 * An implementation **may throw** — [AndroidShortcutQuery] documents the one exception it catches
 * itself, but anything else it or a future implementation raises is expected to propagate here.
 * Containment is [ShortcutCatalog.refresh]'s `runCatching`, not this interface: the same
 * per-implementation-convention containment already recorded as a residual for `ToolWorker` in
 * `CLAUDE.md`.
 */
fun interface ShortcutQuery {
    fun shortcuts(): List<AppShortcut>
}
