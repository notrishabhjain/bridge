package com.sentinel.bridge.feature.notification

import java.time.Instant

/**
 * Event emitted when a transcription-complete notification is detected from the Xiaomi Recorder.
 *
 * The [SentinelNotificationListener] emits this event on its [SharedFlow][kotlinx.coroutines.flow.SharedFlow]
 * whenever a notification matching the configured completion text is posted by the configured
 * recorder package. Downstream consumers (e.g., `WaitForTranscriptionHandler`) subscribe to
 * this flow to detect transcription readiness.
 *
 * @property packageName The package name of the application that posted the notification.
 * @property notificationText The notification text content that matched the configured completion pattern.
 * @property timestamp The instant when the notification was received by the listener.
 */
data class TranscriptionCompleteEvent(
    val packageName: String,
    val notificationText: String,
    val timestamp: Instant
)
