package com.sentinel.bridge.core.domain.model

/**
 * Final output of a completed pipeline run after AI inference and post-processing.
 *
 * Contains the structured analysis produced by the AI provider, validated and
 * potentially modified by post-AI rules.
 *
 * @property sessionId Unique identifier for the pipeline session that produced this result.
 * @property summary AI-generated summary of the input content.
 * @property confidence Overall confidence score for the result (0.0 to 1.0).
 * @property tasks Extracted action items from the content.
 * @property calendarEvents Extracted calendar events from the content.
 * @property followUps Extracted follow-up items from the content.
 * @property people Identified people mentioned in the content.
 * @property projects Identified projects referenced in the content.
 * @property processingTimeMs Total pipeline processing time in milliseconds.
 * @property model Identifier of the AI model used for inference.
 * @property promptVersion Version of the prompt template used.
 * @property pipelineVersion Version of the pipeline definition.
 */
data class PipelineResult(
    val sessionId: String,
    val summary: String,
    val confidence: Float,
    val tasks: List<ExtractedTask>,
    val calendarEvents: List<CalendarEvent>,
    val followUps: List<FollowUp>,
    val people: List<String>,
    val projects: List<String>,
    val processingTimeMs: Long,
    val model: String,
    val promptVersion: String,
    val pipelineVersion: String
)

/**
 * A task extracted from transcript content by AI inference.
 *
 * @property id Unique identifier for this task.
 * @property title Short description of the task.
 * @property description Detailed description of the task.
 * @property priority Priority level for the task.
 * @property dueDate Optional due date in ISO-8601 format.
 * @property confidence Confidence score for this extraction (0.0 to 1.0).
 * @property source The event source that originated this task.
 */
data class ExtractedTask(
    val id: String,
    val title: String,
    val description: String,
    val priority: TaskPriority,
    val dueDate: String?,
    val confidence: Float,
    val source: EventSource
)

/**
 * Priority level for an extracted task.
 */
enum class TaskPriority {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * A calendar event extracted from transcript content by AI inference.
 *
 * @property id Unique identifier for this event.
 * @property title Event title.
 * @property dateTime ISO-8601 date-time string.
 * @property description Optional event description.
 */
data class CalendarEvent(
    val id: String,
    val title: String,
    val dateTime: String,
    val description: String?
)

/**
 * A follow-up item extracted from transcript content by AI inference.
 *
 * @property id Unique identifier for this follow-up.
 * @property description Description of the follow-up action.
 * @property person Optional person associated with the follow-up.
 */
data class FollowUp(
    val id: String,
    val description: String,
    val person: String?
)
