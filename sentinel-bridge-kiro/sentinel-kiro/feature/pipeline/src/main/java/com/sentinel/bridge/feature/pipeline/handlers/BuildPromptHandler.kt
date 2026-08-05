package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.prompt.OutputSchemas
import com.sentinel.bridge.feature.ai.prompt.PromptRenderer
import com.sentinel.bridge.feature.ai.prompt.PromptRepository
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Renders the inference prompt from the session's transcript and a versioned template.
 *
 * Reads the transcript the extraction stage (or manual entry) produced, renders
 * `task_extraction_v1.md` around it, and stores the finished prompt on the session
 * for the inference stage to pick up.
 */
class BuildPromptHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val promptRepository: PromptRepository,
    private val promptRenderer: PromptRenderer,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.BuildPrompt>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.BUILD_PROMPT

    /**
     * Renders the prompt and records it on the session.
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if no
     *         transcript is present — rendering a prompt around an empty transcript
     *         would send the model an analysis request with nothing to analyse.
     */
    override suspend fun doExecute(command: PipelineCommand.BuildPrompt): CommandResult {
        val transcript = sessionStore.requireTranscript(command.sessionId)
        val state = sessionStore.get(command.sessionId)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Building prompt (transcript=${transcript.length} chars)"
        )

        val template = promptRepository.loadTemplate(TEMPLATE_FILE_NAME)

        val variables = mapOf(
            "transcript" to transcript,
            "language" to (state?.language ?: DEFAULT_LANGUAGE),
            "sessionId" to command.sessionId,
            "currentDate" to LocalDate.now().toString(),
            "conversationMemory" to "",
            "userPreferences" to "",
            "schema" to OutputSchemas.forName(template.schema)
        )

        val renderedPrompt = promptRenderer.render(template, variables)

        sessionStore.update(command.sessionId) {
            it.copy(
                renderedPrompt = renderedPrompt,
                promptVersion = "${template.name}@${template.version}",
                model = template.model
            )
        }

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Prompt rendered (template=${template.name}, version=${template.version}, " +
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

        /** Language assumed when the session did not record one. */
        const val DEFAULT_LANGUAGE = "en"
    }
}
