package com.sidr.launcher.feature.assistant

import com.sidr.launcher.core.common.UiError
import com.sidr.launcher.domain.ai.AiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * DS-10 presentation mapping (plan Task 5). The ViewModel's status mapping is untouched, so these
 * tests build `AssistantStatus.Error` exactly the way `AssistantViewModel` does — from the real
 * `AiError` → `toUiError()`/`isButtonRetryable()`/`needsProviderSetup()` helpers — and assert only
 * what DS-10 added: the DS-5 what/why/next copy and which single action appears.
 */
class AssistantPresentationTest {

    private fun errorStatus(error: AiError) = AssistantStatus.Error(
        error = error.toUiError(),
        retryable = error.isButtonRetryable(),
        showProviderCta = error.needsProviderSetup(),
    )

    @Test fun `network error keeps retry as the only action`() {
        val presentation = errorStatus(AiError.Network()).toPresentation()

        assertEquals(RETRY_LABEL, presentation.primaryLabel)
        assertEquals("No network connection.", presentation.why)
        assertTrue(presentation.next.isNotBlank())
    }

    @Test fun `missing credentials keeps the provider CTA, never retry`() {
        val presentation = errorStatus(AiError.MissingCredentials).toPresentation()

        assertEquals(PROVIDER_CTA_LABEL, presentation.primaryLabel)
    }

    @Test fun `unauthorized keeps the provider CTA, never retry`() {
        val presentation = errorStatus(AiError.Unauthorized).toPresentation()

        assertEquals(PROVIDER_CTA_LABEL, presentation.primaryLabel)
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
            assertTrue("title for $error", presentation.title.isNotBlank())
            assertTrue("whatFailed for $error", presentation.whatFailed.isNotBlank())
            assertTrue("why for $error", presentation.why.isNotBlank())
            assertTrue("next for $error", presentation.next.isNotBlank())
        }
    }

    @Test fun `unknown ui error still renders safe non-technical copy`() {
        val presentation = AssistantStatus.Error(
            error = UiError.Unknown,
            retryable = true,
            showProviderCta = false,
        ).toPresentation()

        assertEquals("Something went wrong.", presentation.why)
        assertEquals(RETRY_LABEL, presentation.primaryLabel)
    }

    // ── Provenance: host + model + key presence, never the key or the raw URL ────────────────────

    @Test fun `provenance details carry host, model, and key presence only`() {
        val details = providerProvenanceDetails(
            baseUrl = "https://openrouter.ai/api/v1",
            modelId = "mistralai/mistral-7b-instruct",
            keySet = true,
        )

        assertEquals(listOf("openrouter.ai", "mistralai/mistral-7b-instruct", "key in keystore"), details)
    }

    @Test fun `provenance never leaks the scheme or path of a malformed base url`() {
        val details = providerProvenanceDetails(baseUrl = "https://my org.com", modelId = "", keySet = false)

        assertEquals(listOf(UNKNOWN_HOST, "no key set"), details)
        assertFalse(details.any { it.contains("https://") })
    }

    @Test fun `cloud disclosure names the provider hand-off and the keystore, and promises nothing else is sent`() {
        assertTrue(CLOUD_DISCLOSURE_BODY.contains("Send"))
        assertTrue(CLOUD_DISCLOSURE_BODY.contains("Keystore"))
        assertTrue(CLOUD_DISCLOSURE_BODY.contains("no history"))
        assertTrue(CLOUD_DISCLOSURE_BODY.contains("no memory"))
    }

    @Test fun `no-provider copy promises nothing is sent yet`() {
        assertTrue(NO_PROVIDER_BODY.contains("nothing is sent anywhere"))
    }
}
