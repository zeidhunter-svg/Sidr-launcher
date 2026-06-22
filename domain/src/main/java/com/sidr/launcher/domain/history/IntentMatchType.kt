package com.sidr.launcher.domain.history

/**
 * Category of intent produced by the rule-based matcher — stored in [IntentMatchRecord].
 *
 * This is a coarse structural category, NOT a carrier of user query content. The data layer
 * is responsible for redacting [IntentMatchRecord.normalizedText] for [SEARCH] matches before
 * persisting (Fork 3 / Block F privacy constraint). The domain model itself is unaware of the
 * redaction policy.
 */
enum class IntentMatchType {
    LAUNCH_APP,
    SEARCH,
    OPEN_SETTINGS,
    SIMPLE_COMMAND,
    UNKNOWN,
}
