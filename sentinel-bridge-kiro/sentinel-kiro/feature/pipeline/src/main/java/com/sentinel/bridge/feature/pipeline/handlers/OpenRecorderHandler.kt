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
 * Handles opening the Xiaomi Recorder application via explicit Intent.
 *
 * Once real business logic is implemented, this handler will launch the
 * Recorder app using an explicit Intent and verify it reaches the foreground
 * via AccessibilityGateway within a configurable timeout.
 */
open class OpenRecorderHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.OpenRecorder>(maxRetries = 2) {

    override val stage: PipelineStage = PipelineStage.OPEN_RECORDER

    override suspend fun doExecute(command: PipelineCommand.OpenRecorder): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.OpenRecorder, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_OPEN_RECORDER",
            category = ErrorCategory.UI_AUTOMATION,
            message = exception.message ?: "Failed to open Recorder app",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
