package com.sentinel.bridge.feature.notification

/**
 * Thrown when the transcription-complete notification is not received within the configured timeout.
 *
 * The `WaitForTranscriptionHandler` throws this exception after the timeout elapses without
 * receiving a [TranscriptionCompleteEvent] from the [TranscriptionEventBus]. The handler's
 * retry policy (single retry with exponential backoff) will re-attempt before the exception
 * propagates as a terminal failure.
 *
 * @property sessionId The pipeline session that experienced the timeout.
 * @property timeoutMs The timeout duration in milliseconds that was exceeded.
 */
class TranscriptionTimeoutException(
    val sessionId: String,
    val timeoutMs: Long
) : RuntimeException("Transcription not completed within ${timeoutMs}ms for session $sessionId")
