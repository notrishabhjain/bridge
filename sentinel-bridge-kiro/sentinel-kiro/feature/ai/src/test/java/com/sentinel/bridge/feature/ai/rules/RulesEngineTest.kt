package com.sentinel.bridge.feature.ai.rules

import com.sentinel.bridge.core.domain.model.CalendarEvent
import com.sentinel.bridge.core.domain.model.EventSource
import com.sentinel.bridge.core.domain.model.ExtractedTask
import com.sentinel.bridge.core.domain.model.FollowUp
import com.sentinel.bridge.core.domain.model.InputContext
import com.sentinel.bridge.core.domain.model.PipelineResult
import com.sentinel.bridge.core.domain.model.RuleDecision
import com.sentinel.bridge.core.domain.model.TaskPriority
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * Unit tests for [RulesEngine].
 *
 * Tests Pre-AI and Post-AI rule evaluation phases, priority ordering, disabled rule
 * handling, confidence thresholds, and task filtering.
 *
 * Uses a mock [RuleParser] to inject configurable rule sets without reading from assets.
 */
class RulesEngineTest {

    private lateinit var ruleParser: RuleParser
    private lateinit var engine: RulesEngine

    @BeforeEach
    fun setUp() {
        ruleParser = mockk()
        engine = RulesEngine(ruleParser)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private fun inputContext(rawContent: String): InputContext = InputContext(
        sessionId = "test-session",
        source = EventSource.CALL,
        rawContent = rawContent,
        language = "en",
        timestamp = Instant.now(),
        conversationId = null,
        metadata = emptyMap(),
        attachments = emptyList(),
        capabilityProfileVersion = 1,
        recorderStrategy = "default",
        pipelineVersion = 1
    )

    private fun pipelineResult(
        confidence: Float = 0.9f,
        tasks: List<ExtractedTask> = emptyList()
    ): PipelineResult = PipelineResult(
        sessionId = "test-session",
        summary = "Test summary",
        confidence = confidence,
        tasks = tasks,
        calendarEvents = emptyList(),
        followUps = emptyList(),
        people = emptyList(),
        projects = emptyList(),
        processingTimeMs = 100L,
        model = "qwen3-4b",
        promptVersion = "1.0",
        pipelineVersion = "1"
    )

    private fun preAiRule(
        id: String,
        enabled: Boolean = true,
        priority: Int = 100,
        contains: List<String>,
        action: RuleAction
    ): Rule = Rule(
        id = id,
        enabled = enabled,
        priority = priority,
        phase = RulePhase.PRE_AI,
        description = "Test rule $id",
        match = RuleMatch(
            contains = contains,
            confidenceBelow = null,
            confidenceAbove = null,
            taskConfidenceBelow = null,
            source = null
        ),
        action = action,
        transform = null
    )

    private fun postAiRule(
        id: String,
        enabled: Boolean = true,
        priority: Int = 100,
        action: RuleAction,
        confidenceBelow: Float? = null,
        confidenceAbove: Float? = null,
        taskConfidenceBelow: Float? = null,
        transform: String? = null
    ): Rule = Rule(
        id = id,
        enabled = enabled,
        priority = priority,
        phase = RulePhase.POST_AI,
        description = "Test post-AI rule $id",
        match = RuleMatch(
            contains = null,
            confidenceBelow = confidenceBelow,
            confidenceAbove = confidenceAbove,
            taskConfidenceBelow = taskConfidenceBelow,
            source = null
        ),
        action = action,
        transform = transform
    )

    private fun task(id: String, confidence: Float): ExtractedTask = ExtractedTask(
        id = id,
        title = "Task $id",
        description = "Description for $id",
        priority = TaskPriority.MEDIUM,
        dueDate = null,
        confidence = confidence,
        source = EventSource.CALL
    )

    private fun stubRules(rules: List<Rule>) {
        every { ruleParser.loadRules() } returns rules
        every { ruleParser.loadRuleFile() } returns RuleFile(
            version = 1,
            description = "Test rules",
            rules = rules
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pre-AI Phase
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Pre-AI Phase")
    inner class PreAiPhase {

        @Test
        @DisplayName("OTP content triggers Ignore decision")
        fun otpContentTriggersIgnore() {
            stubRules(
                listOf(
                    preAiRule(
                        id = "otp-filter",
                        contains = listOf("OTP", "verification code"),
                        action = RuleAction.IGNORE
                    )
                )
            )

            val decision = engine.evaluate(inputContext("Your OTP is 123456"))

            assertInstanceOf(RuleDecision.Ignore::class.java, decision)
            assertEquals("otp-filter", (decision as RuleDecision.Ignore).ruleId)
        }

        @Test
        @DisplayName("Promotional content triggers Ignore decision")
        fun promotionalContentTriggersIgnore() {
            stubRules(
                listOf(
                    preAiRule(
                        id = "promo-filter",
                        contains = listOf("limited time offer", "unsubscribe", "exclusive deal"),
                        action = RuleAction.IGNORE
                    )
                )
            )

            val decision = engine.evaluate(inputContext("Get this exclusive deal today! Unsubscribe at..."))

            assertInstanceOf(RuleDecision.Ignore::class.java, decision)
            assertEquals("promo-filter", (decision as RuleDecision.Ignore).ruleId)
        }

        @Test
        @DisplayName("Normal content returns Allow decision")
        fun normalContentReturnsAllow() {
            stubRules(
                listOf(
                    preAiRule(
                        id = "otp-filter",
                        contains = listOf("OTP", "verification code"),
                        action = RuleAction.IGNORE
                    ),
                    preAiRule(
                        id = "promo-filter",
                        contains = listOf("limited time offer"),
                        action = RuleAction.IGNORE
                    )
                )
            )

            val decision = engine.evaluate(inputContext("Please schedule the meeting for tomorrow at 3 PM"))

            assertEquals(RuleDecision.Allow, decision)
        }

        @Test
        @DisplayName("Disabled rule is not evaluated")
        fun disabledRuleNotEvaluated() {
            stubRules(
                listOf(
                    preAiRule(
                        id = "disabled-rule",
                        enabled = false,
                        contains = listOf("OTP"),
                        action = RuleAction.IGNORE
                    )
                )
            )

            val decision = engine.evaluate(inputContext("Your OTP is 123456"))

            assertEquals(RuleDecision.Allow, decision)
        }

        @Test
        @DisplayName("Priority ordering — higher priority evaluated first")
        fun priorityOrderingHigherFirst() {
            stubRules(
                listOf(
                    preAiRule(
                        id = "low-priority",
                        priority = 50,
                        contains = listOf("hello"),
                        action = RuleAction.REJECT
                    ),
                    preAiRule(
                        id = "high-priority",
                        priority = 200,
                        contains = listOf("hello"),
                        action = RuleAction.IGNORE
                    )
                )
            )

            val decision = engine.evaluate(inputContext("hello world"))

            // High priority rule (IGNORE) should fire first, not the REJECT
            assertInstanceOf(RuleDecision.Ignore::class.java, decision)
            assertEquals("high-priority", (decision as RuleDecision.Ignore).ruleId)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Post-AI Phase
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Post-AI Phase")
    inner class PostAiPhase {

        @Test
        @DisplayName("Low confidence → tasks cleared (reject)")
        fun lowConfidenceRejectsResult() {
            stubRules(
                listOf(
                    postAiRule(
                        id = "low-conf-reject",
                        action = RuleAction.REJECT,
                        confidenceBelow = 0.4f
                    )
                )
            )

            val result = pipelineResult(
                confidence = 0.2f,
                tasks = listOf(task("t1", 0.8f))
            )

            val processed = engine.postProcess(result)

            assertTrue(processed.tasks.isEmpty(), "Tasks should be cleared on low confidence reject")
            assertTrue(processed.calendarEvents.isEmpty())
            assertTrue(processed.followUps.isEmpty())
            assertEquals(0.2f, processed.confidence, "Confidence value is preserved")
        }

        @Test
        @DisplayName("Medium confidence → flagged (pass through for MVP)")
        fun mediumConfidenceFlagged() {
            stubRules(
                listOf(
                    postAiRule(
                        id = "medium-conf-flag",
                        action = RuleAction.FLAG,
                        confidenceBelow = 0.7f,
                        confidenceAbove = 0.4f
                    )
                )
            )

            val tasks = listOf(task("t1", 0.8f))
            val result = pipelineResult(confidence = 0.5f, tasks = tasks)

            val processed = engine.postProcess(result)

            // FLAG action passes through unchanged in MVP
            assertEquals(tasks, processed.tasks, "Tasks should pass through when flagged")
            assertEquals(0.5f, processed.confidence)
        }

        @Test
        @DisplayName("High confidence → result unchanged")
        fun highConfidenceUnchanged() {
            stubRules(
                listOf(
                    postAiRule(
                        id = "low-conf-reject",
                        action = RuleAction.REJECT,
                        confidenceBelow = 0.4f
                    ),
                    postAiRule(
                        id = "medium-conf-flag",
                        action = RuleAction.FLAG,
                        confidenceBelow = 0.7f,
                        confidenceAbove = 0.4f
                    )
                )
            )

            val tasks = listOf(task("t1", 0.9f))
            val result = pipelineResult(confidence = 0.95f, tasks = tasks)

            val processed = engine.postProcess(result)

            assertEquals(tasks, processed.tasks, "Tasks should remain unchanged at high confidence")
            assertEquals(0.95f, processed.confidence)
        }

        @Test
        @DisplayName("Low-confidence tasks are filtered out by TRANSFORM rule")
        fun lowConfidenceTasksFilteredOut() {
            stubRules(
                listOf(
                    postAiRule(
                        id = "task-filter",
                        action = RuleAction.TRANSFORM,
                        taskConfidenceBelow = 0.6f,
                        transform = "removeMatchingTasks"
                    )
                )
            )

            val tasks = listOf(
                task("high-conf", 0.9f),
                task("low-conf-1", 0.3f),
                task("borderline", 0.6f),
                task("low-conf-2", 0.5f)
            )
            val result = pipelineResult(confidence = 0.8f, tasks = tasks)

            val processed = engine.postProcess(result)

            assertEquals(2, processed.tasks.size)
            assertTrue(processed.tasks.any { it.id == "high-conf" })
            assertTrue(processed.tasks.any { it.id == "borderline" })
            assertTrue(processed.tasks.none { it.id == "low-conf-1" })
            assertTrue(processed.tasks.none { it.id == "low-conf-2" })
        }
    }
}
