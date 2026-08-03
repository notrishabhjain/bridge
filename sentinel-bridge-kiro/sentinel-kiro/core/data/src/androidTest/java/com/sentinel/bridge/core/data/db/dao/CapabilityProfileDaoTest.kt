package com.sentinel.bridge.core.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.sentinel.bridge.core.data.db.SentinelDatabase
import com.sentinel.bridge.core.data.db.entity.CapabilityProfileEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CapabilityProfileDaoTest {

    private lateinit var database: SentinelDatabase
    private lateinit var dao: CapabilityProfileDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.capabilityProfileDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProfile(
        recordedAt: Long = System.currentTimeMillis(),
        recorderVersion: String = "4.5.0"
    ) = CapabilityProfileEntity(
        id = 0,
        version = 1,
        recorderPackage = "com.miui.voicerecorder",
        recorderVersion = recorderVersion,
        hyperOsVersion = "1.0.2",
        availableNodes = """["record_button","stop_button","timer_text"]""",
        recordedAt = recordedAt
    )

    @Test
    fun insertAndRetrieveLatest() = runTest {
        val profile = createProfile(recordedAt = 1000)
        dao.insert(profile)

        val latest = dao.getLatest()
        assertNotNull(latest)
        assertEquals("com.miui.voicerecorder", latest!!.recorderPackage)
        assertEquals("4.5.0", latest.recorderVersion)
        assertEquals(1000L, latest.recordedAt)
    }

    @Test
    fun multipleInsertsGetLatestReturnsMostRecent() = runTest {
        dao.insert(createProfile(recordedAt = 1000, recorderVersion = "4.3.0"))
        dao.insert(createProfile(recordedAt = 2000, recorderVersion = "4.4.0"))
        dao.insert(createProfile(recordedAt = 3000, recorderVersion = "4.5.0"))

        val latest = dao.getLatest()
        assertNotNull(latest)
        assertEquals("4.5.0", latest!!.recorderVersion)
        assertEquals(3000L, latest.recordedAt)
    }

    @Test
    fun deleteByIdRemovesSpecificProfile() = runTest {
        dao.insert(createProfile(recordedAt = 1000, recorderVersion = "4.3.0"))
        dao.insert(createProfile(recordedAt = 2000, recorderVersion = "4.4.0"))

        // Get all to find the first profile's ID
        val latest = dao.getLatest()
        assertNotNull(latest)

        // Delete the latest profile
        dao.deleteById(latest!!.id)

        // The remaining profile should now be the latest
        val newLatest = dao.getLatest()
        assertNotNull(newLatest)
        assertEquals("4.3.0", newLatest!!.recorderVersion)
    }

    @Test
    fun deleteByIdWithNonExistentIdDoesNothing() = runTest {
        dao.insert(createProfile(recordedAt = 1000))

        dao.deleteById(9999L)

        val latest = dao.getLatest()
        assertNotNull(latest)
    }

    @Test
    fun getLatestReturnsNullWhenEmpty() = runTest {
        val latest = dao.getLatest()
        assertNull(latest)
    }

    @Test
    fun observeAllEmitsListInCorrectOrder() = runTest {
        dao.observeAll().test {
            // Initial empty emission
            assertEquals(emptyList<CapabilityProfileEntity>(), awaitItem())

            // Insert profiles out of order
            dao.insert(createProfile(recordedAt = 1000, recorderVersion = "4.3.0"))
            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.size)

            dao.insert(createProfile(recordedAt = 3000, recorderVersion = "4.5.0"))
            val afterSecond = awaitItem()
            assertEquals(2, afterSecond.size)
            // Ordered by recordedAt DESC
            assertEquals("4.5.0", afterSecond[0].recorderVersion)
            assertEquals("4.3.0", afterSecond[1].recorderVersion)

            dao.insert(createProfile(recordedAt = 2000, recorderVersion = "4.4.0"))
            val afterThird = awaitItem()
            assertEquals(3, afterThird.size)
            assertEquals("4.5.0", afterThird[0].recorderVersion)
            assertEquals("4.4.0", afterThird[1].recorderVersion)
            assertEquals("4.3.0", afterThird[2].recorderVersion)

            cancelAndConsumeRemainingEvents()
        }
    }
}
