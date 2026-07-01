package com.sidr.launcher.core.android.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.sidr.launcher.domain.voice.SpeechInputSource
import com.sidr.launcher.domain.voice.SpeechRecognitionError
import com.sidr.launcher.domain.voice.SpeechRecognitionState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of the [SpeechInputSource] port (Phase 7, Block T) over the framework
 * [android.speech.SpeechRecognizer].
 *
 * **On-device preferred (Fork F7-1).** When the platform exposes an on-device recognizer
 * (API 31+ and [SpeechRecognizer.isOnDeviceRecognitionAvailable]) we use it so audio never leaves
 * the device; otherwise we fall back to the system recognizer biased offline via
 * [RecognizerIntent.EXTRA_PREFER_OFFLINE]. Either way the recognizer is the **user's own** service,
 * never a Sidr backend (there is none — BYOK).
 *
 * **Threading.** `SpeechRecognizer`'s methods "must be invoked only from the main application
 * thread" (framework contract). [listen] is a cold [callbackFlow] whose recognizer construction,
 * `startListening`, and teardown are all marshalled onto the main [Looper] via [mainHandler];
 * the `RecognitionListener` callbacks already arrive on the main thread, and `trySend` is
 * thread-safe. `destroy()` (mandatory per the contract) runs on `awaitClose` so cancelling
 * collection releases the recognizer.
 *
 * **Terminal-as-value (the `Flow<AiChunk>` precedent).** Expected failures are emitted as
 * [SpeechRecognitionState.Error] then the flow completes normally — nothing is thrown to the
 * collector. A missing `RECORD_AUDIO` grant surfaces as `onError(ERROR_INSUFFICIENT_PERMISSIONS)`
 * → [SpeechRecognitionError.PERMISSION_DENIED], not a crash.
 *
 * **Availability probing.** Some devices report `isRecognitionAvailable=false` even when a speech
 * recognizer component is present but hidden behind Android 11+ package-visibility rules or an OEM
 * quirk. We therefore treat a resolvable `RecognitionService` / `ACTION_RECOGNIZE_SPEECH` handler
 * as a valid fallback signal before hiding the mic affordance.
 *
 * **Privacy.** No transcript, partial, or audio is ever logged or persisted here.
 *
 * Plain class (no Hilt annotations) so `core/android` stays DI-framework-free; constructed in
 * `:app` with the application [Context] (the [com.sidr.launcher.core.android.permission.AndroidPermissionChecker]
 * / `AndroidConnectivityChecker` precedent). Not unit-tested in `core/android` (no test deps there —
 * the JVM-only `:core:testing` rule); covered by `FakeSpeechInputSource` + a device run (OQ#4).
 */
class AndroidSpeechInputSource(
    private val context: Context,
) : SpeechInputSource {

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Best-effort snapshot. True when a system recognizer is installed, or (API 31+) an on-device
     * recognizer is present. `false` → the caller hides the mic and degrades to keyboard input.
     */
    override fun isAvailable(): Boolean =
        SpeechRecognizerAvailability.snapshot(context).isAvailable()

    override fun listen(languageTag: String?): Flow<SpeechRecognitionState> = callbackFlow {
        // Touched only on the main looper (both the create-post and the teardown-post run there),
        // so no cross-thread synchronisation is needed for the reference itself.
        var recognizer: SpeechRecognizer? = null

        // Set on the flow's coroutine thread when collection is torn down; read on the main thread
        // by a create-post that is still queued behind the teardown. Atomic for safe visibility.
        val torndown = AtomicBoolean(false)

        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                trySend(SpeechRecognitionState.Ready)
            }

            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults.firstRecognition()?.let {
                    trySend(SpeechRecognitionState.Partial(it))
                }
            }

            override fun onResults(results: Bundle?) {
                results.firstRecognition()?.let { trySend(SpeechRecognitionState.Final(it)) }
                trySend(SpeechRecognitionState.Ended)
                close() // success terminal — triggers awaitClose → destroy()
            }

            override fun onError(error: Int) {
                trySend(SpeechRecognitionState.Error(error.toRecognitionError()))
                close() // failure terminal-as-value — the flow still completes normally
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        }

        mainHandler.post {
            // Collection may already have been cancelled before this post ran; if so, create then
            // immediately release so we never leak a live recognizer/microphone session.
            val sr = createRecognizer()
            if (torndown.get()) {
                sr.destroy()
                return@post
            }
            recognizer = sr
            sr.setRecognitionListener(listener)
            sr.startListening(recognizerIntent(languageTag))
        }

        awaitClose {
            torndown.set(true)
            mainHandler.post {
                recognizer?.apply {
                    // stop/cancel are best-effort (the run may already be finished); destroy() is
                    // mandatory and releases the microphone.
                    runCatching { stopListening() }
                    runCatching { cancel() }
                    destroy()
                }
                recognizer = null
            }
        }
    }

    /** Main-thread only. On-device preferred where the platform supports it; else system recognizer. */
    private fun createRecognizer(): SpeechRecognizer =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }

    private fun recognizerIntent(languageTag: String?): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            // Bias the engine to offline transcription where it can (API 23+); the on-device
            // recognizer above already keeps audio local when present.
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            languageTag?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
        }

    /**
     * Map the framework error code to the pure domain taxonomy. All `ERROR_*` are compile-time
     * `static final int` constants (inlined), so referencing the API-31/33 ones is safe below
     * those API levels — the device simply never emits them on older builds.
     */
    private fun Int.toRecognitionError(): SpeechRecognitionError = when (this) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechRecognitionError.PERMISSION_DENIED
        SpeechRecognizer.ERROR_NO_MATCH -> SpeechRecognitionError.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechRecognitionError.TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> SpeechRecognitionError.BUSY
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechRecognitionError.NETWORK
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechRecognitionError.UNAVAILABLE
        // ERROR_AUDIO, ERROR_CLIENT, ERROR_CANNOT_* and anything unmapped.
        else -> SpeechRecognitionError.UNKNOWN
    }

    /** Extract the top hypothesis from a results [Bundle]; null/blank → null (no transcript logged). */
    private fun Bundle?.firstRecognition(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
}

internal object SpeechRecognizerAvailability {

    fun snapshot(context: Context): Snapshot = Snapshot(
        frameworkAvailable = SpeechRecognizer.isRecognitionAvailable(context),
        onDeviceAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context),
        hasRecognitionService = context.hasResolvableService(Intent(RecognitionService.SERVICE_INTERFACE)),
        hasRecognizeSpeechActivity = context.hasResolvableActivity(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
        ),
    )

    data class Snapshot(
        val frameworkAvailable: Boolean,
        val onDeviceAvailable: Boolean,
        val hasRecognitionService: Boolean,
        val hasRecognizeSpeechActivity: Boolean,
    ) {
        fun isAvailable(): Boolean =
            frameworkAvailable ||
                onDeviceAvailable ||
                hasRecognitionService ||
                hasRecognizeSpeechActivity
    }

    private fun Context.hasResolvableService(intent: Intent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveService(intent, PackageManager.ResolveInfoFlags.of(0L)) != null
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveService(intent, 0) != null
        }

    private fun Context.hasResolvableActivity(intent: Intent): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0L)) != null
        } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0) != null
        }
}
