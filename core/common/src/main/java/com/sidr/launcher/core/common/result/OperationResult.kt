package com.sidr.launcher.core.common.result

sealed interface OperationResult<out T> {
    data class Success<T>(val value: T) : OperationResult<T>
    data class Failure(val error: OperationError) : OperationResult<Nothing>
}
