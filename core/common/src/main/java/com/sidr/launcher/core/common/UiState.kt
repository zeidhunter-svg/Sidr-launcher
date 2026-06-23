package com.sidr.launcher.core.common

/**
 * Generic UI state wrapper used by ViewModels to drive Compose screens.
 * [T] is the success payload type (e.g. LauncherUiState).
 * Errors are expressed as [UiError] — a UI-layer type — so this file has no domain dependency.
 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>

    /**
     * A recoverable error surface.
     *
     * [retryable] expresses whether re-running the failed operation could plausibly succeed (e.g. a
     * transient network/unknown failure) — it is a property of the *state in context*, decided by
     * the ViewModel, not of the [UiError] identity. The retry **action** is intentionally not
     * carried here (no lambda in a `data class`, so equality / `distinctUntilChanged` stay intact);
     * the UI invokes a ViewModel method when [retryable] is true.
     */
    data class Error(val error: UiError, val retryable: Boolean = false) : UiState<Nothing>
}
