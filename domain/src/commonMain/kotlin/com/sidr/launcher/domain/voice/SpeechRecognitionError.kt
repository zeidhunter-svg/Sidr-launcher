package com.sidr.launcher.domain.voice

/**
 * Pure, framework-neutral speech-recognition failure (Phase 7, Block S).
 *
 * The Android `SpeechRecognizer` error-code mapping (`ERROR_NO_MATCH`, `ERROR_RECOGNIZER_BUSY`,
 * `ERROR_INSUFFICIENT_PERMISSIONS`, `ERROR_NETWORK`, …) lives in the `:core:android` impl (Block T) —
 * the domain only sees this taxonomy. Enum (not a payload-bearing sealed type) like `ModelAvailability`
 * / `AiStopReason`.
 */
enum class SpeechRecognitionError {
    /** No recognizer or no usable on-device pack (also the `isAvailable() == false` reason). */
    UNAVAILABLE,

    /** Microphone permission (`RECORD_AUDIO`) not granted. */
    PERMISSION_DENIED,

    /** Recognition completed but matched no speech. */
    NO_MATCH,

    /** The recognizer is already busy with another request. */
    BUSY,

    /** A network error in a recognizer that needed the network (on-device avoids this). */
    NETWORK,

    /** No speech / response within the recognizer's timeout. */
    TIMEOUT,

    /** Any error not mapped above. */
    UNKNOWN,
}
