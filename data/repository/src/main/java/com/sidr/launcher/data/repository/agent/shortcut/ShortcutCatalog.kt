package com.sidr.launcher.data.repository.agent.shortcut

import com.sidr.launcher.core.common.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The snapshot behind the `app_shortcut` adapter, and the only place that talks to `LauncherApps`.
 *
 * **Why a snapshot and not a live read.** `ToolRegistry.all()` is documented read-only and side-effect
 * free, and it is called by `InvocationValidator`, by the selector, by guards and by the presentation
 * mapper. Putting a binder call behind that contract would hide I/O behind a synchronous, pure-looking
 * signature. So the source returns a list it was *given*, and refreshing is this class's explicit,
 * suspending job.
 *
 * **Degradation is a requirement, not politeness.** [refresh] never throws: a device where Sidr is not
 * the default home yields an empty tool set, which means the registry advertises nothing it cannot
 * run. The opposite failure — advertising a tool that always fails — is what cost A1′ its first
 * acceptance run.
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

/** The Android seam, so [ShortcutCatalog] is testable without Robolectric. */
fun interface ShortcutQuery {
    fun shortcuts(): List<AppShortcut>
}
