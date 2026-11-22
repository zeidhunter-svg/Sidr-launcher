package com.sidr.launcher.domain.text

import com.sidr.launcher.domain.model.InstalledApp

/**
 * How a typed name answers to installed labels (A4′ phase 3b, B-2). [Exact] — every word is the label's
 * word; [Edge] — at least one word is a FORM of the label's word (a case ending, a suffix, a prefix), the
 * rest exact. An edge match against a LABEL is only ever offered for the user's confirmation, never bound. The one
 * exception is an alias's phrase in FastPath's launch (A4′ acceptance tail, task 3; owner 2026-11-21 «алиасы не
 * спрашивать»): there an edge of the user's own alias launches its app (`IntentActionResolver`); step 2b and the
 * bridge still only offer a form of an alias (`AppTargetResolver`).
 */
sealed interface NameMatch {
    data class Exact(val apps: List<InstalledApp>) : NameMatch
    data class Edge(val apps: List<InstalledApp>) : NameMatch
    data object None : NameMatch
}

/** A window's answer; [whole] = the window is all the words, so nothing the user said is left over. */
data class WindowMatch(val match: NameMatch, val whole: Boolean)

/**
 * The one name matcher (A4′ phase 3b, forks 3b-G and 3b-E; plan §0.2 B-2) — no language tables: a word
 * form is recognised by its shape at the two ends of the word, in any script the folding keeps letters
 * in. Both sides pass the same [TextFolding]; words are compared folded.
 *
 * **The edge rule** ([wordsMatch]), for a typed word `a` and a label word `b`:
 *  - (i) an ENDING — a common prefix of `p ≥` [MIN_STEM] letters, at most [MAX_ENDING] letters left on
 *    each side, and `3p ≥ 2·max(|a|, |b|)`, so the stem is two thirds of the longer word («миравину» /
 *    «Миравин», «Vemblic'i» / «Vemblic»; «lum» / «Lumora» is a fragment, not a form);
 *  - (ii) a PREFIX — `b` has at least [MIN_STEM] letters and occurs in `a` at offset 1..[MAX_PREFIX], with
 *    at most [MAX_ENDING] letters after it («بالسنداف» / «سنداف»).
 *
 * The constants are **chosen, not measured** (B-2). Against a label a wrong value costs one question, never a bind,
 * because an edge match of a label is only ever offered. Against an alias's phrase in FastPath's launch (task 3 of the
 * A4′ acceptance tail) a wrong value CAN launch: a word the constants wrongly take for a form of the user's alias opens
 * that alias's app — a `SAFE` launch of an app the user named, not an irreversible act; 2b and the bridge still offer it.
 *
 * **Two uses** (§0.1 refinement):
 *  - [matchWhole] — wherever a whole name is matched: the bridge's mention, step 2b's first look at the
 *    whole slot (`AppTargetResolver.windows`, wired in Task 5), and FastPath's launch
 *    (`IntentActionResolver`, Task 7);
 *  - [matchWindows] — windows of the user's words, for step 2b only (Task 5). A window short of the words
 *    is a confirmation, never a bind (phase 1's F1-2: the extra words may name another app).
 *
 * **Named limits, not tested:**
 *  - **CJK.** Lengths count UTF-16 code units, and an ICU-segmented CJK label may come apart into single
 *    ideographs (`IcuTextFolding`: «打开微信» → [打开, 微, 信]); a one-character word is below [MIN_STEM], so
 *    an edge is effectively never offered on CJK, and exact works per segment.
 *  - **No Unicode normalisation here.** Both sides pass the same fold, and the fold does not normalise
 *    (`ScriptFolding`): a name typed decomposed (NFD) against a precomposed (NFC) label is NOT found —
 *    never a wrong bind. NFC, if it is ever needed, belongs to the platform [TextFolding] implementations,
 *    not to this matcher.
 */
object AppNameMatcher {
    const val MIN_STEM = 3
    const val MAX_ENDING = 3
    const val MAX_PREFIX = 3

    /** Two folded words: `true` exact, `false` edge (a form), `null` no match. */
    fun wordsMatch(typed: String, label: String): Boolean? = when {
        typed == label -> true
        byEnding(typed, label) || byPrefix(typed, label) -> false
        else -> null
    }

    /**
     * An app matches when its label has exactly as many words as [words] and every pair is exact or edge;
     * it is exact iff every pair is. Any exact app → [NameMatch.Exact] of those (edges dropped); else any
     * edge → [NameMatch.Edge] of ALL of them, in [apps]' order — never one picked.
     */
    fun matchWhole(words: String, apps: List<InstalledApp>, folding: TextFolding): NameMatch {
        val typed = folding.words(words)
        if (typed.isEmpty()) return NameMatch.None
        return matchOf(typed, labelled(apps, folding).filter { (_, label) -> label.size == typed.size })
    }

    /**
     * Windows of consecutive words, longest first. At the first length where any window matches (as in
     * [matchWhole]), the answer is the union over every window of that length — deduplicated by package,
     * exact over edge — and [WindowMatch.whole] says whether that length is all the words (E4-P6).
     */
    fun matchWindows(words: String, apps: List<InstalledApp>, folding: TextFolding): WindowMatch {
        val typed = folding.words(words)
        val labelled = labelled(apps, folding)
        for (length in typed.size downTo 1) {
            val sized = labelled.filter { (_, label) -> label.size == length }
            if (sized.isEmpty()) continue
            val exact = LinkedHashMap<String, InstalledApp>()
            val edge = LinkedHashMap<String, InstalledApp>()
            for (window in typed.windowed(length)) {
                when (val found = matchOf(window, sized)) {
                    is NameMatch.Exact -> found.apps.forEach { exact.getOrPut(it.packageName) { it } }
                    is NameMatch.Edge -> found.apps.forEach { edge.getOrPut(it.packageName) { it } }
                    NameMatch.None -> Unit
                }
            }
            val match = when {
                exact.isNotEmpty() -> NameMatch.Exact(exact.values.toList())
                edge.isNotEmpty() -> NameMatch.Edge(edge.values.toList())
                else -> continue
            }
            return WindowMatch(match, whole = length == typed.size)
        }
        return WindowMatch(NameMatch.None, whole = false)
    }

    private fun labelled(apps: List<InstalledApp>, folding: TextFolding): List<Pair<InstalledApp, List<String>>> =
        apps.map { it to folding.words(it.label) }

    /** Over labels already of the typed words' length: each pair exact or edge, exact over edge. */
    private fun matchOf(typed: List<String>, labelled: List<Pair<InstalledApp, List<String>>>): NameMatch {
        val exact = mutableListOf<InstalledApp>()
        val edge = mutableListOf<InstalledApp>()
        for ((app, label) in labelled) {
            when (pairsOf(typed, label)) {
                true -> exact += app
                false -> edge += app
                null -> Unit
            }
        }
        return when {
            exact.isNotEmpty() -> NameMatch.Exact(exact)
            edge.isNotEmpty() -> NameMatch.Edge(edge)
            else -> NameMatch.None
        }
    }

    /** `true` every pair exact, `false` every pair matched and one at least by edge, `null` a pair unmatched. */
    private fun pairsOf(typed: List<String>, label: List<String>): Boolean? {
        var allExact = true
        for ((a, b) in typed.zip(label)) {
            val pair = wordsMatch(a, b) ?: return null
            if (!pair) allExact = false
        }
        return allExact
    }

    private fun byEnding(a: String, b: String): Boolean {
        val p = a.commonPrefixWith(b).length
        return p >= MIN_STEM && a.length - p <= MAX_ENDING && b.length - p <= MAX_ENDING &&
            3 * p >= 2 * maxOf(a.length, b.length)
    }

    private fun byPrefix(a: String, b: String): Boolean =
        b.length >= MIN_STEM &&
            (1..MAX_PREFIX).any { offset -> a.startsWith(b, offset) && a.length - offset - b.length <= MAX_ENDING }
}
