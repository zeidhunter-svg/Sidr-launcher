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
 *  3. **Equal candidates decline.** `singleOrNull` refuses on *any* two hits, not only on hits of equal
 *     specificity — a shortcut named "New" and one named "New message" are both discarded when both
 *     match, even though one name is strictly more specific than the other. That is fail-closed and
 *     deliberate (this class is not a ranker), stated here because "equal candidates" undersells what
 *     the code actually does: it declines on ambiguity, not on a tie.
 *
 * **What "naming its app" and "naming itself" actually require.** The app-qualifier check is a loose,
 * order-free membership test: any one word of the qualifier appearing anywhere in the command is enough.
 * The shortcut's own name is stricter: it must appear as a **contiguous run on word boundaries** in the
 * normalized command — the same word-boundary grammar `ToolVocabulary.Entry.matchIn` already uses for
 * the authored half. A token-set test here would let "message new telegram" select the same tool as
 * "telegram new message"; recall bought by treating third-party data as a bag of words is not a trade
 * this repo makes for a match that fires an effect.
 *
 * **Known limitation, left as the owner's to weigh, not fixed here.** Contiguity does not require that
 * every other word in the command be accounted for. A command that merely *contains* the app token and
 * the full shortcut name still selects even when the surrounding words mean something else — e.g.
 * "don't send a telegram new message" selects the "new message" shortcut exactly as "telegram new
 * message" does, because both the qualifier token and the contiguous name run are present either way.
 * Forbidding unaccounted leftover words (the way `ToolVocabulary.Entry.toMatch` refuses them for a
 * zero-argument authored tool) would close this but also decline every natural sentence that happens to
 * carry the shortcut's name inside a longer command with its own verb.
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
     * the limitation this stricter rule does not close.
     */
    private fun DynamicToolName.matches(normalizedText: String, tokens: Set<String>): Boolean {
        val qualifierTokens = CommandNormalizer.normalize(qualifier).split(' ').filter { it.isNotBlank() }
        val normalizedName = CommandNormalizer.normalize(name)
        if (qualifierTokens.isEmpty() || normalizedName.isBlank()) return false
        if (!" $normalizedText ".contains(" $normalizedName ")) return false
        return qualifierTokens.any { it in tokens }
    }
}
