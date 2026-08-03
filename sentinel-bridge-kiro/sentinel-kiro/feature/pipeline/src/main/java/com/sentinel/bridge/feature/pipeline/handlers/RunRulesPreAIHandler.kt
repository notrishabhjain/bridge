package com.sentinel.bridge.feature.pipeline.handlers

import com.sentinel.bridge.core.common.logging.SentinelLogger
import com.sentinel.bridge.core.domain.model.ErrorCategory
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineStage
import com.sentinel.bridge.core.domain.model.RuleDecision
import com.sentinel.bridge.core.domain.model.SentinelError
import com.sentinel.bridge.feature.ai.rules.RulesEngine
import com.sentinel.bridge.feature.pipeline.BaseCommandHandler
import com.sentinel.bridge.feature.pipeline.CommandResult
import com.sentinel.bridge.feature.pipeline.commands.PipelineCommand
import java.time.Instant
import javax.inject.Inject

/**
 * Handles evaluating Pre-AI rules from the [RulesEngine] before inference begins.
 *
 * Constructs a stub [InputContext] from the session ID (the full context will be
 * supplied by session state once end-to-end wiring is complete) and delegates to
 * [RulesEngine.evaluate] to determine whether the pipeline should proceed, be
 * skipped, or be rejected.
 *
 * ## Decision Mapping
 * - [RuleDecision.Allow] → [CommandResult.Success] — pipeline continues.
 * - [RuleDecision.Ignore] → [CommandResult.Skipped] — pipeline exits gracefully.
 * - [RuleDecision.Reject] → throws exception → triggers [CommandResult.Failure].
 */
class RunRulesPreAIHandler @Inject constructor(
    private val logger: SentinelLogger,
    private val rulesEngine: RulesEngine
) : BaseCommandHandler<PipelineCommand.RunRulesPreAI>(maxRetries = 0) {

    override val stage: PipelineStage = PipelineStage.RULES_PRE

    /**
     * Evaluates pre-AI rules against the pipeline input context.
     *
     * For MVP, constructs a stub [InputContext] using the session ID. The real
     * input context will be loaded from Room/session state once the full pipeline
     * integration is complete (Task 78).
     *
     * @param command The pre-AI rules command containing the session ID.
     * @return [CommandResult.Success] if rules allow, [CommandResult.Skipped] if ignored.
     * @throws RuleRejectedException if a reject rule matches, triggering failure via retry policy.
     */
    override suspend fun doExecute(command: PipelineCommand.RunRulesPreAI): CommandResult {
        logger.logInfo(command.sessionId, stage.name, "Evaluating pre-AI rules")

        // Stub InputContext — real implementation will load from session state
        val inputContext = InputContext(
            sessionId = command.sessionId,
            source = EventSource.CALL,
            rawContent = "", // Will be populated from session state in full integration
            language = "en",
            timestamp = Instant.now(),
            conversationId = null,
            metadata = emptyMap(),
            attachments = emptyList(),
            capabilityProfileVersion = 1,
            recorderStrategy = "xiaomi_default",
            pipelineVersion = 1
        )

        return when (val decision = rulesEngine.evaluate(inputContext)) {
            is RuleDecision.Allow -> {
                logger.logInfo(command.sessionId, stage.name, "Pre-AI rules: ALLOW")
                CommandResult.Success(command.sessionId)
            }
            is RuleDecision.Ignore -> {
                logger.logInfo(
                    command.sessionId,
                    stage.name,
                    "Pre-AI rules: IGNORE (rule=${decision.ruleId}, reason=${decision.reason})"
                )
                CommandResult.Skipped(command.sessionId, decision.reason)
            }
            is RuleDecision.Reject -> {
                logger.logInfo(
                    command.sessionId,
                    stage.name,
                    "Pre-AI rules: REJECT (rule=${decision.ruleId}, reason=${decision.reason})"
                )
                throw RuleRejectedException(decision.ruleId, decision.reason)
            }
        }
    }

    override fun buildError(command: PipelineCommand.RunRulesPreAI, exception: Exception): SentinelError {
        return SentinelError(
            code = "ERR_RULES_PRE_AI",
            category = ErrorCategory.SYSTEM,
            message = exception.message ?: "Pre-AI rules evaluation failed",
            stage = stage,
            retryable = false,
            timestamp = Instant.now(),
            sessionId = command.sessionId
        )
    }
}

/**
 * Exception thrown when a Pre-AI reject rule matches the input.
 *
 * @property ruleId Identifier of the rule that triggered rejection.
 * @property reason Human-readable explanation.
 */
class RuleRejectedException(
    val ruleId: String,
    val reason: String
) : RuntimeException("Rule $ruleId rejected input: $reason")
