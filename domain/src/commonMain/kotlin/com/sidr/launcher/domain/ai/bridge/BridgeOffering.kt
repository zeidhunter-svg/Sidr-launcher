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

    /**
     * Every output name the offered tools describe, distinct and sorted (A4′ phase 3b, Task 8) — what a
     * reference's `key` may say. Empty means references do not exist for this offering: the prompt never
     * mentions them and the schema has no reference form (B-8; empty on Android).
     */
    fun outputNames(): List<String> = tools.flatMap { it.description.outputs }.map { it.name }.distinct().sorted()
}

object BridgeFilter {

    /**
     * **Android's** allow-list (owner fork F3a-A, allow; since phase 3b, B-12, one consumer's set among
     * others — `:consumer:jvm` passes its sandbox level). A predicate on the level every tool declares
     * about itself — not a list of ids (spec §7.6, criterion 8) — and an ALLOW-list, so a level nobody
     * decided about (AppFunctions, MCP — third-party names, like `app_shortcut`) is not offered by default.
     * `app_shortcut` is out under D1 (the model never sees the user's world); `launcher_memory` is out
     * under §7.5 (`B13`).
     *
     * **The obligation of spec §7.9, restated where it bites:** the day inference becomes local, whoever
     * does it re-checks whether the egress guard can still fail, and widens or keeps this set by a
     * written decision.
     */
    val ANDROID_OFFERED_LEVELS: Set<ToolLevel> = setOf(ToolLevels.IN_APP, ToolLevels.SYSTEM_INTENT)

    /**
     * The tools of [registry] at one of [levels] that [descriptions] describe. [levels] are the CONSUMER's
     * allow-list (phase 3b, B-12) — Android passes [ANDROID_OFFERED_LEVELS], `:consumer:jvm` its sandbox
     * level — with no default, so no consumer is offered another's tools by forgetting to choose.
     */
    fun offeringOf(registry: ToolRegistry, levels: Set<ToolLevel>, descriptions: ToolDescriptions): BridgeOffering =
        BridgeOffering(
            registry.all()
                .filter { it.level in levels }
                .mapNotNull { descriptor ->
                    descriptions.describe(descriptor.id)?.let { OfferedTool(descriptor, it) }
                },
        )
}
