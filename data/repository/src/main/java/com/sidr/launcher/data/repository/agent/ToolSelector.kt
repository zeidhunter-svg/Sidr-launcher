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
 *     is neither, and any app may ship a shortcut named "system settings".
 *  2. **A dynamic candidate must name its app.** "telegram new message" matches; bare "new message"
 *     does not, even with exactly one candidate. A shortcut launch performs an effect, so a false
 *     positive must be structurally unlikely, not statistically unlikely.
 *  3. **Equal candidates decline.** Breaking a tie would choose an effect for the user in silence. A
 *     miss is free: routing falls through to the existing chain exactly as before.
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

        val tokens = normalized.split(' ').filter { it.isNotBlank() }.toSet()
        val hits = dynamicNames.names().filter { it.matches(tokens) }
        return hits.singleOrNull()?.let { ToolMatch(it.id, emptyMap()) }
    }

    /**
     * Every word of the shortcut's own name must be present, **and** at least one word of the app's
     * name. Requiring all of the name rather than any of it is what keeps "telegram new message" from
     * reaching a shortcut called "new saved message": a looser rule buys recall in a place where a
     * wrong answer performs an effect.
     */
    private fun DynamicToolName.matches(tokens: Set<String>): Boolean {
        val qualifierTokens = CommandNormalizer.normalize(qualifier).split(' ').filter { it.isNotBlank() }
        val nameTokens = CommandNormalizer.normalize(name).split(' ').filter { it.isNotBlank() }
        if (qualifierTokens.isEmpty() || nameTokens.isEmpty()) return false
        return qualifierTokens.any { it in tokens } && nameTokens.all { it in tokens }
    }
}
