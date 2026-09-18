package com.sidr.launcher.data.repository.agent

import android.content.Context
import android.content.pm.PackageManager
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
 */
interface PermissionPresence {
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
        )
    }
}
