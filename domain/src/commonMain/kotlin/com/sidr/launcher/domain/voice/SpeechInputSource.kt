package com.sidr.launcher.domain.voice

import kotlinx.coroutines.flow.Flow

/**
 * Port: speech-to-text **input modality** (Phase 7, Block S).
 *
 * Voice is **not** a fourth pipeline — it produces the same normalized text the keyboard produces,
 * which then feeds the existing command-input → `IntentMatcher` path (and may pre-fill the assistant
 * prompt). It is therefore neither an `IntentMatcher` nor a `GenerativeAiEngine` (the three-port
 * invariant holds). The Android implementation over `android.speech.SpeechRecognizer` lives in
 * `:core:android` (Block T); a JVM `FakeSpeechInputSource` in `:core:testing` proves this contract.
 *
 * [listen] is a **cold** flow: collecting it starts recognition and emits
 * `Ready → Partial(text)* → Final(text) → Ended`, or terminates with `Error(...)`. Terminal events are
 * **values, not exceptions** (the `Flow<AiChunk>` precedent): implementations must not throw expected
 * errors to the collector. Cancelling collection cancels recognition (the impl calls `destroy()` on
 * `awaitClose`).
 *
 * When [isAvailable] is `false` (no recognizer / no on-device pack), callers degrade to keyboard input
 * — voice never blocks the launcher core.
 */
interface SpeechInputSource {
    /** Best-effort snapshot: is any speech recognizer usable right now? `false` → degrade to keyboard. */
    fun isAvailable(): Boolean

    /**
     * Start recognition. On-device recognition is preferred by the impl (Block T). [languageTag] is an
     * optional BCP-47 tag (e.g. "en-US"); null lets the recognizer choose its default.
     */
    fun listen(languageTag: String? = null): Flow<SpeechRecognitionState>
}
