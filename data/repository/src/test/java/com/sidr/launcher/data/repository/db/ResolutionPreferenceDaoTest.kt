package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Stage-2 block S2-1, Phase B (Task 7): Room DAO test for `resolution_preferences`.
 *
 * Mirrors [IntentMatchHistoryRepositoryImplTest]'s Robolectric in-memory-db setup, but exercises
 * [ResolutionPreferenceDao] directly (there is no repository yet — nothing consumes this table).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResolutionPreferenceDaoTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: ResolutionPreferenceDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.resolutionPreferenceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun row(
        actionId: String = "launch_app",
        query: String = "open telegram",
        contextKey: String = "none",
        targetValue: String = "org.telegram.messenger",
        streak: Int = 1,
        totalChoices: Int = 1,
        lastChosenAt: Long = 1_000L,
        fingerprint: String = "fp-1",
    ) = ResolutionPreferenceEntity(
        actionId = actionId,
        query = query,
        contextKey = contextKey,
        preferredTargetType = "app",
        preferredTargetValue = targetValue,
        streak = streak,
        totalChoices = totalChoices,
        lastChosenAtEpochMs = lastChosenAt,
        learnedInFingerprint = fingerprint,
    )

    @Test
    fun `round-trip upsert and findByKey returns the stored row`() = runTest {
        dao.upsert(row())

        val found = dao.findByKey("launch_app", "open telegram", "none")

        assertNotNull(found)
        assertEquals("app", found?.preferredTargetType)
        assertEquals("org.telegram.messenger", found?.preferredTargetValue)
        assertEquals(1, found?.streak)
        assertEquals(1, found?.totalChoices)
        assertEquals(1_000L, found?.lastChosenAtEpochMs)
        assertEquals("fp-1", found?.learnedInFingerprint)
    }

    @Test
    fun `findByKey returns null when no row matches the composite key`() = runTest {
        val found = dao.findByKey("launch_app", "open telegram", "none")

        assertNull(found)
    }

    @Test
    fun `upsert replaces the existing row on the same composite key`() = runTest {
        dao.upsert(row(streak = 1, totalChoices = 1, targetValue = "org.telegram.messenger"))
        dao.upsert(row(streak = 2, totalChoices = 2, targetValue = "com.whatsapp"))

        assertEquals(1, dao.count())
        val found = dao.findByKey("launch_app", "open telegram", "none")
        assertEquals("com.whatsapp", found?.preferredTargetValue)
        assertEquals(2, found?.streak)
        assertEquals(2, found?.totalChoices)
    }

    @Test
    fun `deleteByKey removes only the matching composite key`() = runTest {
        dao.upsert(row(query = "open telegram"))
        dao.upsert(row(query = "open telegram web"))

        dao.deleteByKey("launch_app", "open telegram", "none")

        assertEquals(1, dao.count())
        assertNull(dao.findByKey("launch_app", "open telegram", "none"))
        assertNotNull(dao.findByKey("launch_app", "open telegram web", "none"))
    }

    @Test
    fun `deleteByTargetValue removes every row pointing at that package`() = runTest {
        dao.upsert(row(query = "open telegram", targetValue = "org.telegram.messenger"))
        dao.upsert(row(query = "telegram", targetValue = "org.telegram.messenger"))
        dao.upsert(row(query = "open whatsapp", targetValue = "com.whatsapp"))

        dao.deleteByTargetValue("org.telegram.messenger")

        assertEquals(1, dao.count())
        assertEquals(
            "com.whatsapp",
            dao.findByKey("launch_app", "open whatsapp", "none")?.preferredTargetValue,
        )
    }

    @Test
    fun `count reflects the number of stored rows`() = runTest {
        assertEquals(0, dao.count())

        dao.upsert(row(query = "a"))
        dao.upsert(row(query = "b"))

        assertEquals(2, dao.count())
    }

    @Test
    fun `deleteOldest removes the given number of least-recently-chosen rows`() = runTest {
        dao.upsert(row(query = "a", lastChosenAt = 1_000L))
        dao.upsert(row(query = "b", lastChosenAt = 2_000L))
        dao.upsert(row(query = "c", lastChosenAt = 3_000L))

        dao.deleteOldest(2)

        assertEquals(1, dao.count())
        val remaining = dao.observeAll().first()
        assertEquals("c", remaining.single().query)
    }

    @Test
    fun `observeAll orders rows by last-chosen descending`() = runTest {
        dao.upsert(row(query = "old", lastChosenAt = 1_000L))
        dao.upsert(row(query = "newest", lastChosenAt = 3_000L))
        dao.upsert(row(query = "middle", lastChosenAt = 2_000L))

        val all = dao.observeAll().first()

        assertEquals(listOf("newest", "middle", "old"), all.map { it.query })
    }
}
