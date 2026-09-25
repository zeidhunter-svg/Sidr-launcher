package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.data.repository.agent.memory.MemoryToolIds
import com.sidr.launcher.domain.intent.CommandNormalizer
import com.sidr.launcher.domain.tool.ToolId
import javax.inject.Inject

/** One recognised tool and the arguments the text supplied for it. */
data class ToolMatch(val id: ToolId, val args: Map<String, String>)

/**
 * Deterministic, localized recognition of a tool from raw text — FastPath for tools.
 *
 * It lives in `:data:repository` beside `RuleBasedIntentMatcher` for the reason the hard rule gives:
 * `:domain` is `commonMain` and must stay free of locale tables and of text parsing, so the JVM
 * consumer is unaffected by anything here.
 *
 * **It recognises, it does not understand.** A miss is not an error and never becomes "unknown
 * command" — [ToolMatchPlanner] answers `NoPlan` and routing falls through to the model exactly as it
 * did before. That is the doctrine's rule 1: FastPath is a latency optimization, not a filter. Because
 * a miss is free and a false positive performs an effect, every ambiguity below is resolved by
 * declining.
 *
 * **One normalizer, not two.** Matching runs on [CommandNormalizer.normalize], the same function
 * `HandleUserCommandUseCase` feeds `RuleBasedIntentMatcher`. Two of its properties are load-bearing
 * here, and `ToolVocabularyTest` holds both:
 *  - it **collapses internal whitespace**, so "set  a  timer for 5" matches instead of missing for a
 *    reason no user could see — a local `trim().lowercase()` does not;
 *  - it lowercases with an **explicit `Locale.ROOT`**, which keeps recognition stable on a Turkish
 *    device, where the default locale maps "I" to the dotless "ı" and every trigger containing an `i`
 *    would stop matching uppercase input.
 *
 * Stated no wider than it was measured: Kotlin's bare `String.lowercase()` is *already* `Locale.ROOT`,
 * so the second property bites only against an explicit `lowercase(Locale.getDefault())` — which is
 * precisely the edit someone reaches for when "fixing" localization, and is why `CommandNormalizer`
 * spells the locale out rather than relying on the default.
 *
 * **The two maps encode grammatical position, not locale.** They mirror
 * `RuleBasedIntentMatcher.VerbForms`, and the axis is where the *verb* sits: `en`/`ru` are SVO, so a
 * verb-carrying form is a prefix ("set a timer for ..."), and `tr` is SOV, so its verb-carrying forms
 * are suffixes ("... zamanlayici ayarla"). A **verbless** form has no verb to place and is declared as
 * a prefix whatever its locale — which, for a zero-argument entry, means "the text must equal it".
 * That is why `set_timer` declares `tr` suffixes while `open_system_settings` declares `tr` prefixes:
 * its shipped triggers are noun phrases in all three locales, exactly as
 * `RuleBasedIntentMatcher.SETTINGS_KEYWORDS_BY_LOCALE` already is. [ToolVocabularyLocaleGuardTest]
 * therefore checks locale *coverage* across both maps and never position, so a tool added with English
 * triggers only is red rather than quietly monolingual.
 *
 * **A trigger FastPath already claims is a capability that exists only in this table.** FastPath runs
 * first and `RouteCommandUseCase` only builds a `GoalShape.Free` goal for a command it left undecided,
 * so a trigger colliding with a FastPath verb never reaches the planner. Six of the triggers this task's
 * brief proposed were dead on exactly that rule, Turkish entirely so:
 *  - `"open system settings"` / `"открой настройки системы"` — the `LAUNCH_VERBS` prefixes `"open"` /
 *    `"открой"` take them first, so they arrive as an app launch. The verbless `"system settings"` and
 *    `"системные настройки"` carry the same meaning and survive.
 *  - `"sistem ayarlarını aç"` / `"android ayarlarını aç"` — the `LAUNCH_VERBS` `tr` suffix `"aç"`.
 *  - `"zamanlayıcı kur"` / `"sayaç kur"` — the `INSTALL_VERBS` `tr` suffix `"kur"`, which turns a timer
 *    request into a Play Store search. `"ayarla"` is not a FastPath verb and carries the same sense.
 *
 * They are absent rather than present-and-inert, and [ToolVocabularyReachabilityTest] is what keeps the
 * next entry from repeating the mistake. Adding one of them back needs FastPath's own vocabulary
 * changed first.
 */
class ToolVocabulary internal constructor(val entries: List<Entry>) {

    /**
     * Hilt's entry point. The `internal` primary constructor exists so tests can build a vocabulary
     * whose entries are chosen to exercise a branch the shipping two cannot reach — [match]'s
     * ambiguity refusal — rather than leaving it untested the way A0's `D11` findings were.
     */
    @Inject constructor() : this(DEFAULT_ENTRIES)

    /**
     * One tool's triggers. [argName] is `null` for a zero-argument tool; when it is set, whatever
     * follows (or precedes, in a suffix form) the trigger becomes that argument's value — **normalized,
     * not verbatim**: [match] runs [CommandNormalizer.normalize] over the whole text before matching,
     * so what reaches the worker is lower-cased and whitespace-collapsed. `"timer for 10 Minutes"`
     * yields `"10 minutes"`. Reading the value is the worker's business, not the vocabulary's.
     *
     * **The cost of that, named here rather than discovered in a worker.** A duration does not care.
     * The first `A1"` tool whose argument is free text — a note body, a search phrase, a contact name —
     * will receive a case-folded, whitespace-collapsed payload, and **the original is unrecoverable
     * from this type**: [ToolMatch] carries the value, not a span into the raw command. Such a tool
     * needs the raw text handed back (offsets into the unnormalized string, or a per-entry opt-out),
     * decided when it arrives rather than guessed now.
     *
     * For a **zero-argument** entry the two maps are interchangeable — the text must equal the trigger
     * either way — so such an entry declares all its forms, in every locale, as prefixes.
     *
     * [infixByLocale] and [secondArgName] together declare a **bounded two-slot** entry —
     * `<prefix> A <infix> B` — the one capability §7.6 of the second-tool-mass design names beyond a
     * single trailing argument ("называй телеграм как телега"). Both default to empty/`null`, and an
     * entry that leaves them at their defaults is a one-slot entry whose match path is entirely
     * unaffected — see [twoSlotMatch].
     */
    data class Entry(
        val id: ToolId,
        val prefixByLocale: Map<String, Set<String>>,
        val suffixByLocale: Map<String, Set<String>> = emptyMap(),
        val argName: String? = null,
        val infixByLocale: Map<String, Set<String>> = emptyMap(),
        val secondArgName: String? = null,
    )

    /**
     * The **unambiguous** match, or `null`. Two entries matching the same text is a vocabulary defect,
     * not a ranking problem, so it yields `null` rather than a guess — the same fail-closed choice the
     * federation makes for a colliding id.
     *
     * With the two shipping entries the refusal is **unreachable**, and provably so: an ambiguity needs
     * a text that both claims, `open_system_settings` claims only a text it equals exactly, and no such
     * text also carries a `set_timer` trigger. The branch stays because the vocabulary is precisely the
     * thing designed to grow — `A1"`'s twelve tools make collisions a question of when, and the answer
     * must not be "whichever entry was listed first". `ToolVocabularyTest` exercises it on a
     * purpose-built pair.
     */
    fun match(text: String): ToolMatch? {
        val normalized = CommandNormalizer.normalize(text)
        if (normalized.isEmpty()) return null

        val hits = entries.mapNotNull { entry -> entry.matchIn(normalized) }
        return hits.singleOrNull()
    }

    /**
     * True when more than one entry claims [text] — the same ambiguity [match] already declines by
     * returning `null`. Exposed so a caller that layers another source underneath this vocabulary (in
     * particular `ToolSelector`) can tell that refusal apart from "nothing of ours claimed this text"
     * and decline outright rather than falling through to a lower-priority candidate: without this, an
     * authored ambiguity would be tie-broken by whatever a third-party label happened to claim, which
     * inverts the priority `ToolSelector`'s own KDoc states. It answers only that one question — it does
     * not say which entries collided, and it changes nothing about what [match] itself returns.
     */
    fun isAmbiguous(text: String): Boolean {
        val normalized = CommandNormalizer.normalize(text)
        if (normalized.isEmpty()) return false
        return entries.count { entry -> entry.matchIn(normalized) != null } > 1
    }

    /**
     * A trigger must end on a word boundary — the text either **is** the trigger or continues after a
     * space. A bare `startsWith` reads "timer format" as a timer of duration "mat": recognised, planned,
     * and only failing three layers down in the worker's duration parse. `RuleBasedIntentMatcher`
     * matches `"$verb "` for the same reason.
     *
     * The longest matching form of an entry wins, so a trigger may be extended with a more specific one
     * without the shorter form swallowing it.
     *
     * **A prefix hit is final, including its refusal.** If a prefix matches and [toMatch] then declines
     * — a required argument with nothing after it, or trailing words on a zero-argument tool — this
     * returns `null` rather than trying [Entry.suffixByLocale] against the same text. That is a
     * decision, not an oversight, and it is unreachable with the two shipping entries either way.
     * The fall-through would re-read a text this entry has already claimed under a second grammar, and
     * what it can produce is assembled from the trigger's own words: an entry with the prefix
     * `"set timer"` and the suffix `"timer"` would read `"set timer"` as a duration of `"set"`.
     * Declining for a reason that can be stated beats matching for one that cannot, and a miss is free
     * — routing falls through to the model exactly as before.
     *
     * **What a future entry declaring both shapes must know:** the prefix grammar is tried first and
     * wins outright, so do not declare a prefix form ending in one of your own suffix forms and expect
     * the suffix to rescue it. `ToolVocabularyTest`'s
     * `a declined prefix does not fall through to the suffix` pins this; chaining the two is defensible
     * but must be a change that deletes that test deliberately, not an accident that discovers it.
     */
    private fun Entry.matchIn(text: String): ToolMatch? {
        val prefix = prefixByLocale.values.flatten()
            .filter { text == it || text.startsWith("$it ") }
            .maxByOrNull { it.length }
        if (prefix != null) return toMatch(text.removePrefix(prefix).trim())

        val suffix = suffixByLocale.values.flatten()
            .filter { text == it || text.endsWith(" $it") }
            .maxByOrNull { it.length }
        if (suffix != null) return toMatch(text.removeSuffix(suffix).trim())

        return null
    }

    /**
     * A required argument the text did not supply is **no match at all**, not a match with a blank —
     * an agent that silently starts a zero-length timer is worse than one that declines. Same reasoning
     * as `InvocationValidator.resolve`'s `UNRESOLVED_ARG_SOURCE`.
     *
     * A **zero-argument** tool takes no text, so leftover words mean the user meant something else.
     * Accepting them would silently discard the rest of the sentence and open system settings for
     * "system settings for my car" — the broadest possible reading of a command, chosen in silence.
     */
    private fun Entry.toMatch(remainder: String): ToolMatch? = when {
        secondArgName != null -> twoSlotMatch(remainder)
        argName == null -> if (remainder.isBlank()) ToolMatch(id, emptyMap()) else null
        remainder.isBlank() -> null
        else -> ToolMatch(id, mapOf(argName to remainder))
    }

    /**
     * `<prefix> A <infix> B`. The infix is matched **surrounded by spaces** so "какао" cannot serve as
     * "как", and the **first** occurrence wins so a phrase containing the infix cannot silently move
     * the boundary. Either side blank is a miss: a half-filled invocation is worse than none, which is
     * the same rule [toMatch] already applies to one-slot entries.
     */
    private fun Entry.twoSlotMatch(remainder: String): ToolMatch? {
        val first = argName ?: return null
        val second = secondArgName ?: return null
        val infix = infixByLocale.values.flatten()
            .mapNotNull { form -> remainder.indexOf(" $form ").takeIf { it >= 0 }?.let { it to form } }
            .minByOrNull { it.first } ?: return null
        val left = remainder.take(infix.first).trim()
        val right = remainder.drop(infix.first + infix.second.length + 2).trim()
        if (left.isBlank() || right.isBlank()) return null
        return ToolMatch(id, mapOf(first to left, second to right))
    }

    private companion object {
        val DEFAULT_ENTRIES: List<Entry> = listOf(
            Entry(
                id = Tier0ToolIds.SET_TIMER,
                prefixByLocale = mapOf(
                    "en" to setOf("set a timer for", "set timer for", "timer for"),
                    "ru" to setOf("поставь таймер на", "заведи таймер на", "таймер на"),
                ),
                suffixByLocale = mapOf("tr" to setOf("zamanlayıcı ayarla", "sayaç ayarla")),
                argName = "duration",
            ),
            Entry(
                id = Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("system settings", "android settings"),
                    "ru" to setOf("системные настройки", "настройки андроид"),
                    "tr" to setOf("sistem ayarları", "android ayarları"),
                ),
            ),
            // Task 10 (A1″ Phase 3a) — the five acting tools' triggers. See §7.6 of the second-tool-mass
            // design and this task's report for which candidates were rejected as FastPath collisions.
            Entry(
                id = Tier0ToolIds.SET_ALARM,
                prefixByLocale = mapOf(
                    "en" to setOf("set an alarm for", "set alarm for", "alarm for"),
                    "ru" to setOf("поставь будильник на", "заведи будильник на", "будильник на"),
                ),
                // NOT "alarm kur": `INSTALL_VERBS.suffixByLocale["tr"]` is `setOf("kur")` and matches on
                // `endsWith(" kur")`, so "07:30 alarm kur" would become a Play Store search — the same
                // collision that already deleted "zamanlayıcı kur" / "sayaç kur" from this vocabulary
                // (review finding I2).
                suffixByLocale = mapOf("tr" to setOf("alarm ayarla")),
                argName = "time",
            ),
            Entry(
                id = Tier0ToolIds.UNINSTALL_APP,
                prefixByLocale = mapOf(
                    "en" to setOf("uninstall", "remove app"),
                    "ru" to setOf("удали приложение", "удали"),
                ),
                suffixByLocale = mapOf("tr" to setOf("uygulamasını kaldır", "kaldır")),
                argName = "app",
            ),
            // Two slots: `SaveAliasUseCase` runs its own `CommandNormalizer.normalize` on the phrase it
            // stores, so what this vocabulary hands the worker is byte-identical to what the use case
            // would have produced from the raw text (spec §7.6). `tr`'s infix "için" ("for") is a
            // postposition that follows the noun it governs, which is exactly A's position in
            // `<A> <infix> <B> <suffix>` — see the task report for why "olarak"/"diye" (the more literal
            // renderings of "as") do not fit this slot order and for the confidence caveat on this form.
            Entry(
                id = MemoryToolIds.SET_APP_ALIAS,
                prefixByLocale = mapOf(
                    "en" to setOf("call"),
                    "ru" to setOf("называй"),
                ),
                suffixByLocale = mapOf("tr" to setOf("kullan")),
                infixByLocale = mapOf(
                    "en" to setOf("as"),
                    "ru" to setOf("как"),
                    "tr" to setOf("için"),
                ),
                argName = "app",
                secondArgName = "phrase",
            ),
            Entry(
                id = MemoryToolIds.FORGET_APP_ALIAS,
                prefixByLocale = mapOf(
                    "en" to setOf("forget the name", "forget name"),
                    "ru" to setOf("забудь название", "забудь имя"),
                ),
                suffixByLocale = mapOf("tr" to setOf("adını unut")),
                argName = "phrase",
            ),
            Entry(
                id = MemoryToolIds.FORGET_LEARNED_CHOICE,
                prefixByLocale = mapOf(
                    "en" to setOf("forget what to open for"),
                    "ru" to setOf("забудь что открывать по слову", "забудь выбор для"),
                ),
                suffixByLocale = mapOf("tr" to setOf("için seçimi unut")),
                argName = "phrase",
            ),
            // A1″ Phase 3b, Task 2 (Slice A) — the first four of the eleven navigating tools. All four
            // are zero-argument, so per the class KDoc every form goes in `prefixByLocale` in every
            // locale (a zero-argument entry's two maps are interchangeable: the text must equal the
            // trigger either way). Every `en` form avoids the `open`/`launch`/`start` prefixes
            // `LAUNCH_VERBS` claims, every `tr` form avoids the ` aç`/` kur` suffixes `LAUNCH_VERBS`/
            // `INSTALL_VERBS` claim, and none is a bare `settings`/`настройки`/`ayarlar` form
            // `SETTINGS_KEYWORDS` claims (§7.6: never a bare settings synonym).
            Entry(
                id = Tier0ToolIds.SHOW_ALARMS,
                prefixByLocale = mapOf(
                    "en" to setOf("alarms", "my alarms"),
                    "ru" to setOf("будильники", "мои будильники"),
                    "tr" to setOf("alarmlar", "alarmlarım"),
                ),
            ),
            // `camera app` / `приложение камеры` / `kamera uygulaması` carry a distinguishing token on
            // purpose (see the task report): a bare `camera`/`камера`/`kamera` is what a user types
            // when an installed app IS labelled exactly that, and FastPath's own app-name resolution
            // answering first for that case is correct behaviour, not a defect this tool needs to
            // pre-empt. The bare nouns are included for ru/tr, where the installed label is unlikely
            // to match exactly.
            Entry(
                id = Tier0ToolIds.OPEN_CAMERA,
                prefixByLocale = mapOf(
                    "en" to setOf("camera app", "photo camera"),
                    "ru" to setOf("камера", "приложение камеры"),
                    "tr" to setOf("kamera", "kamera uygulaması"),
                ),
            ),
            // Finding P3 lives on the descriptor, not here: this entry only recognises the command,
            // and the SAFETY note about the landed screen belongs where the tool actually runs.
            Entry(
                id = Tier0ToolIds.OPEN_WIFI_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("wifi settings", "wi-fi settings"),
                    "ru" to setOf("настройки wi-fi", "настройки вайфая"),
                    "tr" to setOf("wi-fi ayarları", "kablosuz ayarları"),
                ),
            ),
            Entry(
                id = Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("bluetooth settings"),
                    "ru" to setOf("настройки bluetooth", "настройки блютуз"),
                    "tr" to setOf("bluetooth ayarları"),
                ),
            ),
            // A1″ Phase 3b, Task 3 (Slice B) — the next four navigating tools. Same zero-argument shape
            // and the same three exclusions as Slice A: no `open`/`launch`/`start` prefix, no ` aç`/
            // ` kur` `tr` suffix, no bare settings synonym.
            //
            // **Two of the three least-confident `tr` forms named in the plan's §0.3 are here, and
            // neither is judged by a native speaker:** `pil ayarları` (battery — `pil` is the everyday
            // word, but Samsung's own screen is «Действия с аккумулятором» / One UI's own Turkish
            // wording may differ) and `veri kullanımı` (data usage). Named, not resolved — owner-level,
            // per §0.3.
            Entry(
                id = Tier0ToolIds.OPEN_BATTERY_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("battery settings", "battery usage"),
                    "ru" to setOf("настройки батареи", "расход батареи"),
                    "tr" to setOf("pil ayarları", "pil kullanımı"),
                ),
            ),
            Entry(
                id = Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("data usage", "mobile data settings"),
                    "ru" to setOf("расход трафика", "настройки мобильных данных"),
                    "tr" to setOf("veri kullanımı", "mobil veri ayarları"),
                ),
            ),
            Entry(
                id = Tier0ToolIds.OPEN_DISPLAY_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("display settings", "screen settings"),
                    "ru" to setOf("настройки экрана", "настройки дисплея"),
                    "tr" to setOf("ekran ayarları"),
                ),
            ),
            Entry(
                id = Tier0ToolIds.OPEN_SOUND_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("sound settings", "volume settings"),
                    "ru" to setOf("настройки звука", "настройки громкости"),
                    "tr" to setOf("ses ayarları"),
                ),
            ),
            // A1″ Phase 3b, Task 4 (Slice C) — the last three navigating tools. The first two are
            // zero-argument and follow Slices A and B exactly: every form in `prefixByLocale` in every
            // locale, no `open`/`launch`/`start` `en` prefix, no ` aç`/` kur` `tr` suffix, and no bare
            // `settings`/`настройки`/`ayarlar` form — which is not a style rule but a
            // FastPath-collision rule: a bare settings synonym does not risk colliding with
            // `SETTINGS_KEYWORDS`, it **is** one.
            Entry(
                id = Tier0ToolIds.OPEN_LOCATION_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("location settings", "gps settings"),
                    "ru" to setOf("настройки геолокации", "настройки локации"),
                    "tr" to setOf("konum ayarları"),
                ),
            ),
            // **`bildirim ayarları` is the third of the three least-confident Turkish forms the plan
            // named in advance** (§0.3), after Slice B's `pil ayarları` and `veri kullanımı`: it is the
            // literal rendering, but whether One UI's own Turkish labels the screen that way is
            // exactly the question no guard and no agent can answer. Named, not resolved — owner-level,
            // and «assumed fine» is not an acceptable answer to it.
            Entry(
                id = Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS,
                prefixByLocale = mapOf(
                    "en" to setOf("notification settings"),
                    "ru" to setOf("настройки уведомлений"),
                    "tr" to setOf("bildirim ayarları"),
                ),
            ),
            // The only ARGUMENT-CARRYING entry of the eleven navigating tools, so it is the only one
            // whose `tr` form is a **suffix**: Turkish is SOV and the argument precedes, exactly as
            // `uninstall_app` above declares `uygulamasını kaldır`. `argName = "app"` only names which
            // descriptor argument the matched remainder fills (and, since an entry with an `argName`
            // refuses a bare trigger, why «app info» alone matches nothing); it opts nothing into
            // resolution. Since A4′ phase 0 `ToolMatchPlanner` resolves an app only for a tool with a
            // `ToolArgumentSorts` row — this one has one — and it reads `required`; why the
            // descriptor's `required = true` is written out and pinned by its own test is said on the
            // descriptor. Until then resolution keyed on the argument NAME regardless of `required`
            // (R14-37).
            Entry(
                id = Tier0ToolIds.OPEN_APP_INFO,
                prefixByLocale = mapOf(
                    "en" to setOf("app info", "app details"),
                    "ru" to setOf("сведения о приложении", "информация о приложении"),
                ),
                suffixByLocale = mapOf("tr" to setOf("uygulama bilgisi")),
                argName = "app",
            ),
        )
    }
}
