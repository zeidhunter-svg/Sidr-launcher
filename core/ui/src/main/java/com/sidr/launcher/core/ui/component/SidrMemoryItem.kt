package com.sidr.launcher.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import com.sidr.launcher.core.ui.R
import com.sidr.launcher.core.ui.i18n.sidrString
import com.sidr.launcher.core.ui.primitive.SidrProvenanceLine
import com.sidr.launcher.core.ui.primitive.SidrStatus
import com.sidr.launcher.core.ui.primitive.SidrSurface
import com.sidr.launcher.core.ui.primitive.SidrSurfaceTone
import com.sidr.launcher.core.ui.primitive.SidrText
import com.sidr.launcher.core.ui.primitive.SidrTextRole
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.core.ui.theme.Spacing
import java.util.Locale

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
    val scopeLabel = if (localOnly) sidrString(R.string.ui_scope_local) else null
    // `typeLabel` is locked machine vocabulary (strings_locked.xml), not human copy, so its case fold
    // stays locale-independent — a Turkish default locale would lower "EXPLICIT ALIAS" to "explıcıt
    // alıas". Locale.ROOT preserves today's behaviour exactly.
    val forgetDescription = sidrString(
        R.string.ui_memory_forget_content_description,
        typeLabel.lowercase(Locale.ROOT),
        title,
    )
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
                        SidrSecondaryButton(text = sidrString(R.string.ui_action_open), onClick = it)
                    }
                    onEdit?.let {
                        SidrSecondaryButton(
                            text = sidrString(R.string.ui_action_edit),
                            onClick = it,
                            modifier = Modifier.padding(start = Spacing.sm),
                        )
                    }
                    onForget?.let {
                        SidrDestructiveButton(
                            text = sidrString(R.string.ui_action_forget),
                            onClick = it,
                            modifier = Modifier
                                .padding(start = Spacing.sm)
                                .semantics { contentDescription = forgetDescription },
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
    val localOnlyLabel = sidrString(R.string.ui_memory_provenance_local_only)
    val details = buildList {
        add(evidence)
        provenance?.let { add(it) }
        if (localOnly) add(localOnlyLabel)
    }
    SidrProvenanceLine(
        source = sidrString(R.string.ui_memory_provenance_source),
        details = details,
        modifier = modifier,
    )
}

@Composable
@ReadOnlyComposable
internal fun SidrMemoryType.label(): String = when (this) {
    SidrMemoryType.LearnedPreference -> sidrString(R.string.ui_memory_type_learned_preference)
    SidrMemoryType.ExplicitAlias -> sidrString(R.string.ui_memory_type_explicit_alias)
    SidrMemoryType.UserProvidedFact -> sidrString(R.string.ui_memory_type_user_provided_fact)
    SidrMemoryType.TemporaryContext -> sidrString(R.string.ui_memory_type_temporary_context)
    SidrMemoryType.SystemPolicy -> sidrString(R.string.ui_memory_type_system_policy)
    SidrMemoryType.AutomationState -> sidrString(R.string.ui_memory_type_automation_state)
}

@Composable
@ReadOnlyComposable
internal fun SidrMemoryStatus.label(): String = when (this) {
    SidrMemoryStatus.Active -> sidrString(R.string.ui_memory_status_active)
    SidrMemoryStatus.Learning -> sidrString(R.string.ui_memory_status_learning)
    SidrMemoryStatus.NeedsConfirmation -> sidrString(R.string.ui_memory_status_needs_confirmation)
    SidrMemoryStatus.NeedsReconfirmation -> sidrString(R.string.ui_memory_status_needs_reconfirmation)
    SidrMemoryStatus.Inactive -> sidrString(R.string.ui_memory_status_inactive)
    SidrMemoryStatus.Expired -> sidrString(R.string.ui_memory_status_expired)
    SidrMemoryStatus.Unavailable -> sidrString(R.string.ui_memory_status_unavailable)
    SidrMemoryStatus.Deleted -> sidrString(R.string.ui_memory_status_deleted)
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
