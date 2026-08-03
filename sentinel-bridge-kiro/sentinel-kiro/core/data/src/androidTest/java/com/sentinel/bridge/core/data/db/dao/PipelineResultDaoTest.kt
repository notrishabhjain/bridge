package com.sentinel.bridge.core.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.sentinel.bridge.core.data.db.SentinelDatabase
import com.sentinel.bridge.core.data.db.entity.PipelineResultEntity
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PipelineResultDaoTest {

    private lateinit var database: SentinelDatabase
    private lateinit var resultDao: PipelineResultDao
    private lateinit var sessionDao: PipelineSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        resultDao = database.pipelineResultDao()
        sessionDao = database.pipelineSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSession(sessionId: String = "session-1", createdAt: Long = 1000L) =
        PipelineSessionEntity(
            sessionId = sessionId,
            source = "MACRODROID",
            currentStage = "COMPLETE",
            language = "hi",
            callerName = "Test User",
            phoneNumber = "+911234567890",
            callDuration = 120L,
            macroInvocationId = "macro-1",
            createdAt = createdAt,
            updatedAt = createdAt,
            completedAt = createdAt + 5000,
            errorCode = null,
            errorCategory = null,
            errorMessage = null,
            retryCount = 0
        )

    private fun createResult(
        sessionId: String = "session-1",
        createdAt: Long = 1000L,
        summary: String = "Call summary"
    ) = PipelineResultEntity(
        sessionId = sessionId,
        summary = summary,
        confidence = 0.85f,
        tasksJson = """[{"title":"Follow up"}]""",
        calendarEventsJson = "[]",
        followUpsJson = "[]",
        peopleJson = """["John"]""",
        projectsJson = "[]",
        processingTimeMs = 3200L,
        model = "gemini-1.5-flash",
        promptVersion = "v1",
        pipelineVersion = "1.0.0",
        createdAt = createdAt
    )

    @Test
    fun insertAndRetrieveBySessionId() = runTest {
        sessionDao.insert(createSession())
        val result = createResult()
        resultDao.insert(result)

        val retrieved = resultDao.getBySessionId("session-1")
        assertNotNull(retrieved)
        assertEquals("Call summary", retrieved!!.summary)
        assertEquals(0.85f, retrieved.confidence, 0.001f)
        assertEquals("gemini-1.5-flash", retrieved.model)
    }

    @Test
    fun replaceStrategyInsertTwiceWithSameSessionIdOnlyOneRecordExists() = runTest {
        sessionDao.insert(createSession())

        val firstResult = createResult(summary = "First summary")
        resultDao.insert(firstResult)

        val secondResult = createResult(summary = "Updated summary")
        resultDao.insert(secondResult)

        val retrieved = resultDao.getBySessionId("session-1")
        assertNotNull(retrieved)
        assertEquals("Updated summary", retrieved!!.summary)

        // Verify only one record exists by observing all
        resultDao.observeAll().test {
            val all = awaitItem()
            assertEquals(1, all.size)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun cascadeDeleteRemovesResultWhenSessionDeleted() = runTest {
        sessionDao.insert(createSession())
        resultDao.insert(createResult())

        // Verify result exists
        assertNotNull(resultDao.getBySessionId("session-1"))

        // Delete the parent session — cascade should remove the result
        sessionDao.deleteById("session-1")

        val result = resultDao.getBySessionId("session-1")
        assertNull(result)
    }

    @Test
    fun observeRecentLimitsResultsCorrectly() = runTest {
        // Insert 3 sessions with results
        sessionDao.insert(createSession(sessionId = "s1", createdAt = 1000))
        sessionDao.insert(createSession(sessionId = "s2", createdAt = 2000))
        sessionDao.insert(createSession(sessionId = "s3", createdAt = 3000))

        resultDao.insert(createResult(sessionId = "s1", createdAt = 1000, summary = "Oldest"))
        resultDao.insert(createResult(sessionId = "s2", createdAt = 2000, summary = "Middle"))
        resultDao.insert(createResult(sessionId = "s3", createdAt = 3000, summary = "Newest"))

        resultDao.observeRecent(2).test {
            val results = awaitItem()
            assertEquals(2, results.size)
            // Ordered by createdAt DESC, limited to 2
            assertEquals("Newest", results[0].summary)
            assertEquals("Middle", results[1].summary)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun getBySessionIdReturnsNullForNonExistentSession() = runTest {
        val result = resultDao.getBySessionId("non-existent")
        assertNull(result)
    }
}
