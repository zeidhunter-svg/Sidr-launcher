package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.memory.alias.AliasView
import com.sidr.launcher.domain.model.InstalledApp

sealed interface AliasEvidence {
    data object UserDeclared : AliasEvidence
}

sealed interface AliasProvenance {
    data object Settings : AliasProvenance
}

data class AliasMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String,
    val status: SidrMemoryStatus,
    val evidence: AliasEvidence,
    val provenance: AliasProvenance,
    val localOnly: Boolean = true,
)

data class AliasPickerAppUiModel(
    val packageName: String,
    val label: String,
)

internal fun AliasView.toMemoryUiModel(): AliasMemoryUiModel = AliasMemoryUiModel(
    stableId = phrase,
    phrase = phrase,
    targetLabel = targetLabel,
    targetPackageName = targetPackageName,
    status = SidrMemoryStatus.Active,
    evidence = AliasEvidence.UserDeclared,
    provenance = AliasProvenance.Settings,
    localOnly = true,
)

internal fun InstalledApp.toAliasPickerUiModel(): AliasPickerAppUiModel = AliasPickerAppUiModel(
    packageName = packageName,
    label = label,
)
