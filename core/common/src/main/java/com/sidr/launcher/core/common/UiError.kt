package com.sidr.launcher.core.common

/**
 * UI-layer error type. ViewModels map domain [com.sidr.launcher.domain.result.OperationError]
 * to [UiError] before emitting to the UI — keeping core:common free of any domain dependency.
 */
sealed interface UiError {
    /** A user-facing message that is safe to display (no stack traces, no sensitive data). */
    data class Message(val text: String) : UiError

    /** Could not reach the network. */
    data object Network : UiError

    /** Catch-all for errors that don't need a specific UI treatment. */
    data object Unknown : UiError
}
