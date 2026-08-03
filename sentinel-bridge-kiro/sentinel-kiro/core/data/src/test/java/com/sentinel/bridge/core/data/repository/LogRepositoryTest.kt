package com.sentinel.bridge.core.data.repository

import com.sentinel.bridge.core.data.db.dao.LogEntryDao
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [LogRepository.insertWithRotation] verifying that the
 * 100-session rotation policy is correctly enforced.
 */
class LogRepositoryTest {

    private lateinit var pipelineSessionDao: PipelineSessionDao
    private lateinit var logEntryDao: LogEntryDao
    private lateinit var repository: LogRepository

    private val testSession = PipelineSessionEntity(
        sessionId = "test-session-1",
        source = "CALL",
        currentStage = "IDLE",
        language = "hi",
        callerName = "Test User",
        phoneNumber = "+911234567890",
        callDuration = 300L,
        macroInvocationId = "macro-1",
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = null,
        errorCode = null,
        errorCategory = null,
        errorMessage = null,
        retryCount = 0
    )

    @BeforeEach
    fun setUp() {
        pipelineSessionDao = mockk()
        logEntryDao = mockk()
        repository = LogRepository(pipelineSessionDao, logEntryDao)
    }

    @Test
    fun `insertWithRotation - when session count is below limit, no deletion occurs`() = runTest {
        // Arrange: 50 sessions after insert (well below 100)
        coEvery { pipelineSessionDao.insert(any()) } just Runs
        coEvery { pipelineSessionDao.count() } returns 50

        // Act
        repository.insertWithRotation(testSession)

        // Assert
        coVerify(exactly = 1) { pipelineSessionDao.insert(testSession) }
        coVerify(exactly = 0) { pipelineSessionDao.deleteOldest(any()) }
    }

    @Test
    fun `insertWithRotation - when session count equals 100, no deletion occurs`() = runTest {
        // Arrange: exactly at the boundary (100 sessions)
        coEvery { pipelineSessionDao.insert(any()) } just Runs
        coEvery { pipelineSessionDao.count() } returns 100

        // Act
        repository.insertWithRotation(testSession)

        // Assert
        coVerify(exactly = 1) { pipelineSessionDao.insert(testSession) }
        coVerify(exactly = 0) { pipelineSessionDao.deleteOldest(any()) }
    }

    @Test
    fun `insertWithRotation - when session count is 101, exactly 1 is deleted`() = runTest {
        // Arrange: one over the limit
        coEvery { pipelineSessionDao.insert(any()) } just Runs
        coEvery { pipelineSessionDao.count() } returns 101
        coEvery { pipelineSessionDao.deleteOldest(any()) } just Runs

        // Act
        repository.insertWithRotation(testSession)

        // Assert
        coVerify(exactly = 1) { pipelineSessionDao.insert(testSession) }
        coVerify(exactly = 1) { pipelineSessionDao.deleteOldest(1) }
    }

    @Test
    fun `insertWithRotation - when session count is 150, 50 oldest are deleted`() = runTest {
        // Arrange: 50 over the limit
        coEvery { pipelineSessionDao.insert(any()) } just Runs
        coEvery { pipelineSessionDao.count() } returns 150
        coEvery { pipelineSessionDao.deleteOldest(any()) } just Runs

        // Act
        repository.insertWithRotation(testSession)

        // Assert
        coVerify(exactly = 1) { pipelineSessionDao.insert(testSession) }
        coVerify(exactly = 1) { pipelineSessionDao.deleteOldest(50) }
    }

    @Test
    fun `insertLogEntry delegates to logEntryDao insert`() = runTest {
        // Arrange
        val entry = LogEntryEntity(
            id = 0,
            sessionId = "test-session-1",
            stage = "INFERENCE",
            level = "INFO",
            message = "Inference completed",
            durationMs = 1500L,
            status = "SUCCESS",
            timestamp = System.currentTimeMillis()
        )
        coEvery { logEntryDao.insert(any()) } just Runs

        // Act
        repository.insertLogEntry(entry)

        // Assert
        coVerify(exactly = 1) { logEntryDao.insert(entry) }
    }

    @Test
    fun `getSessionById delegates to pipelineSessionDao getById`() = runTest {
        // Arrange
        val sessionId = "test-session-1"
        coEvery { pipelineSessionDao.getById(sessionId) } returns testSession

        // Act
        val result = repository.getSessionById(sessionId)

        // Assert
        assert(result == testSession)
        coVerify(exactly = 1) { pipelineSessionDao.getById(sessionId) }
    }
}
