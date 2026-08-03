package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.notification.TranscriptionEventBus
import com.sentinel.bridge.feature.notification.TranscriptionTimeoutException
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import javax.inject.Inject

/**
 * Handles waiting for the "Finished transcribing" notification from Xiaomi Recorder.
 *
 * Subscribes to the shared [TranscriptionEventBus] and suspends until a
 * [TranscriptionCompleteEvent][com.sentinel.bridge.feature.notification.TranscriptionCompleteEvent]
 * is received or the configured timeout elapses.
 *
 * ## Timeout Behaviour
 *
 * The timeout is specified by [PipelineCommand.WaitForTranscription.timeoutMs] (default 180,000ms).
 * If the timeout elapses without receiving an event, a [TranscriptionTimeoutException] is thrown
 * to trigger the retry mechanism in [BaseCommandHandler].
 *
 * ## Retry Policy
 *
 * Configured with `maxRetries = 1` (one additional attempt after the initial failure). The retry
 * uses exponential backoff as defined by [com.sentinel.bridge.feature.pipeline.RetryPolicy].
 * If both attempts time out, the error propagates as [CommandResult.Failure] with
 * [ErrorCategory.TRANSCRIPTION].
 *
 * ## Threading
 *
 * This handler suspends on the coroutine provided by the pipeline worker. The
 * [TranscriptionEventBus] SharedFlow is thread-safe and events emitted from the main thread
 * (notification callback) are delivered to all active collectors regardless of dispatcher.
 */
class WaitForTranscriptionHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val transcriptionEventBus: TranscriptionEventBus
) : BaseCommandHandler<PipelineCommand.WaitForTranscription>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.WAIT_TRANSCRIPTION

    /**
     * Subscribes to the [TranscriptionEventBus] and waits for a transcription-complete event.
     *
     * Applies [PipelineCommand.WaitForTranscription.timeoutMs] as the maximum wait duration.
     * Returns [CommandResult.Success] if the event arrives within the timeout, or throws
     * [TranscriptionTimeoutException] to trigger a retry.
     *
     * @param command The wait-for-transcription command containing session ID and timeout.
     * @return [CommandResult.Success] when the transcription-complete notification is received.
     * @throws TranscriptionTimeoutException if the timeout elapses without receiving an event.
     */
    override suspend fun doExecute(command: PipelineCommand.WaitForTranscription): CommandResult {
        logger.logInfo(
            command.sessionId,
            stage.name,
            "Waiting for transcription notification (timeout=${command.timeoutMs}ms)"
        )

        val event = withTimeoutOrNull(command.timeoutMs) {
            transcriptionEventBus.events.first()
        }

        return if (event != null) {
            logger.logInfo(
                command.sessionId,
                stage.name,
                "Transcription complete notification received from ${event.packageName}"
            )
            CommandResult.Success(command.sessionId)
        } else {
            throw TranscriptionTimeoutException(
                sessionId = command.sessionId,
                timeoutMs = command.timeoutMs
            )
        }
    }

    /**
     * Builds a structured [SentinelError] for transcription timeout failures.
     *
     * @param command The command that timed out.
     * @param exception The [TranscriptionTimeoutException] or other exception.
     * @return A [SentinelError] categorized as [ErrorCategory.TRANSCRIPTION] and marked retryable.
     */
    override fun buildError(command: PipelineCommand.WaitForTranscription, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_WAIT_TRANSCRIPTION",
            category = ErrorCategory.TRANSCRIPTION,
            message = exception.message ?: "Transcription wait timed out or failed",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
