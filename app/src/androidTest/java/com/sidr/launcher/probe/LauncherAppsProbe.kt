package com.sidr.launcher.probe

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A1″ Task 5 — a measurement probe, not a test of the product. It asserts nothing; it records what
 * the platform actually does, because spec §3.1 forbids writing any Android premise in this block
 * from documentation or recall. Run once with Sidr not the default home, once with it set.
 */
@RunWith(AndroidJUnit4::class)
class LauncherAppsProbe {

    private fun log(key: String, value: String) = Log.i("SIDR_PROBE", "$key :: $value")

    @Test
    fun measureLauncherApps() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        log("context.packageName", ctx.packageName)
        log("Process.myUid", Process.myUid().toString())

        val launcherApps = ctx.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

        val hostPermission = runCatching { launcherApps.hasShortcutHostPermission() }
        log(
            "hasShortcutHostPermission",
            hostPermission.fold({ it.toString() }, { "THREW ${it::class.java.name}: ${it.message}" }),
        )

        val query = LauncherApps.ShortcutQuery().setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        val result = runCatching { launcherApps.getShortcuts(query, Process.myUserHandle()) }

        result.onFailure { e ->
            log("getShortcuts.OUTCOME", "EXCEPTION")
            log("getShortcuts.exceptionClass", e::class.java.name)
            log("getShortcuts.exceptionMessage", e.message ?: "<null message>")
        }

        result.onSuccess { shortcuts ->
            if (shortcuts == null) {
                log("getShortcuts.OUTCOME", "NULL")
                return@onSuccess
            }
            log("getShortcuts.OUTCOME", "VALUE")
            log("getShortcuts.total", shortcuts.size.toString())

            val packages = shortcuts.map { it.`package` }.distinct()
            log("getShortcuts.distinctPackages", packages.size.toString())
            log("getShortcuts.packages", packages.joinToString(","))

            log("shortLabel.populated", shortcuts.count { !it.shortLabel.isNullOrBlank() }.toString())
            log("longLabel.populated", shortcuts.count { !it.longLabel.isNullOrBlank() }.toString())
            log("isEnabled.true", shortcuts.count { it.isEnabled }.toString())

            shortcuts.take(5).forEachIndexed { i, s ->
                log("sample[$i]", "pkg=${s.`package`} id=${s.id} short=${s.shortLabel} long=${s.longLabel}")
            }

            val first = shortcuts.firstOrNull { it.isEnabled } ?: return@onSuccess
            log("startShortcut.target", "pkg=${first.`package`} id=${first.id}")
            val started = runCatching {
                launcherApps.startShortcut(first.`package`, first.id, null, null, Process.myUserHandle())
            }
            log(
                "startShortcut.OUTCOME",
                started.fold({ "RETURNED_NORMALLY" }, { "THREW ${it::class.java.name}: ${it.message}" }),
            )
        }
    }
}
