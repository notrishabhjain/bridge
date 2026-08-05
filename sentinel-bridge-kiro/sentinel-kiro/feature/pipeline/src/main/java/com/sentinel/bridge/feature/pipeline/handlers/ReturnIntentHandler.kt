package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Terminal stage: releases the run's in-memory working state.
 *
 * The result was already persisted by the store stage and broadcast by the dispatch
 * stage, so nothing here is load-bearing for the caller. Its job is to drop the
 * session's transcript, prompt, and model output, which would otherwise be retained
 * for the process lifetime.
 */
class ReturnIntentHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.ReturnIntent>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.RETURN_INTENT

    override suspend fun doExecute(
        command: PipelineCommand.ReturnIntent
    ): CommandResult {
        sessionStore.clear(command.sessionId)
        logger.logInfo(command.sessionId, stage.name, "Pipeline finished, session state released")
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
