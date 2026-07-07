package com.sidr.launcher.data.repository.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2: adds the `resolution_preferences` table (Stage-2 S2-1 Task 9).
 *
 * The `CREATE TABLE` SQL below is copied byte-for-byte from the generated golden schema
 * (`schemas/com.sidr.launcher.data.repository.db.SidrDatabase/2.json`, `resolution_preferences`
 * entity's `createSql`, with `${TABLE_NAME}` substituted for the literal table name) — Room's
 * `MigrationTestHelper.runMigrationsAndValidate` diffs the post-migration schema against that
 * golden file, so any drift (column order, `NOT NULL`, or primary-key shape) fails validation.
 *
 * No data migration is needed: the table is new, nothing wrote to it before v2, and nothing
 * consumes it yet (Phase C wiring is deferred).
 */
val Migration1To2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `resolution_preferences` (" +
                "`action_id` TEXT NOT NULL, " +
                "`query` TEXT NOT NULL, " +
                "`context_key` TEXT NOT NULL, " +
                "`preferred_target_type` TEXT NOT NULL, " +
                "`preferred_target_value` TEXT NOT NULL, " +
                "`streak` INTEGER NOT NULL, " +
                "`total_choices` INTEGER NOT NULL, " +
                "`last_chosen_at` INTEGER NOT NULL, " +
                "`learned_in_fingerprint` TEXT NOT NULL, " +
                "PRIMARY KEY(`action_id`, `query`, `context_key`))",
        )
    }
}
