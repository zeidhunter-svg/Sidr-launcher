package com.sidr.launcher.domain.action

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the action-family vocabulary (AIL-1). These pin the wire identity that the AIL-4
 * router depends on: every [LauncherAction] variant must report its canonical [ActionId], and those
 * ids must be stable, unique lowercase snake_case strings.
 */
class LauncherActionTest {

    @Test
    fun `each variant reports its canonical action id`() {
        assertEquals(ActionIds.LAUNCH_APP, LauncherAction.LaunchApp("telegram").id)
        assertEquals(ActionIds.WEB_SEARCH, LauncherAction.WebSearch("flights").id)
        assertEquals(ActionIds.OPEN_SETTINGS, LauncherAction.OpenSettings.id)
        assertEquals(ActionIds.OPEN_ASSISTANT, LauncherAction.OpenAssistant("hi").id)
        assertEquals(ActionIds.SHOW_APPS, LauncherAction.ShowApps.id)
        assertEquals(ActionIds.OPEN_URL, LauncherAction.OpenUrl("https://x.com").id)
        assertEquals(ActionIds.PLAY_STORE_SEARCH, LauncherAction.PlayStoreSearch("signal").id)
    }

    @Test
    fun `variants carry their unresolved semantic args verbatim`() {
        assertEquals("telegram", (LauncherAction.LaunchApp("telegram")).query)
        assertEquals("flights to rome", (LauncherAction.WebSearch("flights to rome")).query)
        assertEquals("draft an email", (LauncherAction.OpenAssistant("draft an email")).prompt)
        assertEquals(null, LauncherAction.OpenAssistant().prompt)
        assertEquals("https://example.com", (LauncherAction.OpenUrl("https://example.com")).url)
        assertEquals("signal", (LauncherAction.PlayStoreSearch("signal")).query)
    }

    @Test
    fun `all registered ids are unique`() {
        val ids = ActionIds.ALL
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `all registered ids are stable lowercase snake_case wire strings`() {
        val expected = mapOf(
            ActionIds.LAUNCH_APP to "launch_app",
            ActionIds.WEB_SEARCH to "web_search",
            ActionIds.OPEN_SETTINGS to "open_settings",
            ActionIds.OPEN_ASSISTANT to "open_assistant",
            ActionIds.SHOW_APPS to "show_apps",
            ActionIds.OPEN_URL to "open_url",
            ActionIds.PLAY_STORE_SEARCH to "play_store_search",
        )
        expected.forEach { (id, wire) -> assertEquals(wire, id.value) }
        // Guard: ALL and the pinned expectations stay in lockstep.
        assertEquals(expected.keys, ActionIds.ALL.toSet())
        ActionIds.ALL.forEach { id ->
            assertTrue("`${id.value}` must be lowercase snake_case", id.value.matches(Regex("[a-z]+(_[a-z]+)*")))
        }
    }
}
