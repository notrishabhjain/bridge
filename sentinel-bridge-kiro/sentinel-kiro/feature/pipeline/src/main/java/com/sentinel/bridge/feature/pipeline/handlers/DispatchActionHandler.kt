package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.data.db.dao.PipelineSessionDao
import com.sentinel.bridge.core.domain.interfaces.ActionProvider
import com.sentinel.bridge.core.domain.model.ActionOutcome
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Delivers the finished result to the configured [ActionProvider].
 *
 * This is the stage that makes a run visible outside the app: for the MacroDroid
 * provider it broadcasts `PIPELINE_COMPLETE` with the extracted tasks and events
 * attached.
 *
 * The [InputContext] handed to the provider is rebuilt from the persisted session row
 * so that correlation data — notably `macroInvocationId`, which lets MacroDroid match
 * a result to the macro run that requested it — survives a resume after process death.
 */
class DispatchActionHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val actionProvider: ActionProvider,
    private val pipelineSessionDao: PipelineSessionDao,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.DispatchAction>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.DISPATCH_ACTION

    /**
     * Hands the session's result to the action provider.
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if no
     *         parsed result is present.
     * @throws IllegalStateException if the session row is missing, or the provider
     *         reports the delivery failed.
     */
    override suspend fun doExecute(
        command: PipelineCommand.DispatchAction
    ): CommandResult {
        val result = sessionStore.requireResult(command.sessionId)
        val session = pipelineSessionDao.getById(command.sessionId)
            ?: error("Session ${command.sessionId} is no longer in the database")

        val source = runCatching { EventSource.valueOf(session.source.uppercase()) }
            .getOrDefault(EventSource.CALL)

        val metadata = buildMap {
            session.callerName?.let { put("callerName", it) }
            session.phoneNumber?.let { put("phoneNumber", it) }
            session.callDuration?.let { put("callDuration", it.toString()) }
            session.macroInvocationId?.let { put("macroInvocationId", it) }
        }

        val inputContext = InputContext(
            sessionId = command.sessionId,
            source = source,
            rawContent = sessionStore.get(command.sessionId)?.transcript.orEmpty(),
            language = session.language,
            timestamp = Instant.ofEpochMilli(session.createdAt),
            conversationId = null,
            metadata = metadata,
            attachments = emptyList(),
            capabilityProfileVersion = 0,
            recorderStrategy = "",
            pipelineVersion = PIPELINE_VERSION
        )

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Dispatching result via '${actionProvider.id}' " +
                "(tasks=${result.tasks.size}, events=${result.calendarEvents.size})"
        )

        return when (val outcome = actionProvider.dispatch(result, inputContext)) {
            is ActionOutcome.Success -> {
                logger.logInfo(command.sessionId, stage.name, "Result dispatched")
                CommandResult.Success(command.sessionId)
            }

            is ActionOutcome.Failure -> error(
                "Action provider '${actionProvider.id}' failed to deliver the result: " +
                    outcome.reason
            )
        }
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

    private companion object {
        /** Pipeline definition version reported to action providers. */
        const val PIPELINE_VERSION = 1
    }
}
