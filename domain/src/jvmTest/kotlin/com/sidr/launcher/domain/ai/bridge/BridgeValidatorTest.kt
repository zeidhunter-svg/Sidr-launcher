package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.RuntimeBudget
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.answer
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.chainOffering
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.chainRegistry
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.offering
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.plan
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.registry
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.step
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.steps
import com.sidr.launcher.domain.sort.ArgSorts
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The bridge's first line (spec §7.12.2): shape, membership in THE OFFERING (not the federation),
 * derivability. Everything here runs before `startPlanned`; the engine's own check against the
 * federation is the second line, not the first.
 */
class BridgeValidatorTest {

    private fun validate(answer: RawAnswer, command: String = "открой lumora") =
        BridgeValidator.validate(answer, offering, registry, command)

    /**
     * Over the reference offering, at the production cap (the engine's `maxSteps` since Task 9, controller
     * ruling E9-P2): a two-step plan is within it, so each check below is judged on its own.
     */
    private fun validateChain(answer: RawAnswer) =
        BridgeValidator.validate(answer, chainOffering, chainRegistry, "открой lumora")

    private val sieve = step("fixture_sieve", "name" to RawArg.Text("lumora"), "label" to null)

    /** Right on every count: step 1 names step 0's `kept`, on an argument that accepts a reference. */
    private val chained = steps(sieve, step("fixture_mortar", "target" to RawArg.Ref(0, "kept"), "label" to null))

    @Test
    fun `a plan over an offered tool is valid, with its risk and effect from the registry`() {
        val verdict = validate(plan("launch_app", "app" to "lumora", "query" to null, "url" to null))

        assertEquals(
            Verdict.Valid(
                ModelPlanning.Proposed(
                    listOf(
                        ProposedStep(
                            tool = ToolId("launch_app"),
                            args = mapOf("app" to "lumora"),
                            origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                            risk = ActionRiskLevel.SAFE,
                            effect = ToolEffect.EXTERNAL,
                        ),
                    ),
                ),
            ),
            verdict,
        )
    }

    @Test
    fun `a tool id nothing registers is unknown`() {
        assertEquals(Verdict.Rejected(BridgeError.UNKNOWN_TOOL), validate(plan("fixture_quill")))
    }

    /** Criterion 16, second half: membership is the OFFERING's. Both are registered; neither is offered. */
    @Test
    fun `a registered tool outside the offering is rejected before anything runs`() {
        assertEquals(
            Verdict.Rejected(BridgeError.NOT_OFFERED),
            validate(plan("set_app_alias", "app" to "lumora", "phrase" to "кварцелот")),
        )
        assertEquals(
            Verdict.Rejected(BridgeError.NOT_OFFERED),
            validate(plan("shortcut:net.fixture.qorvath/brimwell")),
        )
    }

    /**
     * Criterion 16, first half, **as amended by D1** (A4′ acceptance tail, task 2; ADR «2026-11-22 — D1, поправка:
     * имя приложения, исправленное моделью»). Until the amendment an app argument the user did not type was
     * refused as `UNDERIVABLE_MENTION`; there is no transliterating fold, so «открой клод» could never bind
     * «Claude» whatever the model answered. Now an APP mention the user did not type is admitted — and labelled
     * the MODEL's ([ValueOrigin.ModelGenerated]), which is what the binder reads to bind it only to an exact
     * label or alias. The user's own words stay the user's (`a plan over an offered tool is valid…`, above).
     */
    @Test
    fun `an app the user did not name is admitted as the model's`() {
        assertEquals(
            Verdict.Valid(
                ModelPlanning.Proposed(
                    listOf(
                        ProposedStep(
                            tool = ToolId("launch_app"),
                            args = mapOf("app" to "Claude"),
                            origins = mapOf("app" to ValueOrigin.ModelGenerated),
                            risk = ActionRiskLevel.SAFE,
                            effect = ToolEffect.EXTERNAL,
                        ),
                    ),
                ),
            ),
            validate(plan("launch_app", "app" to "Claude", "query" to null, "url" to null), command = "открой клод"),
        )
    }

    /**
     * The amendment is for APPS only. A mention of another sort — a file the sandbox resolves by its name, whether
     * its description declares the sort or (as `SandboxToolDescriptions` does) none — that the user did not type is
     * refused exactly as before: the device would resolve it by word forms, as it resolves the user's words. The
     * control: the same argument with the user's own words is valid.
     */
    @Test
    fun `a non-app mention the user did not type is still refused`() {
        val fileSorted = BridgeFilter.offeringOf(
            chainRegistry,
            BridgeFilter.ANDROID_OFFERED_LEVELS,
            ToolDescriptions { id ->
                if (id.value == "fixture_sieve") {
                    ToolDescription(
                        ToolNamespace.CONTENT,
                        "Sieve a file.",
                        listOf(ModelArg("name", "the file, in the user's own words", required = true, mention = true, sort = ArgSorts.FILE)),
                    )
                } else {
                    null
                }
            },
        )
        val sieve = plan("fixture_sieve", "name" to "brennquist", "label" to null)

        assertEquals(
            "a FILE-sorted mention",
            Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION),
            BridgeValidator.validate(sieve, fileSorted, chainRegistry, "просей отчёт"),
        )
        assertEquals(
            "a mention whose sort is not declared",
            Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION),
            BridgeValidator.validate(sieve, chainOffering, chainRegistry, "просей отчёт"),
        )
        assertEquals(
            "the control: the user's own words",
            ValueOrigin.SaidBy(PrincipalId.USER),
            ((BridgeValidator.validate(sieve, fileSorted, chainRegistry, "просей brennquist") as Verdict.Valid).planning as ModelPlanning.Proposed)
                .steps.single().origins["name"],
        )
    }

    @Test
    fun `a missing required argument is a shape error`() {
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(plan("web_search", "query" to null)))
    }

    @Test
    fun `an argument the tool does not take, given a value, is a shape error`() {
        assertEquals(
            Verdict.Rejected(BridgeError.SHAPE),
            validate(plan("launch_app", "app" to "lumora", "url" to "https://quillmere.example")),
        )
    }

    @Test
    fun `an assistant answer needs its question`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.ToAssistant("почему небо синее")),
            validate(answer(BridgeRendering.KIND_ASSISTANT, question = " почему небо синее ")),
        )
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_ASSISTANT, question = " ")))
    }

    @Test
    fun `a navigation answer names one of Sidr's screens`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.Navigate(SidrDestination.APP_GRID)),
            validate(answer(BridgeRendering.KIND_NAVIGATE, destination = "app_grid")),
        )
        assertEquals(
            Verdict.Rejected(BridgeError.SHAPE),
            validate(answer(BridgeRendering.KIND_NAVIGATE, destination = "system_settings")),
        )
    }

    @Test
    fun `a clarify answer needs its question`() {
        assertEquals(
            Verdict.Valid(ModelPlanning.Clarify("какой мессенджер?")),
            validate(answer(BridgeRendering.KIND_CLARIFY, question = "какой мессенджер?")),
        )
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_CLARIFY)))
    }

    @Test
    fun `a decline is a decline`() {
        assertEquals(Verdict.Valid(ModelPlanning.Declined), validate(answer(BridgeRendering.KIND_DECLINE)))
    }

    @Test
    fun `an unknown kind, no kind, or a plan with no step is a shape error`() {
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer("maybe")))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(null)))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(answer(BridgeRendering.KIND_PLAN)))
        // `steps` present and empty: the schema says `minItems: 1`, and the validator holds it too.
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validate(steps()))
    }

    /** The minimum of labels (spec §7.12.1): a search text the user did not type is the model's. */
    @Test
    fun `a search text the user did not type is labelled the model's`() {
        val verdict = validate(
            plan("web_search", "query" to "ostravel lectures by experts"),
            command = "найди лекции ostravel",
        ) as Verdict.Valid
        val step = (verdict.planning as ModelPlanning.Proposed).steps.single()

        assertEquals(mapOf("query" to ValueOrigin.ModelGenerated), step.origins)
    }

    /**
     * Fork 3b-B: a plan is judged step by step, and EVERY step is — not only the first. The control is
     * [chained], read back whole: it is what makes a two-step plan admissible at all, and the one positive
     * reading of a reference (it lands in `refs`, never in `args`, and carries no origin: its value is the
     * device's, from step 0). The bad plan differs from it only in its second step, whose tool is unknown.
     */
    @Test
    fun `every step of a plan is judged, and one bad step rejects the plan`() {
        assertEquals(
            Verdict.Valid(
                ModelPlanning.Proposed(
                    listOf(
                        ProposedStep(
                            tool = ToolId("fixture_sieve"),
                            args = mapOf("name" to "lumora"),
                            origins = mapOf("name" to ValueOrigin.SaidBy(PrincipalId.USER)),
                            risk = ActionRiskLevel.SAFE,
                            effect = ToolEffect.EXTERNAL,
                        ),
                        ProposedStep(
                            tool = ToolId("fixture_mortar"),
                            args = emptyMap(),
                            origins = emptyMap(),
                            risk = ActionRiskLevel.SAFE,
                            effect = ToolEffect.EXTERNAL,
                            refs = mapOf("target" to StepRef(step = 0, key = "kept")),
                        ),
                    ),
                ),
            ),
            validateChain(chained),
        )
        assertEquals(Verdict.Rejected(BridgeError.UNKNOWN_TOOL), validateChain(steps(sieve, step("fixture_quill"))))
    }

    /**
     * B-9, B-18: a reference names an EARLIER step. Each reference below is right on every other count —
     * on an argument that accepts one, naming an output the named step's tool does describe — so only the
     * order refuses it.
     */
    @Test
    fun `a reference to a later or the same step is rejected`() {
        val forward = steps(step("fixture_mortar", "target" to RawArg.Ref(1, "kept")), sieve)
        val itself = steps(sieve, step("fixture_mortar", "target" to RawArg.Ref(1, "ground")))
        val beforeTheFirst = steps(sieve, step("fixture_mortar", "target" to RawArg.Ref(-1, "kept")))

        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validateChain(forward))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validateChain(itself))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validateChain(beforeTheFirst))
    }

    /** B-9: `ground` IS an output — of `fixture_mortar`, not of `fixture_sieve`, the step it names. */
    @Test
    fun `a reference to an output the step's tool does not describe is rejected`() {
        assertEquals(
            Verdict.Rejected(BridgeError.SHAPE),
            validateChain(steps(sieve, step("fixture_mortar", "target" to RawArg.Ref(0, "ground")))),
        )
    }

    /**
     * B-18: a reference only where the chosen tool's description says so. `label` accepts one in
     * `fixture_sieve` and not in `fixture_mortar`, so the name is not what decides; and a mention stays
     * words. Both references are otherwise right (step 0, its `kept`).
     */
    @Test
    fun `a reference on an argument that does not accept one is rejected`() {
        val onAnotherToolsName = steps(
            sieve,
            step("fixture_mortar", "target" to RawArg.Text("ostravel"), "label" to RawArg.Ref(0, "kept")),
        )
        val onAMention = steps(sieve, step("fixture_sieve", "name" to RawArg.Ref(0, "kept")))

        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validateChain(onAnotherToolsName))
        assertEquals(Verdict.Rejected(BridgeError.SHAPE), validateChain(onAMention))
    }

    /**
     * Fork 3b-B, controller ruling E9-P1: the cap is the engine's own — [RuntimeBudget.Default]'s
     * `maxSteps`, which the instruction and the schema also say ([BridgeRendering.MAX_STEPS_OFFERED]) —
     * and it is judged on both sides of the bound at the production default. Every step is [sieve], valid
     * on its own, so the size and nothing else decides. Rewritten at constant count from Task 8's interim
     * `> a plan with more steps than offered is rejected` (two steps refused while the cap was one).
     */
    @Test
    fun `more steps than the engine allows is rejected`() {
        val most = RuntimeBudget.Default.maxSteps
        val sieved = ProposedStep(
            tool = ToolId("fixture_sieve"),
            args = mapOf("name" to "lumora"),
            origins = mapOf("name" to ValueOrigin.SaidBy(PrincipalId.USER)),
            risk = ActionRiskLevel.SAFE,
            effect = ToolEffect.EXTERNAL,
        )

        assertEquals(
            "exactly the engine's cap",
            Verdict.Valid(ModelPlanning.Proposed(List(most) { sieved })),
            BridgeValidator.validate(steps(*Array(most) { sieve }), chainOffering, chainRegistry, "открой lumora"),
        )
        assertEquals(
            "one step over it",
            Verdict.Rejected(BridgeError.SHAPE),
            BridgeValidator.validate(steps(*Array(most + 1) { sieve }), chainOffering, chainRegistry, "открой lumora"),
        )
    }
}
