package com.sidr.launcher.data.repository.agent.shortcut

/**
 * One shortcut an installed app publishes, reduced to the four fields the tool layer needs.
 *
 * [appLabel] and [shortcutLabel] are **third-party copy**: they are authored by another app, in
 * whatever language that app chose, and they are neither translated nor sanitized by us. They stay in
 * `:data:repository` and reach the surface through `DynamicToolNames`, so no third-party string
 * crosses into `:domain` — see the block spec §5.2 for why `ToolDescriptor` deliberately did not gain
 * a `label` field.
 */
data class AppShortcut(
    val packageName: String,
    val shortcutId: String,
    val appLabel: String,
    val shortcutLabel: String,
)
