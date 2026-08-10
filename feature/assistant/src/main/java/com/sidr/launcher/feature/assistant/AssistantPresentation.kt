package com.sidr.launcher.feature.assistant

import com.sidr.launcher.core.common.UiError
import java.net.URI

/**
 * DS-10 feature-local presentation mapping (spec §4, §7).
 *
 * Pure Kotlin, no Compose: the Assistant's *wording* — what failed, why, what to do next, which
 * action button appears, and what the cloud disclosure says — is decided here so it can be unit
 * tested without a screenshot or a Robolectric host, and so `AssistantScreen` stays a render.
 *
 * This mapper is presentation-only. It never touches the ViewModel's status mapping
 * ([AssistantStatus.Error.retryable] / [AssistantStatus.Error.showProviderCta] are consumed
 * verbatim), so DS-10 changes no runtime behaviour.
 */

/** Label of the Retry action; shown only for retryable errors. */
internal const val RETRY_LABEL = "Retry"

/** Label of the provider CTA; shown only for credential errors (missing key / unauthorized). */
internal const val PROVIDER_CTA_LABEL = "Fix provider settings"

/** Neutral sentinel for a `baseUrl` with no parseable host — never the raw string. */
internal const val UNKNOWN_HOST = "(unknown host)"

/**
 * Host-only display for the provenance line — never the raw base URL/scheme, never the key.
 * Falls back to a neutral sentinel (never the raw string) if the URL doesn't parse or carries no
 * host, so a malformed `baseUrl` (e.g. `"https://my org.com"` or `"https://"`, both of which throw
 * `URISyntaxException`) can never leak the scheme onto the screen.
 *
 * `AssistantViewModel.saveProvider` only validates the `https://` prefix before persisting, so this
 * fallback is load-bearing, not defensive decoration.
 */
internal fun providerHost(baseUrl: String): String =
    runCatching { URI(baseUrl).host }.getOrNull()?.takeIf { it.isNotBlank() } ?: UNKNOWN_HOST

/**
 * What a `SidrErrorSurface` renders for a failed generation (DS-5 what / why / next + at most one
 * action). [primaryLabel] is `null` for errors that are neither retryable nor credential-shaped
 * (e.g. an invalid request) — those get honest copy and no button that would repeat the failure.
 */
internal data class AssistantErrorPresentation(
    val title: String,
    val whatFailed: String,
    val why: String,
    val next: String,
    val primaryLabel: String?,
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
    val why = when (val err = error) {
        is UiError.Message -> err.text
        UiError.Network -> "No network connection."
        UiError.Unknown -> "Something went wrong."
    }
    return AssistantErrorPresentation(
        title = "Assistant could not reply",
        whatFailed = "The reply did not finish.",
        why = why,
        next = when {
            retryable -> "Nothing was saved. Send the same message again when you are ready."
            showProviderCta -> "Open provider settings and check the base URL, model, and API key."
            else -> "Nothing was saved. Rewording the message usually helps."
        },
        primaryLabel = when {
            retryable -> RETRY_LABEL
            showProviderCta -> PROVIDER_CTA_LABEL
            else -> null
        },
    )
}

// ── Cloud disclosure (spec §6) ───────────────────────────────────────────────────────────────────
// Assistant is the one surface that leaves the device, so it says so plainly and near Send. The
// disclosure carries the host and model only — never the base URL's scheme/path and never the key.

internal const val CLOUD_DISCLOSURE_TITLE = "Replies come from your provider"

internal const val CLOUD_DISCLOSURE_BODY =
    "When you tap Send, the message you typed is sent to the AI provider you configured. " +
        "Nothing else goes with it — no apps, no history, no location, no memory. " +
        "Your API key stays in this device's Keystore and is never shown or sent anywhere else."

internal const val NO_PROVIDER_TITLE = "No AI provider configured"

internal const val NO_PROVIDER_BODY =
    "The assistant needs a provider you configure and pay for. Until then nothing is sent anywhere, " +
        "and the rest of the launcher keeps working offline."

/** Provenance details for the configured-provider line: `CLOUD · <host> · <model> · KEY IN KEYSTORE`. */
internal fun providerProvenanceDetails(baseUrl: String, modelId: String, keySet: Boolean): List<String> =
    buildList {
        add(providerHost(baseUrl))
        if (modelId.isNotBlank()) add(modelId)
        add(if (keySet) "key in keystore" else "no key set")
    }
