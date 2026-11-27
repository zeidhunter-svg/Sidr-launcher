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
 * execution layer below the consent card, whoever wrote the address.
 *
 * **The host check** (owner ruling on batch 1 question 2, 2026-11-27: «отказ»; ADR «2026-11-22 — Пачка 1 хвоста A4′…»,
 * subsection «Решение владельца по вопросу 2 и третья перепроверка»). An address is opened only when its host is plain
 * ASCII with no punycode label, so that the consent card names the host the browser will reach in the letters it will
 * reach: «https://аpple.com» with a Cyrillic «а» looks genuine on the card and is another site. This is the one place
 * every `open_url` value passes — the bridge's binder reads the model's value through [resolver] before any card or
 * session (`DeviceMentionBinder`, step 7, and the learned-plan replay of step 2c, which binds through it), and the
 * worker reads it again through [normalized] below the consent card, whoever wrote it — so it closes every path the
 * FastPath predicate (`HandleUserCommandUseCase.carriesUnsafeAddress`) does not see: an address glued to a word, undecided
 * text, a homograph in the TLD. **The cost, accepted by the owner:** a legitimate IDN site («пример.рф», the whole `.рф`
 * zone, any `xn--` host) cannot be opened through the model or the `open_url` tool; FastPath's typed path already sends
 * such an address to a search (`UrlDetector` → `SearchFallback`).
 */
object UrlSort {

    /**
     * The address with its scheme lower-cased and everything after it kept **byte for byte** — the
     * host's case too, since only the scheme is case-insensitive by RFC 3986; or `null`. Refused:
     * a blank, any whitespace inside the trimmed address, and any scheme but `http://` / `https://`
     * (compared case-insensitively) — the same allow-list as FastPath's `UrlDetector` — and any address
     * whose host [hostIsPlain] refuses.
     */
    fun normalized(words: String): String? {
        val address = words.trim()
        if (address.any { it.isWhitespace() }) return null
        val scheme = WEB_SCHEMES.firstOrNull { address.startsWith(it, ignoreCase = true) } ?: return null
        val rest = address.substring(scheme.length)
        if (!hostIsPlain(rest)) return null
        return scheme + rest
    }

    /** Normalises (B-5): the normalised address resolves, anything else is unreadable. */
    val resolver: SortResolver = SortResolver { words ->
        normalized(words)?.let { SortResolution.Resolved(it) } ?: SortResolution.Unreadable
    }

    /**
     * Whether the host of [afterScheme] (the address past `scheme://`) may be opened. The **authority** runs to the
     * first `/`, `?` or `#` — where Android's `Uri` ends it; a browser's URL parser ends it there too, or earlier (at
     * `\`), so a host it reads is never longer than the one judged here. Then, in order:
     *  - **userinfo is refused outright** — any `@` in the authority. «https://apple.com@quillmere.example» opens
     *    `quillmere.example`, and a consent card that begins «apple.com@…» is a look-alike of its own; dropping the
     *    userinfo and judging the host after the LAST `@` would pass exactly that card. No address a model needs to open
     *    carries credentials.
     *  - **a port is dropped** only as Android's `Uri` drops it: a `:` followed by ASCII digits to the end of the
     *    authority. Anything else after a `:` stays part of the host, so a non-ASCII character cannot hide in a "port".
     *  - the host is refused when it is **empty**, holds **any non-ASCII character** (a Cyrillic «а» or «о», a whole IDN
     *    host, a full-width letter or dot), holds a **`%`** (a browser percent-decodes a host before it reads it as a
     *    name: `%D0%B0` is the Cyrillic «а» again), or has a dot-separated label that **starts with `xn--`**, compared
     *    case-insensitively (punycode: the same look-alike spelled in ASCII).
     * An ASCII host passing all of these is the name the browser reaches, lower-cased, letter for letter.
     */
    private fun hostIsPlain(afterScheme: String): Boolean {
        val authority = afterScheme.takeWhile { it != '/' && it != '?' && it != '#' }
        if ('@' in authority) return false
        val host = authority.withoutPort()
        if (host.isEmpty()) return false
        if (host.any { it.code > ASCII_MAX || it == '%' }) return false
        return host.split('.').none { it.startsWith(PUNYCODE_PREFIX, ignoreCase = true) }
    }

    /** The authority without a trailing `:digits` — Android `Uri`'s own port rule; otherwise unchanged. */
    private fun String.withoutPort(): String {
        val colon = lastIndexOf(':')
        if (colon < 0) return this
        val port = substring(colon + 1)
        return if (port.all { it in '0'..'9' }) substring(0, colon) else this
    }

    /** Lower-case on purpose: [normalized] writes the matched scheme from here, not from the input. */
    private val WEB_SCHEMES = listOf("http://", "https://")

    private const val ASCII_MAX = 0x7F
    private const val PUNYCODE_PREFIX = "xn--"
}
