package com.sidr.launcher.data.prayer

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import com.sidr.launcher.core.common.di.IoDispatcher
import com.sidr.launcher.domain.prayer.CachedPrayerSchedule
import com.sidr.launcher.domain.prayer.CalculationMethodId
import com.sidr.launcher.domain.prayer.Madhab
import com.sidr.launcher.domain.prayer.PrayerAuthority
import com.sidr.launcher.domain.prayer.PrayerDaySchedule
import com.sidr.launcher.domain.prayer.PrayerInstant
import com.sidr.launcher.domain.prayer.PrayerName
import com.sidr.launcher.domain.prayer.PrayerScheduleCache
import com.sidr.launcher.domain.prayer.PrayerScheduleProvenance
import com.sidr.launcher.domain.result.OperationError
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * [PrayerScheduleCache] over the shared `sidr_preferences` DataStore (DS-6B Task 6). Same impl
 * shape as [PrayerPreferencesRepositoryImpl] — constructor-injected `DataStore<Preferences>` +
 * `@IoDispatcher`, `withContext(ioDispatcher) { dataStore.edit { } }` for writes, `IOException` on
 * read tolerated as an empty store, `CancellationException` always re-thrown.
 *
 * **"No schedule without provenance", structurally enforced on read:** [read] reconstructs a
 * [CachedPrayerSchedule] only when ALL THREE persisted fields (date, times JSON, provenance JSON)
 * are present AND parse AND satisfy every domain invariant ([PrayerDaySchedule]'s exactly-five-prayers
 * check included) — any single failure anywhere in that pipeline yields `Success(null)`, never a
 * partial or unlabelled result. [write] always writes date + times + provenance together in one
 * `edit` transaction, so a write can never leave one of the three fields stale from a previous entry.
 *
 * City labels are arbitrary user text (spaces, commas, non-ASCII) — times/provenance are encoded as
 * kotlinx-serialization JSON (UTF-8), which round-trips any such label losslessly; the date is a
 * plain `LocalDate.toEpochDay()` string.
 */
class PrayerScheduleCacheImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : PrayerScheduleCache {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun read(): OperationResult<CachedPrayerSchedule?> =
        withContext(ioDispatcher) {
            try {
                val prefs = dataStore.data
                    .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
                    .first()
                OperationResult.Success(prefs.toCachedPrayerSchedule())
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                OperationResult.Success(null)
            }
        }

    override suspend fun write(entry: CachedPrayerSchedule): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs -> writeCachedPrayerSchedule(prefs, entry) }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "prayer_schedule_write_failed"))
            }
        }

    override suspend fun clear(): OperationResult<Unit> =
        withContext(ioDispatcher) {
            try {
                dataStore.edit { prefs -> prefs.removePrayerScheduleKeys() }
                OperationResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                OperationResult.Failure(OperationError.UnknownError(reason = "prayer_schedule_clear_failed"))
            }
        }

    private fun writeCachedPrayerSchedule(prefs: MutablePreferences, entry: CachedPrayerSchedule) {
        val schedule = entry.schedule
        val provenance = entry.provenance

        prefs[PrayerPreferencesKeys.PRAYER_SCHED_DATE] = schedule.dateInLocationTz.toEpochDay().toString()

        val timesDto = ScheduleTimesDto(
            instants = schedule.instants.map { it.toDto() },
            sunrise = schedule.sunrise?.toDto(),
        )
        prefs[PrayerPreferencesKeys.PRAYER_SCHED_TIMES] = json.encodeToString(ScheduleTimesDto.serializer(), timesDto)

        val provenanceDto = ProvenanceDto(
            authority = provenance.authority.name,
            methodId = provenance.methodId.key,
            madhab = provenance.madhab.name,
            locationLabel = provenance.locationLabel,
            computedAtMillis = provenance.computedAtMillis,
        )
        prefs[PrayerPreferencesKeys.PRAYER_SCHED_PROVENANCE] =
            json.encodeToString(ProvenanceDto.serializer(), provenanceDto)
    }

    /**
     * Reconstructs a [CachedPrayerSchedule] iff every field is present, parses, and satisfies every
     * domain invariant. Any failure anywhere (missing key, malformed JSON, unknown enum value, bad
     * epoch-day, or a [PrayerDaySchedule]/[PrayerScheduleProvenance] `require()` violation) is caught
     * by the single outer [runCatching] and yields `null` — never a partial/unlabelled result.
     */
    private fun Preferences.toCachedPrayerSchedule(): CachedPrayerSchedule? = runCatching {
        val epochDayRaw = this[PrayerPreferencesKeys.PRAYER_SCHED_DATE] ?: return@runCatching null
        val timesJson = this[PrayerPreferencesKeys.PRAYER_SCHED_TIMES] ?: return@runCatching null
        val provenanceJson = this[PrayerPreferencesKeys.PRAYER_SCHED_PROVENANCE] ?: return@runCatching null

        val date = LocalDate.ofEpochDay(epochDayRaw.toLong())
        val timesDto = json.decodeFromString(ScheduleTimesDto.serializer(), timesJson)
        val provenanceDto = json.decodeFromString(ProvenanceDto.serializer(), provenanceJson)

        val schedule = PrayerDaySchedule(
            dateInLocationTz = date,
            instants = timesDto.instants.map { it.toDomain() },
            sunrise = timesDto.sunrise?.toDomain(),
        )
        val provenance = PrayerScheduleProvenance(
            authority = PrayerAuthority.valueOf(provenanceDto.authority),
            methodId = CalculationMethodId(provenanceDto.methodId),
            madhab = Madhab.valueOf(provenanceDto.madhab),
            locationLabel = provenanceDto.locationLabel,
            computedAtMillis = provenanceDto.computedAtMillis,
        )

        CachedPrayerSchedule(schedule, provenance)
    }.getOrNull()

    private fun PrayerInstant.toDto() = PrayerInstantDto(name = name.name, epochMillis = epochMillis)
    private fun PrayerInstantDto.toDomain() = PrayerInstant(name = PrayerName.valueOf(name), epochMillis = epochMillis)

    @Serializable
    private data class PrayerInstantDto(val name: String, val epochMillis: Long)

    @Serializable
    private data class ScheduleTimesDto(
        val instants: List<PrayerInstantDto>,
        val sunrise: PrayerInstantDto? = null,
    )

    @Serializable
    private data class ProvenanceDto(
        val authority: String,
        val methodId: String,
        val madhab: String,
        val locationLabel: String,
        val computedAtMillis: Long,
    )
}
