package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.ResolutionPreferenceDao
import com.sidr.launcher.data.repository.db.entity.ResolutionPreferenceEntity
import com.sidr.launcher.domain.action.ActionId
import com.sidr.launcher.domain.action.ActionIds
import com.sidr.launcher.domain.memory.resolution.CandidateSetFingerprint
import com.sidr.launcher.domain.memory.resolution.CapabilityKey
import com.sidr.launcher.domain.memory.resolution.MAX_RESOLUTION_PREFERENCES
import com.sidr.launcher.domain.memory.resolution.PreferenceEvidence
import com.sidr.launcher.domain.memory.resolution.ResolutionContext
import com.sidr.launcher.domain.memory.resolution.ResolutionPreference
import com.sidr.launcher.domain.memory.resolution.ResolvedTarget
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class ResolutionPreferenceStoreImplTest {

    private lateinit var db: SidrDatabase
    private lateinit var dao: ResolutionPreferenceDao
    private lateinit var store: ResolutionPreferenceStoreImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.resolutionPreferenceDao()
        store = ResolutionPreferenceStoreImpl(dao, testDispatcher)
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun preference(
        actionId: ActionId = ActionIds.LAUNCH_APP,
        query: String = "music",
        packageName: String = "com.spotify.music",
        streak: Int = 3,
        totalChoices: Int = 5,
        lastChosenAtEpochMs: Long = 1000L,
        fingerprint: String = "app:com.a|app:com.b",
    ) = ResolutionPreference(
        capabilityKey = CapabilityKey(actionId = actionId, query = query),
        context = ResolutionContext.None,
        preferredTarget = ResolvedTarget.App(packageName = packageName),
        evidence = PreferenceEvidence(streak, totalChoices, lastChosenAtEpochMs),
        learnedInSetFingerprint = CandidateSetFingerprint(fingerprint),
    )

    @Test
    fun `round-trip upsert then find returns the stored preference`() = runTest {
        val pref = preference()

        assertTrue(store.upsert(pref) is OperationResult.Success)

        val result = store.find(pref.capabilityKey, pref.context)
        result as OperationResult.Success
        val found = result.value
        assertEquals(pref, found)
        // Explicit field asserts (ActionId round-trips through the action_id String column).
        assertEquals(ActionIds.LAUNCH_APP, found!!.capabilityKey.actionId)
        assertEquals("music", found.capabilityKey.query)
        assertEquals("com.spotify.music", (found.preferredTarget as ResolvedTarget.App).packageName)
        assertEquals(3, found.evidence.streak)
        assertEquals(5, found.evidence.totalChoices)
        assertEquals(1000L, found.evidence.lastChosenAtEpochMs)
        assertEquals("app:com.a|app:com.b", found.learnedInSetFingerprint.value)
    }

    @Test
    fun `find returns Success null when the row is absent`() = runTest {
        val result = store.find(CapabilityKey(ActionIds.LAUNCH_APP, "nothing"), ResolutionContext.None)
        result as OperationResult.Success
        assertNull(result.value)
    }

    @Test
    fun `find on a DAO error returns Failure and never throws`() = runTest {
        val throwingStore = ResolutionPreferenceStoreImpl(ThrowingDao(), testDispatcher)

        val result = throwingStore.find(
            CapabilityKey(ActionIds.LAUNCH_APP, "music"),
            ResolutionContext.None,
        )

        assertTrue(result is OperationResult.Failure)
    }

    @Test
    fun `delete removes the stored row`() = runTest {
        val pref = preference()
        store.upsert(pref)

        assertTrue(store.delete(pref.capabilityKey, pref.context) is OperationResult.Success)

        val result = store.find(pref.capabilityKey, pref.context)
        result as OperationResult.Success
        assertNull(result.value)
    }

    @Test
    fun `upsert enforces the cap evicting the oldest by lastChosenAt`() = runTest {
        // Pre-fill MAX rows directly via DAO, timestamps 1..MAX (row #1 is oldest).
        repeat(MAX_RESOLUTION_PREFERENCES) { i ->
            dao.upsert(
                ResolutionPreferenceEntity(
                    actionId = ActionIds.LAUNCH_APP.value,
                    query = "q$i",
                    contextKey = "none",
                    preferredTargetType = "app",
                    preferredTargetValue = "com.pkg$i",
                    streak = 1,
                    totalChoices = 1,
                    lastChosenAtEpochMs = (i + 1).toLong(),
                    learnedInFingerprint = "app:com.pkg$i",
                )
            )
        }
        assertEquals(MAX_RESOLUTION_PREFERENCES, dao.count())

        // One more via the store triggers the cap trim.
        store.upsert(preference(query = "newest", packageName = "com.newest", lastChosenAtEpochMs = 10_000L))

        assertEquals(MAX_RESOLUTION_PREFERENCES, dao.count())
        // Oldest (timestamp = 1, query "q0") must have been evicted.
        val oldest = store.find(CapabilityKey(ActionIds.LAUNCH_APP, "q0"), ResolutionContext.None)
        oldest as OperationResult.Success
        assertNull(oldest.value)
        // Newest survives.
        val newest = store.find(CapabilityKey(ActionIds.LAUNCH_APP, "newest"), ResolutionContext.None)
        newest as OperationResult.Success
        assertEquals("com.newest", (newest.value!!.preferredTarget as ResolvedTarget.App).packageName)
    }

    @Test
    fun `observeAll maps rows to domain and skips a malformed row`() = runTest {
        // Two valid rows.
        dao.upsert(
            ResolutionPreferenceEntity(
                actionId = ActionIds.LAUNCH_APP.value, query = "valid1", contextKey = "none",
                preferredTargetType = "app", preferredTargetValue = "com.one",
                streak = 1, totalChoices = 1, lastChosenAtEpochMs = 100L,
                learnedInFingerprint = "app:com.one",
            )
        )
        dao.upsert(
            ResolutionPreferenceEntity(
                actionId = ActionIds.LAUNCH_APP.value, query = "valid2", contextKey = "none",
                preferredTargetType = "app", preferredTargetValue = "com.two",
                streak = 1, totalChoices = 1, lastChosenAtEpochMs = 200L,
                learnedInFingerprint = "app:com.two",
            )
        )
        // One deliberately-malformed row: unknown preferred_target_type discriminator.
        dao.upsert(
            ResolutionPreferenceEntity(
                actionId = ActionIds.LAUNCH_APP.value, query = "malformed", contextKey = "none",
                preferredTargetType = "shortcut_v99", preferredTargetValue = "whatever",
                streak = 1, totalChoices = 1, lastChosenAtEpochMs = 300L,
                learnedInFingerprint = "x",
            )
        )

        val emitted = store.observeAll().first()

        // Bad row skipped, no crash; the two valid rows still stream.
        assertEquals(2, emitted.size)
        val queries = emitted.map { it.capabilityKey.query }.toSet()
        assertEquals(setOf("valid1", "valid2"), queries)
        assertTrue(emitted.all { it.preferredTarget is ResolvedTarget.App })
    }

    /**
     * A [ResolutionPreferenceDao] whose read throws, to prove `find` maps a DAO error to
     * [OperationResult.Failure] without propagating the exception.
     */
    private class ThrowingDao : ResolutionPreferenceDao {
        override suspend fun findByKey(
            actionId: String,
            query: String,
            contextKey: String,
        ): ResolutionPreferenceEntity? = throw RuntimeException("boom")

        override suspend fun upsert(entity: ResolutionPreferenceEntity) = throw RuntimeException("boom")
        override suspend fun deleteByKey(actionId: String, query: String, contextKey: String) =
            throw RuntimeException("boom")
        override suspend fun deleteByTargetValue(packageName: String) = throw RuntimeException("boom")
        override fun observeAll(): Flow<List<ResolutionPreferenceEntity>> = throw RuntimeException("boom")
        override suspend fun count(): Int = throw RuntimeException("boom")
        override suspend fun deleteOldest(excess: Int) = throw RuntimeException("boom")
    }
}
