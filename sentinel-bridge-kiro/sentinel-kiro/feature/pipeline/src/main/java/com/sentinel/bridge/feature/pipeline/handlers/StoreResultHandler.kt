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
 * Handles persisting the validated pipeline result to Room and file storage.
 *
 * Once real business logic is implemented, this handler will write the
 * final PipelineResult entity to Room and save the transcript text
 * to getExternalFilesDir("transcripts")/<sessionId>.txt.
 */
class StoreResultHandler @Inject constructor(
    private val logger: SentinelLogger
) : BaseCommandHandler<PipelineCommand.StoreResult>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.STORE_RESULT

    override suspend fun doExecute(
        command: PipelineCommand.StoreResult
    ): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Executing ${stage.name}")
        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(
        command: PipelineCommand.StoreResult,
        exception: Exception
    ): SentinelError {
        return SentinelError(
            code = "ERR_STORE_RESULT",
            category = ErrorCategory.STORAGE,
            message = exception.message ?: "Failed to store pipeline result",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
