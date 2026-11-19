package com.sidr.launcher.consumer.jvm.store

import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.MAX_LEARNED_PLANS
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.RecordLearnedPlanUseCase
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files

class JvmLearnedPlanStoreTest {

    @get:Rule val temp = TemporaryFolder()

    private val file get() = temp.root.toPath().resolve(".sidr-agent/learned-plans.json")

    private fun shape(key: String, tool: String = "find_file") = LearnedPlanShape(
        form = CommandForm.parse(key),
        template = PlanTemplate(
            listOf(
                TemplateStep(ToolId(tool), mapOf("query" to TemplateArg.Slot(0), "root" to TemplateArg.Constant("x"))),
                TemplateStep(ToolId("delete_file"), mapOf("path" to TemplateArg.Ref(0, "resolved_path"))),
            ),
        ),
    )

    private fun plan(key: String, streak: Int = 1, at: Long = 1_000L, tool: String = "find_file") =
        LearnedPlan(shape(key, tool), streak = streak, confirmations = streak, confirmedAtEpochMs = at)

    @Test
    fun `a learned plan survives a new store over the same file`() = runTest {
        val written = plan("find … and delete it", streak = 3, at = 42L)
        assertEquals(OperationResult.Success(Unit), JvmLearnedPlanStore(file).upsert(written))

        val reopened = JvmLearnedPlanStore(file)

        assertEquals(OperationResult.Success(listOf(written)), reopened.all())
        assertEquals(OperationResult.Success<LearnedPlan?>(written), reopened.find(written.shape.form))
        assertEquals(listOf(written), reopened.observeAll().first())
    }

    @Test
    fun `an unreadable file is a failure, not an empty memory`() = runTest {
        Files.createDirectories(file.parent)
        Files.writeString(file, "this is { not json")
        val store = JvmLearnedPlanStore(file)

        assertTrue("all() on garbage: ${store.all()}", store.all() is OperationResult.Failure)
        assertTrue(store.find(CommandForm.parse("find … and delete it")) is OperationResult.Failure)
        // A record on top of an unreadable memory is refused, never written over it.
        assertTrue(RecordLearnedPlanUseCase(store).record(shape("find … and delete it"), 5L) is OperationResult.Failure)
        assertTrue(store.upsert(plan("find … and delete it")) is OperationResult.Failure)
        assertEquals("the file is left as it is", "this is { not json", Files.readString(file))
    }

    @Test
    fun `a row whose template cannot be read is deleted when met`() = runTest {
        val store = JvmLearnedPlanStore(file)
        store.upsert(plan("find … and delete it"))
        val text = Files.readString(file).replace("\"slot\"", "\"nonsense\"")
        Files.writeString(file, text)

        assertEquals(OperationResult.Success(emptyList<LearnedPlan>()), store.all())
        assertTrue("the unreadable row is gone from the file", !Files.readString(file).contains("find … and delete it"))
    }

    @Test
    fun `an upsert of a known form replaces its row`() = runTest {
        val store = JvmLearnedPlanStore(file)
        store.upsert(plan("find … and delete it", streak = 1))
        store.upsert(plan("find … and delete it", streak = 2, at = 9L))
        store.delete(CommandForm.parse("no such form"))

        assertEquals(OperationResult.Success(listOf(plan("find … and delete it", streak = 2, at = 9L))), store.all())
        store.delete(CommandForm.parse("find … and delete it"))
        assertEquals(OperationResult.Success(emptyList<LearnedPlan>()), store.all())
    }

    @Test
    fun `the cap evicts unpinned rows first, the lowest streak then the oldest`() = runTest {
        val store = JvmLearnedPlanStore(file)
        store.upsert(plan("pinned old", streak = 3, at = 1L))
        for (i in 2..MAX_LEARNED_PLANS) store.upsert(plan("row $i", streak = 1, at = 100L + i))
        store.upsert(plan("row two", streak = 2, at = 5L))
        // 201 rows: the unpinned streak-1 row with the oldest confirmation goes, not the old pinned one.
        val kept = (store.all() as OperationResult.Success).value.map { it.shape.form.key }
        assertEquals(MAX_LEARNED_PLANS, kept.size)
        assertTrue("pinned row kept", "pinned old" in kept)
        assertTrue("the row just written kept", "row two" in kept)
        assertTrue("the oldest unpinned streak-1 row evicted", "row 2" !in kept)
    }
}
