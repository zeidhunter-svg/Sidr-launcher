package com.sidr.launcher.domain.input

import com.sidr.launcher.domain.intent.UrlClassification
import com.sidr.launcher.domain.intent.UrlDetector
import java.util.Locale

/**
 * Classifies the universal-input buffer into an [InputIntent] (AIL-3). Pure, Android-free, stdlib-only —
 * mirrors [UrlDetector], which it reuses for URL safety (scheme allow-list, curated TLD, homograph
 * guard). Deterministic and side-effect-free, so it is exhaustively unit-testable and the ViewModel can
 * call it on every keystroke without a coroutine.
 */
object UniversalInputRouter {

    private const val DEV_SENTINEL = "//dev-mode"

    fun classify(buffer: String): InputIntent {
        val raw = buffer.trim()
        if (raw.isEmpty()) return InputIntent.Empty
        if (raw.equals(DEV_SENTINEL, ignoreCase = true)) return InputIntent.DevSentinel

        // A safe, openable URL earns a "site" chip; anything else (search fallback / not a URL) does not.
        // UrlDetector expects a lowercased token, but we preserve the original casing in raw.
        val siteUrl = when (val url = UrlDetector.classify(raw.lowercase(Locale.ROOT))) {
            is UrlClassification.Url -> url.url
            is UrlClassification.SearchFallback -> null
            UrlClassification.None -> null
        }
        return InputIntent.Query(raw = raw, siteUrl = siteUrl)
    }
}
