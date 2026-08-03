package com.sentinel.bridge.feature.accessibility.strategies

import com.sentinel.bridge.feature.accessibility.AccessibilityGateway
import com.sentinel.bridge.feature.accessibility.AccessibilityNodeResult
import javax.inject.Inject

/**
 * Recorder automation strategy for Xiaomi HyperOS 2.x.
 *
 * Drives the Xiaomi Recorder (package: [RecorderConfig.recorderPackage]) through its
 * accessibility tree to open a recording, trigger transcription, select language,
 * and extract the resulting transcript text.
 *
 * Node resolution follows the architecture-mandated priority order for every interaction:
 * 1. Text match (`getText()` equals or contains target)
 * 2. Content description (`getContentDescription()` matches)
 * 3. Class hierarchy traversal (e.g., first child of RecyclerView/ListView)
 * 4. Relative node positioning (sibling/parent)
 * 5. Coordinate fallback (from [RecorderConfig], never inline)
 *
 * All waits are coroutine-based — never `Thread.sleep()`.
 *
 * @property recorderConfig Configuration holding text patterns, class names, and fallback
 *   coordinates for the Recorder app on this OS version.
 */
class HyperOS2RecorderStrategy @Inject constructor(
    private val recorderConfig: RecorderConfig
) : RecorderAutomationStrategy {

    /**
     * Opens the latest recording from the Recorder app's list screen.
     *
     * Resolution sequence:
     * 1. Attempt to find a recording item node by text (partial match on common naming patterns).
     * 2. If not found, attempt to find by content description.
     * 3. If still not found, locate the first clickable item inside a RecyclerView or ListView.
     * 4. Click the resolved node.
     *
     * @param gateway The accessibility gateway used to query and interact with the node tree.
     * @return `true` if a recording node was found and clicked successfully.
     */
    override suspend fun openLatestRecording(gateway: AccessibilityGateway): Boolean {
        val node = resolveLatestRecordingNode(gateway) ?: return false
        return gateway.clickNode(node)
    }

    /**
     * Clicks the "Show text" button to trigger or reveal the transcription view.
     *
     * Resolution sequence:
     * 1. Find button by visible text ([RecorderConfig.showTextButtonText]).
     * 2. If not found, find by content description (case-insensitive "show text").
     * 3. If not found, perform a coordinate tap at the fallback position from [RecorderConfig].
     *
     * @param gateway The accessibility gateway used to query and interact with the node tree.
     * @return `true` if the show-text action was successfully triggered.
     */
    override suspend fun clickShowText(gateway: AccessibilityGateway): Boolean {
        val timeout = recorderConfig.timeoutMs

        val nodeByText = gateway.findNodeByText(recorderConfig.showTextButtonText, timeout)
        if (nodeByText != null) {
            return gateway.clickNode(nodeByText)
        }

        val nodeByDescription = gateway.findNodeByContentDescription("show text", timeout)
        if (nodeByDescription != null) {
            return gateway.clickNode(nodeByDescription)
        }

        return gateway.tapCoordinate(
            recorderConfig.fallbackShowTextX,
            recorderConfig.fallbackShowTextY
        )
    }

    /**
     * Selects a target transcription language from the Recorder's language picker.
     *
     * Steps:
     * 1. Find and click the language selector node (identified by content description).
     * 2. Wait for the language list to appear.
     * 3. Find the target language option by its display text.
     * 4. Click the language option.
     *
     * @param gateway The accessibility gateway used to query and interact with the node tree.
     * @param language Display name of the language to select (e.g., "English", "Hindi").
     * @return `true` if the language was successfully found and selected.
     */
    override suspend fun selectLanguage(gateway: AccessibilityGateway, language: String): Boolean {
        val timeout = recorderConfig.timeoutMs

        val selectorNode = findLanguageSelectorNode(gateway, timeout) ?: return false
        val selectorClicked = gateway.clickNode(selectorNode)
        if (!selectorClicked) return false

        val languageOption = gateway.findNodeByText(language, timeout) ?: return false
        return gateway.clickNode(languageOption)
    }

    /**
     * Extracts transcript paragraph text from the currently visible transcription view.
     *
     * Filters out non-transcript UI elements by className-based filtering: only nodes with
     * class [RecorderConfig.transcriptNodeClassName] that contain non-empty text and do not
     * match known non-transcript patterns (timestamps, controls, AI summary cards, speaker chips).
     *
     * @param gateway The accessibility gateway used to query the node tree.
     * @return Ordered list of transcript paragraph strings. Empty if no transcript content is found.
     */
    override suspend fun extractTranscriptNodes(gateway: AccessibilityGateway): List<String> {
        val allTextNodes = gateway.findAllNodesByClassName(recorderConfig.transcriptNodeClassName)

        return allTextNodes
            .filter { node -> isTranscriptParagraph(node) }
            .mapNotNull { node -> node.text?.trim() }
            .filter { text -> text.isNotEmpty() }
    }

    /**
     * Resolves the latest recording node using the priority order.
     *
     * @return The node representing the latest recording, or `null` if none found.
     */
    private suspend fun resolveLatestRecordingNode(
        gateway: AccessibilityGateway
    ): AccessibilityNodeResult? {
        val timeout = recorderConfig.timeoutMs

        val nodeByText = gateway.findNodeByText("Recording", timeout)
        if (nodeByText != null) return nodeByText

        val nodeByDescription = gateway.findNodeByContentDescription("recording", timeout)
        if (nodeByDescription != null) return nodeByDescription

        return findFirstListItem(gateway, timeout)
    }

    /**
     * Finds the first clickable item inside a RecyclerView or ListView.
     *
     * This is the class-hierarchy fallback when text/content-description matching fails.
     *
     * @return The first clickable child node, or `null` if no list container is found.
     */
    private suspend fun findFirstListItem(
        gateway: AccessibilityGateway,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        val recyclerView = gateway.findNodeByClassName(
            "androidx.recyclerview.widget.RecyclerView",
            timeoutMs
        )
        val listContainer = recyclerView
            ?: gateway.findNodeByClassName("android.widget.ListView", timeoutMs)

        if (listContainer != null) {
            val children = gateway.getNodeChildren(listContainer)
            return children.firstOrNull { it.isClickable }
        }

        return null
    }

    /**
     * Locates the language selector node using text and content description fallback.
     *
     * @return The language selector node, or `null` if not found.
     */
    private suspend fun findLanguageSelectorNode(
        gateway: AccessibilityGateway,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        val byDescription = gateway.findNodeByContentDescription("language", timeoutMs)
        if (byDescription != null) return byDescription

        val byText = gateway.findNodeByText("Language", timeoutMs)
        if (byText != null) return byText

        return null
    }

    /**
     * Determines whether a node is a transcript paragraph (as opposed to a UI control,
     * timestamp, AI summary card, or speaker chip).
     *
     * Filtering rules:
     * - Node must have non-null, non-blank text.
     * - Text must not match timestamp patterns (e.g., "00:00", "12:34:56").
     * - Text must not match known control labels ("Show text", "AI Summary", "Copy", etc.).
     * - Text must not be a short speaker chip (e.g., "Speaker 1", "Speaker 2").
     *
     * @param node The candidate accessibility node.
     * @return `true` if the node represents actual transcript content.
     */
    private fun isTranscriptParagraph(node: AccessibilityNodeResult): Boolean {
        val text = node.text?.trim() ?: return false
        if (text.isBlank()) return false

        if (TIMESTAMP_PATTERN.matches(text)) return false

        val lowerText = text.lowercase()
        if (EXCLUDED_LABELS.any { label -> lowerText == label }) return false

        if (SPEAKER_CHIP_PATTERN.matches(text)) return false

        return true
    }

    private companion object {
        /** Matches timestamp formats like "00:00", "1:23", "12:34:56". */
        val TIMESTAMP_PATTERN = Regex("""^\d{1,2}:\d{2}(:\d{2})?$""")

        /** Matches speaker chip labels like "Speaker 1", "Speaker 2". */
        val SPEAKER_CHIP_PATTERN = Regex("""^Speaker \d+$""", RegexOption.IGNORE_CASE)

        /** Lowercase labels of known non-transcript UI elements to exclude. */
        val EXCLUDED_LABELS = setOf(
            "show text",
            "ai summary",
            "copy",
            "share",
            "delete",
            "more",
            "language",
            "pause",
            "play",
            "stop",
            "record"
        )
    }
}
