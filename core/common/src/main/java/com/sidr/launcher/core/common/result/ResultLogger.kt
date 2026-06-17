package com.sidr.launcher.core.common.result

interface ResultLogger {
    fun logFailure(error: OperationError, context: String? = null)
}

fun <T> OperationResult<T>.logIfFailure(
    logger: ResultLogger,
    context: String? = null
): OperationResult<T> {
    if (this is OperationResult.Failure) {
        logger.logFailure(error, context)
    }
    return this
}
