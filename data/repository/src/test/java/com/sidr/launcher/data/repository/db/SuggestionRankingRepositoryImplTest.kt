package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.SuggestionRankingDao
import com.sidr.launcher.data.repository.db.entity.SuggestionRankingEntity
import com.sidr.launcher.domain.history.SuggestionRankingRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SuggestionRankingRepositoryImplTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: SuggestionRankingDao
    private lateinit var repo: SuggestionRankingRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.suggestionRankingDao()
        repo = SuggestionRankingRepositoryImpl(dao, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `round-trip upsertRanking stores and retrieves record`() = runTest {
        val record = SuggestionRankingRecord("open_telegram", "Telegram", score = 0.9, lastUpdatedEpochMs = 1000L)

        repo.upsertRanking(record)

        val records = repo.getRankingRecords().first()
        assertEquals(1, records.size)
        assertEquals("open_telegram", records[0].actionId)
        assertEquals("Telegram", records[0].label)
        assertEquals(0.9, records[0].score, 0.001)
        assertEquals(1000L, records[0].lastUpdatedEpochMs)
    }

    @Test
    fun `upsertRanking updates existing record with same actionId`() = runTest {
        val original = SuggestionRankingRecord("open_telegram", "Telegram", score = 0.5, lastUpdatedEpochMs = 1000L)
        val updated = SuggestionRankingRecord("open_telegram", "Telegram", score = 0.95, lastUpdatedEpochMs = 2000L)

        repo.upsertRanking(original)
        repo.upsertRanking(updated)

        val records = repo.getRankingRecords().first()
        assertEquals(1, records.size)
        assertEquals(0.95, records[0].score, 0.001)
        assertEquals(2000L, records[0].lastUpdatedEpochMs)
    }

    @Test
    fun `records ordered by score DESC`() = runTest {
        dao.upsert(SuggestionRankingEntity("low", "Low", score = 0.1, lastUpdatedEpochMs = 0L))
        dao.upsert(SuggestionRankingEntity("high", "High", score = 0.9, lastUpdatedEpochMs = 0L))
        dao.upsert(SuggestionRankingEntity("mid", "Mid", score = 0.5, lastUpdatedEpochMs = 0L))

        val records = repo.getRankingRecords().first()

        assertEquals(listOf("high", "mid", "low"), records.map { it.actionId })
    }

    @Test
    fun `retention prunes to 100 rows removing lowest-scored`() = runTest {
        // Pre-fill 100 rows directly via DAO (scores 0.01..1.00)
        repeat(100) { i ->
            dao.upsert(SuggestionRankingEntity("action$i", "Label$i", score = (i + 1) * 0.01, lastUpdatedEpochMs = 0L))
        }
        // 101st upsert via repo triggers pruning
        repo.upsertRanking(SuggestionRankingRecord("action_top", "Top", score = 1.5, lastUpdatedEpochMs = 0L))

        assertEquals(100, dao.count())
        // action0 had the lowest score (0.01) and should be pruned
        val remaining = repo.getRankingRecords().first()
        val remainingIds = remaining.map { it.actionId }
        assert("action0" !in remainingIds) { "Lowest-scored action0 should be pruned; remaining: $remainingIds" }
        assert("action_top" in remainingIds) { "Highest-scored action_top must survive" }
    }
}
