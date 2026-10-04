package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry

/** One tool the model is offered, with what we say about it. */
data class OfferedTool(val descriptor: ToolDescriptor, val description: ToolDescription)

/**
 * The filtered set the prompt, the schema and the validator all read (spec §7.12.2: membership is
 * checked against THIS set, not the federation — otherwise an invented or injected `set_app_alias` or
 * `shortcut:…` would pass). Computed per call: the federation's tool set moves while the process lives.
 */
data class BridgeOffering(val tools: List<OfferedTool>) {
    fun find(id: ToolId): OfferedTool? = tools.firstOrNull { it.descriptor.id == id }
}

object BridgeFilter {

    /**
     * Owner fork F3a-A (allow). A predicate on the level every tool declares about itself — not a list
     * of ids (spec §7.6, criterion 8) — and an ALLOW-list, so a level nobody decided about (AppFunctions,
     * MCP — third-party names, like `app_shortcut`) is not offered by default. `app_shortcut` is out
     * under D1 (the model never sees the user's world); `launcher_memory` is out under §7.5 (`B13`).
     *
     * **The obligation of spec §7.9, restated where it bites:** the day inference becomes local, whoever
     * does it re-checks whether the egress guard can still fail, and widens or keeps this set by a
     * written decision.
     */
    val OFFERED_LEVELS: Set<ToolLevel> = setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT)

    fun offeringOf(registry: ToolRegistry, descriptions: ToolDescriptions): BridgeOffering =
        BridgeOffering(
            registry.all()
                .filter { it.level in OFFERED_LEVELS }
                .mapNotNull { descriptor ->
                    descriptions.describe(descriptor.id)?.let { OfferedTool(descriptor, it) }
                },
        )
}
