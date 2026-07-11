package com.sidr.launcher.data.repository.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3: adds the `aliases` table (Stage-2 S2-2). No data migration is needed: the table
 * is new and explicit alias memory did not exist before this version.
 */
val Migration2To3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `aliases` (" +
                "`phrase` TEXT NOT NULL, " +
                "`target_type` TEXT NOT NULL, " +
                "`target_package` TEXT NOT NULL, " +
                "`created_at` INTEGER NOT NULL, " +
                "PRIMARY KEY(`phrase`))",
        )
    }
}
