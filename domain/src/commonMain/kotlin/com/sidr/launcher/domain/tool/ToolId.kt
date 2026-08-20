package com.sidr.launcher.domain.tool

/**
 * Opaque, stable identifier of a registered tool. A value class over a raw [String] for the same
 * reason `ActionId` is one: the string is the identity a plan step references and, from A4' on, the
 * identity a model emits.
 */
@JvmInline
value class ToolId(val value: String)

/**
 * The two tools A0 registers. Their strings mirror the frozen `ActionIds` (ADR 3/4) and `ToolIdsTest`
 * pins that. They are repeated rather than imported so `domain/tool` and `domain/agent` never
 * reference the action vocabulary — the edge whose absence keeps the A1 fork (parallel vocabulary vs.
 * evolving `ActionCatalog` in place) genuinely open for A1'.
 */
object ToolIds {
    val LAUNCH_APP = ToolId("launch_app")
    val PLAY_STORE_SEARCH = ToolId("play_store_search")
}
