package com.sidr.launcher.probe

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Process
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.data.repository.agent.ToolSelector
import com.sidr.launcher.data.repository.agent.ToolVocabulary
import com.sidr.launcher.data.repository.agent.shortcut.AndroidShortcutQuery
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutCatalog
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A1″ Task 13 — a measurement probe for spec §7.2's thirteen Tier-0 candidates. It asserts nothing;
 * it records what the platform actually does, because spec §3.1 forbids writing any Android premise
 * in this block from documentation or recall.
 *
 * **Why this is an in-app probe rather than `adb shell am start`** (controller ruling R13-1): `am
 * start` runs as uid 2000 (`shell`), whose permission set and package-visibility filtering are not
 * `com.sidr.launcher`'s. Measuring from the shell would reproduce exactly the error class §3.1 exists
 * to prevent — A1′'s `set_timer` was refused for the *app* on a device where the shell could fire the
 * same intent. Every cell in §7.2 is about the app's own identity, so the probe confirms that identity
 * first ([logIdentity]) and a reading that names another caller must be thrown away.
 *
 * **The launch shape is production's, not a convenient one.** `ContextIntentLauncher` (see
 * `Tier0IntentToolWorker`) does `context.startActivity(intent.addFlags(FLAG_ACTIVITY_NEW_TASK))` from
 * the application context; [fire] does the same against
 * `InstrumentationRegistry.getInstrumentation().targetContext`. A probe that launched some other way
 * would measure the probe.
 *
 * **One `@Test` per candidate, named after §7.2's proposed id**, so each can be run alone with
 * `-e class com.sidr.launcher.probe.Tier0IntentProbe#setAlarm`. That separation is what makes the
 * second half of each row observable: only one intent has been fired when the host looks at the
 * screen, so what is focused is attributable.
 *
 * Two candidates take their target package from an instrumentation argument (`-e pkg <name>`) so the
 * same method can be run against the app itself and against a third party without editing this file.
 * [uninstallApp] additionally refuses a protected target outright — see its KDoc.
 */
@RunWith(AndroidJUnit4::class)
class Tier0IntentProbe {

    private fun log(key: String, value: String) = Log.i("SIDR_PROBE", "$key :: $value")

    /**
     * Runs before every candidate so identity is the first thing in the log of every single-method
     * run. Expected `com.sidr.launcher` / `10752`; anything else means the reading is about another
     * caller and must be discarded rather than interpreted.
     */
    @Before
    fun logIdentity() {
        log("context.packageName", InstrumentationRegistry.getInstrumentation().targetContext.packageName)
        log("Process.myUid", Process.myUid().toString())
    }

    // `Bundle.get` is deprecated with no non-reflective replacement that keeps the value's
    // declared type; this is a log line in a probe, and the extras are part of the measurement.
    @Suppress("DEPRECATION")
    private fun fire(candidate: String, intent: Intent) {
        log("$candidate.action", intent.action ?: "<null>")
        log("$candidate.data", intent.data?.toString() ?: "<null>")
        val extras = intent.extras
        log(
            "$candidate.extras",
            extras?.keySet()?.joinToString(",") { "$it=${extras.get(it)}" }?.ifEmpty { "<none>" } ?: "<none>",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val outcome = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        log(
            "$candidate.OUTCOME",
            outcome.fold({ "RETURNED_NORMALLY" }, { "THREW ${it::class.java.name}: ${it.message}" }),
        )
    }

    /** `-e pkg <name>` overrides [default]; both the request and the value used are logged. */
    private fun argPackage(default: String): String {
        val requested = InstrumentationRegistry.getArguments().getString("pkg")?.takeIf { it.isNotBlank() }
        log("targetPackage.requested", requested ?: "<none — default used>")
        log("targetPackage.used", requested ?: default)
        return requested ?: default
    }

    @Test
    fun setAlarm() = fire(
        "set_alarm",
        // 04:37 / "SIDR PROBE" so the row is identifiable in the clock's own list afterwards.
        // EXTRA_SKIP_UI = false is production's shape (`Tier0IntentToolWorker.setTimer`), and 2026-09-05
        // measured on this phone that it governs whether the responding app shows its UI, not whether
        // it acts — which is precisely what this row re-measures for ACTION_SET_ALARM.
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, 4)
            .putExtra(AlarmClock.EXTRA_MINUTES, 37)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "SIDR PROBE")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false),
    )

    @Test
    fun showAlarms() = fire("show_alarms", Intent(AlarmClock.ACTION_SHOW_ALARMS))

    @Test
    fun openAppInfo() = fire(
        "open_app_info",
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(
                Uri.fromParts(
                    "package",
                    argPackage(InstrumentationRegistry.getInstrumentation().targetContext.packageName),
                    null,
                ),
            ),
    )

    /**
     * The one destructive candidate, and the only method here with a refusal of its own.
     *
     * The probe will not fire `ACTION_DELETE` at [PROTECTED] — the app package or its own test
     * package — however it is invoked: a mistyped `-e pkg` must not be able to put the owner's install
     * in front of an uninstall dialog. The default target is a dead leftover from the deleted ONNX
     * stack. Nothing here confirms anything: `ACTION_DELETE` asks the OS, and whether the OS then asks
     * the user is the measurement.
     */
    @Test
    fun uninstallApp() {
        val target = argPackage(DEAD_LEFTOVER)
        if (target in PROTECTED) {
            log("uninstall_app.OUTCOME", "REFUSED_BY_PROBE: $target is protected; no intent was fired")
            return
        }
        fire("uninstall_app", Intent(Intent.ACTION_DELETE, Uri.fromParts("package", target, null)))
    }

    /**
     * **Task 13b, cell B's consequential half: can the caller detect a refusal at all?**
     *
     * [uninstallApp] measured that `startActivity` returns `RETURNED_NORMALLY` **both** when the
     * responder refuses (no permission — the uninstaller dies without drawing) and when it draws the
     * OS dialog (permission declared). The return value therefore carries no information, and neither
     * does an exception, because none is thrown in either direction. So the only channel left to a
     * worker is an **in-process precondition check before firing**, and this method measures what that
     * check actually returns on this device — once on a build that declares
     * `REQUEST_DELETE_PACKAGES` and once on a build that does not. It fires no intent and changes
     * nothing.
     *
     * `SET_ALARM` is read alongside it as a positive control (row 27 proved that one *is* enforced at
     * `startActivity`), and `ACCESS_FINE_LOCATION` because row 30 read its grant state from
     * `dumpsys package` — from **outside** the process — and row 24 turns on it.
     */
    @Test
    fun permissionSelfCheck() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        listOf(
            "android.permission.REQUEST_DELETE_PACKAGES",
            "com.android.alarm.permission.SET_ALARM",
            "android.permission.ACCESS_FINE_LOCATION",
        ).forEach { permission ->
            val raw = context.checkSelfPermission(permission)
            val verdict = when (raw) {
                PackageManager.PERMISSION_GRANTED -> "PERMISSION_GRANTED"
                PackageManager.PERMISSION_DENIED -> "PERMISSION_DENIED"
                else -> "UNKNOWN"
            }
            log("checkSelfPermission.$permission", "$verdict (raw=$raw)")
        }
    }

    @Test
    fun openCamera() = fire("open_camera", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))

    @Test
    fun openWifiSettings() = fire("open_wifi_settings", Intent(Settings.ACTION_WIFI_SETTINGS))

    @Test
    fun openBluetoothSettings() = fire("open_bluetooth_settings", Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    @Test
    fun openBatterySettings() = fire("open_battery_settings", Intent(Intent.ACTION_POWER_USAGE_SUMMARY))

    @Test
    fun openDataUsageSettings() = fire("open_data_usage_settings", Intent(Settings.ACTION_DATA_USAGE_SETTINGS))

    @Test
    fun openDisplaySettings() = fire("open_display_settings", Intent(Settings.ACTION_DISPLAY_SETTINGS))

    @Test
    fun openSoundSettings() = fire("open_sound_settings", Intent(Settings.ACTION_SOUND_SETTINGS))

    @Test
    fun openLocationSettings() = fire("open_location_settings", Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))

    /**
     * **The action is a string literal, not a `Settings` constant, and that is itself a finding.**
     * Spec §7.2 names `Settings.ACTION_NOTIFICATION_SETTINGS`; `javap` over
     * `platforms/android-37.0/android.jar` finds no such field (`ACTION_APP_NOTIFICATION_SETTINGS`
     * and `ACTION_NOTIFICATION_LISTENER_SETTINGS` exist, the bare one does not — it is `@hide`). A
     * Phase 3 tool for this screen would therefore have to hardcode the action string, so the probe
     * hardcodes it rather than silently substituting a different, public constant and measuring the
     * wrong screen.
     */
    @Test
    fun openNotificationSettings() =
        fire("open_notification_settings", Intent("android.settings.NOTIFICATION_SETTINGS"))

    /**
     * **Task 13b, piece 3 (smoke round), observation 1: what the *adapter* produces on this device.**
     *
     * Rows 6–9 measured `LauncherApps` directly. `ShortcutToolSource` / `ShortcutCatalog` /
     * `AndroidShortcutQuery` — the shipped `app_shortcut` adapter — had **never executed on a phone**,
     * so "the registry carries 205 shortcut descriptors" rested on the raw API's count and on unit
     * tests over a fake seam. This wires the three production classes exactly as
     * `AgentProvidesModule` does and logs what the adapter itself returns.
     *
     * **What this does and does not measure.** It runs the adapter's own production code over the real
     * `LauncherApps`; it does **not** run the Hilt graph, because `:app`'s `androidTest` has no Hilt
     * testing dependency and adding one would change the build under measurement. So a wiring mistake
     * in `AgentProvidesModule` is invisible here — `DoctrineGuardTest` and `ToolRegistryPermissionGuardTest`
     * cover that on the host — and what is measured is the adapter's behaviour against the device.
     */
    @Test
    fun shortcutAdapterSnapshot() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        log("adapter.hasShortcutHostPermission", launcherApps.hasShortcutHostPermission().toString())

        val catalog = ShortcutCatalog(AndroidShortcutQuery(context), Dispatchers.IO)
        runBlocking { catalog.refresh() }
        val source = ShortcutToolSource(catalog)

        val raw = catalog.current()
        val descriptors = source.all()
        val names = source.names()
        log("adapter.catalog.current.size", raw.size.toString())
        log("adapter.all.size", descriptors.size.toString())
        log("adapter.names.size", names.size.toString())
        log("adapter.distinctPackages", raw.map { it.packageName }.distinct().size.toString())
        log("adapter.distinctIds", descriptors.map { it.id }.distinct().size.toString())
        log("adapter.levels", descriptors.map { it.level.value }.distinct().sorted().joinToString(","))
        log("adapter.effects", descriptors.map { it.effect.name }.distinct().sorted().joinToString(","))
        log("adapter.risks", descriptors.map { it.risk.name }.distinct().sorted().joinToString(","))
        log("adapter.durabilities", descriptors.map { it.durability.name }.distinct().sorted().joinToString(","))
        log("adapter.idPrefixOk", descriptors.count { it.id.value.startsWith("app_shortcut:") }.toString())
        // Row 12 is Task 5's and stays theirs; this is only the adapter-visible symptom of that branch
        // — `AndroidShortcutQuery` falls back to the raw package name when `getApplicationInfo` fails.
        log(
            "adapter.appLabelEqualsPackageName",
            raw.count { it.appLabel == it.packageName }.toString(),
        )
        log("adapter.blankLabels", raw.count { it.shortcutLabel.isBlank() || it.appLabel.isBlank() }.toString())
        names.take(SAMPLE).forEachIndexed { index, name ->
            log("adapter.name[$index]", "qualifier=«${name.qualifier}» name=«${name.name}» id=${name.id.value}")
        }
    }

    /**
     * **Task 13b, piece 3, observation 3: does `ToolSelector` return one candidate at registry scale?**
     *
     * `ToolSelectorTest` exercises this class over a handful of hand-built names. Here it runs over the
     * device's **real** `app_shortcut` set beside the authored `ToolVocabulary`, which is the only place
     * the "collapses at scale" and "offers many" failure modes can actually appear. The return type is
     * `ToolMatch?`, so "offers many" is structurally impossible — the live question is whether, with
     * hundreds of third-party names in play, a text that *should* select still does and a text that
     * should not still declines.
     *
     * Texts come from `-e texts "a|b|c"` so the exact shortcut labels this device happens to publish can
     * be supplied after [shortcutAdapterSnapshot] has reported them, without editing this file.
     */
    @Test
    fun toolSelectorAtScale() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = ShortcutCatalog(AndroidShortcutQuery(context), Dispatchers.IO)
        runBlocking { catalog.refresh() }
        val source = ShortcutToolSource(catalog)
        val selector = ToolSelector(ToolVocabulary(), source)
        log("selector.registrySize", source.all().size.toString())

        val texts = InstrumentationRegistry.getArguments().getString("texts")
            ?.split('|')?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?: emptyList()
        log("selector.textCount", texts.size.toString())
        texts.forEach { text ->
            val match = selector.select(text)
            log(
                "selector.select«$text»",
                match?.let { "MATCHED id=${it.id.value} args=${it.args}" } ?: "NO_MATCH",
            )
        }
    }

    private companion object {
        const val SAMPLE = 30
        const val DEAD_LEFTOVER = "com.sidr.launcher.data.ailocal.test"
        val PROTECTED = setOf("com.sidr.launcher", "com.sidr.launcher.test")
    }
}
