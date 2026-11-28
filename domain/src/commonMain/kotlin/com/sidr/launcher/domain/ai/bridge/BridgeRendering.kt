package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.sort.ArgSorts

/**
 * Everything the bridge says to the model, from ONE offering (spec §7.12.2): the instruction, the
 * offering text (with the reference sentence when the offering describes an output, B-8), the JSON
 * schema, and the four retry notes. What leaves the device is exactly the
 * user's command, this text, and the generation limits (`OutboundContextPolicy.AllowedContext`
 * `USER_COMMAND`, `STATIC_SYSTEM_PROMPT`, `AUTHORED_TOOL_SCHEMA`, `GENERATION_LIMITS` — the last two
 * since Task 11). Its production callers are `BridgeModelPlanner` (the request and the retry note) and
 * `BridgeValidator` (the answer kinds and the step cap).
 *
 * **b′ (owner decision D2):** the answer form is strict-compatible — every field required, `null`
 * instead of "optional", nothing extra — whether or not the transport sends the schema. The schema is
 * written here as text because `:domain` carries no serialization library; its shape is fixed and
 * ASCII-only (kinds, destinations, tool ids, argument and output names), and `BridgeRenderingTest` parses it.
 */
object BridgeRendering {

    const val KIND_PLAN = "plan"
    const val KIND_ASSISTANT = "assistant"
    const val KIND_NAVIGATE = "navigate"
    const val KIND_CLARIFY = "clarify"
    const val KIND_DECLINE = "decline"
    val KINDS: List<String> = listOf(KIND_PLAN, KIND_ASSISTANT, KIND_NAVIGATE, KIND_CLARIFY, KIND_DECLINE)

    /**
     * How many steps a plan may have, as the instruction and the schema say it and the validator holds it
     * (fork 3b-B): the ENGINE's own cap, [RuntimeBudget.Default]'s `maxSteps`, since phase 3b Task 9 (one in
     * Task 8's interim). Not `const`, since it reads the engine's value; [INSTRUCTION] is not `const`
     * either, for that reason alone.
     */
    val MAX_STEPS_OFFERED: Int = RuntimeBudget.Default.maxSteps

    /**
     * Static and context-free; scanned against `FORBIDDEN_CONTEXT_TERMS` with [REFERENCE_SENTENCE] and the
     * retry notes. The answer form is `steps` since phase 3b Task 8 (fork 3b-B), from one to
     * [MAX_STEPS_OFFERED], each step `{"tool", "args"}` in an array (`steps = [{…}]`, controller ruling
     * E8-I1: under `PROMPT_ONLY` the model sees no schema); it never mentions references (B-8 — those are
     * the offering's). 1 426 characters since the tail's fix round, task 7 — `ToolSchemaWeightTest` (the cap's
     * «1» → «8» in Task 9 is one character for one; since Task 9's fix round 1 the plan line defines a plan as one or more actions,
     * in order, one step per action — it no longer contradicts the cap sentence, finding I2). Since the A4′
     * acceptance tail, task 2 (amendment D1, controller ruling T2-R1; 1 187 → 1 292), the app sentence let the
     * model write the usual name of an app the user misspelt or transliterated. Since the tail's fix round, task 7
     * (owner ruling 4: а, part ii; 1 292 → 1 426 characters) it is two sentences, one per marker ([markersOf]): an
     * argument marked [MARK_APP] is the app's usual name as published, correcting the user's spelling,
     * transliteration or translation (the user's words only when the model knows no such app) — the validator admits
     * it as the model's (`ArgSorts.APP` mentions only) and the device binds it to an exact label or alias only, never
     * a package; an argument marked [MARK_WORDS] repeats the user's own words. A line after the `clarify` line asks
     * for any question in the language of the request (the model asked a Russian request's question in English).
     * Nothing about the phone is in it. `:consumer:jvm` sends this text too: its file mentions carry no app sort, so
     * they are marked [MARK_WORDS], a «corrected» file name is still refused (`UNDERIVABLE_MENTION`) and retried
     * once with the note that names the same marker — no binding follows from it.
     */
    val INSTRUCTION: String =
        "You read one request typed into Sidr, an Android home screen, and say what it asks for. You see " +
            "only the request and the tools listed below; you know nothing else about this phone. Reply with " +
            "ONLY one JSON object, without prose or code fences, of exactly this form:\n" +
            "{\"kind\": K, \"steps\": S, \"question\": Q, \"destination\": D}\n" +
            "\"plan\": the request is one or more actions of listed tools, in order, one step per action. steps = [{\"tool\": " +
            "\"<tool id>\", \"args\": {...}}]; question and destination are null.\n" +
            "\"assistant\": the request is a question, or a wish to talk. question = the question; steps and " +
            "destination are null.\n" +
            "\"navigate\": the request is to open a screen of Sidr itself. destination = " +
            "\"launcher_settings\" (Sidr's own settings) or \"app_grid\" (all apps); steps and question are " +
            "null.\n" +
            "\"clarify\": you must ask the user one short question first. question = that question; steps and " +
            "destination are null.\n" +
            "Write any question in the language of the request.\n" +
            "\"decline\": nothing listed fits. steps, question and destination are null.\n" +
            "A plan has from one to $MAX_STEPS_OFFERED steps, in order. An argument marked \"$MARK_APP\" names an app: " +
            "write the app's usual name as it is published, correcting the user's spelling, transliteration or " +
            "translation; if you know no such app, repeat the user's words. An argument marked \"$MARK_WORDS\" must " +
            "repeat the user's own words as they typed them. Never write a package name. Never invent a tool, " +
            "an argument or a destination."

    /**
     * The two mention markers the offering renders and the instruction keys on ([markersOf]): a mention of
     * `ArgSorts.APP` is [MARK_APP], any other mention (`:consumer:jvm`'s file names) [MARK_WORDS].
     */
    const val MARK_APP = "app"
    const val MARK_WORDS = "words"

    /**
     * What a reference looks like (phase 3b, B-8, B-18). It lives in the OFFERING text, right after the
     * argument names, and only when the offering describes an output ([BridgeOffering.outputNames]) — so the
     * Android prompt, which describes none, never mentions references. Static, and scanned with the
     * instruction (`BridgeRenderingTest`).
     */
    const val REFERENCE_SENTENCE: String =
        "An argument marked \"ref\" may hold, instead of words, an output of an earlier step: " +
            "{\"from_step\": <that step's position, counting from 0>, \"key\": \"<an output that step's tool lists>\"}."

    /** The reference object's two fields, as [REFERENCE_SENTENCE] and the schema name them and the transport reads them. */
    const val REF_FROM_STEP = "from_step"
    const val REF_KEY = "key"

    /** The whole system text: the instruction, then — when anything is offered — the offering. */
    fun systemText(offering: BridgeOffering): String {
        val listed = offeringText(offering)
        return if (listed.isEmpty()) INSTRUCTION else "$INSTRUCTION\n\n$listed"
    }

    /**
     * The offering in our words, grouped by namespace; the ONLY offering-derived part of the prompt. When an
     * offered tool describes an output (B-8), and only then, the text also carries [REFERENCE_SENTENCE], each
     * such tool's `outputs`, and the `ref` marker on each argument that accepts a reference.
     */
    fun offeringText(offering: BridgeOffering): String {
        val tools = ordered(offering)
        if (tools.isEmpty()) return ""
        val names = argNamesOf(offering)
        val references = offering.outputNames().isNotEmpty()
        return buildString {
            append("Every argument name: ")
            append(if (names.isEmpty()) "(none)" else names.joinToString(", "))
            append(". Give \"args\" every one of these names: the value for each argument the chosen tool takes, null for the rest.")
            if (references) {
                append(' ')
                append(REFERENCE_SENTENCE)
            }
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
                                "${arg.name} (${markersOf(arg, references)}) — ${arg.description}"
                            },
                        )
                    }
                    if (tool.description.outputs.isNotEmpty()) {
                        append(" outputs: ")
                        append(tool.description.outputs.joinToString(", ") { output -> "${output.name} — ${output.description}" })
                    }
                }
            }
        }
    }

    /**
     * The JSON schema of the answer, from the same offering and in the same order as [offeringText].
     * `steps` is an array of one to [MAX_STEPS_OFFERED] steps. An argument takes the reference form — words,
     * `null`, or `{"from_step": integer, "key": one of the offering's output names}` — when some offered
     * tool's argument of that name accepts a reference AND the offering describes an output (controller
     * ruling E8-P3); otherwise it is `string | null`, so an offering without outputs renders its `args`
     * exactly as in 3a. Which tool's argument accepts a reference is the validator's to judge, per tool.
     */
    fun schemaJson(offering: BridgeOffering): String {
        val names = argNamesOf(offering)
        val nullableString = JsonText.obj("type" to JsonText.arr(listOf(JsonText.str("string"), JsonText.str("null"))))
        val outputNames = offering.outputNames()
        val referable = offering.tools.flatMap { it.description.args }.filter { it.acceptsRef }.map { it.name }.toSet()
        val wordsOrReference = JsonText.obj(
            "anyOf" to JsonText.arr(
                listOf(
                    JsonText.obj("type" to JsonText.str("string")),
                    JsonText.obj("type" to JsonText.str("null")),
                    JsonText.obj(
                        "type" to JsonText.str("object"),
                        "additionalProperties" to "false",
                        "required" to JsonText.arr(listOf(JsonText.str(REF_FROM_STEP), JsonText.str(REF_KEY))),
                        "properties" to JsonText.obj(
                            REF_FROM_STEP to JsonText.obj("type" to JsonText.str("integer")),
                            REF_KEY to JsonText.obj("type" to JsonText.str("string"), "enum" to JsonText.arr(outputNames.map(JsonText::str))),
                        ),
                    ),
                ),
            ),
        )
        val args = JsonText.obj(
            "type" to JsonText.str("object"),
            "additionalProperties" to "false",
            "required" to JsonText.arr(names.map(JsonText::str)),
            "properties" to JsonText.obj(
                *names.map { name ->
                    name to if (outputNames.isNotEmpty() && name in referable) wordsOrReference else nullableString
                }.toTypedArray(),
            ),
        )
        val step = JsonText.obj(
            "type" to JsonText.str("object"),
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
            "required" to JsonText.arr(listOf("kind", "steps", "question", "destination").map(JsonText::str)),
            "properties" to JsonText.obj(
                "kind" to JsonText.obj("type" to JsonText.str("string"), "enum" to JsonText.arr(KINDS.map(JsonText::str))),
                "steps" to JsonText.obj(
                    "type" to JsonText.arr(listOf(JsonText.str("array"), JsonText.str("null"))),
                    "minItems" to "1",
                    "maxItems" to MAX_STEPS_OFFERED.toString(),
                    "items" to step,
                ),
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
            // `BridgeValidator` raises it only for a mention NOT of the app sort — one marked [MARK_WORDS].
            "Your previous reply was rejected: an argument marked \"$MARK_WORDS\" held words the user did not type. Copy the user's own words."
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

    /**
     * `ref` only where references exist for this offering (B-8): with no output, an `acceptsRef` is silent. A mention
     * is marked by its sort (the tail's fix round, task 7): [MARK_APP] for `ArgSorts.APP`, [MARK_WORDS] for any other.
     */
    private fun markersOf(arg: ModelArg, references: Boolean): String =
        listOfNotNull(
            when {
                !arg.mention -> null
                arg.sort == ArgSorts.APP -> MARK_APP
                else -> MARK_WORDS
            },
            if (arg.required) "required" else "optional",
            if (references && arg.acceptsRef) "ref" else null,
        ).joinToString(", ")
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
