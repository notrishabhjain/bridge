package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.rules.RulesEngine
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles evaluating Post-AI rules from the [RulesEngine] after inference completes.
 *
 * Delegates to [RulesEngine.postProcess] to apply confidence thresholds, filter
 * low-confidence tasks, and flag results for manual review based on configured rules
 * in `assets/rules/default_rules.json`.
 *
 * For MVP, constructs a stub [PipelineResult]. The real result will be loaded from
 * session state once the full pipeline integration is wired (Task 78).
 */
class RunRulesPostAIHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val rulesEngine: RulesEngine
) : BaseCommandHandler<PipelineCommand.RunRulesPostAI>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.RULES_POST

    /**
     * Applies post-AI rules to the pipeline result.
     *
     * For MVP, constructs a stub [PipelineResult] with default values. The real
     * result will be loaded from session state (written by the parse/validate stages)
     * once the full pipeline integration is complete (Task 78).
     *
     * @param command The post-AI rules command containing the session ID.
     * @return [CommandResult.Success] after post-processing rules are applied.
     * @throws Exception if rule evaluation fails, triggering failure.
     */
    override suspend fun doExecute(command: PipelineCommand.RunRulesPostAI): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Evaluating post-AI rules")

        // Stub PipelineResult — real value will come from session state
        val pipelineResult = PipelineResult(
            sessionId = command.sessionId,
            summary = "",
            confidence = 0.0f,
            tasks = emptyList(),
            calendarEvents = emptyList(),
            followUps = emptyList(),
            people = emptyList(),
            projects = emptyList(),
            processingTimeMs = 0L,
            model = "",
            promptVersion = "",
            pipelineVersion = ""
        )

        val processedResult = rulesEngine.postProcess(pipelineResult)

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Post-AI rules applied (tasks=${processedResult.tasks.size}, " +
                "confidence=${processedResult.confidence})"
        )

        return CommandResult.Success(command.sessionId)
    }

    override fun buildError(command: PipelineCommand.RunRulesPostAI, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_RULES_POST_AI",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Post-AI rules evaluation failed",
            stage = stage,
            retryable = false,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}
