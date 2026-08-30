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
    fun `a projected tool's id is the action's id, not a copy of its spelling`() {
        // Identity C (owner fork F1, 2026-08-29). The two must be equal because one is DERIVED from the
        // other in the adapter, not because someone kept two string literals in step. The derivation lives
        // in `SystemIntentToolSource` on purpose: `domain/tool` keeps no edge to `domain/action`, which is
        // the edge A0 left absent so the A1 fork stayed open.
        assertEquals(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP.value)
        assertEquals(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH.value)
    }
}
