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

    val offering: BridgeOffering get() = BridgeFilter.offeringOf(registry, descriptions)

    fun plan(tool: String?, vararg args: Pair<String, String?>) =
        RawAnswer(kind = BridgeRendering.KIND_PLAN, step = RawStep(tool, mapOf(*args)), question = null, destination = null)

    fun answer(kind: String?, question: String? = null, destination: String? = null) =
        RawAnswer(kind = kind, step = null, question = question, destination = destination)
}
