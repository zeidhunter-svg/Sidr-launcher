package com.sidr.launcher.data.repository.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 -> v5: adds the A4′ phase 4 `learned_plans` table (the learned-plan cache, spec §8). No data migration is
 * needed — the table is new and nothing was learned before this version; every pre-existing table is untouched.
 *
 * The statement is copied **verbatim** from the generated `schemas/5.json` with `${TABLE_NAME}` substituted,
 * because `MigrationTestHelper.runMigrationsAndValidate` compares the migrated database against that file and
 * fails on any drift. It is proved in the JVM gate by `Migration4To5Test` (Robolectric, plan §0.2 B-14) and on a
 * device by the instrumented `MigrationTest`.
 */
val Migration4To5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `learned_plans` (" +
                "`form` TEXT NOT NULL, `template_json` TEXT NOT NULL, `streak` INTEGER NOT NULL, " +
                "`confirmations` INTEGER NOT NULL, `confirmed_at` INTEGER NOT NULL, PRIMARY KEY(`form`))",
        )
    }
}
