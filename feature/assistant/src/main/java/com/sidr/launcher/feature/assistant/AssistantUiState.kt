package com.sidr.launcher.feature.assistant

import com.sidr.launcher.core.common.UiError

data class AssistantUiState(
    val reply: String = "",
    val status: AssistantStatus = AssistantStatus.Idle,
    val form: ProviderFormState = ProviderFormState(),
)

sealed interface AssistantStatus {
    data object Idle : AssistantStatus
    data object Streaming : AssistantStatus

    /** Generation finished. [refused] = true when the model declined (REFUSAL stop reason). */
    data class Done(val refused: Boolean) : AssistantStatus

    /**
     * Generation failed. [retryable] drives the Retry button. [showProviderCta] is true for
     * credential errors (MissingCredentials / Unauthorized) — the fix is updating provider settings,
     * not retrying with the same config.
     */
    data class Error(
        val error: UiError,
        val retryable: Boolean,
        val showProviderCta: Boolean = false,
    ) : AssistantStatus
}

/** Non-secret provider form state; the API key is never held here. */
data class ProviderFormState(
    val baseUrl: String = "",
    val modelId: String = "",
    /** True iff a key is persisted in the Keystore for the current provider slot. */
    val keySet: Boolean = false,
    /** Inline validation / save error; null when clean. */
    val saveError: String? = null,
)
