package com.sidr.launcher.domain.intent

/**
 * The outcome of classifying a single already-normalized token for web routing (AIL-2, forks R6 +
 * AIL-Q3). Pure, Android-free, so the rule matcher can decide *whether* an input is a URL without
 * ever opening anything — recognition is not execution.
 */
sealed interface UrlClassification {

    /**
     * A high-confidence, safe URL — open it directly. [url] always carries an explicit scheme
     * (`https://` for scheme-less input; a user-typed `http://` is preserved). Only `http`/`https`
     * ever reach here (scheme allow-list).
     */
    data class Url(val url: String) : UrlClassification

    /**
     * Domain-shaped input that is NOT safe to open blindly — an unknown TLD, an IDN/punycode or
     * non-ASCII host (homograph guard), or a URL carrying a case-sensitive query string that
     * lowercasing would corrupt (Q4). Never opened; routed to a web search for [term] instead
     * ("ambiguous → browser search", never open a guessed/malformed URL silently).
     */
    data class SearchFallback(val term: String) : UrlClassification

    /** No web signal at all — let the normal rule pipeline handle it. */
    data object None : UrlClassification
}

/**
 * Detects and safely classifies URL-like input (AIL-2 / AIL-Q3). Operates on a single normalized
 * (lowercased, whitespace-collapsed) token — anything containing a space is [UrlClassification.None].
 *
 * Safety rules (AIL-Q3, finalized 2026-07-05):
 * - **Scheme allow-list:** only `http`/`https` are ever opened. Any other scheme (`intent://`,
 *   `javascript:`, `market://`, `tel:`, `file://`, …) → [UrlClassification.None] — never opened
 *   silently. `market://` is produced only by the Play-Store executor, never from user-typed input.
 * - **TLD:** scheme-less hosts must end in a curated known TLD to auto-open; a domain-shaped token
 *   with an unknown (but TLD-looking) suffix → [UrlClassification.SearchFallback].
 * - **Homograph/punycode guard:** an `xn--` label or any non-ASCII host → [UrlClassification.SearchFallback].
 * - **Case guard (Q4):** a URL with a query string is routed to search rather than opened, because
 *   the token has already been lowercased and a case-sensitive query would be corrupted.
 */
object UrlDetector {

    fun classify(rawToken: String): UrlClassification {
        val token = rawToken.trim()
        if (token.isEmpty() || token.any { it.isWhitespace() }) return UrlClassification.None

        // Explicit scheme: only http/https are candidates; everything else is never opened.
        val schemeSep = token.indexOf("://")
        if (schemeSep >= 0) {
            val scheme = token.substring(0, schemeSep)
            if (scheme != "http" && scheme != "https") return UrlClassification.None
            return classifyAuthority(
                fullUrl = token,
                authority = token.substring(schemeSep + 3),
                explicitScheme = true,
            )
        }
        // Any other colon usage is a non-authority scheme (tel:, mailto:, javascript:, market:, or a
        // host:port we deliberately don't support) — never opened.
        if (token.contains(':')) return UrlClassification.None

        // Scheme-less: treat as a bare host, defaulting to https.
        return classifyAuthority(
            fullUrl = "https://$token",
            authority = token,
            explicitScheme = false,
        )
    }

    private fun classifyAuthority(
        fullUrl: String,
        authority: String,
        explicitScheme: Boolean,
    ): UrlClassification {
        val queryStart = authority.indexOf('?')
        val hasQuery = queryStart >= 0
        val beforeQuery = if (hasQuery) authority.substring(0, queryStart) else authority
        val pathStart = beforeQuery.indexOf('/')
        val host = if (pathStart >= 0) beforeQuery.substring(0, pathStart) else beforeQuery

        // Must look like a dotted host; a bare single label is not a domain.
        if (!host.contains('.')) return UrlClassification.None
        val labels = host.split('.')
        if (labels.any { it.isEmpty() }) return UrlClassification.None

        val tld = labels.last()
        // A final label that isn't 2+ letters (e.g. "3.14", "v1.2") is not a domain at all.
        if (!tld.matches(TLD_SHAPE)) return UrlClassification.None

        val hostAscii = host.all { it.code in 0..127 }
        val hasPunycode = labels.any { it.startsWith("xn--") }
        val labelsValid = labels.all { it.matches(LABEL_SHAPE) && !it.startsWith("-") && !it.endsWith("-") }
        val safeHost = hostAscii && !hasPunycode && labelsValid
        val knownTld = tld in CURATED_TLDS

        return when {
            // Domain-shaped but unsafe (IDN/punycode/bad label) → search, never open.
            !safeHost -> UrlClassification.SearchFallback(fullUrlToTerm(fullUrl, explicitScheme, authority))
            // Unknown TLD without an explicit scheme → ambiguous → search.
            !knownTld && !explicitScheme -> UrlClassification.SearchFallback(authority)
            // Case-sensitive query would be corrupted by lowercasing → search instead of a wrong page.
            hasQuery -> UrlClassification.SearchFallback(fullUrlToTerm(fullUrl, explicitScheme, authority))
            else -> UrlClassification.Url(fullUrl)
        }
    }

    // For the search term, prefer what the user actually typed (the authority for scheme-less input,
    // the whole URL for explicit-scheme input) rather than the synthesized https:// form.
    private fun fullUrlToTerm(fullUrl: String, explicitScheme: Boolean, authority: String): String =
        if (explicitScheme) fullUrl else authority

    private val TLD_SHAPE = Regex("[a-z]{2,}")
    private val LABEL_SHAPE = Regex("[a-z0-9-]+")

    /**
     * Curated common TLDs (Q1). Deliberately conservative: an uncommon-but-valid TLD falls through to
     * a web search rather than a silent open. Extend deliberately.
     */
    private val CURATED_TLDS: Set<String> = setOf(
        // Common gTLDs
        "com", "org", "net", "edu", "gov", "mil", "int", "io", "co", "dev", "app", "ai", "me",
        "tv", "cc", "xyz", "info", "biz", "online", "site", "tech", "store", "shop", "blog",
        "news", "page", "gg", "so", "sh", "ly", "to",
        // Popular ccTLDs (incl. the NLU target locales: en/ar/tr/ru)
        "us", "uk", "ca", "au", "de", "fr", "es", "it", "nl", "ru", "tr", "ua", "kz", "jp",
        "cn", "kr", "in", "br", "mx", "pl", "se", "no", "fi", "dk", "ch", "at", "be", "cz",
        "pt", "gr", "ro", "ir", "sa", "ae", "eg", "id", "th", "vn", "ph", "za",
    )
}
