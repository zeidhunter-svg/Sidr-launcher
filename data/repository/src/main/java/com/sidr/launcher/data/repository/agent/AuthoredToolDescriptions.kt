package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.ai.bridge.ModelArg
import com.sidr.launcher.domain.ai.bridge.ToolDescription
import com.sidr.launcher.domain.ai.bridge.ToolDescriptions
import com.sidr.launcher.domain.ai.bridge.ToolNamespace
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import javax.inject.Inject

/**
 * Our vocabulary for the model (spec §7.12.1, D1): one row per authored tool the bridge may offer,
 * written by us, in English — never a third party's text. The model reads these words and nothing of
 * this phone.
 *
 * **An app argument is a mention, not a package.** `uninstall_app` declares `app` as "Package of the app
 * to remove"; the model must never write a package, so every app-sort tool is described with one
 * argument, [MENTION_ARG], "the app's usual name" (the A4′ tail's fix round, task 7, owner ruling 4: а, part ii —
 * before it "the app, in the user's own words", which made the model echo «плюс», «cloude», «клод»). The mention is
 * of `ArgSorts.APP`, so it is marked «app» and the instruction asks for the app's usual name as published;
 * `BridgeValidator` takes the user's words as theirs and (amendment D1) admits a name the model corrected as the
 * model's, and `DeviceMentionBinder` turns either into a package, a corrected name only by an exact label or alias.
 * Nothing about the phone is in this text. Every other argument keeps the tool's own name and requiredness
 * (`AuthoredToolDescriptionsTest`).
 *
 * The three `launcher_memory` tools have no row: they are never offered (spec §7.5), and a missing
 * description is a second reason they could not be (`BridgeFilter` offers only described tools).
 *
 * Two constructors, the [CatalogArgumentSorts] shape: production takes no argument, a test may state rows.
 */
class AuthoredToolDescriptions internal constructor(
    private val rows: Map<ToolId, ToolDescription>,
) : ToolDescriptions {

    @Inject constructor() : this(ROWS)

    override fun describe(id: ToolId): ToolDescription? = rows[id]

    fun rows(): Map<ToolId, ToolDescription> = rows

    companion object {
        /** The model-facing name of every app mention; the binder reads exactly this name. */
        const val MENTION_ARG = "app"

        private const val THE_APP = "the app's usual name"

        // Of the app sort (amendment D1): `BridgeValidator` admits a name the model corrected only for such a mention,
        // and `DeviceMentionBinder` binds that name to an exact label or alias only.
        private fun mention() = ModelArg(MENTION_ARG, THE_APP, required = true, mention = true, sort = ArgSorts.APP)

        private fun text(name: String, description: String) =
            ModelArg(name, description, required = true, mention = false)

        private fun device(text: String, vararg args: ModelArg) =
            ToolDescription(ToolNamespace.DEVICE, text, args.toList())

        private fun content(text: String, vararg args: ModelArg) =
            ToolDescription(ToolNamespace.CONTENT, text, args.toList())

        private val ROWS: Map<ToolId, ToolDescription> = mapOf(
            // Launcher — what Sidr itself does. Sidr's own screens are `navigate` answers, not tools.
            ToolIds.LAUNCH_APP to ToolDescription(ToolNamespace.LAUNCHER, "Open an app installed on this phone.", listOf(mention())),
            // Device — this phone's own functions. "Settings" here are the phone's.
            Tier0ToolIds.UNINSTALL_APP to device("Remove an app from this phone. The user is always asked first.", mention()),
            Tier0ToolIds.OPEN_APP_INFO to device("Show the system information screen of an installed app.", mention()),
            Tier0ToolIds.SET_TIMER to device("Start a countdown timer.", text("duration", "a number and a unit, e.g. 30 minutes")),
            Tier0ToolIds.SET_ALARM to device("Set an alarm.", text("time", "the clock time, e.g. 7:30")),
            Tier0ToolIds.SHOW_ALARMS to device("Show the list of alarms."),
            Tier0ToolIds.OPEN_CAMERA to device("Open the camera."),
            Tier0ToolIds.OPEN_SYSTEM_SETTINGS to device("Open the phone's system settings (not Sidr's own settings)."),
            Tier0ToolIds.OPEN_WIFI_SETTINGS to device("Open the Wi-Fi settings."),
            Tier0ToolIds.OPEN_BLUETOOTH_SETTINGS to device("Open the Bluetooth settings."),
            Tier0ToolIds.OPEN_BATTERY_SETTINGS to device("Open the battery settings."),
            Tier0ToolIds.OPEN_DATA_USAGE_SETTINGS to device("Open the mobile data settings."),
            Tier0ToolIds.OPEN_DISPLAY_SETTINGS to device("Open the display settings."),
            Tier0ToolIds.OPEN_SOUND_SETTINGS to device("Open the sound settings."),
            Tier0ToolIds.OPEN_LOCATION_SETTINGS to device("Open the location settings."),
            Tier0ToolIds.OPEN_NOTIFICATION_SETTINGS to device("Open the notification settings."),
            // Content — the web and the app store.
            ToolIds.PLAY_STORE_SEARCH to content("Look up an app in the Play Store, to install it.", text("query", "the app's name to look for")),
            InAppToolIds.WEB_SEARCH to content("Search the web.", text("query", "what to search for")),
            InAppToolIds.OPEN_URL to content("Open a web address in the browser.", text("url", "the full address, starting with https://")),
        )
    }
}
