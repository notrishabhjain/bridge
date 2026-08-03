package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles running local LLM inference via [AIProvider] (backed by LlamaCppProvider).
 *
 * Invokes [AIProvider.infer] with the rendered prompt and a default [InferenceConfig].
 * For MVP, the prompt is a placeholder string — the real prompt will come from session
 * state once full pipeline integration is wired (Task 78).
 *
 * The handler uses `maxRetries = 1` to allow one automatic retry on transient inference
 * failures (e.g., OOM during token generation).
 */
open class RunInferenceHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val aiProvider: AIProvider
) : BaseCommandHandler<PipelineCommand.RunInference>(maxRetries = 1) {

    override val stage: PipelineStage = PipelineStage.INFERENCE

    /**
     * Runs LLM inference using the injected [AIProvider].
     *
     * For MVP, uses a default [InferenceConfig] and an empty prompt placeholder.
     * The real prompt and config will be sourced from session state in the full
     * pipeline integration (Task 78).
     *
     * @param command The inference command containing the session ID.
     * @return [CommandResult.Success] after inference completes successfully.
     * @throws Exception if inference fails, triggering retry via [BaseCommandHandler].
     */
    override suspend fun doExecute(command: PipelineCommand.RunInference): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Starting LLM inference")

        // Default config — real values will come from PromptTemplate in full integration
        val config = InferenceConfig(
            temperature = DEFAULT_TEMPERATURE,
            maxTokens = DEFAULT_MAX_TOKENS,
            topP = DEFAULT_TOP_P,
            topK = DEFAULT_TOP_K,
            repeatPenalty = DEFAULT_REPEAT_PENALTY,
            contextSize = DEFAULT_CONTEXT_SIZE,
            threads = DEFAULT_THREADS
        )

        // Stub prompt — real prompt will come from session state (written by BuildPromptHandler)
        val prompt = ""

        val result = aiProvider.infer(prompt, config)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Inference completed (output length=${result.length} chars)"
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
