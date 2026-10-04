package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.core.testing.FakeToolRegistry
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the bridge's prompt weighs — re-anchored from the deleted router (spec §7.12.3). The previous
 * version measured a local helper modelled on `CatalogSchemaRenderer` with the router's 562-char
 * instruction as a proxy preamble; the real renderer exists now, so this measures it.
 *
 * Synthetic descriptors on purpose (`tool_i`, one launcher namespace, "Do one thing.", no arguments):
 * `:domain` cannot reach the production descriptions, and a synthetic series is what makes the
 * marginal cost a property of the RENDERER rather than of whatever we wrote about a tool. Every number
 * pinned below was computed before the code existed (plan §0.10) from the exact strings the plan
 * specifies; a mismatch is a finding about the renderer, not a band to widen.
 *
 * `B3`'s conclusion stands on the real renderer: two hundred synthetic tools render to about 1 600
 * tokens — the prompt size was never the barrier.
 */
class ToolSchemaWeightTest {

    @Test
    fun `the bridge prompt's preamble and per-tool cost are what they were measured to be`() {
        listOf(2, 15, 120, 200).forEach { n ->
            val text = render(n)
            println("bridge prompt (synthetic, no args): n=$n chars=${text.length} approx_tokens=${text.length / 4}")
        }

        assertEquals(
            "BridgeRendering.INSTRUCTION changed length; re-measure and update the ADR rather than this pin",
            1_140,
            render(0).length,
        )
        val marginal = (render(120).length - render(2).length) / 118.0
        assertTrue("marginal per-tool cost drifted to $marginal (measured ~25.1)", marginal in 24.0..26.5)
        assertTrue("15 tools render to ${render(15).length} (measured 1 668)", render(15).length in 1_650..1_690)
    }

    private fun render(n: Int): String {
        val registry = FakeToolRegistry(
            List(n) {
                ToolDescriptor(
                    id = ToolId("tool_$it"),
                    level = ToolLevels.IN_APP,
                    effect = ToolEffect.LOCAL,
                    risk = ActionRiskLevel.SAFE,
                    durability = ToolDurability.TRANSIENT,
                )
            },
        )
        val offering = BridgeFilter.offeringOf(registry) { ToolDescription(ToolNamespace.LAUNCHER, "Do one thing.", emptyList()) }
        return BridgeRendering.systemText(offering)
    }
}
