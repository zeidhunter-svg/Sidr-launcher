package com.sidr.launcher.domain.tool

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * How much of a footprint a tool leaves behind.
 *
 * [DURABLE] is the "irreversible steps are marked BEFORE execution" half of `DOC-HMA-3`. A0 marks and
 * gates on it; the compensation/rollback machinery is A4', and this block does not claim otherwise.
 * Both A0 tools are [TRANSIENT] — launching an app and opening a store page write no durable state —
 * so the gate below exists, is unit-tested, and never fires in this slice.
 */
enum class ToolDurability { TRANSIENT, DURABLE }

/**
 * What the registry knows about one tool. Carries **no user-facing copy**: the surface maps [id] to a
 * string resource in the feature layer. [ActionArg] / [ActionRiskLevel] / [PermissionFeature] are
 * reused unchanged rather than duplicated — the spec's "reused without a single edit" list.
 */
data class ToolDescriptor(
    val id: ToolId,
    val argSchema: List<ActionArg> = emptyList(),
    val risk: ActionRiskLevel,
    val durability: ToolDurability,
    val permissionGate: PermissionFeature? = null,
)
