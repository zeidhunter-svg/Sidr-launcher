package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import com.sidr.launcher.data.repository.db.migrations.Migration4To5
import com.sidr.launcher.domain.memory.plan.CommandForm
import com.sidr.launcher.domain.memory.plan.LearnedPlan
import com.sidr.launcher.domain.memory.plan.LearnedPlanShape
import com.sidr.launcher.domain.memory.plan.PlanTemplate
import com.sidr.launcher.domain.memory.plan.TemplateArg
import com.sidr.launcher.domain.memory.plan.TemplateStep
import com.sidr.launcher.domain.result.OperationResult
import com.sidr.launcher.domain.tool.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Schema 4 → 5 inside the JVM gate (A4′ phase 4, plan §0.2 B-14). [MigrationTestHelper] creates a version-4
 * database from the committed `schemas/…/4.json` (its identity hash in `room_master_table`, `user_version = 4`),
 * every pre-existing table gets one row, [Migration4To5] runs and the helper validates the result against
 * `5.json` — then the production [SidrDatabase] opens the file through `addMigrations(Migration4To5)`. That
 * file is already at version 5, so the migration does NOT run there: Room checks only the identity hash, and the
 * every-table check is the helper's (`runMigrationsAndValidate`) — the open proves the production class reads the
 * migrated file, and every row is read back. The instrumented `MigrationTest`
 * carries `v4_to_v5` and `v1_to_v5` for the device; this one is the gate's.
 *
 * Fixture rows are invented (R14-43); they are compared by equality, table by table, before and after.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class Migration4To5Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SidrDatabase::class.java,
    )

    private val preference = ResolutionPreferenceEntity(
        actionId = "launch_app",
        query = "tessar",
        contextKey = "none",
        preferredTargetType = "app",
        preferredTargetValue = "net.fixture.tessar.two",
        streak = 3,
        totalChoices = 4,
        lastChosenAtEpochMs = 4_000L,
        learnedInFingerprint = "app:net.fixture.tessar.one|app:net.fixture.tessar.two",
    )
    private val alias = AliasEntity(phrase = "телега", targetType = "app", targetPackage = "net.fixture.zantor", createdAt = 5_000L)

    @Test
    fun `a version-4 database with rows migrates to 5, keeps every row and opens`() = runTest {
        val before = helper.createDatabase(TEST_DB, 4).use { v4 ->
            v4.seed()
            PRE_EXISTING_TABLES.associateWith { v4.rowsOf(it) }
        }
        PRE_EXISTING_TABLES.forEach { table -> assertEquals("$table must be seeded", 1, before.getValue(table).size) }

        helper.runMigrationsAndValidate(TEST_DB, 5, true, Migration4To5).use { v5 ->
            PRE_EXISTING_TABLES.forEach { table -> assertEquals("$table after the migration", before.getValue(table), v5.rowsOf(table)) }
            assertEquals(emptyList<List<String?>>(), v5.rowsOf("learned_plans"))
        }

        val context = ApplicationProvider.getApplicationContext<Application>()
        val room = Room.databaseBuilder(context, SidrDatabase::class.java, TEST_DB)
            .addMigrations(Migration4To5)
            .allowMainThreadQueries()
            .build()
        helper.closeWhenFinished(room)

        assertEquals(preference, room.resolutionPreferenceDao().findByKey("launch_app", "tessar", "none"))
        assertEquals(alias, room.aliasDao().findByPhrase("телега"))
        PRE_EXISTING_TABLES.forEach { table ->
            assertEquals("$table read through Room", before.getValue(table), room.openHelper.readableDatabase.rowsOf(table))
        }

        val store = LearnedPlanStoreImpl(room.learnedPlanDao(), UnconfinedTestDispatcher(testScheduler))
        assertEquals(OperationResult.Success(emptyList<LearnedPlan>()), store.all())
        val learned = LearnedPlan(
            LearnedPlanShape(
                CommandForm.parse("сотри …"),
                PlanTemplate(listOf(TemplateStep(ToolId("uninstall_app"), mapOf("app" to TemplateArg.Slot(0))))),
            ),
            streak = 1,
            confirmations = 1,
            confirmedAtEpochMs = 6_000L,
        )
        assertEquals(OperationResult.Success(Unit), store.upsert(learned))
        assertEquals(OperationResult.Success(learned), store.find(learned.shape.form))
    }

    /** One row in each of the eight tables schema 4 has. */
    private fun SupportSQLiteDatabase.seed() {
        execSQL("INSERT INTO app_usage (package_name, last_used_epoch_ms, launch_count) VALUES ('net.fixture.lumora', 1000, 7)")
        execSQL("INSERT INTO suggestion_ranking (action_id, label, score, last_updated_epoch_ms) VALUES ('launch_app', 'Lumora', 0.5, 2000)")
        execSQL(
            "INSERT INTO intent_match (id, normalized_text, match_type, confidence, timestamp_epoch_ms) " +
                "VALUES (1, 'открой lumora', 'LAUNCH_APP', 0.9, 3000)",
        )
        execSQL(
            "INSERT INTO resolution_preferences (action_id, query, context_key, preferred_target_type, " +
                "preferred_target_value, streak, total_choices, last_chosen_at, learned_in_fingerprint) VALUES (" +
                "'${preference.actionId}', '${preference.query}', '${preference.contextKey}', '${preference.preferredTargetType}', " +
                "'${preference.preferredTargetValue}', ${preference.streak}, ${preference.totalChoices}, " +
                "${preference.lastChosenAtEpochMs}, '${preference.learnedInFingerprint}')",
        )
        execSQL(
            "INSERT INTO aliases (phrase, target_type, target_package, created_at) VALUES " +
                "('${alias.phrase}', '${alias.targetType}', '${alias.targetPackage}', ${alias.createdAt})",
        )
        execSQL(
            "INSERT INTO agent_session (id, goal_text, goal_shape, goal_shape_arg, state, cursor, created_at) " +
                "VALUES ('s1', 'сотри lumora', 'Free', 'сотри lumora', 'AwaitingConsent', 0, 6000)",
        )
        execSQL(
            "INSERT INTO agent_plan_step (session_id, step_index, tool_id, args_json, risk, precondition_fact, " +
                "rationale, observation_type, observation_fact, observation_output_json, consent) " +
                "VALUES ('s1', 0, 'uninstall_app', '{}', 'CONFIRM', NULL, 'MODEL_PROPOSED', NULL, NULL, NULL, NULL)",
        )
        execSQL(
            "INSERT INTO agent_trace_event (session_id, seq, type, step_index, detail, at) " +
                "VALUES ('s1', 0, 'PlanCreated', NULL, '1', 6000)",
        )
    }

    /** Every row of [table], every column as text, in rowid order. */
    private fun SupportSQLiteDatabase.rowsOf(table: String): List<List<String?>> =
        query("SELECT * FROM `$table` ORDER BY rowid").use { cursor ->
            buildList {
                while (cursor.moveToNext()) add((0 until cursor.columnCount).map { cursor.getString(it) })
            }
        }

    private companion object {
        const val TEST_DB = "sidr-migration-4-5-test"

        val PRE_EXISTING_TABLES = listOf(
            "app_usage",
            "suggestion_ranking",
            "intent_match",
            "resolution_preferences",
            "aliases",
            "agent_session",
            "agent_plan_step",
            "agent_trace_event",
        )
    }
}
