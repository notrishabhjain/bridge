package com.sentinel.bridge.feature.ai.rules

/**
 * Phase in which a rule is evaluated during the pipeline.
 */
enum class RulePhase {
    /** Evaluated before AI inference to decide whether input proceeds. */
    PRE_AI,
    /** Evaluated after AI inference to normalize and filter results. */
    POST_AI
}

/**
 * Action to take when a rule matches.
 */
enum class RuleAction {
    /** Silently ignore the input without generating output. */
    IGNORE,
    /** Explicitly reject the input or result with an error. */
    REJECT,
    /** Flag the result for manual review. */
    FLAG,
    /** Transform the result by applying the specified transformation. */
    TRANSFORM,
    /** Allow the input or result to pass through unchanged. */
    PASS
}

/**
 * Match criteria for a rule. Uses the same structure as `default_rules.json`.
 *
 * For Pre-AI rules, [contains] is used to match against raw content.
 * For Post-AI rules, [confidenceBelow], [confidenceAbove], and [taskConfidenceBelow]
 * evaluate numeric thresholds.
 *
 * @property contains List of substrings to match against input content (case-insensitive).
 * @property confidenceBelow Matches when overall confidence is below this threshold.
 * @property confidenceAbove Matches when overall confidence is above this threshold.
 * @property taskConfidenceBelow Matches individual tasks with confidence below this threshold.
 * @property source Optional event source filter.
 */
data class RuleMatch(
    val contains: List<String>?,
    val confidenceBelow: Float?,
    val confidenceAbove: Float?,
    val taskConfidenceBelow: Float?,
    val source: String?
)

/**
 * A single rule loaded from `assets/rules/default_rules.json`.
 *
 * Rules are evaluated in priority order (higher priority number = evaluated first within
 * the same phase). The first matching rule with a terminal action (IGNORE, REJECT) short-circuits
 * evaluation.
 *
 * @property id Unique identifier for this rule.
 * @property enabled Whether this rule is currently active.
 * @property priority Evaluation priority — higher values are evaluated first.
 * @property phase Pipeline phase in which this rule runs.
 * @property description Human-readable description of the rule's purpose.
 * @property match Criteria that determine whether the rule triggers.
 * @property action Action to take when the rule matches.
 * @property transform Optional transformation identifier for [RuleAction.TRANSFORM] rules.
 */
data class Rule(
    val id: String,
    val enabled: Boolean,
    val priority: Int,
    val phase: RulePhase,
    val description: String,
    val match: RuleMatch,
    val action: RuleAction,
    val transform: String?
)

/**
 * Container for the full rule file structure.
 *
 * @property version Schema version of the rule file.
 * @property description Optional human-readable description of the rule set.
 * @property rules List of rules defined in the file.
 */
data class RuleFile(
    val version: Int,
    val description: String?,
    val rules: List<Rule>
)
