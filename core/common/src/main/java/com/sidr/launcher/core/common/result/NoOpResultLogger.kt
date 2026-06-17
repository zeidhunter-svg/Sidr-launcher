package com.sidr.launcher.core.common.result

class NoOpResultLogger : ResultLogger {
    override fun logFailure(error: OperationError, context: String?) {
        // no-op: logging not wired yet
    }
}
