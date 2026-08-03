package com.sentinel.bridge.core.domain.interfaces

import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.RuleDecision

/**
 * Plugin interface for rule evaluation providers.
 *
 * Implementations evaluate configurable rules at two pipeline phases:
 * - Pre-AI: determines whether input should proceed, be ignored, or rejected.
 * - Post-AI: normalizes, filters, or rejects inference results based on confidence
 *   and content rules.
 *
 * Rules are loaded from versioned JSON files, never hardcoded in Kotlin.
 */
interface RuleProvider {

    /**
     * Version of the currently loaded rule set.
     */
    val version: Int

    /**
     * Evaluates pre-AI rules against the pipeline input context.
     *
     * Called before AI inference to determine whether processing should continue,
     * be silently ignored, or explicitly rejected.
     *
     * @param context The pipeline input to evaluate rules against.
     * @return [RuleDecision] indicating whether to allow, ignore, or reject the input.
     */
    fun evaluate(context: InputContext): RuleDecision

    /**
     * Applies post-AI rules to the inference result.
     *
     * Called after AI inference to normalize outputs, filter low-confidence items,
     * or reject results that violate post-processing rules.
     *
     * @param result The pipeline result to post-process.
     * @return Modified [PipelineResult] with post-processing rules applied.
     */
    fun postProcess(result: PipelineResult): PipelineResult
}
