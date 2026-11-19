package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.LearnedPlanDao
import com.sidr.launcher.data.repository.db.entity.LearnedPlanEntity
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.FormPart
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.MAX_LEARNED_PLANS
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.memory.resolution.DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Room learned-plan store (A4′ phase 4, plan §0.2 B-9) over an in-memory database: a template round-trips,
 * the cap evicts in B-9's order, an unreadable row is deleted when it is met, and the flow follows the table.
 * Forms and tools are invented fixtures (R14-43); assertions compare by equality.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LearnedPlanStoreImplTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: LearnedPlanDao
    private lateinit var store: LearnedPlanStoreImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.learnedPlanDao()
        store = LearnedPlanStoreImpl(dao, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** A one-word form with a slot — «w<n> …» — distinct per [n]. */
    private fun form(n: Int) = CommandForm(listOf(FormPart.Word("w$n"), FormPart.Slot))

    private val searchTemplate = PlanTemplate(listOf(TemplateStep(ToolId("web_search"), mapOf("query" to TemplateArg.Slot(0)))))

    private fun plan(form: CommandForm, streak: Int, confirmedAt: Long, template: PlanTemplate = searchTemplate) =
        LearnedPlan(LearnedPlanShape(form, template), streak = streak, confirmations = streak, confirmedAtEpochMs = confirmedAt)

    private suspend fun present(form: CommandForm): Boolean = dao.find(form.key) != null

    @Test
    fun `a learned plan round-trips with its template`() = runTest {
        val form = CommandForm.parse("поищи … и открой …")
        val template = PlanTemplate(
            listOf(
                TemplateStep(ToolId("web_search"), mapOf("query" to TemplateArg.Slot(0))),
                TemplateStep(ToolId("set_alarm"), mapOf("time" to TemplateArg.Constant("6:00"))),
                TemplateStep(ToolId("open_url"), mapOf("url" to TemplateArg.Ref(step = 0, key = "first_result"), "label" to TemplateArg.Slot(1))),
            ),
        )
        val learned = LearnedPlan(LearnedPlanShape(form, template), streak = 2, confirmations = 5, confirmedAtEpochMs = 1_234L)

        assertEquals(OperationResult.Success(Unit), store.upsert(learned))

        assertEquals(OperationResult.Success(learned), store.find(form))
        assertEquals(OperationResult.Success(listOf(learned)), store.all())
        assertEquals(OperationResult.Success(null), store.find(CommandForm.parse("поищи …")))
    }

    /**
     * B-9, review S1: unpinned rows go first — the lowest streak, then the oldest confirmation — so the oldest row
     * here (streak 2) outlives a younger streak-1 row, which a «least recently confirmed» cap would not do. Then,
     * with only pinned rows left, a pinned row goes — the oldest confirmation, whatever its streak — and never the
     * row just written.
     */
    @Test
    fun `past the cap an unpinned row goes first, then the oldest confirmation`() = runTest {
        val oldestStreakTwo = form(0)
        val lowestOldest = form(1)
        store.upsert(plan(oldestStreakTwo, streak = 2, confirmedAt = 0L))
        store.upsert(plan(lowestOldest, streak = 1, confirmedAt = 1L))
        (2 until MAX_LEARNED_PLANS).forEach { n -> store.upsert(plan(form(n), streak = 1, confirmedAt = n.toLong())) }
        assertEquals(MAX_LEARNED_PLANS, dao.count())

        val fresh = form(MAX_LEARNED_PLANS)
        store.upsert(plan(fresh, streak = 1, confirmedAt = 10_000L))

        assertEquals(MAX_LEARNED_PLANS, dao.count())
        assertEquals("the lowest-streak, oldest unpinned row goes", false, present(lowestOldest))
        assertEquals("an older row with a higher streak stays", true, present(oldestStreakTwo))
        assertEquals("the row just written stays", true, present(fresh))

        // Only pinned rows: the oldest confirmation goes first — not the lowest streak — and a fresh streak-1
        // reading is not evicted by its own write.
        dao.all().forEach { dao.delete(it.form) }
        val pinnedOldestHighStreak = form(1_000)
        val pinnedYoungerAtThreshold = form(1_001)
        store.upsert(plan(pinnedOldestHighStreak, streak = 9, confirmedAt = 0L))
        store.upsert(plan(pinnedYoungerAtThreshold, streak = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD, confirmedAt = 1L))
        (2 until MAX_LEARNED_PLANS).forEach { n ->
            store.upsert(plan(form(1_000 + n), streak = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD, confirmedAt = n.toLong()))
        }
        val freshUnpinned = form(5_000)
        store.upsert(plan(freshUnpinned, streak = 1, confirmedAt = 20_000L))

        assertEquals(MAX_LEARNED_PLANS, dao.count())
        assertEquals("the row just written stays, though it is the one unpinned row", true, present(freshUnpinned))
        assertEquals("the oldest pinned row goes, whatever its streak", false, present(pinnedOldestHighStreak))
        assertEquals(true, present(pinnedYoungerAtThreshold))
    }

    /** B-9, review S1: a command used every day keeps its third confirmation's time (a replay writes nothing). */
    @Test
    fun `a pinned command survives 200 one-off readings`() = runTest {
        val pinned = plan(CommandForm.parse("сотри …"), streak = DEFAULT_AUTO_RESOLVE_STREAK_THRESHOLD, confirmedAt = 0L)
        store.upsert(pinned)

        (1..MAX_LEARNED_PLANS).forEach { n -> store.upsert(plan(form(n), streak = 1, confirmedAt = n.toLong())) }

        assertEquals(MAX_LEARNED_PLANS, dao.count())
        assertEquals(OperationResult.Success(pinned), store.find(pinned.shape.form))
    }

    /**
     * B-9, review M3: a row whose template cannot be read can never replay and the screen could not show it
     * (`DOC-AML-5`), so whichever read meets it — `find`, `all`, `observeAll` — deletes it and leaves it out.
     */
    @Test
    fun `a row whose template cannot be read is deleted when it is met`() = runTest {
        val readable = plan(CommandForm.parse("поищи …"), streak = 1, confirmedAt = 5L)
        store.upsert(readable)
        val metByFind = CommandForm.parse("сотри …")
        val metByAll = CommandForm.parse("открой …")
        dao.upsert(LearnedPlanEntity(metByFind.key, templateJson = "{", streak = 3, confirmations = 3, confirmedAtEpochMs = 1L))

        assertEquals(OperationResult.Success(null), store.find(metByFind))
        assertNull("find deleted the row it could not read", dao.find(metByFind.key))

        dao.upsert(LearnedPlanEntity(metByAll.key, templateJson = "{", streak = 3, confirmations = 3, confirmedAtEpochMs = 2L))
        assertEquals(OperationResult.Success(listOf(readable)), store.all())
        assertNull("all() deleted the row it could not read", dao.find(metByAll.key))

        val metByObserve = CommandForm.parse("найди …")
        dao.upsert(LearnedPlanEntity(metByObserve.key, templateJson = "{", streak = 3, confirmations = 3, confirmedAtEpochMs = 3L))
        assertEquals(listOf(readable), store.observeAll().first())
        assertNull("observeAll deleted the row it could not read", dao.find(metByObserve.key))
    }

    @Test
    fun `observeAll follows upsert and delete`() = runTest {
        val first = plan(CommandForm.parse("поищи …"), streak = 1, confirmedAt = 1L)
        val second = plan(CommandForm.parse("сотри …"), streak = 3, confirmedAt = 2L)
        val flow = store.observeAll()

        assertEquals(emptyList<LearnedPlan>(), flow.first())
        store.upsert(first)
        store.upsert(second)
        assertEquals(listOf(second, first), flow.first())
        store.delete(first.shape.form)
        assertEquals(listOf(second), flow.first())
    }
}
