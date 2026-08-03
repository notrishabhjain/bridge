package com.sentinel.bridge.core.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single structured log entry in the pipeline.
 *
 * Each log entry is associated with a [PipelineSessionEntity] via [sessionId].
 * Cascade delete ensures log entries are removed when their parent session is deleted,
 * supporting the 100-session rotation policy.
 *
 * @property id Auto-generated primary key.
 * @property sessionId Foreign key reference to the parent [PipelineSessionEntity].
 * @property stage The [PipelineStage][com.sentinel.bridge.core.domain.model.PipelineStage] name during which this entry was logged.
 * @property level Log severity level: INFO, WARN, or ERROR.
 * @property message Structured log message describing the event.
 * @property durationMs Duration of the operation in milliseconds, null if not applicable.
 * @property status Outcome of the logged operation: SUCCESS, FAILURE, or SKIPPED.
 * @property timestamp Epoch milliseconds when this log entry was created.
 */
@Entity(
    tableName = "log_entries",
    foreignKeys = [ForeignKey(
        entity = PipelineSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class LogEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val stage: String,
    val level: String,
    val message: String,
    val durationMs: Long?,
    val status: String,
    val timestamp: Long
)
