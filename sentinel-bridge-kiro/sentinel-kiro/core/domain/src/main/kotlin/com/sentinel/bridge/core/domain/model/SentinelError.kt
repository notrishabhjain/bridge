package com.sentinel.bridge.core.domain.model

import java.time.Instant

/**
 * Structured error produced by any pipeline stage failure.
 *
 * Every stage handler emits a [SentinelError] on failure. The error is:
 * - Logged to Logcat as structured JSON via [SentinelLogger].
 * - Persisted to Room for the last 100 sessions.
 * - Included in the PIPELINE_FAILED intent extras sent to MacroDroid.
 *
 * The [retryable] flag drives the retry policy: if `true`, the pipeline
 * will attempt exponential backoff retries up to the stage-specific limit.
 *
 * @property code Machine-readable error code (e.g., "ERR_NODE_NOT_FOUND", "ERR_INFERENCE_TIMEOUT").
 * @property category High-level classification for routing and filtering.
 * @property message Human-readable description of what went wrong.
 * @property stage The pipeline stage where the error occurred.
 * @property retryable Whether the pipeline should attempt to retry the failed stage.
 * @property timestamp When the error was created.
 * @property sessionId The pipeline session that produced this error.
 */
data class SentinelError(
    val code: String,
    val category: ErrorCategory,
    val message: String,
    val stage: PipelineStage,
    val retryable: Boolean,
    val timestamp: Instant,
    val sessionId: String
)
