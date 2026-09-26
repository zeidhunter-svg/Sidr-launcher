package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.action.LauncherAction
import com.sidr.launcher.domain.permission.PermissionFeature

/**
 * Transient, UI-layer state for a router-proposed [action] awaiting the user's go-ahead (AIL-5).
 *
 * AIL-4 surfaces a proposal that is **never** auto-executed (Fork R4). AIL-5 turns it into one of two
 * affordances, decided by [requiresConfirmation]:
 * - `true` (a [com.sidr.launcher.domain.action.ActionRiskLevel.CONFIRM] family, or any unregistered id
 *   — the fail-safe default) → the DF-4 **confirm card** (`EXECUTE?` + `[riskLabel]` + CONFIRM/CANCEL).
 * - `false` (a `SAFE` family) → a lighter **one-tap** affordance. Still a tap — nothing runs silently.
 *
 * Android-free (the ViewModel builds it); the screen renders it and, on confirm, executes via
 * [LauncherViewModel.confirmRoutedAction]. Carries only display-safe strings.
 *
 * @property commandLine the `>`-prefixed command form shown on the card / chip (e.g. `open https://x.com`).
 * @property riskLabel the bracketed risk tag shown on the card (e.g. `CONFIRM`).
 * @property permissionGate the optional runtime permission the action needs; when non-null the screen
 *   routes confirm through the existing education flow instead of executing (inert in the MVP catalog —
 *   no family declares a gate — but wired so a future gated family is safe by construction).
 */
data class PendingRoutedAction(
    val action: LauncherAction,
    val commandLine: String,
    val riskLabel: String,
    val requiresConfirmation: Boolean,
    val permissionGate: PermissionFeature?,
)
