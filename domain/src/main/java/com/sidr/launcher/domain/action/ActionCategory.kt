package com.sidr.launcher.domain.action

/**
 * Coarse grouping for a registered action, used for catalog presentation/grouping and to give the
 * AIL-4 router a hint about the kind of target. Kept small and total; extend deliberately.
 */
enum class ActionCategory {
    /** Launch or act on an installed app. */
    APP,

    /** Web search or opening a URL/site. */
    WEB,

    /** The device / launcher itself (settings, app grid). */
    SYSTEM,

    /** Route to the conversational assistant. */
    ASSISTANT,

    /** App store (Play Store) targets. */
    STORE,
}
