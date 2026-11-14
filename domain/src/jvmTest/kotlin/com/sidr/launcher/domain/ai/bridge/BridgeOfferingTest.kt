package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevel
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the model is offered (spec §7.5, §7.6, §7.12.1; owner fork F3a-A). The filter is a predicate on
 * a property every tool declares about itself — its level — never a list of ids; and it is an
 * ALLOW-list, so a level nobody has decided about is not offered.
 */
class BridgeOfferingTest {

    private fun tool(id: String, level: ToolLevel) = ToolDescriptor(
        id = ToolId(id),
        level = level,
        effect = ToolEffect.EXTERNAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )

    private val described = ToolDescriptions { id ->
        ToolDescription(ToolNamespace.DEVICE, "Fixture tool ${id.value}.", emptyList())
    }

    private fun offered(vararg tools: ToolDescriptor, descriptions: ToolDescriptions = described): List<String> =
        BridgeFilter.offeringOf(FakeToolRegistry(tools.toList()), BridgeFilter.ANDROID_OFFERED_LEVELS, descriptions)
            .tools.map { it.descriptor.id.value }

    @Test
    fun `the authored levels are offered, each tool with its description`() {
        val registry = FakeToolRegistry(
            listOf(tool("fixture_quill", ToolLevels.IN_APP), tool("fixture_ledger", ToolLevels.SYSTEM_INTENT)),
        )

        val offering = BridgeFilter.offeringOf(registry, BridgeFilter.ANDROID_OFFERED_LEVELS, described)

        assertEquals(listOf("fixture_quill", "fixture_ledger"), offering.tools.map { it.descriptor.id.value })
        assertEquals("Fixture tool fixture_ledger.", offering.find(ToolId("fixture_ledger"))!!.description.text)
    }

    /** Criterion 7 (spec §7.5, `B13`): the agent changes its own memory only by an explicit trigger. */
    @Test
    fun `launcher memory is never offered`() {
        assertEquals(
            listOf("fixture_quill"),
            offered(tool("fixture_quill", ToolLevels.IN_APP), tool("fixture_note", ToolLevels.LAUNCHER_MEMORY)),
        )
    }

    /** Criterion 6 / D1: a shortcut's name is third-party text — the user's world, and an injection path. */
    @Test
    fun `a shortcut is never offered, even with a description`() {
        assertEquals(
            listOf("fixture_quill"),
            offered(
                tool("fixture_quill", ToolLevels.IN_APP),
                tool("shortcut:net.fixture.qorvath/brimwell", ToolLevels.APP_SHORTCUT),
            ),
        )
    }

    /** Criterion 8 (spec §7.6): tool №21 at an offered level is offered with no edit to the filter. */
    @Test
    fun `a new tool at an offered level is offered without touching the filter`() {
        assertEquals(listOf("fixture_parcel"), offered(tool("fixture_parcel", ToolLevels.SYSTEM_INTENT)))
    }

    /**
     * Owner fork F3a-A (allow). `ToolLevel` is open; AppFunctions or MCP would arrive as a new level
     * carrying third-party names. Under the allow-list such a level is offered only by decision.
     * Under (deny) this test inverts to `…is offered`.
     */
    @Test
    fun `a tool at a level the filter has not heard of is not offered`() {
        assertEquals(emptyList<String>(), offered(tool("fixture_unnote", ToolLevel("app_function"))))
    }

    /**
     * A4′ phase 3b, B-12: the levels are the consumer's, not the filter's. One registry holding a sandbox
     * tool and an Android one answers with the sandbox tool alone for the JVM's set and with the Android
     * tool alone for Android's — so neither consumer is offered the other's tools by default.
     */
    @Test
    fun `the offering is the levels its consumer passes`() {
        val registry = FakeToolRegistry(
            listOf(tool("fixture_quill", ToolLevels.IN_APP), tool("fixture_pallet", ToolLevels.SANDBOX)),
        )

        val forSandbox = BridgeFilter.offeringOf(registry, setOf(ToolLevels.SANDBOX), described)
        val forAndroid = BridgeFilter.offeringOf(registry, BridgeFilter.ANDROID_OFFERED_LEVELS, described)

        assertEquals(listOf("fixture_pallet"), forSandbox.tools.map { it.descriptor.id.value })
        assertEquals(listOf("fixture_quill"), forAndroid.tools.map { it.descriptor.id.value })
    }

    /** Fail-closed: the model may only see what we have described in our own words. */
    @Test
    fun `a tool with no description is not offered`() {
        val partial = ToolDescriptions { id ->
            if (id.value == "fixture_quill") ToolDescription(ToolNamespace.DEVICE, "Fixture.", emptyList()) else null
        }

        assertEquals(
            listOf("fixture_quill"),
            offered(
                tool("fixture_quill", ToolLevels.IN_APP),
                tool("fixture_ledger", ToolLevels.SYSTEM_INTENT),
                descriptions = partial,
            ),
        )
    }
}
