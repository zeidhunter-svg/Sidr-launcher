package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ToolIds` deliberately repeats two of the seven frozen `ActionIds` strings, because the A0 planner
 * names tools and must not import the action vocabulary (that edge is what keeps the A1 fork open).
 * A duplicated constant that can drift silently is a bug waiting to happen, so it is pinned here.
 * `ActionIds`' seven values are frozen byte-for-byte by ADR 3/4 — if this test ever fails, the fix is
 * to correct `ToolIds`, never `ActionIds`.
 */
class ToolIdsTest {

    @Test
    fun `tool ids mirror the frozen action ids byte-for-byte`() {
        assertEquals(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP.value)
        assertEquals(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH.value)
    }
}
