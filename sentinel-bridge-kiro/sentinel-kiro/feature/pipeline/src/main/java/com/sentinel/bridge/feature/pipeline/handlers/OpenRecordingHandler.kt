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
 * Handles navigating to the most recent recording inside the Recorder UI.
 *
 * Once real business logic is implemented, this handler will use
 * AccessibilityGateway to locate and click the latest recording entry
 * in the Recorder's list view.
 */
class OpenRecordingHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.OpenRecording>(maxRetries = 3) {

    override val stage: PipelineStage = PipelineStage.OPEN_RECORDING

    override suspend fun doExecute(command: PipelineCommand.OpenRecording): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.OpenRecording, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_OPEN_RECORDING",
            category = ErrorCategory.UI_AUTOMATION,
            message = exception.message ?: "Failed to open recording",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
