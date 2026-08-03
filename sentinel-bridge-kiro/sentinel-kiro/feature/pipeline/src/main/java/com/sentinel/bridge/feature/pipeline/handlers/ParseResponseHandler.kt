package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.validation.ResponseParser
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles parsing the raw LLM output string into Kotlin domain objects using [ResponseParser].
 *
 * Strips markdown fences, extracts the JSON payload, and deserializes it into a
 * [PipelineResult]. For MVP, the raw response is a placeholder — the real response
 * will be retrieved from session state once full pipeline integration is wired (Task 78).
 *
 * Throws [ResponseParseException] if parsing fails, which is caught by the retry policy
 * and converted to a [CommandResult.Failure].
 */
class ParseResponseHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val responseParser: ResponseParser
) : BaseCommandHandler<PipelineCommand.ParseResponse>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.PARSE_RESPONSE

    /**
     * Parses the raw LLM response into structured domain objects.
     *
     * For MVP, uses a placeholder raw response. The real response will be loaded
     * from session state (written by [RunInferenceHandler]) once the full pipeline
     * integration is complete (Task 78).
     *
     * @param command The parse-response command containing the session ID.
     * @return [CommandResult.Success] after the response is parsed successfully.
     * @throws ResponseParseException if no valid JSON object is found in the response.
     */
    override suspend fun doExecute(command: PipelineCommand.ParseResponse): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Parsing LLM response")

        // Stub raw response — real value will come from session state
        val rawResponse = ""

        val pipelineResult = responseParser.parse(rawResponse)

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
}
