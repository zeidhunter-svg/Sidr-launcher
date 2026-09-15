package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.intent.CommandNormalizer
import javax.inject.Inject

/**
 * One command, at most one tool — over a registry that is now partly **third-party and dynamic**.
 *
 * `ToolVocabulary` alone was enough for two authored tools. It is not enough here for a reason its own
 * KDoc predicted: `match` ends `singleOrNull`, so ambiguity yields nothing, and a table entry shadowed
 * by a shortcut label is invisible to `ToolVocabularyReachabilityTest` because the shortcut is not in
 * the table. The ordering below is the whole of the policy, and each rule is here because of a failure
 * mode rather than a preference (spec §6.3):
 *
 *  1. **Authored beats dynamic, outright.** Our vocabulary is deliberate and localized; an app's label
 *     is neither, and any app may ship a shortcut named "system settings". This includes the case where
 *     our own vocabulary is *ambiguous* about a text — two authored entries claiming the same words is
 *     still an authored outcome, and it must decline rather than let a dynamic name break the tie.
 *     [ToolVocabulary.isAmbiguous] exists so this class can tell that refusal apart from "nothing of
 *     ours claimed this text" and stop there rather than falling through.
 *
 *     **What that check does not cover, narrowed by Task 10b rather than closed.** `ToolVocabulary.match`
 *     returns `null` for a *third* reason besides "nothing claimed it" and "two entries claimed it": one
 *     entry claimed a prefix and then **refused** the rest — a leftover-words decline, the
 *     `"system settings for my car"` case `ToolVocabulary.Entry.toMatch`'s own KDoc argues for.
 *     [ToolVocabulary.isAmbiguous] is `false` there too (exactly one entry produced a hit before its own
 *     refusal), so such a text still falls through to the dynamic branch. What no longer follows from that
 *     fall-through, since rule 4 below, is a stray match: the dynamic branch now requires its own leftover
 *     words to be accounted for by the same shortcut's name or qualifier tokens, so "for my car" declines
 *     there too — unless some shortcut happens to be named, or published by an app named, with tokens that
 *     cover exactly those leftover words. That residual coincidence is what remains of this gap; it is not
 *     the general fall-through the previous wording described.
 *  2. **A dynamic candidate must name its app.** "telegram new message" matches; bare "new message"
 *     does not, even with exactly one candidate. A shortcut launch performs an effect, so a false
 *     positive must be structurally unlikely, not statistically unlikely.
 *
 *     **This rule is vacuous whenever the shortcut's own name already contains an app-name token** —
 *     the app-token requirement is then implied by the name requirement and constrains nothing. A
 *     one-word shortcut named e.g. "Settings" from a qualifier "Settings" is reachable by that single
 *     word alone; the same is true for a real app whose shortcut label repeats its own name, such as
 *     "WhatsApp Web". Rule 2 is real protection only when the shortcut's name and its app's name share
 *     no words.
 *  3. **Equal candidates decline.** `singleOrNull` refuses on *any* two hits, not only on hits that are
 *     equally specific — two shortcuts sharing the same qualifier and the same name are discarded
 *     together rather than picked by insertion order (`ToolSelectorTest`'s "two dynamic candidates of
 *     equal strength decline rather than guessing"). That is fail-closed and deliberate (this class is
 *     not a ranker), stated here because "equal candidates" undersells what the code actually does: it
 *     declines on ambiguity, not on a tie.
 *
 *     **Since rule 4, a genuine ambiguity needs both candidates' name-and-qualifier tokens to
 *     independently cover every command token.** A strictly-less-specific name (e.g. "New" beside
 *     "New message") no longer ties for a command that carries the extra word, because rule 4 now
 *     eliminates it alone rather than leaving both hits standing — `ToolSelectorTest`'s "rule 4 can
 *     turn a former tie into a single survivor that now selects" pins the resulting behaviour change.
 *  4. **Every token of the command must be accounted for** (Task 10b, owner-approved fix ahead of Task
 *     12 — Task 10's review, Important 3). Contiguity (below) bounds the shortcut's own name from below —
 *     the whole name must appear — but until this rule existed nothing bounded the *command* from above:
 *     any number of extra words around a matched name-and-qualifier pair were accepted, so
 *     "отправь saved messages в telegram" ("send saved messages to telegram" — an intent to *send*, not
 *     to open) selected and would have launched the "Saved Messages" shortcut, because the qualifier
 *     token and the contiguous name run were both present regardless of what the rest of the sentence
 *     meant. Formally: every token of the normalized command must be present in the shortcut's own name
 *     tokens or its qualifier tokens — `commandTokens ⊆ nameTokens ∪ qualifierTokens`. Note the direction:
 *     it is the *command's* tokens that must all be allowed, not the reverse, so a two-word app label like
 *     "WhatsApp Business" still matches "whatsapp new message" even though the command never says
 *     "business" — the shortcut need not use every token it is allowed to use.
 *
 *     **Why not strip a launch verb instead** (reusing `RuleBasedIntentMatcher.LAUNCH_VERBS`, stripping a
 *     leading/trailing verb before matching) **— considered and rejected on evidence, not on cost.**
 *     `RuleBasedIntentMatcher` returns `LaunchAppIntent` for a verb-led command ("открой telegram new
 *     message", or the `tr` suffix shape "... aç") at confidence 0.90 — above the 0.85 auto-execute
 *     threshold — so FastPath always *decides* such a command: it can never come back
 *     `Unknown`/`LowConfidence`, the two states `RouteCommandUseCase` calls undecided, and step 2b
 *     runs only on an undecided command. What a decided verb-led command does next depends on whether
 *     the app resolves: a match just launches, and a miss is `NoAppFound`, which **step 2** (not 2b)
 *     takes into the A0 launch→store plan. Either way it never reaches step 2b and therefore never
 *     reaches this selector at all — a verb table here would be machinery built for inputs that
 *     structurally cannot arrive.
 *
 * **What "naming its app" and "naming itself" actually require.** The app-qualifier check is a loose,
 * order-free membership test: any one word of the qualifier appearing anywhere in the command is enough.
 * The shortcut's own name is stricter: it must appear as a **contiguous run on word boundaries** in the
 * normalized command — the same word-boundary grammar `ToolVocabulary.Entry.matchIn` already uses for
 * the authored half. A token-set test here would let "message new telegram" select the same tool as
 * "telegram new message"; recall bought by treating third-party data as a bag of words is not a trade
 * this repo makes for a match that fires an effect.
 *
 * **Recall gaps this branch does not attempt to close, named rather than silently accepted.** The
 * app-token match is **exact**: an inflected form of the app's own name — Russian `в телеграме`,
 * Turkish `telegramda`, `яндексе` — will not match a qualifier token `телеграм`/`telegram`/`яндекс`, so a
 * shortcut is unreachable by any sentence that inflects its app's name. This is recall-only: it never
 * produces a false positive, but it is the gate the entire dynamic branch sits behind. Separately,
 * [CommandNormalizer] lowercases with `Locale.ROOT` and strips no punctuation, so some third-party
 * labels are unreachable by construction rather than by any rule above: a label ending in an ellipsis
 * ("New message…") normalizes to a token with the ellipsis still attached, and Turkish `İletiler`
 * normalizes to a form a user typing plain `iletiler` never produces.
 *
 * **Rule 4 has its own recall cost, real since Task 10b and not merely theoretical.** Any function
 * word in the command that is neither a name token nor a qualifier token kills an otherwise-good
 * match: `"new chat in whatsapp"` declines because of `"in"`; `"новое сообщение в телеграм"` declines
 * because of `"в"`. Both are verbless, so unlike the verb-led case above — which never reaches this
 * selector at all — these **do** reach step 2b and are declined here. The trade is deliberate: a
 * decline is free and falls through to the model planner exactly as any other miss, while a false
 * positive fires an effect. Closing this would need a per-locale stopword set — a scope decision for
 * the owner, not one this rule takes on its own.
 *
 * It does not learn, rank by usage, or consult a model — those are A2/A3 and A4′'s plan cache, and a
 * selector whose behaviour depends on history is untestable by the guards this block ships.
 */
class ToolSelector @Inject constructor(
    private val vocabulary: ToolVocabulary,
    private val dynamicNames: DynamicToolNames,
) {
    fun select(text: String): ToolMatch? {
        val normalized = CommandNormalizer.normalize(text)
        if (normalized.isEmpty()) return null

        vocabulary.match(normalized)?.let { return it }
        if (vocabulary.isAmbiguous(normalized)) return null

        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        val hits = dynamicNames.names().filter { it.matches(normalized, tokens) }
        return hits.singleOrNull()?.let { ToolMatch(it.id, emptyMap()) }
    }

    /**
     * The app name ([qualifier]) may appear anywhere in the command, in any order — a set-membership
     * test. The shortcut's own [name] may not: it must appear as a contiguous, word-bounded run inside
     * [normalizedText] — see the class KDoc for why a token-set test is not good enough here, and for
     * rule 4, added by Task 10b: every token of [normalizedText] must belong to [name]'s or [qualifier]'s
     * own tokens, or this declines. [tokens] is a `Set`, so rule 4 is about which **distinct** words
     * the command uses, not how many times each occurs — "telegram new message new message" still
     * selects.
     *
     * **A blank [name] needs no guard of its own.** `normalizedText` reaches this function already
     * normalized and non-blank ([select] returns before calling it otherwise), so it never contains a
     * double space; the contiguity check below therefore already rejects an empty `normalizedName` — its
     * padded needle is two spaces, which a normalized command can never contain — for every possible
     * input. Fix round 2 removed a `normalizedName.isBlank()` disjunct that used to sit here for exactly
     * that reason: it was live under the token-set implementation this method started with, and became
     * dead the moment contiguity replaced it. `qualifierTokens.isEmpty()` below is separately redundant
     * with the qualifier-presence check that follows it (`none {}` on an empty collection is already
     * `true`) — noted in the fix-round-1 review and left in place rather than removed there, unchanged
     * again here.
     */
    private fun DynamicToolName.matches(normalizedText: String, tokens: Set<String>): Boolean {
        val qualifierTokens = CommandNormalizer.normalize(qualifier).split(' ').filter { it.isNotBlank() }
        val normalizedName = CommandNormalizer.normalize(name)
        val nameTokens = normalizedName.split(' ').filter { it.isNotBlank() }
        if (qualifierTokens.isEmpty()) return false
        if (!" $normalizedText ".contains(" $normalizedName ")) return false
        if (qualifierTokens.none { it in tokens }) return false
        val allowed = nameTokens.toSet() + qualifierTokens
        return tokens.all { it in allowed }
    }
}
