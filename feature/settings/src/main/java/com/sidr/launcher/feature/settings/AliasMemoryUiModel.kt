package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.memory.alias.AliasView
import com.sidr.launcher.domain.model.InstalledApp

data class AliasMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String,
    val status: SidrMemoryStatus,
    val evidence: String,
    val provenance: String,
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
    evidence = "User-declared alias",
    provenance = "Settings",
    localOnly = true,
)

internal fun InstalledApp.toAliasPickerUiModel(): AliasPickerAppUiModel = AliasPickerAppUiModel(
    packageName = packageName,
    label = label,
)
