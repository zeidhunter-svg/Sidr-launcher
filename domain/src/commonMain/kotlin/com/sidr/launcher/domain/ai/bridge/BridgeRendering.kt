package com.sidr.launcher.domain.ai.bridge

/**
 * Everything the bridge says to the model, from ONE offering (spec §7.12.2): the instruction, the
 * offering text, the JSON schema, and the four retry notes. What leaves the device is exactly the
 * user's command, this text, and the generation limits (`OutboundContextPolicy.AllowedContext`
 * `USER_COMMAND`, `STATIC_SYSTEM_PROMPT`, `AUTHORED_TOOL_SCHEMA`, `GENERATION_LIMITS` — the last two
 * from Task 11; until then this object has no caller in production).
 *
 * **b′ (owner decision D2):** the answer form is strict-compatible — every field required, `null`
 * instead of "optional", nothing extra — whether or not the transport sends the schema. The schema is
 * written here as text because `:domain` carries no serialization library; its shape is fixed and
 * ASCII-only (kinds, destinations, tool ids, argument names), and `BridgeRenderingTest` parses it.
 */
object BridgeRendering {

    const val KIND_PLAN = "plan"
    const val KIND_ASSISTANT = "assistant"
    const val KIND_NAVIGATE = "navigate"
    const val KIND_CLARIFY = "clarify"
    const val KIND_DECLINE = "decline"
    val KINDS: List<String> = listOf(KIND_PLAN, KIND_ASSISTANT, KIND_NAVIGATE, KIND_CLARIFY, KIND_DECLINE)

    /**
     * Static and context-free; the only bridge text scanned against `FORBIDDEN_CONTEXT_TERMS` (with the
     * retry notes). One step per plan in 3a (plan §0.2 B-1). 1 140 characters — `ToolSchemaWeightTest`.
     */
    const val INSTRUCTION: String =
        "You read one request typed into Sidr, an Android home screen, and say what it asks for. You see " +
            "only the request and the tools listed below; you know nothing else about this phone. Reply with " +
            "ONLY one JSON object, without prose or code fences, of exactly this form:\n" +
            "{\"kind\": K, \"step\": S, \"question\": Q, \"destination\": D}\n" +
            "\"plan\": the request is one action that one listed tool performs. step = {\"tool\": " +
            "\"<tool id>\", \"args\": {...}}; question and destination are null.\n" +
            "\"assistant\": the request is a question, or a wish to talk. question = the question; step and " +
            "destination are null.\n" +
            "\"navigate\": the request is to open a screen of Sidr itself. destination = " +
            "\"launcher_settings\" (Sidr's own settings) or \"app_grid\" (all apps); step and question are " +
            "null.\n" +
            "\"clarify\": you must ask the user one short question first. question = that question; step and " +
            "destination are null.\n" +
            "\"decline\": nothing listed fits. step, question and destination are null.\n" +
            "A plan has exactly one step. An argument marked \"app\" must repeat the user's own words for the " +
            "app exactly as they typed them, never a package name. Never invent a tool, an argument or a " +
            "destination."

    /** The whole system text: the instruction, then — when anything is offered — the offering. */
    fun systemText(offering: BridgeOffering): String {
        val listed = offeringText(offering)
        return if (listed.isEmpty()) INSTRUCTION else "$INSTRUCTION\n\n$listed"
    }

    /** The offering in our words, grouped by namespace; the ONLY offering-derived part of the prompt. */
    fun offeringText(offering: BridgeOffering): String {
        val tools = ordered(offering)
        if (tools.isEmpty()) return ""
        val names = argNamesOf(offering)
        return buildString {
            append("Every argument name: ")
            append(if (names.isEmpty()) "(none)" else names.joinToString(", "))
            append(". Give \"args\" every one of these names: the value for each argument the chosen tool takes, null for the rest.")
            ToolNamespace.entries.forEach { namespace ->
                val inNamespace = tools.filter { it.description.namespace == namespace }
                if (inNamespace.isEmpty()) return@forEach
                append("\n\n")
                append(headerOf(namespace))
                inNamespace.forEach { tool ->
                    append("\n- ")
                    append(tool.descriptor.id.value)
                    append(": ")
                    append(tool.description.text)
                    if (tool.description.args.isNotEmpty()) {
                        append(" args: ")
                        append(
                            tool.description.args.joinToString(", ") { arg ->
                                "${arg.name} (${markersOf(arg)}) — ${arg.description}"
                            },
                        )
                    }
                }
            }
        }
    }

    /** The JSON schema of the answer, from the same offering and in the same order as [offeringText]. */
    fun schemaJson(offering: BridgeOffering): String {
        val names = argNamesOf(offering)
        val nullableString = JsonText.obj("type" to JsonText.arr(listOf(JsonText.str("string"), JsonText.str("null"))))
        val args = JsonText.obj(
            "type" to JsonText.str("object"),
            "additionalProperties" to "false",
            "required" to JsonText.arr(names.map(JsonText::str)),
            "properties" to JsonText.obj(*names.map { it to nullableString }.toTypedArray()),
        )
        val step = JsonText.obj(
            "type" to JsonText.arr(listOf(JsonText.str("object"), JsonText.str("null"))),
            "additionalProperties" to "false",
            "required" to JsonText.arr(listOf(JsonText.str("tool"), JsonText.str("args"))),
            "properties" to JsonText.obj(
                "tool" to JsonText.obj(
                    "type" to JsonText.str("string"),
                    "enum" to JsonText.arr(ordered(offering).map { JsonText.str(it.descriptor.id.value) }),
                ),
                "args" to args,
            ),
        )
        return JsonText.obj(
            "type" to JsonText.str("object"),
            "additionalProperties" to "false",
            "required" to JsonText.arr(listOf("kind", "step", "question", "destination").map(JsonText::str)),
            "properties" to JsonText.obj(
                "kind" to JsonText.obj("type" to JsonText.str("string"), "enum" to JsonText.arr(KINDS.map(JsonText::str))),
                "step" to step,
                "question" to nullableString,
                "destination" to JsonText.obj(
                    "type" to JsonText.arr(listOf(JsonText.str("string"), JsonText.str("null"))),
                    "enum" to JsonText.arr(SidrDestination.entries.map { JsonText.str(it.wire) } + "null"),
                ),
            ),
        )
    }

    /** The static note a retry adds — the code and nothing else (spec §7.12.2). */
    fun retryNote(error: BridgeError): String = when (error) {
        BridgeError.SHAPE ->
            "Your previous reply was rejected: it was not one JSON object of the required form. Reply again with only that object."
        // ONE text for both: the validator tells them apart for the device, but a note that differed
        // would tell the model whether an id it proposed exists on this phone (spec §7.12.1 D1).
        BridgeError.UNKNOWN_TOOL, BridgeError.NOT_OFFERED ->
            "Your previous reply was rejected: it named a tool that is not among the tool ids listed above. Use only those ids."
        BridgeError.UNDERIVABLE_MENTION ->
            "Your previous reply was rejected: an argument marked \"app\" held words the user did not type. Copy the user's own words for the app."
    }

    private fun ordered(offering: BridgeOffering): List<OfferedTool> =
        ToolNamespace.entries.flatMap { namespace -> offering.tools.filter { it.description.namespace == namespace } }

    private fun argNamesOf(offering: BridgeOffering): List<String> =
        offering.tools.flatMap { it.description.args }.map { it.name }.distinct().sorted()

    private fun headerOf(namespace: ToolNamespace): String = when (namespace) {
        ToolNamespace.LAUNCHER -> "Launcher (Sidr itself):"
        ToolNamespace.DEVICE -> "Device (this phone's own functions):"
        ToolNamespace.CONTENT -> "Content (the web and the app store):"
    }

    private fun markersOf(arg: ModelArg): String =
        listOfNotNull(if (arg.mention) "app" else null, if (arg.required) "required" else "optional").joinToString(", ")
}

/** A minimal JSON writer for the schema's fixed shape — values passed in are already JSON text. */
internal object JsonText {
    fun str(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u" + c.code.toString(16).padStart(4, '0')) else append(c)
            }
        }
        append('"')
    }

    fun arr(items: List<String>): String = items.joinToString(",", "[", "]")

    fun obj(vararg pairs: Pair<String, String>): String =
        pairs.joinToString(",", "{", "}") { (key, value) -> "${str(key)}:$value" }
}
