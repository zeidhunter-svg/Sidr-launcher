package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionIds
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `ToolIds` deliberately repeats two of the seven frozen `ActionIds` strings, because the A0 planner
 * names tools and `ToolId`/`ToolIds` must not import the action vocabulary — that absent *identifier*
 * edge is what keeps the A1 fork open. (`domain/tool` as a whole is not edge-free: `ToolDescriptor`
 * deliberately imports `ActionArg`/`ActionRiskLevel` to reuse them rather than clone them; this
 * narrower claim is about `ToolId`, not the package.) A duplicated constant that can drift silently is
 * a bug waiting to happen, so it is pinned here. `ActionIds`' seven values are frozen byte-for-byte by
 * ADR 3/4 — if this test ever fails, the fix is to correct `ToolIds`, never `ActionIds`.
 */
class ToolIdsTest {

    @Test
    fun `a projected tool's id is the action's id, not a copy of its spelling`() {
        // Identity C (owner fork F1, 2026-08-29). The two must be equal because one is DERIVED from the
        // other in the adapter, not because someone kept two string literals in step. The derivation lives
        // in `SystemIntentToolSource` on purpose: `ToolId`/`ToolIds` keep no edge to `domain/action`
        // (unlike `ToolDescriptor`, which deliberately does, to reuse `ActionArg`/`ActionRiskLevel`) —
        // that absent identifier edge is the one that keeps the A1 fork open.
        assertEquals(ActionIds.LAUNCH_APP.value, ToolIds.LAUNCH_APP.value)
        assertEquals(ActionIds.PLAY_STORE_SEARCH.value, ToolIds.PLAY_STORE_SEARCH.value)
    }
}
