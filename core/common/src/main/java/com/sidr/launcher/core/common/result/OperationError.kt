package com.sidr.launcher.core.common.result

sealed interface OperationError {
    data class NetworkError(val retryable: Boolean = true) : OperationError
    data object AiUnavailable : OperationError
    data class PermissionDenied(val permission: String) : OperationError
    data class DeviceNotCapable(val feature: String) : OperationError
    data class UnknownError(val reason: String? = null) : OperationError
}
