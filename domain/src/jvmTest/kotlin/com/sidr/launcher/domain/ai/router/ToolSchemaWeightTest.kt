package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.tool.ToolDescriptor
import com.sidr.launcher.domain.tool.ToolDurability
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolLevels
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a tool-shaped schema would weigh, if a future block ever renders one.
 *
 * This is a measurement, not a feature: nothing in the product renders tools to a model today, and
 * this test asserts only that the weight is what it is measured to be. It exists because Master Plan
 * §3.6 `B3` justifies selection with a prompt-size claim about a code path that does not exist, and
 * the block that builds that path should start from a number.
 *
 * [renderToolSchema] is a **local test helper**, not a production renderer — §12 of the A1'' spec
 * lists rendering tools to the model as a non-goal, and shipping an unused renderer is how an
 * unreviewed egress path appears. It is modelled on [CatalogSchemaRenderer.render]'s one-line-per
 * -descriptor shape (the same [CatalogSchemaRenderer.ROUTER_INSTRUCTION] preamble, one `"\n- " + id +
 * ": " + args` line per tool), with one necessary difference: [ToolDescriptor] carries no
 * `description` field — spec §5.2 deliberately withheld a label/description so no third-party string
 * crosses into `:domain` — so a rendered tool line has **no description text**, only an id and (when
 * present) an arg schema. That makes every number below **lighter than a real tool prompt would be**:
 * a model choosing among tools would need descriptions, and [ToolDescriptor] does not hold one. This
 * test does not invent a description field to "make it realistic" — it measures what the type
 * actually holds, and this paragraph is the record of what that omits.
 */
class ToolSchemaWeightTest {

    @Test
    fun `tool schema weight is measured for the sizes A1'' actually produces`() {
        listOf(2, 15, 120).forEach { n ->
            val rendered = renderToolSchema(List(n) { descriptor(ToolId("tool_$it")) })
            println("tool schema: n=$n chars=${rendered.length} approx_tokens=${rendered.length / 4}")
        }

        val fifteen = renderToolSchema(List(15) { descriptor(ToolId("tool_$it")) })
        assertTrue(
            "15 descriptors rendering to ${fifteen.length} chars is far outside the shape this measured; " +
                "re-measure and update the ADR rather than widening this bound",
            fifteen.length < 4_000,
        )
    }

    /**
     * Local test helper modelled on [CatalogSchemaRenderer.render]'s shape. Not a production
     * renderer — see the class KDoc. Omits description text because [ToolDescriptor] has none.
     */
    private fun renderToolSchema(descriptors: List<ToolDescriptor>): String = buildString {
        append(CatalogSchemaRenderer.ROUTER_INSTRUCTION)
        descriptors.forEach { d ->
            append("\n- ")
            append(d.id.value)
            append(":")
            if (d.argSchema.isNotEmpty()) {
                append(" args: ")
                append(
                    d.argSchema.joinToString(", ") { arg ->
                        val req = if (arg.required) "required" else "optional"
                        "${arg.name} ($req) — ${arg.description}"
                    },
                )
            }
        }
    }

    private fun descriptor(id: ToolId): ToolDescriptor = ToolDescriptor(
        id = id,
        level = ToolLevels.IN_APP,
        effect = ToolEffect.LOCAL,
        risk = ActionRiskLevel.SAFE,
        durability = ToolDurability.TRANSIENT,
    )
}
