package com.sentinel.bridge.feature.pipeline

import android.content.Context
import android.content.Intent
import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.TaskPriority
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.unmockkConstructor
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MacroDroidActionProviderTest {

    private lateinit var context: Context
    private lateinit var provider: MacroDroidActionProvider

    @BeforeEach
    fun setUp() {
        mockkConstructor(Intent::class)
        context = mockk(relaxed = true)
        provider = MacroDroidActionProvider(context)
    }

    @AfterEach
    fun tearDown() {
        unmockkConstructor(Intent::class)
    }

    @Test
    fun `id returns macrodroid`() {
        assertEquals("macrodroid", provider.id)
    }

    @Test
    fun `canHandle returns true for CALL source`() {
        assertTrue(provider.canHandle(EventSource.CALL))
    }

    @Test
    fun `canHandle returns true for NOTIFICATION source`() {
        assertTrue(provider.canHandle(EventSource.NOTIFICATION))
    }

    @Test
    fun `canHandle returns true for MANUAL source`() {
        assertTrue(provider.canHandle(EventSource.MANUAL))
    }

    @Test
    fun `dispatch broadcasts PIPELINE_COMPLETE with correct extras`() = runTest {
        val result = createPipelineResult(
            sessionId = "session-abc",
            summary = "Meeting follow-up tasks extracted",
            confidence = 0.92f,
            processingTimeMs = 4500L
        )
        val inputContext = createInputContext(
            sessionId = "session-abc",
            macroInvocationId = "macro-123"
        )

        val outcome = provider.dispatch(result, inputContext)

        verify(exactly = 1) { context.sendBroadcast(any()) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_SESSION_ID, "session-abc") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_STATUS, "COMPLETE") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_SUMMARY, "Meeting follow-up tasks extracted") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_CONFIDENCE, 0.92f) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_PROCESSING_TIME_MS, 4500L) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID, "macro-123") }

        assertInstanceOf(ActionOutcome.Success::class.java, outcome)
        assertEquals("macrodroid", (outcome as ActionOutcome.Success).actionId)
    }

    @Test
    fun `dispatch handles null macroInvocationId in metadata`() = runTest {
        val result = createPipelineResult(sessionId = "session-no-macro")
        val inputContext = createInputContext(
            sessionId = "session-no-macro",
            macroInvocationId = null
        )

        provider.dispatch(result, inputContext)

        verify(exactly = 1) { context.sendBroadcast(any()) }
        // Verify no non-null macroInvocationId extra was added
        verify(exactly = 0) { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID, any<String>()) }
    }

    @Test
    fun `dispatch does not include rawTranscript in intent`() = runTest {
        val result = createPipelineResult(sessionId = "session-no-raw")
        val inputContext = createInputContext(sessionId = "session-no-raw")

        provider.dispatch(result, inputContext)

        verify(exactly = 1) { context.sendBroadcast(any()) }
        verify(exactly = 0) { anyConstructed<Intent>().putExtra(eq("rawTranscript"), any<String>()) }
        verify(exactly = 0) { anyConstructed<Intent>().putExtra(eq("rawContent"), any<String>()) }
    }

    @Test
    fun `broadcastFailure sends PIPELINE_FAILED with correct extras`() {
        provider.broadcastFailure(
            sessionId = "session-fail",
            errorCode = "JSON_VALIDATION_FAILED",
            errorStage = "VALIDATE_JSON",
            retryable = true,
            macroInvocationId = "macro-456"
        )

        verify(exactly = 1) { context.sendBroadcast(any()) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_SESSION_ID, "session-fail") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_STATUS, "FAILED") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_ERROR_CODE, "JSON_VALIDATION_FAILED") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_ERROR_STAGE, "VALIDATE_JSON") }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_RETRYABLE, true) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID, "macro-456") }
    }

    @Test
    fun `broadcastFailure handles null macroInvocationId`() {
        provider.broadcastFailure(
            sessionId = "session-fail-2",
            errorCode = "MODEL_LOADING_FAILED",
            errorStage = "LOAD_MODEL",
            retryable = false,
            macroInvocationId = null
        )

        verify(exactly = 1) { context.sendBroadcast(any()) }
        verify { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_RETRYABLE, false) }
        // Verify no non-null macroInvocationId extra was added
        verify(exactly = 0) { anyConstructed<Intent>().putExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID, any<String>()) }
    }

    private fun createPipelineResult(
        sessionId: String,
        summary: String = "Test summary",
        confidence: Float = 0.95f,
        processingTimeMs: Long = 3000L
    ): PipelineResult = PipelineResult(
        sessionId = sessionId,
        summary = summary,
        confidence = confidence,
        tasks = listOf(
            ExtractedTask(
                id = "task-1",
                title = "Follow up with client",
                description = "Schedule a meeting next week",
                priority = TaskPriority.HIGH,
                dueDate = null,
                confidence = 0.9f,
                source = EventSource.CALL
            )
        ),
        calendarEvents = emptyList(),
        followUps = emptyList(),
        people = listOf("John"),
        projects = emptyList(),
        processingTimeMs = processingTimeMs,
        model = "qwen3-4b",
        promptVersion = "task_extraction_v1",
        pipelineVersion = "1"
    )

    private fun createInputContext(
        sessionId: String,
        macroInvocationId: String? = "default-invocation"
    ): InputContext {
        val metadata = mutableMapOf(
            "callerName" to "John Doe",
            "phoneNumber" to "+919876543210",
            "callDuration" to "120"
        )
        if (macroInvocationId != null) {
            metadata["macroInvocationId"] = macroInvocationId
        }
        return InputContext(
            sessionId = sessionId,
            source = EventSource.CALL,
            rawContent = "Test transcript content",
            language = "hi",
            timestamp = Instant.now(),
            conversationId = null,
            metadata = metadata,
            attachments = emptyList(),
            capabilityProfileVersion = 1,
            recorderStrategy = "HyperOS2RecorderStrategy",
            pipelineVersion = 1
        )
    }
}
