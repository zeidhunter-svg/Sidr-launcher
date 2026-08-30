package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.requiresConsent
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A1' Task 6: the federation's second real source. Two Android system intents that are not among the
 * frozen seven `ActionIds`, so they mint their own [com.sidr.launcher.domain.tool.ToolId]s rather than
 * projecting `ActionCatalog` the way [SystemIntentToolSource] does.
 */
class Tier0IntentToolSourceTest {

    @Test
    fun `the tier-0 source offers two tools at the system-intent level, both external and safe`() {
        val source = Tier0IntentToolSource()

        assertEquals(
            listOf(Tier0ToolIds.SET_TIMER, Tier0ToolIds.OPEN_SYSTEM_SETTINGS),
            source.all().map { it.id },
        )
        assertEquals(true, source.all().all { it.level == ToolLevels.SYSTEM_INTENT })
        assertEquals(true, source.all().all { it.effect == ToolEffect.EXTERNAL })
        assertEquals(true, source.all().none { requiresConsent(it.risk) })
    }

    @Test
    fun `set_timer takes a required duration and open_system_settings takes nothing`() {
        val source = Tier0IntentToolSource()

        assertEquals(listOf("duration"), source.find(Tier0ToolIds.SET_TIMER)!!.argSchema.map { it.name })
        assertEquals(emptyList<String>(), source.find(Tier0ToolIds.OPEN_SYSTEM_SETTINGS)!!.argSchema.map { it.name })
    }
}
