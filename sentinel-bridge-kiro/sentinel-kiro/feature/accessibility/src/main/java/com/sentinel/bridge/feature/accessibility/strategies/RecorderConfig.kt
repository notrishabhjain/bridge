package com.sentinel.bridge.feature.accessibility.strategies

/**
 * Configuration for the Recorder automation strategy.
 *
 * Holds all text patterns, package identifiers, class names, fallback coordinates,
 * and user-facing settings needed to drive the Recorder UI. Values are sourced from
 * [RecorderConfigProvider] (backed by DataStore) in production; defaults match
 * Xiaomi Recorder on HyperOS 2.x.
 *
 * Coordinates are stored here as the last-resort fallback — never hardcoded inline
 * in strategy logic (per architecture doc).
 *
 * @property recorderPackage Package name of the Xiaomi Recorder app.
 * @property preferredLanguage Target transcription language for the Recorder.
 * @property completionNotificationText Text pattern matched in the notification
 *     that signals transcription completion.
 * @property showTextButtonText Display text of the "Show text" button.
 * @property transcriptNodeClassName Fully-qualified class name of transcript text nodes.
 * @property fallbackShowTextX Horizontal coordinate for the show-text button fallback tap.
 * @property fallbackShowTextY Vertical coordinate for the show-text button fallback tap.
 * @property timeoutMs Default timeout in milliseconds for node resolution waits.
 */
data class RecorderConfig(
    val recorderPackage: String = "com.miui.voiceassist",
    val preferredLanguage: String = "Hindi",
    val completionNotificationText: String = "Finished transcribing",
    val showTextButtonText: String = "Show text",
    val transcriptNodeClassName: String = "android.widget.TextView",
    val fallbackShowTextX: Float = 540f,
    val fallbackShowTextY: Float = 1800f,
    val timeoutMs: Long = 5000
)
