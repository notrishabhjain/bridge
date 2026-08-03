package com.sentinel.bridge.core.domain.model

/**
 * Configuration parameters for a single AI inference request.
 *
 * Combines values from two sources:
 * - **Prompt frontmatter** (`assets/prompts/<name>_v<N>.md`): [temperature], [maxTokens], [topP], [topK], [repeatPenalty].
 * - **Model config** (`assets/model_config.json`): [contextSize], [threads].
 *
 * Both sources are merged into this config before being passed to
 * [com.sentinel.bridge.core.domain.interfaces.AIProvider.infer].
 *
 * @property temperature Sampling temperature controlling randomness (0.0 = deterministic, 1.0+ = creative).
 * @property maxTokens Maximum number of tokens to generate in a single inference call.
 * @property topP Nucleus sampling threshold — only tokens comprising the top-P cumulative probability are considered.
 * @property topK Top-K sampling parameter — limits candidate tokens to the K most probable.
 * @property repeatPenalty Penalty multiplier applied to tokens that have already appeared, reducing repetition.
 * @property contextSize Maximum context window size in tokens, sourced from model_config.json.
 * @property threads Number of CPU threads to use for inference, sourced from model_config.json.
 */
data class InferenceConfig(
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val contextSize: Int,
    val threads: Int
)
