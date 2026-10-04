package com.sidr.launcher.domain.ai.bridge

/**
 * A screen of Sidr itself the model may name in a `navigate` answer (spec §7.12.3: navigation is an
 * answer, not a tool). [wire] is what the model writes; it appears in the instruction and the schema.
 * The assistant is not here: it is its own answer kind, carrying the question.
 */
enum class SidrDestination(val wire: String) {
    LAUNCHER_SETTINGS("launcher_settings"),
    APP_GRID("app_grid"),
}
