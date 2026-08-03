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
 * Handles dispatching the final action (e.g., broadcast result to MacroDroid).
 *
 * Once real business logic is implemented, this handler will invoke
 * the appropriate ActionProvider to deliver the pipeline result to
 * the configured destination (MacroDroid intent, notification, etc.).
 */
class DispatchActionHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.DispatchAction>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.DISPATCH_ACTION

    override suspend fun doExecute(
        command: PipelineCommand.DispatchAction
    ): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(
        command: PipelineCommand.DispatchAction,
        exception: Exception
    ): SentinelError {
        return SentinelError(
            code = "ERR_DISPATCH_ACTION",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Failed to dispatch action",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
