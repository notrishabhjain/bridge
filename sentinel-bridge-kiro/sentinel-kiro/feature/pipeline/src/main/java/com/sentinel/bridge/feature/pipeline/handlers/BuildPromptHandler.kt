package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.prompt.PromptRenderer
import com.sentinel.bridge.feature.ai.prompt.PromptRepository
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles building the inference prompt from a versioned template and session variables.
 *
 * Loads the prompt template via [PromptRepository], constructs a variable map (stubbed
 * for MVP), and renders the final prompt string using [PromptRenderer].
 *
 * For MVP, since handlers are stateless and communicate via Room session state, the
 * rendered prompt is logged for debugging. The full data-flow between handlers (passing
 * the rendered prompt to [RunInferenceHandler]) will be wired in Task 78.
 */
class BuildPromptHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val promptRepository: PromptRepository,
    private val promptRenderer: PromptRenderer
) : BaseCommandHandler<PipelineCommand.BuildPrompt>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.BUILD_PROMPT

    /**
     * Loads the prompt template, builds the variable map, and renders the final prompt.
     *
     * For MVP, uses a stub variable map with placeholder values. The real variables
     * will be sourced from the session's [InputContext] once full pipeline integration
     * is complete (Task 78).
     *
     * @param command The build-prompt command containing the session ID.
     * @return [CommandResult.Success] after the prompt is rendered successfully.
     * @throws Exception if template loading or rendering fails, triggering failure.
     */
    override suspend fun doExecute(command: PipelineCommand.BuildPrompt): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Loading prompt template")

        val template = promptRepository.loadTemplate(TEMPLATE_FILE_NAME)

        // Stub variables — real values come from session's InputContext
        val variables = mapOf(
            "transcript" to "",
            "language" to "en",
            "sessionId" to command.sessionId,
            "conversationMemory" to "",
            "userPreferences" to "",
            "schema" to template.schema
        )

        val renderedPrompt = promptRenderer.render(template, variables)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Prompt rendered successfully (template=${template.name}, version=${template.version}, " +
                "length=${renderedPrompt.length} chars)"
        )

        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.BuildPrompt, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_BUILD_PROMPT",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Failed to build inference prompt",
            stage = stage,
            retryable = false,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }

    private companion object {
        /** Default prompt template file used for task extraction. */
        const val TEMPLATE_FILE_NAME = "task_extraction_v1.md"
    }
}
