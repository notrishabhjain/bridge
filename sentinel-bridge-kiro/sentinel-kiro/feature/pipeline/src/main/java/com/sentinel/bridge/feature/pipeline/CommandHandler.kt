package com.sentinel.bridge.feature.pipeline

import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand

/**
 * Contract for a single pipeline stage handler.
 *
 * Each [PipelineCommand] subclass has exactly one corresponding [CommandHandler]
 * implementation, registered via Hilt multibinding into the [CommandBus] dispatch map.
 *
 * Handlers are responsible for executing the stage logic and returning a [CommandResult].
 * Retry logic is handled by [BaseCommandHandler]; direct implementations should
 * extend [BaseCommandHandler] rather than implementing this interface directly.
 *
 * @param T The specific [PipelineCommand] subclass this handler processes.
 */
interface CommandHandler<T : PipelineCommand> {

    /**
     * The pipeline stage this handler is responsible for.
     *
     * Used by the orchestrator for logging, stage persistence, and error attribution.
     */
    val stage: PipelineStage

    /**
     * Maximum number of retry attempts for this handler.
     *
     * After [maxRetries] failed attempts with exponential backoff, the handler
     * returns [CommandResult.Failure]. The backoff schedule follows:
     * 1s → 2s → 4s → 8s (capped at 8000ms).
     */
    val maxRetries: Int

    /**
     * Executes the pipeline command and returns the result.
     *
     * Implementations with retry logic (via [BaseCommandHandler]) will attempt
     * the operation up to [maxRetries] + 1 times before returning a failure.
     *
     * @param command The pipeline command to execute.
     * @return [CommandResult.Success], [CommandResult.Failure], or [CommandResult.Skipped].
     */
    suspend fun execute(command: T): CommandResult
}
