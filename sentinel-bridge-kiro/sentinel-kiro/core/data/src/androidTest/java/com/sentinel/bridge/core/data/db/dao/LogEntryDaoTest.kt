package com.sentinel.bridge.core.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sentinel.bridge.core.data.db.SentinelDatabase
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LogEntryDaoTest {

    private lateinit var database: SentinelDatabase
    private lateinit var logEntryDao: LogEntryDao
    private lateinit var sessionDao: PipelineSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        logEntryDao = database.logEntryDao()
        sessionDao = database.pipelineSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSession(sessionId: String = "session-1") = PipelineSessionEntity(
        sessionId = sessionId,
        source = "MACRODROID",
        currentStage = "INTAKE",
        language = "hi",
        callerName = "Test User",
        phoneNumber = "+911234567890",
        callDuration = 120L,
        macroInvocationId = "macro-1",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = null,
        errorCode = null,
        errorCategory = null,
        errorMessage = null,
        retryCount = 0
    )

    private fun createLogEntry(
        sessionId: String = "session-1",
        timestamp: Long = System.currentTimeMillis(),
        stage: String = "INTAKE",
        level: String = "INFO",
        message: String = "Test log message"
    ) = LogEntryEntity(
        id = 0,
        sessionId = sessionId,
        stage = stage,
        level = level,
        message = message,
        durationMs = 150L,
        status = "SUCCESS",
        timestamp = timestamp
    )

    @Test
    fun insertSingleEntryAndRetrieveBySession() = runTest {
        sessionDao.insert(createSession())
        val entry = createLogEntry(timestamp = 1000)
        logEntryDao.insert(entry)

        val entries = logEntryDao.getBySession("session-1")
        assertEquals(1, entries.size)
        assertEquals("Test log message", entries[0].message)
        assertEquals("INTAKE", entries[0].stage)
    }

    @Test
    fun insertMultipleEntriesViaInsertAll() = runTest {
        sessionDao.insert(createSession())
        val entries = listOf(
            createLogEntry(timestamp = 1000, message = "First"),
            createLogEntry(timestamp = 2000, message = "Second"),
            createLogEntry(timestamp = 3000, message = "Third")
        )
        logEntryDao.insertAll(entries)

        val retrieved = logEntryDao.getBySession("session-1")
        assertEquals(3, retrieved.size)
    }

    @Test
    fun getBySessionReturnsEntriesInTimestampOrder() = runTest {
        sessionDao.insert(createSession())
        // Insert out of order
        logEntryDao.insert(createLogEntry(timestamp = 3000, message = "Third"))
        logEntryDao.insert(createLogEntry(timestamp = 1000, message = "First"))
        logEntryDao.insert(createLogEntry(timestamp = 2000, message = "Second"))

        val entries = logEntryDao.getBySession("session-1")
        assertEquals(3, entries.size)
        assertEquals("First", entries[0].message)
        assertEquals("Second", entries[1].message)
        assertEquals("Third", entries[2].message)
    }

    @Test
    fun deleteBySessionRemovesAllEntriesForSession() = runTest {
        sessionDao.insert(createSession("session-1"))
        sessionDao.insert(createSession("session-2"))

        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 1000))
        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 2000))
        logEntryDao.insert(createLogEntry(sessionId = "session-2", timestamp = 3000))

        logEntryDao.deleteBySession("session-1")

        val session1Entries = logEntryDao.getBySession("session-1")
        val session2Entries = logEntryDao.getBySession("session-2")
        assertTrue(session1Entries.isEmpty())
        assertEquals(1, session2Entries.size)
    }

    @Test
    fun cascadeDeleteRemovesLogEntriesWhenSessionDeleted() = runTest {
        sessionDao.insert(createSession("session-1"))
        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 1000, message = "Entry 1"))
        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 2000, message = "Entry 2"))

        // Verify entries exist
        assertEquals(2, logEntryDao.getBySession("session-1").size)

        // Delete the parent session — cascade should remove log entries
        sessionDao.deleteById("session-1")

        val entries = logEntryDao.getBySession("session-1")
        assertTrue(entries.isEmpty())
    }

    @Test
    fun getDistinctSessionIdsReturnsUniqueSessionIds() = runTest {
        sessionDao.insert(createSession("session-1"))
        sessionDao.insert(createSession("session-2"))
        sessionDao.insert(createSession("session-3"))

        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 1000))
        logEntryDao.insert(createLogEntry(sessionId = "session-1", timestamp = 2000))
        logEntryDao.insert(createLogEntry(sessionId = "session-2", timestamp = 3000))
        logEntryDao.insert(createLogEntry(sessionId = "session-3", timestamp = 4000))
        logEntryDao.insert(createLogEntry(sessionId = "session-3", timestamp = 5000))

        val distinctIds = logEntryDao.getDistinctSessionIds()
        assertEquals(3, distinctIds.size)
        assertTrue(distinctIds.contains("session-1"))
        assertTrue(distinctIds.contains("session-2"))
        assertTrue(distinctIds.contains("session-3"))
    }
}
