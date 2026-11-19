package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One learned plan (A4′ phase 4, spec §8; schema v5 via `Migration4To5`): a command's form and the plan the
 * user confirmed for it. [form] is `CommandForm.key` — folded words, each value the user said written `…` — so
 * the row holds no value of any one command and **no package**: the template's app arguments are slots, bound
 * again on every replay (spec §8.2). [templateJson] is the template as `LearnedPlanStoreImpl` writes it; a row
 * it cannot read is deleted when the store meets it (B-9).
 *
 * Columns are named so that `RoomColumnNamesGuardTest` needs no exemption for this table.
 */
@Entity(tableName = "learned_plans")
data class LearnedPlanEntity(
    @PrimaryKey @ColumnInfo(name = "form") val form: String,
    @ColumnInfo(name = "template_json") val templateJson: String,
    @ColumnInfo(name = "streak") val streak: Int,
    @ColumnInfo(name = "confirmations") val confirmations: Int,
    @ColumnInfo(name = "confirmed_at") val confirmedAtEpochMs: Long,
)
