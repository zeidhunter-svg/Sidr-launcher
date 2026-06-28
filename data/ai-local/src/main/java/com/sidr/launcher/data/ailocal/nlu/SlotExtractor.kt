package com.sidr.launcher.data.ailocal.nlu

/**
 * Heuristic slot extraction for `LAUNCH_APP` / `SEARCH` (Open Question #1: slots are NOT modeled —
 * the classifier emits only the intent *family*). Strips a single leading verb/filler phrase from
 * the normalized command; if nothing remains, passes the whole normalized input through so
 * `IntentActionResolver` can still fuzzy-match it against installed apps.
 *
 * Pure (no `ai.onnxruntime`, no Android) — part of the P2a JVM-testable layer. Lives here, NOT in
 * the ONNX shell, so it is unit-testable without a session. Joint intent+slot is frozen to Phase 7+.
 */
object SlotExtractor {

    // Longest phrases first so "search for x" strips "search for", not just "search".
    private val LEADING_PHRASES: List<String> = listOf(
        "can you please", "could you please", "i want to", "i wanna", "i need to",
        "search for", "look up", "looking for", "fire up", "go to", "open up", "pull up",
        "show me", "take me to", "can you", "could you", "please",
        "open", "launch", "start", "run", "search", "find", "google",
    )

    fun extractSlot(normalizedInput: String): String {
        val input = normalizedInput.trim()
        for (phrase in LEADING_PHRASES) {
            if (input == phrase) return input // bare verb: nothing to strip to, keep whole
            if (input.startsWith("$phrase ")) {
                val remainder = input.removePrefix("$phrase ").trim()
                return remainder.ifEmpty { input }
            }
        }
        return input
    }
}
