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
 * Nothing consumes the catalog on the runtime path yet: the AIL-4 LLM router renders these descriptors
 * to the model and validates its proposed args against [ActionDescriptor.argSchema], and the AIL-5
 * confirmation UI reads [ActionDescriptor.risk]. Registering it now keeps the metadata co-located with
 * the executors + rule recognition AIL-2 adds, and lets the Hilt graph bind [ActionCatalog].
 *
 * Risk model (MVP): opening an arbitrary URL and the Play Store are [ActionRiskLevel.CONFIRM] (their
 * confirmation gating lands in AIL-5); the app/settings/assistant/search families are
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
