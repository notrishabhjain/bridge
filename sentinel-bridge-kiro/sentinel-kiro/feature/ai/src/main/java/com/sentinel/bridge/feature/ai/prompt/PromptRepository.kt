package com.sentinel.bridge.feature.ai.prompt

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for loading and caching prompt templates from the `assets/prompts/` directory.
 *
 * Prompt files follow the convention `<name>_v<N>.md` and contain YAML-style frontmatter
 * between `---` delimiters, followed by the prompt body text. The frontmatter is parsed
 * manually (no YAML library) to extract inference configuration parameters.
 *
 * Parsed templates are cached in a [ConcurrentHashMap] so subsequent requests for the same
 * file return instantly without re-reading from assets.
 *
 * ## File Format
 *
 * ```
 * ---
 * version: "1.0"
 * model: "qwen3-4b"
 * temperature: 0.3
 * maxTokens: 2048
 * topP: 0.9
 * topK: 40
 * repeatPenalty: 1.1
 * schema: "task_extraction"
 * ---
 *
 * <prompt body text here>
 * ```
 *
 * @param context Application context used to access the `assets/` directory.
 */
@Singleton
class PromptRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /** Cache of parsed templates keyed by file name. */
    private val cache = ConcurrentHashMap<String, PromptTemplate>()

    /**
     * Loads and parses a prompt template from `assets/prompts/<fileName>`.
     *
     * If the template has been loaded previously, the cached instance is returned immediately.
     * Otherwise the file is read from assets, frontmatter is parsed, and the result is cached.
     *
     * @param fileName The file name within `assets/prompts/` (e.g. "task_extraction_v1.md").
     * @return The parsed [PromptTemplate] containing both metadata and body.
     * @throws IllegalArgumentException if the file does not contain valid frontmatter delimiters.
     * @throws IllegalStateException if required frontmatter keys are missing.
     */
    fun loadTemplate(fileName: String): PromptTemplate {
        cache[fileName]?.let { return it }

        val rawContent = context.assets.open("prompts/$fileName").bufferedReader().use {
            it.readText()
        }

        val template = parseTemplate(fileName, rawContent)
        cache[fileName] = template
        return template
    }

    /**
     * Lists all prompt template file names available in `assets/prompts/`.
     *
     * @return List of file names (e.g. ["task_extraction_v1.md", "summary_v1.md"]).
     */
    fun listTemplates(): List<String> {
        return context.assets.list("prompts")?.toList() ?: emptyList()
    }

    /**
     * Parses a raw prompt file into a [PromptTemplate].
     *
     * Splits the content on `---` delimiters to separate frontmatter from the body,
     * then parses key-value pairs line by line. Handles both quoted and unquoted values,
     * and converts numeric types appropriately.
     *
     * @param fileName The file name used as the template name.
     * @param rawContent The full text content of the prompt file.
     * @return A fully populated [PromptTemplate].
     * @throws IllegalArgumentException if frontmatter delimiters are not found.
     * @throws IllegalStateException if required keys are missing from frontmatter.
     */
    private fun parseTemplate(fileName: String, rawContent: String): PromptTemplate {
        val parts = rawContent.split("---")

        require(parts.size >= 3) {
            "Invalid prompt file '$fileName': missing frontmatter delimiters (---)"
        }

        val frontmatterRaw = parts[1]
        val body = parts.drop(2).joinToString("---").trimStart('\n', '\r')

        val metadata = parseFrontmatter(frontmatterRaw)

        val version = metadata["version"]
            ?: error("Missing required frontmatter key 'version' in '$fileName'")
        val model = metadata["model"]
            ?: error("Missing required frontmatter key 'model' in '$fileName'")
        val schema = metadata["schema"]
            ?: error("Missing required frontmatter key 'schema' in '$fileName'")

        return PromptTemplate(
            name = fileName,
            version = version,
            model = model,
            temperature = metadata["temperature"]?.toFloat() ?: 0.7f,
            maxTokens = metadata["maxTokens"]?.toIntOrFloat() ?: 2048,
            topP = metadata["topP"]?.toFloat() ?: 0.9f,
            topK = metadata["topK"]?.toIntOrFloat() ?: 40,
            repeatPenalty = metadata["repeatPenalty"]?.toFloat() ?: 1.0f,
            schema = schema,
            body = body,
            chatFormat = metadata["chatFormat"] ?: ChatTemplates.DEFAULT
        )
    }

    /**
     * Parses the frontmatter block into a map of key-value string pairs.
     *
     * Each line is expected to follow the format `key: value`. Leading/trailing whitespace
     * is trimmed. Values wrapped in quotes (single or double) have their quotes stripped.
     * Empty lines and lines without a colon separator are skipped.
     *
     * @param raw The raw frontmatter text (content between `---` delimiters).
     * @return Map of parsed key-value pairs with trimmed, unquoted values.
     */
    private fun parseFrontmatter(raw: String): Map<String, String> {
        val result = mutableMapOf<String, String>()

        raw.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || !trimmed.contains(':')) return@forEach

            val colonIndex = trimmed.indexOf(':')
            val key = trimmed.substring(0, colonIndex).trim()
            val value = trimmed.substring(colonIndex + 1).trim().unquote()

            if (key.isNotEmpty() && value.isNotEmpty()) {
                result[key] = value
            }
        }

        return result
    }

    /**
     * Strips surrounding single or double quotes from a string value.
     *
     * @return The unquoted string, or the original string if not quoted.
     */
    private fun String.unquote(): String {
        if (length < 2) return this
        if ((startsWith('"') && endsWith('"')) || (startsWith('\'') && endsWith('\''))) {
            return substring(1, length - 1)
        }
        return this
    }

    /**
     * Converts a string to [Int], handling values that may be expressed as floats
     * (e.g. "40.0" → 40).
     *
     * @return The integer value, or `null` if the string cannot be parsed.
     */
    private fun String.toIntOrFloat(): Int {
        return toIntOrNull() ?: toFloat().toInt()
    }
}
