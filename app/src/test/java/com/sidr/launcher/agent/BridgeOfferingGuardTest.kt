package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.data.repository.agent.shortcut.ShortcutToolIds
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The offering over the PRODUCTION graph (spec §7.12.3: 22 authored − 3 memory = 19; criteria 6, 7).
 * `BridgeOfferingTest` (`:domain`) holds the filter on fixtures; this holds that the real federation,
 * through the real filter and our real descriptions, offers exactly the nineteen — with the shortcut
 * tier and the memory tools registered beside them, so "not offered" is not "not there".
 */
class BridgeOfferingGuardTest {

    private val federation = ProductionGraph.federation()
    private val offered = BridgeFilter.offeringOf(federation.registry, AuthoredToolDescriptions())
        .tools.map { it.descriptor.id.value }

    @Test
    fun `the production offering is the nineteen authored tools of the in-app and system levels`() {
        assertEquals(OFFERED_NINETEEN, offered.toSet())
        assertEquals("no tool is offered twice", offered.size, offered.toSet().size)
    }

    @Test
    fun `the shortcut tier and the launcher's memory are registered, and neither reaches the offering`() {
        val registered = federation.registry.all().map { it.id.value }
        assertTrue("control: a shortcut is registered", registered.any { it.startsWith(ShortcutToolIds.PREFIX) })
        assertTrue("control: the memory tools are registered", registered.contains("set_app_alias"))

        assertTrue(offered.none { it.startsWith(ShortcutToolIds.PREFIX) })
        assertTrue(offered.none { it in setOf("set_app_alias", "forget_app_alias", "forget_learned_choice") })
    }

    companion object {
        /**
         * The ids offered today (ADR 2, owner fork F3a-B) — the equality list a new tool edits. Written
         * out, never derived from a source. Which of them are FROZEN is `OfferedToolIdsFreezeGuardTest`'s
         * own list, not this one.
         */
        val OFFERED_NINETEEN: Set<String> = setOf(
            "launch_app", "play_store_search", "web_search", "open_url",
            "set_timer", "open_system_settings", "set_alarm", "uninstall_app", "show_alarms", "open_camera",
            "open_wifi_settings", "open_bluetooth_settings", "open_battery_settings", "open_data_usage_settings",
            "open_display_settings", "open_sound_settings", "open_location_settings",
            "open_notification_settings", "open_app_info",
        )
    }
}
