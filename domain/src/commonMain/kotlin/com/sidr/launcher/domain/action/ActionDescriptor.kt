package com.sidr.launcher.domain.action

import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * The catalog + risk + schema metadata for one registered action family — the layer that sits **above**
 * `ExecutableAction` (which stays the execution vocabulary) without changing it.
 *
 * A descriptor is what [ActionCatalog] enumerates: the AIL-5 confirmation UI reads [risk] and
 * [permissionGate]; the AIL-4 router renders [id]/[title]/[description]/[argSchema] to the model and
 * validates its proposed args against [argSchema].
 *
 * AIL-1 defines this **type** and the family vocabulary ([LauncherAction] / [ActionIds]); the concrete
 * catalog of descriptor instances (with per-family title/description/risk/args) is registered by the
 * data-layer [ActionCatalog] implementation in AIL-2, alongside the executors that act on them.
 *
 * @property id the family's stable [ActionId] (see [ActionIds]).
 * @property title short human label for the action.
 * @property description one line explaining what it does (also shown to the LLM router).
 * @property category coarse grouping ([ActionCategory]).
 * @property risk confirmation friction ([ActionRiskLevel]).
 * @property argSchema declared arguments; empty for zero-arg actions (settings, app grid).
 * @property permissionGate an optional [PermissionFeature] the action needs (education flow, AIL-5);
 *   `null` when the action needs no runtime permission.
 */
data class ActionDescriptor(
    val id: ActionId,
    val title: String,
    val description: String,
    val category: ActionCategory,
    val risk: ActionRiskLevel,
    val argSchema: List<ActionArg> = emptyList(),
    val permissionGate: PermissionFeature? = null,
)
