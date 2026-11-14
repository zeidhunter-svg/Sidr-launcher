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
 * Re-measured in phase 3b Task 8, when the answer form became `steps` (the form line, the plan line's
 * `steps = [{…}]` — controller ruling E8-I1 —, the four «… are null» phrases, and «A plan has from one
 * to 1 steps, in order.»): the instruction 1 140 → 1 161 characters (+21, as predicted from the edits
 * before the run); fifteen tools 1 668 → 1 689, the band moved by the same 21 and kept its width (40)
 * and offsets; the marginal cost is unchanged (25.10 — the offering text adds nothing when no tool
 * describes an output, B-8).
 *
 * Re-measured in phase 3b Task 9, when the cap opened to the engine's (`RuntimeBudget.Default.maxSteps`,
 * «from one to 1 steps» → «from one to 8 steps», one character for one): the instruction 1 161, fifteen
 * tools 1 689, the marginal cost 25.10 — every pin where it was; none moved, no band changed.
 *
 * Re-measured in Task 9's fix round 1 (finding I2, ruling E9-R2), when the plan line stopped defining a
 * plan as ONE action («one action that one listed tool performs.» → «one or more actions of listed tools,
 * in order, one step per action.», +26 characters): the instruction 1 161 → 1 187; fifteen tools
 * 1 689 → 1 715, the band moved by the same 26 and kept its width (40) and offsets; the marginal cost
 * unchanged (25.10).
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
            1_187,
            render(0).length,
        )
        val marginal = (render(120).length - render(2).length) / 118.0
        assertTrue("marginal per-tool cost drifted to $marginal (measured ~25.1)", marginal in 24.0..26.5)
        assertTrue("15 tools render to ${render(15).length} (measured 1 715)", render(15).length in 1_697..1_737)
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
