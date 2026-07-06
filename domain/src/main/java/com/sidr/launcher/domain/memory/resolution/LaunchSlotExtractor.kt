package com.sidr.launcher.domain.memory.resolution

/**
 * Minimal deterministic slot extractor for the LAUNCH_APP ambiguity flow ONLY — NOT a general NLU/parser.
 * From an already-normalized command it strips a leading launch verb + leading determiner/possessive and a
 * trailing "app"/"application", yielding the app slot. Falls back to the input if stripping empties it.
 *
 *   open bank → bank ; launch bank → bank ; open my bank app → bank ; go to bank → bank
 */
object LaunchSlotExtractor {
    private val LEADING = setOf(
        "open", "launch", "start", "run", "go", "to", "show", "get", "the", "a", "an", "my", "this",
    )
    private val TRAILING = setOf("app", "application")

    fun slotOf(normalizedCommand: String): String {
        val tokens = normalizedCommand.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.first() in LEADING) tokens.removeAt(0)
        while (tokens.isNotEmpty() && tokens.last() in TRAILING) tokens.removeAt(tokens.size - 1)
        return tokens.joinToString(" ").ifBlank { normalizedCommand }
    }
}
