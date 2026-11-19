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

    private fun formOf(n: Int) = "row $n"

    /**
     * B-9 (Android's `LearnedPlanStoreImplTest` scenario): unpinned rows go first — the lowest streak, then the
     * oldest confirmation — so the oldest row (streak 2) outlives a younger streak-1 row; with only pinned rows,
     * the oldest confirmation goes whatever its streak, and the row just written is never the victim.
     */
    @Test
    fun `the cap evicts unpinned rows first, the lowest streak then the oldest`() = runTest {
        val store = JvmLearnedPlanStore(file)
        store.upsert(plan(formOf(0), streak = 2, at = 0L))
        store.upsert(plan(formOf(1), streak = 1, at = 1L))
        for (n in 2 until MAX_LEARNED_PLANS) store.upsert(plan(formOf(n), streak = 1, at = n.toLong()))
        assertEquals(MAX_LEARNED_PLANS, keys(store).size)

        store.upsert(plan(formOf(MAX_LEARNED_PLANS), streak = 1, at = 10_000L))

        val kept = keys(store)
        assertEquals(MAX_LEARNED_PLANS, kept.size)
        assertTrue("the lowest-streak, oldest unpinned row goes", formOf(1) !in kept)
        assertTrue("an older row with a higher streak stays", formOf(0) in kept)
        assertTrue("the row just written stays", formOf(MAX_LEARNED_PLANS) in kept)

        // Only pinned rows: the oldest confirmation goes — not the lowest streak — and a fresh streak-1 write
        // is not evicted by its own write.
        Files.delete(file)
        store.upsert(plan("pinned oldest high", streak = 9, at = 0L))
        store.upsert(plan("pinned younger", streak = 3, at = 1L))
        for (n in 2 until MAX_LEARNED_PLANS) store.upsert(plan("pinned $n", streak = 3, at = n.toLong()))
        store.upsert(plan("fresh unpinned", streak = 1, at = 20_000L))

        val kept2 = keys(store)
        assertEquals(MAX_LEARNED_PLANS, kept2.size)
        assertTrue("the row just written stays, though it is the one unpinned row", "fresh unpinned" in kept2)
        assertTrue("the oldest pinned row goes, whatever its streak", "pinned oldest high" !in kept2)
        assertTrue("pinned younger" in kept2)
    }

    private suspend fun keys(store: JvmLearnedPlanStore): List<String> =
        (store.all() as OperationResult.Success).value.map { it.shape.form.key }
}
