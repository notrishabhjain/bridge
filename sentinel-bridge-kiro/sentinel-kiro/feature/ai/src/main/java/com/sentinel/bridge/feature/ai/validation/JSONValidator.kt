package com.sentinel.bridge.feature.ai.validation

import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates raw JSON strings and attempts to repair common formatting issues
 * produced by on-device LLM inference.
 *
 * The validator implements a multi-step repair pipeline that applies fixes in a
 * deterministic order. After each repair step the JSON is re-tested for validity.
 * If any step produces valid JSON, the result is returned immediately as [ValidationResult.Repaired].
 *
 * Repair pipeline order:
 * 1. Strip markdown code fences (` ```json ... ``` ` or ` ``` ... ``` `)
 * 2. Fix trailing commas (`,}` → `}`, `,]` → `]`)
 * 3. Fix unescaped control characters (newlines, tabs inside string values)
 *
 * Usage:
 * ```kotlin
 * val validator = JSONValidator()
 * when (val result = validator.validate(rawJson)) {
 *     is ValidationResult.Valid -> // use result.json
 *     is ValidationResult.Repaired -> // use result.json, log result.repairs
 *     is ValidationResult.Invalid -> // handle result.error
 * }
 * ```
 */
@Singleton
class JSONValidator @Inject constructor() {

    /**
     * Validates the given raw JSON string and attempts repair if it is initially invalid.
     *
     * @param rawJson The raw JSON string to validate. May contain markdown fences,
     *               trailing commas, or unescaped control characters.
     * @return [ValidationResult.Valid] if the input parses as a JSON object without modification,
     *         [ValidationResult.Repaired] if repairs were applied successfully,
     *         [ValidationResult.Invalid] if all repair attempts fail.
     */
    fun validate(rawJson: String): ValidationResult {
        // Step 0: Try parsing as-is
        if (isValidJsonObject(rawJson)) {
            return ValidationResult.Valid(rawJson)
        }

        // Apply repair pipeline
        val repairs = mutableListOf<String>()
        var current = rawJson

        // Step 1: Strip markdown fences
        val afterFences = stripMarkdownFences(current)
        if (afterFences != current) {
            repairs.add("Stripped markdown code fences")
            current = afterFences
            if (isValidJsonObject(current)) {
                return ValidationResult.Repaired(current, repairs.toList())
            }
        }

        // Step 2: Fix trailing commas
        val afterCommas = fixTrailingCommas(current)
        if (afterCommas != current) {
            repairs.add("Removed trailing commas")
            current = afterCommas
            if (isValidJsonObject(current)) {
                return ValidationResult.Repaired(current, repairs.toList())
            }
        }

        // Step 3: Fix unescaped control characters
        val afterEscape = fixUnescapedControlCharacters(current)
        if (afterEscape != current) {
            repairs.add("Escaped control characters")
            current = afterEscape
            if (isValidJsonObject(current)) {
                return ValidationResult.Repaired(current, repairs.toList())
            }
        }

        // All repairs failed — return invalid with the last parse error
        return try {
            JSONObject(current)
            // Shouldn't reach here, but if it does, treat as valid after all repairs
            ValidationResult.Repaired(current, repairs.toList())
        } catch (e: Exception) {
            ValidationResult.Invalid("JSON validation failed after all repair attempts: ${e.message}")
        }
    }

    /**
     * Checks whether the given string can be parsed as a valid [JSONObject].
     *
     * @param json The string to test.
     * @return `true` if parsing succeeds without exceptions.
     */
    private fun isValidJsonObject(json: String): Boolean {
        return try {
            JSONObject(json)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Removes markdown code fences from the input string.
     *
     * Handles patterns:
     * - ` ```json\n...\n``` `
     * - ` ```JSON\n...\n``` `
     * - ` ```\n...\n``` `
     *
     * @param input The raw string potentially wrapped in code fences.
     * @return The content inside the fences, or the original input if no fences are found.
     */
    private fun stripMarkdownFences(input: String): String {
        val fencePattern = Regex("""```(?:json|JSON)?\s*\n?(.*?)\n?\s*```""", RegexOption.DOT_MATCHES_ALL)
        val match = fencePattern.find(input)
        return match?.groupValues?.get(1)?.trim() ?: input
    }

    /**
     * Fixes trailing commas before closing braces or brackets.
     *
     * Handles:
     * - `,}` → `}`
     * - `,]` → `]`
     * - Allows whitespace between the comma and closing delimiter.
     *
     * @param input The JSON string with potential trailing commas.
     * @return The JSON string with trailing commas removed.
     */
    private fun fixTrailingCommas(input: String): String {
        return input
            .replace(Regex(",\\s*}"), "}")
            .replace(Regex(",\\s*]"), "]")
    }

    /**
     * Escapes unescaped control characters within JSON string values.
     *
     * Specifically targets raw newline (`\n`), carriage return (`\r`), and
     * tab (`\t`) characters that appear inside quoted string values but are
     * not properly escaped.
     *
     * @param input The JSON string with potential unescaped control characters.
     * @return The JSON string with control characters properly escaped.
     */
    private fun fixUnescapedControlCharacters(input: String): String {
        val result = StringBuilder()
        var inString = false
        var escaped = false

        for (c in input) {
            if (escaped) {
                result.append(c)
                escaped = false
                continue
            }
            if (c == '\\' && inString) {
                result.append(c)
                escaped = true
                continue
            }
            if (c == '"') {
                inString = !inString
                result.append(c)
                continue
            }
            if (inString) {
                when (c) {
                    '\n' -> result.append("\\n")
                    '\r' -> result.append("\\r")
                    '\t' -> result.append("\\t")
                    else -> result.append(c)
                }
            } else {
                result.append(c)
            }
        }
        return result.toString()
    }
}
