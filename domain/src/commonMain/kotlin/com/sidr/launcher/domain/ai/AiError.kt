package com.sidr.launcher.domain.ai

/**
 * Provider-neutral failure surface for generation, carried by [AiChunk.Failed].
 *
 * Adapters map their transport / HTTP-status / error-body into one of these. **No field may ever
 * contain the API key or raw user/prompt content** — [detail] is a short, safe diagnostic string
 * only (a status hint, a parser note), never user text or credentials. `AiError` is its own domain
 * type, distinct from [com.sidr.launcher.domain.result.OperationError] (just as `CommandOutcome` is
 * distinct from `OperationResult`).
 *
 * Intended later mapping to UI (Block H-style `UiState.Error(retryable)`), documented here, not yet
 * implemented: [Offline] / [Network] / [Timeout] / [RateLimited] / [ServerError] / [Unknown] are
 * retryable; [MissingCredentials] / [Unauthorized] are not button-retryable (resolved by entering or
 * fixing a key); [InvalidRequest] is not retryable.
 */
sealed interface AiError {
    /** No network connectivity — the request was not attempted. */
    data object Offline : AiError

    /** No API key is configured for the active provider. */
    data object MissingCredentials : AiError

    /** The key was rejected by the provider (HTTP 401/403). */
    data object Unauthorized : AiError

    /** Rate limited (HTTP 429); [retryAfterMs] is the provider hint when present. */
    data class RateLimited(val retryAfterMs: Long? = null) : AiError

    /** First-token or idle-between-chunks deadline exceeded. */
    data object Timeout : AiError

    /** Connection/transport failure; [detail] is a short safe string (no user content). */
    data class Network(val detail: String? = null) : AiError

    /** Provider server error (HTTP 5xx). */
    data class ServerError(val statusCode: Int? = null) : AiError

    /** Other client-side rejection (HTTP 4xx, e.g. bad model/params); [detail] is safe-only. */
    data class InvalidRequest(val detail: String? = null) : AiError

    /** Anything not mapped above; [detail] is a short safe string (no user content). */
    data class Unknown(val detail: String? = null) : AiError
}
