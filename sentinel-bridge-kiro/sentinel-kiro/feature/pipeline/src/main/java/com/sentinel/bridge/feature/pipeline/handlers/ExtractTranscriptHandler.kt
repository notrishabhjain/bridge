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
 * Handles extracting transcript text nodes from the Recorder's accessibility tree.
 *
 * Once real business logic is implemented, this handler will traverse
 * the accessibility node tree, skip UI controls, timestamps, AI summary
 * cards, and speaker chips, then concatenate the raw transcript text.
 */
class ExtractTranscriptHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.ExtractTranscript>(maxRetries = 2) {

    override val stage: PipelineStage = PipelineStage.EXTRACT_TRANSCRIPT

    override suspend fun doExecute(command: PipelineCommand.ExtractTranscript): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.ExtractTranscript, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_EXTRACT_TRANSCRIPT",
            category = ErrorCategory.TRANSCRIPTION,
            message = exception.message ?: "Failed to extract transcript",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
