package com.sidr.launcher.feature.launcher.learned

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.sidr.launcher.core.ui.theme.SidrTheme
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A4′ device acceptance 2026-11-21: opening Settings → «Выученные команды» crashed the launcher with
 * `MissingFormatArgumentException: '%2$s'` once the store held «сотри …» — a learned `uninstall_app` whose
 * `app` is a slot and which carries no `app_label`. `PlanStep.line` fell through to `toolLabelFor`, whose
 * resource for `uninstall_app` (and `set_app_alias`) has TWO placeholders, and called it with one. The KDoc
 * promised that fallback «says less» instead of crashing; these tests hold that promise on the screen that
 * broke it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LearnedCommandsScreenStepLineTest {

    @get:Rule val compose = createComposeRule()

    private fun show(form: String, step: PlanStep) {
        compose.setContent {
            SidrTheme {
                LearnedCommandsContent(
                    uiState = LearnedCommandsUiState(
                        commands = listOf(
                            LearnedCommandUiModel(form = form, steps = listOf(step), streak = 3, threshold = 3, pinned = true),
                        ),
                        isLoading = false,
                    ),
                    onBack = {},
                    onRetry = {},
                    onForget = {},
                )
            }
        }
    }

    private fun step(tool: String, vararg args: Pair<String, String>) = PlanStep(
        0,
        ToolInvocation(ToolId(tool), args.associate { (k, v) -> k to ArgSource.Literal(v) }),
        ActionRiskLevel.CONFIRM, StepPrecondition.None, StepRationale.MODEL_PROPOSED,
    )

    @Test
    fun `a learned uninstall whose app is a slot renders one value instead of crashing`() {
        show("сотри ⟨…⟩", step("uninstall_app", "app" to "⟨…⟩"))

        compose.onNodeWithText("Remove ⟨…⟩", substring = true).assertExists()
    }

    @Test
    fun `a learned alias with only one of its two values renders that value instead of crashing`() {
        show("называй ⟨…⟩", step("set_app_alias", "phrase" to "⟨…⟩"))

        compose.onNodeWithText("Set the name “⟨…⟩”", substring = true).assertExists()
    }
}
