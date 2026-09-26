package com.sidr.launcher.domain.security

import com.sidr.launcher.domain.ai.AiProviderId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class SecretKeysTest {

    @Test
    fun `apiKey is stable for a given provider`() {
        val provider = AiProviderId("cloud-default")
        assertEquals(SecretKeys.apiKey(provider), SecretKeys.apiKey(provider))
    }

    @Test
    fun `apiKey is distinct per provider`() {
        assertNotEquals(
            SecretKeys.apiKey(AiProviderId("cloud-default")),
            SecretKeys.apiKey(AiProviderId("cloud-compatible")),
        )
    }

    /**
     * Content-privacy guard for the secret-store keyspace.
     *
     * The denylist is the **content subset** of the Phase-4 privacy denylist — it deliberately
     * EXCLUDES `secret`/`token`/`api`/`key`: a secret-store slot name legitimately references a
     * credential ("api key"), and the secret *value* lives encrypted in the Keystore-backed impl,
     * never in a plaintext store. What must never appear is a *user-content* category (voice,
     * location, calendar, behavioural history, free-text queries) leaking via a provider id or a
     * future key convention. This still catches e.g. a provider id of "voicebot".
     */
    @Test
    fun `apiKey slot names carry no user-content term`() {
        val contentTerms = listOf(
            "voice", "query", "search", "location", "calendar",
            "history", "conversation", "message", "transcript",
        )
        val sampleProviders = listOf("cloud-default", "cloud-compatible", "provider-c", "provider-d")

        val violations = sampleProviders.flatMap { id ->
            val keyName = SecretKeys.apiKey(AiProviderId(id)).value
            contentTerms
                .filter { term -> keyName.contains(term, ignoreCase = true) }
                .map { term -> keyName to term }
        }

        if (violations.isNotEmpty()) {
            val detail = violations.joinToString("\n") { (key, term) ->
                "  secret key \"$key\" contains forbidden content term \"$term\"" }
            fail("Forbidden user-content term in a secret-store key:\n$detail")
        }
    }
}
