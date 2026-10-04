package com.sidr.launcher.domain.ai.bridge

/**
 * "The model wrote the user's words" made checkable (spec §7.12.1, D1 mechanism 1): a value is
 * derivable when its words appear, whole and in order, in the command — case-insensitive, punctuation
 * ignored. The base case only, by design: «телегу» is not «телега», and a reference to an earlier turn
 * is not in the command at all. Both end as honest refusals (3b, `B16`), never as a guess.
 */
object Derivability {

    fun isDerivable(mention: String, command: String): Boolean {
        val wanted = words(mention)
        if (wanted.isEmpty()) return false
        val typed = words(command)
        return typed.windowed(wanted.size).any { it == wanted }
    }

    fun originOf(value: String, command: String): ValueOrigin =
        if (isDerivable(value, command)) ValueOrigin.SaidBy(PrincipalId.USER) else ValueOrigin.ModelGenerated

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")

    private fun words(text: String): List<String> = text.lowercase().split(SEPARATORS).filter { it.isNotEmpty() }
}
