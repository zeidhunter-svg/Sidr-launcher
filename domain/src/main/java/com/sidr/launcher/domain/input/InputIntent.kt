package com.sidr.launcher.domain.input

/**
 * What the live universal-input buffer affords (AIL-3). Purely a *presentation-routing* classification:
 * it never executes anything and never replaces the command pipeline. The ViewModel uses it to decide
 * which route chips to offer; app-match filtering stays in the ViewModel (it needs the installed-app
 * list). Enter/submit still goes through the unchanged [com.sidr.launcher.domain.intent.HandleUserCommandUseCase].
 */
sealed interface InputIntent {

    /** Blank buffer — show the home body (favorites / suggestions / all apps). */
    data object Empty : InputIntent

    /** The literal `//dev-mode` toggle string (developer Command-console unlock). Inert unless armed. */
    data object DevSentinel : InputIntent

    /**
     * Normal typing. [raw] is the trimmed original text (used verbatim for web search / assistant
     * prefill / submit). [siteUrl] is non-null only when the buffer is a high-confidence, safe openable
     * `http(s)` URL — then the UI may offer an "open site" chip targeting it.
     */
    data class Query(val raw: String, val siteUrl: String?) : InputIntent
}
