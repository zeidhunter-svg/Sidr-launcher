package com.sidr.launcher.domain.intent

interface IntentConfidencePolicy {
    val autoExecuteThreshold: Float
    val suggestThreshold: Float

    fun shouldAutoExecute(confidence: Float): Boolean =
        confidence >= autoExecuteThreshold

    fun shouldSuggest(confidence: Float): Boolean =
        confidence >= suggestThreshold

    fun isLowConfidence(confidence: Float): Boolean =
        confidence < suggestThreshold
}
