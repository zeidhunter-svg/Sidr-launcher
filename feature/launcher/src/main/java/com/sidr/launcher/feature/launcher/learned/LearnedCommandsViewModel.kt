package com.sidr.launcher.feature.launcher.learned

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.ObserveLearnedPlansUseCase
import com.sidr.launcher.domain.memory.plan.SLOT_MARK
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolInvocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * One learned command as the screen shows it. [steps] are the template drawn as the session card's own
 * [PlanStep]s, so `PlanStep.line` renders them without a second wording: a slot is [SLOT_MARK], a constant its
 * text, a reference its source step (A4′ phase 4, B-11).
 */
data class LearnedCommandUiModel(
    val form: String,
    val steps: List<PlanStep>,
    val streak: Int,
    val threshold: Int,
    val pinned: Boolean,
)

data class LearnedCommandsUiState(
    val commands: List<LearnedCommandUiModel> = emptyList(),
    val isLoading: Boolean = true,
    /** The store could not be read; [LearnedCommandsViewModel.retry] reads it again. */
    val failed: Boolean = false,
)

/**
 * The learned-plan cache made visible and deletable (`DOC-AML-5`, spec §8). Mirrors `LearnedChoicesViewModel`:
 * a reload signal restarts the observation, an unreadable store becomes [LearnedCommandsUiState.failed] — never
 * a crash. A failed forget leaves the row (the store still holds it), so the list tells the truth.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LearnedCommandsViewModel @Inject constructor(
    private val observeLearnedPlans: ObserveLearnedPlansUseCase,
    private val forgetLearnedPlan: ForgetLearnedPlanUseCase,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val reloadSignal = MutableStateFlow(0)

    val uiState: StateFlow<LearnedCommandsUiState> = reloadSignal
        .flatMapLatest { observeCommands() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = LearnedCommandsUiState(),
        )

    fun retry() {
        reloadSignal.value = reloadSignal.value + 1
    }

    fun onForget(form: String) {
        viewModelScope.launch(ioDispatcher) {
            try {
                forgetLearnedPlan.forget(CommandForm.parse(form))
            } catch (e: CancellationException) {
                throw e
            } catch (_: RuntimeException) {
                // Best-effort management action: the row stays, and the list says so.
            }
        }
    }

    private fun observeCommands(): Flow<LearnedCommandsUiState> =
        observeLearnedPlans.observe()
            .map { plans ->
                LearnedCommandsUiState(
                    commands = plans.sortedByDescending { it.confirmedAtEpochMs }.map { it.toUiModel() },
                    isLoading = false,
                )
            }
            .onStart { emit(LearnedCommandsUiState(isLoading = true)) }
            .catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(LearnedCommandsUiState(isLoading = false, failed = true))
            }
}

private fun LearnedPlan.toUiModel(): LearnedCommandUiModel = LearnedCommandUiModel(
    form = shape.form.key,
    steps = shape.template.steps.mapIndexed { index, step ->
        PlanStep(
            index = index,
            invocation = ToolInvocation(
                id = step.tool,
                args = step.args.mapValues { (_, arg) ->
                    when (arg) {
                        is TemplateArg.Slot -> ArgSource.Literal(SLOT_MARK)
                        is TemplateArg.Constant -> ArgSource.Literal(arg.text)
                        is TemplateArg.Ref -> ArgSource.FromStep(arg.step, arg.key)
                    }
                },
            ),
            // Display only: the card's line reads neither; today's registry decides the real risk (B-4).
            risk = ActionRiskLevel.SAFE,
            precondition = StepPrecondition.None,
            rationale = StepRationale.MODEL_PROPOSED,
        )
    },
    streak = streak,
    threshold = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD,
    pinned = pinned,
)
