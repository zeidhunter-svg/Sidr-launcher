package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionCatalog

/**
 * Renders the [ActionCatalog] into the **static, content-free** tool schema the LLM router (AIL-4)
 * receives alongside the user command — the ONLY other thing that leaves the device
 * (`OutboundContextPolicy.AllowedContext.ACTION_CATALOG_SCHEMA`). Pure stdlib; derives solely from the
 * registered [ActionDescriptor] metadata (id / description / arg names+descriptions) and the static
 * [ROUTER_INSTRUCTION] — never from device/usage/calendar/location/history/clipboard context. The
 * privacy guard scans this rendered output for forbidden terms.
 */
object CatalogSchemaRenderer {

    /**
     * Static routing instruction. Pins the strict portable-JSON reply contract (Fork R3): the model
     * must answer with ONE JSON object and nothing else, so the impl can strict-parse it and fail
     * closed to `NoPlan` on any deviation (prose, markdown, extra keys, non-tool-capable chatter).
     * Context-free and denylist-clean by construction.
     */
    const val ROUTER_INSTRUCTION: String =
        "You are the Sidr launcher command router. Map the user's request to exactly one registered " +
            "action. Reply with ONLY a single JSON object — no prose, no explanation, no markdown code " +
            "fences — in exactly this shape:\n" +
            "{\"action\":\"<action_id>\",\"args\":{...},\"confidence\":<0..1>,\"question\":\"<text>\"}\n" +
            "Use \"action\":\"none\" when no registered action fits the request. Use " +
            "\"action\":\"clarify\" with a \"question\" only when you must ask one short question to " +
            "proceed. Only use the action ids and argument names listed below; never invent an id or an " +
            "argument.\n\nRegistered actions:"

    /** Full prompt text: [ROUTER_INSTRUCTION] followed by one line per registered descriptor. */
    fun render(catalog: ActionCatalog): String = buildString {
        append(ROUTER_INSTRUCTION)
        catalog.all().forEach { descriptor ->
            append("\n- ")
            append(descriptor.id.value)
            append(": ")
            append(descriptor.description)
            if (descriptor.argSchema.isNotEmpty()) {
                append(" args: ")
                append(
                    descriptor.argSchema.joinToString(", ") { arg ->
                        val req = if (arg.required) "required" else "optional"
                        "${arg.name} ($req) — ${arg.description}"
                    },
                )
            }
        }
    }
}
