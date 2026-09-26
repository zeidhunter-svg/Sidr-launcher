package com.sidr.launcher.data.repository.agent

import android.content.Context
import android.content.pm.PackageManager
import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Is a manifest permission held by **this** process, asked from inside it.
 *
 * Row 33 of the measurement file is why this port exists: a responder-side refusal is invisible to the
 * caller *afterwards* (`startActivity` returns normally and throws nothing whether the uninstaller
 * refuses or draws its dialog, row 32) and knowable *beforehand* — `checkSelfPermission` answered
 * `GRANTED(0)` on the declaring build and `DENIED(-1)` without it. So detection is a **precondition**,
 * never a better failure signal.
 *
 * **`fun interface` rather than `interface`** (Task 2). Every test fixture in this family is the same
 * one-line answer — `PermissionPresence { true }` for the tests whose subject is the registry's
 * *contents*, a set-membership lambda for the tests whose subject is the filter — and a Kotlin
 * `interface` admits no SAM conversion, so each of those would otherwise be a four-line `object`
 * expression. One abstract method is all this port will ever have: the question "is this permission
 * held" has no second form.
 */
fun interface PermissionPresence {
    fun isGranted(permission: String): Boolean
}

class ContextPermissionPresence @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionPresence {
    override fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * The one **production** statement of which manifest permissions a registered tool needs.
 *
 * It exists in `:data:repository` rather than `:domain` because `android.permission.*` strings are
 * Android's vocabulary and `:domain` is KMP `commonMain`. It exists in production rather than in a test
 * because `ToolRegistryPermissionGuardTest` used to carry this column by hand, and its own KDoc named
 * that as its weak point: a hand-written column is as green when it is wrong as when it is right.
 *
 * **`null` is not `emptyList()`.** An id with no row means nobody has stated an answer, and the
 * **Tier-0 source and its worker** fail closed on it — that source does not advertise it, that worker
 * does not dispatch it, and the guard goes red. An explicit `emptyList()` is the statement "this tool
 * needs no permission". Stated no wider than it is true (review finding M1): `SystemIntentToolSource`,
 * `ShortcutToolSource` and Task 9's `MemoryToolSource` do **not** consult this catalog. No live defect
 * follows — every row of theirs is `emptyList()` — but a KDoc claiming a property the code does not
 * have is the exact failure mode this block exists to remove.
 *
 * Shortcut tools (`shortcut:` prefix) carry no row by construction: their ids are device-dependent.
 * That family is answered where it always was — in the guard, against the measured fact that shortcut
 * host access is the `android.app.role.HOME` role and not a manifest permission at all.
 */
class ToolPermissionCatalog @Inject constructor() {

    fun permissionsFor(id: ToolId): List<String>? = ROWS[id]

    fun rows(): Map<ToolId, List<String>> = ROWS

    private companion object {
        val ROWS: Map<ToolId, List<String>> = mapOf(
            ToolIds.LAUNCH_APP to emptyList(),
            ToolIds.PLAY_STORE_SEARCH to emptyList(),
            Tier0ToolIds.SET_TIMER to listOf("com.android.alarm.permission.SET_ALARM"),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to emptyList(),
            Tier0ToolIds.SET_ALARM to listOf("com.android.alarm.permission.SET_ALARM"),
            // Task 7. `normal`, install-time, granted with no prompt (row 28). Without it the
            // uninstaller starts and dies in ~190 ms drawing nothing, and `startActivity` still
            // returns normally — which is the whole reason this is a precondition rather than a
            // failure signal. Its limit, per spec §7.7: a row here closes exactly ONE cause of
            // refusal, a missing permission. Device policy, a work profile and a package that cannot
            // be removed refuse for reasons no row in this map can see or predict.
            Tier0ToolIds.UNINSTALL_APP to listOf("android.permission.REQUEST_DELETE_PACKAGES"),
            // Task 9. MemoryToolSource does not consult this catalog (it writes to the launcher's own
            // store, never dispatches an intent), but the totality guard quantifies over the whole
            // federation regardless of which sources filter on it. emptyList() states "needs no
            // permission" — the true answer here — never "nobody has stated an answer".
            MemoryToolIds.SET_APP_ALIAS to emptyList(),
            MemoryToolIds.FORGET_APP_ALIAS to emptyList(),
            MemoryToolIds.FORGET_LEARNED_CHOICE to emptyList(),
            // A1″ Phase 3b, Task 2 (Slice A). Four navigating tools, each an EXPLICIT `emptyList()` —
            // "needs no permission" — not a missing row. `show_alarms` (AlarmClock.ACTION_SHOW_ALARMS,
            // rows 14/29), `open_camera` (MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, row 17: the app
            // holds no CAMERA permission, so the negative is readable here), `open_wifi_settings`
            // (Settings.ACTION_WIFI_SETTINGS, row 18) and `open_bluetooth_settings`
            // (Settings.ACTION_BLUETOOTH_SETTINGS, row 19: the app holds no BLUETOOTH* permission
            // either) all measured needing none.
            Tier0ToolIds.SHOW_ALARMS to emptyList(),
            Tier0ToolIds.OPEN_CAMERA to emptyList(),
            Tier0ToolIds.OPEN_WIFI_SETTINGS to emptyList(),
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to emptyList(),
            // A1″ Phase 3b, Task 3 (Slice B). Four more navigating tools, each an EXPLICIT
            // `emptyList()`. `open_battery_settings` (Intent.ACTION_POWER_USAGE_SUMMARY, row 20 —
            // resolving at all is a ROM-dependent premise measured on one device, see the descriptor),
            // `open_data_usage_settings` (Settings.ACTION_DATA_USAGE_SETTINGS, row 21),
            // `open_display_settings` (Settings.ACTION_DISPLAY_SETTINGS, row 22) and
            // `open_sound_settings` (Settings.ACTION_SOUND_SETTINGS, row 23) all measured needing none.
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to emptyList(),
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to emptyList(),
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to emptyList(),
            Tier0ToolIds.OPEN_SOUND_SETTINGS to emptyList(),
            // A1″ Phase 3b, Task 4 (Slice C). Three more navigating tools, each an EXPLICIT
            // `emptyList()`.
            //
            // **`open_location_settings`'s row is the one claim in this file that is measured
            // SUFFICIENT and never proved NECESSARY** (finding P2), and it is written here rather than
            // only on the descriptor because this is the row a reader checks. Rows 24/29/30/33:
            // `Settings.ACTION_LOCATION_SOURCE_SETTINGS` opened the screen with
            // `ACCESS_FINE_LOCATION: granted=false` — measured both from outside and, on row 33, from
            // inside the process (`PERMISSION_DENIED (raw=-1)`). So the *grant* is excluded; what is
            // NOT excluded is this app's *declaration* of that permission, which the prayer feature
            // already carries. `emptyList()` here therefore means **"no permission we must add"**, and
            // the necessity question is **OPEN** — a named limit, not a closed question, and a
            // numbered item on the acceptance checklist. Naming the permission instead would be
            // strictly worse: `Tier0IntentToolSource.available()` filters on `presence::isGranted`, so
            // the tool would be withheld on the owner's own phone, registered nowhere and green.
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to emptyList(),
            // `open_notification_settings` (row 25) — the action string is ours, not the platform's
            // (finding P1, see the worker's own constant); it needs no permission.
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to emptyList(),
            // `open_app_info` (Settings.ACTION_APPLICATION_DETAILS_SETTINGS + a package `Uri`, row 15)
            // opened the screen needing none — including, per run 2, for a target package with no
            // launcher activity.
            Tier0ToolIds.OPEN_APP_INFO to emptyList(),
        )
    }
}
