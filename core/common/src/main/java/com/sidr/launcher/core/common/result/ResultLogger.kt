package com.sidr.launcher.core.common.result

/**
 * Logging contract for failed operations.
 *
 * Operates on **non-sensitive primitives only** — a coarse [FailureCategory] plus a
 * `retryable` flag — so that `core/common` does not need to reference the domain's
 * `OperationError`. This keeps the dependency direction clean: moving `OperationResult` /
 * `OperationError` to `:domain` introduces **no** `core/common -> domain` edge (Block A / A4).
 *
 * The bridge that maps `OperationError -> (FailureCategory, retryable)` belongs in a layer
 * that depends on both `:domain` and `:core:common` (e.g. `:data:repository` once logging is
 * wired). It is intentionally absent here while there are no callers.
 */
interface ResultLogger {
    fun logFailure(
        category: FailureCategory,
        retryable: Boolean = false,
        context: String? = null,
    )
}

/** Coarse, non-sensitive classification of a failure for logging/metrics. */
enum class FailureCategory {
    NETWORK,
    AI_UNAVAILABLE,
    PERMISSION_DENIED,
    DEVICE_NOT_CAPABLE,
    UNKNOWN,
}
