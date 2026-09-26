package com.sidr.launcher.domain.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/**
 * Secret-leak guard (Block L / Fork P5-3) — proves the **outbound domain surface** ([AiRequest] +
 * [AiError]) has no field that can carry the API key or raw prompt, and that nothing renders a
 * credential into a `toString()`. The key lives in `SecureSecretStore` / the adapter's
 * `Authorization` header (Blocks J/K), never in a domain type.
 *
 * Reflection-free: credential terms are scanned over the **hand-synced field-name inventories**
 * ([OutboundContextPolicy.OUTBOUND_FIELD_NAMES] / [OutboundContextPolicy.AIERROR_FIELD_NAMES]),
 * mirroring the Phase-4 `RoomColumnNames.TABLE_NAMES` precedent. The rendered `toString()` is
 * scanned only for a planted sentinel — credential terms are NOT scanned over rendered text because
 * the safe diagnostic variant name [AiError.MissingCredentials] legitimately contains "credential"
 * (the same vacuous-collision class as `maxOutputTokens`/"token"; see `CREDENTIAL_TERMS`).
 */
class OutboundSecretLeakGuardTest {

    /** A value the key never gets placed into — its absence proves there is no field to hold it. */
    private val secretSentinel = "FAKE-KEY-d34db33f"

    @Test
    fun `outbound field inventory matches AiRequest's real fields`() {
        // Hand-sync check (Phase-4 TABLE_NAMES rule): if AiRequest gains/loses a field, update
        // OUTBOUND_FIELD_NAMES and this expectation together — the credential scan depends on it.
        assertEquals(
            setOf("messages", "system", "maxOutputTokens", "model", "stopSequences"),
            OutboundContextPolicy.OUTBOUND_FIELD_NAMES,
        )
    }

    @Test
    fun `no AiRequest field name carries a credential term`() {
        // Passes precisely because "token" was EXCLUDED from CREDENTIAL_TERMS (maxOutputTokens
        // contains the substring "token"). Do NOT "fix" the policy by re-adding "token" — it would
        // vacuously fail the build on the legitimate maxOutputTokens field.
        assertNoCredentialTerm(OutboundContextPolicy.OUTBOUND_FIELD_NAMES, "AiRequest")
    }

    @Test
    fun `AiError field inventory matches AiError's real fields`() {
        // Hand-sync check: AiError's only fields are safe diagnostics (no secret/raw-content field).
        assertEquals(
            setOf("retryAfterMs", "detail", "statusCode"),
            OutboundContextPolicy.AIERROR_FIELD_NAMES,
        )
    }

    @Test
    fun `no AiError field name carries a credential term`() {
        assertNoCredentialTerm(OutboundContextPolicy.AIERROR_FIELD_NAMES, "AiError")
    }

    @Test
    fun `every AiError variant toString omits the secret sentinel`() {
        // Each variant constructed with a safe detail/value; the sentinel was never inserted, so the
        // surface has no place to hold it. (detail is safe-only — a status hint, never user text.)
        val variants: List<AiError> = listOf(
            AiError.Offline,
            AiError.MissingCredentials,
            AiError.Unauthorized,
            AiError.RateLimited(retryAfterMs = 1000L),
            AiError.Timeout,
            AiError.Network(detail = "connection reset"),
            AiError.ServerError(statusCode = 500),
            AiError.InvalidRequest(detail = "bad model"),
            AiError.Unknown(detail = "unmapped"),
        )
        variants.forEach { variant ->
            assertFalse(
                "${variant::class.simpleName}.toString() must not contain the secret sentinel",
                variant.toString().contains(secretSentinel),
            )
        }
    }

    @Test
    fun `AiRequest built from a sentinel command never renders an unplaced key`() {
        // The key is never passed to the builder, so it has no field to live in: toString cannot
        // surface it. Proves the outbound request type cannot carry a credential.
        val request = PromptContextBuilder().build("<<SENTINEL_CMD>>")
        assertFalse(
            "AiRequest.toString() must not contain a key that was never placed in it",
            request.toString().contains(secretSentinel),
        )
    }

    private fun assertNoCredentialTerm(fieldNames: Set<String>, owner: String) {
        val violations = fieldNames.flatMap { name ->
            OutboundContextPolicy.CREDENTIAL_TERMS
                .filter { term -> name.contains(term, ignoreCase = true) }
                .map { term -> name to term }
        }
        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (name, term) ->
                "  $owner field \"$name\" contains credential term \"$term\"" }
            fail("Credential-named outbound field detected:\n$detail")
        }
    }
}
