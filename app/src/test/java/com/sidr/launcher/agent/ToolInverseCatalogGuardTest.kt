package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.data.repository.agent.PermissionPresence
import com.sidr.launcher.data.repository.agent.SystemIntentToolSource
import com.sidr.launcher.data.repository.agent.Tier0IntentToolSource
import com.sidr.launcher.data.repository.agent.ToolInverseCatalog
import com.sidr.launcher.data.repository.agent.ToolPermissionCatalog
import com.sidr.launcher.data.repository.agent.memory.MemoryToolKeys
import com.sidr.launcher.data.repository.agent.memory.MemoryToolSource
import com.sidr.launcher.domain.tool.InvocationCheck
import com.sidr.launcher.domain.tool.InvocationValidator
import com.sidr.launcher.domain.tool.ResolvedInvocation
import com.sidr.launcher.domain.tool.ToolAdapter
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolFederation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolOutput
import com.sidr.launcher.domain.tool.ToolResult
import com.sidr.launcher.domain.tool.ToolWorker
import com.sidr.launcher.domain.tool.Undo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A4′ phase 2, Task 5. `ToolInverseCatalog` answers "how is this undone" for the tools the launcher
 * AUTHORS, and a missing row means `NotUndoable` — fail-safe, but silent: a new navigating tool without
 * a row would be named «не отменить» on a card when the truth is «нечего отменять». This guard makes a
 * new authored tool force the question, over the PRODUCTION federation's authored adapters. Shortcuts
 * are dynamic and have no row by design (spec §6.2: the publisher's intent is unknown).
 *
 * This is one more list that changes when a tool is added — the family A1″ residual (5) measured
 * (five → eight lists). It is registry-KEYED, so it cannot silently skip a tool, but it is still a list.
 */
class ToolInverseCatalogGuardTest {

    private val catalog = ToolInverseCatalog()

    private object NoopWorker : ToolWorker {
        override suspend fun invoke(invocation: ResolvedInvocation): ToolResult =
            ToolResult.Failed(com.sidr.launcher.domain.intent.CommandFailure.Generic)
    }

    /** Everything granted, for the reason `ToolRegistryPermissionGuardTest` states: a filtered registry would pass by absence. */
    private val authored = ToolFederation(
        listOf(
            ToolAdapter(ToolLevels.IN_APP, SystemIntentToolSource(DefaultActionCatalog()), NoopWorker),
            ToolAdapter(ToolLevels.SYSTEM_INTENT, Tier0IntentToolSource(ToolPermissionCatalog(), PermissionPresence { true }), NoopWorker),
            ToolAdapter(ToolLevels.LAUNCHER_MEMORY, MemoryToolSource(), NoopWorker),
        ),
    ).registry

    @Test
    fun `every authored tool has a row, so a new one forces the undo question`() {
        assertEquals(20, authored.all().size)
        assertEquals(authored.all().map { it.id }.toSet(), catalog.rows().keys)
    }

    @Test
    fun `a durable tool is never declared undoable`() {
        val durable = authored.all().filter { it.durability == ToolDurability.DURABLE }
        assertTrue("no DURABLE tool reached this guard — it would pass by absence", durable.isNotEmpty())
        durable.forEach { assertEquals("${it.id}", ToolInverseCatalog.Row.NOT_UNDOABLE, catalog.rows()[it.id]) }
    }

    /**
     * Every inverse a row promises is a registered tool whose arguments validate — the undo can run.
     * Both branches of each row: with a prior target (point back / set again) and, where the row has
     * one, without (a new alias → forget; a forget of nothing → nothing to undo, so skipped).
     */
    @Test
    fun `every inverse a row promises names a tool the same federation can run`() {
        val promising = catalog.rows().filterValues { it == ToolInverseCatalog.Row.BY_INVERSE }.keys
        assertTrue(promising.isNotEmpty())
        var checked = 0
        for (previous in listOf("net.fixture.parcel", "")) {
            val output = ToolOutput(mapOf(MemoryToolKeys.STORED_PHRASE to "вельтрикс", MemoryToolKeys.PREVIOUS_APP to previous))
            promising.forEach { id ->
                val undo = catalog.undoOf(
                    ResolvedInvocation(id, mapOf("app" to "net.fixture.quill", "phrase" to "вельтрикс")),
                    ToolResult.Effected(output),
                )
                if (undo is Undo.By) {
                    checked++
                    assertEquals("$id / previous='$previous' must validate", InvocationCheck.Valid, InvocationValidator.validate(undo.invocation, emptyList(), authored))
                }
            }
        }
        assertEquals("both rows with a prior target, and set_app_alias without one", 3, checked)
    }
}
