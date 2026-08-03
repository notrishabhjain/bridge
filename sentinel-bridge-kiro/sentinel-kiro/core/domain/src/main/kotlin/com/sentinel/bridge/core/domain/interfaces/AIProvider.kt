package com.sentinel.bridge.core.domain.interfaces

import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.ProviderHealth

/**
 * Plugin interface for AI inference providers.
 *
 * Implementations manage model lifecycle (load, unload) and perform text generation.
 * For MVP, the sole implementation is `LlamaCppProvider` backed by llama.cpp via JNI.
 */
interface AIProvider {

    /**
     * Unique identifier for this provider instance.
     */
    val id: String

    /**
     * Whether the provider is currently available to accept inference requests.
     *
     * A provider may be unavailable if the model file is missing, checksum is invalid,
     * or insufficient memory is available.
     */
    val isAvailable: Boolean

    /**
     * Loads the AI model into memory, preparing for inference.
     *
     * @return [Result.success] if model loaded successfully, [Result.failure] with the
     *         underlying exception if loading failed.
     */
    suspend fun loadModel(): Result<Unit>

    /**
     * Unloads the AI model from memory, releasing resources.
     */
    suspend fun unloadModel()

    /**
     * Performs text generation using the loaded model.
     *
     * @param prompt The fully rendered prompt string to send to the model.
     * @param config Inference parameters (temperature, maxTokens, etc.).
     * @return Generated text response from the model.
     */
    suspend fun infer(prompt: String, config: InferenceConfig): String

    /**
     * Cancels any in-progress inference operation.
     *
     * Sets an atomic flag that is checked during token generation. The current
     * [infer] call will return with whatever tokens were generated up to cancellation.
     */
    fun cancelInference()

    /**
     * Reports the current health status of this provider.
     *
     * @return [ProviderHealth] indicating operational state, degradation, or unavailability.
     */
    suspend fun health(): ProviderHealth
}
