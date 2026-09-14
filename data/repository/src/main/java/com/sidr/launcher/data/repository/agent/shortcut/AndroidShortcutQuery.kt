package com.sidr.launcher.data.repository.agent.shortcut

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * The Android implementation of [ShortcutQuery], and the only place in **production** code that calls
 * `LauncherApps` (the `androidTest` probe `app/src/androidTest/java/com/sidr/launcher/probe/LauncherAppsProbe.kt`
 * also calls it, deliberately, to produce the measurements this class follows).
 *
 * Shortcut host access is gated by the `android.app.role.HOME` role — held by exactly one package at a
 * time and assigned by the user, not by a manifest permission. What each call actually does across
 * that role boundary is measured, verbatim, on the SM-A325F in
 * `docs/superpowers/plans/2026-09-12-a1-device-measurements.md` (rows 1, 2 and 6) — this class follows
 * that measurement rather than the platform documentation or the API's name, per the block's own rule
 * (spec §3.1) that no Android premise in A1″ may come from either:
 *
 *  - [LauncherApps.hasShortcutHostPermission] answers `false`/`true` across the HOME-role transition
 *    **without throwing** (rows 1 and 6). It is checked first, as the primary gate.
 *  - [LauncherApps.getShortcuts] **throws** `SecurityException("Caller can't access shortcut
 *    information")` when Sidr does not hold the role (row 2) — it does **not** return an empty list or
 *    `null`. Relying on `getShortcuts(...).orEmpty()` alone (no gate) would crash the launcher at
 *    exactly the moment most users meet it: installed and not yet chosen as the default home.
 *  - The `SecurityException` catch below is kept as a **fail-closed backstop**, not the primary
 *    mechanism — the HOME role can change in the window between the [LauncherApps.hasShortcutHostPermission]
 *    check and the [LauncherApps.getShortcuts] call.
 *
 * [android.content.pm.ShortcutInfo.getLongLabel] is populated for only about half of shortcuts on the
 * measured device (row 9: `shortLabel` 205/205, `longLabel` 109/205), so [android.content.pm.ShortcutInfo.getShortLabel]
 * is the fallback, never the other way round.
 */
class AndroidShortcutQuery @Inject constructor(
    @ApplicationContext private val context: Context,
) : ShortcutQuery {

    override fun shortcuts(): List<AppShortcut> {
        val launcherApps =
            context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps ?: return emptyList()
        if (!launcherApps.hasShortcutHostPermission()) return emptyList()

        val packageManager = context.packageManager
        val request = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )

        val shortcuts = try {
            launcherApps.getShortcuts(request, Process.myUserHandle()).orEmpty()
        } catch (e: SecurityException) {
            return emptyList()
        }

        val labels = mutableMapOf<String, String>()
        return shortcuts.mapNotNull { info ->
            if (!info.isEnabled) return@mapNotNull null
            val label = (info.longLabel?.takeIf { it.isNotBlank() } ?: info.shortLabel)
                ?.toString()?.trim().orEmpty()
            if (label.isEmpty()) return@mapNotNull null
            // Unmeasured branch (docs/superpowers/plans/2026-09-12-a1-device-measurements.md, row 12):
            // no device was available to observe whether `getApplicationInfo` throws
            // `NameNotFoundException` for a shortcut-contributing package with no `LAUNCHER` activity —
            // `LauncherApps` is exempt from Android 11+ package-visibility filtering for the HOME-role
            // holder, but `PackageManager` is not known to share that exemption, and this was never
            // observed either way. The fallback to the raw package name is fail-safe by construction
            // (it never crashes and never offers a tool that cannot run), but whether it actually fires
            // on a real device is an open question, not a documented certainty.
            val appLabel = labels.getOrPut(info.`package`) {
                runCatching {
                    packageManager.getApplicationInfo(info.`package`, 0).loadLabel(packageManager).toString()
                }.getOrElse { info.`package` }
            }
            AppShortcut(
                packageName = info.`package`,
                shortcutId = info.id,
                appLabel = appLabel,
                shortcutLabel = label,
            )
        }
    }
}
