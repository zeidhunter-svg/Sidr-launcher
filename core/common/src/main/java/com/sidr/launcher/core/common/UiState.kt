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
    data class Error(val error: UiError) : UiState<Nothing>
}
