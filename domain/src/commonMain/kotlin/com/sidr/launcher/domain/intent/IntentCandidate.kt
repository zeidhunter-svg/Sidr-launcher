package com.sidr.launcher.domain.intent

data class IntentCandidate(
    val intent: LauncherIntent,
    val confidence: Float,
    val debugReason: String? = null,
)
