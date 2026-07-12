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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrShapes
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

enum class SidrActionProposalTone { Safe, Confirm, External, Destructive }

enum class SidrResultTone { Completed, Partial, Failed }

data class SidrSurfaceAction(
    val label: String,
    val onClick: () -> Unit,
)

private fun SidrActionProposalTone.riskTone(): SidrRiskTone = when (this) {
    SidrActionProposalTone.Safe -> SidrRiskTone.Safe
    SidrActionProposalTone.Confirm -> SidrRiskTone.Confirm
    SidrActionProposalTone.External -> SidrRiskTone.External
    SidrActionProposalTone.Destructive -> SidrRiskTone.Destructive
}

private fun SidrResultTone.status(): SidrStatus = when (this) {
    SidrResultTone.Completed -> SidrStatus.SUCCESS
    SidrResultTone.Partial -> SidrStatus.ATTENTION
    SidrResultTone.Failed -> SidrStatus.DANGER
}

private fun permissionNoticeStatus(label: String): SidrStatus = when (label.uppercase()) {
    "ENABLED", "GRANTED" -> SidrStatus.SUCCESS
    "BLOCKED", "DENIED" -> SidrStatus.DANGER
    "OPTIONAL" -> SidrStatus.INFO
    else -> SidrStatus.INFO
}

@Composable
fun SidrActionProposal(
    title: String,
    onExecute: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    tone: SidrActionProposalTone = SidrActionProposalTone.Safe,
    provenance: (@Composable (() -> Unit))? = null,
    onCancel: (() -> Unit)? = null,
    executing: Boolean = false,
    executeLabel: String = "Run",
) {
    var executeDispatched by remember(title, description) { mutableStateOf(false) }
    var cancelDispatched by remember(title, description) { mutableStateOf(false) }
    val controlsDisabled = executing || executeDispatched || cancelDispatched
    val surfaceTone = if (tone == SidrActionProposalTone.Destructive) SidrSurfaceTone.RISK else SidrSurfaceTone.SURFACE

    SidrSurface(tone = surfaceTone, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                SidrRiskChip(label = tone.name.uppercase(), tone = tone.riskTone())
            }
            description?.let { SidrText(text = it, role = SidrTextRole.HUMAN_BODY) }
            provenance?.invoke()
            SidrSurfaceActions(
                primary = SidrSurfaceAction(executeLabel) {
                    if (!executing && !executeDispatched && !cancelDispatched) {
                        executeDispatched = true
                        onExecute()
                    }
                },
                secondary = onCancel?.let {
                    SidrSurfaceAction("Cancel") {
                        if (!executing && !executeDispatched && !cancelDispatched) {
                            cancelDispatched = true
                            it()
                        }
                    }
                },
                primaryLoading = executing,
                enabled = !controlsDisabled,
            )
        }
    }
}

@Composable
fun SidrPermissionNotice(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    withoutPermission: String? = null,
    secondaryLabel: String = "Not now",
    onSecondary: (() -> Unit)? = null,
    status: String? = null,
    primaryLoading: Boolean = false,
) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                status?.let { SidrStatusChip(label = it, status = permissionNoticeStatus(it)) }
            }
            SidrText(text = body, role = SidrTextRole.HUMAN_BODY)
            withoutPermission?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }
            SidrSurfaceActions(
                primary = SidrSurfaceAction(primaryLabel, onPrimary),
                secondary = onSecondary?.let { SidrSurfaceAction(secondaryLabel, it) },
                primaryLoading = primaryLoading,
            )
        }
    }
}

@Composable
fun SidrPrivacyNotice(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    provenance: (@Composable (() -> Unit))? = null,
) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SidrText(text = title, role = SidrTextRole.HUMAN_TITLE)
            SidrText(text = body, role = SidrTextRole.HUMAN_BODY)
            provenance?.invoke()
        }
    }
}

@Composable
fun SidrResultSurface(
    tone: SidrResultTone,
    title: String,
    modifier: Modifier = Modifier,
    body: String? = null,
    provenance: (@Composable (() -> Unit))? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
) {
    val surfaceTone = if (tone == SidrResultTone.Failed) SidrSurfaceTone.RISK else SidrSurfaceTone.SURFACE
    SidrSurface(tone = surfaceTone, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                SidrStatusChip(label = tone.name.uppercase(), status = tone.status())
            }
            body?.let { SidrText(text = it, role = SidrTextRole.HUMAN_BODY) }
            provenance?.invoke()
            if (primaryAction != null || secondaryAction != null) {
                SidrSurfaceActions(primary = primaryAction, secondary = secondaryAction)
            }
        }
    }
}

@Composable
fun SidrErrorSurface(
    title: String,
    modifier: Modifier = Modifier,
    whatFailed: String? = null,
    why: String? = null,
    next: String? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
) {
    SidrSurface(tone = SidrSurfaceTone.RISK, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                SidrStatusChip(label = "FAILED", status = SidrStatus.DANGER)
            }
            whatFailed?.let { LabeledSafetyText(label = "WHAT", text = it) }
            why?.let { LabeledSafetyText(label = "WHY", text = it) }
            next?.let { LabeledSafetyText(label = "NEXT", text = it) }
            if (primaryAction != null || secondaryAction != null) {
                SidrSurfaceActions(primary = primaryAction, secondary = secondaryAction)
            }
        }
    }
}

@Composable
fun SidrOfflineState(
    title: String = "Offline",
    body: String,
    modifier: Modifier = Modifier,
    availableOffline: String? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                SidrStatusChip(label = "OFFLINE", status = SidrStatus.ATTENTION)
            }
            SidrText(text = body, role = SidrTextRole.HUMAN_BODY)
            availableOffline?.let { SidrText(text = it, role = SidrTextRole.PROVENANCE) }
            if (primaryAction != null || secondaryAction != null) {
                SidrSurfaceActions(primary = primaryAction, secondary = secondaryAction)
            }
        }
    }
}

@Composable
fun SidrBlockedState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    next: String? = null,
    primaryAction: SidrSurfaceAction? = null,
    secondaryAction: SidrSurfaceAction? = null,
) {
    SidrSurface(tone = SidrSurfaceTone.SURFACE, modifier = modifier.fillMaxWidth(), shape = SidrShapes.medium) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                SidrText(text = title, role = SidrTextRole.HUMAN_TITLE, modifier = Modifier.weight(1f))
                SidrStatusChip(label = "BLOCKED", status = SidrStatus.ATTENTION)
            }
            SidrText(text = body, role = SidrTextRole.HUMAN_BODY)
            next?.let { LabeledSafetyText(label = "NEXT", text = it) }
            if (primaryAction != null || secondaryAction != null) {
                SidrSurfaceActions(primary = primaryAction, secondary = secondaryAction)
            }
        }
    }
}

@Composable
private fun LabeledSafetyText(label: String, text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        SidrText(text = label, role = SidrTextRole.PROVENANCE)
        SidrText(text = text, role = SidrTextRole.HUMAN_BODY)
    }
}

@Composable
internal fun SidrSurfaceActions(
    primary: SidrSurfaceAction?,
    secondary: SidrSurfaceAction? = null,
    modifier: Modifier = Modifier,
    primaryLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val stacked = LocalDensity.current.fontScale >= 1.7f
    if (stacked) {
        Column(
            modifier = modifier.fillMaxWidth().padding(top = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            primary?.let {
                SidrPrimaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    enabled = enabled,
                    loading = primaryLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            secondary?.let {
                SidrSecondaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    enabled = enabled && !primaryLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    } else {
        Row(
            modifier = modifier.fillMaxWidth().padding(top = Spacing.sm),
            horizontalArrangement = Arrangement.End,
        ) {
            secondary?.let {
                SidrSecondaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    enabled = enabled && !primaryLoading,
                )
            }
            primary?.let {
                SidrPrimaryButton(
                    text = it.label,
                    onClick = it.onClick,
                    enabled = enabled,
                    loading = primaryLoading,
                    modifier = Modifier.padding(start = if (secondary != null) Spacing.md else 0.dp),
                )
            }
        }
    }
}
