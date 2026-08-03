package com.sentinel.bridge.feature.pipeline

import android.content.Context
import android.content.Intent
import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.FollowUp
import com.sentinel.bridge.core.domain.model.InputAttachment
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.TaskPriority
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class MacroDroidActionProviderTest {

    private lateinit var context: Context
    private lateinit var provider: MacroDroidActionProvider
    private val intentSlot = slot<Intent>()

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        provider = MacroDroidActionProvider(context)
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

        verify(exactly = 1) { context.sendBroadcast(capture(intentSlot)) }
        val intent = intentSlot.captured

        assertEquals(MacroDroidActionProvider.ACTION_PIPELINE_COMPLETE, intent.action)
        assertEquals("session-abc", intent.getStringExtra(MacroDroidActionProvider.EXTRA_SESSION_ID))
        assertEquals("COMPLETE", intent.getStringExtra(MacroDroidActionProvider.EXTRA_STATUS))
        assertEquals("Meeting follow-up tasks extracted", intent.getStringExtra(MacroDroidActionProvider.EXTRA_SUMMARY))
        assertEquals(0.92f, intent.getFloatExtra(MacroDroidActionProvider.EXTRA_CONFIDENCE, 0f))
        assertEquals(4500L, intent.getLongExtra(MacroDroidActionProvider.EXTRA_PROCESSING_TIME_MS, 0L))
        assertEquals("macro-123", intent.getStringExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID))

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

        verify(exactly = 1) { context.sendBroadcast(capture(intentSlot)) }
        val intent = intentSlot.captured

        assertNull(intent.getStringExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID))
    }

    @Test
    fun `dispatch does not include rawTranscript in intent`() = runTest {
        val result = createPipelineResult(sessionId = "session-no-raw")
        val inputContext = createInputContext(sessionId = "session-no-raw")

        provider.dispatch(result, inputContext)

        verify(exactly = 1) { context.sendBroadcast(capture(intentSlot)) }
        val intent = intentSlot.captured

        assertNull(intent.getStringExtra("rawTranscript"))
        assertNull(intent.getStringExtra("rawContent"))
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

        verify(exactly = 1) { context.sendBroadcast(capture(intentSlot)) }
        val intent = intentSlot.captured

        assertEquals(MacroDroidActionProvider.ACTION_PIPELINE_FAILED, intent.action)
        assertEquals("session-fail", intent.getStringExtra(MacroDroidActionProvider.EXTRA_SESSION_ID))
        assertEquals("FAILED", intent.getStringExtra(MacroDroidActionProvider.EXTRA_STATUS))
        assertEquals("JSON_VALIDATION_FAILED", intent.getStringExtra(MacroDroidActionProvider.EXTRA_ERROR_CODE))
        assertEquals("VALIDATE_JSON", intent.getStringExtra(MacroDroidActionProvider.EXTRA_ERROR_STAGE))
        assertTrue(intent.getBooleanExtra(MacroDroidActionProvider.EXTRA_RETRYABLE, false))
        assertEquals("macro-456", intent.getStringExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID))
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

        verify(exactly = 1) { context.sendBroadcast(capture(intentSlot)) }
        val intent = intentSlot.captured

        assertEquals(MacroDroidActionProvider.ACTION_PIPELINE_FAILED, intent.action)
        assertNull(intent.getStringExtra(MacroDroidActionProvider.EXTRA_MACRO_INVOCATION_ID))
        assertEquals(false, intent.getBooleanExtra(MacroDroidActionProvider.EXTRA_RETRYABLE, true))
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
