package com.sentinel.bridge.feature.accessibility.strategies

import com.sentinel.bridge.feature.accessibility.AccessibilityNodeResult
import com.sentinel.bridge.feature.accessibility.FakeAccessibilityGateway
import com.sentinel.bridge.feature.accessibility.NodeBounds
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Unit tests for [HyperOS2RecorderStrategy].
 *
 * Exercises every public method through the [FakeAccessibilityGateway], covering
 * the happy path plus each fallback layer defined in the architecture's resolution
 * priority order (text → content description → class hierarchy → coordinate).
 */
class HyperOS2RecorderStrategyTest {

    private lateinit var gateway: FakeAccessibilityGateway
    private lateinit var strategy: HyperOS2RecorderStrategy
    private val config = RecorderConfig()

    @BeforeEach
    fun setUp() {
        gateway = FakeAccessibilityGateway()
        strategy = HyperOS2RecorderStrategy(config)
    }

    // ----- Helper -----

    private fun createNode(
        text: String? = null,
        contentDescription: String? = null,
        className: String? = null,
        viewId: String? = null,
        bounds: NodeBounds? = null,
        isClickable: Boolean = false,
        isScrollable: Boolean = false,
        childCount: Int = 0
    ) = AccessibilityNodeResult(
        text = text,
        contentDescription = contentDescription,
        className = className,
        viewId = viewId,
        bounds = bounds,
        isClickable = isClickable,
        isScrollable = isScrollable,
        childCount = childCount
    )

    // =========================================================================
    // openLatestRecording
    // =========================================================================

    @Nested
    @DisplayName("openLatestRecording")
    inner class OpenLatestRecordingTests {

        @Test
        fun `happy path - node found by text, click succeeds`() = runTest {
            val recordingNode = createNode(text = "Recording", isClickable = true)
            gateway.nodesByText["Recording"] = recordingNode

            val result = strategy.openLatestRecording(gateway)

            assertTrue(result)
            assertEquals(recordingNode, gateway.lastClickedNode)
            assertEquals(1, gateway.clickNodeCallCount)
        }

        @Test
        fun `node found by content description when text unavailable`() = runTest {
            // No nodesByText entry for "Recording"
            val recordingNode = createNode(contentDescription = "recording", isClickable = true)
            gateway.nodesByDescription["recording"] = recordingNode

            val result = strategy.openLatestRecording(gateway)

            assertTrue(result)
            assertEquals(recordingNode, gateway.lastClickedNode)
            assertEquals(1, gateway.findNodeByTextCallCount)
            assertEquals(1, gateway.findNodeByContentDescriptionCallCount)
        }

        @Test
        fun `node found in RecyclerView when text and description unavailable`() = runTest {
            // No text or description matches
            val recyclerView = createNode(
                className = "androidx.recyclerview.widget.RecyclerView",
                isScrollable = true,
                childCount = 3
            )
            val firstClickableChild = createNode(text = "Meeting notes", isClickable = true)
            val nonClickableChild = createNode(text = "Header", isClickable = false)

            gateway.nodesByClassName["androidx.recyclerview.widget.RecyclerView"] =
                listOf(recyclerView)
            gateway.childrenMap[recyclerView] = listOf(nonClickableChild, firstClickableChild)

            val result = strategy.openLatestRecording(gateway)

            assertTrue(result)
            assertEquals(firstClickableChild, gateway.lastClickedNode)
        }

        @Test
        fun `returns false when no node found anywhere`() = runTest {
            // Nothing configured — all lookups return null / empty

            val result = strategy.openLatestRecording(gateway)

            assertFalse(result)
            assertEquals(0, gateway.clickNodeCallCount)
        }

        @Test
        fun `returns false when node found but click fails`() = runTest {
            val recordingNode = createNode(text = "Recording", isClickable = true)
            gateway.nodesByText["Recording"] = recordingNode
            gateway.clickResult = false

            val result = strategy.openLatestRecording(gateway)

            assertFalse(result)
            assertEquals(recordingNode, gateway.lastClickedNode)
        }
    }

    // =========================================================================
    // clickShowText
    // =========================================================================

    @Nested
    @DisplayName("clickShowText")
    inner class ClickShowTextTests {

        @Test
        fun `happy path - button found by text, click succeeds`() = runTest {
            val showTextNode = createNode(text = "Show text", isClickable = true)
            gateway.nodesByText["Show text"] = showTextNode

            val result = strategy.clickShowText(gateway)

            assertTrue(result)
            assertEquals(showTextNode, gateway.lastClickedNode)
            assertEquals(1, gateway.clickNodeCallCount)
            assertEquals(0, gateway.tapCoordinateCallCount)
        }

        @Test
        fun `button found by content description when text unavailable`() = runTest {
            val showTextNode = createNode(contentDescription = "show text", isClickable = true)
            gateway.nodesByDescription["show text"] = showTextNode

            val result = strategy.clickShowText(gateway)

            assertTrue(result)
            assertEquals(showTextNode, gateway.lastClickedNode)
            assertEquals(1, gateway.findNodeByTextCallCount)
            assertEquals(1, gateway.findNodeByContentDescriptionCallCount)
            assertEquals(0, gateway.tapCoordinateCallCount)
        }

        @Test
        fun `fallback to coordinate tap when text and description unavailable`() = runTest {
            // No text or description configured
            gateway.tapResult = true

            val result = strategy.clickShowText(gateway)

            assertTrue(result)
            assertEquals(
                config.fallbackShowTextX to config.fallbackShowTextY,
                gateway.lastTapCoordinate
            )
            assertEquals(1, gateway.tapCoordinateCallCount)
            assertEquals(0, gateway.clickNodeCallCount)
        }

        @Test
        fun `returns false when everything fails`() = runTest {
            // No text or description, and tap fails
            gateway.tapResult = false

            val result = strategy.clickShowText(gateway)

            assertFalse(result)
            assertEquals(1, gateway.tapCoordinateCallCount)
        }
    }

    // =========================================================================
    // selectLanguage
    // =========================================================================

    @Nested
    @DisplayName("selectLanguage")
    inner class SelectLanguageTests {

        @Test
        fun `happy path - selector found, language option found, both clicks succeed`() = runTest {
            val selectorNode = createNode(contentDescription = "language", isClickable = true)
            val languageOption = createNode(text = "Hindi", isClickable = true)
            gateway.nodesByDescription["language"] = selectorNode
            gateway.nodesByText["Hindi"] = languageOption

            val result = strategy.selectLanguage(gateway, "Hindi")

            assertTrue(result)
            assertEquals(2, gateway.clickNodeCallCount)
            // First click is the selector, second is the language option
            assertEquals(languageOption, gateway.lastClickedNode)
        }

        @Test
        fun `returns false when selector not found`() = runTest {
            // No selector node configured
            val languageOption = createNode(text = "Hindi", isClickable = true)
            gateway.nodesByText["Hindi"] = languageOption

            val result = strategy.selectLanguage(gateway, "Hindi")

            assertFalse(result)
            assertEquals(0, gateway.clickNodeCallCount)
        }

        @Test
        fun `returns false when selector found but language option not found`() = runTest {
            val selectorNode = createNode(contentDescription = "language", isClickable = true)
            gateway.nodesByDescription["language"] = selectorNode
            // No language option in nodesByText

            val result = strategy.selectLanguage(gateway, "Hindi")

            assertFalse(result)
            // Selector click happened, but then language lookup failed
            assertEquals(1, gateway.clickNodeCallCount)
        }
    }

    // =========================================================================
    // extractTranscriptNodes
    // =========================================================================

    @Nested
    @DisplayName("extractTranscriptNodes")
    inner class ExtractTranscriptNodesTests {

        @Test
        fun `returns only transcript text, filtering out timestamps, controls, and speaker chips`() =
            runTest {
                val transcriptNodes = listOf(
                    // Transcript content — should be included
                    createNode(text = "Hello, this is the first paragraph.", className = "android.widget.TextView"),
                    createNode(text = "And this is the second one.", className = "android.widget.TextView"),
                    // Timestamp — should be excluded
                    createNode(text = "00:00", className = "android.widget.TextView"),
                    createNode(text = "12:34:56", className = "android.widget.TextView"),
                    // Control labels — should be excluded
                    createNode(text = "Show text", className = "android.widget.TextView"),
                    createNode(text = "AI Summary", className = "android.widget.TextView"),
                    createNode(text = "Copy", className = "android.widget.TextView"),
                    createNode(text = "Share", className = "android.widget.TextView"),
                    // Speaker chip — should be excluded
                    createNode(text = "Speaker 1", className = "android.widget.TextView"),
                    createNode(text = "Speaker 2", className = "android.widget.TextView"),
                )
                gateway.nodesByClassName["android.widget.TextView"] = transcriptNodes

                val result = strategy.extractTranscriptNodes(gateway)

                assertEquals(
                    listOf(
                        "Hello, this is the first paragraph.",
                        "And this is the second one."
                    ),
                    result
                )
            }

        @Test
        fun `returns empty list when no text nodes found`() = runTest {
            // No nodes configured for the className
            val result = strategy.extractTranscriptNodes(gateway)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `returns text in correct order`() = runTest {
            val orderedNodes = listOf(
                createNode(text = "First sentence of the transcript.", className = "android.widget.TextView"),
                createNode(text = "Second sentence follows.", className = "android.widget.TextView"),
                createNode(text = "Third and final.", className = "android.widget.TextView"),
            )
            gateway.nodesByClassName["android.widget.TextView"] = orderedNodes

            val result = strategy.extractTranscriptNodes(gateway)

            assertEquals(
                listOf(
                    "First sentence of the transcript.",
                    "Second sentence follows.",
                    "Third and final."
                ),
                result
            )
        }
    }
}
