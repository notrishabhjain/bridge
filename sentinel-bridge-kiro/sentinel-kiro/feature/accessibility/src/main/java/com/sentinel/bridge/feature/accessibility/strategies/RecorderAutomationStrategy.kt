package com.sentinel.bridge.feature.accessibility.strategies

import com.sentinel.bridge.feature.accessibility.AccessibilityGateway

/**
 * Defines the contract for automating a recorder application's UI.
 *
 * Each implementation encapsulates the full interaction sequence for a specific
 * OS/recorder version (e.g., [HyperOS2RecorderStrategy] for Xiaomi HyperOS 2.x).
 *
 * Node resolution follows the architecture-mandated priority order:
 * text → content description → class hierarchy → relative position → coordinate (last resort).
 *
 * All methods are suspending; waiting is done via coroutine suspension or Flow timeouts,
 * never via `Thread.sleep()`.
 */
interface RecorderAutomationStrategy {

    /**
     * Opens the latest (most recent) recording in the Recorder app's list.
     *
     * Resolution priority:
     * 1. Find recording node by text matching
     * 2. Fall back to content description
     * 3. Fall back to first item in a RecyclerView/ListView
     *
     * @param gateway The accessibility gateway for performing UI interactions.
     * @return `true` if the latest recording was successfully opened.
     */
    suspend fun openLatestRecording(gateway: AccessibilityGateway): Boolean

    /**
     * Clicks the "Show text" button (or equivalent) to trigger transcription display.
     *
     * Resolution priority:
     * 1. Find button by text
     * 2. Fall back to content description
     * 3. Fall back to coordinate tap (from [RecorderConfig])
     *
     * @param gateway The accessibility gateway for performing UI interactions.
     * @return `true` if the button was successfully activated.
     */
    suspend fun clickShowText(gateway: AccessibilityGateway): Boolean

    /**
     * Selects the target transcription language from the language picker.
     *
     * @param gateway The accessibility gateway for performing UI interactions.
     * @param language The display name of the language to select (e.g., "English").
     * @return `true` if the language was successfully selected.
     */
    suspend fun selectLanguage(gateway: AccessibilityGateway, language: String): Boolean

    /**
     * Extracts all transcript text nodes from the currently displayed transcription.
     *
     * Filters out non-transcript elements such as timestamps, controls, AI summary cards,
     * and speaker chips. Returns transcript paragraphs in document order.
     *
     * @param gateway The accessibility gateway for performing UI interactions.
     * @return Ordered list of transcript paragraph strings. Empty if no transcript is found.
     */
    suspend fun extractTranscriptNodes(gateway: AccessibilityGateway): List<String>
}
