package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.domain.voice.SpeechRecognitionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * JVM-only scripted fake [SpeechInputSource] (Phase 7, Block S). Proves the voice port form — the
 * `Partial → Final → Ended` success run and the `Error` terminal-as-value — with no Android. Not wired
 * into any Hilt graph; use directly in unit tests.
 *
 * Set [available] to flip the availability probe. Set [scriptedStates] (or use [scriptSuccess] /
 * [scriptError]) to drive the next [listen] collection. Language tags passed to [listen] are recorded
 * in [receivedLanguageTags].
 */
class FakeSpeechInputSource(
    var available: Boolean = true,
) : SpeechInputSource {

    /** Emitted in order on each [listen] collection. */
    var scriptedStates: List<SpeechRecognitionState> = emptyList()

    val receivedLanguageTags = mutableListOf<String?>()

    override fun isAvailable(): Boolean = available

    override fun listen(languageTag: String?): Flow<SpeechRecognitionState> = flow {
        receivedLanguageTags += languageTag
        scriptedStates.forEach { emit(it) }
    }

    /** Script a typical `Ready → Partial* → Final → Ended` success run. */
    fun scriptSuccess(partials: List<String>, finalText: String) {
        scriptedStates = buildList {
            add(SpeechRecognitionState.Ready)
            partials.forEach { add(SpeechRecognitionState.Partial(it)) }
            add(SpeechRecognitionState.Final(finalText))
            add(SpeechRecognitionState.Ended)
        }
    }

    /** Script a `Ready → Error` terminal run (the stream still completes normally). */
    fun scriptError(error: SpeechRecognitionError) {
        scriptedStates = listOf(
            SpeechRecognitionState.Ready,
            SpeechRecognitionState.Error(error),
        )
    }

    fun reset() {
        available = true
        scriptedStates = emptyList()
        receivedLanguageTags.clear()
    }
}
