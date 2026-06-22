package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.AppUsageDao
import com.sidr.launcher.data.repository.db.entity.AppUsageEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UsageHistoryRepositoryImplTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: AppUsageDao
    private lateinit var repo: UsageHistoryRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.appUsageDao()
        repo = UsageHistoryRepositoryImpl(dao, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `round-trip recordLaunch stores and retrieves record`() = runTest {
        repo.recordLaunch("com.example.app", 1000L)

        val records = repo.getUsageRecords().first()

        assertEquals(1, records.size)
        assertEquals("com.example.app", records[0].packageName)
        assertEquals(1, records[0].launchCount)
        assertEquals(1000L, records[0].lastUsedEpochMs)
    }

    @Test
    fun `recordLaunch increments launch_count and updates recency on second call`() = runTest {
        repo.recordLaunch("com.example.app", 1000L)
        repo.recordLaunch("com.example.app", 2000L)

        val records = repo.getUsageRecords().first()

        assertEquals(1, records.size)
        assertEquals(2, records[0].launchCount)
        assertEquals(2000L, records[0].lastUsedEpochMs)
    }

    @Test
    fun `records ordered by launch_count DESC then last_used_epoch_ms DESC for tiebreak`() = runTest {
        // pkgA: count=5, recency=100
        // pkgB: count=10, recency=50   → wins by count
        // pkgC: count=5, recency=200  → tiebreaks pkgA by recency
        dao.insert(AppUsageEntity("pkgA", lastUsedEpochMs = 100L, launchCount = 5))
        dao.insert(AppUsageEntity("pkgB", lastUsedEpochMs = 50L, launchCount = 10))
        dao.insert(AppUsageEntity("pkgC", lastUsedEpochMs = 200L, launchCount = 5))

        val records = repo.getUsageRecords().first()

        assertEquals(listOf("pkgB", "pkgC", "pkgA"), records.map { it.packageName })
    }

    @Test
    fun `retention prunes to 200 rows removing oldest by timestamp`() = runTest {
        // Pre-fill 200 rows directly via DAO (timestamps 0..199)
        repeat(200) { i ->
            dao.insert(AppUsageEntity("pkg$i", lastUsedEpochMs = i.toLong(), launchCount = 1))
        }
        // 201st insert via repo triggers pruning
        repo.recordLaunch("pkg_new", 10_000L)

        assertEquals(200, dao.count())
        assertNull("oldest row (timestamp=0) should be pruned", dao.getByPackageName("pkg0"))
        assertNotNull("newest row must survive", dao.getByPackageName("pkg_new"))
    }
}
