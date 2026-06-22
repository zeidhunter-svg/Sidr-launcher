package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.IntentMatchDao
import com.sidr.launcher.data.repository.db.entity.IntentMatchEntity
import com.sidr.launcher.domain.history.IntentMatchRecord
import com.sidr.launcher.domain.history.IntentMatchType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class IntentMatchHistoryRepositoryImplTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: IntentMatchDao
    private lateinit var repo: IntentMatchHistoryRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.intentMatchDao()
        repo = IntentMatchHistoryRepositoryImpl(dao, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `round-trip recordMatch stores LAUNCH_APP record and retrieves it`() = runTest {
        val record = IntentMatchRecord(
            normalizedText = "open telegram",
            matchType = IntentMatchType.LAUNCH_APP,
            confidence = 0.95,
            timestampEpochMs = 1000L,
        )

        repo.recordMatch(record)

        val records = repo.getMatchRecords().first()
        assertEquals(1, records.size)
        assertEquals("open telegram", records[0].normalizedText)
        assertEquals(IntentMatchType.LAUNCH_APP, records[0].matchType)
        assertEquals(0.95, records[0].confidence, 0.001)
        assertEquals(1000L, records[0].timestampEpochMs)
    }

    // ─── Redaction tests (F8.3, privacy-critical) ────────────────────────────────────────────────

    @Test
    fun `redaction SEARCH normalizedText is replaced with placeholder not content`() = runTest {
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "search cats",
                matchType = IntentMatchType.SEARCH,
                confidence = 0.88,
                timestampEpochMs = 2000L,
            )
        )

        val records = repo.getMatchRecords().first()
        assertEquals(1, records.size)
        // Query content "cats" must NOT be stored — only the redacted placeholder.
        assertEquals("search", records[0].normalizedText)
    }

    @Test
    fun `redaction UNKNOWN normalizedText is replaced with placeholder not content`() = runTest {
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "some unrecognized free text",
                matchType = IntentMatchType.UNKNOWN,
                confidence = 0.1,
                timestampEpochMs = 3000L,
            )
        )

        val records = repo.getMatchRecords().first()
        assertEquals(1, records.size)
        assertEquals("unknown", records[0].normalizedText)
    }

    @Test
    fun `redaction LAUNCH_APP normalizedText is stored as-is (structurally bounded)`() = runTest {
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "open telegram",
                matchType = IntentMatchType.LAUNCH_APP,
                confidence = 0.95,
                timestampEpochMs = 4000L,
            )
        )

        val records = repo.getMatchRecords().first()
        // Structurally-bounded types are not redacted.
        assertEquals("open telegram", records[0].normalizedText)
    }

    @Test
    fun `redaction OPEN_SETTINGS normalizedText is stored as-is`() = runTest {
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "open settings",
                matchType = IntentMatchType.OPEN_SETTINGS,
                confidence = 0.90,
                timestampEpochMs = 5000L,
            )
        )

        val records = repo.getMatchRecords().first()
        assertEquals("open settings", records[0].normalizedText)
    }

    @Test
    fun `redaction SIMPLE_COMMAND normalizedText is stored as-is`() = runTest {
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "clear",
                matchType = IntentMatchType.SIMPLE_COMMAND,
                confidence = 1.0,
                timestampEpochMs = 6000L,
            )
        )

        val records = repo.getMatchRecords().first()
        assertEquals("clear", records[0].normalizedText)
    }

    // ─── Retention ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `retention prunes to 200 rows removing oldest by timestamp`() = runTest {
        // Pre-fill 200 rows directly via DAO (timestamps 1..200)
        repeat(200) { i ->
            dao.insert(
                IntentMatchEntity(
                    normalizedText = "open pkg$i",
                    matchType = IntentMatchType.LAUNCH_APP,
                    confidence = 0.9,
                    timestampEpochMs = (i + 1).toLong(),
                )
            )
        }
        // 201st insert via repo triggers pruning
        repo.recordMatch(
            IntentMatchRecord(
                normalizedText = "open newest",
                matchType = IntentMatchType.LAUNCH_APP,
                confidence = 0.95,
                timestampEpochMs = 10_000L,
            )
        )

        assertEquals(200, dao.count())
        // Most-recent first; newest record should be at index 0
        val records = repo.getMatchRecords().first()
        assertEquals(200, records.size)
        assertEquals("open newest", records[0].normalizedText)
        assertEquals(10_000L, records[0].timestampEpochMs)
        // Record with timestamp=1 (oldest) should have been pruned
        assert(records.none { it.timestampEpochMs == 1L }) {
            "Oldest record (timestamp=1) should be pruned"
        }
    }
}
