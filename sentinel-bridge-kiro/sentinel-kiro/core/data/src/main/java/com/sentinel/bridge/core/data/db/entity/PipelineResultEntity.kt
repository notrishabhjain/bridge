package com.sentinel.bridge.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing the structured output of a completed pipeline run.
 *
 * Stores the AI-generated analysis results including extracted tasks, calendar events,
 * follow-ups, people, and projects. All structured collections are stored as JSON strings
 * and deserialized at the repository layer.
 *
 * Associated with a [PipelineSessionEntity] via [sessionId] with cascade delete,
 * ensuring results are cleaned up when sessions are rotated.
 *
 * @property sessionId Foreign key and primary key referencing the parent [PipelineSessionEntity].
 * @property summary AI-generated summary of the conversation.
 * @property confidence Overall confidence score (0.0 to 1.0) of the AI output.
 * @property tasksJson JSON array of extracted task objects.
 * @property calendarEventsJson JSON array of extracted calendar event objects.
 * @property followUpsJson JSON array of extracted follow-up action objects.
 * @property peopleJson JSON array of people mentioned in the conversation.
 * @property projectsJson JSON array of projects referenced in the conversation.
 * @property processingTimeMs Total pipeline processing time in milliseconds.
 * @property model Identifier of the LLM model used for inference.
 * @property promptVersion Version identifier of the prompt template used.
 * @property pipelineVersion Version identifier of the pipeline configuration.
 * @property createdAt Epoch milliseconds when the result was persisted.
 */
@Entity(
    tableName = "pipeline_results",
    foreignKeys = [ForeignKey(
        entity = PipelineSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class PipelineResultEntity(
    @PrimaryKey val sessionId: String,
    val summary: String,
    val confidence: Float,
    val tasksJson: String,
    val calendarEventsJson: String,
    val followUpsJson: String,
    val peopleJson: String,
    val projectsJson: String,
    val processingTimeMs: Long,
    val model: String,
    val promptVersion: String,
    val pipelineVersion: String,
    val createdAt: Long
)
