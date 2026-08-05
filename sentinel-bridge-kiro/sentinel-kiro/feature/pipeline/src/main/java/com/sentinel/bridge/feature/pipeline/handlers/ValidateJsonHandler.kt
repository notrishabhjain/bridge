package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.validation.JSONValidator
import com.sentinel.bridge.feature.ai.validation.ValidationResult
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles validating raw JSON output from the LLM using [JSONValidator].
 *
 * Runs the validator's repair pipeline (strip fences, fix trailing commas, escape
 * control characters) and maps the [ValidationResult] to the appropriate
 * [CommandResult]:
 * - [ValidationResult.Valid] or [ValidationResult.Repaired] → [CommandResult.Success]
 * - [ValidationResult.Invalid] → throws exception → triggers retry/failure
 *
 * Uses `maxRetries = 1` so one automatic retry is attempted before returning failure.
 */
open class ValidateJsonHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val jsonValidator: JSONValidator,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.ValidateJson>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.VALIDATE_JSON

    /**
     * Validates the model's raw JSON output and confirms the parse stage produced a
     * usable result.
     *
     * This stage runs after parsing, so a structural problem would already have
     * surfaced there. Validating here still earns its place: it records which repairs
     * the output needed, which is the signal that the prompt or model is drifting.
     *
     * @return [CommandResult.Success] if JSON is valid or successfully repaired.
     * @throws JsonValidationException if validation fails after all repair attempts.
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if the
     *         session holds no model output or no parsed result.
     */
    override suspend fun doExecute(command: PipelineCommand.ValidateJson): CommandResult {
        val rawJson = sessionStore.requireRawResponse(command.sessionId)

        // Fails loudly if the parse stage did not store a result.
        sessionStore.requireResult(command.sessionId)

        logger.logInfo(command.sessionId, stage.name, "Validating JSON output")

        return when (val result = jsonValidator.validate(rawJson)) {
            is ValidationResult.Valid -> {
                logger.logInfo(command.sessionId, stage.name, "JSON validation: VALID")
                CommandResult.Success(command.sessionId)
            }
            is ValidationResult.Repaired -> {
                logger.logInfo(
                    command.sessionId,
                    stage.name,
                    "JSON validation: REPAIRED (repairs=${result.repairs.joinToString(", ")})"
                )
                CommandResult.Success(command.sessionId)
            }
            is ValidationResult.Invalid -> {
                logger.logInfo(
                    command.sessionId,
                    stage.name,
                    "JSON validation: INVALID (error=${result.error})"
                )
                throw JsonValidationException(result.error)
            }
        }
    }

    override fun buildError(command: PipelineCommand.ValidateJson, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_VALIDATE_JSON",
            category = ErrorCategory.JSON_VALIDATION,
            message = exception.message ?: "JSON validation failed",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}

/**
 * Exception thrown when JSON validation fails after all repair attempts.
 *
 * @property validationError Description of why validation failed.
 */
class JsonValidationException(
    val validationError: String
) : RuntimeException("JSON validation failed: $validationError")
