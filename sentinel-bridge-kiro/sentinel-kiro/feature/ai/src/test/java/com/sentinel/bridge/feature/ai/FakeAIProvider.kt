package com.sentinel.bridge.feature.ai

import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.ProviderHealth

/**
 * Test fake implementing [AIProvider] with configurable responses, failure modes, and call tracking.
 *
 * Use this fake in unit and integration tests to verify components that depend on
 * [AIProvider] without requiring the real llama.cpp backend.
 *
 * ## Usage
 *
 * ```kotlin
 * val fake = FakeAIProvider()
 * fake.inferResponse = """{"tasks": []}"""
 * fake.shouldFail = false
 *
 * // Pass to system under test
 * val result = systemUnderTest.runInference(fake)
 *
 * // Assert call tracking
 * assert(fake.inferCallCount == 1)
 * assert(fake.lastPrompt == expectedPrompt)
 * ```
 */
class FakeAIProvider : AIProvider {

    // ──────────────────────────────────────────────────────────────────────────
    // Controllable properties
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The identifier returned by [id].
     */
    var fakeId: String = "fake-provider"

    /**
     * The availability flag returned by [isAvailable].
     */
    var fakeIsAvailable: Boolean = true

    /**
     * When `true`, [infer] throws [failureException] instead of returning [inferResponse].
     */
    var shouldFail: Boolean = false

    /**
     * The exception thrown by [infer] when [shouldFail] is `true`.
     */
    var failureException: Exception = RuntimeException("Fake inference failure")

    /**
     * The response string returned by [infer] on success.
     */
    var inferResponse: String = "{}"

    /**
     * The result returned by [loadModel].
     */
    var loadModelResult: Result<Unit> = Result.success(Unit)

    /**
     * The health status returned by [health].
     */
    var fakeHealth: ProviderHealth = ProviderHealth.Healthy(
        modelName = "fake-model",
        memoryUsageMb = 0L
    )

    // ──────────────────────────────────────────────────────────────────────────
    // Call tracking
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Number of times [loadModel] has been called.
     */
    var loadModelCallCount: Int = 0

    /**
     * Number of times [unloadModel] has been called.
     */
    var unloadModelCallCount: Int = 0

    /**
     * Number of times [infer] has been called.
     */
    var inferCallCount: Int = 0

    /**
     * Number of times [cancelInference] has been called.
     */
    var cancelInferenceCallCount: Int = 0

    /**
     * Number of times [health] has been called.
     */
    var healthCallCount: Int = 0

    // ──────────────────────────────────────────────────────────────────────────
    // Argument capture
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * The prompt argument from the most recent [infer] call, or `null` if never called.
     */
    var lastPrompt: String? = null

    /**
     * The config argument from the most recent [infer] call, or `null` if never called.
     */
    var lastConfig: InferenceConfig? = null

    // ──────────────────────────────────────────────────────────────────────────
    // AIProvider implementation
    // ──────────────────────────────────────────────────────────────────────────

    override val id: String
        get() = fakeId

    override val isAvailable: Boolean
        get() = fakeIsAvailable

    override suspend fun loadModel(): Result<Unit> {
        loadModelCallCount++
        return loadModelResult
    }

    override suspend fun unloadModel() {
        unloadModelCallCount++
    }

    override suspend fun infer(prompt: String, config: InferenceConfig): String {
        inferCallCount++
        lastPrompt = prompt
        lastConfig = config
        if (shouldFail) {
            throw failureException
        }
        return inferResponse
    }

    override fun cancelInference() {
        cancelInferenceCallCount++
    }

    override suspend fun health(): ProviderHealth {
        healthCallCount++
        return fakeHealth
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Test utilities
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Resets all controllable properties, call counts, and captured arguments to their defaults.
     */
    fun reset() {
        fakeId = "fake-provider"
        fakeIsAvailable = true
        shouldFail = false
        failureException = RuntimeException("Fake inference failure")
        inferResponse = "{}"
        loadModelResult = Result.success(Unit)
        fakeHealth = ProviderHealth.Healthy(
            modelName = "fake-model",
            memoryUsageMb = 0L
        )

        loadModelCallCount = 0
        unloadModelCallCount = 0
        inferCallCount = 0
        cancelInferenceCallCount = 0
        healthCallCount = 0

        lastPrompt = null
        lastConfig = null
    }
}
