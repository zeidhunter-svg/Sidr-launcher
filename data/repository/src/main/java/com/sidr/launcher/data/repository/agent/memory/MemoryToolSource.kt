package com.sidr.launcher.data.repository.agent.memory

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * A1″ Phase 3a's fourth adapter: the agent's own memory, acted on by explicit command. Three thin
 * adapters over contracts that already ship — `SaveAliasUseCase`, `DeleteAliasUseCase`
 * (`domain/memory/alias`) and `DeleteLearnedChoiceUseCase` (`domain/memory/resolution`) — spec §7.6.
 *
 * **Like [com.sidr.launcher.data.repository.agent.SystemIntentToolSource], not like
 * [com.sidr.launcher.data.repository.agent.Tier0IntentToolSource]: this source consults no
 * [com.sidr.launcher.data.repository.agent.ToolPermissionCatalog] and filters nothing.** These three
 * tools need no Android permission — they write to the launcher's own store — so there is no presence
 * to gate on. `ToolPermissionCatalog`'s own KDoc names this source by name as one that does not consult
 * it; the catalog still carries `emptyList()` rows for these three ids, because
 * `ToolRegistryPermissionGuardTest`'s totality check quantifies over the whole federation, not over
 * which sources happen to filter.
 *
 * **`TRANSIENT`, never `DURABLE` (owner decision 2026-09-18, review finding C1).** `DURABLE` is a
 * consent trigger in `AgentExecutor.checkpointFor`'s fourth branch, and these are `SAFE` tools: marking
 * one `DURABLE` would stop the loop for consent over an operation that hands nothing outside the
 * launcher and is undone in Settings in two taps — not the "irreversible footprint" `ToolDurability`'s
 * own KDoc defines `DURABLE` to mean.
 *
 * **`app`/`phrase`/`app_label` on `set_app_alias` are all declared, because an undeclared argument is
 * dropped silently rather than rejected** (review finding I4): `ToolMatchPlanner` drops any argument its
 * schema does not name, so a missing `app_label` entry would never surface a symptom — the alias would
 * simply save without it. `app_label` is `required = false` for the same reason
 * [com.sidr.launcher.data.repository.agent.Tier0IntentToolSource]'s `uninstall_app` declares its own
 * label argument optional: the vocabulary never supplies it directly, a later step in the planner binds
 * it from the raw text, and a required declaration would answer `NoPlan` forever.
 */
class MemoryToolSource @Inject constructor() : ToolRegistry {

    private val descriptors = listOf(
        ToolDescriptor(
            id = MemoryToolIds.SET_APP_ALIAS,
            argSchema = listOf(
                ActionArg("app", description = "The resolved package the alias should point at"),
                ActionArg("phrase", description = "The nickname to save"),
                ActionArg("app_label", required = false, description = "What the user called the app"),
            ),
            level = ToolLevels.LAUNCHER_MEMORY,
            effect = ToolEffect.LOCAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = MemoryToolIds.FORGET_APP_ALIAS,
            argSchema = listOf(
                ActionArg("phrase", description = "The alias phrase to remove"),
            ),
            level = ToolLevels.LAUNCHER_MEMORY,
            effect = ToolEffect.LOCAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
        ToolDescriptor(
            id = MemoryToolIds.FORGET_LEARNED_CHOICE,
            argSchema = listOf(
                ActionArg("phrase", description = "The learned launch_app phrase to forget"),
            ),
            level = ToolLevels.LAUNCHER_MEMORY,
            effect = ToolEffect.LOCAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
        ),
    )

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }
}
