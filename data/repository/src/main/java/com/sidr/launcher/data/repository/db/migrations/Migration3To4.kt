package com.sidr.launcher.data.repository.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v3 -> v4: adds the three agentic-track A0 tables (`agent_session`, `agent_plan_step`,
 * `agent_trace_event`). No data migration is needed — the tables are new and no agent session could
 * have existed before this version.
 *
 * The three statements are copied **verbatim** from the generated `schemas/4.json` with
 * `${TABLE_NAME}` substituted, because `MigrationTestHelper.runMigrationsAndValidate` compares the
 * migrated database against that file and fails on any drift — a re-typed `NOT NULL` or a dropped
 * `ON DELETE CASCADE` is exactly the kind of difference it exists to catch.
 */
val Migration3To4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_session` (" +
                "`id` TEXT NOT NULL, `goal_text` TEXT NOT NULL, `goal_shape` TEXT NOT NULL, " +
                "`goal_shape_arg` TEXT NOT NULL, `state` TEXT NOT NULL, `cursor` INTEGER NOT NULL, " +
                "`created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_plan_step` (" +
                "`session_id` TEXT NOT NULL, `step_index` INTEGER NOT NULL, `tool_id` TEXT NOT NULL, " +
                "`args_json` TEXT NOT NULL, `risk` TEXT NOT NULL, `precondition_fact` TEXT, " +
                "`rationale` TEXT NOT NULL, `observation_type` TEXT, `observation_fact` TEXT, " +
                "`observation_output_json` TEXT, `consent` INTEGER, " +
                "PRIMARY KEY(`session_id`, `step_index`), " +
                "FOREIGN KEY(`session_id`) REFERENCES `agent_session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `agent_trace_event` (" +
                "`session_id` TEXT NOT NULL, `seq` INTEGER NOT NULL, `type` TEXT NOT NULL, " +
                "`step_index` INTEGER, `detail` TEXT, `at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`session_id`, `seq`), " +
                "FOREIGN KEY(`session_id`) REFERENCES `agent_session`(`id`) " +
                "ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
    }
}
