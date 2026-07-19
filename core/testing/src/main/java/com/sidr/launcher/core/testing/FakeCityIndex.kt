package com.sidr.launcher.core.testing

import com.sidr.launcher.domain.prayer.CityIndex
import com.sidr.launcher.domain.prayer.PrayerLocation

/**
 * In-memory fake [CityIndex] (DS-6B Task 3). Seed [cities]; [search] is a case-insensitive
 * label-contains filter honouring `limit`, and records queries in [receivedQueries]. Not wired
 * into any Hilt graph — use directly in unit tests.
 */
class FakeCityIndex(
    var cities: List<PrayerLocation> = emptyList(),
) : CityIndex {

    val receivedQueries = mutableListOf<String>()

    override suspend fun search(query: String, limit: Int): List<PrayerLocation> {
        receivedQueries += query
        if (query.isBlank()) return emptyList()
        return cities.filter { it.label.contains(query, ignoreCase = true) }.take(limit)
    }
}
