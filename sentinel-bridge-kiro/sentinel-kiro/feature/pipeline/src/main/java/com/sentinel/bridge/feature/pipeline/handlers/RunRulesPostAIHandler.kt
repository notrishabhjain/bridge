package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.rules.RulesEngine
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.PipelineSessionStore
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
 * The post-processed result replaces the one on the session, so the store and dispatch
 * stages persist and broadcast the filtered output rather than the raw model output.
 */
class RunRulesPostAIHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val rulesEngine: RulesEngine,
    private val sessionStore: PipelineSessionStore
) : BaseCommandHandler<PipelineCommand.RunRulesPostAI>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.RULES_POST

    /**
     * Applies post-AI rules to the session's parsed result and writes the outcome back.
     *
     * @throws com.sentinel.bridge.feature.pipeline.MissingSessionStateException if no
     *         parsed result is present.
     * @throws Exception if rule evaluation fails, triggering failure.
     */
    override suspend fun doExecute(command: PipelineCommand.RunRulesPostAI): CommandResult {
        val pipelineResult = sessionStore.requireResult(command.sessionId)

        logger.logInfo(command.sessionId, stage.name, "Evaluating post-AI rules")

        val processedResult = rulesEngine.postProcess(pipelineResult)

        sessionStore.update(command.sessionId) { it.copy(result = processedResult) }

        logger.logInfo(
            command.sessionId,
            stage.name,
            "Post-AI rules applied (tasks=${pipelineResult.tasks.size} → " +
                "${processedResult.tasks.size}, confidence=${processedResult.confidence})"
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
