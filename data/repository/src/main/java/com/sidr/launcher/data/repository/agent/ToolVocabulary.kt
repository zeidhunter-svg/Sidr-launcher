package com.sidr.launcher.data.repository.agent

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
 * **Locale shape mirrors `RuleBasedIntentMatcher.VerbForms`:** `en`/`ru` are prefix (SVO) languages,
 * `tr` is a suffix (SOV) language. [ToolVocabularyLocaleGuardTest] asserts every entry carries a
 * non-empty form for all three locales, so a tool added with English triggers only is red rather than
 * quietly monolingual.
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
     * follows (or precedes, in `tr`) the trigger is handed over **verbatim** as that argument's value.
     * Reading the value is the worker's business, not the vocabulary's.
     *
     * For a **zero-argument** entry the two maps are interchangeable — the text must equal the trigger
     * either way — so such an entry declares all its forms, in every locale, as prefixes.
     */
    data class Entry(
        val id: ToolId,
        val prefixByLocale: Map<String, Set<String>>,
        val suffixByLocale: Map<String, Set<String>> = emptyMap(),
        val argName: String? = null,
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
     * A trigger must end on a word boundary — the text either **is** the trigger or continues after a
     * space. A bare `startsWith` reads "timer format" as a timer of duration "mat": recognised, planned,
     * and only failing three layers down in the worker's duration parse. `RuleBasedIntentMatcher`
     * matches `"$verb "` for the same reason.
     *
     * The longest matching form of an entry wins, so a trigger may be extended with a more specific one
     * without the shorter form swallowing it.
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
        argName == null -> if (remainder.isBlank()) ToolMatch(id, emptyMap()) else null
        remainder.isBlank() -> null
        else -> ToolMatch(id, mapOf(argName to remainder))
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
        )
    }
}
