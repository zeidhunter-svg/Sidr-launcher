package com.sidr.launcher.data.repository.action

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.ArgType
import javax.inject.Inject

/**
 * The concrete [ActionCatalog] registered in AIL-2 — the descriptor metadata for all seven shipped
 * families (AIL-1 shipped the type vocabulary + a `FakeActionCatalog`; this registers the real one).
 *
 * Its consumers today, since A4′ phase 3a deleted the AIL-4 router and its AIL-5 confirm card:
 * `SystemIntentToolSource` projects four of the seven families (`launch_app`, `play_store_search`,
 * `web_search`, `open_url`) into `in_app` tools, carrying their arg schema, risk and permission gate onto
 * the tool's descriptor; the learned-choice decorator reads `launch_app`'s risk. The three navigation
 * families (settings, assistant, app grid) stay registered and are projected nowhere — the model's
 * navigation is an answer kind, not a tool (spec §7.12.3). Titles and descriptions reach no runtime path.
 *
 * Risk model (MVP): opening an arbitrary URL and the Play Store are [ActionRiskLevel.CONFIRM] — the
 * engine's consent gate stops their projected tools; the app/settings/assistant/search families are
 * [ActionRiskLevel.SAFE]. No family needs a runtime permission gate in the MVP.
 */
class DefaultActionCatalog @Inject constructor() : ActionCatalog {

    private val descriptors: List<ActionDescriptor> = listOf(
        ActionDescriptor(
            id = ActionIds.LAUNCH_APP,
            title = "Open app",
            description = "Launch an installed app by name",
            category = ActionCategory.APP,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("query", ArgType.STRING, required = true, description = "The app name to launch")),
        ),
        ActionDescriptor(
            id = ActionIds.WEB_SEARCH,
            title = "Web search",
            description = "Search the web with the configured search provider",
            category = ActionCategory.WEB,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("query", ArgType.STRING, required = true, description = "What to search for")),
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_SETTINGS,
            title = "Launcher settings",
            description = "Open the launcher's own settings",
            category = ActionCategory.SYSTEM,
            risk = ActionRiskLevel.SAFE,
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_ASSISTANT,
            title = "Assistant",
            description = "Open the AI assistant, optionally with a prompt",
            category = ActionCategory.ASSISTANT,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("prompt", ArgType.STRING, required = false, description = "Optional prompt to prefill")),
        ),
        ActionDescriptor(
            id = ActionIds.SHOW_APPS,
            title = "All apps",
            description = "Show the full app grid",
            category = ActionCategory.SYSTEM,
            risk = ActionRiskLevel.SAFE,
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_URL,
            title = "Open link",
            description = "Open a web address in the browser",
            category = ActionCategory.WEB,
            risk = ActionRiskLevel.CONFIRM,
            argSchema = listOf(ActionArg("url", ArgType.STRING, required = true, description = "The web address to open")),
        ),
        ActionDescriptor(
            id = ActionIds.PLAY_STORE_SEARCH,
            title = "Find in Play Store",
            description = "Search the Play Store for an app",
            category = ActionCategory.STORE,
            risk = ActionRiskLevel.CONFIRM,
            argSchema = listOf(ActionArg("query", ArgType.STRING, required = true, description = "The app name to find")),
        ),
    )

    override fun all(): List<ActionDescriptor> = descriptors

    override fun descriptor(id: ActionId): ActionDescriptor? = descriptors.firstOrNull { it.id == id }
}
