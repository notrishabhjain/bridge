package com.sentinel.bridge.feature.notification

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.Instant

/**
 * System-managed notification listener that detects transcription-complete notifications
 * from the Xiaomi Recorder application.
 *
 * This service extends [NotificationListenerService] and monitors incoming notifications
 * for text patterns indicating that HyperAI transcription has finished. When a match is
 * detected, a [TranscriptionCompleteEvent] is emitted on the [transcriptionComplete] flow.
 *
 * Because [NotificationListenerService] is instantiated by the Android system and is not
 * compatible with `@AndroidEntryPoint`, dependencies are retrieved using Hilt's
 * [EntryPointAccessors.fromApplication] mechanism.
 *
 * Configuration is read reactively from [AppSettingsRepository]:
 * - `recorderPackage`: the package name to filter notifications from.
 * - `completionNotificationText`: the text pattern to match in notification content.
 *
 * ## Usage
 *
 * Downstream consumers (e.g., `WaitForTranscriptionHandler`) collect from
 * [transcriptionComplete] with an appropriate timeout:
 *
 * ```kotlin
 * listener.transcriptionComplete
 *     .first() // or withTimeout(180_000) { ... }
 * ```
 *
 * ## Threading
 *
 * [onNotificationPosted] is called on the main thread by the system. The settings lookup
 * uses [runBlocking] with [Flow.first][kotlinx.coroutines.flow.first] to retrieve the
 * latest configured values synchronously, which is acceptable given the low frequency of
 * notification callbacks and the near-instant DataStore read from cache.
 */
class SentinelNotificationListener : NotificationListenerService() {

    /**
     * Hilt entry point for obtaining dependencies that cannot be constructor-injected
     * into a system-managed [NotificationListenerService].
     */
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationListenerEntryPoint {
        /**
         * Provides the [AppSettingsRepository] for reading notification matching configuration.
         */
        fun appSettingsRepository(): AppSettingsRepository

        /**
         * Provides the shared [TranscriptionEventBus] for broadcasting events to pipeline handlers.
         */
        fun transcriptionEventBus(): TranscriptionEventBus
    }

    private val _transcriptionComplete = MutableSharedFlow<TranscriptionCompleteEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    /**
     * Flow of [TranscriptionCompleteEvent] emissions.
     *
     * A new event is emitted each time a notification from the configured recorder package
     * is posted with text containing the configured completion notification text pattern.
     * The flow has no replay; only active collectors receive events.
     */
    val transcriptionComplete: SharedFlow<TranscriptionCompleteEvent> =
        _transcriptionComplete.asSharedFlow()

    private val entryPoint: NotificationListenerEntryPoint by lazy {
        EntryPointAccessors.fromApplication(
            applicationContext,
            NotificationListenerEntryPoint::class.java
        )
    }

    private val appSettingsRepository: AppSettingsRepository by lazy {
        entryPoint.appSettingsRepository()
    }

    private val transcriptionEventBus: TranscriptionEventBus by lazy {
        entryPoint.transcriptionEventBus()
    }

    /**
     * Called by the system when a new notification is posted.
     *
     * Filters notifications by:
     * 1. Package name matching the configured recorder package.
     * 2. Notification text containing the configured completion notification text.
     *
     * If both conditions are met, emits a [TranscriptionCompleteEvent] on [transcriptionComplete].
     *
     * @param sbn The [StatusBarNotification] representing the posted notification.
     */
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val recorderPackage = runBlocking {
            appSettingsRepository.recorderPackage.first()
        }

        if (sbn.packageName != recorderPackage) return

        val notificationText = extractNotificationText(sbn) ?: return

        val completionText = runBlocking {
            appSettingsRepository.completionNotificationText.first()
        }

        if (!notificationText.contains(completionText, ignoreCase = true)) return

        val event = TranscriptionCompleteEvent(
            packageName = sbn.packageName,
            notificationText = notificationText,
            timestamp = Instant.now()
        )

        _transcriptionComplete.tryEmit(event)
        transcriptionEventBus.emit(event)
    }

    /**
     * Extracts the primary text content from a [StatusBarNotification].
     *
     * Checks [android.app.Notification.extras] for `EXTRA_TEXT` first, falling back
     * to [android.app.Notification.tickerText] if the extra is absent.
     *
     * @param sbn The notification to extract text from.
     * @return The notification text as a [String], or `null` if no text is available.
     */
    private fun extractNotificationText(sbn: StatusBarNotification): String? {
        val extras = sbn.notification?.extras ?: return null
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) return text
        return sbn.notification?.tickerText?.toString()
    }
}
