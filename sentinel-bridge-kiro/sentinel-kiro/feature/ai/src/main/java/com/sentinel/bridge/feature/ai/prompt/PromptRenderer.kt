package com.sentinel.bridge.feature.ai.prompt

import com.sentinel.bridge.core.domain.model.InputContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders prompt templates by injecting variable values into `{{variableName}}` placeholders.
 *
 * Uses simple string replacement with `{{}}` delimiters. If a variable referenced in the
 * template is not present in the provided map, the placeholder is left unchanged (no crash).
 *
 * ## Usage
 *
 * ```kotlin
 * val rendered = promptRenderer.render(template, mapOf("transcript" to "Hello world"))
 * ```
 *
 * ## Variable Syntax
 *
 * Template bodies use Mustache-style double-brace placeholders:
 * ```
 * Summarize the following call transcript in {{language}}:
 * {{transcript}}
 * ```
 */
@Singleton
class PromptRenderer @Inject constructor() {

    /** Regex matching `{{variableName}}` placeholders (allows letters, digits, and underscores). */
    private val placeholderPattern = Regex("""\{\{(\w+)\}\}""")

    /**
     * Renders a [PromptTemplate] by replacing all `{{variableName}}` placeholders in its body
     * with corresponding values from [variables].
     *
     * Placeholders whose keys are not found in [variables] are left as-is in the output.
     *
     * @param template The prompt template whose [PromptTemplate.body] contains placeholders.
     * @param variables A map of variable names to their string values.
     * @return The fully rendered prompt string with all matched placeholders replaced.
     */
    fun render(template: PromptTemplate, variables: Map<String, String>): String {
        return placeholderPattern.replace(template.body) { matchResult ->
            val variableName = matchResult.groupValues[1]
            variables[variableName] ?: matchResult.value
        }
    }

    /**
     * Builds the standard variable map from an [InputContext] and output schema string.
     *
     * This convenience method assembles the common set of variables expected by most prompt
     * templates:
     * - **transcript** — the raw content (speech-to-text output or notification text)
     * - **language** — BCP-47 language tag (e.g. "hi", "en")
     * - **sessionId** — unique pipeline run identifier
     * - **conversationMemory** — prior conversation context (from metadata, empty if absent)
     * - **userPreferences** — serialized user preferences (from metadata, empty if absent)
     * - **schema** — the JSON output schema the model should conform to
     *
     * @param context The pipeline [InputContext] carrying session data and metadata.
     * @param schema The JSON schema string that defines the expected output structure.
     * @return A map of variable names to values, ready to pass to [render].
     */
    fun buildVariables(context: InputContext, schema: String): Map<String, String> {
        return mapOf(
            "transcript" to context.rawContent,
            "language" to context.language,
            "sessionId" to context.sessionId,
            "conversationMemory" to (context.metadata["conversationMemory"] ?: ""),
            "userPreferences" to (context.metadata["userPreferences"] ?: ""),
            "schema" to schema
        )
    }
}
