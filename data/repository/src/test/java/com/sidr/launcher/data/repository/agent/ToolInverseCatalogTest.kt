package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Test

/** A4′ phase 2, spec §6.2: which launcher tool undoes which, and — as often — why none does. */
class ToolInverseCatalogTest {

    private val catalog = ToolInverseCatalog()

    private fun effected(vararg values: Pair<String, String>) = ToolResult.Effected(ToolOutput(mapOf(*values)))

    @Test
    fun `a new alias is undone by forgetting the key that was written`() {
        assertEquals(
            Undo.By(ToolInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to ArgSource.Literal("вельтрикс")))),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "  ВЕЛЬТРИКС ")),
                effected(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to ""),
            ),
        )
    }

    /** Review Focus 2. Forgetting here would delete the user's older alias along with ours. */
    @Test
    fun `an alias that replaced another is undone by pointing it back, not by forgetting it`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    MemoryToolIds.SET_APP_ALIAS,
                    mapOf("app" to ArgSource.Literal("net.fixture.parcel"), "phrase" to ArgSource.Literal("вельтрикс")),
                ),
            ),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.SET_APP_ALIAS, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
                effected(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"),
            ),
        )
    }

    @Test
    fun `a forgotten alias is undone by setting it again`() {
        assertEquals(
            Undo.By(
                ToolInvocation(
                    MemoryToolIds.SET_APP_ALIAS,
                    mapOf("app" to ArgSource.Literal("net.fixture.parcel"), "phrase" to ArgSource.Literal("вельтрикс")),
                ),
            ),
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "вельтрикс")),
                effected(MemoryToolKeys.PREVIOUS_APP to "net.fixture.parcel"),
            ),
        )
    }

    @Test
    fun `forgetting an alias that did not exist leaves nothing to undo`() {
        assertEquals(
            Undo.NothingToUndo,
            catalog.undoOf(
                ResolvedInvocation(MemoryToolIds.FORGET_APP_ALIAS, mapOf("phrase" to "вельтрикс")),
                effected(MemoryToolKeys.PREVIOUS_APP to ""),
            ),
        )
    }

    @Test
    fun `a learned choice, a running timer, a removed app and a shortcut cannot be undone here - a screen leaves nothing`() {
        val cases = mapOf(
            MemoryToolIds.FORGET_LEARNED_CHOICE to Undo.NotUndoable,
            Tier0ToolIds.SET_TIMER to Undo.NotUndoable,
            Tier0ToolIds.UNINSTALL_APP to Undo.NotUndoable,
            ToolId("shortcut:net.fixture.alpha/compose") to Undo.NotUndoable,
            Tier0ToolIds.OPEN_WIFI_SETTINGS to Undo.NothingToUndo,
        )
        cases.forEach { (id, expected) ->
            assertEquals("$id", expected, catalog.undoOf(ResolvedInvocation(id), ToolResult.Effected()))
        }
    }
}
