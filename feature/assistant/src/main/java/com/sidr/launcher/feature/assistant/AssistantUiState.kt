package com.sidr.launcher.feature.assistant

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
        val error: AssistantError,
        val retryable: Boolean,
        val showProviderCta: Boolean = false,
    ) : AssistantStatus
}

/**
 * Why a generation failed, as a value rather than a sentence (I18N-1 Task 10).
 *
 * Replaces `core.common.UiError` in this state slot: the wording now lives in string resources and is
 * chosen by `AssistantPresentation`, so the ViewModel carries the *reason* and the screen carries the
 * *language*. The mapping from `domain.ai.AiError` (`AiError.toAssistantError()`) is the one before
 * it, unchanged in every case: `Offline` and `Network` both land on [Network] because they already
 * produced identical output (`UiError.Network`) before this task — preservation, not a merge.
 *
 * Public because it is a property type of the public [AssistantStatus.Error]; nothing outside this
 * module consumes it (`:app` imports only `AssistantScreen`/`AssistantProviderScreen`/
 * `AssistantViewModel`).
 */
sealed interface AssistantError {
    data object MissingCredentials : AssistantError
    data object Unauthorized : AssistantError
    data object RateLimited : AssistantError
    data object Timeout : AssistantError

    /** [statusCode] is the provider's HTTP status when it reported one. */
    data class ServerError(val statusCode: Int?) : AssistantError

    /** [detail] is the provider's short, safe diagnostic — never user text or credentials. */
    data class InvalidRequest(val detail: String?) : AssistantError

    /** Offline and transport failures alike: the user-visible outcome is the same. */
    data object Network : AssistantError
    data object Unknown : AssistantError
}

/**
 * Why saving the provider form failed, as a value rather than a sentence (I18N-1 Task 10). The
 * wording lives in `strings.xml` and is selected by `ProviderSaveError.messageRes()`.
 */
enum class ProviderSaveError { BASE_URL_NOT_HTTPS, KEY_SAVE_FAILED, CONFIG_SAVE_FAILED }

/** Non-secret provider form state; the API key is never held here. */
data class ProviderFormState(
    val baseUrl: String = "",
    val modelId: String = "",
    /** True iff a key is persisted in the Keystore for the current provider slot. */
    val keySet: Boolean = false,
    /** Inline validation / save error; null when clean. */
    val saveError: ProviderSaveError? = null,
)
