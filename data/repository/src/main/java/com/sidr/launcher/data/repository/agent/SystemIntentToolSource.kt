package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolIds
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * A0's single tool source: a projection of the existing [ActionCatalog] into the two tools this slice
 * registers. The registry is a list of sources; A1' turns it into a federation by adding adapters
 * beside this one, or discards the projection and grows the catalog — **A0 decides neither**, and the
 * mapping lives here, in thirty lines of adapter, precisely so both remain cheap.
 *
 * Only two of the seven families are projected. Registering all seven would hand the planner
 * capabilities this slice has neither gated, traced, nor accepted on a device.
 *
 * `launch_app` alone declares [RESOLVED_QUERY_OUTPUT] (F6, Task 5b): it is the only step-0 tool the A0
 * plan ever binds a later step to, and an output is a property of the tool, not of the catalog family
 * it was projected from — `ActionDescriptor` carries no such schema, so this is stated here rather than
 * carried over.
 */
class SystemIntentToolSource @Inject constructor(
    private val catalog: ActionCatalog,
) : ToolRegistry {

    private val projected: List<ToolDescriptor> by lazy {
        listOfNotNull(
            project(ActionIds.LAUNCH_APP, ToolIds.LAUNCH_APP, outputSchema = RESOLVED_QUERY_OUTPUT),
            project(ActionIds.PLAY_STORE_SEARCH, ToolIds.PLAY_STORE_SEARCH),
        )
    }

    override fun all(): List<ToolDescriptor> = projected

    override fun find(id: ToolId): ToolDescriptor? = projected.firstOrNull { it.id == id }

    private fun project(
        actionId: ActionId,
        toolId: ToolId,
        outputSchema: List<ActionArg> = emptyList(),
    ): ToolDescriptor? {
        val descriptor = catalog.descriptor(actionId) ?: return null
        return ToolDescriptor(
            id = toolId,
            argSchema = descriptor.argSchema,
            outputSchema = outputSchema,
            risk = descriptor.risk,
            // Neither A0 tool writes durable state: launching an app and opening a store page both
            // leave nothing to undo. When A1' registers a tool that does, it declares DURABLE and the
            // consent gate picks it up with no change to the engine.
            durability = ToolDurability.TRANSIENT,
            permissionGate = descriptor.permissionGate,
        )
    }

    internal companion object {
        /**
         * The one spelling of `launch_app`'s output key. [SystemIntentToolExecutor] emits under this
         * same constant, so the declaration and the value can never disagree on the name — only
         * `SystemIntentToolSourceTest` states the literal, and it does so as the wire contract.
         */
        internal const val RESOLVED_QUERY = "resolved_query"

        val RESOLVED_QUERY_OUTPUT = listOf(
            ActionArg(RESOLVED_QUERY, description = "The query this launch resolved against"),
        )
    }
}
