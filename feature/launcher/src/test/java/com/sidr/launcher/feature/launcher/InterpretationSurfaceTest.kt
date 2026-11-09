package com.sidr.launcher.feature.launcher

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.AgentGoal
import com.sidr.launcher.domain.agent.GoalShape
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.ai.bridge.AppChoice
import com.sidr.launcher.domain.ai.bridge.Interpretation
import com.sidr.launcher.domain.ai.bridge.PrincipalId
import com.sidr.launcher.domain.ai.bridge.SidrDestination
import com.sidr.launcher.domain.ai.bridge.ValueOrigin
import com.sidr.launcher.domain.ai.bridge.choose
import com.sidr.launcher.domain.model.InstalledApp
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolEffect
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import com.sidr.launcher.domain.tool.ToolLevels
import com.sidr.launcher.feature.launcher.agent.StepProvenance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The interpretation card (spec §7.12.3 condition 1; `DOC-NYH-2`, `DOC-HYA-2`, `DOC-MZN-2`), rendered
 * with real resources. Every expected sentence is whole and exact; the fixture values share no
 * substring with each other or with the sentences around them (R14-43).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InterpretationSurfaceTest {

    @get:Rule val compose = createComposeRule()

    private var runs = 0
    private var dismissals = 0
    private val picks = mutableListOf<String>()

    private fun act(
        tool: String,
        arg: String,
        value: String,
        origin: ValueOrigin,
        choice: AppChoice? = null,
        risk: ActionRiskLevel = ActionRiskLevel.SAFE,
    ) =
        Interpretation.Act(
            goal = AgentGoal("fixture goal", GoalShape.Free("fixture goal")),
            step = PlanStep(
                index = 0,
                invocation = ToolInvocation(ToolId(tool), mapOf(arg to ArgSource.Literal(value))),
                risk = risk,
                precondition = StepPrecondition.None,
                rationale = StepRationale.MODEL_PROPOSED,
            ),
            origins = mapOf(arg to origin),
            effect = ToolEffect.EXTERNAL,
            choice = choice,
        )

    private val twoAppsSharingALabel = AppChoice(
        arg = "app",
        mention = "kelmora",
        candidates = listOf(
            InstalledApp(packageName = "net.fixture.quarzel", label = "Pellix"),
            InstalledApp(packageName = "net.fixture.ondrami", label = "Pellix"),
        ),
        learning = null,
    )

    private fun render(interpretation: Interpretation, provenance: Map<ToolId, StepProvenance> = emptyMap()) {
        compose.setContent {
            SidrTheme {
                InterpretationArea(
                    interpretation = interpretation,
                    provenance = provenance,
                    dynamicLabels = emptyMap(),
                    onRun = { runs++ },
                    onDismiss = { dismissals++ },
                    onChoose = { picks += it },
                )
            }
        }
    }

    /** `DOC-NYH-2` and `DOC-HYA-2`: the reading is visible before anything runs, and only Run runs it. */
    @Test
    fun `the card names the step and runs nothing until Run`() {
        render(act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)))

        compose.onNodeWithText("Here is how I understood it").assertExists()
        compose.onNodeWithText("Search the web: ostravel lectures").assertExists()
        assertEquals(0, runs)

        compose.onNodeWithText("Run").performClick()
        assertEquals(1, runs)
    }

    /** `DOC-MZN-2`: an address the model wrote, on a step whose effect leaves the phone, is shown as written. */
    @Test
    fun `a model-generated address on an external step is shown verbatim, marked as the model's`() {
        render(act("open_url", "url", "https://quillmere.example/path?q=a&b=c", ValueOrigin.ModelGenerated))

        compose.onNodeWithText("Suggested by the model: https://quillmere.example/path?q=a&b=c").assertExists()
    }

    @Test
    fun `a value the user said carries no model mark`() {
        render(act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)))

        compose.onNodeWithText("Suggested by the model", substring = true).assertDoesNotExist()
    }

    /** Condition 2: the list names label AND package — two apps can share a label — and a tap binds that one. */
    @Test
    fun `the pick list shows each candidate's label and package, and a tap binds that one`() {
        render(act("launch_app", "query", "kelmora", ValueOrigin.SaidBy(PrincipalId.USER), twoAppsSharingALabel))

        compose.onNodeWithText("Which app is \"kelmora\"?").assertExists()
        compose.onNodeWithText("Pellix (net.fixture.ondrami)").performClick()

        assertEquals(listOf("net.fixture.ondrami"), picks)
        assertEquals(0, runs)
    }

    /**
     * Fork 3b-G: a word form is offered for confirmation as one app — the title asks «did you mean» with
     * that app's label instead of «which app», and the tap binds that one exactly as the pick list does.
     */
    @Test
    fun `a word-form pick asks did-you-mean with the one app`() {
        val form = AppChoice(
            arg = "app",
            mention = "Vemblic'i",
            candidates = listOf(InstalledApp(packageName = "net.fixture.vemblic", label = "Vemblic")),
            learning = null,
            confirm = true,
        )
        render(act("launch_app", "query", "Vemblic'i", ValueOrigin.SaidBy(PrincipalId.USER), form))

        compose.onNodeWithText("Did you mean «Vemblic»?").assertExists()
        compose.onNodeWithText("Which app is \"Vemblic'i\"?").assertDoesNotExist()
        compose.onNodeWithText("Vemblic (net.fixture.vemblic)").performClick()

        assertEquals(listOf("net.fixture.vemblic"), picks)
        assertEquals(0, runs)
    }

    @Test
    fun `the wait can be cancelled`() {
        var cancelled = 0
        compose.setContent { SidrTheme { ThinkingArea(onCancel = { cancelled++ }) } }

        compose.onNodeWithText("Working out what you meant…").assertExists()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun `a navigation reading names Sidr's screen`() {
        render(Interpretation.Navigate(SidrDestination.LAUNCHER_SETTINGS))

        compose.onNodeWithText("Open Sidr settings").assertExists()
    }

    /** T1: «Cancel» on the card calls `onDismiss` once and runs nothing. */
    @Test
    fun `Cancel on the card dismisses it once and runs nothing`() {
        render(act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)))

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissals)
        assertEquals(0, runs)
    }

    /** T1b: the same on the pick list, which has its own «Cancel» — and a cancel binds nothing. */
    @Test
    fun `Cancel on the pick list dismisses it once and binds nothing`() {
        render(act("launch_app", "query", "kelmora", ValueOrigin.SaidBy(PrincipalId.USER), twoAppsSharingALabel))

        compose.onNodeWithText("Cancel").performClick()

        assertEquals(1, dismissals)
        assertEquals(emptyList<String>(), picks)
        assertEquals(0, runs)
    }

    /** T2: a navigation reading is run by the same «Run», exactly once. */
    @Test
    fun `Run on a navigation reading calls onRun once`() {
        render(Interpretation.Navigate(SidrDestination.LAUNCHER_SETTINGS))

        compose.onNodeWithText("Run").performClick()

        assertEquals(1, runs)
        assertEquals(0, dismissals)
    }

    /** T3: the other destination has its own sentence, compared with the resolved resource by equality. */
    @Test
    fun `a navigation reading of the app grid names the grid`() {
        render(Interpretation.Navigate(SidrDestination.APP_GRID))

        val sentence = RuntimeEnvironment.getApplication().getString(R.string.launcher_interpretation_show_apps)
        compose.onNodeWithText(sentence).assertExists()
        compose.onNodeWithText(RuntimeEnvironment.getApplication().getString(R.string.launcher_interpretation_open_settings))
            .assertDoesNotExist()
    }

    /**
     * T5: after a pick the package is the DEVICE's determination ([ValueOrigin.Ours]) — built here through
     * the real `choose()` — and carries no model mark, though the step is `EXTERNAL`.
     */
    @Test
    fun `a package bound by a pick carries no model mark`() {
        val picked = requireNotNull(
            act("launch_app", "query", "kelmora", ValueOrigin.SaidBy(PrincipalId.USER), twoAppsSharingALabel)
                .choose("net.fixture.ondrami"),
        )
        render(picked)

        compose.onNodeWithText("Open kelmora (net.fixture.ondrami)").assertExists()
        compose.onNodeWithText("Suggested by the model", substring = true).assertDoesNotExist()
    }

    /** T6: a step that needs consent is drawn with the CONFIRM tone, not the SAFE one. */
    @Test
    fun `a step that needs consent is drawn with the confirm tone`() {
        render(act("play_store_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER), risk = ActionRiskLevel.CONFIRM))

        compose.onNodeWithText("CONFIRM").assertExists()
        compose.onNodeWithText("SAFE").assertDoesNotExist()
    }

    /** T7: where the step's effect goes is disclosed on the card (`DOC-ILM-2`) — the same line the plan list draws. */
    @Test
    fun `the card discloses where the step's effect goes`() {
        render(
            act("web_search", "query", "ostravel lectures", ValueOrigin.SaidBy(PrincipalId.USER)),
            provenance = mapOf(ToolId("web_search") to StepProvenance(ToolLevels.IN_APP, ToolEffect.EXTERNAL)),
        )

        compose.onNodeWithContentDescription("source SIDR · EXTERNAL").assertExists()
    }
}
