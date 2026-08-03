package com.sentinel.bridge.core.common.logging

import android.util.Log
import com.sentinel.bridge.core.data.db.entity.LogEntryEntity
import com.sentinel.bridge.core.data.repository.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured logger for the Sentinel pipeline.
 *
 * Every log entry is formatted as a single JSON line and written to Logcat via [Log.d].
 * Additionally, each entry is persisted to Room through [LogRepository] for post-mortem
 * analysis (last 100 sessions are retained by the rotation policy).
 *
 * The JSON format is:
 * ```json
 * {"sessionId":"<id>","stage":"<STAGE>","durationMs":<ms>,"status":"<SUCCESS|FAILURE|WARNING>","message":"<msg>","timestamp":"<ISO-8601>"}
 * ```
 *
 * Persistence is fire-and-forget: a failure in Room write never crashes the pipeline.
 * All persistence runs on [Dispatchers.IO] via a [SupervisorJob]-backed scope so that
 * individual failures do not cancel other pending writes.
 *
 * Usage:
 * ```kotlin
 * sentinelLogger.logInfo(sessionId = "abc-123", stage = "INFERENCE", message = "Model loaded", durationMs = 1200)
 * sentinelLogger.logError(sessionId = "abc-123", stage = "INFERENCE", message = "OOM during inference")
 * ```
 */
@Singleton
class SentinelLogger @Inject constructor(
    private val logRepository: LogRepository
) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Logs an informational event with status SUCCESS.
     *
     * @param sessionId The pipeline session identifier.
     * @param stage The pipeline stage name (e.g. "INFERENCE", "PREPROCESS").
     * @param message A human-readable description of the event.
     * @param durationMs Optional duration of the operation in milliseconds.
     */
    fun logInfo(sessionId: String, stage: String, message: String, durationMs: Long? = null) {
        log(sessionId = sessionId, stage = stage, message = message, durationMs = durationMs, status = "SUCCESS", level = "INFO")
    }

    /**
     * Logs a warning event with status WARNING.
     *
     * @param sessionId The pipeline session identifier.
     * @param stage The pipeline stage name (e.g. "INFERENCE", "PREPROCESS").
     * @param message A human-readable description of the warning condition.
     * @param durationMs Optional duration of the operation in milliseconds.
     */
    fun logWarn(sessionId: String, stage: String, message: String, durationMs: Long? = null) {
        log(sessionId = sessionId, stage = stage, message = message, durationMs = durationMs, status = "WARNING", level = "WARN")
    }

    /**
     * Logs an error event with status FAILURE.
     *
     * @param sessionId The pipeline session identifier.
     * @param stage The pipeline stage name (e.g. "INFERENCE", "PREPROCESS").
     * @param message A human-readable description of the error.
     * @param durationMs Optional duration of the operation in milliseconds.
     */
    fun logError(sessionId: String, stage: String, message: String, durationMs: Long? = null) {
        log(sessionId = sessionId, stage = stage, message = message, durationMs = durationMs, status = "FAILURE", level = "ERROR")
    }

    /**
     * Persists a [LogEntryEntity] to Room via [LogRepository].
     *
     * This is a suspend function for callers that need explicit control over
     * persistence timing. For most pipeline logging, prefer [logInfo], [logWarn],
     * or [logError] which handle persistence automatically in a fire-and-forget manner.
     *
     * @param entry The [LogEntryEntity] to persist.
     */
    suspend fun persistEntry(entry: LogEntryEntity) {
        logRepository.insertLogEntry(entry)
    }

    /**
     * Core logging implementation that builds the structured JSON line,
     * writes to Logcat, and fires off Room persistence.
     */
    private fun log(
        sessionId: String,
        stage: String,
        message: String,
        durationMs: Long?,
        status: String,
        level: String
    ) {
        val timestamp = Instant.now().toString()
        val epochMs = Instant.now().toEpochMilli()

        val jsonLine = buildJsonLine(
            sessionId = sessionId,
            stage = stage,
            durationMs = durationMs,
            status = status,
            message = message,
            timestamp = timestamp
        )

        Log.d(TAG, jsonLine)

        val entity = LogEntryEntity(
            sessionId = sessionId,
            stage = stage,
            level = level,
            message = message,
            durationMs = durationMs,
            status = status,
            timestamp = epochMs
        )

        scope.launch {
            try {
                logRepository.insertLogEntry(entity)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // Fire-and-forget: never crash the pipeline due to Room persistence failure.
                // Log the persistence error to Logcat only (avoids infinite recursion).
                Log.d(TAG, """{"sessionId":"$sessionId","stage":"$stage","status":"FAILURE","message":"Log persistence failed: ${escapeJson(e.message ?: "unknown")}","timestamp":"${Instant.now()}"}""")
            }
        }
    }

    /**
     * Builds a structured JSON line for Logcat output.
     *
     * Uses simple string concatenation — no JSON library required for this fixed schema.
     */
    private fun buildJsonLine(
        sessionId: String,
        stage: String,
        durationMs: Long?,
        status: String,
        message: String,
        timestamp: String
    ): String {
        val durationPart = if (durationMs != null) """"durationMs":$durationMs,""" else """"durationMs":null,"""
        return """{"sessionId":"${escapeJson(sessionId)}","stage":"${escapeJson(stage)}",${durationPart}"status":"$status","message":"${escapeJson(message)}","timestamp":"$timestamp"}"""
    }

    /**
     * Escapes special JSON characters in a string value to prevent malformed output.
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    companion object {
        private const val TAG = "SentinelBridge"
    }
}
