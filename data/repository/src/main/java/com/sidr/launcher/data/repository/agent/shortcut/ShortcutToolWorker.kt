package com.sidr.launcher.data.repository.agent.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import com.sidr.launcher.domain.intent.CommandFailure
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The Android seam: `LauncherApps.startShortcut`. Kept behind an interface so the worker is testable
 * without a device — and, as A1′'s `set_timer` defect proved, so that "testable" is never mistaken for
 * "verified": what the real call does is measured on the phone
 * (`docs/superpowers/plans/2026-09-12-a1-device-measurements.md`, row 10), not asserted here.
 *
 * **`start` may throw, and that is deliberate**, the same division [com.sidr.launcher.data.repository.agent.IntentLauncher]
 * uses: containment belongs to the object that owns the result type, because a seam that swallows makes
 * a failed launch indistinguishable from a successful one (`start` returns `Unit`).
 */
fun interface ShortcutLauncher {
    fun start(packageName: String, shortcutId: String)
}

class AndroidShortcutLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShortcutLauncher {

    /**
     * `sourceBounds` and `startActivityOptions` are `null` — the shape row 10 actually measured
     * (`startShortcut(pkg, id, null, null, myUserHandle)` → `RETURNED_NORMALLY`, the target app opened,
     * no additional permission and no throw). Nothing here is inferred from the API's name.
     */
    override fun start(packageName: String, shortcutId: String) {
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            ?: error("LAUNCHER_APPS_SERVICE unavailable")
        launcherApps.startShortcut(packageName, shortcutId, null, null, Process.myUserHandle())
    }
}

/**
 * The `APP_SHORTCUT` level's worker. It performs one act — start the shortcut this id names — and its
 * whole argument comes from the id, so it binds nothing and declares no `argSchema`.
 *
 * **Containment is broad here, by decision rather than by oversight.** `Tier0IntentToolWorker` catches
 * a narrow, named pair (`ActivityNotFoundException`/`SecurityException`) and argues in its own KDoc
 * against a `RuntimeException` net, because the exception set `startActivity` raises was known from
 * `AndroidActionExecutor`'s measured precedent. No such precedent exists for `startShortcut`: the
 * device-measurement file has a row for the **success** path (row 10) and **none** for what the call
 * raises when the `android.app.role.HOME` role has been taken away between the snapshot that advertised
 * this tool and the moment the step runs. Row 2 shows that this API family is role-sensitive and that at
 * least one member throws across that boundary, so the failure mode is real and its shape is unmeasured.
 * Per the block rule (spec §3.1) that an Android premise may not be filled in from documentation or from
 * an API's name, this worker does not name a set it cannot cite; it contains everything and says so. The
 * cost of the opposite choice is not hypothetical — `AgentExecutor.perform`'s one `toolExecutor.invoke`
 * call site still has no `try` (`CLAUDE.md`, residual (8)), so an uncaught throw here kills the
 * home-screen process, which is exactly the crash A1′'s final review had to fix.
 *
 * The floor Task 1 added to the engine does not make this redundant: adapter-level catches produce a
 * specific failure at the place that knows what failed, and a floor is a floor.
 *
 * The failure is [CommandFailure.Generic], the same value every other fail-closed path here uses — a
 * richer per-tool failure vocabulary was rejected with a measured reason in the A1′ ADR, and a new
 * variant is a closed-sum widening in `commonMain` plus three locale strings for a state the user can do
 * nothing about.
 */
class ShortcutToolWorker @Inject constructor(
    private val launcher: ShortcutLauncher,
) : ToolWorker {

    override suspend fun invoke(invocation: ResolvedInvocation): ToolResult {
        // Unreachable in a well-formed graph — the federation routes by the registry this adapter
        // declares, and that registry only advertises ids that round-trip. Fail-closed and labelled as
        // such, never named in `CommandFailure` (spec §4.4).
        val parsed = ShortcutToolIds.parse(invocation.id)
            ?: return ToolResult.Failed(CommandFailure.Generic)

        return runCatching { launcher.start(parsed.first, parsed.second) }
            .fold(
                onSuccess = { ToolResult.Effected() },
                onFailure = { ToolResult.Failed(CommandFailure.Generic) },
            )
    }
}
