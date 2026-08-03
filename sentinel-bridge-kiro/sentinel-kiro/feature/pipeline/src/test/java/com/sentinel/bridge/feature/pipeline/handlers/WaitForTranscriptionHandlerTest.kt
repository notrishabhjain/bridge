package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.feature.notification.TranscriptionCompleteEvent
import com.sentinel.bridge.feature.notification.TranscriptionEventBus
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class WaitForTranscriptionHandlerTest {

    private lateinit var eventBus: TranscriptionEventBus
    private lateinit var logger: SentinelLogger
    private lateinit var handler: WaitForTranscriptionHandler

    @BeforeEach
    fun setUp() {
        eventBus = TranscriptionEventBus()
        logger = mockk(relaxed = true)
        handler = WaitForTranscriptionHandler(logger, eventBus)
    }

    @Test
    @DisplayName("Notification arrives in time → returns Success")
    fun notificationArrivesInTime_returnsSuccess() = runTest {
        val command = PipelineCommand.WaitForTranscription(
            sessionId = "session-1",
            timeoutMs = 5000L
        )

        // Launch a coroutine that emits an event after a short delay
        launch {
            advanceTimeBy(1000L)
            eventBus.emit(
                TranscriptionCompleteEvent(
                    packageName = "com.miui.player",
                    notificationText = "Finished transcribing",
                    timestamp = Instant.now()
                )
            )
        }

        val result = handler.execute(command)

        assertTrue(result is CommandResult.Success)
        assertEquals("session-1", (result as CommandResult.Success).sessionId)
    }

    @Test
    @DisplayName("Timeout elapses with no event → retries once → times out again → returns Failure")
    fun timeoutElapsesNoEvent_retriesAndFails() = runTest {
        val command = PipelineCommand.WaitForTranscription(
            sessionId = "session-2",
            timeoutMs = 100L // Short timeout for test speed
        )

        // Don't emit anything — both attempts should time out
        val result = handler.execute(command)

        assertTrue(result is CommandResult.Failure)
        val failure = result as CommandResult.Failure
        assertEquals("ERR_WAIT_TRANSCRIPTION", failure.error.code)
        assertEquals("session-2", failure.error.sessionId)
        assertTrue(failure.error.retryable)
    }

    @Test
    @DisplayName("First attempt times out, retry succeeds when notification arrives on second attempt")
    fun firstAttemptTimesOut_retrySucceeds() = runTest {
        val command = PipelineCommand.WaitForTranscription(
            sessionId = "session-3",
            timeoutMs = 100L // Short timeout for test speed
        )

        // Timeline with virtual time:
        // t=0:     First attempt starts, subscribes to events.first()
        // t=100:   First attempt's withTimeoutOrNull expires → throws TranscriptionTimeoutException
        // t=100:   RetryPolicy catches exception, delays 1000ms (baseDelayMs * 2^0)
        // t=1100:  Second attempt starts, subscribes to events.first()
        // t=1150:  We emit the event → second attempt's .first() completes
        // t=1150:  Handler returns Success
        launch {
            advanceTimeBy(1150L)
            eventBus.emit(
                TranscriptionCompleteEvent(
                    packageName = "com.miui.player",
                    notificationText = "Finished transcribing",
                    timestamp = Instant.now()
                )
            )
        }

        val result = handler.execute(command)

        assertTrue(result is CommandResult.Success)
        assertEquals("session-3", (result as CommandResult.Success).sessionId)
    }
}
