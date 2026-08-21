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
    /**
     * What this tool can hand to a later step (F6). **Declared, not inferred:** a step may only bind to
     * a key that appears here, so `InvocationValidator.validate` rejects a bad binding at plan time —
     * and again on every resume — instead of discovering it mid-run. Empty for a tool that produces
     * nothing, which is also what makes "nothing may be bound from it" the default.
     *
     * [ActionArg] is reused for outputs as well as inputs, so one type describes both ends of a
     * binding and the type check is a comparison rather than a mapping. A tool that declares an output
     * must return it on **every** result, including [ToolResult.Effected]: outputs are a property of
     * the tool, not of the branch it happened to take, and a schema that only sometimes holds is not a
     * schema.
     */
    val outputSchema: List<ActionArg> = emptyList(),
    val risk: ActionRiskLevel,
    val durability: ToolDurability,
    val permissionGate: PermissionFeature? = null,
)
