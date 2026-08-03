package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand

/**
 * Abstract base class for pipeline command handlers with built-in retry logic.
 *
 * Subclasses implement [doExecute] for the actual stage logic and [buildError]
 * to produce a structured [SentinelError] when all retries are exhausted.
 *
 * The retry mechanism uses [RetryPolicy] with exponential backoff (1s → 2s → 4s → 8s).
 * If the block throws on every attempt, [execute] catches the final exception and
 * returns [CommandResult.Failure] with the error produced by [buildError].
 *
 * @param T The specific [PipelineCommand] subclass this handler processes.
 * @property maxRetries Maximum number of retry attempts for this handler.
 */
abstract class BaseCommandHandler<T : PipelineCommand>(
    override val maxRetries: Int
) : CommandHandler<T> {

    private val retryPolicy = RetryPolicy(maxRetries)

    /**
     * Executes the command with exponential backoff retries.
     *
     * Delegates to [doExecute] for the actual logic. If all attempts fail,
     * returns [CommandResult.Failure] with the error from [buildError].
     *
     * @param command The pipeline command to execute.
     * @return [CommandResult] indicating success, failure, or skip.
     */
    override suspend fun execute(command: T): CommandResult {
        return try {
            retryPolicy.executeWithRetry { doExecute(command) }
        } catch (e: Exception) {
            CommandResult.Failure(buildError(command, e))
        }
    }

    /**
     * Performs the actual stage logic for the given command.
     *
     * Implementations should throw an exception to trigger a retry, or return
     * a [CommandResult] directly to indicate completion.
     *
     * @param command The pipeline command to execute.
     * @return [CommandResult] on successful execution.
     * @throws Exception to trigger retry logic via [RetryPolicy].
     */
    protected abstract suspend fun doExecute(command: T): CommandResult

    /**
     * Builds a structured [SentinelError] from the final exception after all retries are exhausted.
     *
     * Implementations should populate the error with the correct [stage], [ErrorCategory],
     * and session ID extracted from the command.
     *
     * @param command The pipeline command that failed.
     * @param exception The last exception thrown during execution.
     * @return A fully populated [SentinelError] for logging and reporting.
     */
    protected abstract fun buildError(command: T, exception: Exception): SentinelError
}
