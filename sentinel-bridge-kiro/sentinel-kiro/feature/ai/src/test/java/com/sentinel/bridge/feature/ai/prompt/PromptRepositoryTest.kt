package com.sentinel.bridge.feature.ai.prompt

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PromptRepository].
 *
 * Verifies frontmatter parsing logic including valid/quoted values, missing delimiters,
 * missing required fields, default value handling, and template caching.
 *
 * Uses a mock [Context] backed by an in-memory [AssetManager] to avoid filesystem access.
 */
class PromptRepositoryTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var repository: PromptRepository

    @BeforeEach
    fun setUp() {
        context = mockk()
        assetManager = mockk()
        every { context.assets } returns assetManager
        repository = PromptRepository(context)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun stubAsset(fileName: String, content: String) {
        every { assetManager.open("prompts/$fileName") } returns content.byteInputStream()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Parses valid frontmatter with all fields")
    fun parsesValidFrontmatterWithAllFields() {
        val content = """
            |---
            |version: 1.0
            |model: qwen3-4b
            |temperature: 0.3
            |maxTokens: 2048
            |topP: 0.9
            |topK: 40
            |repeatPenalty: 1.1
            |schema: task_extraction
            |---
            |Summarize the following transcript:
            |{{transcript}}
        """.trimMargin()

        stubAsset("task_extraction_v1.md", content)

        val template = repository.loadTemplate("task_extraction_v1.md")

        assertEquals("task_extraction_v1.md", template.name)
        assertEquals("1.0", template.version)
        assertEquals("qwen3-4b", template.model)
        assertEquals(0.3f, template.temperature)
        assertEquals(2048, template.maxTokens)
        assertEquals(0.9f, template.topP)
        assertEquals(40, template.topK)
        assertEquals(1.1f, template.repeatPenalty)
        assertEquals("task_extraction", template.schema)
        assertEquals("Summarize the following transcript:\n{{transcript}}", template.body)
    }

    @Test
    @DisplayName("Parses frontmatter with quoted values (double and single quotes)")
    fun parsesFrontmatterWithQuotedValues() {
        val content = """
            |---
            |version: "2.0"
            |model: 'gemma-3b'
            |temperature: 0.5
            |maxTokens: 1024
            |topP: 0.95
            |topK: 50
            |repeatPenalty: 1.0
            |schema: "summary"
            |---
            |Hello {{name}}
        """.trimMargin()

        stubAsset("summary_v2.md", content)

        val template = repository.loadTemplate("summary_v2.md")

        assertEquals("2.0", template.version)
        assertEquals("gemma-3b", template.model)
        assertEquals("summary", template.schema)
    }

    @Test
    @DisplayName("Throws on missing frontmatter delimiters (---)")
    fun throwsOnMissingFrontmatterDelimiters() {
        val content = """
            |version: 1.0
            |model: qwen3-4b
            |schema: tasks
            |
            |Some body text
        """.trimMargin()

        stubAsset("bad.md", content)

        val exception = assertThrows(IllegalArgumentException::class.java) {
            repository.loadTemplate("bad.md")
        }
        assertEquals(
            "Invalid prompt file 'bad.md': missing frontmatter delimiters (---)",
            exception.message
        )
    }

    @Test
    @DisplayName("Throws on missing required field 'version'")
    fun throwsOnMissingRequiredFieldVersion() {
        val content = """
            |---
            |model: qwen3-4b
            |schema: task_extraction
            |---
            |body
        """.trimMargin()

        stubAsset("no_version.md", content)

        val exception = assertThrows(IllegalStateException::class.java) {
            repository.loadTemplate("no_version.md")
        }
        assertEquals(
            "Missing required frontmatter key 'version' in 'no_version.md'",
            exception.message
        )
    }

    @Test
    @DisplayName("Throws on missing required field 'model'")
    fun throwsOnMissingRequiredFieldModel() {
        val content = """
            |---
            |version: 1.0
            |schema: task_extraction
            |---
            |body
        """.trimMargin()

        stubAsset("no_model.md", content)

        val exception = assertThrows(IllegalStateException::class.java) {
            repository.loadTemplate("no_model.md")
        }
        assertEquals(
            "Missing required frontmatter key 'model' in 'no_model.md'",
            exception.message
        )
    }

    @Test
    @DisplayName("Throws on missing required field 'schema'")
    fun throwsOnMissingRequiredFieldSchema() {
        val content = """
            |---
            |version: 1.0
            |model: qwen3-4b
            |---
            |body
        """.trimMargin()

        stubAsset("no_schema.md", content)

        val exception = assertThrows(IllegalStateException::class.java) {
            repository.loadTemplate("no_schema.md")
        }
        assertEquals(
            "Missing required frontmatter key 'schema' in 'no_schema.md'",
            exception.message
        )
    }

    @Test
    @DisplayName("Uses default values for optional numeric fields when not specified")
    fun usesDefaultValuesForOptionalNumericFields() {
        val content = """
            |---
            |version: 1.0
            |model: qwen3-4b
            |schema: summary
            |---
            |{{transcript}}
        """.trimMargin()

        stubAsset("defaults.md", content)

        val template = repository.loadTemplate("defaults.md")

        assertEquals(0.7f, template.temperature, "Default temperature should be 0.7")
        assertEquals(2048, template.maxTokens, "Default maxTokens should be 2048")
        assertEquals(0.9f, template.topP, "Default topP should be 0.9")
        assertEquals(40, template.topK, "Default topK should be 40")
        assertEquals(1.0f, template.repeatPenalty, "Default repeatPenalty should be 1.0")
        // Defaulting to a chat format rather than raw text matters: an unwrapped prompt
        // leaves an instruct model in completion mode, where it never stops generating.
        assertEquals(ChatTemplates.DEFAULT, template.chatFormat)
    }

    @Test
    @DisplayName("Reads chatFormat from frontmatter when present")
    fun readsChatFormatFromFrontmatter() {
        val content = """
            |---
            |version: 1.0
            |model: some-base-model
            |schema: summary
            |chatFormat: none
            |---
            |{{transcript}}
        """.trimMargin()

        stubAsset("raw.md", content)

        assertEquals(ChatTemplates.NONE, repository.loadTemplate("raw.md").chatFormat)
    }

    @Test
    @DisplayName("Caches templates — second load returns same instance")
    fun cachesTemplatesSecondLoadReturnsSameInstance() {
        val content = """
            |---
            |version: 1.0
            |model: qwen3-4b
            |schema: task_extraction
            |---
            |body
        """.trimMargin()

        stubAsset("cached.md", content)

        val first = repository.loadTemplate("cached.md")
        val second = repository.loadTemplate("cached.md")

        assertSame(first, second, "Second load should return the exact same cached instance")
    }
}
