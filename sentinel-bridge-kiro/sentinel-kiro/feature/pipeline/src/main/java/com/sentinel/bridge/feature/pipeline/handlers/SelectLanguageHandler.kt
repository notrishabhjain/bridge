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
 * Handles selecting the target transcription language from the Recorder's language picker.
 *
 * Once real business logic is implemented, this handler will use
 * AccessibilityGateway to open the language picker and select the
 * configured target language (e.g., "Hindi").
 */
class SelectLanguageHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.SelectLanguage>(maxRetries = 2) {

    override val stage: PipelineStage = PipelineStage.SELECT_LANGUAGE

    override suspend fun doExecute(command: PipelineCommand.SelectLanguage): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.SelectLanguage, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_SELECT_LANGUAGE",
            category = ErrorCategory.UI_AUTOMATION,
            message = exception.message ?: "Failed to select language",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
