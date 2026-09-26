package com.sidr.launcher.domain.prayer

import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.Flow

/**
 * The user's explicit prayer setup. [methodId] and [madhab] are mandatory first-run choices —
 * a `PrayerSetup` cannot exist without both, and neither is ever defaulted (spec §0.1/§0.2).
 * [location] is nullable: method/madhab may be chosen before a location is picked, which is the
 * `LOCATION_MISSING` state — not a guess.
 */
data class PrayerSetup(
    val methodId: CalculationMethodId,
    val madhab: Madhab,
    val location: PrayerLocation?,
)

/**
 * Port: persisted prayer setup (DS-6B Task 3; impl over DataStore in a later task with
 * denylist-clean `prayer_*` keys — spec §0.5). `null` in the flow = never configured.
 * Writes follow the repo convention: [OperationResult], never thrown to UI.
 */
interface PrayerPreferencesRepository {
    fun setup(): Flow<PrayerSetup?>

    suspend fun saveSetup(setup: PrayerSetup): OperationResult<Unit>

    /** Clears the whole setup (spec §5: location can be changed or cleared). */
    suspend fun clearSetup(): OperationResult<Unit>
}
