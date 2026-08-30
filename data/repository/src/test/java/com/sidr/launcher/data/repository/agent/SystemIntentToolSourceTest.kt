package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.action.DefaultActionCatalog
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A0 registers exactly two tools, both projected from the real `ActionCatalog`. The projection is
 * deliberately narrow: registering all seven families would hand the planner capabilities the slice
 * has not gated or traced.
 */
class SystemIntentToolSourceTest {

    private val source = SystemIntentToolSource(DefaultActionCatalog())

    @Test
    fun `exactly two tools are registered, their ids derived from ActionIds`() {
        // Identity C (owner fork F1, 2026-08-29): the projected id must equal ActionIds directly, not
        // ToolIds — ToolIds is itself only pinned to ActionIds by ToolIdsTest, so comparing against it
        // here would not catch the projection drifting from its source of truth.
        assertEquals(
            listOf(ActionIds.LAUNCH_APP.value, ActionIds.PLAY_STORE_SEARCH.value),
            source.all().map { it.id.value },
        )
    }

    @Test
    fun `risk is carried over from the catalog, not restated`() {
        assertEquals(ActionRiskLevel.SAFE, source.find(ToolIds.LAUNCH_APP)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(ToolIds.PLAY_STORE_SEARCH)!!.risk)
    }

    @Test
    fun `the argument schema is carried over from the catalog`() {
        assertEquals(listOf("query"), source.find(ToolIds.LAUNCH_APP)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(ToolIds.PLAY_STORE_SEARCH)!!.argSchema.map { it.name })
    }

    @Test
    fun `both A0 tools are transient - neither writes durable state`() {
        assertEquals(ToolDurability.TRANSIENT, source.find(ToolIds.LAUNCH_APP)!!.durability)
        assertEquals(ToolDurability.TRANSIENT, source.find(ToolIds.PLAY_STORE_SEARCH)!!.durability)
    }

    @Test
    fun `launch_app declares resolved_query as an output, play_store_search declares none`() {
        assertEquals(
            listOf("resolved_query"),
            source.find(ToolIds.LAUNCH_APP)!!.outputSchema.map { it.name },
        )
        assertTrue(source.find(ToolIds.PLAY_STORE_SEARCH)!!.outputSchema.isEmpty())
    }

    @Test
    fun `an action family that is not an A0 tool is not registered`() {
        assertNull(source.find(ToolId("web_search")))
        assertNull(source.find(ToolId("open_url")))
    }

    @Test
    fun `every projected tool declares the in-app level and an external effect`() {
        val source = SystemIntentToolSource(DefaultActionCatalog())

        assertEquals(listOf(ToolLevels.IN_APP, ToolLevels.IN_APP), source.all().map { it.level })
        assertEquals(listOf(ToolEffect.EXTERNAL, ToolEffect.EXTERNAL), source.all().map { it.effect })
    }
}
