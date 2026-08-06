package com.sentinel.bridge.feature.ai.prompt

/**
 * Immutable representation of a parsed prompt template loaded from `assets/prompts/`.
 *
 * Each template file contains YAML-style frontmatter (between `---` delimiters) that specifies
 * inference parameters, followed by the prompt body text. This data class captures both the
 * parsed metadata and the raw body content.
 *
 * @property name The file name (without path) used as the template identifier.
 * @property version Schema/prompt version string parsed from frontmatter.
 * @property model Target model identifier (e.g. "qwen3-4b").
 * @property temperature Sampling temperature — lower values produce more deterministic output.
 * @property maxTokens Maximum number of tokens the model should generate.
 * @property topP Nucleus sampling probability threshold.
 * @property topK Top-K sampling — number of highest-probability tokens to consider.
 * @property repeatPenalty Penalty applied to repeated token sequences.
 * @property schema Name of the output JSON schema this prompt targets.
 * @property chatFormat Conversation format the target model expects, applied by
 *   [ChatTemplates]. Defaults to [ChatTemplates.DEFAULT] when the frontmatter omits it.
 * @property body The prompt body text (everything after the closing `---` delimiter).
 */
data class PromptTemplate(
    val name: String,
    val version: String,
    val model: String,
    val temperature: Float,
    val maxTokens: Int,
    val topP: Float,
    val topK: Int,
    val repeatPenalty: Float,
    val schema: String,
    val body: String,
    val chatFormat: String = ChatTemplates.DEFAULT
)
