package com.sidr.launcher.domain.ai.bridge

import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.answer
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.offering
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.plan
import com.sidr.launcher.domain.ai.bridge.BridgeFixtures.registry
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

    @Test
    fun `a plan over an offered tool is valid, with its risk and effect from the registry`() {
        val verdict = validate(plan("launch_app", "app" to "lumora", "query" to null, "url" to null))

        assertEquals(
            Verdict.Valid(
                ModelPlanning.Proposed(
                    ProposedStep(
                        tool = ToolId("launch_app"),
                        args = mapOf("app" to "lumora"),
                        origins = mapOf("app" to ValueOrigin.SaidBy(PrincipalId.USER)),
                        risk = ActionRiskLevel.SAFE,
                        effect = ToolEffect.EXTERNAL,
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

    /** Criterion 16, first half: an app argument the user did not type is not the user's app. */
    @Test
    fun `an app mention the user did not type is rejected as underivable`() {
        assertEquals(
            Verdict.Rejected(BridgeError.UNDERIVABLE_MENTION),
            validate(plan("launch_app", "app" to "lumora"), command = "открой мой мессенджер"),
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
    }

    /** The minimum of labels (spec §7.12.1): a search text the user did not type is the model's. */
    @Test
    fun `a search text the user did not type is labelled the model's`() {
        val verdict = validate(
            plan("web_search", "query" to "ostravel lectures by experts"),
            command = "найди лекции ostravel",
        ) as Verdict.Valid
        val step = (verdict.planning as ModelPlanning.Proposed).step

        assertEquals(mapOf("query" to ValueOrigin.ModelGenerated), step.origins)
    }
}
