package com.sidr.launcher.probe

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.data.repository.InstalledAppsRepositoryImpl
import com.sidr.launcher.data.repository.agent.shortcut.AndroidShortcutQuery
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import kotlinx.coroutines.Dispatchers
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `B15` — dumps the device's **dynamic tool names** so the selection-decline rate can be measured
 * offline, reproducibly, against the registry the owner's phone actually has.
 *
 * **Why a dump rather than an on-device measurement.** `Tier0IntentProbe.toolSelectorAtScale` already
 * runs `ToolSelector` over the real registry, but it answers `MATCHED` / `NO_MATCH` only, and `B15` is
 * about the **categories** of decline — ambiguous authored trigger, dynamic tie, dynamic name whose app
 * was not named, and the target that did not resolve. Those need the planner too, not just the selector.
 * Dumping once turns a phone session into a fixture, so the measurement re-runs on every vocabulary edit
 * instead of costing a device round each time. Owner decision, 2026-09-20.
 *
 * **This probe asserts nothing and changes nothing.** It is deliberately NOT modelled on
 * [LauncherAppsProbe], which ends by calling `startShortcut` on the first enabled shortcut — that is a
 * side effect (it launches a third-party app), and a dump must not have one. Nothing here writes,
 * launches, or touches the app under test; the only Android call is the same read
 * `ShortcutToolSource` performs in production.
 *
 * **It reads what PRODUCTION reads, not what the platform offers.** The source of truth is
 * `ShortcutToolSource.names()` — the same projection `ToolSelector` consumes — so the fixture cannot
 * drift from the thing being measured by including shortcuts the adapter would have filtered out (the
 * id round-trip filter in `usable()`).
 *
 * **Requires the `android.app.role.HOME` role**, because `LauncherApps.getShortcuts` throws without it
 * and `ShortcutCatalog.refresh` swallows that into an empty list. A dump of `count :: 0` therefore means
 * "Sidr is not the default home", not "this phone has no shortcuts" — the two are indistinguishable from
 * here, so the count is printed rather than asserted and the reader is told which is which.
 *
 * Rows are JSON so a label containing any separator character survives; `dump.end` closes the stream so
 * a truncated logcat read is detectable rather than silently short.
 */
@RunWith(AndroidJUnit4::class)
class ShortcutCatalogDumpProbe {

    private fun log(key: String, value: String) = Log.i(TAG, "$key :: $value")

    @Test
    fun dumpDynamicToolNames() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = ShortcutCatalog(AndroidShortcutQuery(context), Dispatchers.IO)
        runBlocking { catalog.refresh() }

        val names = ShortcutToolSource(catalog).names()
        log("dump.count", names.size.toString())
        log(
            "dump.note",
            if (names.isEmpty()) {
                "EMPTY - either Sidr does not hold android.app.role.HOME, or the phone publishes none"
            } else {
                "distinctPackages=" + names.map { it.id.value.substringAfter(':').substringBefore('/') }
                    .distinct().size
            },
        )

        names.forEachIndexed { i, n ->
            val row = JSONObject()
                .put("id", n.id.value)
                .put("qualifier", n.qualifier)
                .put("name", n.name)
            log("dump.row[$i]", row.toString())
        }

        log("dump.end", names.size.toString())
    }

    /**
     * The second input the offline measurement needs: the installed-app list, dumped through the
     * **production** repository rather than through `PackageManager` directly, so the fixture contains
     * exactly the apps `AppTargetResolver` would see.
     *
     * Without it the decline category "a tool matched but its target did not resolve" — `R14-39`, the
     * one whose consequence is that the raw command text reaches the cloud model on a goal a registered
     * tool had already claimed — cannot be told apart from "nothing matched at all". Those two feed
     * different decisions, so collapsing them would make the measurement answer a question nobody asked.
     *
     * Labels, not just package names: the resolver matches on a normalized **label** and falls back to
     * an alias, so a fixture of package names alone would resolve nothing and report every target as
     * unresolved.
     */
    @Test
    fun dumpInstalledApps() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = InstalledAppsRepositoryImpl(context, Dispatchers.IO)

        when (val result = runBlocking { repo.getInstalledApps() }) {
            is OperationResult.Success -> {
                val apps = result.value
                log("apps.count", apps.size.toString())
                apps.forEachIndexed { i, app ->
                    val row = JSONObject()
                        .put("package", app.packageName)
                        .put("label", app.label)
                    log("apps.row[$i]", row.toString())
                }
                log("apps.end", apps.size.toString())
            }
            is OperationResult.Failure -> {
                log("apps.count", "FAILURE")
                log("apps.note", result.toString())
                log("apps.end", "FAILURE")
            }
        }
    }

    private companion object {
        const val TAG = "SIDR_B15"
    }
}
