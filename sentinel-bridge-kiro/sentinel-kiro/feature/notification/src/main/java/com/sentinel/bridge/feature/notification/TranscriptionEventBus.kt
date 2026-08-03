package com.sentinel.bridge.feature.notification

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared event bus for transcription-complete notifications.
 *
 * Because [SentinelNotificationListener] is instantiated by the Android system and cannot
 * participate in standard Hilt constructor injection, this singleton acts as the bridge
 * between the listener (producer) and pipeline handlers (consumers).
 *
 * The listener emits [TranscriptionCompleteEvent] instances via [emit], and downstream
 * consumers (e.g., `WaitForTranscriptionHandler`) collect from [events] with an appropriate
 * timeout.
 *
 * ## Flow Configuration
 *
 * - **replay = 0**: No buffered history; only active collectors receive events.
 * - **extraBufferCapacity = 1**: Allows one pending event if no collector is ready yet.
 * - **onBufferOverflow = DROP_OLDEST**: If the buffer is full, the oldest undelivered
 *   event is dropped to avoid blocking the producer.
 *
 * ## Thread Safety
 *
 * [MutableSharedFlow] is thread-safe by design. [emit] uses [tryEmit] which is non-suspending
 * and safe to call from any thread, including the main thread where notification callbacks run.
 */
@Singleton
class TranscriptionEventBus @Inject constructor() {

    private val _events = MutableSharedFlow<TranscriptionCompleteEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Observable stream of transcription-complete events.
     *
     * Consumers should collect this flow with a timeout to avoid indefinite suspension.
     * Only events emitted while a collector is active will be delivered (no replay).
     */
    val events: SharedFlow<TranscriptionCompleteEvent> = _events.asSharedFlow()

    /**
     * Emits a [TranscriptionCompleteEvent] to all active collectors.
     *
     * Uses [MutableSharedFlow.tryEmit] internally, which is non-suspending and safe to call
     * from any thread (including the main thread in [SentinelNotificationListener.onNotificationPosted]).
     *
     * @param event The transcription-complete event to broadcast.
     */
    fun emit(event: TranscriptionCompleteEvent) {
        _events.tryEmit(event)
    }
}
