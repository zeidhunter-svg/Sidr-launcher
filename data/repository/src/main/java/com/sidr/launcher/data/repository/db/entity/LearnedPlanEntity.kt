package com.sidr.launcher.data.repository.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One learned plan (A4′ phase 4, spec §8; schema v5 via `Migration4To5`): a command's form and the plan the
 * user confirmed for it. [form] is `CommandForm.key` — folded words, each value the user said written `…`.
 * The row holds **no package**: the template's app arguments are slots, bound again on every replay (spec
 * §8.2). It does hold command text, and says so: a form with no slot is the **whole folded command**
 * («будильник на рассвете»), and a constant in [templateJson] can be model-written text, a search query
 * (`web_search(query = орвелин история)`) for one. Unlike `agent_session.goal_text` the row **stays after the
 * session ends** — until the user forgets it or the cap of 200 evicts it — which is why the column is kept
 * visible and deletable (`DOC-AML-5`), beside the `resolution_preferences.query` precedent
 * (`RoomColumnNames.APPROVED_SENSITIVE_COLUMNS`). The column name `form` is not in the column-name scan's
 * denylist, so that scan does not see this. [templateJson] is the template as `LearnedPlanStoreImpl` writes it;
 * a row it cannot read is deleted when the store meets it (B-9).
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
