package com.sidr.launcher.domain.tool

/**
 * Opaque, stable identifier of a registered tool. A value class over a raw [String] for the same
 * reason `ActionId` is one: the string is the identity a plan step references and, from A4' on, the
 * identity a model emits.
 */
@JvmInline
value class ToolId(val value: String)

/**
 * The two tools A0 registers. Their strings are pinned to the frozen `ActionIds` (ADR 3/4) by
 * `ToolIdsTest`, and `SystemIntentToolSource` (`:data:repository`) now *derives* a projected tool's id
 * from its `ActionId` at the point of projection, rather than copying it into a second literal here —
 * Identity C (owner fork F1, 2026-08-29). These constants stay because `TemplatePlanner` and the A0
 * workers still reference them as tool ids in `:domain`/`:domain:agent`. What the derivation does not
 * change: `domain/tool` still carries no import edge to `domain/action`, so these two strings are still
 * written out here rather than imported — that absent edge is what keeps the A1 fork (parallel
 * vocabulary vs. evolving `ActionCatalog` in place) genuinely open for A1'. No test can distinguish a
 * derived id from a correctly hand-copied one — both produce the same string; the derivation itself is
 * held by construction (one expression in `SystemIntentToolSource.project`), not by any test.
 */
object ToolIds {
    val LAUNCH_APP = ToolId("launch_app")
    val PLAY_STORE_SEARCH = ToolId("play_store_search")
}
