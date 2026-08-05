package com.sentinel.bridge.feature.pipeline

import android.content.Context
import android.content.Intent
import com.sentinel.bridge.core.domain.interfaces.ActionProvider
import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
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
            context.metadata["macroInvocationId"]?.let { putExtra(EXTRA_MACRO_INVOCATION_ID, it) }

            putExtra(EXTRA_TASKS_JSON, PipelineResultJson.tasks(result.tasks))
            putExtra(EXTRA_EVENTS_JSON, PipelineResultJson.calendarEvents(result.calendarEvents))
            putTaskExtras(result.tasks)
            putEventExtras(result.calendarEvents)
        }
        this.context.sendBroadcast(intent)
        return ActionOutcome.Success(actionId = id)
    }

    /**
     * Adds one flattened group of extras per task, shaped for a MacroDroid action that
     * creates a Google Task.
     *
     * MacroDroid cannot index into a JSON array in a plain action, so each task is also
     * emitted as numbered scalar extras that a macro loop can read directly using
     * `%task_0_title%` and friends. The JSON array is still provided for macros that do
     * parse it.
     *
     * `dueMillis` is omitted rather than defaulted when a due date is absent or
     * unparseable, so a macro can distinguish "no due date" from a real time.
     */
    private fun Intent.putTaskExtras(tasks: List<ExtractedTask>) {
        putExtra(EXTRA_TASK_COUNT, tasks.size)
        tasks.forEachIndexed { index, task ->
            putExtra("${TASK_PREFIX}${index}_title", task.title)
            putExtra("${TASK_PREFIX}${index}_notes", task.description)
            putExtra("${TASK_PREFIX}${index}_priority", task.priority.name)
            task.dueDate?.let { putExtra("${TASK_PREFIX}${index}_due", it) }
            IsoDateTimes.toEpochMillis(task.dueDate)?.let {
                putExtra("${TASK_PREFIX}${index}_dueMillis", it)
            }
        }
    }

    /**
     * Adds one flattened group of extras per calendar event, shaped for a MacroDroid
     * action that creates a Google Calendar event.
     *
     * Both an ISO string and an epoch-millis form are emitted because the calendar
     * insert intent takes millis while the ISO text is what a human reads in a
     * notification. An end time is derived by adding [IsoDateTimes.DEFAULT_EVENT_DURATION_MS],
     * since the extraction schema captures only a start.
     */
    private fun Intent.putEventExtras(events: List<CalendarEvent>) {
        putExtra(EXTRA_EVENT_COUNT, events.size)
        events.forEachIndexed { index, event ->
            putExtra("${EVENT_PREFIX}${index}_title", event.title)
            putExtra("${EVENT_PREFIX}${index}_description", event.description.orEmpty())
            putExtra("${EVENT_PREFIX}${index}_begin", event.dateTime)
            IsoDateTimes.toEpochMillis(event.dateTime)?.let { beginMillis ->
                putExtra("${EVENT_PREFIX}${index}_beginMillis", beginMillis)
                putExtra(
                    "${EVENT_PREFIX}${index}_endMillis",
                    beginMillis + IsoDateTimes.DEFAULT_EVENT_DURATION_MS
                )
            }
        }
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
            macroInvocationId?.let { putExtra(EXTRA_MACRO_INVOCATION_ID, it) }
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

        /** Extra key: all extracted tasks as a JSON array string. */
        const val EXTRA_TASKS_JSON = "tasksJson"

        /** Extra key: all extracted calendar events as a JSON array string. */
        const val EXTRA_EVENTS_JSON = "eventsJson"

        /** Extra key: number of extracted tasks, bounding the `task_N_*` extras. */
        const val EXTRA_TASK_COUNT = "taskCount"

        /** Extra key: number of extracted events, bounding the `event_N_*` extras. */
        const val EXTRA_EVENT_COUNT = "eventCount"

        /** Prefix for per-task flattened extras, e.g. `task_0_title`. */
        const val TASK_PREFIX = "task_"

        /** Prefix for per-event flattened extras, e.g. `event_0_title`. */
        const val EVENT_PREFIX = "event_"
    }
}
