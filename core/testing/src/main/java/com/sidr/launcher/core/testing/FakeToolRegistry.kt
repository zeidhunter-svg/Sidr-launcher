package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry

/** In-memory registry for domain tests. [withA0Tools] mirrors what `SystemIntentToolSource` projects. */
class FakeToolRegistry(private val descriptors: List<ToolDescriptor>) : ToolRegistry {

    override fun all(): List<ToolDescriptor> = descriptors

    override fun find(id: ToolId): ToolDescriptor? = descriptors.firstOrNull { it.id == id }

    companion object {
        fun withA0Tools() = FakeToolRegistry(
            listOf(
                ToolDescriptor(
                    id = ToolIds.LAUNCH_APP,
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.EXTERNAL,
                    argSchema = listOf(ActionArg("query", description = "The app name to launch")),
                    // Declared on the tool, not on the branch: `launch_app` reports what it resolved
                    // against on every result, and step 1 of the A0 plan binds to it (F6).
                    outputSchema = listOf(
                        ActionArg("resolved_query", description = "The query this launch resolved against"),
                    ),
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                ),
                ToolDescriptor(
                    id = ToolIds.PLAY_STORE_SEARCH,
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.EXTERNAL,
                    argSchema = listOf(ActionArg("query", description = "The app name to find")),
                    risk = ActionRiskLevel.CONFIRM,
                    durability = ToolDurability.TRANSIENT,
                ),
            ),
        )
    }
}
