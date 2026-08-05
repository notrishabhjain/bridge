package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.db.dao.PipelineResultDao
import com.sentinel.bridge.core.data.db.entity.PipelineResultEntity
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineResultJson
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Persists the parsed pipeline result to Room.
 *
 * Runs before the dispatch stage broadcasts the result, so anything MacroDroid
 * receives is already retrievable from the database. Collections are stored as JSON
 * strings to match [PipelineResultEntity].
 */
class StoreResultHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val pipelineResultDao: PipelineResultDao,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.StoreResult>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.STORE_RESULT

    /**
     * Writes the session's parsed result to [PipelineResultDao].
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if no
     *         parsed result is present.
     */
    override suspend fun doExecute(
        command: PipelineCommand.StoreResult
    ): CommandResult {
        val result = sessionStore.requireResult(command.sessionId)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Persisting result (tasks=${result.tasks.size}, events=${result.calendarEvents.size})"
        )

        pipelineResultDao.insert(
            PipelineResultEntity(
                sessionId = command.sessionId,
                summary = result.summary,
                confidence = result.confidence,
                tasksJson = PipelineResultJson.tasks(result.tasks),
                calendarEventsJson = PipelineResultJson.calendarEvents(result.calendarEvents),
                followUpsJson = PipelineResultJson.followUps(result.followUps),
                peopleJson = PipelineResultJson.strings(result.people),
                projectsJson = PipelineResultJson.strings(result.projects),
                processingTimeMs = result.processingTimeMs,
                model = result.model,
                promptVersion = result.promptVersion,
                pipelineVersion = result.pipelineVersion,
                createdAt = Instant.now().toEpochMilli()
            )
        )

        logger.logInfo(command.sessionId, stage.name, "Result persisted")

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
