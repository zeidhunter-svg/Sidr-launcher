package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.primitive.SidrProvenanceLine
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing

enum class SidrMemoryType {
    LearnedPreference,
    ExplicitAlias,
    UserProvidedFact,
    TemporaryContext,
    SystemPolicy,
    AutomationState,
}

enum class SidrMemoryStatus {
    Active,
    Learning,
    NeedsConfirmation,
    NeedsReconfirmation,
    Inactive,
    Expired,
    Unavailable,
    Deleted,
}

@Composable
fun SidrMemoryItem(
    title: String,
    value: String,
    type: SidrMemoryType,
    status: SidrMemoryStatus,
    modifier: Modifier = Modifier,
    evidence: String? = null,
    provenance: String? = null,
    lastUsed: String? = null,
    localOnly: Boolean = true,
    leadingContent: (@Composable (() -> Unit))? = null,
    onOpen: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onForget: (() -> Unit)? = null,
) {
    val typeLabel = type.label()
    val statusLabel = status.label()
    val scopeLabel = if (localOnly) "LOCAL" else null
    val summary = listOfNotNull(
        typeLabel,
        "$title -> $value",
        evidence,
        provenance,
        lastUsed,
        scopeLabel,
        statusLabel,
    ).joinToString(", ")

    SidrSurface(
        tone = if (status == SidrMemoryStatus.Unavailable) SidrSurfaceTone.RAISED else SidrSurfaceTone.SURFACE,
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = summary },
    ) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                leadingContent?.invoke()
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    SidrText(text = typeLabel, role = SidrTextRole.SYSTEM)
                    SidrText(text = "\"$title\" -> $value", role = SidrTextRole.HUMAN_BODY)
                }
                SidrStatusChip(label = statusLabel, status = status.statusToken())
            }

            if (evidence != null || provenance != null || localOnly) {
                SidrMemoryEvidence(
                    evidence = evidence ?: statusLabel,
                    provenance = provenance,
                    localOnly = localOnly,
                )
            }

            lastUsed?.let {
                SidrText(text = it, role = SidrTextRole.PROVENANCE)
            }

            if (onOpen != null || onEdit != null || onForget != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    onOpen?.let {
                        SidrSecondaryButton(text = "Open", onClick = it)
                    }
                    onEdit?.let {
                        SidrSecondaryButton(
                            text = "Edit",
                            onClick = it,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                    onForget?.let {
                        SidrDestructiveButton(
                            text = "Forget",
                            onClick = it,
                            modifier = Modifier
                                .padding(start = Spacing.sm)
                                .semantics {
                                    contentDescription = "Forget ${typeLabel.lowercase()} $title"
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SidrMemoryEvidence(
    evidence: String,
    modifier: Modifier = Modifier,
    provenance: String? = null,
    localOnly: Boolean = true,
) {
    val details = buildList {
        add(evidence)
        provenance?.let { add(it) }
        if (localOnly) add("local only")
    }
    SidrProvenanceLine(
        source = "memory",
        details = details,
        modifier = modifier,
    )
}

internal fun SidrMemoryType.label(): String = when (this) {
    SidrMemoryType.LearnedPreference -> "LEARNED PREFERENCE"
    SidrMemoryType.ExplicitAlias -> "EXPLICIT ALIAS"
    SidrMemoryType.UserProvidedFact -> "USER PROVIDED FACT"
    SidrMemoryType.TemporaryContext -> "TEMPORARY CONTEXT"
    SidrMemoryType.SystemPolicy -> "SYSTEM POLICY"
    SidrMemoryType.AutomationState -> "AUTOMATION STATE"
}

internal fun SidrMemoryStatus.label(): String = when (this) {
    SidrMemoryStatus.Active -> "ACTIVE"
    SidrMemoryStatus.Learning -> "LEARNING"
    SidrMemoryStatus.NeedsConfirmation -> "NEEDS CONFIRMATION"
    SidrMemoryStatus.NeedsReconfirmation -> "NEEDS RECONFIRMATION"
    SidrMemoryStatus.Inactive -> "INACTIVE"
    SidrMemoryStatus.Expired -> "EXPIRED"
    SidrMemoryStatus.Unavailable -> "UNAVAILABLE"
    SidrMemoryStatus.Deleted -> "DELETED"
}

private fun SidrMemoryStatus.statusToken(): SidrStatus = when (this) {
    SidrMemoryStatus.Active -> SidrStatus.SUCCESS
    SidrMemoryStatus.Learning -> SidrStatus.INFO
    SidrMemoryStatus.NeedsConfirmation,
    SidrMemoryStatus.NeedsReconfirmation,
    -> SidrStatus.ATTENTION
    SidrMemoryStatus.Inactive,
    SidrMemoryStatus.Expired,
    SidrMemoryStatus.Unavailable,
    -> SidrStatus.CAUTION
    SidrMemoryStatus.Deleted -> SidrStatus.DANGER
}

@Preview(showBackground = true, backgroundColor = 0xFF131415)
@Composable
private fun SidrMemoryItemPreview() {
    SidrTheme(darkTheme = true) {
        SidrMemoryItem(
            title = "open bank",
            value = "Turkiye Finans",
            type = SidrMemoryType.LearnedPreference,
            status = SidrMemoryStatus.Active,
            evidence = "Based on confirmed choices",
            provenance = "Learned from ambiguous app launches",
            lastUsed = "Last used 8 Jul 2026",
            onForget = {},
        )
    }
}
