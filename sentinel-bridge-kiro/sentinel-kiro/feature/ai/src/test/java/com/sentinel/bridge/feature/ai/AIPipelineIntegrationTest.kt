package com.sentinel.bridge.feature.ai

import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.feature.ai.prompt.PromptRenderer
import com.sentinel.bridge.feature.ai.prompt.PromptTemplate
import com.sentinel.bridge.feature.ai.validation.JSONValidator
import com.sentinel.bridge.feature.ai.validation.ValidationResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Integration test for the full AI pipeline using [FakeAIProvider].
 *
 * Verifies the end-to-end flow:
 * 1. Prompt template is rendered with variables injected
 * 2. Rendered prompt is passed to [FakeAIProvider]
 * 3. Response from the fake LLM is validated by [JSONValidator]
 * 4. The pipeline produces a valid [ValidationResult.Valid] or [ValidationResult.Repaired]
 *
 * No mocks are used — real [PromptRenderer] and [JSONValidator] instances are exercised.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AIPipelineIntegrationTest {

    private lateinit var fakeAIProvider: FakeAIProvider
    private lateinit var promptRenderer: PromptRenderer
    private lateinit var jsonValidator: JSONValidator

    private val testTemplate = PromptTemplate(
        name = "task_extraction_v1.md",
        version = "1.0",
        model = "qwen3-4b",
        temperature = 0.3f,
        maxTokens = 2048,
        topP = 0.9f,
        topK = 40,
        repeatPenalty = 1.1f,
        schema = "task_extraction",
        body = "Extract tasks from the following transcript in {{language}}:\n\n{{transcript}}\n\nOutput JSON conforming to:\n{{schema}}"
    )

    private val testContext = InputContext(
        sessionId = "integration-test-001",
        source = EventSource.CALL,
        rawContent = "Please schedule the quarterly review meeting for next Tuesday at 2 PM",
        language = "en",
        timestamp = Instant.now(),
        conversationId = null,
        metadata = emptyMap(),
        attachments = emptyList(),
        capabilityProfileVersion = 1,
        recorderStrategy = "default",
        pipelineVersion = 1
    )

    private val validJsonResponse = """
        |{
        |  "summary": "Schedule quarterly review meeting",
        |  "confidence": 0.92,
        |  "tasks": [
        |    {
        |      "id": "task-001",
        |      "title": "Schedule quarterly review",
        |      "description": "Quarterly review meeting next Tuesday at 2 PM",
        |      "priority": "HIGH",
        |      "dueDate": "2024-01-16T14:00:00Z",
        |      "confidence": 0.95
        |    }
        |  ],
        |  "calendarEvents": [],
        |  "followUps": [],
        |  "people": [],
        |  "projects": []
        |}
    """.trimMargin()

    @BeforeEach
    fun setUp() {
        fakeAIProvider = FakeAIProvider()
        promptRenderer = PromptRenderer()
        jsonValidator = JSONValidator()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Test cases
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("End-to-end: prompt rendered → fake LLM returns valid JSON → validator passes")
    fun endToEndPromptToValidation() = runTest {
        // Configure fake to return valid JSON
        fakeAIProvider.inferResponse = validJsonResponse

        // Step 1: Build variables and render prompt
        val schemaJson = """{"type": "object", "properties": {"tasks": {"type": "array"}}}"""
        val variables = promptRenderer.buildVariables(testContext, schemaJson)
        val renderedPrompt = promptRenderer.render(testTemplate, variables)

        // Step 2: Pass rendered prompt to FakeAIProvider
        val config = InferenceConfig(
            temperature = testTemplate.temperature,
            maxTokens = testTemplate.maxTokens,
            topP = testTemplate.topP,
            topK = testTemplate.topK,
            repeatPenalty = testTemplate.repeatPenalty,
            contextSize = 2048,
            threads = 4
        )
        val rawResponse = fakeAIProvider.infer(renderedPrompt, config)

        // Step 3: Validate JSON response
        val validationResult = jsonValidator.validate(rawResponse)

        // Assertions
        assertInstanceOf(ValidationResult.Valid::class.java, validationResult)
        assertEquals(1, fakeAIProvider.inferCallCount)
        assertEquals(renderedPrompt, fakeAIProvider.lastPrompt)

        // Verify prompt was rendered correctly
        assertTrue(renderedPrompt.contains("en"), "Language should be injected")
        assertTrue(
            renderedPrompt.contains(testContext.rawContent),
            "Transcript should be injected"
        )
        assertTrue(renderedPrompt.contains(schemaJson), "Schema should be injected")
    }

    @Test
    @DisplayName("Prompt rendered correctly with all variables substituted")
    fun promptRenderedCorrectlyWithVariables() = runTest {
        val schemaJson = """{"type": "object"}"""
        val variables = promptRenderer.buildVariables(testContext, schemaJson)
        val rendered = promptRenderer.render(testTemplate, variables)

        // Verify no unresolved placeholders for known variables
        assertTrue(!rendered.contains("{{language}}"), "{{language}} should be replaced")
        assertTrue(!rendered.contains("{{transcript}}"), "{{transcript}} should be replaced")
        assertTrue(!rendered.contains("{{schema}}"), "{{schema}} should be replaced")

        // Verify content is correctly substituted
        assertTrue(rendered.contains("en"))
        assertTrue(rendered.contains("Please schedule the quarterly review meeting"))
        assertTrue(rendered.contains(schemaJson))
    }

    @Test
    @DisplayName("Fake LLM response with markdown fences is repaired by validator")
    fun fakeLlmResponseWithFencesIsRepaired() = runTest {
        // Fake returns JSON wrapped in markdown fences (common LLM behavior)
        fakeAIProvider.inferResponse = """
            |```json
            |$validJsonResponse
            |```
        """.trimMargin()

        val schemaJson = """{"type": "object"}"""
        val variables = promptRenderer.buildVariables(testContext, schemaJson)
        val renderedPrompt = promptRenderer.render(testTemplate, variables)

        val config = InferenceConfig(
            temperature = testTemplate.temperature,
            maxTokens = testTemplate.maxTokens,
            topP = testTemplate.topP,
            topK = testTemplate.topK,
            repeatPenalty = testTemplate.repeatPenalty,
            contextSize = 2048,
            threads = 4
        )
        val rawResponse = fakeAIProvider.infer(renderedPrompt, config)
        val validationResult = jsonValidator.validate(rawResponse)

        assertInstanceOf(ValidationResult.Repaired::class.java, validationResult)
        val repaired = validationResult as ValidationResult.Repaired
        assertTrue(repaired.repairs.any { it.contains("fence", ignoreCase = true) })
        // The repaired JSON should still be valid
        assertTrue(repaired.json.startsWith("{"))
    }

    @Test
    @DisplayName("InferenceConfig is correctly constructed from template parameters")
    fun inferenceConfigFromTemplate() = runTest {
        fakeAIProvider.inferResponse = validJsonResponse

        val config = InferenceConfig(
            temperature = testTemplate.temperature,
            maxTokens = testTemplate.maxTokens,
            topP = testTemplate.topP,
            topK = testTemplate.topK,
            repeatPenalty = testTemplate.repeatPenalty,
            contextSize = 2048,
            threads = 4
        )

        val rendered = promptRenderer.render(testTemplate, mapOf("language" to "en", "transcript" to "hello", "schema" to "{}"))
        fakeAIProvider.infer(rendered, config)

        // Verify the config captured by FakeAIProvider matches template params
        val captured = fakeAIProvider.lastConfig!!
        assertEquals(0.3f, captured.temperature)
        assertEquals(2048, captured.maxTokens)
        assertEquals(0.9f, captured.topP)
        assertEquals(40, captured.topK)
        assertEquals(1.1f, captured.repeatPenalty)
    }
}
