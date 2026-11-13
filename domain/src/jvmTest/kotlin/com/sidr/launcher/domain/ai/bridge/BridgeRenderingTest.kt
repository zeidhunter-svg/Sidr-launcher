package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.OutboundContextPolicy
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The text that leaves the device for the model (spec §7.12.2–§7.12.3). Two halves, held apart:
 *  - the STATIC text — the instruction, the reference sentence (phase 3b, B-8) and the four retry
 *    notes — is the only text scanned against the denylist: the ids of our own tools legitimately
 *    contain `usage` and `location`, and narrowing the denylist for them is forbidden (spec §7.12.3 (3));
 *  - the OFFERING-derived text and the JSON schema must name one set — read off the schema's TEXT, so a
 *    tool that reached only the schema is seen (criterion 18).
 */
class BridgeRenderingTest {

    private val staticTexts: List<String> =
        listOf(BridgeRendering.INSTRUCTION, BridgeRendering.REFERENCE_SENTENCE) + BridgeError.entries.map(BridgeRendering::retryNote)

    private fun descriptor(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private fun fixtureOffering() = BridgeOffering(
        listOf(
            OfferedTool(
                descriptor("fixture_quill", ToolLevels.IN_APP),
                ToolDescription(
                    ToolNamespace.LAUNCHER,
                    "Open a quill.",
                    listOf(ModelArg("app", "the app, in the user's own words", required = true, mention = true)),
                ),
            ),
            OfferedTool(
                descriptor("fixture_ledger", ToolLevels.SYSTEM_INTENT),
                ToolDescription(
                    ToolNamespace.DEVICE,
                    "Write the ledger.",
                    listOf(ModelArg("duration", "how long", required = true, mention = false)),
                ),
            ),
            OfferedTool(
                descriptor("fixture_parcel", ToolLevels.IN_APP),
                ToolDescription(ToolNamespace.CONTENT, "Find a parcel.", emptyList()),
            ),
        ),
    )

    @Test
    fun `the static text carries no forbidden context term`() {
        val hits = staticTexts.flatMap { text ->
            OutboundContextPolicy.FORBIDDEN_CONTEXT_TERMS.filter { text.contains(it, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), hits)
    }

    @Test
    fun `the static text carries no credential term`() {
        val hits = staticTexts.flatMap { text ->
            OutboundContextPolicy.CREDENTIAL_TERMS.filter { text.contains(it, ignoreCase = true) }
        }
        assertEquals(emptyList<String>(), hits)
    }

    /** The renderer takes ONLY the offering: an empty one renders nothing but the static instruction. */
    @Test
    fun `an empty offering renders exactly the static instruction`() {
        assertEquals(BridgeRendering.INSTRUCTION, BridgeRendering.systemText(BridgeOffering(emptyList())))
    }

    @Test
    fun `the schema and the prompt name the same tools and the same argument names`() {
        val offering = fixtureOffering()
        val schema = Json.parseToJsonElement(BridgeRendering.schemaJson(offering)).jsonObject
        val stepProperties = schema.getValue("properties").jsonObject
            .getValue("steps").jsonObject.getValue("items").jsonObject.getValue("properties").jsonObject
        val schemaTools = stepProperties.getValue("tool").jsonObject.getValue("enum").jsonArray
            .map { it.jsonPrimitive.content }
        val schemaArgs = stepProperties.getValue("args").jsonObject.getValue("properties").jsonObject.keys.toList()

        val prompt = BridgeRendering.systemText(offering)
        val promptTools = Regex("^- ([^:]+): ", RegexOption.MULTILINE).findAll(prompt)
            .map { it.groupValues[1] }.toList()
        val promptArgs = Regex("Every argument name: ([^.]*)\\.").find(prompt)!!.groupValues[1].split(", ")

        assertEquals(promptTools, schemaTools)
        assertEquals(promptArgs, schemaArgs)
        // The control: both are non-empty and in the offering's namespace order, so the two equalities
        // above cannot pass by comparing two empty lists.
        assertEquals(listOf("fixture_quill", "fixture_ledger", "fixture_parcel"), schemaTools)
        assertEquals(listOf("app", "duration"), schemaArgs)
    }

    /** Strict-compatible (D2, b′): every object closes its properties and requires every one of them. */
    @Test
    fun `every object in the schema closes its properties and requires all of them`() {
        val problems = mutableListOf<String>()
        fun walk(element: JsonElement, path: String) {
            when (element) {
                is JsonObject -> {
                    element["properties"]?.jsonObject?.let { properties ->
                        if (element["additionalProperties"] != JsonPrimitive(false)) problems += "$path: open"
                        val required = element["required"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet()
                        if (required != properties.keys) problems += "$path: required $required != ${properties.keys}"
                    }
                    element.forEach { (key, value) -> walk(value, "$path.$key") }
                }
                is JsonArray -> element.forEachIndexed { index, value -> walk(value, "$path[$index]") }
                else -> Unit
            }
        }

        walk(Json.parseToJsonElement(BridgeRendering.schemaJson(fixtureOffering())), "$")

        assertEquals(emptyList<String>(), problems)
    }

    @Test
    fun `the offering is grouped by namespace, one line per tool, in offering order`() {
        val expected =
            "Every argument name: app, duration. Give \"args\" every one of these names: the value for " +
                "each argument the chosen tool takes, null for the rest.\n" +
                "\n" +
                "Launcher (Sidr itself):\n" +
                "- fixture_quill: Open a quill. args: app (app, required) — the app, in the user's own words\n" +
                "\n" +
                "Device (this phone's own functions):\n" +
                "- fixture_ledger: Write the ledger. args: duration (required) — how long\n" +
                "\n" +
                "Content (the web and the app store):\n" +
                "- fixture_parcel: Find a parcel."

        assertEquals(expected, BridgeRendering.offeringText(fixtureOffering()))
    }

    /**
     * B-8 and controller ruling E8-P3: references reach the model only when the offering describes an
     * output — the outputs on each tool's line, the `ref` marker, the reference sentence, and the reference
     * form in the schema all arrive together. An argument that accepts a reference with NO output in the
     * offering changes nothing, byte for byte: a reference could name nothing. The key's enum is every
     * output name in the offering, distinct and sorted.
     */
    @Test
    fun `outputs and the reference sentence appear only when an offered tool describes an output`() {
        fun offering(withOutputs: Boolean) = BridgeOffering(
            listOf(
                OfferedTool(
                    descriptor("fixture_quill", ToolLevels.IN_APP),
                    ToolDescription(
                        ToolNamespace.LAUNCHER,
                        "Open a quill.",
                        listOf(ModelArg("app", "the app, in the user's own words", required = true, mention = true)),
                    ),
                ),
                OfferedTool(
                    descriptor("fixture_ledger", ToolLevels.SYSTEM_INTENT),
                    ToolDescription(
                        ToolNamespace.DEVICE,
                        "Write the ledger.",
                        listOf(ModelArg("duration", "how long", required = true, mention = false, acceptsRef = true)),
                        outputs = if (withOutputs) listOf(ModelOutput("entry", "the line written")) else emptyList(),
                    ),
                ),
                OfferedTool(
                    descriptor("fixture_parcel", ToolLevels.IN_APP),
                    ToolDescription(
                        ToolNamespace.CONTENT,
                        "Find a parcel.",
                        emptyList(),
                        outputs = if (withOutputs) listOf(ModelOutput("bundle", "the parcel found")) else emptyList(),
                    ),
                ),
            ),
        )

        // Without an output: exactly the 3a text and schema of the same tools, though `duration` accepts a reference.
        assertEquals(BridgeRendering.offeringText(fixtureOffering()), BridgeRendering.offeringText(offering(withOutputs = false)))
        assertEquals(BridgeRendering.schemaJson(fixtureOffering()), BridgeRendering.schemaJson(offering(withOutputs = false)))
        assertFalse(BridgeRendering.systemText(offering(withOutputs = false)).contains("from_step"))
        assertFalse(BridgeRendering.schemaJson(offering(withOutputs = false)).contains("from_step"))

        val expected =
            "Every argument name: app, duration. Give \"args\" every one of these names: the value for " +
                "each argument the chosen tool takes, null for the rest. An argument marked \"ref\" may hold, " +
                "instead of words, an output of an earlier step: {\"from_step\": <that step's position, " +
                "counting from 0>, \"key\": \"<an output that step's tool lists>\"}.\n" +
                "\n" +
                "Launcher (Sidr itself):\n" +
                "- fixture_quill: Open a quill. args: app (app, required) — the app, in the user's own words\n" +
                "\n" +
                "Device (this phone's own functions):\n" +
                "- fixture_ledger: Write the ledger. args: duration (required, ref) — how long outputs: entry — the line written\n" +
                "\n" +
                "Content (the web and the app store):\n" +
                "- fixture_parcel: Find a parcel. outputs: bundle — the parcel found"
        assertEquals(expected, BridgeRendering.offeringText(offering(withOutputs = true)))

        val args = Json.parseToJsonElement(BridgeRendering.schemaJson(offering(withOutputs = true))).jsonObject
            .getValue("properties").jsonObject.getValue("steps").jsonObject.getValue("items").jsonObject
            .getValue("properties").jsonObject.getValue("args").jsonObject.getValue("properties")
        assertEquals(
            Json.parseToJsonElement(
                """{"app":{"type":["string","null"]},""" +
                    """"duration":{"anyOf":[{"type":"string"},{"type":"null"},{"type":"object","additionalProperties":false,""" +
                    """"required":["from_step","key"],"properties":{"from_step":{"type":"integer"},""" +
                    """"key":{"type":"string","enum":["bundle","entry"]}}}]}}""",
            ),
            args,
        )
    }
}
