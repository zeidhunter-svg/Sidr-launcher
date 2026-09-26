package com.sidr.launcher.domain.memory.resolution

data class LearnedChoiceView(
    val capabilityKey: CapabilityKey,
    val targetPackageName: String,
    val targetLabel: String,
    val displayState: LearnedChoiceDisplayState,
)
