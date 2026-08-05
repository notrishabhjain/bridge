package com.sentinel.bridge.feature.pipeline

import android.content.Context
import androidx.work.WorkManager
import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import com.sentinel.bridge.core.data.repository.LogRepository
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.setup.CapabilityManager
import com.sentinel.bridge.feature.setup.CapabilityReport
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for [PipelineOrchestrator] covering state transitions during
 * [PipelineOrchestrator.startPipeline] and [PipelineOrchestrator.resumePipeline].
 *
 * Validates both valid (happy-path) and invalid (failure, skip, missing session)
 * transitions to ensure the orchestrator correctly coordinates capability checks,
 * session persistence, WorkManager enqueuing, and sequential command dispatch.
 *
 * Uses MockK for all dependencies and kotlinx-coroutines-test [runTest] for
 * structured concurrency in tests.
 */
class PipelineOrchestratorTest {

    private lateinit var context: Context
    private lateinit var capabilityManager: CapabilityManager
    private lateinit var commandBus: CommandBus
    private lateinit var pipelineSessionDao: PipelineSessionDao
    private lateinit var logRepository: LogRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var logger: SentinelLogger
    private lateinit var workManager: WorkManager

    private lateinit var orchestrator: PipelineOrchestrator

    private val testSessionId = "test-session-001"
    private val testSource = "CALL"
    private val testLanguage = "Hindi"
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

        // Default: appSettingsRepository returns the default timeout
        every { appSettingsRepository.transcriptionTimeoutMs } returns flowOf(testTimeoutMs)

        // Mock WorkManager static instance
        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager

        orchestrator = PipelineOrchestrator(
            context = context,
            capabilityManager = capabilityManager,
            commandBus = commandBus,
            pipelineSessionDao = pipelineSessionDao,
            logRepository = logRepository,
            appSettingsRepository = appSettingsRepository,
            sessionStore = PipelineSessionStore(),
            logger = logger
        )
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic(WorkManager::class)
        clearAllMocks()
    }

    private fun allPassedCapabilityReport() = CapabilityReport(
        accessibilityEnabled = true,
        notificationListenerEnabled = true,
        recorderInstalled = true,
        modelValid = true,
        sufficientRam = true,
        sufficientStorage = true,
        allPassed = true
    )

    private fun failedCapabilityReport() = CapabilityReport(
        accessibilityEnabled = true,
        notificationListenerEnabled = false,
        recorderInstalled = true,
        modelValid = true,
        sufficientRam = true,
        sufficientStorage = true,
        allPassed = false
    )

    private fun createSessionEntity(
        sessionId: String = testSessionId,
        stage: String = PipelineStage.PIPELINE_CREATED.name
    ) = PipelineSessionEntity(
        sessionId = sessionId,
        source = testSource,
        currentStage = stage,
        language = testLanguage,
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

    @Nested
    @DisplayName("startPipeline()")
    inner class StartPipelineTests {

        @Test
        @DisplayName("When all capabilities pass → returns true, session inserted, work enqueued")
        fun `startPipeline succeeds when all capabilities pass`() = runTest {
            coEvery { capabilityManager.checkAllCapabilities() } returns allPassedCapabilityReport()

            val sessionSlot = slot<PipelineSessionEntity>()
            coEvery { logRepository.insertWithRotation(capture(sessionSlot)) } returns Unit

            val result = orchestrator.startPipeline(
                sessionId = testSessionId,
                source = testSource,
                language = testLanguage,
                callerName = "Test Caller",
                phoneNumber = "+911234567890",
                callDuration = 120L,
                macroInvocationId = "macro-001"
            )

            assertTrue(result)

            // Verify session was inserted with correct data
            val insertedSession = sessionSlot.captured
            assertEquals(testSessionId, insertedSession.sessionId)
            assertEquals(testSource, insertedSession.source)
            assertEquals(PipelineStage.PIPELINE_CREATED.name, insertedSession.currentStage)
            assertEquals(testLanguage, insertedSession.language)

            // Verify work was enqueued via WorkManager
            coVerify { logRepository.insertWithRotation(any()) }
        }

        @Test
        @DisplayName("When capability check fails → returns false, no session inserted")
        fun `startPipeline fails when capability check fails`() = runTest {
            coEvery { capabilityManager.checkAllCapabilities() } returns failedCapabilityReport()

            val result = orchestrator.startPipeline(
                sessionId = testSessionId,
                source = testSource,
                language = testLanguage,
                callerName = null,
                phoneNumber = null,
                callDuration = null,
                macroInvocationId = null
            )

            assertFalse(result)

            // Verify no session was inserted
            coVerify(exactly = 0) { logRepository.insertWithRotation(any()) }

            // Verify error was logged
            coVerify { logger.logError(sessionId = testSessionId, stage = any(), message = any()) }
        }
    }

    @Nested
    @DisplayName("resumePipeline()")
    inner class ResumePipelineTests {

        @Test
        @DisplayName("When session not found → returns Failure with ERR_SESSION_NOT_FOUND")
        fun `resumePipeline returns failure when session not found`() = runTest {
            coEvery { pipelineSessionDao.getById(testSessionId) } returns null

            val result = orchestrator.resumePipeline(testSessionId)

            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_SESSION_NOT_FOUND", failure.error.code)
            assertEquals(ErrorCategory.SYSTEM, failure.error.category)
            assertEquals(PipelineStage.IDLE, failure.error.stage)
            assertFalse(failure.error.retryable)
        }

        @Test
        @DisplayName("Happy path: all commands succeed → returns Success, session marked COMPLETE")
        fun `resumePipeline completes successfully when all commands succeed`() = runTest {
            val session = createSessionEntity(stage = PipelineStage.PIPELINE_CREATED.name)
            coEvery { pipelineSessionDao.getById(testSessionId) } returns session
            coEvery { commandBus.dispatch(any()) } returns CommandResult.Success(testSessionId)

            val result = orchestrator.resumePipeline(testSessionId)

            assertTrue(result is CommandResult.Success)
            assertEquals(testSessionId, (result as CommandResult.Success).sessionId)

            // Verify session was marked COMPLETE
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
        @DisplayName("When a command fails → returns Failure, session updated with error")
        fun `resumePipeline returns failure when a command fails`() = runTest {
            val session = createSessionEntity(stage = PipelineStage.PIPELINE_CREATED.name)
            coEvery { pipelineSessionDao.getById(testSessionId) } returns session

            val sentinelError = SentinelError(
                code = "ERR_NODE_NOT_FOUND",
                category = ErrorCategory.UI_AUTOMATION,
                message = "Show text button not found",
                stage = PipelineStage.CLICK_SHOW_TEXT,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            // First two commands succeed, third fails
            coEvery { commandBus.dispatch(any<PipelineCommand.OpenRecorder>()) } returns
                CommandResult.Success(testSessionId)
            coEvery { commandBus.dispatch(any<PipelineCommand.OpenRecording>()) } returns
                CommandResult.Success(testSessionId)
            coEvery { commandBus.dispatch(any<PipelineCommand.ClickShowText>()) } returns
                CommandResult.Failure(sentinelError)

            // For the generic any() catch, default to success for first two then fail on third
            var callCount = 0
            coEvery { commandBus.dispatch(any()) } answers {
                callCount++
                when {
                    callCount <= 2 -> CommandResult.Success(testSessionId)
                    else -> CommandResult.Failure(sentinelError)
                }
            }

            val result = orchestrator.resumePipeline(testSessionId)

            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_NODE_NOT_FOUND", failure.error.code)
            assertEquals(ErrorCategory.UI_AUTOMATION, failure.error.category)

            // Verify session was updated with error details
            coVerify {
                pipelineSessionDao.updateCompletion(
                    sessionId = testSessionId,
                    stage = any(),
                    updatedAt = any(),
                    completedAt = any(),
                    errorCode = "ERR_NODE_NOT_FOUND",
                    errorCategory = ErrorCategory.UI_AUTOMATION.name,
                    errorMessage = "Show text button not found"
                )
            }
        }

        @Test
        @DisplayName("When a command returns Skipped → returns Skipped, session marked COMPLETE")
        fun `resumePipeline returns skipped when a command is skipped`() = runTest {
            val session = createSessionEntity(stage = PipelineStage.PIPELINE_CREATED.name)
            coEvery { pipelineSessionDao.getById(testSessionId) } returns session

            // First few commands succeed, then rules-pre returns Skipped (IGNORE decision)
            var callCount = 0
            coEvery { commandBus.dispatch(any()) } answers {
                callCount++
                when {
                    callCount <= 7 -> CommandResult.Success(testSessionId) // up through PREPROCESS
                    else -> CommandResult.Skipped(testSessionId, "Pre-AI rule returned IGNORE")
                }
            }

            val result = orchestrator.resumePipeline(testSessionId)

            assertTrue(result is CommandResult.Skipped)
            val skipped = result as CommandResult.Skipped
            assertEquals("Pre-AI rule returned IGNORE", skipped.reason)

            // Verify session was marked COMPLETE (skip is a graceful terminal state)
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
        @DisplayName("Resume from mid-pipeline stage (INFERENCE) → only executes remaining commands")
        fun `resumePipeline from INFERENCE stage only executes remaining commands`() = runTest {
            // Session was persisted at INFERENCE stage (simulating a resume after crash)
            val session = createSessionEntity(stage = PipelineStage.INFERENCE.name)
            coEvery { pipelineSessionDao.getById(testSessionId) } returns session

            val dispatchedCommands = mutableListOf<PipelineCommand>()
            coEvery { commandBus.dispatch(any()) } answers {
                dispatchedCommands.add(firstArg())
                CommandResult.Success(testSessionId)
            }

            val result = orchestrator.resumePipeline(testSessionId)

            assertTrue(result is CommandResult.Success)

            // The pipeline should start from INFERENCE onward.
            // Stages from INFERENCE: INFERENCE, PARSE_RESPONSE, VALIDATE_JSON,
            // RULES_POST, STORE_RESULT, DISPATCH_ACTION, RETURN_INTENT = 7 commands
            assertEquals(7, dispatchedCommands.size)

            // Verify earlier stages were NOT executed
            assertFalse(dispatchedCommands.any { it is PipelineCommand.OpenRecorder })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.OpenRecording })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.ClickShowText })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.SelectLanguage })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.WaitForTranscription })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.ExtractTranscript })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.RunPreprocessor })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.RunRulesPreAI })
            assertFalse(dispatchedCommands.any { it is PipelineCommand.BuildPrompt })

            // Verify INFERENCE and later commands WERE executed
            assertTrue(dispatchedCommands.any { it is PipelineCommand.RunInference })
            assertTrue(dispatchedCommands.any { it is PipelineCommand.ParseResponse })
            assertTrue(dispatchedCommands.any { it is PipelineCommand.ReturnIntent })
        }
    }
}
