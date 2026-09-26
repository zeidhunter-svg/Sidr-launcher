package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceDisplayState
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceView

sealed interface LearnedChoiceEvidence {
    data object Unavailable : LearnedChoiceEvidence
    data class Learning(val streak: Int, val threshold: Int) : LearnedChoiceEvidence
    data object NeedsReconfirm : LearnedChoiceEvidence
    data object Confirmed : LearnedChoiceEvidence
}

sealed interface LearnedChoiceProvenance {
    data object ConfirmedChoices : LearnedChoiceProvenance
}

data class LearnedChoiceMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String,
    val status: SidrMemoryStatus,
    val evidence: LearnedChoiceEvidence,
    val provenance: LearnedChoiceProvenance,
    val lastUsed: String? = null,
    val localOnly: Boolean = true,
)

internal fun LearnedChoiceView.toMemoryUiModel(): LearnedChoiceMemoryUiModel {
    val state = displayState
    return LearnedChoiceMemoryUiModel(
        stableId = stableId,
        phrase = capabilityKey.query,
        targetLabel = targetLabel,
        targetPackageName = targetPackageName,
        status = state.toMemoryStatus(),
        evidence = state.toEvidence(),
        provenance = LearnedChoiceProvenance.ConfirmedChoices,
        localOnly = true,
    )
}

internal val LearnedChoiceView.stableId: String
    get() = "${capabilityKey.actionId.value}:${capabilityKey.query}"

private fun LearnedChoiceDisplayState.toMemoryStatus(): SidrMemoryStatus = when (this) {
    LearnedChoiceDisplayState.Unavailable -> SidrMemoryStatus.Unavailable
    is LearnedChoiceDisplayState.Learning -> SidrMemoryStatus.Learning
    LearnedChoiceDisplayState.NeedsReconfirm -> SidrMemoryStatus.NeedsReconfirmation
    LearnedChoiceDisplayState.Auto -> SidrMemoryStatus.Active
    LearnedChoiceDisplayState.AutoReady -> SidrMemoryStatus.Active
}

private fun LearnedChoiceDisplayState.toEvidence(): LearnedChoiceEvidence = when (this) {
    LearnedChoiceDisplayState.Unavailable -> LearnedChoiceEvidence.Unavailable
    is LearnedChoiceDisplayState.Learning -> LearnedChoiceEvidence.Learning(streak, threshold)
    LearnedChoiceDisplayState.NeedsReconfirm -> LearnedChoiceEvidence.NeedsReconfirm
    LearnedChoiceDisplayState.Auto -> LearnedChoiceEvidence.Confirmed
    LearnedChoiceDisplayState.AutoReady -> LearnedChoiceEvidence.Confirmed
}
