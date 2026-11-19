package com.sidr.launcher.domain.memory.plan

import com.sidr.launcher.core.testing.FakeLearnedPlanStore
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LearnedPlanUseCasesTest {
    private val store = FakeLearnedPlanStore()
    private val record = RecordLearnedPlanUseCase(store)
    private val form = CommandForm(listOf(FormPart.Word("сотри"), FormPart.Slot))
    private fun template(tool: String) =
        PlanTemplate(listOf(TemplateStep(ToolId(tool), mapOf("app" to TemplateArg.Slot(0)))))
    private val shape = LearnedPlanShape(form, template("uninstall_app"))

    @Test fun `the first confirmation is a streak of one`() = runTest {
        assertTrue(record.record(shape, 10) is OperationResult.Success)
        assertEquals(LearnedPlan(shape, 1, 1, 10), store.rows[form.key])
        assertFalse(store.rows.getValue(form.key).pinned)
    }

    @Test fun `the same form and template count up and pin at the threshold`() = runTest {
        record.record(shape, 10); record.record(shape, 20); record.record(shape, 30)
        val plan = store.rows.getValue(form.key)
        assertEquals(3, plan.streak)
        assertEquals(3, plan.confirmations)
        assertEquals(30L, plan.confirmedAtEpochMs)
        assertTrue(plan.pinned)
    }

    @Test fun `another template for the same form starts over and is kept`() = runTest {
        val other = LearnedPlanShape(form, template("open_app_info"))
        record.record(shape, 10); record.record(shape, 20)
        record.record(other, 30)
        val plan = store.rows.getValue(form.key)
        assertEquals(1, plan.streak)
        assertEquals(3, plan.confirmations)
        assertEquals(other.template, plan.shape.template)
    }

    @Test fun `a store failure is returned, never thrown`() = runTest {
        val failing = FakeLearnedPlanStore(failReads = true)
        assertTrue(RecordLearnedPlanUseCase(failing).record(shape, 10) is OperationResult.Failure)
        assertTrue(failing.rows.isEmpty())
    }

    @Test fun `forget deletes the form and observe lists what is stored`() = runTest {
        record.record(shape, 10)
        val observe = ObserveLearnedPlansUseCase(store)
        assertEquals(listOf(LearnedPlan(shape, 1, 1, 10)), observe.observe().first())
        assertTrue(ForgetLearnedPlanUseCase(store).forget(form) is OperationResult.Success)
        assertTrue(store.rows.isEmpty())
        assertEquals(emptyList<LearnedPlan>(), observe.observe().first())
    }
}
