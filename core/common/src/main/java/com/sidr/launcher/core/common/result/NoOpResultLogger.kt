package com.sidr.launcher.core.common.result

class NoOpResultLogger : ResultLogger {
    override fun logFailure(
        category: FailureCategory,
        retryable: Boolean,
        context: String?,
    ) {
        // no-op: logging not wired yet
    }
}
