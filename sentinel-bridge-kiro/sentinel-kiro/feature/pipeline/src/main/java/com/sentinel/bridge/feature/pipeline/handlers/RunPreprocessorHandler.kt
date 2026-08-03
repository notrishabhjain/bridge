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
 * Handles running the text preprocessor on the raw transcript.
 *
 * Once real business logic is implemented, this handler will normalize
 * Unicode, trim whitespace, collapse blank lines, and apply any
 * configured text transformations before rules evaluation.
 */
class RunPreprocessorHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.RunPreprocessor>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.PREPROCESS

    override suspend fun doExecute(command: PipelineCommand.RunPreprocessor): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.RunPreprocessor, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_PREPROCESS",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Preprocessing failed",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
