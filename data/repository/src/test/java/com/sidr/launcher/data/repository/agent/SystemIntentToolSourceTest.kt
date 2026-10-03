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
    fun `exactly four tools are registered, their ids derived from ActionIds`() {
        // Identity C (owner fork F1, 2026-08-29): the projected id must equal ActionIds directly. Since
        // A4′ phase 3a the two router actions with an executor are projected too (spec §7.12.3).
        assertEquals(
            listOf(
                ActionIds.LAUNCH_APP.value,
                ActionIds.PLAY_STORE_SEARCH.value,
                ActionIds.WEB_SEARCH.value,
                ActionIds.OPEN_URL.value,
            ),
            source.all().map { it.id.value },
        )
    }

    @Test
    fun `risk is carried over from the catalog, not restated`() {
        assertEquals(ActionRiskLevel.SAFE, source.find(ToolIds.LAUNCH_APP)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(ToolIds.PLAY_STORE_SEARCH)!!.risk)
        assertEquals(ActionRiskLevel.SAFE, source.find(InAppToolIds.WEB_SEARCH)!!.risk)
        assertEquals(ActionRiskLevel.CONFIRM, source.find(InAppToolIds.OPEN_URL)!!.risk)
    }

    @Test
    fun `the argument schema is carried over from the catalog`() {
        assertEquals(listOf("query"), source.find(ToolIds.LAUNCH_APP)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(ToolIds.PLAY_STORE_SEARCH)!!.argSchema.map { it.name })
        assertEquals(listOf("query"), source.find(InAppToolIds.WEB_SEARCH)!!.argSchema.map { it.name })
        assertEquals(listOf("url"), source.find(InAppToolIds.OPEN_URL)!!.argSchema.map { it.name })
    }

    @Test
    fun `every projected tool is transient - none writes durable state`() {
        assertEquals(List(4) { ToolDurability.TRANSIENT }, source.all().map { it.durability })
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
    fun `the three navigation families are answers, not tools`() {
        // Spec §7.12.3: open_settings, open_assistant and show_apps change what is on screen, not the
        // world. Making them tools would need a "worker in data -> navigation" channel the hard rules
        // forbid, so they stay variants of the model's answer.
        assertNull(source.find(ToolId("open_settings")))
        assertNull(source.find(ToolId("open_assistant")))
        assertNull(source.find(ToolId("show_apps")))
    }

    @Test
    fun `every projected tool declares the in-app level and an external effect`() {
        val source = SystemIntentToolSource(DefaultActionCatalog())

        assertEquals(List(4) { ToolLevels.IN_APP }, source.all().map { it.level })
        assertEquals(List(4) { ToolEffect.EXTERNAL }, source.all().map { it.effect })
    }
}
