package com.sidr.launcher.domain.intent

/**
 * Why the model gave no usable answer — finding (c) of the A1″ acceptance: a wrong key, a wrong model,
 * a dead endpoint and "the model answered prose" used to fold into one silent "unknown command". Each
 * is now said as what it is; the feature layer picks the sentence.
 */
enum class ModelUnavailableReason { CREDENTIALS, REQUEST_REJECTED, PROVIDER_FAILING, TIMED_OUT, NETWORK, UNREADABLE }
