package com.sentinel.bridge.native_

/**
 * Kotlin JNI bridge to the sentinel_native shared library.
 *
 * Exposes exactly five native functions that wrap the llama.cpp inference engine.
 * This class is the sole JNI interface to the native layer and must NOT be used
 * directly by pipeline handlers or any component outside [LlamaCppProvider].
 * All access to the native inference engine is routed through [LlamaCppProvider],
 * which owns the lifecycle and thread-safety guarantees.
 *
 * The native library is loaded eagerly when the class is first referenced, via
 * the companion object static initializer.
 */
class NativeBridge {

    /**
     * Loads a GGUF model from the filesystem into memory.
     *
     * If a model is already loaded, the previous model and context are freed
     * before the new model is initialized. The cancellation flag is reset on
     * successful load.
     *
     * @param modelPath Absolute path to the GGUF model file on device storage.
     * @param contextSize Maximum context window size in tokens (e.g. 2048, 4096).
     * @param threads Number of CPU threads to use for inference computation.
     * @return `true` if the model and context were created successfully, `false` otherwise.
     */
    external fun loadModel(modelPath: String, contextSize: Int, threads: Int): Boolean

    /**
     * Runs text generation (inference) on the currently loaded model.
     *
     * Tokenizes the prompt, processes it through the model, and generates up to
     * [maxTokens] new tokens using the specified sampling parameters. The generation
     * loop checks the cancellation flag on every iteration and stops early if set.
     *
     * @param prompt The input text to feed to the model.
     * @param maxTokens Maximum number of tokens to generate.
     * @param temperature Sampling temperature controlling randomness (0.0 = greedy, higher = more random).
     * @param topP Nucleus sampling threshold; only tokens with cumulative probability above this are considered.
     * @param topK Limits sampling to the top-K most probable tokens.
     * @param repeatPenalty Penalty factor applied to repeated tokens to reduce repetition.
     * @return The generated text as a [String].
     * @throws IllegalStateException if no model is currently loaded.
     * @throws IllegalArgumentException if [prompt] is null.
     * @throws RuntimeException if tokenization or decoding fails.
     */
    external fun infer(
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float
    ): String

    /**
     * Unloads the current model and frees all native resources.
     *
     * Sets the cancellation flag (stopping any in-progress inference), then releases
     * the llama context and model memory. Safe to call even if no model is loaded.
     */
    external fun unloadModel()

    /**
     * Checks whether the native inference engine is ready to accept requests.
     *
     * @return `true` if a model is loaded and the context is initialized, `false` otherwise.
     */
    external fun health(): Boolean

    /**
     * Requests cancellation of any in-progress inference.
     *
     * Sets an atomic flag that the token-generation loop checks on every iteration.
     * The inference will stop at the next token boundary and return whatever text
     * has been generated so far. This call is non-blocking and returns immediately.
     */
    external fun cancelInference()

    companion object {
        init {
            System.loadLibrary("sentinel_native")
        }
    }
}
