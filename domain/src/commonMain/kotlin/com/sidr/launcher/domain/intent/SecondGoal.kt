package com.sidr.launcher.domain.intent

/**
 * Does a FastPath remainder carry a SECOND goal — one the arm would silently fold into its own
 * argument? A4′ phase 1, spec §5.2, owner fork F1-1 (2026-09-26): «союз + закрытый список».
 *
 * **What it detects is discarded content, not query quality.** "найди билеты в казань и закажи
 * такси" handed to a web search searches for the taxi order too; "найди рецепт хлеба и соли" does
 * not discard anything — its conjunction joins two nouns of one query. The rule tells those apart by
 * one thing: a verb from [VERBS_AFTER_CONJUNCTION_BY_LOCALE] / [VERBS_BEFORE_CONJUNCTION_BY_LOCALE]
 * standing next to a conjunction from [CONJUNCTIONS_BY_LOCALE]. It is the same idea as
 * `ToolSelector`'s rule 4 — the command must not be bounded from above by nothing — said for a layer
 * that has no target to bound it with.
 *
 * **The position is grammar, not locale** — the axis `RuleBasedIntentMatcher.VerbForms` already
 * uses. `en`/`ru` are SVO: a second clause's verb FOLLOWS the conjunction ("… and send it", "… и
 * закажи такси"). `tr` is SOV: FastPath only claims a Turkish text that ENDS in its own verb, so
 * whatever clause precedes the conjunction ends in ITS verb ("haberleri oku ve …"). Matching is
 * locale-blind, like every FastPath and tool vocabulary in this repo: all forms are live at once.
 *
 * **A conjunction with a listed verb on BOTH sides joins two verbs of one clause**, not two goals:
 * "how to download and install python", «скачай и установи» — the query is about one thing. That
 * case is skipped, so the rule fires only where a verb stands on the side of the conjunction its
 * grammar puts a new clause.
 *
 * **Imperatives only.** The lists hold the imperative forms of FastPath's own command verbs — a
 * clause led by one of them is a second command by definition — and a closed list of imperatives of
 * acts a phone is asked to do. FastPath's infinitives («открыть», «запустить», «установить»,
 * «найти») and "google" are left out on purpose, and `FastPathLocaleGuardTest` names each exemption:
 * as a noun or an infinitive inside a query ("apple and google", «как скачать и установить») they
 * would withdraw correct searches. **The list was written after the owner's `B15` corpus had been
 * read**, so the corpus rows it catches are its calibration, not evidence of its generality; the
 * `intent_match` history cannot validate it, because SEARCH text is redacted there (`IntentMatchMapper`).
 *
 * **Named limits, not oversights.** One conjunction per locale (no "then" / "потом" / "sonra").
 * Infinitives and verbs outside the list are not caught ("… и забронировать"). Punctuation stuck to
 * a token defeats the match, because `CommandNormalizer` strips none ("… и проверь." — the verb
 * token is «проверь.»); a comma before the conjunction does not ("… почту, и проверь"). A second
 * app named without a verb ("open veltrix and gmail") is not a second goal here. And a word spelled
 * like a listed verb reads as a verb: `en` "order", "pay", "book", "play", "call", "check", "show"
 * are also nouns ("law and order", "tap and pay"); `tr` "et" is also "meat", "yaz" "summer", "al"
 * "red", "aç" "hungry", "ara" "interval". Each miss costs, on one more command, what the phase set
 * out to remove — a confident, slightly wrong answer; each false hit costs an honest "not understood"
 * or a round-trip to the model, never a wrong act.
 */
object SecondGoal {

    val CONJUNCTIONS_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "en" to setOf("and"),
        "ru" to setOf("и"),
        "tr" to setOf("ve"),
    )

    /** SVO: the verb that opens the second clause, right after the conjunction. */
    val VERBS_AFTER_CONJUNCTION_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "en" to setOf(
            // FastPath's own verbs (RuleBasedIntentMatcher LAUNCH/INSTALL/SEARCH, en prefixes; not "google")
            "open", "launch", "start", "install", "search", "find",
            // acts
            "do", "make", "download", "check", "set", "write", "send", "call", "buy", "order", "book",
            "pay", "transfer", "save", "delete", "turn", "show", "read", "add", "create", "remind", "play",
        ),
        "ru" to setOf(
            // FastPath's own verbs (ru prefixes, imperatives only)
            "открой", "запусти", "установи", "найди",
            // acts
            "сделай", "скачай", "проверь", "поставь", "напиши", "отправь", "позвони", "купи", "закажи",
            "забронируй", "оплати", "переведи", "запиши", "сохрани", "удали", "включи", "выключи",
            "покажи", "прочитай", "добавь", "создай", "напомни",
        ),
    )

    /** SOV: the verb that closes the first clause, right before the conjunction. */
    val VERBS_BEFORE_CONJUNCTION_BY_LOCALE: Map<String, Set<String>> = mapOf(
        "tr" to setOf(
            // FastPath's own verbs (tr suffixes)
            "aç", "kur", "ara",
            // acts
            "yap", "et", "indir", "yaz", "gönder", "al", "öde", "kaydet", "sil", "göster", "oku", "ekle",
            "oluştur", "hatırlat", "bul", "başlat", "kapat",
        ),
    )

    private val conjunctions: Set<String> = CONJUNCTIONS_BY_LOCALE.values.flatten().toSet()
    private val verbsAfter: Set<String> = VERBS_AFTER_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
    private val verbsBefore: Set<String> = VERBS_BEFORE_CONJUNCTION_BY_LOCALE.values.flatten().toSet()
    private val anyVerb: Set<String> = verbsAfter + verbsBefore

    fun carriedBy(remainder: String): Boolean {
        val tokens = CommandNormalizer.normalize(remainder).split(' ')
        return tokens.indices.any { i ->
            val before = tokens.getOrNull(i - 1)
            val after = tokens.getOrNull(i + 1)
            tokens[i] in conjunctions &&
                !(before in anyVerb && after in anyVerb) &&
                (after in verbsAfter || before in verbsBefore)
        }
    }
}
