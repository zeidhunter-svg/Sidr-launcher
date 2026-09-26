package com.sidr.launcher.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerLocation
import com.sidr.launcher.domain.prayer.PrayerLocationSource
import com.sidr.launcher.domain.prayer.PrayerPreferencesRepository
import com.sidr.launcher.domain.prayer.PrayerSetup
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * [PrayerPreferencesRepository] over the shared `sidr_preferences` DataStore (DS-6B Task 6).
 * Mirrors the Block-E impl shape ([com.sidr.launcher.data.repository.preferences.UserPreferencesRepositoryImpl]):
 * constructor-injected `DataStore<Preferences>` + `@IoDispatcher`, reads tolerate `IOException` as an
 * empty store, writes/clears go through `withContext(ioDispatcher) { dataStore.edit { } }` and never
 * throw an expected failure to the caller (`CancellationException` is always re-thrown).
 *
 * [setup] reconstructs a [PrayerSetup] only when every REQUIRED field is present and every stored
 * enum/value-class value is still valid; any missing/malformed field yields `null` — a setup is never
 * partially resurrected. [location] is independently nullable within a present setup (method/madhab
 * chosen before a location is picked is a real state, not a parse failure).
 *
 * I1 fix: [saveSetup] and [clearSetup] each remove the schedule-cache keys in the SAME `edit`
 * transaction as the setup write — see [removePrayerScheduleKeys] kdoc for why this is structural,
 * not best-effort.
 */
class PrayerPreferencesRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PrayerPreferencesRepository {

    override fun setup(): Flow<PrayerSetup?> =
        dataStore.data
            .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
            .map { it.toPrayerSetup() }

    override suspend fun saveSetup(setup: PrayerSetup): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs ->
                    writeSetup(prefs, setup)
                    // I1: atomically invalidate any cache computed under the setup being replaced.
                    prefs.removePrayerScheduleKeys()
                }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "prayer_setup_write_failed"))
            }
        }

    override suspend fun clearSetup(): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs ->
                    prefs.remove(PrayerPreferencesKeys.PRAYER_METHOD)
                    prefs.remove(PrayerPreferencesKeys.PRAYER_MADHAB)
                    clearLocationKeys(prefs)
                    // I1: same invalidation on a full clear.
                    prefs.removePrayerScheduleKeys()
                }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "prayer_setup_clear_failed"))
            }
        }

    private fun writeSetup(prefs: MutablePreferences, setup: PrayerSetup) {
        prefs[PrayerPreferencesKeys.PRAYER_METHOD] = setup.methodId.key
        prefs[PrayerPreferencesKeys.PRAYER_MADHAB] = setup.madhab.name
        val location = setup.location
        if (location == null) {
            clearLocationKeys(prefs)
        } else {
            prefs[PrayerPreferencesKeys.PRAYER_LOC_LABEL] = location.label
            prefs[PrayerPreferencesKeys.PRAYER_LOC_LAT2DP] = location.lat2dp.toString()
            prefs[PrayerPreferencesKeys.PRAYER_LOC_LON2DP] = location.lon2dp.toString()
            prefs[PrayerPreferencesKeys.PRAYER_LOC_TZ] = location.tzId
            prefs[PrayerPreferencesKeys.PRAYER_LOC_SOURCE] = location.source.name
        }
    }

    private fun clearLocationKeys(prefs: MutablePreferences) {
        prefs.remove(PrayerPreferencesKeys.PRAYER_LOC_LABEL)
        prefs.remove(PrayerPreferencesKeys.PRAYER_LOC_LAT2DP)
        prefs.remove(PrayerPreferencesKeys.PRAYER_LOC_LON2DP)
        prefs.remove(PrayerPreferencesKeys.PRAYER_LOC_TZ)
        prefs.remove(PrayerPreferencesKeys.PRAYER_LOC_SOURCE)
    }

    private fun Preferences.toPrayerSetup(): PrayerSetup? = runCatching {
        val methodKey = this[PrayerPreferencesKeys.PRAYER_METHOD] ?: return@runCatching null
        val madhabName = this[PrayerPreferencesKeys.PRAYER_MADHAB] ?: return@runCatching null

        PrayerSetup(
            methodId = CalculationMethodId(methodKey),
            madhab = Madhab.valueOf(madhabName),
            // A malformed/corrupt location degrades to `null` (LOCATION_MISSING), independent of the
            // outer setup — losing a corrupt location must not also discard valid method/madhab.
            location = toPrayerLocation(),
        )
    }.getOrNull()

    private fun Preferences.toPrayerLocation(): PrayerLocation? = runCatching {
        val label = this[PrayerPreferencesKeys.PRAYER_LOC_LABEL] ?: return@runCatching null
        val lat = this[PrayerPreferencesKeys.PRAYER_LOC_LAT2DP]?.toDoubleOrNull() ?: return@runCatching null
        val lon = this[PrayerPreferencesKeys.PRAYER_LOC_LON2DP]?.toDoubleOrNull() ?: return@runCatching null
        val tz = this[PrayerPreferencesKeys.PRAYER_LOC_TZ] ?: return@runCatching null
        val sourceName = this[PrayerPreferencesKeys.PRAYER_LOC_SOURCE] ?: return@runCatching null

        PrayerLocation(
            label = label,
            lat2dp = lat,
            lon2dp = lon,
            tzId = tz,
            source = PrayerLocationSource.valueOf(sourceName),
        )
    }.getOrNull()
}
