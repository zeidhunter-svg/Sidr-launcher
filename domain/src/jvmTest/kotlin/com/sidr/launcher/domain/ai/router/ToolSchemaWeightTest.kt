package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionArg
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
 * What a tool-shaped schema would weigh, if a future block ever renders one.
 *
 * This is a measurement, not a feature: nothing in the product renders tools to a model today. It
 * exists because Master Plan §3.6 `B3` justifies selection with a prompt-size claim ("two hundred
 * descriptors do not fit in a prompt") about a code path that does not exist, and the block that
 * builds that path should start from a number.
 *
 * **Decomposition (fix round 1 — the original single bound could not fail).** The rendered length is
 * exactly `562 (fixed preamble) + n * (4 + len(id) [+ arg tail])`. At `n=2` — the sketch's smallest
 * case — 562 of 582 chars, **96.6%**, is the static [CatalogSchemaRenderer.ROUTER_INSTRUCTION]
 * preamble, not per-descriptor content; `n=2 → 145 approx tokens` must **not** be read as "two tools
 * cost 145 tokens". The assertions below pin the two halves of that decomposition separately — the
 * fixed preamble length and the marginal per-descriptor cost — instead of one loose upper bound, so
 * that editing [CatalogSchemaRenderer.ROUTER_INSTRUCTION] (the **live outbound router prompt**, a
 * thing that does get edited) fails this test rather than silently falsifying every number quoted
 * from it. With that decomposition pinned, **this test now does assert only that the weight is what
 * it is measured to be** — the earlier draft's bound (`< 4_000` against a measured 717) was 5.6x
 * above the measurement and untrue to that sentence.
 *
 * [renderToolSchema] is a **local test helper**, not a production renderer — §12 of the A1'' spec
 * lists rendering tools to the model as a non-goal, and shipping an unused renderer is how an
 * unreviewed egress path appears. It is modelled on [CatalogSchemaRenderer.render]'s one-line-per
 * -descriptor shape (the same [CatalogSchemaRenderer.ROUTER_INSTRUCTION] preamble, one `"\n- " + id +
 * ":" [+ args]` line per tool). It differs from [CatalogSchemaRenderer.render] in one place:
 * [ToolDescriptor] carries no `description` field on the tool itself, so a rendered tool line has no
 * description text after the id — only the id and (when declared) an arg schema. **That is not a
 * spec §5.2 fact** — §5.2 concerns a *shortcut's* label, resolved at runtime through the
 * `DynamicToolNames` port, which is a different and later concern; the absent tool-level
 * `description` predates §5.2 and is a spec §5.1/design-of-`ToolDescriptor` choice, made so no
 * third-party string crosses into `:domain` at the *tool* level. It is not categorically true that no
 * description crosses into `:domain`: [ActionArg.description] is a plain `String` **inside**
 * [ToolDescriptor.argSchema], and `Tier0IntentToolSource` (`:data:repository`) puts a real English
 * sentence there for `set_timer`'s `duration` arg — reused verbatim below as [REALISTIC_ARG_SCHEMA].
 * Descriptions do cross into `:domain`, at the **arg** level, not the tool level.
 *
 * That makes every number below **lighter than a real tool prompt would be**, in four independent
 * ways, none hidden and none invented — three measured below, the fourth named but deliberately not
 * modelled:
 * - **ids.** `ToolId("tool_$it")` (6-8 chars) is the sketch's synthetic floor. The ids this block
 *   actually registers for its own subject — `ShortcutToolIds.of(pkg, shortcutId)`, e.g.
 *   `shortcut:com.whatsapp/compose_message` — run roughly 3x longer, because per-descriptor cost
 *   *is* id length (`4 + len(id)`, nothing else varies per line without an arg schema). This test
 *   therefore measures **two** series side by side: the synthetic floor, and a shortcut-shaped series
 *   using ids of that real form (`shortcut:com.example.app$it/shortcut_$it`) — not the real
 *   `ShortcutToolSource`, which `:domain:jvmTest` cannot and must not reach; only the id *shape*.
 * - **args.** No descriptor in either series declares an argument, so the arg-rendering branch in
 *   [renderToolSchema] never executes. A real declared arg is not free: `set_timer`'s one real arg
 *   renders to 75 chars (`"duration (required) — How long the timer should run, e.g. 10 minutes"`
 *   plus the `" args: "` lead-in) — about **7x** the ~11-char marginal cost an argument-free
 *   descriptor measures at.
 * - **the preamble itself.** [CatalogSchemaRenderer.ROUTER_INSTRUCTION] is the **action** router's
 *   preamble ("Map the user's request to exactly one registered action … only use the action ids and
 *   argument names listed below"), reused here as the only static instruction text `:domain` owns —
 *   it is a proxy, not a tool-router preamble that does not exist yet. A real one would likely be
 *   longer, so even the fixed 562-char term this test pins is itself a **floor**.
 * - **the tool-level description — named, not modelled (fix round 2).** [CatalogSchemaRenderer.render]
 *   — the real analog this helper is modelled on — appends `": " + descriptor.description` after
 *   every id; [ToolDescriptor] has no such field (the KDoc paragraph above this list is about exactly
 *   that absence), so [renderToolSchema] cannot render one and none of the figures below carry it.
 *   This is **not** folded into the "pessimistic correction" below: inventing a plausible length for a
 *   field the type does not have would be measuring a hypothetical rather than the type, which is the
 *   error this whole test exists to avoid. `CatalogSchemaRenderer.render`'s own per-action description
 *   tail (e.g. `"Launch an installed app by name"`, `"Open a web address in the browser"`) is the
 *   closest indication of what it would additionally cost, and it is not small — comparable to or
 *   larger than the id itself.
 *
 * **The conclusion (the point of this task).** Even on the synthetic-id, no-arg series this test
 * actually measures, 200 descriptors — the exact figure Master Plan `B3` uses to justify selection —
 * render to well under a thousand tokens (see the `n=200, as measured` line printed by the test); the
 * shortcut-shaped, real-arg-schema `n=200, pessimistic correction` line, combining the id and arg
 * widening factors named above (**not** the un-modelled tool-level description, and not a longer
 * preamble), is still in the low thousands of tokens. Both fit any modern context window with room to
 * spare — and the conclusion below holds at either figure by a wide margin, so it does not turn on
 * which one a reader picks. **This measurement undercuts `B3`'s stated justification for tool
 * selection** ("two hundred descriptors do not fit in a prompt") — they do fit, by a wide margin, even
 * under the pessimistic correction. The honest resolution is not that selection is unjustified, but
 * that its justification does not come from prompt size: selection in this block (spec §6.1) stands on
 * the **deterministic matcher branch**, which is a reachability/precision argument, not a
 * token-budget one — so this measurement confirms the design actually shipped rather than undermining
 * it. This paragraph reports that finding; it does not amend Master Plan `B3` — the Master Plan has
 * its own change-control and is not amended from inside a block.
 */
class ToolSchemaWeightTest {

    @Test
    fun `tool schema weight is measured for the sizes A1'' actually produces`() {
        listOf(2, 15, 120).forEach { n ->
            val synthetic = renderToolSchema(List(n) { descriptor(ToolId("tool_$it")) })
            println(
                "tool schema (synthetic id, no args): n=$n chars=${synthetic.length} " +
                    "approx_tokens=${synthetic.length / 4}",
            )

            val shortcutShaped = renderToolSchema(List(n) { descriptor(shortcutShapedId(it)) })
            println(
                "tool schema (shortcut-shaped id, no args): n=$n chars=${shortcutShaped.length} " +
                    "approx_tokens=${shortcutShaped.length / 4}",
            )
        }

        // The B3 comparison point itself: Master Plan §3.6 B3 says "two hundred descriptors do not
        // fit in a prompt". Measured both as the sketch's synthetic series and under the pessimistic
        // correction (shortcut-shaped id + a real declared arg schema, see the class KDoc).
        val twoHundredAsMeasured = renderToolSchema(List(200) { descriptor(ToolId("tool_$it")) })
        println(
            "tool schema (n=200, as measured): chars=${twoHundredAsMeasured.length} " +
                "approx_tokens=${twoHundredAsMeasured.length / 4}",
        )
        val twoHundredPessimistic = renderToolSchema(
            List(200) { descriptor(shortcutShapedId(it), argSchema = REALISTIC_ARG_SCHEMA) },
        )
        println(
            "tool schema (n=200, pessimistic correction): chars=${twoHundredPessimistic.length} " +
                "approx_tokens=${twoHundredPessimistic.length / 4}",
        )

        // Pin 1: the fixed preamble. An empty descriptor list renders to exactly the static
        // ROUTER_INSTRUCTION — this is the ~96% of the n=2 figure that is not per-descriptor content.
        val preambleOnly = renderToolSchema(emptyList())
        assertEquals(
            "ROUTER_INSTRUCTION's rendered length changed to ${preambleOnly.length} chars; " +
                "re-measure and update the ADR rather than widening this pin",
            562,
            preambleOnly.length,
        )

        // Pin 2: the marginal per-descriptor cost, derived from two measured points rather than
        // hardcoded — so it tracks the renderer's actual line shape ("\n- " + id + ":") instead of a
        // number typed once and forgotten.
        val two = renderToolSchema(List(2) { descriptor(ToolId("tool_$it")) })
        val oneTwenty = renderToolSchema(List(120) { descriptor(ToolId("tool_$it")) })
        val marginalPerDescriptor = (oneTwenty.length - two.length) / 118.0
        assertTrue(
            "marginal per-descriptor cost drifted to $marginalPerDescriptor chars (measured ~11); " +
                "re-measure and update the ADR rather than widening this bound",
            marginalPerDescriptor in 10.0..12.0,
        )

        // Pin 3: a tightened total-size check at n=15 — a band around the measured 717 chars, not a
        // loose ceiling. A tripling (~2151) or even a doubling (~1434) now goes red; the earlier
        // `< 4_000` bound passed both.
        val fifteen = renderToolSchema(List(15) { descriptor(ToolId("tool_$it")) })
        assertTrue(
            "15 descriptors rendering to ${fifteen.length} chars is far outside the shape this " +
                "measured (562 preamble + ~11/descriptor ~ 727); re-measure and update the ADR " +
                "rather than widening this bound",
            fifteen.length in 700..760,
        )
    }

    /**
     * Local test helper modelled on [CatalogSchemaRenderer.render]'s shape. Not a production
     * renderer — see the class KDoc. Omits description text because [ToolDescriptor] has none at the
     * tool level (an arg-level description, when [ToolDescriptor.argSchema] declares one, is
     * rendered — matching [CatalogSchemaRenderer.render]'s arg-tail shape exactly).
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

    private fun descriptor(id: ToolId, argSchema: List<ActionArg> = emptyList()): ToolDescriptor =
        ToolDescriptor(
            id = id,
            level = ToolLevels.IN_APP,
            effect = ToolEffect.LOCAL,
            risk = ActionRiskLevel.SAFE,
            durability = ToolDurability.TRANSIENT,
            argSchema = argSchema,
        )

    /**
     * An id in the real shape `ShortcutToolIds.of` mints (`:data:repository`, not reachable from
     * `:domain:jvmTest`, and not reached here — only the id *shape* is reused): `"shortcut:" + pkg +
     * "/" + shortcutId`. Stands in for A1'''s own tool mass, which this block's `n=120` line is meant
     * to represent (item 2 of fix round 1) — the synthetic `tool_$it` series alone understated it.
     */
    private fun shortcutShapedId(i: Int): ToolId = ToolId("shortcut:com.example.app$i/shortcut_$i")

    private companion object {
        /**
         * `Tier0IntentToolSource`'s real `set_timer` arg schema (`:data:repository`), copied verbatim
         * rather than paraphrased — this is what "a real declared arg" actually costs, used only for
         * the `n=200` pessimistic-correction line.
         */
        val REALISTIC_ARG_SCHEMA = listOf(
            ActionArg("duration", description = "How long the timer should run, e.g. 10 minutes"),
        )
    }
}
