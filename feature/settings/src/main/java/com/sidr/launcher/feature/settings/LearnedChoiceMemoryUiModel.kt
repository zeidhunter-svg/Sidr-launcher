package com.sidr.launcher.feature.settings

import com.sidr.launcher.core.ui.component.SidrMemoryStatus
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceDisplayState
import com.sidr.launcher.domain.memory.resolution.LearnedChoiceView

data class LearnedChoiceMemoryUiModel(
    val stableId: String,
    val phrase: String,
    val targetLabel: String,
    val targetPackageName: String,
    val status: SidrMemoryStatus,
    val evidence: String,
    val provenance: String,
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
        evidence = state.evidenceText(),
        provenance = "Learned from confirmed choices",
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

private fun LearnedChoiceDisplayState.evidenceText(): String = when (this) {
    LearnedChoiceDisplayState.Unavailable -> "Target unavailable"
    is LearnedChoiceDisplayState.Learning -> "Learning from confirmed choices (${streak}/${threshold})"
    LearnedChoiceDisplayState.NeedsReconfirm -> "Needs reconfirmation before auto-open"
    LearnedChoiceDisplayState.Auto -> "Based on confirmed choices"
    LearnedChoiceDisplayState.AutoReady -> "Based on confirmed choices"
}
