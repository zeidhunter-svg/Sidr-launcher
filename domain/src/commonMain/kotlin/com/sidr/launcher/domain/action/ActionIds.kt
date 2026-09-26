package com.sidr.launcher.domain.action

/**
 * The canonical [ActionId] for every registered action family — the single source of truth for the
 * wire identity shared by [LauncherAction] variants and (AIL-4) the LLM router's `action` id.
 *
 * The five [LAUNCH_APP], [WEB_SEARCH], [OPEN_SETTINGS], [OPEN_ASSISTANT], [SHOW_APPS] families map
 * onto capabilities the launcher already ships (the rule → resolver → executor path). [OPEN_URL] and
 * [PLAY_STORE_SEARCH] are the two new families introduced by AIL-2; their [LauncherAction] variants
 * exist now so the vocabulary is complete, but their executor + rule recognition land in AIL-2.
 *
 * Values are lowercase snake_case and MUST stay stable — they are a persisted/wire contract.
 */
object ActionIds {
    val LAUNCH_APP = ActionId("launch_app")
    val WEB_SEARCH = ActionId("web_search")
    val OPEN_SETTINGS = ActionId("open_settings")
    val OPEN_ASSISTANT = ActionId("open_assistant")
    val SHOW_APPS = ActionId("show_apps")

    // AIL-2 families — vocabulary registered now, execution wired in AIL-2.
    val OPEN_URL = ActionId("open_url")
    val PLAY_STORE_SEARCH = ActionId("play_store_search")

    /** Every registered family id, in a stable order. Useful for exhaustiveness tests + rendering. */
    val ALL: List<ActionId> = listOf(
        LAUNCH_APP,
        WEB_SEARCH,
        OPEN_SETTINGS,
        OPEN_ASSISTANT,
        SHOW_APPS,
        OPEN_URL,
        PLAY_STORE_SEARCH,
    )
}
