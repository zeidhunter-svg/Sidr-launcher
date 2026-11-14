package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolInverses
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo

/**
 * The second consumer's inverses (A4′ phase 2, spec §6.5): `rename_file` undoes itself, a read leaves
 * nothing, and `delete_file` cannot be undone — nothing kept the file. Built from what the rename
 * REPORTED, never from a re-read of the directory.
 */
class SandboxToolInverses : ToolInverses {
    override fun undoOf(forward: ResolvedInvocation, result: ToolResult.Effected): Undo = when (forward.id) {
        SandboxToolIds.RENAME_FILE -> {
            val renamed = result.output.values[SandboxKeys.RENAMED_PATH].orEmpty()
            val previous = result.output.values[SandboxKeys.PREVIOUS_NAME].orEmpty()
            if (renamed.isBlank() || previous.isBlank()) {
                Undo.NotUndoable
            } else {
                Undo.By(
                    ToolInvocation(
                        SandboxToolIds.RENAME_FILE,
                        mapOf(SandboxKeys.PATH to ArgSource.Literal(renamed), SandboxKeys.NEW_NAME to ArgSource.Literal(previous)),
                    ),
                )
            }
        }
        SandboxToolIds.WORKSPACE_INFO, SandboxToolIds.FIND_FILE -> Undo.NothingToUndo
        SandboxToolIds.DELETE_FILE -> Undo.NotUndoable
        else -> Undo.NotUndoable
    }
}
