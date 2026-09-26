package com.sidr.launcher.feature.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the Assistant provenance line's host-only fallback (review finding on
 * commit d29ae5b, Task 3). `AssistantViewModel.saveProvider` only validates that `baseUrl` starts
 * with `"https://"` before persisting it — it does not validate the rest is a well-formed URI, so
 * `providerHost` must never leak the scheme (or any other raw substring) when `URI(baseUrl)` throws.
 *
 * I18N-1 Task 10 moved the neutral sentinel out of a Kotlin constant and into the locked, never-translated
 * `assistant_provider_unknown_host` resource, so `providerHost` now returns `null` and the composable
 * substitutes it. These tests therefore assert **both** halves of that split: the pure function yields
 * no host at all, and the value the screen actually renders in its place is still the scheme-free
 * `(unknown host)` — read from the resource file that ships, not from a constant a refactor could
 * quietly diverge from.
 */
class AssistantScreenTest {

    /** Exactly what `AssistantScreen.providerProvenanceDetails` puts in the host slot. */
    private fun renderedHost(baseUrl: String): String =
        providerHost(baseUrl) ?: AssistantStrings.en("assistant_provider_unknown_host")

    @Test
    fun `well-formed https url resolves to its host`() {
        assertEquals("api.openai.com", providerHost("https://api.openai.com/v1"))
    }

    @Test
    fun `url with a space never falls back to the raw scheme-bearing string`() {
        // URI("https://my org.com") throws URISyntaxException — verified independently.
        assertNull("a malformed base URL must resolve to no host at all", providerHost("https://my org.com"))

        val result = renderedHost("https://my org.com")

        assertFalse(result.contains("https://"))
        assertEquals("(unknown host)", result)
    }

    @Test
    fun `bare scheme with no host never falls back to the raw scheme-bearing string`() {
        // URI("https://") throws URISyntaxException — verified independently.
        assertNull("a host-less base URL must resolve to no host at all", providerHost("https://"))

        val result = renderedHost("https://")

        assertFalse(result.contains("https://"))
        assertEquals("(unknown host)", result)
    }
}
