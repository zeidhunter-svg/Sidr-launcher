package com.sidr.launcher.domain.action

import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * The catalog + risk + schema metadata for one registered action family — the layer that sits **above**
 * `ExecutableAction` (which stays the execution vocabulary) without changing it.
 *
 * A descriptor is what [ActionCatalog] enumerates. The AIL-4 router and its AIL-5 confirm card, which
 * it was written for, were deleted in A4′ phase 3a (D3); two readers remain. `SystemIntentToolSource`
 * projects four families into `in_app` tools — [id], [argSchema], [risk] and [permissionGate] become the
 * tool's descriptor, which the engine's consent gate and argument validation read — and the
 * learned-choice decorator reads `launch_app`'s [risk]. [title] and [description] reach no runtime
 * path: the model reads our own authored descriptions (`AuthoredToolDescriptions`), not these.
 *
 * AIL-1 defines this **type** and the family vocabulary ([LauncherAction] / [ActionIds]); the concrete
 * catalog of descriptor instances (with per-family title/description/risk/args) is registered by the
 * data-layer [ActionCatalog] implementation in AIL-2, alongside the executors that act on them.
 *
 * @property id the family's stable [ActionId] (see [ActionIds]).
 * @property title short human label for the action.
 * @property description one line explaining what it does (shown to the deleted router; read by no
 *   runtime path today).
 * @property category coarse grouping ([ActionCategory]).
 * @property risk confirmation friction ([ActionRiskLevel]).
 * @property argSchema declared arguments; empty for zero-arg actions (settings, app grid).
 * @property permissionGate an optional [PermissionFeature] the action needs — carried onto the projected
 *   tool, where the engine's consent gate stops on it (`MISSING_PERMISSION`); `null` when the action
 *   needs no runtime permission.
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
