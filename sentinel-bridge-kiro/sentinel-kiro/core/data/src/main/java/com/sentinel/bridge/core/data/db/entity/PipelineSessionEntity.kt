package com.sentinel.bridge.core.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a single pipeline execution session.
 *
 * Each session tracks the full lifecycle of one pipeline run — from the initial
 * MacroDroid trigger through all processing stages to completion or failure.
 * The [currentStage] field enables WorkManager to resume from the correct point
 * after a process restart.
 *
 * @property sessionId Unique identifier for this pipeline session (UUID).
 * @property source The [EventSource][com.sentinel.bridge.core.domain.model.EventSource] name that triggered this pipeline.
 * @property currentStage The current [PipelineStage][com.sentinel.bridge.core.domain.model.PipelineStage] name in the state machine.
 * @property language The language code for transcription and inference (e.g., "hi" for Hindi).
 * @property callerName Display name of the caller, if available from MacroDroid extras.
 * @property phoneNumber Phone number of the caller, if available from MacroDroid extras.
 * @property callDuration Duration of the call in seconds, if applicable.
 * @property macroInvocationId MacroDroid invocation identifier for correlating result intents.
 * @property createdAt Epoch milliseconds when the session was created.
 * @property updatedAt Epoch milliseconds when the session was last updated.
 * @property completedAt Epoch milliseconds when the session completed or failed, null if still running.
 * @property errorCode Structured error code if the pipeline failed, null on success.
 * @property errorCategory The [ErrorCategory][com.sentinel.bridge.core.domain.model.ErrorCategory] name if the pipeline failed.
 * @property errorMessage Human-readable error description if the pipeline failed.
 * @property retryCount Number of retries attempted for the current stage.
 */
@Entity(tableName = "pipeline_sessions")
data class PipelineSessionEntity(
    @PrimaryKey val sessionId: String,
    val source: String,
    val currentStage: String,
    val language: String,
    val callerName: String?,
    val phoneNumber: String?,
    val callDuration: Long?,
    val macroInvocationId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val completedAt: Long?,
    val errorCode: String?,
    val errorCategory: String?,
    val errorMessage: String?,
    val retryCount: Int
)
