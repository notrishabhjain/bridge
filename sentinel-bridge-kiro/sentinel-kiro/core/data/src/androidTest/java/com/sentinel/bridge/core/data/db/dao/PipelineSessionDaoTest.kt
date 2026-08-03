package com.sentinel.bridge.core.data.db.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.sentinel.bridge.core.data.db.SentinelDatabase
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
class PipelineSessionDaoTest {

    private lateinit var database: SentinelDatabase
    private lateinit var dao: PipelineSessionDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, SentinelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.pipelineSessionDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createSession(
        sessionId: String = "session-1",
        createdAt: Long = System.currentTimeMillis(),
        stage: String = "INTAKE"
    ) = PipelineSessionEntity(
        sessionId = sessionId,
        source = "MACRODROID",
        currentStage = stage,
        language = "hi",
        callerName = "Test User",
        phoneNumber = "+911234567890",
        callDuration = 120L,
        macroInvocationId = "macro-1",
        createdAt = createdAt,
        updatedAt = createdAt,
        completedAt = null,
        errorCode = null,
        errorCategory = null,
        errorMessage = null,
        retryCount = 0
    )

    @Test
    fun insertAndRetrieveById() = runTest {
        val session = createSession()
        dao.insert(session)

        val retrieved = dao.getById("session-1")
        assertNotNull(retrieved)
        assertEquals(session.sessionId, retrieved!!.sessionId)
        assertEquals(session.source, retrieved.source)
        assertEquals(session.language, retrieved.language)
    }

    @Test
    fun updateSessionAndVerifyChanges() = runTest {
        val session = createSession()
        dao.insert(session)

        val updated = session.copy(
            callerName = "Updated User",
            retryCount = 2,
            updatedAt = session.updatedAt + 1000
        )
        dao.update(updated)

        val retrieved = dao.getById("session-1")
        assertNotNull(retrieved)
        assertEquals("Updated User", retrieved!!.callerName)
        assertEquals(2, retrieved.retryCount)
    }

    @Test
    fun updateStageUpdatesOnlyStageAndUpdatedAt() = runTest {
        val session = createSession()
        dao.insert(session)

        val newUpdatedAt = session.updatedAt + 5000
        dao.updateStage("session-1", "TRANSCRIPTION", newUpdatedAt)

        val retrieved = dao.getById("session-1")
        assertNotNull(retrieved)
        assertEquals("TRANSCRIPTION", retrieved!!.currentStage)
        assertEquals(newUpdatedAt, retrieved.updatedAt)
        // Other fields remain unchanged
        assertEquals(session.callerName, retrieved.callerName)
        assertEquals(session.source, retrieved.source)
        assertNull(retrieved.completedAt)
    }

    @Test
    fun updateCompletionUpdatesCompletionFields() = runTest {
        val session = createSession()
        dao.insert(session)

        val completedAt = session.updatedAt + 10000
        dao.updateCompletion(
            sessionId = "session-1",
            stage = "COMPLETE",
            updatedAt = completedAt,
            completedAt = completedAt,
            errorCode = null,
            errorCategory = null,
            errorMessage = null
        )

        val retrieved = dao.getById("session-1")
        assertNotNull(retrieved)
        assertEquals("COMPLETE", retrieved!!.currentStage)
        assertEquals(completedAt, retrieved.completedAt)
        assertNull(retrieved.errorCode)
    }

    @Test
    fun updateCompletionWithError() = runTest {
        val session = createSession()
        dao.insert(session)

        val failedAt = session.updatedAt + 10000
        dao.updateCompletion(
            sessionId = "session-1",
            stage = "INFERENCE",
            updatedAt = failedAt,
            completedAt = failedAt,
            errorCode = "LLM_TIMEOUT",
            errorCategory = "TRANSIENT",
            errorMessage = "LLM request timed out"
        )

        val retrieved = dao.getById("session-1")
        assertNotNull(retrieved)
        assertEquals("LLM_TIMEOUT", retrieved!!.errorCode)
        assertEquals("TRANSIENT", retrieved.errorCategory)
        assertEquals("LLM request timed out", retrieved.errorMessage)
    }

    @Test
    fun countReturnsCorrectCount() = runTest {
        assertEquals(0, dao.count())

        dao.insert(createSession(sessionId = "s1", createdAt = 1000))
        dao.insert(createSession(sessionId = "s2", createdAt = 2000))
        dao.insert(createSession(sessionId = "s3", createdAt = 3000))

        assertEquals(3, dao.count())
    }

    @Test
    fun deleteOldestRemovesCorrectNumberOfOldestSessions() = runTest {
        dao.insert(createSession(sessionId = "oldest", createdAt = 1000))
        dao.insert(createSession(sessionId = "middle", createdAt = 2000))
        dao.insert(createSession(sessionId = "newest", createdAt = 3000))

        dao.deleteOldest(2)

        assertEquals(1, dao.count())
        assertNull(dao.getById("oldest"))
        assertNull(dao.getById("middle"))
        assertNotNull(dao.getById("newest"))
    }

    @Test
    fun observeAllEmitsUpdatedListAfterInsert() = runTest {
        dao.observeAll().test {
            // Initial empty list
            assertEquals(emptyList<PipelineSessionEntity>(), awaitItem())

            // Insert first session
            dao.insert(createSession(sessionId = "s1", createdAt = 2000))
            val afterFirst = awaitItem()
            assertEquals(1, afterFirst.size)
            assertEquals("s1", afterFirst[0].sessionId)

            // Insert second session (newer, should appear first due to DESC order)
            dao.insert(createSession(sessionId = "s2", createdAt = 3000))
            val afterSecond = awaitItem()
            assertEquals(2, afterSecond.size)
            assertEquals("s2", afterSecond[0].sessionId)
            assertEquals("s1", afterSecond[1].sessionId)

            cancelAndConsumeRemainingEvents()
        }
    }
}
