package com.sidr.launcher.consumer.jvm.tool

import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Test

class SandboxToolInversesTest {

    private val inverses = SandboxToolInverses()

    @Test
    fun `a rename is undone by renaming the result back to its old name`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    SandboxToolIds.RENAME_FILE,
                    mapOf(SandboxKeys.PATH to ArgSource.Literal("/box/ledger.old"), SandboxKeys.NEW_NAME to ArgSource.Literal("ledger.txt")),
                ),
            ),
            inverses.undoOf(
                ResolvedInvocation(SandboxToolIds.RENAME_FILE, mapOf(SandboxKeys.PATH to "/box/ledger.txt", SandboxKeys.NEW_NAME to "ledger.old")),
                ToolResult.Effected(ToolOutput(mapOf(SandboxKeys.RENAMED_PATH to "/box/ledger.old", SandboxKeys.PREVIOUS_NAME to "ledger.txt"))),
            ),
        )
    }

    @Test
    fun `a deleted file cannot be undone - nothing kept it`() {
        assertEquals(Undo.NotUndoable, inverses.undoOf(ResolvedInvocation(SandboxToolIds.DELETE_FILE), ToolResult.Effected()))
    }

    @Test
    fun `a read leaves nothing to undo`() {
        assertEquals(Undo.NothingToUndo, inverses.undoOf(ResolvedInvocation(SandboxToolIds.WORKSPACE_INFO), ToolResult.Effected()))
        assertEquals(Undo.NothingToUndo, inverses.undoOf(ResolvedInvocation(SandboxToolIds.FIND_FILE), ToolResult.Effected()))
    }

    @Test
    fun `a rename that reported nothing cannot be undone`() {
        assertEquals(Undo.NotUndoable, inverses.undoOf(ResolvedInvocation(SandboxToolIds.RENAME_FILE), ToolResult.Effected()))
    }
}
