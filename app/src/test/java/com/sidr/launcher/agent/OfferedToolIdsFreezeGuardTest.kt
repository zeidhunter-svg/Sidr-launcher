package com.sidr.launcher.agent

import com.sidr.launcher.data.repository.agent.AuthoredToolDescriptions
import com.sidr.launcher.domain.ai.bridge.BridgeFilter
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * ADR 2 of A4′ phase 3a (owner fork F3a-B): the outbound contract with the model passed from the seven
 * `ActionIds` to the offered tools. Their VALUES are frozen — a rename or a removal orphans what phase 4
 * caches and what a model was taught, and needs a superseding ADR. Their NUMBER is not: a new tool is
 * offered by registering it, with no edit here (containment, not equality) — only
 * `BridgeOfferingGuardTest`'s equality list learns the new id.
 *
 * [FROZEN] is this guard's OWN list, written out here and read from nothing else (final review A2). It
 * used to be `BridgeOfferingGuardTest.OFFERED_NINETEEN` — the equality list, which a new tool must edit —
 * so a rename made in the source and in that list together left both guards green: this one had no red
 * set of its own. Editing [FROZEN] is the superseding-ADR act; adding a tool never touches it.
 */
class OfferedToolIdsFreezeGuardTest {

    @Test
    fun `the nineteen offered tool ids are frozen byte for byte`() {
        val offered = BridgeFilter.offeringOf(
            ProductionGraph.federation().registry,
            BridgeFilter.ANDROID_OFFERED_LEVELS,
            AuthoredToolDescriptions(),
        )
            .tools.map { it.descriptor.id.value }.toSet()

        assertEquals("a frozen id is no longer offered", emptySet<String>(), FROZEN - offered)
    }

    private companion object {
        /** The ids frozen by ADR 2 (2026-10-05), byte for byte. Never derived — not from a source, not from another guard. */
        val FROZEN: Set<String> = setOf(
            "launch_app",
            "play_store_search",
            "web_search",
            "open_url",
            "set_timer",
            "open_system_settings",
            "set_alarm",
            "uninstall_app",
            "show_alarms",
            "open_camera",
            "open_wifi_settings",
            "open_bluetooth_settings",
            "open_battery_settings",
            "open_data_usage_settings",
            "open_display_settings",
            "open_sound_settings",
            "open_location_settings",
            "open_notification_settings",
            "open_app_info",
        )
    }
}
