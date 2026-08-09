package com.sidr.launcher.data.prayer

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * DataStore key OBJECTS for prayer setup + last schedule cache (DS-6B Task 6).
 *
 * These `Preferences.Key<>` objects live HERE, in `:data:prayer`, because the impls
 * ([PrayerPreferencesRepositoryImpl] / [PrayerScheduleCacheImpl]) must not create a
 * `:data:prayer -> :data:repository` module edge (no data→data edge; hard rule). The matching key
 * NAME STRINGS are separately inventoried in
 * `data/repository/.../preferences/PreferencesKeys.kt` (`ALL_KEY_NAMES`) purely so
 * `PrivacyInventoryGuardTest` (which lives in `:data:repository`) scans them too — keys are just
 * names, so duplicating the string literal carries no coupling. Both files are cross-referenced by
 * this comment; if you add/rename/remove a key here, mirror the change there, and vice versa.
 *
 * All ten strings are read over the SAME shared `sidr_preferences` DataStore<Preferences> file the
 * rest of the app already writes to (Block E precedent) — no second DataStore file.
 */
internal object PrayerPreferencesKeys {

    // Setup — prefix: prayer_ / prayer_loc_ (mirrors PreferencesKeys.PRAYER_* in :data:repository)
    val PRAYER_METHOD = stringPreferencesKey("prayer_method")
    val PRAYER_MADHAB = stringPreferencesKey("prayer_madhab")

    // Deliberately "loc", not "location" — see PreferencesKeys' PrivacyInventoryGuardTest denylist.
    // Coordinates are always already-rounded-to-2dp PrayerLocation values (domain enforces this by
    // construction), so no precise coordinate is ever persisted.
    val PRAYER_LOC_LABEL = stringPreferencesKey("prayer_loc_label")
    val PRAYER_LOC_LAT2DP = stringPreferencesKey("prayer_loc_lat2dp")
    val PRAYER_LOC_LON2DP = stringPreferencesKey("prayer_loc_lon2dp")
    val PRAYER_LOC_TZ = stringPreferencesKey("prayer_loc_tz")
    val PRAYER_LOC_SOURCE = stringPreferencesKey("prayer_loc_source")

    // Last schedule cache — prefix: prayer_sched_. DATE is the location-tz civil date as
    // LocalDate.toEpochDay().toString(); TIMES/PROVENANCE are kotlinx-serialization JSON (UTF-8 safe
    // for arbitrary city labels — spaces, commas, non-ASCII all round-trip losslessly).
    val PRAYER_SCHED_DATE = stringPreferencesKey("prayer_sched_date")
    val PRAYER_SCHED_TIMES = stringPreferencesKey("prayer_sched_times")
    val PRAYER_SCHED_PROVENANCE = stringPreferencesKey("prayer_sched_provenance")
}

/**
 * Removes all three schedule-cache keys within an ALREADY-OPEN `dataStore.edit { }` block.
 *
 * Shared by [PrayerScheduleCacheImpl] (its own `write`/`clear`) and
 * [PrayerPreferencesRepositoryImpl] (`saveSetup`/`clearSetup`) — the latter usage is the I1 fix:
 * saving or clearing the setup MUST atomically drop any previously cached schedule in the SAME
 * `edit` transaction, so a cache computed under a stale/replaced setup (e.g. a different location
 * that happens to share a label) can never be read back as usable. See GetPrayerContextUseCase's
 * `matchesSetup`, which compares provenance by [com.sidr.launcher.domain.prayer.PrayerLocation.label]
 * only — this write-time invalidation is what closes that hole structurally.
 */
internal fun MutablePreferences.removePrayerScheduleKeys() {
    remove(PrayerPreferencesKeys.PRAYER_SCHED_DATE)
    remove(PrayerPreferencesKeys.PRAYER_SCHED_TIMES)
    remove(PrayerPreferencesKeys.PRAYER_SCHED_PROVENANCE)
}
