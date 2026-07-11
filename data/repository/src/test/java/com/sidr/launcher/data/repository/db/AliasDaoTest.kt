package com.sidr.launcher.data.repository.db

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sidr.launcher.data.repository.db.dao.AliasDao
import com.sidr.launcher.data.repository.db.entity.AliasEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AliasDaoTest {
    private lateinit var db: SidrDatabase
    private lateinit var dao: AliasDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        db = Room.inMemoryDatabaseBuilder(context, SidrDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.aliasDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `upsert then find returns entity`() = runTest {
        dao.upsert(AliasEntity("work chat", "app", "com.telegram", 5L))

        assertEquals("com.telegram", dao.findByPhrase("work chat")?.targetPackage)
    }

    @Test
    fun `upsert replaces on same phrase`() = runTest {
        dao.upsert(AliasEntity("bank", "app", "com.a", 1L))
        dao.upsert(AliasEntity("bank", "app", "com.b", 2L))

        assertEquals("com.b", dao.findByPhrase("bank")?.targetPackage)
        assertEquals(1, dao.observeAll().first().size)
    }

    @Test
    fun `deleteByPhrase removes`() = runTest {
        dao.upsert(AliasEntity("bank", "app", "com.a", 1L))

        dao.deleteByPhrase("bank")

        assertNull(dao.findByPhrase("bank"))
    }
}
