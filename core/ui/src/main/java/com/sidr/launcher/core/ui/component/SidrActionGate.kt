package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import androidx.compose.ui.unit.dp

/**
 * DS-3 Action Gate (spec §5.6). One shape handles confirmation and consent boundaries.
 *
 * Rules (spec §5.6):
 * - consequence is always visible;
 * - Cancel is always visible and does not perform the action;
 * - dismiss/back equals Cancel when hosted in a dismissable surface;
 * - confirming disables both controls and prevents duplicate confirm;
 * - URL/target text wraps, never silently truncates meaningful origin;
 * - risk uses [SidrRiskChip] + title/consequence text, not colour alone;
 * - feature modules map domain risk/action into this presentation interface.
 *
 * Presentation-only: no domain/data/feature imports.
 */
enum class SidrActionGateType {
    Confirmation,
    Permission,
    SensitiveData,
    ExternalHandoff,
    Destructive,
}

private fun SidrActionGateType.riskTone(): SidrRiskTone = when (this) {
    SidrActionGateType.Confirmation -> SidrRiskTone.Confirm
    SidrActionGateType.Permission -> SidrRiskTone.Confirm
    SidrActionGateType.SensitiveData -> SidrRiskTone.External
    SidrActionGateType.ExternalHandoff -> SidrRiskTone.External
    SidrActionGateType.Destructive -> SidrRiskTone.Destructive
}

private fun SidrActionGateType.label(): String = when (this) {
    SidrActionGateType.Confirmation -> "CONFIRM"
    SidrActionGateType.Permission -> "PERMISSION"
    SidrActionGateType.SensitiveData -> "SENSITIVE"
    SidrActionGateType.ExternalHandoff -> "EXTERNAL"
    SidrActionGateType.Destructive -> "DESTRUCTIVE"
}

/**
 * Shared consent gate. Composes [SidrSurface] (risk tone), [SidrText], [SidrRiskChip], [SidrProvenanceLine]
 * slot, and SIDR buttons. [confirming] disables both controls and prevents duplicate confirm.
 */
@Composable
fun SidrActionGate(
    type: SidrActionGateType,
    title: String,
    consequence: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    target: String? = null,
    reason: String? = null,
    provenance: (@Composable (() -> Unit))? = null,
    privacyText: String? = null,
    details: (@Composable (() -> Unit))? = null,
    confirming: Boolean = false,
) {
    val tone = if (type == SidrActionGateType.Destructive) SidrSurfaceTone.RISK else SidrSurfaceTone.SURFACE
    var confirmDispatched by remember(title, consequence, target) { mutableStateOf(false) }
    var cancelDispatched by remember(title, consequence, target) { mutableStateOf(false) }
    val controlsDisabled = confirming || confirmDispatched || cancelDispatched

    SidrSurface(tone = tone, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            // Title + risk chip — risk is label + marker, never colour alone (spec §5.6).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
                SidrRiskChip(label = type.label(), tone = type.riskTone())
            }

            // Consequence is always visible (spec §5.6).
            SidrText(text = consequence, role = SidrTextRole.HUMAN_BODY)

            // Target/URL wraps, never silently truncates (spec §5.6).
            target?.let {
                SidrText(text = it, role = SidrTextRole.COMMAND)
            }

            // Reason for the proposal (optional).
            reason?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }

            // Provenance slot — caller supplies a SidrProvenanceLine or equivalent.
            provenance?.invoke()

            // Privacy text (optional, for sensitive-data / permission gates).
            privacyText?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }

            // Details slot (optional, for extra structured content).
            details?.invoke()

            // Actions: Cancel always visible + does not perform the action; Confirm is one-shot.
            SidrSurfaceActions(
                primary = SidrSurfaceAction(confirmLabel) {
                    if (!confirming && !confirmDispatched && !cancelDispatched) {
                        confirmDispatched = true
                        onConfirm()
                    }
                },
                secondary = SidrSurfaceAction("Cancel") {
                    if (!confirming && !confirmDispatched && !cancelDispatched) {
                        cancelDispatched = true
                        onCancel()
                    }
                },
                primaryLoading = confirming,
                enabled = !controlsDisabled,
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrActionGatePreview() {
    SidrTheme(darkTheme = true) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp)) {
            SidrActionGate(
                type = SidrActionGateType.ExternalHandoff,
                title = "Open website",
                consequence = "This will open https://github.com in your browser.",
                target = "https://github.com",
                confirmLabel = "Continue",
                onConfirm = {},
                onCancel = {},
            )
            SidrActionGate(
                type = SidrActionGateType.Destructive,
                title = "Forget memory",
                consequence = "All learned choices will be permanently removed.",
                confirmLabel = "Forget",
                onConfirm = {},
                onCancel = {},
            )
            SidrActionGate(
                type = SidrActionGateType.Confirmation,
                title = "Run action",
                consequence = "This will execute the proposed action.",
                confirmLabel = "Confirm",
                onConfirm = {},
                onCancel = {},
                confirming = true,
            )
        }
    }
}
