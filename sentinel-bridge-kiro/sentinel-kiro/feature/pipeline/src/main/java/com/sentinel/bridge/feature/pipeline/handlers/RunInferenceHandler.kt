package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Runs local LLM inference via [AIProvider] (backed by LlamaCppProvider).
 *
 * Reads the prompt the build-prompt stage rendered, ensures the model is loaded,
 * runs inference, and stores the raw output on the session for the parse stage.
 *
 * Uses `maxRetries = 1` to allow one automatic retry on transient inference
 * failures such as an OOM during token generation.
 */
open class RunInferenceHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val aiProvider: AIProvider,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.RunInference>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.INFERENCE

    /**
     * Runs inference over the session's rendered prompt.
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if no
     *         prompt is present.
     * @throws IllegalStateException if the model could not be loaded, or if the model
     *         returned nothing — an empty completion cannot be parsed and is reported
     *         here where the cause is still visible.
     */
    override suspend fun doExecute(command: PipelineCommand.RunInference): CommandResult {
        val prompt = sessionStore.requirePrompt(command.sessionId)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Starting LLM inference (prompt=${prompt.length} chars)"
        )

        if (!aiProvider.isAvailable) {
            aiProvider.loadModel().getOrElse { cause ->
                throw IllegalStateException("Failed to load the AI model: ${cause.message}", cause)
            }
        }

        val config = InferenceConfig(
            temperature = DEFAULT_TEMPERATURE,
            maxTokens = DEFAULT_MAX_TOKENS,
            topP = DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            repeatPenalty = DEFAULT_REPEAT_PENALTY,
            contextSize = DEFAULT_CONTEXT_SIZE,
            threads = DEFAULT_THREADS
        )

        val output = aiProvider.infer(prompt, config)

        check(output.isNotBlank()) {
            "The model returned an empty response. It may have run out of context or been cancelled."
        }

        sessionStore.update(command.sessionId) { it.copy(rawResponse = output) }

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Inference completed (output=${output.length} chars)"
        )

        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.RunInference, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_INFERENCE",
            category = ErrorCategory.INFERENCE,
            message = exception.message ?: "LLM inference failed",
            stage = stage,
            retryable = true,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }

    private companion object {
        const val DEFAULT_TEMPERATURE = 0.3f
        const val DEFAULT_MAX_TOKENS = 2048
        const val DEFAULT_TOP_P = 0.9f
        const val DEFAULT_TOP_K = 40
        const val DEFAULT_REPEAT_PENALTY = 1.1f
        const val DEFAULT_CONTEXT_SIZE = 4096
        const val DEFAULT_THREADS = 4
    }
}
