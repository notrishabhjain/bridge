package com.sentinel.bridge.feature.ai.validation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [JSONValidator].
 *
 * Verifies the validation and repair pipeline including:
 * - Valid JSON passes through unchanged
 * - Markdown code fences are stripped
 * - Trailing commas are fixed
 * - Unescaped control characters are escaped
 * - Completely invalid input returns an error
 * - Multiple repairs are applied and listed
 */
class JSONValidatorTest {

    private lateinit var validator: JSONValidator

    @BeforeEach
    fun setUp() {
        validator = JSONValidator()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Valid JSON object returns Valid result")
    fun validJsonReturnsValidResult() {
        val json = """{"name": "test", "value": 42}"""

        val result = validator.validate(json)

        assertInstanceOf(ValidationResult.Valid::class.java, result)
        assertEquals(json, (result as ValidationResult.Valid).json)
    }

    @Test
    @DisplayName("JSON wrapped in markdown fences returns Repaired with fences stripped")
    fun jsonInMarkdownFencesReturnsRepaired() {
        val json = """
            |```json
            |{"name": "test", "value": 42}
            |```
        """.trimMargin()

        val result = validator.validate(json)

        assertInstanceOf(ValidationResult.Repaired::class.java, result)
        val repaired = result as ValidationResult.Repaired
        assertEquals("""{"name": "test", "value": 42}""", repaired.json)
        assertTrue(repaired.repairs.any { it.contains("fence", ignoreCase = true) })
    }

    @Test
    @DisplayName("JSON with trailing comma returns Repaired with comma fixed")
    fun jsonWithTrailingCommaReturnsRepaired() {
        val json = """{"name": "test", "value": 42,}"""

        val result = validator.validate(json)

        assertInstanceOf(ValidationResult.Repaired::class.java, result)
        val repaired = result as ValidationResult.Repaired
        assertEquals("""{"name": "test", "value": 42}""", repaired.json)
        assertTrue(repaired.repairs.any { it.contains("comma", ignoreCase = true) })
    }

    @Test
    @DisplayName("JSON with unescaped newline in string returns Repaired")
    fun jsonWithUnescapedNewlineReturnsRepaired() {
        // Construct a JSON with a raw newline inside a string value
        val json = "{\"summary\": \"line1\nline2\"}"

        val result = validator.validate(json)

        assertInstanceOf(ValidationResult.Repaired::class.java, result)
        val repaired = result as ValidationResult.Repaired
        assertTrue(repaired.json.contains("\\n"), "Newline should be escaped")
        assertTrue(repaired.repairs.any { it.contains("control", ignoreCase = true) || it.contains("escape", ignoreCase = true) })
    }

    @Test
    @DisplayName("Completely invalid string returns Invalid result")
    fun completelyInvalidStringReturnsInvalid() {
        val notJson = "this is not json at all {{{---"

        val result = validator.validate(notJson)

        assertInstanceOf(ValidationResult.Invalid::class.java, result)
        val invalid = result as ValidationResult.Invalid
        assertTrue(invalid.error.isNotBlank(), "Error message should be present")
    }

    @Test
    @DisplayName("Multiple repairs needed — all listed in repairs field")
    fun multipleRepairsAllListed() {
        // JSON wrapped in fences AND has trailing comma
        val json = """
            |```json
            |{"name": "test", "value": 42,}
            |```
        """.trimMargin()

        val result = validator.validate(json)

        assertInstanceOf(ValidationResult.Repaired::class.java, result)
        val repaired = result as ValidationResult.Repaired
        assertTrue(repaired.repairs.size >= 2, "Should list at least 2 repairs")
        assertTrue(repaired.repairs.any { it.contains("fence", ignoreCase = true) })
        assertTrue(repaired.repairs.any { it.contains("comma", ignoreCase = true) })
    }
}
