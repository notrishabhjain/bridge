package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.validation.ResponseParser
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Parses the raw LLM output into Kotlin domain objects using [ResponseParser].
 *
 * Strips markdown fences, extracts the JSON payload, and deserializes it into a
 * `PipelineResult`, which is stored on the session for the stages that follow.
 *
 * Throws `ResponseParseException` if parsing fails, which is caught by the retry policy
 * and converted to a [CommandResult.Failure].
 */
class ParseResponseHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val responseParser: ResponseParser,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.ParseResponse>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.PARSE_RESPONSE

    /**
     * Parses the model's raw output into structured domain objects.
     *
     * Provenance fields — session ID, processing time, model, and version identifiers
     * — are overwritten with values taken from the run itself rather than trusted from
     * the model, which has no reliable knowledge of them.
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if the
     *         session holds no model output.
     * @throws ResponseParseException if no valid JSON object is found in the response.
     */
    override suspend fun doExecute(command: PipelineCommand.ParseResponse): CommandResult {
        val rawResponse = sessionStore.requireRawResponse(command.sessionId)
        val state = sessionStore.get(command.sessionId)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Parsing LLM response (${rawResponse.length} chars)"
        )

        val parsed = responseParser.parse(rawResponse)

        val elapsedMs = state?.startedAtMs
            ?.let { System.currentTimeMillis() - it }
            ?: 0L

        val pipelineResult = parsed.copy(
            sessionId = command.sessionId,
            processingTimeMs = elapsedMs,
            model = state?.model.orEmpty(),
            promptVersion = state?.promptVersion.orEmpty(),
            pipelineVersion = PIPELINE_VERSION
        )

        sessionStore.update(command.sessionId) { it.copy(result = pipelineResult) }

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Response parsed successfully (tasks=${pipelineResult.tasks.size}, " +
                "events=${pipelineResult.calendarEvents.size}, " +
                "confidence=${pipelineResult.confidence})"
        )

        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.ParseResponse, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_PARSE_RESPONSE",
            category = ErrorCategory.JSON_VALIDATION,
            message = exception.message ?: "Failed to parse LLM response",
            stage = stage,
            retryable = false,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }

    private companion object {
        /** Version of the pipeline definition that produced the result. */
        const val PIPELINE_VERSION = "1"
    }
}
