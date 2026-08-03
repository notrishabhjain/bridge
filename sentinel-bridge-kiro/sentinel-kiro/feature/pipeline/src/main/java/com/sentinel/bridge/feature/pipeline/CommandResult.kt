package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.SentinelError

/**
 * Sealed result type representing the outcome of a [CommandHandler] execution.
 *
 * Every command handler returns one of these variants to indicate whether the
 * pipeline should advance, abort, or skip the current stage.
 */
sealed class CommandResult {

    /**
     * The command executed successfully and the pipeline should advance to the next stage.
     *
     * @property sessionId Unique identifier for the pipeline session that completed this stage.
     */
    data class Success(val sessionId: String) : CommandResult()

    /**
     * The command failed after exhausting all retry attempts.
     *
     * The pipeline will abort and broadcast PIPELINE_FAILED to MacroDroid.
     *
     * @property error Structured error describing what went wrong, including the stage,
     *                 error category, and whether the failure was retryable.
     */
    data class Failure(val error: SentinelError) : CommandResult()

    /**
     * The command was intentionally skipped (e.g., a pre-AI rule returned IGNORE).
     *
     * The pipeline may continue or terminate gracefully depending on the orchestrator's logic.
     *
     * @property sessionId Unique identifier for the pipeline session.
     * @property reason Human-readable explanation of why the stage was skipped.
     */
    data class Skipped(val sessionId: String, val reason: String) : CommandResult()
}
