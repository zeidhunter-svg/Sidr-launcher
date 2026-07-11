package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.domain.memory.alias.Alias
import com.sidr.launcher.domain.memory.alias.AliasTarget
import com.sidr.launcher.domain.result.OperationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AliasStoreImplTest {
    private lateinit var db: SidrDatabase
    private lateinit var store: AliasStoreImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = AliasStoreImpl(db.aliasDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `round-trips an alias`() = runTest {
        store.upsert(Alias("work chat", AliasTarget.App("com.telegram"), 5L))

        val found = store.find("work chat")

        assertTrue(found is OperationResult.Success<*>)
        found as OperationResult.Success<Alias?>
        assertEquals(AliasTarget.App("com.telegram"), found.value?.target)
        assertEquals(listOf("work chat"), store.observeAll().first().map { it.phrase })
    }

    @Test
    fun `find missing returns Success null`() = runTest {
        val result = store.find("nope")

        assertTrue(result is OperationResult.Success<*> && result.value == null)
    }
}
