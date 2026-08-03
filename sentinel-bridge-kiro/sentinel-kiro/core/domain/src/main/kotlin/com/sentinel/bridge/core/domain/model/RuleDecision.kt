package com.sentinel.bridge.core.domain.model

/**
 * Decision produced by the rules engine after evaluating an [InputContext].
 *
 * Returned by [com.sentinel.bridge.core.domain.interfaces.RuleProvider.evaluate] during
 * the Pre-AI rules phase.
 */
sealed class RuleDecision {

    /**
     * Input should proceed through the pipeline normally.
     */
    data object Allow : RuleDecision()

    /**
     * Input should be silently ignored without generating output.
     *
     * @property ruleId Identifier of the rule that triggered the ignore decision.
     * @property reason Explanation of why the input was ignored.
     */
    data class Ignore(
        val ruleId: String,
        val reason: String
    ) : RuleDecision()

    /**
     * Input should be explicitly rejected with an error response.
     *
     * @property ruleId Identifier of the rule that triggered the rejection.
     * @property reason Explanation of why the input was rejected.
     */
    data class Reject(
        val ruleId: String,
        val reason: String
    ) : RuleDecision()
}
