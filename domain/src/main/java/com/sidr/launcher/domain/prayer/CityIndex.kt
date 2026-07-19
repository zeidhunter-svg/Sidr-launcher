package com.sidr.launcher.domain.prayer

/**
 * Port: offline search over the bundled city index (DS-6B Task 3; impl in `:data:prayer`, Task 5).
 *
 * The primary, zero-permission location path (spec §0.4): every hit is a complete
 * [PrayerLocation] (label, 2dp-rounded coordinates, tzId, `source = CITY`). Works fully offline;
 * an empty list is a valid "no match" result, never an error.
 */
interface CityIndex {
    suspend fun search(query: String, limit: Int): List<PrayerLocation>
}
