package com.sidr.launcher.domain.intent

/**
 * A technical failure named, not worded (I18N-1 spec §3.5). Display-safe by construction: a failure
 * carries no message, so no stack trace or PII can ride along in one.
 */
sealed interface CommandFailure {
    /** Was `SAFE_FAILURE_MESSAGE` - "Something went wrong. Please try again." */
    data object Generic : CommandFailure
    /** Was `AndroidActionExecutor.CANT_OPEN_APP`. */
    data object CantOpenApp : CommandFailure
    /** Was `NO_SEARCH_APP`. */
    data object NoSearchApp : CommandFailure
    /** Was `CANT_OPEN_URL`. */
    data object CantOpenUrl : CommandFailure
    /** Was `NO_STORE_APP`. */
    data object NoStoreApp : CommandFailure
}
