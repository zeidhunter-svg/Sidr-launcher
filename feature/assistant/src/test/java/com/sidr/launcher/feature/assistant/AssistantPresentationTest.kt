package com.sidr.launcher.feature.assistant

import com.sidr.launcher.domain.ai.AiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DS-10 presentation mapping (plan Task 5). The ViewModel's status mapping is untouched, so these
 * tests build `AssistantStatus.Error` exactly the way `AssistantViewModel` does — from the real
 * `AiError` → `toAssistantError()`/`isButtonRetryable()`/`needsProviderSetup()` helpers — and assert
 * only what DS-10 added: the DS-5 what/why/next copy and which single action appears.
 *
 * I18N-1 Task 10: the mapper now returns `@StringRes` ids plus at most one argument, so an assertion
 * that pinned a sentence pins the exact id instead — and, where the *words* were the point (the cloud
 * disclosure, the "unknown" fallback, the provenance vocabulary), it pins the shipped English resource
 * value via [AssistantStrings] so the guarantee is not weakened to "some id was returned".
 */
class AssistantPresentationTest {

    private fun errorStatus(error: AiError) = AssistantStatus.Error(
        error = error.toAssistantError(),
        retryable = error.isButtonRetryable(),
        showProviderCta = error.needsProviderSetup(),
    )

    @Test fun `network error keeps retry as the only action`() {
        val presentation = errorStatus(AiError.Network()).toPresentation()

        assertEquals(R.string.assistant_action_retry, presentation.primaryLabel)
        assertEquals(R.string.assistant_error_why_network, presentation.why)
        assertNull("this sentence carries no runtime value", presentation.whyArg)
        assertEquals("No network connection.", AssistantStrings.en("assistant_error_why_network"))
        assertEquals(R.string.assistant_error_next_retry, presentation.next)
    }

    @Test fun `missing credentials keeps the provider CTA, never retry`() {
        val presentation = errorStatus(AiError.MissingCredentials).toPresentation()

        assertEquals(R.string.assistant_action_fix_provider, presentation.primaryLabel)
    }

    @Test fun `unauthorized keeps the provider CTA, never retry`() {
        val presentation = errorStatus(AiError.Unauthorized).toPresentation()

        assertEquals(R.string.assistant_action_fix_provider, presentation.primaryLabel)
    }

    @Test fun `invalid request offers no button that would just repeat the failure`() {
        val status = errorStatus(AiError.InvalidRequest("bad model"))

        assertFalse(status.retryable)
        assertFalse(status.showProviderCta)
        assertNull(status.toPresentation().primaryLabel)
    }

    @Test fun `every error surface states what failed, why, and what to do next`() {
        val errors = listOf(
            AiError.Offline,
            AiError.Network(),
            AiError.Timeout,
            AiError.RateLimited(),
            AiError.ServerError(500),
            AiError.Unknown(),
            AiError.MissingCredentials,
            AiError.Unauthorized,
            AiError.InvalidRequest("bad model"),
        )

        errors.forEach { error ->
            val presentation = errorStatus(error).toPresentation()
            assertEquals("title for $error", R.string.assistant_error_title, presentation.title)
            assertEquals("whatFailed for $error", R.string.assistant_error_what_failed, presentation.whatFailed)
            assertNotEquals("why for $error", 0, presentation.why)
            assertNotEquals("next for $error", 0, presentation.next)
        }

        // Offline and Network deliberately share one sentence (they rendered identical copy before
        // I18N-1 too); every other failure keeps its own. This also proves the ids are real, distinct
        // resources rather than a collapsed placeholder.
        val whyIds = errors.map { errorStatus(it).toPresentation().why }
        assertEquals("Offline and Network share one why; the other seven are distinct", 8, whyIds.toSet().size)

        // An id is only as good as the copy behind it: every error sentence must exist and be non-blank
        // in the resources that ship.
        AssistantStrings.englishKeys()
            .filter { it.startsWith("assistant_error_") }
            .forEach { key -> assertTrue("blank English copy for $key", AssistantStrings.en(key).isNotBlank()) }
    }

    @Test fun `server error carries its status code as the sentence's only argument`() {
        val withCode = errorStatus(AiError.ServerError(500)).toPresentation()
        val withoutCode = errorStatus(AiError.ServerError(null)).toPresentation()

        assertEquals(R.string.assistant_error_why_server, withCode.why)
        assertEquals("500", withCode.whyArg)
        assertEquals("Server error (%1\$s). Please retry.", AssistantStrings.en("assistant_error_why_server"))

        // Value-absent case is its own key, never the placeholder key fed a nested resource.
        assertEquals(R.string.assistant_error_why_server_no_code, withoutCode.why)
        assertNull(withoutCode.whyArg)
        assertEquals(
            "Server error (unknown). Please retry.",
            AssistantStrings.en("assistant_error_why_server_no_code"),
        )
    }

    @Test fun `invalid request carries its safe detail, or falls back to its own key`() {
        val withDetail = errorStatus(AiError.InvalidRequest("bad model")).toPresentation()
        val withoutDetail = errorStatus(AiError.InvalidRequest(null)).toPresentation()

        assertEquals(R.string.assistant_error_why_invalid_request, withDetail.why)
        assertEquals("bad model", withDetail.whyArg)

        assertEquals(R.string.assistant_error_why_invalid_request_no_detail, withoutDetail.why)
        assertNull(withoutDetail.whyArg)
        assertEquals(
            "Invalid request: check model / settings.",
            AssistantStrings.en("assistant_error_why_invalid_request_no_detail"),
        )
    }

    @Test fun `unknown error still renders safe non-technical copy`() {
        val presentation = AssistantStatus.Error(
            error = AssistantError.Unknown,
            retryable = true,
            showProviderCta = false,
        ).toPresentation()

        assertEquals(R.string.assistant_error_why_unknown, presentation.why)
        assertEquals("Something went wrong.", AssistantStrings.en("assistant_error_why_unknown"))
        assertEquals(R.string.assistant_action_retry, presentation.primaryLabel)
    }

    @Test fun `provider save failures are typed, and each one has its own message`() {
        val ids = ProviderSaveError.entries.map { it.messageRes() }

        assertEquals("every save failure needs its own sentence", ProviderSaveError.entries.size, ids.toSet().size)
        assertEquals(
            R.string.assistant_save_error_base_url_not_https,
            ProviderSaveError.BASE_URL_NOT_HTTPS.messageRes(),
        )
    }

    // ── Provenance: host + model + key presence, never the key or the raw URL ────────────────────

    @Test fun `provenance details carry host, model, and key presence only`() {
        val provenance = providerProvenance(
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "mistralai/mistral-7b-instruct",
            keySet = true,
        )

        assertEquals("openrouter.ai", provenance.host)
        assertEquals("mistralai/mistral-7b-instruct", provenance.modelId)
        assertEquals(
            listOf("openrouter.ai", "mistralai/mistral-7b-instruct", "key in keystore"),
            rendered(provenance),
        )
    }

    @Test fun `provenance never leaks the scheme or path of a malformed base url`() {
        val provenance = providerProvenance(baseUrl = "https://my org.com", modelId = "", keySet = false)

        assertNull("a malformed base URL must resolve to no host at all", provenance.host)
        val details = rendered(provenance)

        assertEquals(listOf("(unknown host)", "no key set"), details)
        assertFalse(details.any { it.contains("https://") })
    }

    @Test fun `cloud disclosure names the provider hand-off and the keystore, and promises nothing else is sent`() {
        val body = AssistantStrings.en("assistant_cloud_disclosure_body")

        assertTrue(body.contains("Send"))
        assertTrue(body.contains("Keystore"))
        assertTrue(body.contains("no history"))
        assertTrue(body.contains("no memory"))
    }

    @Test fun `no-provider copy promises nothing is sent yet`() {
        assertTrue(AssistantStrings.en("assistant_no_provider_body").contains("nothing is sent anywhere"))
    }

    /**
     * The list `AssistantScreen.providerProvenanceDetails` builds, resolved against the shipped
     * English resources — the composable itself needs a Compose host, its substitution rule does not.
     */
    private fun rendered(provenance: ProviderProvenance): List<String> = listOfNotNull(
        provenance.host ?: AssistantStrings.en("assistant_provider_unknown_host"),
        provenance.modelId,
        AssistantStrings.en(
            if (provenance.keySet) "assistant_provenance_key_in_keystore" else "assistant_provenance_no_key_set",
        ),
    )
}
