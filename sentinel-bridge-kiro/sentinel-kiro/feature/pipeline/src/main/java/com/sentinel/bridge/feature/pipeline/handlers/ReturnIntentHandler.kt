package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles returning the pipeline result Intent to MacroDroid.
 *
 * Once real business logic is implemented, this handler will broadcast
 * either PIPELINE_COMPLETE or PIPELINE_FAILED with the required extras
 * (sessionId, summary, error details) back to MacroDroid.
 */
class ReturnIntentHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.ReturnIntent>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.RETURN_INTENT

    override suspend fun doExecute(
        command: PipelineCommand.ReturnIntent
    ): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(
        command: PipelineCommand.ReturnIntent,
        exception: Exception
    ): SentinelError {
        return SentinelError(
            code = "ERR_RETURN_INTENT",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Failed to return intent to MacroDroid",
            stage = stage,
            retryable = false,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
