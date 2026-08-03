package com.sentinel.bridge.feature.pipeline

import android.content.Context
import android.content.Intent
import com.sentinel.bridge.core.domain.interfaces.ActionProvider
import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Action provider that dispatches pipeline results to MacroDroid via broadcast Intents.
 *
 * On success, broadcasts [ACTION_PIPELINE_COMPLETE] with structured extras including
 * sessionId, status, summary, confidence, processingTimeMs, and macroInvocationId.
 *
 * On failure, broadcasts [ACTION_PIPELINE_FAILED] with sessionId, status, errorCode,
 * errorStage, retryable, and macroInvocationId.
 *
 * Per architecture rules, rawTranscript is never included in the Intent (stored separately
 * and retrievable via GET_TRANSCRIPT).
 */
@Singleton
class MacroDroidActionProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : ActionProvider {

    override val id: String = "macrodroid"

    /**
     * Handles all event sources for MVP.
     *
     * @param source The event source type to check.
     * @return Always `true` — MacroDroid is the sole action target in MVP.
     */
    override fun canHandle(source: EventSource): Boolean = true

    /**
     * Broadcasts [ACTION_PIPELINE_COMPLETE] with the pipeline result extras.
     *
     * Extras included:
     * - [EXTRA_SESSION_ID]: Unique pipeline session identifier.
     * - [EXTRA_STATUS]: Always "COMPLETE" for successful pipelines.
     * - [EXTRA_SUMMARY]: AI-generated summary of the transcript content.
     * - [EXTRA_CONFIDENCE]: Overall confidence score (0.0–1.0).
     * - [EXTRA_PROCESSING_TIME_MS]: Total pipeline processing time in milliseconds.
     * - [EXTRA_MACRO_INVOCATION_ID]: The original MacroDroid invocation ID (nullable).
     *
     * @param result The processed pipeline result to deliver.
     * @param context The original input context for reference during dispatch.
     * @return [ActionOutcome.Success] after the broadcast is sent.
     */
    override suspend fun dispatch(result: PipelineResult, context: InputContext): ActionOutcome {
        val intent = Intent(ACTION_PIPELINE_COMPLETE).apply {
            putExtra(EXTRA_SESSION_ID, result.sessionId)
            putExtra(EXTRA_STATUS, "COMPLETE")
            putExtra(EXTRA_SUMMARY, result.summary)
            putExtra(EXTRA_CONFIDENCE, result.confidence)
            putExtra(EXTRA_PROCESSING_TIME_MS, result.processingTimeMs)
            putExtra(EXTRA_MACRO_INVOCATION_ID, context.metadata["macroInvocationId"])
        }
        this.context.sendBroadcast(intent)
        return ActionOutcome.Success(actionId = id)
    }

    /**
     * Broadcasts [ACTION_PIPELINE_FAILED] to notify MacroDroid of a pipeline failure.
     *
     * Extras included:
     * - [EXTRA_SESSION_ID]: Unique pipeline session identifier.
     * - [EXTRA_STATUS]: Always "FAILED" for failed pipelines.
     * - [EXTRA_ERROR_CODE]: Machine-readable error code identifying the failure.
     * - [EXTRA_ERROR_STAGE]: The pipeline stage at which the failure occurred.
     * - [EXTRA_RETRYABLE]: Whether MacroDroid may retry the pipeline.
     * - [EXTRA_MACRO_INVOCATION_ID]: The original MacroDroid invocation ID (nullable).
     *
     * @param sessionId Unique identifier of the failed pipeline session.
     * @param errorCode Machine-readable error code (e.g., "JSON_VALIDATION_FAILED").
     * @param errorStage The pipeline stage where the failure occurred.
     * @param retryable Whether the failure is potentially recoverable on retry.
     * @param macroInvocationId The original MacroDroid invocation ID, if available.
     */
    fun broadcastFailure(
        sessionId: String,
        errorCode: String,
        errorStage: String,
        retryable: Boolean,
        macroInvocationId: String?
    ) {
        val intent = Intent(ACTION_PIPELINE_FAILED).apply {
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_STATUS, "FAILED")
            putExtra(EXTRA_ERROR_CODE, errorCode)
            putExtra(EXTRA_ERROR_STAGE, errorStage)
            putExtra(EXTRA_RETRYABLE, retryable)
            putExtra(EXTRA_MACRO_INVOCATION_ID, macroInvocationId)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        /** Broadcast action for successful pipeline completion. */
        const val ACTION_PIPELINE_COMPLETE = "com.sentinel.bridge.PIPELINE_COMPLETE"

        /** Broadcast action for pipeline failure. */
        const val ACTION_PIPELINE_FAILED = "com.sentinel.bridge.PIPELINE_FAILED"

        /** Extra key: unique session identifier. */
        const val EXTRA_SESSION_ID = "sessionId"

        /** Extra key: pipeline status ("COMPLETE" or "FAILED"). */
        const val EXTRA_STATUS = "status"

        /** Extra key: AI-generated summary text. */
        const val EXTRA_SUMMARY = "summary"

        /** Extra key: confidence score (Float, 0.0–1.0). */
        const val EXTRA_CONFIDENCE = "confidence"

        /** Extra key: total processing time in milliseconds. */
        const val EXTRA_PROCESSING_TIME_MS = "processingTimeMs"

        /** Extra key: MacroDroid invocation identifier for correlation. */
        const val EXTRA_MACRO_INVOCATION_ID = "macroInvocationId"

        /** Extra key: machine-readable error code. */
        const val EXTRA_ERROR_CODE = "errorCode"

        /** Extra key: pipeline stage where the error occurred. */
        const val EXTRA_ERROR_STAGE = "errorStage"

        /** Extra key: whether the failure is retryable (Boolean). */
        const val EXTRA_RETRYABLE = "retryable"
    }
}
