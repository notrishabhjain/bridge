package com.sentinel.bridge.feature.ai.rules

import com.sentinel.bridge.core.domain.interfaces.RuleProvider
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.RuleDecision
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [RuleProvider] that evaluates configurable rules
 * loaded from `assets/rules/default_rules.json`.
 *
 * The engine operates in two phases:
 *
 * ## Pre-AI Phase
 * Evaluates rules against [InputContext.rawContent] before AI inference begins.
 * Rules are sorted by priority (higher priority number = evaluated first) and the first
 * matching rule with a terminal action ([RuleAction.IGNORE] or [RuleAction.REJECT])
 * short-circuits evaluation and prevents inference from running.
 *
 * ## Post-AI Phase
 * Evaluates rules against [PipelineResult] after inference completes.
 * Applies confidence thresholds, filters low-confidence tasks, and flags results
 * for manual review based on configured thresholds.
 *
 * Rules are never hardcoded — all criteria and thresholds come from the JSON rule file.
 *
 * @param ruleParser Parser that provides cached access to the rule definitions.
 */
@Singleton
class RulesEngine @Inject constructor(
    private val ruleParser: RuleParser
) : RuleProvider {

    /**
     * Version of the currently loaded rule set.
     */
    override val version: Int
        get() = ruleParser.loadRuleFile().version

    /**
     * Evaluates Pre-AI rules against the pipeline input context.
     *
     * Rules in the [RulePhase.PRE_AI] phase are filtered to enabled rules only, sorted
     * by priority (descending — higher priority evaluated first), and evaluated in order.
     * The first rule whose [RuleMatch.contains] list has any substring found in
     * [InputContext.rawContent] (case-insensitive) triggers the associated action.
     *
     * @param context The pipeline input to evaluate rules against.
     * @return [RuleDecision.Ignore] if an IGNORE rule matched,
     *         [RuleDecision.Reject] if a REJECT rule matched,
     *         [RuleDecision.Allow] if no terminal rules matched.
     */
    override fun evaluate(context: InputContext): RuleDecision {
        val preAiRules = ruleParser.loadRules()
            .filter { it.phase == RulePhase.PRE_AI && it.enabled }
            .sortedByDescending { it.priority }

        for (rule in preAiRules) {
            if (matchesPreAiCondition(rule.match, context.rawContent)) {
                return when (rule.action) {
                    RuleAction.IGNORE -> RuleDecision.Ignore(rule.id, rule.description)
                    RuleAction.REJECT -> RuleDecision.Reject(rule.id, rule.description)
                    else -> continue
                }
            }
        }

        return RuleDecision.Allow
    }

    /**
     * Applies Post-AI rules to the inference result.
     *
     * Rules in the [RulePhase.POST_AI] phase are filtered to enabled rules only, sorted
     * by priority (descending), and applied sequentially:
     *
     * - **REJECT**: If overall confidence is below threshold, returns result with empty
     *   tasks/events (effectively rejected — caller checks confidence).
     * - **FLAG**: If confidence falls within the flagged range, adds a metadata marker.
     *   Currently returns the result unchanged as flagging is handled at the pipeline level.
     * - **TRANSFORM** with `removeMatchingTasks`: Removes individual tasks whose confidence
     *   is below the specified [RuleMatch.taskConfidenceBelow] threshold.
     *
     * @param result The pipeline result to post-process.
     * @return Modified [PipelineResult] with post-processing rules applied.
     */
    override fun postProcess(result: PipelineResult): PipelineResult {
        val postAiRules = ruleParser.loadRules()
            .filter { it.phase == RulePhase.POST_AI && it.enabled }
            .sortedByDescending { it.priority }

        var processedResult = result

        for (rule in postAiRules) {
            processedResult = applyPostAiRule(rule, processedResult)
        }

        return processedResult
    }

    /**
     * Applies a single Post-AI rule to the result.
     *
     * @param rule The Post-AI rule to apply.
     * @param result The current pipeline result.
     * @return The result after applying this rule, potentially modified.
     */
    private fun applyPostAiRule(rule: Rule, result: PipelineResult): PipelineResult {
        return when (rule.action) {
            RuleAction.REJECT -> {
                if (matchesConfidenceThreshold(rule.match, result.confidence)) {
                    result.copy(
                        tasks = emptyList(),
                        calendarEvents = emptyList(),
                        followUps = emptyList(),
                        confidence = result.confidence
                    )
                } else {
                    result
                }
            }
            RuleAction.FLAG -> {
                if (matchesFlagCondition(rule.match, result.confidence)) {
                    // Flagging is noted at the pipeline orchestration level.
                    // The result itself passes through unchanged.
                    result
                }  else {
                    result
                }
            }
            RuleAction.TRANSFORM -> {
                if (rule.transform == TRANSFORM_REMOVE_MATCHING_TASKS) {
                    applyRemoveMatchingTasks(rule.match, result)
                } else {
                    result
                }
            }
            else -> result
        }
    }

    /**
     * Checks whether the raw content matches a Pre-AI rule's contains criteria.
     *
     * A match occurs when any string in [RuleMatch.contains] is found as a substring
     * in the raw content (case-insensitive comparison).
     *
     * @param match The rule's match criteria.
     * @param rawContent The raw content to check against.
     * @return `true` if any contains value is found in the content.
     */
    private fun matchesPreAiCondition(match: RuleMatch, rawContent: String): Boolean {
        val containsList = match.contains ?: return false
        val contentLower = rawContent.lowercase()

        return containsList.any { keyword ->
            contentLower.contains(keyword.lowercase())
        }
    }

    /**
     * Checks whether the result confidence matches a reject threshold.
     *
     * @param match The rule's match criteria containing [RuleMatch.confidenceBelow].
     * @param confidence The result's overall confidence score.
     * @return `true` if confidence is below the threshold.
     */
    private fun matchesConfidenceThreshold(match: RuleMatch, confidence: Float): Boolean {
        val threshold = match.confidenceBelow ?: return false
        return confidence < threshold
    }

    /**
     * Checks whether the result confidence falls in the flagging range.
     *
     * A result is flagged when confidence is below [RuleMatch.confidenceBelow] but
     * above [RuleMatch.confidenceAbove] (if specified).
     *
     * @param match The rule's match criteria.
     * @param confidence The result's overall confidence score.
     * @return `true` if confidence falls in the flagging range.
     */
    private fun matchesFlagCondition(match: RuleMatch, confidence: Float): Boolean {
        val below = match.confidenceBelow ?: return false
        val above = match.confidenceAbove

        val isBelowThreshold = confidence < below
        val isAboveFloor = above == null || confidence >= above

        return isBelowThreshold && isAboveFloor
    }

    /**
     * Removes tasks from the result whose confidence is below the specified threshold.
     *
     * @param match The rule's match criteria containing [RuleMatch.taskConfidenceBelow].
     * @param result The pipeline result to filter tasks from.
     * @return Result with low-confidence tasks removed.
     */
    private fun applyRemoveMatchingTasks(match: RuleMatch, result: PipelineResult): PipelineResult {
        val threshold = match.taskConfidenceBelow ?: return result

        val filteredTasks = result.tasks.filter { task ->
            task.confidence >= threshold
        }

        return result.copy(tasks = filteredTasks)
    }

    private companion object {
        /** Transform identifier for removing tasks below the confidence threshold. */
        const val TRANSFORM_REMOVE_MATCHING_TASKS = "removeMatchingTasks"
    }
}
