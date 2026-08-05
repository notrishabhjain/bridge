package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class CommandBusTest {

    private lateinit var commandBus: CommandBus

    @AfterEach
    fun tearDown() {
        if (::commandBus.isInitialized) {
            commandBus.close()
        }
    }

    @Test
    fun `dispatch routes command to correct handler and returns success`() = runTest {
        val handler = FakeHandler(CommandResult.Success("session-1"))
        val handlers = mapOf<Class<*>, CommandHandler<PipelineCommand>>(
            PipelineCommand.OpenRecorder::class.java to handler
        )
        commandBus = CommandBus(handlers)

        val result = commandBus.dispatch(PipelineCommand.OpenRecorder("session-1"))

        assertInstanceOf(CommandResult.Success::class.java, result)
        assertEquals("session-1", (result as CommandResult.Success).sessionId)
        assertEquals(1, handler.executionCount.get())
    }

    @Test
    fun `dispatch returns failure when no handler is registered`() = runTest {
        val handlers = emptyMap<Class<*>, CommandHandler<PipelineCommand>>()
        commandBus = CommandBus(handlers)

        val result = commandBus.dispatch(PipelineCommand.OpenRecorder("session-2"))

        assertInstanceOf(CommandResult.Failure::class.java, result)
        val failure = result as CommandResult.Failure
        assertEquals("ERR_NO_HANDLER", failure.error.code)
        assertEquals(ErrorCategory.SYSTEM, failure.error.category)
        assertEquals(PipelineStage.IDLE, failure.error.stage)
        assertTrue(failure.error.message.contains("OpenRecorder"))
    }

    @Test
    fun `dispatch serializes command execution through channel`() = runTest {
        val executionOrder = mutableListOf<String>()
        val handler1 = FakeHandler(CommandResult.Success("s1")) { executionOrder.add("first") }
        val handler2 = FakeHandler(CommandResult.Success("s2")) { executionOrder.add("second") }

        val handlers = mapOf<Class<*>, CommandHandler<PipelineCommand>>(
            PipelineCommand.OpenRecorder::class.java to handler1,
            PipelineCommand.OpenRecording::class.java to handler2
        )
        commandBus = CommandBus(handlers)

        val deferred1 = async { commandBus.dispatch(PipelineCommand.OpenRecorder("s1")) }
        val deferred2 = async { commandBus.dispatch(PipelineCommand.OpenRecording("s2")) }

        deferred1.await()
        deferred2.await()

        assertEquals(listOf("first", "second"), executionOrder)
    }

    @Test
    fun `dispatch returns handler failure result without crashing bus`() = runTest {
        val error = SentinelError(
            code = "ERR_TEST",
            category = ErrorCategory.UI_AUTOMATION,
            message = "Test failure",
            stage = PipelineStage.OPEN_RECORDER,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = "session-3"
        )
        val handler = FakeHandler(CommandResult.Failure(error))
        val handlers = mapOf<Class<*>, CommandHandler<PipelineCommand>>(
            PipelineCommand.OpenRecorder::class.java to handler
        )
        commandBus = CommandBus(handlers)

        val result = commandBus.dispatch(PipelineCommand.OpenRecorder("session-3"))

        assertInstanceOf(CommandResult.Failure::class.java, result)
        assertEquals("ERR_TEST", (result as CommandResult.Failure).error.code)
    }

    @Test
    fun `dispatch routes different command types to different handlers`() = runTest {
        val openRecorderHandler = FakeHandler(CommandResult.Success("s1"))
        val extractHandler = FakeHandler(CommandResult.Success("s2"))

        val handlers = mapOf<Class<*>, CommandHandler<PipelineCommand>>(
            PipelineCommand.OpenRecorder::class.java to openRecorderHandler,
            PipelineCommand.ExtractTranscript::class.java to extractHandler
        )
        commandBus = CommandBus(handlers)

        commandBus.dispatch(PipelineCommand.OpenRecorder("s1"))
        commandBus.dispatch(PipelineCommand.ExtractTranscript("s2"))

        assertEquals(1, openRecorderHandler.executionCount.get())
        assertEquals(1, extractHandler.executionCount.get())
    }

    @Test
    fun `dispatch returns skipped result from handler`() = runTest {
        val handler = FakeHandler(CommandResult.Skipped("session-4", "Pre-AI rule matched IGNORE"))
        val handlers = mapOf<Class<*>, CommandHandler<PipelineCommand>>(
            PipelineCommand.RunRulesPreAI::class.java to handler
        )
        commandBus = CommandBus(handlers)

        val result = commandBus.dispatch(PipelineCommand.RunRulesPreAI("session-4"))

        assertInstanceOf(CommandResult.Skipped::class.java, result)
        val skipped = result as CommandResult.Skipped
        assertEquals("session-4", skipped.sessionId)
        assertEquals("Pre-AI rule matched IGNORE", skipped.reason)
    }

    /**
     * Fake handler for testing that returns a preconfigured result.
     */
    private class FakeHandler(
        private val result: CommandResult,
        private val onExecute: (() -> Unit)? = null
    ) : CommandHandler<PipelineCommand> {

        override val stage: PipelineStage = PipelineStage.OPEN_RECORDER
        override val maxRetries: Int = 0
        val executionCount = AtomicInteger(0)

        override suspend fun execute(command: PipelineCommand): CommandResult {
            executionCount.incrementAndGet()
            onExecute?.invoke()
            return result
        }
    }
}
