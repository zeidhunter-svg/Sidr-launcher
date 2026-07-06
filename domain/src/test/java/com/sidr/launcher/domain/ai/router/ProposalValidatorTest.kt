package com.sidr.launcher.domain.ai.router

import com.sidr.launcher.domain.action.ActionArg
import com.sidr.launcher.domain.action.ActionCatalog
import com.sidr.launcher.domain.action.ActionCategory
import com.sidr.launcher.domain.action.ActionDescriptor
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.action.LauncherAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fail-closed validation core (AIL-4, Fork R3). Proves the router never builds an action from a
 * malformed / hallucinated / schema-violating proposal — every breach collapses to [PlanResult.NoPlan].
 */
class ProposalValidatorTest {

    private val catalog: ActionCatalog = FakeActionCatalogFor(
        ActionDescriptor(
            id = ActionIds.LAUNCH_APP,
            title = "Open app",
            description = "Launch an installed app by name",
            category = ActionCategory.APP,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("query", description = "app name")),
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_URL,
            title = "Open link",
            description = "Open a web address in the browser",
            category = ActionCategory.WEB,
            risk = ActionRiskLevel.CONFIRM,
            argSchema = listOf(ActionArg("url", description = "the address")),
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_SETTINGS,
            title = "Settings",
            description = "Open the launcher's own settings",
            category = ActionCategory.SYSTEM,
            risk = ActionRiskLevel.SAFE,
        ),
        ActionDescriptor(
            id = ActionIds.OPEN_ASSISTANT,
            title = "Assistant",
            description = "Open the assistant, optionally with a prompt",
            category = ActionCategory.ASSISTANT,
            risk = ActionRiskLevel.SAFE,
            argSchema = listOf(ActionArg("prompt", required = false, description = "optional prompt")),
        ),
    )

    @Test
    fun `valid single-arg action becomes RoutedAction`() {
        val result = ProposalValidator.validate(
            ActionProposal(action = "launch_app", args = mapOf("query" to "telegram"), confidence = 0.9f),
            catalog,
        )
        assertEquals(PlanResult.RoutedAction(LauncherAction.LaunchApp("telegram"), 0.9f), result)
    }

    @Test
    fun `zero-arg action becomes RoutedAction`() {
        val result = ProposalValidator.validate(ActionProposal(action = "open_settings"), catalog)
        assertTrue(result is PlanResult.RoutedAction)
        assertEquals(LauncherAction.OpenSettings, (result as PlanResult.RoutedAction).action)
    }

    @Test
    fun `optional arg may be absent`() {
        val result = ProposalValidator.validate(ActionProposal(action = "open_assistant"), catalog)
        assertEquals(LauncherAction.OpenAssistant(null), (result as PlanResult.RoutedAction).action)
    }

    @Test
    fun `optional arg present is carried`() {
        val result = ProposalValidator.validate(
            ActionProposal(action = "open_assistant", args = mapOf("prompt" to "book a flight")),
            catalog,
        )
        assertEquals(LauncherAction.OpenAssistant("book a flight"), (result as PlanResult.RoutedAction).action)
    }

    @Test
    fun `action none is NoPlan`() {
        assertEquals(PlanResult.NoPlan, ProposalValidator.validate(ActionProposal(action = "none"), catalog))
    }

    @Test
    fun `clarify with question is Clarify`() {
        val result = ProposalValidator.validate(
            ActionProposal(action = "clarify", question = "Which browser?"),
            catalog,
        )
        assertEquals(PlanResult.Clarify("Which browser?"), result)
    }

    @Test
    fun `clarify without question is NoPlan`() {
        assertEquals(PlanResult.NoPlan, ProposalValidator.validate(ActionProposal(action = "clarify"), catalog))
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(ActionProposal(action = "clarify", question = "   "), catalog),
        )
    }

    @Test
    fun `unknown action id is NoPlan`() {
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(ActionProposal(action = "format_disk", args = mapOf("path" to "/")), catalog),
        )
    }

    @Test
    fun `action not registered in this catalog is NoPlan`() {
        // web_search is a real ActionId but absent from THIS catalog → must not route.
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(ActionProposal(action = "web_search", args = mapOf("query" to "x")), catalog),
        )
    }

    @Test
    fun `missing required arg is NoPlan`() {
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(ActionProposal(action = "launch_app"), catalog),
        )
    }

    @Test
    fun `blank required arg is NoPlan`() {
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(ActionProposal(action = "launch_app", args = mapOf("query" to "  ")), catalog),
        )
    }

    @Test
    fun `unknown extra arg key fails closed to NoPlan`() {
        assertEquals(
            PlanResult.NoPlan,
            ProposalValidator.validate(
                ActionProposal(action = "launch_app", args = mapOf("query" to "telegram", "flags" to "0x1")),
                catalog,
            ),
        )
    }

    @Test
    fun `absent confidence defaults to advisory 0_5`() {
        val result = ProposalValidator.validate(
            ActionProposal(action = "launch_app", args = mapOf("query" to "maps")),
            catalog,
        )
        assertEquals(0.5f, (result as PlanResult.RoutedAction).confidence)
    }

    @Test
    fun `out-of-range confidence is coerced into 0_1`() {
        val high = ProposalValidator.validate(
            ActionProposal(action = "launch_app", args = mapOf("query" to "maps"), confidence = 4.0f),
            catalog,
        )
        assertEquals(1.0f, (high as PlanResult.RoutedAction).confidence)
        val low = ProposalValidator.validate(
            ActionProposal(action = "launch_app", args = mapOf("query" to "maps"), confidence = -2.0f),
            catalog,
        )
        assertEquals(0.0f, (low as PlanResult.RoutedAction).confidence)
    }

    /** Minimal inline catalog so the validator test does not depend on the data-layer impl. */
    private class FakeActionCatalogFor(vararg descriptors: ActionDescriptor) : ActionCatalog {
        private val list = descriptors.toList()
        override fun all(): List<ActionDescriptor> = list
        override fun descriptor(id: ActionId): ActionDescriptor? = list.firstOrNull { it.id == id }
    }
}
