package com.sidr.launcher.domain.prayer

import com.sidr.launcher.core.testing.FakeCityIndex
import com.sidr.launcher.core.testing.FakePrayerLocationProvider
import com.sidr.launcher.core.testing.FakePrayerPreferencesRepository
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Proves the port forms of [CityIndex], [PrayerLocationProvider], and
 * [PrayerPreferencesRepository] via their fakes (the Block-S `FakeSpeechInputSourceTest`
 * precedent) — these three ports are consumed by Tasks 5–8, not by [GetPrayerContextUseCase].
 */
class PrayerPortFakesTest {

    @Test
    fun `city index search filters case-insensitively and honours the limit`() = runTest {
        val index = FakeCityIndex(
            cities = listOf(
                PrayerTestFixtures.istanbulLocation("Istanbul"),
                PrayerTestFixtures.istanbulLocation("Islamabad"),
                PrayerTestFixtures.tokyoLocation(),
            ),
        )

        assertEquals(listOf("Istanbul"), index.search("istan", limit = 5).map { it.label })
        assertEquals(1, index.search("is", limit = 1).size)
        assertEquals(emptyList<PrayerLocation>(), index.search("  ", limit = 5))
        assertEquals(listOf("istan", "is", "  "), index.receivedQueries)
    }

    @Test
    fun `location provider defaults to no fix and returns the scripted rounded DEVICE location`() = runTest {
        val provider = FakePrayerLocationProvider()

        assertEquals(OperationResult.Success<PrayerLocation?>(null), provider.currentLocation())

        val device = PrayerTestFixtures.istanbulLocation().copy(source = PrayerLocationSource.DEVICE)
        provider.resultToReturn = OperationResult.Success(device)
        assertEquals(OperationResult.Success<PrayerLocation?>(device), provider.currentLocation())
        assertEquals(2, provider.callCount)
    }

    @Test
    fun `preferences fake round-trips a setup and clears it`() = runTest {
        val prefs = FakePrayerPreferencesRepository()
        assertNull(prefs.setup().first())

        val setup = PrayerTestFixtures.turkeySetup()
        assertEquals(OperationResult.Success(Unit), prefs.saveSetup(setup))
        assertEquals(setup, prefs.setup().first())

        assertEquals(OperationResult.Success(Unit), prefs.clearSetup())
        assertNull(prefs.setup().first())
    }
}
