package com.sidr.launcher.domain.sort

/**
 * The web-address sort (A4′ phase 3b, B-5; closes phase 3a's named debt (4) in code). Unlike
 * [DurationSort] and [ClockTimeSort] it **normalises**, because the raw form would fail somewhere the
 * user cannot see: Android's intent-filter matching compares the scheme case-sensitively, so
 * `HTTPS://…` reaches no browser and comes back `Failed`; and an address with a space in it is not an
 * address.
 *
 * `open_url`'s worker (`SystemIntentToolWorker`) reads its argument through [normalized], so the
 * web-only rule (final review A3 of phase 3a: the address is the MODEL's, and `ACTION_VIEW` opens
 * whatever app claims a scheme — `quillpay://…` is a payment or a deep link, not a page) holds in the
 * execution layer below the consent card, whoever wrote the address. The host is not checked: a
 * look-alike («https://аpple.com», a Cyrillic «а») opens after the consent card, which shows it as
 * written; homograph detection is left to the browser (owner decision, batch-1 ADR, subsection
 * «Проверка хоста снята решением владельца (2026-11-27)»).
 */
object UrlSort {

    /**
     * The address with its scheme lower-cased and everything after it kept **byte for byte** — the
     * host's case too, since only the scheme is case-insensitive by RFC 3986; or `null`. Refused:
     * a blank, any whitespace inside the trimmed address, and any scheme but `http://` / `https://`
     * (compared case-insensitively) — the same allow-list as FastPath's `UrlDetector`.
     */
    fun normalized(words: String): String? {
        val address = words.trim()
        if (address.any { it.isWhitespace() }) return null
        val scheme = WEB_SCHEMES.firstOrNull { address.startsWith(it, ignoreCase = true) } ?: return null
        return scheme + address.substring(scheme.length)
    }

    /** Normalises (B-5): the normalised address resolves, anything else is unreadable. */
    val resolver: SortResolver = SortResolver { words ->
        normalized(words)?.let { SortResolution.Resolved(it) } ?: SortResolution.Unreadable
    }

    /** Lower-case on purpose: [normalized] writes the matched scheme from here, not from the input. */
    private val WEB_SCHEMES = listOf("http://", "https://")
}
