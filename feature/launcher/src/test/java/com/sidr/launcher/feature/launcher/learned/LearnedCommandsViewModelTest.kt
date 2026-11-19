package com.sidr.launcher.feature.launcher.learned

import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.domain.action.ActionRiskLevel
import com.sidr.launcher.domain.agent.PlanStep
import com.sidr.launcher.domain.agent.StepPrecondition
import com.sidr.launcher.domain.agent.StepRationale
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.ForgetLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.ObserveLearnedPlansUseCase
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.SLOT_MARK
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.tool.ArgSource
import com.sidr.launcher.domain.tool.ToolId
import com.sidr.launcher.domain.tool.ToolInvocation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LearnedCommandsViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val store = FakeLearnedPlanStore()

    @Before
    fun setUp() = Dispatchers.setMain(testDispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun buildViewModel() = LearnedCommandsViewModel(
        observeLearnedPlans = ObserveLearnedPlansUseCase(store),
        forgetLearnedPlan = ForgetLearnedPlanUseCase(store),
        ioDispatcher = testDispatcher,
    )

    private val erase = ToolId("uninstall_app")
    private val open = ToolId("launch_app")

    private fun plan(key: String, steps: List<TemplateStep>, streak: Int, at: Long) = LearnedPlan(
        shape = LearnedPlanShape(CommandForm.parse(key), PlanTemplate(steps)),
        streak = streak,
        confirmations = streak,
        confirmedAtEpochMs = at,
    )

    private val slotPlan = plan(
        "сотри $SLOT_MARK",
        listOf(TemplateStep(erase, mapOf("app" to TemplateArg.Slot(0)))),
        streak = 3,
        at = 20L,
    )
    private val twoStepPlan = plan(
        "открой банк",
        listOf(
            TemplateStep(open, mapOf("query" to TemplateArg.Constant("банк"))),
            TemplateStep(erase, mapOf("app" to TemplateArg.Ref(0, "package"))),
        ),
        streak = 2,
        at = 10L,
    )

    private fun step(index: Int, tool: ToolId, args: Map<String, ArgSource>) = PlanStep(
        index = index,
        invocation = ToolInvocation(tool, args),
        risk = ActionRiskLevel.SAFE,
        precondition = StepPrecondition.None,
        rationale = StepRationale.MODEL_PROPOSED,
    )

    @Test
    fun `every stored command is listed with its form, its steps and its streak`() = runTest(testDispatcher) {
        store.upsert(twoStepPlan)
        store.upsert(slotPlan)
        val vm = buildViewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.failed)
        // Most recently confirmed first.
        assertEquals(listOf("сотри $SLOT_MARK", "открой банк"), state.commands.map { it.form })
        val slot = state.commands[0]
        assertEquals(listOf(step(0, erase, mapOf("app" to ArgSource.Literal(SLOT_MARK)))), slot.steps)
        assertEquals(3, slot.streak)
        assertEquals(DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD, slot.threshold)
        assertTrue(slot.pinned)
        val two = state.commands[1]
        assertEquals(
            listOf(
                step(0, open, mapOf("query" to ArgSource.Literal("банк"))),
                step(1, erase, mapOf("app" to ArgSource.FromStep(0, "package"))),
            ),
            two.steps,
        )
        assertEquals(2, two.streak)
        assertFalse(two.pinned)
    }

    @Test
    fun `forget deletes the command`() = runTest(testDispatcher) {
        store.upsert(slotPlan)
        store.upsert(twoStepPlan)
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.onForget("сотри $SLOT_MARK")
        advanceUntilIdle()

        assertEquals(setOf("открой банк"), store.rows.keys)
        assertEquals(listOf("открой банк"), vm.uiState.value.commands.map { it.form })
    }

    @Test
    fun `an unreadable store says so and offers a retry`() = runTest(testDispatcher) {
        store.upsert(slotPlan)
        store.failReads = true
        val vm = buildViewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.failed)
        assertFalse(vm.uiState.value.isLoading)
        assertTrue(vm.uiState.value.commands.isEmpty())

        store.failReads = false
        vm.retry()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.failed)
        assertEquals(listOf("сотри $SLOT_MARK"), vm.uiState.value.commands.map { it.form })
    }
}
