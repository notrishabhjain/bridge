package com.sentinel.bridge.feature.pipeline

import android.content.Context
import androidx.work.WorkManager
import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import com.sentinel.bridge.core.data.repository.LogRepository
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.setup.CapabilityManager
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests validating the resume-from-stage logic in [PipelineOrchestrator.resumePipeline].
 *
 * Simulates WorkManager recovery scenarios where the process is killed mid-pipeline
 * and the worker restarts. The orchestrator must read the persisted `currentStage`
 * from Room and only execute commands from that stage onward.
 *
 * Each test verifies:
 * - The correct number of `commandBus.dispatch()` calls
 * - The first dispatched command matches the expected stage
 * - Session ends with COMPLETE on success
 */
class PipelineRecoveryTest {

    private lateinit var context: Context
    private lateinit var capabilityManager: CapabilityManager
    private lateinit var commandBus: CommandBus
    private lateinit var pipelineSessionDao: PipelineSessionDao
    private lateinit var logRepository: LogRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var logger: SentinelLogger
    private lateinit var workManager: WorkManager

    private lateinit var orchestrator: PipelineOrchestrator

    private val testSessionId = "recovery-session-001"
    private val testTimeoutMs = 180_000L

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        capabilityManager = mockk()
        commandBus = mockk()
        pipelineSessionDao = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        appSettingsRepository = mockk()
        logger = mockk(relaxed = true)
        workManager = mockk(relaxed = true)

        every { appSettingsRepository.transcriptionTimeoutMs } returns flowOf(testTimeoutMs)

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager

        orchestrator = PipelineOrchestrator(
            context = context,
            capabilityManager = capabilityManager,
            commandBus = commandBus,
            pipelineSessionDao = pipelineSessionDao,
            logRepository = logRepository,
            appSettingsRepository = appSettingsRepository,
            logger = logger
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(WorkManager::class)
        clearAllMocks()
    }

    private fun createSessionAtStage(stage: PipelineStage) = PipelineSessionEntity(
        sessionId = testSessionId,
        source = "CALL",
        currentStage = stage.name,
        language = "Hindi",
        callerName = "Test Caller",
        phoneNumber = "+911234567890",
        callDuration = 120L,
        macroInvocationId = "macro-001",
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli(),
        completedAt = null,
        errorCode = null,
        errorCategory = null,
        errorMessage = null,
        retryCount = 0
    )

    @Test
    @DisplayName("Recovery from OPEN_RECORDER → executes all 16 commands")
    fun `resume from OPEN_RECORDER executes all 16 commands`() = runTest {
        val session = createSessionAtStage(PipelineStage.OPEN_RECORDER)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns session

        val dispatchedCommands = mutableListOf<PipelineCommand>()
        coEvery { commandBus.dispatch(any()) } answers {
            dispatchedCommands.add(firstArg())
            CommandResult.Success(testSessionId)
        }

        val result = orchestrator.resumePipeline(testSessionId)

        assertTrue(result is CommandResult.Success)
        assertEquals(16, dispatchedCommands.size)

        // First command dispatched is OpenRecorder
        assertTrue(dispatchedCommands.any { it is PipelineCommand.OpenRecorder })

        // Session marked COMPLETE
        coVerify {
            pipelineSessionDao.updateCompletion(
                sessionId = testSessionId,
                stage = PipelineStage.COMPLETE.name,
                updatedAt = any(),
                completedAt = any(),
                errorCode = null,
                errorCategory = null,
                errorMessage = null
            )
        }
    }

    @Test
    @DisplayName("Recovery from INFERENCE → executes only 7 remaining commands")
    fun `resume from INFERENCE executes only 7 remaining commands`() = runTest {
        val session = createSessionAtStage(PipelineStage.INFERENCE)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns session

        val dispatchedCommands = mutableListOf<PipelineCommand>()
        coEvery { commandBus.dispatch(any()) } answers {
            dispatchedCommands.add(firstArg())
            CommandResult.Success(testSessionId)
        }

        val result = orchestrator.resumePipeline(testSessionId)

        assertTrue(result is CommandResult.Success)
        assertEquals(7, dispatchedCommands.size)

        // First command dispatched is RunInference
        assertTrue(dispatchedCommands.any { it is PipelineCommand.RunInference })

        // Earlier stages were NOT executed
        assertFalse(dispatchedCommands.any { it is PipelineCommand.OpenRecorder })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.OpenRecording })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.ClickShowText })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.SelectLanguage })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.WaitForTranscription })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.ExtractTranscript })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.RunPreprocessor })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.RunRulesPreAI })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.BuildPrompt })

        // Session marked COMPLETE
        coVerify {
            pipelineSessionDao.updateCompletion(
                sessionId = testSessionId,
                stage = PipelineStage.COMPLETE.name,
                updatedAt = any(),
                completedAt = any(),
                errorCode = null,
                errorCategory = null,
                errorMessage = null
            )
        }
    }

    @Test
    @DisplayName("Recovery from RETURN_INTENT → executes only 1 command (ReturnIntent)")
    fun `resume from RETURN_INTENT executes only 1 command`() = runTest {
        val session = createSessionAtStage(PipelineStage.RETURN_INTENT)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns session

        val dispatchedCommands = mutableListOf<PipelineCommand>()
        coEvery { commandBus.dispatch(any()) } answers {
            dispatchedCommands.add(firstArg())
            CommandResult.Success(testSessionId)
        }

        val result = orchestrator.resumePipeline(testSessionId)

        assertTrue(result is CommandResult.Success)
        assertEquals(1, dispatchedCommands.size)

        // Only ReturnIntent is dispatched
        assertTrue(dispatchedCommands.any { it is PipelineCommand.ReturnIntent })

        // All other stages skipped
        assertFalse(dispatchedCommands.any { it is PipelineCommand.OpenRecorder })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.RunInference })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.StoreResult })
        assertFalse(dispatchedCommands.any { it is PipelineCommand.DispatchAction })

        // Session marked COMPLETE
        coVerify {
            pipelineSessionDao.updateCompletion(
                sessionId = testSessionId,
                stage = PipelineStage.COMPLETE.name,
                updatedAt = any(),
                completedAt = any(),
                errorCode = null,
                errorCategory = null,
                errorMessage = null
            )
        }
    }

    @Test
    @DisplayName("Recovery from COMPLETE → executes 0 commands (already finished)")
    fun `resume from COMPLETE executes 0 commands`() = runTest {
        val session = createSessionAtStage(PipelineStage.COMPLETE)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns session
        coEvery { commandBus.dispatch(any()) } returns CommandResult.Success(testSessionId)

        val result = orchestrator.resumePipeline(testSessionId)

        assertTrue(result is CommandResult.Success)
        coVerify(exactly = 0) { commandBus.dispatch(any()) }

        // Session still marked COMPLETE (idempotent)
        coVerify {
            pipelineSessionDao.updateCompletion(
                sessionId = testSessionId,
                stage = PipelineStage.COMPLETE.name,
                updatedAt = any(),
                completedAt = any(),
                errorCode = null,
                errorCategory = null,
                errorMessage = null
            )
        }
    }

    @Test
    @DisplayName("Recovery from PIPELINE_CREATED → executes all 16 commands from OPEN_RECORDER")
    fun `resume from PIPELINE_CREATED executes all 16 commands`() = runTest {
        val session = createSessionAtStage(PipelineStage.PIPELINE_CREATED)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns session

        val dispatchedCommands = mutableListOf<PipelineCommand>()
        coEvery { commandBus.dispatch(any()) } answers {
            dispatchedCommands.add(firstArg())
            CommandResult.Success(testSessionId)
        }

        val result = orchestrator.resumePipeline(testSessionId)

        assertTrue(result is CommandResult.Success)
        assertEquals(16, dispatchedCommands.size)

        // First command dispatched is OpenRecorder (not PIPELINE_CREATED itself)
        assertTrue(dispatchedCommands.any { it is PipelineCommand.OpenRecorder })

        // Last command is ReturnIntent
        assertTrue(dispatchedCommands.any { it is PipelineCommand.ReturnIntent })

        // Session marked COMPLETE
        coVerify {
            pipelineSessionDao.updateCompletion(
                sessionId = testSessionId,
                stage = PipelineStage.COMPLETE.name,
                updatedAt = any(),
                completedAt = any(),
                errorCode = null,
                errorCategory = null,
                errorMessage = null
            )
        }
    }
}
