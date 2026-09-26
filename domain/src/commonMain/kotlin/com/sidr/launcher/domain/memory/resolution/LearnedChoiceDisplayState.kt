package com.sidr.launcher.domain.memory.resolution

sealed interface LearnedChoiceDisplayState {
    data object Unavailable : LearnedChoiceDisplayState
    data class Learning(val streak: Int, val threshold: Int) : LearnedChoiceDisplayState
    data object NeedsReconfirm : LearnedChoiceDisplayState
    data object Auto : LearnedChoiceDisplayState
    data object AutoReady : LearnedChoiceDisplayState
}
