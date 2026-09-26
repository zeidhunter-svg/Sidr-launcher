package com.sidr.launcher.feature.assistant

import androidx.annotation.StringRes
import java.net.URI

/**
 * DS-10 feature-local presentation mapping (spec §4, §7).
 *
 * Pure Kotlin, no Compose: the Assistant's *wording* — what failed, why, what to do next, which
 * action button appears, and what the cloud disclosure says — is decided here so it can be unit
 * tested without a screenshot or a Robolectric host, and so `AssistantScreen` stays a render.
 *
 * I18N-1 (Task 10): this mapper now decides **which string** rather than **what text** — it returns
 * `@StringRes` ids (plus at most one runtime argument) and `AssistantScreen` resolves them through
 * the `sidrString` seam. No sentence is ever assembled by concatenation, and no resource id is ever
 * nested inside another resource's argument: an error whose sentence carries a nullable value gets
 * **two** keys (value-present / value-absent) and the choice is made here.
 *
 * This mapper is presentation-only. It never touches the ViewModel's status mapping
 * ([AssistantStatus.Error.retryable] / [AssistantStatus.Error.showProviderCta] are consumed
 * verbatim), so DS-10 changes no runtime behaviour.
 */

/**
 * Host-only display for the provenance line — never the raw base URL/scheme, never the key.
 * Returns `null` (not the raw string) if the URL doesn't parse or carries no host, so a malformed
 * `baseUrl` (e.g. `"https://my org.com"` or `"https://"`, both of which throw `URISyntaxException`)
 * can never leak the scheme onto the screen; the renderer substitutes the neutral
 * `assistant_provider_unknown_host` sentinel for `null`.
 *
 * `AssistantViewModel.saveProvider` only validates the `https://` prefix before persisting, so this
 * fallback is load-bearing, not defensive decoration.
 */
internal fun providerHost(baseUrl: String): String? =
    runCatching { URI(baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() }

/**
 * What a `SidrErrorSurface` renders for a failed generation (DS-5 what / why / next + at most one
 * action). [primaryLabel] is `null` for errors that are neither retryable nor credential-shaped
 * (e.g. an invalid request) — those get honest copy and no button that would repeat the failure.
 *
 * [whyArg] is the single runtime value some `why` sentences carry (a server status code, a provider's
 * safe diagnostic detail); it is `null` for every sentence that has no placeholder.
 */
internal data class AssistantErrorPresentation(
    @StringRes val title: Int,
    @StringRes val whatFailed: Int,
    @StringRes val why: Int,
    val whyArg: String?,
    @StringRes val next: Int,
    @StringRes val primaryLabel: Int?,
)

/**
 * Maps a failed generation to DS-5 error copy.
 *
 * Retryable and credential errors are mutually exclusive by construction in `AssistantViewModel`
 * (`needsProviderSetup()` is true only for `MissingCredentials`/`Unauthorized`, which
 * `isButtonRetryable()` rejects), so at most one action ever appears — the same single-button
 * behaviour the pre-DS-10 screen had.
 */
internal fun AssistantStatus.Error.toPresentation(): AssistantErrorPresentation {
    val err = error
    return AssistantErrorPresentation(
        title = R.string.assistant_error_title,
        whatFailed = R.string.assistant_error_what_failed,
        why = err.whyRes(),
        whyArg = err.whyArg(),
        next = when {
            retryable -> R.string.assistant_error_next_retry
            showProviderCta -> R.string.assistant_error_next_provider
            else -> R.string.assistant_error_next_reword
        },
        primaryLabel = when {
            retryable -> R.string.assistant_action_retry
            showProviderCta -> R.string.assistant_action_fix_provider
            else -> null
        },
    )
}

/**
 * The `why` sentence for a typed failure. `ServerError`/`InvalidRequest` each own two keys instead of
 * embedding a second resource in the first one's argument: the value-absent wording ("unknown",
 * "check model / settings") is copy a translator must be able to phrase inside the whole sentence.
 */
@StringRes
private fun AssistantError.whyRes(): Int = when (this) {
    AssistantError.Network -> R.string.assistant_error_why_network
    AssistantError.Unknown -> R.string.assistant_error_why_unknown
    AssistantError.MissingCredentials -> R.string.assistant_error_why_missing_credentials
    AssistantError.Unauthorized -> R.string.assistant_error_why_unauthorized
    AssistantError.RateLimited -> R.string.assistant_error_why_rate_limited
    AssistantError.Timeout -> R.string.assistant_error_why_timeout
    is AssistantError.ServerError ->
        if (statusCode != null) R.string.assistant_error_why_server else R.string.assistant_error_why_server_no_code
    is AssistantError.InvalidRequest ->
        if (detail != null) {
            R.string.assistant_error_why_invalid_request
        } else {
            R.string.assistant_error_why_invalid_request_no_detail
        }
}

/** The single runtime argument [whyRes] needs, or null when the chosen sentence has no placeholder. */
private fun AssistantError.whyArg(): String? = when (this) {
    is AssistantError.ServerError -> statusCode?.toString()
    is AssistantError.InvalidRequest -> detail
    else -> null
}

/** Inline validation / save-failure message for the provider form. */
@StringRes
internal fun ProviderSaveError.messageRes(): Int = when (this) {
    ProviderSaveError.BASE_URL_NOT_HTTPS -> R.string.assistant_save_error_base_url_not_https
    ProviderSaveError.KEY_SAVE_FAILED -> R.string.assistant_save_error_key_failed
    ProviderSaveError.CONFIG_SAVE_FAILED -> R.string.assistant_save_error_config_failed
}

// ── Cloud disclosure (spec §6) ───────────────────────────────────────────────────────────────────
// Assistant is the one surface that leaves the device, so it says so plainly and near Send. The
// disclosure carries the host and model only — never the base URL's scheme/path and never the key.
// Its wording is Class B locked copy (assistant_cloud_disclosure_* / assistant_no_provider_* in
// strings_locked.xml), read by AssistantScreen through the sidrString seam.

/**
 * Provenance segments for the configured-provider line: `CLOUD · <host> · <model> · KEY IN KEYSTORE`.
 *
 * Stays a pure value: [host] is `null` when [providerHost] found none (the renderer substitutes the
 * locked sentinel) and [modelId] is `null` when blank, so no branch of the rendered line depends on
 * anything but this data.
 */
internal data class ProviderProvenance(
    val host: String?,
    val modelId: String?,
    val keySet: Boolean,
)

internal fun providerProvenance(baseUrl: String, modelId: String, keySet: Boolean): ProviderProvenance =
    ProviderProvenance(
        host = providerHost(baseUrl),
        modelId = modelId.takeIf { it.isNotBlank() },
        keySet = keySet,
    )
