package com.sentinel.bridge.integration

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
import com.sentinel.bridge.feature.pipeline.CommandBus
import com.sentinel.bridge.feature.pipeline.CommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineOrchestrator
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.setup.CapabilityManager
import com.sentinel.bridge.feature.setup.CapabilityReport
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Integration tests verifying pipeline failure propagation.
 *
 * Validates that when a [CommandHandler] returns [CommandResult.Failure],
 * the [PipelineOrchestrator] correctly:
 *
 * 1. Stops executing subsequent pipeline stages.
 * 2. Persists the error details (code, category, message) to the Room session.
 * 3. Returns the [CommandResult.Failure] to the caller (PipelineWorker).
 *
 * Tests cover failure at different pipeline stages to ensure error propagation
 * works regardless of which handler fails, and that the error code from the
 * failing handler is preserved through the orchestrator to the session entity.
 */
class PipelineFailureTest {

    private lateinit var context: Context
    private lateinit var capabilityManager: CapabilityManager
    private lateinit var pipelineSessionDao: PipelineSessionDao
    private lateinit var logRepository: LogRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var logger: SentinelLogger
    private lateinit var workManager: WorkManager

    private lateinit var commandBus: CommandBus
    private lateinit var orchestrator: PipelineOrchestrator

    private val testSessionId = "failure-session-001"
    private val testSource = "CALL"
    private val testLanguage = "Hindi"
    private val testTimeoutMs = 180_000L

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        capabilityManager = mockk()
        pipelineSessionDao = mockk(relaxed = true)
        logRepository = mockk(relaxed = true)
        appSettingsRepository = mockk()
        logger = mockk(relaxed = true)
        workManager = mockk(relaxed = true)

        every { appSettingsRepository.transcriptionTimeoutMs } returns flowOf(testTimeoutMs)

        mockkStatic(WorkManager::class)
        every { WorkManager.getInstance(any()) } returns workManager
    }

    @AfterEach
    fun tearDown() {
        if (::commandBus.isInitialized) {
            commandBus.close()
        }
        unmockkStatic(WorkManager::class)
    }

    /**
     * Creates a session entity for the given session ID at the specified stage.
     */
    private fun createSession(
        sessionId: String = testSessionId,
        stage: PipelineStage = PipelineStage.PIPELINE_CREATED
    ) = PipelineSessionEntity(
        sessionId = sessionId,
        source = testSource,
        currentStage = stage.name,
        language = testLanguage,
        callerName = "Test Caller",
        phoneNumber = "+911234567890",
        callDuration = 120L,
        macroInvocationId = "macro-fail-001",
        createdAt = Instant.now().toEpochMilli(),
        updatedAt = Instant.now().toEpochMilli(),
        completedAt = null,
        errorCode = null,
        errorCategory = null,
        errorMessage = null,
        retryCount = 0
    )

    @Nested
    @DisplayName("Inference failure")
    inner class InferenceFailureTests {

        @Test
        @DisplayName("RunInference handler failure → PIPELINE_FAILED with ERR_INFERENCE code")
        fun `inference failure propagates error code to session`() = runTest {
            // Arrange: Build handlers where RunInference returns Failure
            val inferenceError = SentinelError(
                code = "ERR_INFERENCE",
                category = ErrorCategory.INFERENCE,
                message = "OOM during token generation",
                stage = PipelineStage.INFERENCE,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            val handlers = buildHandlerMapWithFailureAt(
                failingCommand = PipelineCommand.RunInference::class.java,
                error = inferenceError
            )

            commandBus = CommandBus(handlers)
            orchestrator = PipelineOrchestrator(
                context = context,
                capabilityManager = capabilityManager,
                commandBus = commandBus,
                pipelineSessionDao = pipelineSessionDao,
                logRepository = logRepository,
                appSettingsRepository = appSettingsRepository,
                logger = logger
            )

            coEvery { pipelineSessionDao.getById(testSessionId) } returns createSession()

            // Act
            val result = orchestrator.resumePipeline(testSessionId)

            // Assert: Pipeline failed with the correct error
            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_INFERENCE", failure.error.code)
            assertEquals(ErrorCategory.INFERENCE, failure.error.category)
            assertEquals("OOM during token generation", failure.error.message)
            assertTrue(failure.error.retryable)

            // Assert: Session was updated with error details in Room
            coVerify {
                pipelineSessionDao.updateCompletion(
                    sessionId = testSessionId,
                    stage = any(),
                    updatedAt = any(),
                    completedAt = any(),
                    errorCode = "ERR_INFERENCE",
                    errorCategory = ErrorCategory.INFERENCE.name,
                    errorMessage = "OOM during token generation"
                )
            }
        }
    }

    @Nested
    @DisplayName("UI Automation failure")
    inner class UIAutomationFailureTests {

        @Test
        @DisplayName("OpenRecorder handler failure → pipeline fails at first stage")
        fun `open recorder failure stops pipeline immediately`() = runTest {
            val openRecorderError = SentinelError(
                code = "ERR_OPEN_RECORDER",
                category = ErrorCategory.UI_AUTOMATION,
                message = "Recorder app not found",
                stage = PipelineStage.OPEN_RECORDER,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            val handlers = buildHandlerMapWithFailureAt(
                failingCommand = PipelineCommand.OpenRecorder::class.java,
                error = openRecorderError
            )

            commandBus = CommandBus(handlers)
            orchestrator = PipelineOrchestrator(
                context = context,
                capabilityManager = capabilityManager,
                commandBus = commandBus,
                pipelineSessionDao = pipelineSessionDao,
                logRepository = logRepository,
                appSettingsRepository = appSettingsRepository,
                logger = logger
            )

            coEvery { pipelineSessionDao.getById(testSessionId) } returns createSession()

            // Act
            val result = orchestrator.resumePipeline(testSessionId)

            // Assert: Failed at first stage
            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_OPEN_RECORDER", failure.error.code)
            assertEquals(ErrorCategory.UI_AUTOMATION, failure.error.category)

            // Assert: Only 1 stage was attempted (the failing OpenRecorder)
            coVerify(exactly = 1) { pipelineSessionDao.updateStage(testSessionId, any(), any()) }

            // Assert: Session was updated with error
            coVerify {
                pipelineSessionDao.updateCompletion(
                    sessionId = testSessionId,
                    stage = any(),
                    updatedAt = any(),
                    completedAt = any(),
                    errorCode = "ERR_OPEN_RECORDER",
                    errorCategory = ErrorCategory.UI_AUTOMATION.name,
                    errorMessage = "Recorder app not found"
                )
            }
        }
    }

    @Nested
    @DisplayName("JSON Validation failure")
    inner class JsonValidationFailureTests {

        @Test
        @DisplayName("ValidateJson handler failure → pipeline fails with JSON_VALIDATION error")
        fun `json validation failure propagates correctly`() = runTest {
            val jsonError = SentinelError(
                code = "ERR_JSON_VALIDATION",
                category = ErrorCategory.JSON_VALIDATION,
                message = "Invalid JSON: missing 'tasks' field after repair attempt",
                stage = PipelineStage.VALIDATE_JSON,
                retryable = false,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            val handlers = buildHandlerMapWithFailureAt(
                failingCommand = PipelineCommand.ValidateJson::class.java,
                error = jsonError
            )

            commandBus = CommandBus(handlers)
            orchestrator = PipelineOrchestrator(
                context = context,
                capabilityManager = capabilityManager,
                commandBus = commandBus,
                pipelineSessionDao = pipelineSessionDao,
                logRepository = logRepository,
                appSettingsRepository = appSettingsRepository,
                logger = logger
            )

            coEvery { pipelineSessionDao.getById(testSessionId) } returns createSession()

            // Act
            val result = orchestrator.resumePipeline(testSessionId)

            // Assert: Pipeline failed with JSON validation error
            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_JSON_VALIDATION", failure.error.code)
            assertEquals(ErrorCategory.JSON_VALIDATION, failure.error.category)
            assertEquals(false, failure.error.retryable)

            // Assert: Stages up to ValidateJson were attempted (11 stages from start to VALIDATE_JSON inclusive)
            // OpenRecorder, OpenRecording, ClickShowText, SelectLanguage, WaitTranscription,
            // ExtractTranscript, RunPreprocessor, RunRulesPreAI, BuildPrompt, RunInference,
            // ParseResponse, ValidateJson = 12 stages total (11 succeed + 1 fails)
            coVerify(exactly = 12) { pipelineSessionDao.updateStage(testSessionId, any(), any()) }

            // Assert: Session updated with error in Room
            coVerify {
                pipelineSessionDao.updateCompletion(
                    sessionId = testSessionId,
                    stage = any(),
                    updatedAt = any(),
                    completedAt = any(),
                    errorCode = "ERR_JSON_VALIDATION",
                    errorCategory = ErrorCategory.JSON_VALIDATION.name,
                    errorMessage = "Invalid JSON: missing 'tasks' field after repair attempt"
                )
            }
        }
    }

    @Nested
    @DisplayName("Transcription failure")
    inner class TranscriptionFailureTests {

        @Test
        @DisplayName("WaitForTranscription timeout → pipeline fails with ERR_WAIT_TRANSCRIPTION")
        fun `transcription timeout failure propagates correctly`() = runTest {
            val transcriptionError = SentinelError(
                code = "ERR_WAIT_TRANSCRIPTION",
                category = ErrorCategory.TRANSCRIPTION,
                message = "Transcription timed out after 180000ms",
                stage = PipelineStage.WAIT_TRANSCRIPTION,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = testSessionId
            )

            val handlers = buildHandlerMapWithFailureAt(
                failingCommand = PipelineCommand.WaitForTranscription::class.java,
                error = transcriptionError
            )

            commandBus = CommandBus(handlers)
            orchestrator = PipelineOrchestrator(
                context = context,
                capabilityManager = capabilityManager,
                commandBus = commandBus,
                pipelineSessionDao = pipelineSessionDao,
                logRepository = logRepository,
                appSettingsRepository = appSettingsRepository,
                logger = logger
            )

            coEvery { pipelineSessionDao.getById(testSessionId) } returns createSession()

            // Act
            val result = orchestrator.resumePipeline(testSessionId)

            // Assert
            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_WAIT_TRANSCRIPTION", failure.error.code)
            assertEquals(ErrorCategory.TRANSCRIPTION, failure.error.category)
            assertTrue(failure.error.retryable)

            // Assert: Only stages up to and including WaitForTranscription were attempted (5 stages)
            coVerify(exactly = 5) { pipelineSessionDao.updateStage(testSessionId, any(), any()) }
        }
    }

    @Nested
    @DisplayName("Session not found")
    inner class SessionNotFoundTests {

        @Test
        @DisplayName("Missing session → Failure with ERR_SESSION_NOT_FOUND")
        fun `resume with non-existent session returns session not found error`() = runTest {
            // Use a simple handler map (won't matter since session is not found)
            val handlers = buildHandlerMapWithFailureAt(
                failingCommand = PipelineCommand.OpenRecorder::class.java,
                error = mockk(relaxed = true)
            )
            commandBus = CommandBus(handlers)
            orchestrator = PipelineOrchestrator(
                context = context,
                capabilityManager = capabilityManager,
                commandBus = commandBus,
                pipelineSessionDao = pipelineSessionDao,
                logRepository = logRepository,
                appSettingsRepository = appSettingsRepository,
                logger = logger
            )

            coEvery { pipelineSessionDao.getById("nonexistent") } returns null

            // Act
            val result = orchestrator.resumePipeline("nonexistent")

            // Assert
            assertTrue(result is CommandResult.Failure)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_SESSION_NOT_FOUND", failure.error.code)
            assertEquals(ErrorCategory.SYSTEM, failure.error.category)
        }
    }

    /**
     * Builds a handler map where all handlers return [CommandResult.Success] except
     * the handler for [failingCommand], which returns [CommandResult.Failure] with [error].
     *
     * This allows testing failure at any specific pipeline stage while all others succeed,
     * verifying that the orchestrator correctly stops execution and persists the error.
     *
     * @param failingCommand The [PipelineCommand] class whose handler should fail.
     * @param error The [SentinelError] to return in the failure result.
     * @return Map of command types to handlers suitable for [CommandBus] construction.
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildHandlerMapWithFailureAt(
        failingCommand: Class<*>,
        error: SentinelError
    ): Map<Class<*>, CommandHandler<PipelineCommand>> {
        val commandTypes = listOf(
            PipelineCommand.OpenRecorder::class.java,
            PipelineCommand.OpenRecording::class.java,
            PipelineCommand.ClickShowText::class.java,
            PipelineCommand.SelectLanguage::class.java,
            PipelineCommand.WaitForTranscription::class.java,
            PipelineCommand.ExtractTranscript::class.java,
            PipelineCommand.RunPreprocessor::class.java,
            PipelineCommand.RunRulesPreAI::class.java,
            PipelineCommand.BuildPrompt::class.java,
            PipelineCommand.RunInference::class.java,
            PipelineCommand.ParseResponse::class.java,
            PipelineCommand.ValidateJson::class.java,
            PipelineCommand.RunRulesPostAI::class.java,
            PipelineCommand.StoreResult::class.java,
            PipelineCommand.DispatchAction::class.java,
            PipelineCommand.ReturnIntent::class.java
        )

        return commandTypes.associateWith { commandType ->
            if (commandType == failingCommand) {
                mockk<CommandHandler<PipelineCommand>> {
                    coEvery { execute(any()) } returns CommandResult.Failure(error)
                }
            } else {
                mockk<CommandHandler<PipelineCommand>> {
                    coEvery { execute(any()) } answers {
                        val command = firstArg<PipelineCommand>()
                        CommandResult.Success(extractSessionId(command))
                    }
                }
            }
        }
    }

    /**
     * Extracts the session ID from any [PipelineCommand] subclass.
     */
    private fun extractSessionId(command: PipelineCommand): String {
        return when (command) {
            is PipelineCommand.OpenRecorder -> command.sessionId
            is PipelineCommand.OpenRecording -> command.sessionId
            is PipelineCommand.ClickShowText -> command.sessionId
            is PipelineCommand.SelectLanguage -> command.sessionId
            is PipelineCommand.WaitForTranscription -> command.sessionId
            is PipelineCommand.ExtractTranscript -> command.sessionId
            is PipelineCommand.RunPreprocessor -> command.sessionId
            is PipelineCommand.RunRulesPreAI -> command.sessionId
            is PipelineCommand.BuildPrompt -> command.sessionId
            is PipelineCommand.RunInference -> command.sessionId
            is PipelineCommand.ParseResponse -> command.sessionId
            is PipelineCommand.ValidateJson -> command.sessionId
            is PipelineCommand.RunRulesPostAI -> command.sessionId
            is PipelineCommand.StoreResult -> command.sessionId
            is PipelineCommand.DispatchAction -> command.sessionId
            is PipelineCommand.ReturnIntent -> command.sessionId
        }
    }
}
