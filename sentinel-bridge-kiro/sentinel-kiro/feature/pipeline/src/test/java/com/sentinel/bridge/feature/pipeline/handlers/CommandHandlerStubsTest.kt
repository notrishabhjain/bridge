package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.rules.RulesEngine
import com.sentinel.bridge.feature.ai.validation.JSONValidator
import com.sentinel.bridge.feature.ai.validation.ValidationResult
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.InferenceSettings
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.RetryPolicy
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class CommandHandlerStubsTest {

    private val logger: SentinelLogger = mockk(relaxed = true)
    private val aiProvider: AIProvider = mockk(relaxed = true) {
        every { isAvailable } returns true
        coEvery { infer(any(), any()) } returns MODEL_OUTPUT
    }
    private val rulesEngine: RulesEngine = mockk(relaxed = true)
    private val jsonValidator: JSONValidator = mockk(relaxed = true) {
        every { validate(any()) } returns ValidationResult.Valid("{}")
    }

    /**
     * Fresh store per test so state seeded by one case cannot satisfy another.
     */
    private val sessionStore = PipelineSessionStore()

    /**
     * Seeds the state a stage depends on, standing in for the stages that would have
     * run before it in a real pipeline.
     */
    private fun seedState(
        sessionId: String,
        prompt: String? = null,
        rawResponse: String? = null,
        result: PipelineResult? = null
    ) {
        sessionStore.update(sessionId) {
            it.copy(renderedPrompt = prompt, rawResponse = rawResponse, result = result)
        }
    }

    private fun emptyResult(sessionId: String) = PipelineResult(
        sessionId = sessionId,
        summary = "",
        confidence = 0f,
        tasks = emptyList(),
        calendarEvents = emptyList(),
        followUps = emptyList(),
        people = emptyList(),
        projects = emptyList(),
        processingTimeMs = 0L,
        model = "",
        promptVersion = "",
        pipelineVersion = "1"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Section 1: RetryPolicy unit tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("RetryPolicy")
    inner class RetryPolicyTests {

        @Test
        fun `executeWithRetry succeeds on first attempt and returns immediately`() = runTest {
            val policy = RetryPolicy(maxRetries = 3)
            val callCount = AtomicInteger(0)

            val result = policy.executeWithRetry {
                callCount.incrementAndGet()
                "success"
            }

            assertEquals("success", result)
            assertEquals(1, callCount.get())
            assertEquals(0L, currentTime)
        }

        @Test
        fun `executeWithRetry fails once then succeeds on retry`() = runTest {
            val policy = RetryPolicy(maxRetries = 3)
            val callCount = AtomicInteger(0)

            val result = policy.executeWithRetry {
                if (callCount.incrementAndGet() == 1) {
                    throw RuntimeException("transient failure")
                }
                "recovered"
            }

            assertEquals("recovered", result)
            assertEquals(2, callCount.get())
        }

        @Test
        fun `executeWithRetry fails all attempts and throws last exception`() = runTest {
            val policy = RetryPolicy(maxRetries = 2)
            val callCount = AtomicInteger(0)

            var caughtException: Exception? = null
            try {
                policy.executeWithRetry {
                    callCount.incrementAndGet()
                    throw RuntimeException("permanent failure #${callCount.get()}")
                }
            } catch (e: Exception) {
                caughtException = e
            }

            assertEquals(3, callCount.get()) // 1 initial + 2 retries
            assertInstanceOf(RuntimeException::class.java, caughtException)
            assertEquals("permanent failure #3", caughtException!!.message)
        }

        @Test
        fun `executeWithRetry uses exponential backoff delays 1s 2s 4s`() = runTest {
            val policy = RetryPolicy(maxRetries = 3, baseDelayMs = 1000L, maxDelayMs = 8000L)
            val callCount = AtomicInteger(0)
            val timestamps = mutableListOf<Long>()

            policy.executeWithRetry {
                timestamps.add(currentTime)
                if (callCount.incrementAndGet() <= 3) {
                    throw RuntimeException("fail #${callCount.get()}")
                }
                "done"
            }

            assertEquals(4, callCount.get())
            // Verify delays: attempt 0 at t=0, attempt 1 at t=1000, attempt 2 at t=3000, attempt 3 at t=7000
            assertEquals(0L, timestamps[0])
            assertEquals(1000L, timestamps[1])
            assertEquals(3000L, timestamps[2])
            assertEquals(7000L, timestamps[3])
        }

        @Test
        fun `executeWithRetry caps delay at maxDelayMs`() = runTest {
            val policy = RetryPolicy(maxRetries = 4, baseDelayMs = 1000L, maxDelayMs = 4000L)
            val callCount = AtomicInteger(0)
            val timestamps = mutableListOf<Long>()

            policy.executeWithRetry {
                timestamps.add(currentTime)
                if (callCount.incrementAndGet() <= 4) {
                    throw RuntimeException("fail")
                }
                "done"
            }

            // Delays: 1s, 2s, 4s (capped), 4s (capped)
            // Times: 0, 1000, 3000, 7000, 11000
            assertEquals(0L, timestamps[0])
            assertEquals(1000L, timestamps[1])
            assertEquals(3000L, timestamps[2])
            assertEquals(7000L, timestamps[3])
            assertEquals(11000L, timestamps[4])
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section 2: BaseCommandHandler behavior tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("BaseCommandHandler")
    inner class BaseCommandHandlerTests {

        @Test
        fun `execute returns Success when doExecute returns Success`() = runTest {
            val handler = TestableHandler(maxRetries = 2) {
                CommandResult.Success("session-ok")
            }

            val result = handler.execute(PipelineCommand.OpenRecorder("session-ok"))

            assertInstanceOf(CommandResult.Success::class.java, result)
            assertEquals("session-ok", (result as CommandResult.Success).sessionId)
        }

        @Test
        fun `execute retries and returns Success when doExecute throws once then succeeds`() = runTest {
            val callCount = AtomicInteger(0)
            val handler = TestableHandler(maxRetries = 2) {
                if (callCount.incrementAndGet() == 1) {
                    throw RuntimeException("transient")
                }
                CommandResult.Success("session-retry")
            }

            val result = handler.execute(PipelineCommand.OpenRecorder("session-retry"))

            assertInstanceOf(CommandResult.Success::class.java, result)
            assertEquals("session-retry", (result as CommandResult.Success).sessionId)
            assertEquals(2, callCount.get())
        }

        @Test
        fun `execute returns Failure with correct SentinelError when doExecute always throws`() = runTest {
            val handler = TestableHandler(maxRetries = 1) {
                throw RuntimeException("always fails")
            }

            val result = handler.execute(PipelineCommand.OpenRecorder("session-fail"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
            val failure = result as CommandResult.Failure
            assertEquals("ERR_TEST", failure.error.code)
            assertEquals(ErrorCategory.SYSTEM, failure.error.category)
            assertEquals(PipelineStage.OPEN_RECORDER, failure.error.stage)
            assertEquals("always fails", failure.error.message)
            assertEquals("session-fail", failure.error.sessionId)
            assertTrue(failure.error.retryable)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Section 3: Representative handler tests
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("OpenRecorderHandler")
    inner class OpenRecorderHandlerTests {

        @Test
        fun `returns Success with correct sessionId`() = runTest {
            val handler = OpenRecorderHandler(logger)

            val result = handler.execute(PipelineCommand.OpenRecorder("session-open"))

            assertInstanceOf(CommandResult.Success::class.java, result)
            assertEquals("session-open", (result as CommandResult.Success).sessionId)
        }

        @Test
        fun `has correct stage property`() {
            val handler = OpenRecorderHandler(logger)
            assertEquals(PipelineStage.OPEN_RECORDER, handler.stage)
        }

        @Test
        fun `has correct maxRetries value`() {
            val handler = OpenRecorderHandler(logger)
            assertEquals(2, handler.maxRetries)
        }

        @Test
        fun `buildError produces correct ErrorCategory via execute failure path`() = runTest {
            val handler = FailingOpenRecorderHandler(logger, RuntimeException("recorder not found"))

            val result = handler.execute(PipelineCommand.OpenRecorder("session-err"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
            val error = (result as CommandResult.Failure).error
            assertEquals(ErrorCategory.UI_AUTOMATION, error.category)
            assertEquals("ERR_OPEN_RECORDER", error.code)
            assertEquals(PipelineStage.OPEN_RECORDER, error.stage)
            assertEquals("recorder not found", error.message)
            assertEquals("session-err", error.sessionId)
            assertTrue(error.retryable)
        }
    }

    @Nested
    @DisplayName("RunInferenceHandler")
    inner class RunInferenceHandlerTests {

        @Test
        fun `returns Success and stores the model output`() = runTest {
            seedState("session-infer", prompt = "a rendered prompt")
            val handler = RunInferenceHandler(logger, aiProvider, sessionStore)

            val result = handler.execute(PipelineCommand.RunInference("session-infer"))

            assertInstanceOf(CommandResult.Success::class.java, result)
            assertEquals("session-infer", (result as CommandResult.Success).sessionId)
            assertEquals(MODEL_OUTPUT, sessionStore.get("session-infer")?.rawResponse)
        }

        @Test
        fun `fails when no prompt was built`() = runTest {
            val handler = RunInferenceHandler(logger, aiProvider, sessionStore)

            val result = handler.execute(PipelineCommand.RunInference("session-no-prompt"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
            assertEquals("ERR_INFERENCE", (result as CommandResult.Failure).error.code)
        }

        @Test
        fun `uses the sampling settings the prompt template supplied`() = runTest {
            // The handler's own constants are fallbacks; drifting from the template the
            // prompt was authored against silently changes generation behaviour.
            sessionStore.update("session-settings") {
                it.copy(
                    renderedPrompt = "a rendered prompt",
                    inference = InferenceSettings(
                        temperature = 0.15f,
                        maxTokens = 321,
                        topP = 0.5f,
                        topK = 7,
                        repeatPenalty = 1.25f
                    )
                )
            }
            val captured = slot<InferenceConfig>()
            val provider: AIProvider = mockk(relaxed = true) {
                every { isAvailable } returns true
                coEvery { infer(any(), capture(captured)) } returns MODEL_OUTPUT
            }

            val handler = RunInferenceHandler(logger, provider, sessionStore)
            handler.execute(PipelineCommand.RunInference("session-settings"))

            assertEquals(321, captured.captured.maxTokens)
            assertEquals(0.15f, captured.captured.temperature)
            assertEquals(7, captured.captured.topK)
        }

        @Test
        fun `fails when the model returns nothing`() = runTest {
            seedState("session-empty", prompt = "a rendered prompt")
            val emptyProvider: AIProvider = mockk(relaxed = true) {
                every { isAvailable } returns true
                coEvery { infer(any(), any()) } returns "   "
            }
            val handler = RunInferenceHandler(logger, emptyProvider, sessionStore)

            val result = handler.execute(PipelineCommand.RunInference("session-empty"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
        }

        @Test
        fun `has correct stage property`() {
            val handler = RunInferenceHandler(logger, aiProvider, sessionStore)
            assertEquals(PipelineStage.INFERENCE, handler.stage)
        }

        @Test
        fun `has correct maxRetries value`() {
            val handler = RunInferenceHandler(logger, aiProvider, sessionStore)
            assertEquals(1, handler.maxRetries)
        }

        @Test
        fun `buildError produces correct ErrorCategory via execute failure path`() = runTest {
            val handler = FailingRunInferenceHandler(
                logger,
                aiProvider,
                sessionStore,
                RuntimeException("OOM during inference")
            )

            val result = handler.execute(PipelineCommand.RunInference("session-infer-err"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
            val error = (result as CommandResult.Failure).error
            assertEquals(ErrorCategory.INFERENCE, error.category)
            assertEquals("ERR_INFERENCE", error.code)
            assertEquals(PipelineStage.INFERENCE, error.stage)
            assertEquals("OOM during inference", error.message)
            assertEquals("session-infer-err", error.sessionId)
            assertTrue(error.retryable)
        }
    }

    @Nested
    @DisplayName("ValidateJsonHandler")
    inner class ValidateJsonHandlerTests {

        @Test
        fun `returns Success with correct sessionId`() = runTest {
            seedState(
                "session-json",
                rawResponse = MODEL_OUTPUT,
                result = emptyResult("session-json")
            )
            val handler = ValidateJsonHandler(logger, jsonValidator, sessionStore)

            val result = handler.execute(PipelineCommand.ValidateJson("session-json"))

            assertInstanceOf(CommandResult.Success::class.java, result)
            assertEquals("session-json", (result as CommandResult.Success).sessionId)
        }

        @Test
        fun `fails when the parse stage stored no result`() = runTest {
            seedState("session-unparsed", rawResponse = MODEL_OUTPUT)
            val handler = ValidateJsonHandler(logger, jsonValidator, sessionStore)

            val result = handler.execute(PipelineCommand.ValidateJson("session-unparsed"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
        }

        @Test
        fun `has correct stage property`() {
            val handler = ValidateJsonHandler(logger, jsonValidator, sessionStore)
            assertEquals(PipelineStage.VALIDATE_JSON, handler.stage)
        }

        @Test
        fun `has correct maxRetries value`() {
            val handler = ValidateJsonHandler(logger, jsonValidator, sessionStore)
            assertEquals(1, handler.maxRetries)
        }

        @Test
        fun `buildError produces correct ErrorCategory via execute failure path`() = runTest {
            val handler = FailingValidateJsonHandler(
                logger,
                jsonValidator,
                sessionStore,
                RuntimeException("invalid schema")
            )

            val result = handler.execute(PipelineCommand.ValidateJson("session-json-err"))

            assertInstanceOf(CommandResult.Failure::class.java, result)
            val error = (result as CommandResult.Failure).error
            assertEquals(ErrorCategory.JSON_VALIDATION, error.category)
            assertEquals("ERR_VALIDATE_JSON", error.code)
            assertEquals(PipelineStage.VALIDATE_JSON, error.stage)
            assertEquals("invalid schema", error.message)
            assertEquals("session-json-err", error.sessionId)
            assertTrue(error.retryable)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Configurable [BaseCommandHandler] for testing retry/failure behavior.
     */
    private class TestableHandler(
        maxRetries: Int,
        private val doExecuteBlock: suspend () -> CommandResult
    ) : BaseCommandHandler<PipelineCommand.OpenRecorder>(maxRetries) {

        override val stage: PipelineStage = PipelineStage.OPEN_RECORDER

        override suspend fun doExecute(command: PipelineCommand.OpenRecorder): CommandResult {
            return doExecuteBlock()
        }

        override fun buildError(command: PipelineCommand.OpenRecorder, exception: Exception): SentinelError {
            return SentinelError(
                code = "ERR_TEST",
                category = ErrorCategory.SYSTEM,
                message = exception.message ?: "Test error",
                stage = stage,
                retryable = true,
                timestamp = Instant.now(),
                sessionId = command.sessionId
            )
        }
    }

    /**
     * [OpenRecorderHandler] subclass that always throws to exercise the buildError path.
     */
    private class FailingOpenRecorderHandler(
        logger: SentinelLogger,
        private val exception: Exception
    ) : OpenRecorderHandler(logger) {

        override suspend fun doExecute(command: PipelineCommand.OpenRecorder): CommandResult {
            throw exception
        }
    }

    /**
     * [RunInferenceHandler] subclass that always throws to exercise the buildError path.
     */
    private class FailingRunInferenceHandler(
        logger: SentinelLogger,
        aiProvider: AIProvider,
        sessionStore: PipelineSessionStore,
        private val exception: Exception
    ) : RunInferenceHandler(logger, aiProvider, sessionStore) {

        override suspend fun doExecute(command: PipelineCommand.RunInference): CommandResult {
            throw exception
        }
    }

    /**
     * [ValidateJsonHandler] subclass that always throws to exercise the buildError path.
     */
    private class FailingValidateJsonHandler(
        logger: SentinelLogger,
        jsonValidator: JSONValidator,
        sessionStore: PipelineSessionStore,
        private val exception: Exception
    ) : ValidateJsonHandler(logger, jsonValidator, sessionStore) {

        override suspend fun doExecute(command: PipelineCommand.ValidateJson): CommandResult {
            throw exception
        }
    }

    private companion object {
        /** Stand-in for a well-formed model completion. */
        const val MODEL_OUTPUT = """{"summary":"s","confidence":0.9,"tasks":[]}"""
    }
}
