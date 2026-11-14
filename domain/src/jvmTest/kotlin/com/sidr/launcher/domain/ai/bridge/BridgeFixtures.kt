package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels

/**
 * One registry and one set of descriptions for the validator and planner tests. Three offered tools,
 * one memory tool and one shortcut — both REGISTERED, both outside the offering, so "not offered" is
 * a different answer from "unknown". Every fixture word is invented (R14-43).
 */
internal object BridgeFixtures {

    private fun descriptor(id: String, level: ToolLevel, risk: ActionRiskLevel = ActionRiskLevel.SAFE, args: List<String> = emptyList()) =
        ToolDescriptor(
            id = ToolId(id),
            level = level,
            effect = ToolEffect.EXTERNAL,
            argSchema = args.map { ActionArg(it, description = it) },
            risk = risk,
            durability = ToolDurability.TRANSIENT,
        )

    val registry = FakeToolRegistry(
        listOf(
            descriptor("launch_app", ToolLevels.IN_APP, args = listOf("query", "app")),
            descriptor("web_search", ToolLevels.IN_APP, args = listOf("query")),
            descriptor("open_url", ToolLevels.IN_APP, ActionRiskLevel.CONFIRM, listOf("url")),
            descriptor("set_app_alias", ToolLevels.LAUNCHER_MEMORY, args = listOf("app", "phrase", "app_label")),
            descriptor("shortcut:net.fixture.qorvath/brimwell", ToolLevels.APP_SHORTCUT),
        ),
    )

    val descriptions = ToolDescriptions { id ->
        when (id.value) {
            "launch_app" -> ToolDescription(
                ToolNamespace.LAUNCHER,
                "Open an app.",
                listOf(ModelArg("app", "the app, in the user's own words", required = true, mention = true)),
            )
            "web_search" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Search the web.",
                listOf(ModelArg("query", "what to search for", required = true, mention = false)),
            )
            "open_url" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Open an address.",
                listOf(ModelArg("url", "the address", required = true, mention = false)),
            )
            // Described on purpose: it is the LEVEL that keeps it out, not a missing description.
            "set_app_alias" -> ToolDescription(
                ToolNamespace.LAUNCHER,
                "Name an app.",
                listOf(
                    ModelArg("app", "the app", required = true, mention = true),
                    ModelArg("phrase", "the name", required = true, mention = false),
                ),
            )
            else -> null
        }
    }

    val offering: BridgeOffering get() = BridgeFilter.offeringOf(registry, BridgeFilter.ANDROID_OFFERED_LEVELS, descriptions)

    /** A one-step plan whose arguments are words (or null) — the 3a form, read as the transport reads it. */
    fun plan(tool: String?, vararg args: Pair<String, String?>) =
        steps(step(tool, *args.map { (name, value) -> name to value?.let(RawArg::Text) }.toTypedArray()))

    /** One step as the transport reads it: each argument words, a reference, or null. */
    fun step(tool: String?, vararg args: Pair<String, RawArg?>) = RawStep(tool, mapOf(*args))

    /** A plan of these steps, in order. */
    fun steps(vararg steps: RawStep) =
        RawAnswer(kind = BridgeRendering.KIND_PLAN, steps = steps.toList(), question = null, destination = null)

    fun answer(kind: String?, question: String? = null, destination: String? = null) =
        RawAnswer(kind = kind, steps = null, question = question, destination = destination)

    /**
     * A second, separate offering for references (A4′ phase 3b, Task 8; B-18): two tools that each describe
     * an output, so a step can name an earlier one. Kept apart from [offering] so the 3a tests above read
     * the same offering they always did. `label` is taken by both tools and accepts a reference in
     * `fixture_sieve` ONLY — the validator must judge `acceptsRef` on the chosen tool, not on the name.
     * Every word is invented (R14-43); the output names share no word with any argument name.
     */
    val chainRegistry = FakeToolRegistry(
        listOf(
            descriptor("fixture_sieve", ToolLevels.IN_APP, args = listOf("name", "label")),
            descriptor("fixture_mortar", ToolLevels.IN_APP, args = listOf("target", "label")),
        ),
    )

    val chainDescriptions = ToolDescriptions { id ->
        when (id.value) {
            "fixture_sieve" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Sieve a name.",
                listOf(
                    ModelArg("name", "the name, in the user's own words", required = true, mention = true),
                    ModelArg("label", "a label", required = false, mention = false, acceptsRef = true),
                ),
                outputs = listOf(ModelOutput("kept", "what the sieve kept")),
            )
            "fixture_mortar" -> ToolDescription(
                ToolNamespace.CONTENT,
                "Grind a target.",
                listOf(
                    ModelArg("target", "what to grind", required = true, mention = false, acceptsRef = true),
                    ModelArg("label", "a label", required = false, mention = false),
                ),
                outputs = listOf(ModelOutput("ground", "what the mortar ground")),
            )
            else -> null
        }
    }

    val chainOffering: BridgeOffering get() =
        BridgeFilter.offeringOf(chainRegistry, BridgeFilter.ANDROID_OFFERED_LEVELS, chainDescriptions)
}
