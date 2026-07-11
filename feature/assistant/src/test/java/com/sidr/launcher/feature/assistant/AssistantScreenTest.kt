package com.sidr.launcher.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Regression coverage for the Assistant provenance line's host-only fallback (review finding on
 * commit d29ae5b, Task 3). `AssistantViewModel.saveProvider` only validates that `baseUrl` starts
 * with `"https://"` before persisting it — it does not validate the rest is a well-formed URI, so
 * `providerHost` must never leak the scheme (or any other raw substring) when `URI(baseUrl)` throws.
 */
class AssistantScreenTest {

    @Test
    fun `well-formed https url resolves to its host`() {
        assertEquals("api.openai.com", providerHost("https://api.openai.com/v1"))
    }

    @Test
    fun `url with a space never falls back to the raw scheme-bearing string`() {
        // URI("https://my org.com") throws URISyntaxException — verified independently.
        val result = providerHost("https://my org.com")

        assertFalse(result.contains("https://"))
        assertEquals("(unknown host)", result)
    }

    @Test
    fun `bare scheme with no host never falls back to the raw scheme-bearing string`() {
        // URI("https://") throws URISyntaxException — verified independently.
        val result = providerHost("https://")

        assertFalse(result.contains("https://"))
        assertEquals("(unknown host)", result)
    }
}
