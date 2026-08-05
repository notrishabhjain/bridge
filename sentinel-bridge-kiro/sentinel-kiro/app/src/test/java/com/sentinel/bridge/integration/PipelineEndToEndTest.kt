package com.sentinel.bridge.integration

import android.content.Context
import androidx.work.WorkManager
import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.data.db.entity.PipelineSessionEntity
import com.sentinel.bridge.core.data.repository.LogRepository
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.feature.pipeline.CommandBus
import com.sentinel.bridge.feature.pipeline.CommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineOrchestrator
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import com.sentinel.bridge.feature.setup.CapabilityManager
import com.sentinel.bridge.feature.setup.CapabilityReport
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * End-to-end integration test for the full Sentinel pipeline.
 *
 * Verifies the complete flow from [PipelineOrchestrator.startPipeline] through
 * [PipelineOrchestrator.resumePipeline] with a real [CommandBus] dispatching to
 * stub handlers that all return [CommandResult.Success]. The test confirms:
 *
 * 1. Capability check passes (mocked CapabilityManager with allPassed=true).
 * 2. Session is created and inserted into Room (mocked DAO).
 * 3. All 16 pipeline stages execute sequentially via the real CommandBus.
 * 4. Session is marked COMPLETE in Room upon successful pipeline completion.
 *
 * Uses MockK for Android dependencies (Context, WorkManager, DAO, DataStore) and
 * stub handlers that return [CommandResult.Success] for every command type.
 */
class PipelineEndToEndTest {

    private lateinit var context: Context
    private lateinit var capabilityManager: CapabilityManager
    private lateinit var pipelineSessionDao: PipelineSessionDao
    private lateinit var logRepository: LogRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var logger: SentinelLogger
    private lateinit var workManager: WorkManager

    private lateinit var commandBus: CommandBus
    private lateinit var orchestrator: PipelineOrchestrator

    private val testSessionId = "e2e-session-001"
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

        // Build a real CommandBus with stub handlers that all return Success
        val handlers = buildSuccessHandlerMap()
        commandBus = CommandBus(handlers)

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
        commandBus.close()
        unmockkStatic(WorkManager::class)
    }

    @Test
    @DisplayName("Full pipeline end-to-end: startPipeline + resumePipeline → session marked COMPLETE")
    fun `full pipeline completes end-to-end with all stages succeeding`() = runTest {
        // Arrange: capabilities all pass
        coEvery { capabilityManager.checkAllCapabilities() } returns CapabilityReport(
            accessibilityEnabled = true,
            notificationListenerEnabled = true,
            recorderInstalled = true,
            modelValid = true,
            sufficientRam = true,
            sufficientStorage = true,
            allPassed = true
        )

        // Arrange: DAO returns the session when queried (simulating Room insert)
        val sessionSlot = slot<PipelineSessionEntity>()
        coEvery { logRepository.insertWithRotation(capture(sessionSlot)) } returns Unit
        coEvery { pipelineSessionDao.getById(testSessionId) } answers {
            PipelineSessionEntity(
                sessionId = testSessionId,
                source = testSource,
                currentStage = PipelineStage.PIPELINE_CREATED.name,
                language = testLanguage,
                callerName = "Test Caller",
                phoneNumber = "+911234567890",
                callDuration = 300L,
                macroInvocationId = "macro-e2e-001",
                createdAt = Instant.now().toEpochMilli(),
                updatedAt = Instant.now().toEpochMilli(),
                completedAt = null,
                errorCode = null,
                errorCategory = null,
                errorMessage = null,
                retryCount = 0
            )
        }

        // Act: Start the pipeline (creates session + enqueues WorkManager)
        val started = orchestrator.startPipeline(
            sessionId = testSessionId,
            source = testSource,
            language = testLanguage,
            callerName = "Test Caller",
            phoneNumber = "+911234567890",
            callDuration = 300L,
            macroInvocationId = "macro-e2e-001"
        )

        assertTrue(started, "Pipeline should start successfully when capabilities pass")

        // Act: Resume the pipeline (simulates PipelineWorker.doWork)
        val result = orchestrator.resumePipeline(testSessionId)

        // Assert: Pipeline completed successfully
        assertTrue(result is CommandResult.Success, "Pipeline should complete with Success")
        assertEquals(testSessionId, (result as CommandResult.Success).sessionId)

        // Assert: Session was inserted with rotation
        coVerify(exactly = 1) { logRepository.insertWithRotation(any()) }

        // Assert: Session was marked COMPLETE in Room
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

        // Assert: All 16 stages were persisted to Room before execution
        coVerify(exactly = 16) { pipelineSessionDao.updateStage(testSessionId, any(), any()) }
    }

    @Test
    @DisplayName("Pipeline resumes from mid-stage and completes only remaining stages")
    fun `pipeline resumes from INFERENCE stage and completes remaining stages`() = runTest {
        // Arrange: session already at INFERENCE stage (simulating crash recovery)
        coEvery { pipelineSessionDao.getById(testSessionId) } returns PipelineSessionEntity(
            sessionId = testSessionId,
            source = testSource,
            currentStage = PipelineStage.INFERENCE.name,
            language = testLanguage,
            callerName = "Test Caller",
            phoneNumber = "+911234567890",
            callDuration = 300L,
            macroInvocationId = "macro-e2e-002",
            createdAt = Instant.now().toEpochMilli(),
            updatedAt = Instant.now().toEpochMilli(),
            completedAt = null,
            errorCode = null,
            errorCategory = null,
            errorMessage = null,
            retryCount = 0
        )

        // Act
        val result = orchestrator.resumePipeline(testSessionId)

        // Assert: Pipeline completed
        assertTrue(result is CommandResult.Success)
        assertEquals(testSessionId, (result as CommandResult.Success).sessionId)

        // Assert: Only 7 stages from INFERENCE onward were executed
        // INFERENCE, PARSE_RESPONSE, VALIDATE_JSON, RULES_POST, STORE_RESULT, DISPATCH_ACTION, RETURN_INTENT
        coVerify(exactly = 7) { pipelineSessionDao.updateStage(testSessionId, any(), any()) }

        // Assert: Session marked COMPLETE
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
    @DisplayName("Pipeline skips when capability check fails")
    fun `startPipeline returns false when capability check fails`() = runTest {
        // Arrange: capability check fails
        coEvery { capabilityManager.checkAllCapabilities() } returns CapabilityReport(
            accessibilityEnabled = true,
            notificationListenerEnabled = false,
            recorderInstalled = true,
            modelValid = true,
            sufficientRam = true,
            sufficientStorage = true,
            allPassed = false
        )

        // Act
        val started = orchestrator.startPipeline(
            sessionId = testSessionId,
            source = testSource,
            language = testLanguage,
            callerName = null,
            phoneNumber = null,
            callDuration = null,
            macroInvocationId = null
        )

        // Assert: Pipeline not started
        assertEquals(false, started)

        // Assert: No session inserted
        coVerify(exactly = 0) { logRepository.insertWithRotation(any()) }
    }

    /**
     * Builds a map of [PipelineCommand] types to mock [CommandHandler] instances
     * that always return [CommandResult.Success] with the dispatched session ID.
     *
     * This exercises the full [CommandBus] channel dispatch and sequential processing
     * without requiring real handler dependencies (AI, Accessibility, Notification).
     */
    @Suppress("UNCHECKED_CAST")
    private fun buildSuccessHandlerMap(): Map<Class<*>, CommandHandler<PipelineCommand>> {
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

        return commandTypes.associateWith { _ ->
            mockk<CommandHandler<PipelineCommand>> {
                coEvery { execute(any()) } answers {
                    val command = firstArg<PipelineCommand>()
                    val sessionId = extractSessionId(command)
                    CommandResult.Success(sessionId)
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
