package com.sidr.launcher.data.repository.agent

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.domain.tool.ToolRegistry
import javax.inject.Inject

/**
 * The ids of the two router actions projected as tools in A4′ phase 3a (spec §7.12.3). Identity C:
 * each id is DERIVED from its `ActionId` at this one expression, never copied into a second literal —
 * the seven `ActionIds` values stay frozen byte for byte (ADR 3/4), and these two are those values.
 * Public so `:app`'s guards can name them, as they name `Tier0ToolIds`.
 */
object InAppToolIds {
    val WEB_SEARCH: ToolId = ToolId(ActionIds.WEB_SEARCH.value)
    val OPEN_URL: ToolId = ToolId(ActionIds.OPEN_URL.value)
}

/**
 * A0's single tool source: a projection of the existing [ActionCatalog] into the two tools this slice
 * registers. The registry is a list of sources; A1' turns it into a federation by adding adapters
 * beside this one, or discards the projection and grows the catalog — **A0 decides neither**, and the
 * mapping lives here, in thirty lines of adapter, precisely so both remain cheap.
 *
 * Four of the seven families are projected since A4′ phase 3a. The argument that kept this at two —
 * "registering all seven would hand the planner capabilities this slice has neither gated, traced, nor
 * accepted on a device" — lapses for `web_search` and `open_url` only as its three parts are met (spec
 * §7.12.3): the gate is the engine's, with their risk pinned by `DoctrineGuardTest.declaredRisk`; the
 * trace is the engine's; the acceptance is the block's checklist. It still holds for `open_settings`,
 * `open_assistant` and `show_apps`, which are answers of the model, not tools.
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
            project(ActionIds.LAUNCH_APP, outputSchema = RESOLVED_QUERY_OUTPUT),
            project(ActionIds.PLAY_STORE_SEARCH),
            // A4′ phase 3a (spec §7.12.3, D3): the router's two other actions with an executor. The
            // three navigation families (settings, assistant, app grid) are NOT projected — they
            // change what is on screen, not the world, and stay variants of the model's answer.
            project(ActionIds.WEB_SEARCH),
            project(ActionIds.OPEN_URL),
        )
    }

    override fun all(): List<ToolDescriptor> = projected

    override fun find(id: ToolId): ToolDescriptor? = projected.firstOrNull { it.id == id }

    private fun project(
        actionId: ActionId,
        outputSchema: List<ActionArg> = emptyList(),
    ): ToolDescriptor? {
        val descriptor = catalog.descriptor(actionId) ?: return null
        return ToolDescriptor(
            // Identity C: the tool IS the action, so its id is the action's id rather than a second
            // spelling of it kept in step by a test. Tools that are not projections mint their own.
            id = ToolId(actionId.value),
            level = ToolLevels.IN_APP,
            // Both projected families reach another app. No projected tool is LOCAL today; the LOCAL
            // value is carried by `:consumer:jvm`'s sandbox. Named, not implied absent (spec §6.4).
            effect = ToolEffect.EXTERNAL,
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
         * The one spelling of `launch_app`'s output key. [SystemIntentToolWorker] emits under this
         * same constant, so the declaration and the value can never disagree on the name — only
         * `SystemIntentToolSourceTest` states the literal, and it does so as the wire contract.
         */
        internal const val RESOLVED_QUERY = "resolved_query"

        val RESOLVED_QUERY_OUTPUT = listOf(
            ActionArg(RESOLVED_QUERY, description = "The query this launch resolved against"),
        )
    }
}
