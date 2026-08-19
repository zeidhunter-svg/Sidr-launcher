package com.sidr.launcher.domain.intent

class DefaultIntentConfidencePolicy(
    override val autoExecuteThreshold: Float = 0.85f,
    override val suggestThreshold: Float = 0.50f,
) : IntentConfidencePolicy
