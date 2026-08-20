package com.sidr.launcher.feature.launcher

import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.domain.voice.SpeechRecognitionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Этап 4 / A0, preparatory split. Voice input (Block T) extracted unchanged from
 * `LauncherViewModel.startVoiceInput()`: same collection, same `Partial`/`Final`/`Error`/`Ended`
 * branching, same restart-on-retap semantics (a new [start] cancels any in-flight recognition).
 *
 * The `micInputEnabled` preference gate and the `isAvailable()`-unavailable feedback stay in
 * `LauncherViewModel` (they read [com.sidr.launcher.domain.preferences.UserPreferences] and write
 * `commandFeedback`, both ViewModel-owned state this class has no access to) — only the recognizer
 * collection loop moves here. The three callbacks are the exact three side effects the original
 * `collect { }` block performed inline (`setCommandInput` on Partial/Final, `onCommandSubmitted` on
 * Final, and the `CommandFeedback.VoiceError` write on Error); wiring them at construction keeps
 * `start(languageTag)`'s public signature identical to the original `startVoiceInput(languageTag)`.
 */
internal class LauncherVoiceInput(
    private val speechInputSource: SpeechInputSource,
    private val scope: CoroutineScope,
    private val onPartial: (String) -> Unit,
    private val onFinal: (String) -> Unit,
    private val onError: (SpeechRecognitionError) -> Unit,
) {

    private val _state = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Ready)
    val state: StateFlow<SpeechRecognitionState> = _state.asStateFlow()

    // Tracks the in-flight recognition so a second call restarts cleanly (cancelling the previous
    // collection calls destroy() on the recognizer via the impl's awaitClose) — same as the original
    // ViewModel-private `voiceJob`.
    private var job: Job? = null

    fun start(languageTag: String? = null) {
        job?.cancel()
        job = scope.launch {
            speechInputSource.listen(languageTag).collect { recognitionState ->
                _state.value = recognitionState
                when (recognitionState) {
                    SpeechRecognitionState.Ready -> Unit
                    is SpeechRecognitionState.Partial -> onPartial(recognitionState.text)
                    is SpeechRecognitionState.Final -> onFinal(recognitionState.text)
                    is SpeechRecognitionState.Error -> onError(recognitionState.error)
                    SpeechRecognitionState.Ended -> Unit
                }
            }
        }
    }
}
