package com.sidr.launcher.domain.voice

/**
 * One event in a [SpeechInputSource.listen] stream (Phase 7, Block S).
 *
 * Ordering for a successful run: [Ready] → [Partial]* → [Final] → [Ended]. The stream may instead
 * terminate with [Error]. [Ended] and [Error] are the two **terminal** events — after either, the flow
 * completes normally. Like `AiChunk`, terminal failures are **values, not exceptions**.
 */
sealed interface SpeechRecognitionState {
    /** Recognizer is ready and listening for speech. */
    data object Ready : SpeechRecognitionState

    /** An interim hypothesis; may be revised by later [Partial]s or the [Final]. */
    data class Partial(val text: String) : SpeechRecognitionState

    /** The recognized text. Feeds the existing command path exactly as keyboard text would. */
    data class Final(val text: String) : SpeechRecognitionState

    /** Terminal: recognition failed. Carries a pure [SpeechRecognitionError] — never thrown. */
    data class Error(val error: SpeechRecognitionError) : SpeechRecognitionState

    /** Terminal: recognition finished (after a [Final], or with no match handled upstream). */
    data object Ended : SpeechRecognitionState
}
