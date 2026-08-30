package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/** The two Tier-0 ids. Not projections of `ActionIds` — those seven are frozen and contain neither. */
object Tier0ToolIds {
    val SET_TIMER = ToolId("set_timer")
    val OPEN_SYSTEM_SETTINGS = ToolId("open_system_settings")
}

/**
 * A1′'s second source: Android system intents that are **not** among the frozen seven `ActionIds`, so
 * they mint their own ids (spec §5.1 — identity C binds projections only).
 *
 * Both tools are `EXTERNAL` and `SAFE`, and that combination is the one `DOC-ILM-2` is actually about:
 * a `CONFIRM` tool is already stopped by the consent gate, so provenance is the only mechanism telling
 * the user where a `SAFE` effect went. Neither skips the OS's own UI — the "prefilled but not sent"
 * shape leaves the final act with the user, which is what makes `SAFE` honest rather than convenient.
 *
 * Zero new permissions: `ACTION_SET_TIMER` and `ACTION_SETTINGS` both need none.
 */
class Tier0IntentToolSource @Inject constructor() : ToolRegistry {

    private val descriptors = listOf(
        ToolDescriptor(
            id = Tier0ToolIds.SET_TIMER,
            argSchema = listOf(
                ActionArg("duration", description = "How long the timer should run, e.g. 10 minutes"),
            ),
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = Tier0ToolIds.OPEN_SYSTEM_SETTINGS,
            level = ToolLevels.SYSTEM_INTENT,
            effect = ToolEffect.EXTERNAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
