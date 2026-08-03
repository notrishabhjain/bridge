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
 * Handles clicking the "Show Text" button to trigger transcription display.
 *
 * Once real business logic is implemented, this handler will use
 * AccessibilityGateway to find and tap the "Show text" button within
 * the Recorder UI, initiating the transcription view.
 */
class ClickShowTextHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.ClickShowText>(maxRetries = 3) {

    override val stage: PipelineStage = PipelineStage.CLICK_SHOW_TEXT

    override suspend fun doExecute(command: PipelineCommand.ClickShowText): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.ClickShowText, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_CLICK_SHOW_TEXT",
            category = ErrorCategory.UI_AUTOMATION,
            message = exception.message ?: "Failed to click Show Text button",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
