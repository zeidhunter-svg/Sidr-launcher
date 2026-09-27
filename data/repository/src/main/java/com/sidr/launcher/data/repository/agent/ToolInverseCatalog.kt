package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import javax.inject.Inject

/**
 * How each tool the launcher authors is undone (A4′ phase 2, spec §6.2). **Compensable: one family of
 * six, and within it two tools of three** — said, not implied:
 *  - `set_app_alias` → forget the key it wrote, or, if it replaced an alias, point the key back;
 *  - `forget_app_alias` → set the alias again, if there was one;
 *  - everything else either leaves nothing that stays (a screen, a launch, a search) or changed
 *    something no registered tool puts back (a running timer, an alarm, a removed app, a learned
 *    streak).
 *
 * A missing row means [Undo.NotUndoable] — which is also the answer for every app-shortcut tool, whose
 * publisher's intent is unknown. `ToolInverseCatalogGuardTest` (`:app`) makes every AUTHORED tool have
 * an explicit row, so a new one cannot be silently misnamed.
 *
 * The inverses are the tools' own siblings, and they pass the same gates (spec §6.3): both are `SAFE`
 * today, so undo asks nothing, exactly as the act asked nothing.
 */
class ToolInverseCatalog @Inject constructor() : ToolInverses {

    enum class Row { BY_INVERSE, NOTHING_TO_UNDO, NOT_UNDOABLE }

    fun rows(): Map<ToolId, Row> = ROWS

    override fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo = when (forward.id) {
        MemoryToolIds.SET_APP_ALIAS -> {
            val key = result.output.values[MemoryToolKeys.STORED_PHRASE].orEmpty()
            val previous = result.output.values[MemoryToolKeys.PREVIOUS_APP].orEmpty()
            when {
                key.isBlank() -> Undo.NotUndoable
                previous.isBlank() -> Undo.By(
                    ToolInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to ArgSource.Literal(key))),
                )
                else -> Undo.By(
                    ToolInvocation(
                        MemoryToolIds.SET_APP_ALIAS,
                        mapOf("app" to ArgSource.Literal(previous), "phrase" to ArgSource.Literal(key)),
                    ),
                )
            }
        }
        MemoryToolIds.FORGET_APP_ALIAS -> {
            val phrase = forward.args["phrase"].orEmpty()
            val previous = result.output.values[MemoryToolKeys.PREVIOUS_APP].orEmpty()
            if (phrase.isBlank() || previous.isBlank()) {
                Undo.NothingToUndo
            } else {
                Undo.By(
                    ToolInvocation(
                        MemoryToolIds.SET_APP_ALIAS,
                        mapOf("app" to ArgSource.Literal(previous), "phrase" to ArgSource.Literal(phrase)),
                    ),
                )
            }
        }
        else -> when (ROWS[forward.id]) {
            Row.NOTHING_TO_UNDO -> Undo.NothingToUndo
            Row.BY_INVERSE, Row.NOT_UNDOABLE, null -> Undo.NotUndoable
        }
    }

    private companion object {
        val ROWS: Map<ToolId, Row> = mapOf(
            ToolIds.LAUNCH_APP to Row.NOTHING_TO_UNDO,
            ToolIds.PLAY_STORE_SEARCH to Row.NOTHING_TO_UNDO,
            // A real timer / alarm runs (spec §6.2, rows 13/29 of the A1″ measurements); no tool removes one.
            Tier0ToolIds.SET_TIMER to Row.NOT_UNDOABLE,
            Tier0ToolIds.SET_ALARM to Row.NOT_UNDOABLE,
            // DURABLE, and it reports HandedOff — which Compensation never offers anyway.
            Tier0ToolIds.UNINSTALL_APP to Row.NOT_UNDOABLE,
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.SHOW_ALARMS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_CAMERA to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_WIFI_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_SOUND_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to Row.NOTHING_TO_UNDO,
            Tier0ToolIds.OPEN_APP_INFO to Row.NOTHING_TO_UNDO,
            MemoryToolIds.SET_APP_ALIAS to Row.BY_INVERSE,
            MemoryToolIds.FORGET_APP_ALIAS to Row.BY_INVERSE,
            // A learned streak cannot be put back by any tool.
            MemoryToolIds.FORGET_LEARNED_CHOICE to Row.NOT_UNDOABLE,
        )
    }
}
