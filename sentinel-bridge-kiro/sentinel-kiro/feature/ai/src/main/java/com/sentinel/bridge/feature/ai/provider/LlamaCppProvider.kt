package com.sentinel.bridge.feature.ai.provider

import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.domain.interfaces.AIProvider
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.ProviderHealth
import com.sentinel.bridge.native_.NativeBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device AI provider backed by llama.cpp via JNI.
 *
 * Manages the full lifecycle of the native inference engine: lazy model loading on first
 * [infer] call, automatic unloading after a configurable idle timeout, and cooperative
 * cancellation of in-progress inference via an [AtomicBoolean] flag.
 *
 * Thread safety for model load/unload state transitions is guaranteed by a [Mutex].
 * The idle timeout is driven by a coroutine [Job] scoped to an internal [CoroutineScope]
 * with a [SupervisorJob] so that timeout failures do not propagate.
 *
 * This is the **sole consumer** of [NativeBridge] — no other class should interact
 * with the JNI layer directly.
 *
 * @param nativeBridge JNI bridge to the llama.cpp shared library.
 * @param modelRepository Repository for model file location and integrity verification.
 * @param appSettingsRepository Repository for user-configurable settings including idle timeout.
 */
@Singleton
class LlamaCppProvider @Inject constructor(
    private val nativeBridge: NativeBridge,
    private val modelRepository: ModelRepository,
    private val appSettingsRepository: AppSettingsRepository
) : AIProvider {

    override val id: String = "llama-cpp"

    /**
     * Whether the provider can accept inference requests.
     *
     * Returns `true` only if the model GGUF file exists on disk at the expected path.
     */
    override val isAvailable: Boolean
        get() = modelRepository.modelExists()

    /** Atomic flag used to request cancellation of an in-progress inference. */
    private val cancellationFlag = AtomicBoolean(false)

    /** Whether the native model is currently loaded in memory. */
    private var modelLoaded: Boolean = false

    /** Mutex protecting [modelLoaded] state transitions (load/unload). */
    private val loadMutex = Mutex()

    /** Coroutine scope for the idle-timeout timer. Uses [SupervisorJob] for fault isolation. */
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /** Currently active idle-timeout job, or `null` if no timer is running. */
    private var idleJob: Job? = null

    /**
     * Loads the GGUF model into the native inference engine.
     *
     * Verifies the on-disk model checksum via [ModelRepository.verifyChecksum] before
     * delegating to [NativeBridge.loadModel]. Loading is lazy — if the model is already
     * loaded, this method returns immediately with [Result.success].
     *
     * @return [Result.success] if the model is loaded (or was already loaded),
     *         [Result.failure] if checksum verification fails or native load returns false.
     */
    override suspend fun loadModel(): Result<Unit> = loadMutex.withLock {
        if (modelLoaded) return@withLock Result.success(Unit)

        if (!modelRepository.verifyChecksum()) {
            return@withLock Result.failure(
                IllegalStateException("Model checksum verification failed")
            )
        }

        val config = modelRepository.loadConfig()
        val path = modelRepository.getModelPath()

        val success = withContext(Dispatchers.IO) {
            nativeBridge.loadModel(path, config.contextSize, config.threads)
        }

        if (success) {
            modelLoaded = true
            cancellationFlag.set(false)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("NativeBridge.loadModel returned false"))
        }
    }

    /**
     * Unloads the model from memory and frees native resources.
     *
     * Cancels any active idle timer. Safe to call even if no model is currently loaded.
     */
    override suspend fun unloadModel(): Unit = loadMutex.withLock {
        idleJob?.cancel()
        idleJob = null

        if (modelLoaded) {
            withContext(Dispatchers.IO) {
                nativeBridge.unloadModel()
            }
            modelLoaded = false
        }
    }

    /**
     * Performs text generation using the loaded model.
     *
     * If the model is not yet loaded, it is loaded lazily before inference begins.
     * After inference completes, the idle timer is reset — the model will be unloaded
     * automatically if no further inference occurs within the configured timeout.
     *
     * @param prompt The fully rendered prompt string.
     * @param config Inference parameters (temperature, maxTokens, etc.).
     * @return Generated text from the model.
     * @throws IllegalStateException if model loading fails during lazy initialization.
     */
    override suspend fun infer(prompt: String, config: InferenceConfig): String {
        ensureModelLoaded()
        cancellationFlag.set(false)
        resetIdleTimer()

        val result = withContext(Dispatchers.IO) {
            nativeBridge.infer(
                prompt = prompt,
                maxTokens = config.maxTokens,
                temperature = config.temperature,
                topP = config.topP,
                topK = config.topK,
                repeatPenalty = config.repeatPenalty
            )
        }

        resetIdleTimer()
        return result
    }

    /**
     * Requests cancellation of any in-progress inference.
     *
     * Sets the [cancellationFlag] and delegates to [NativeBridge.cancelInference] which
     * sets the native atomic flag checked on every token-generation iteration. The active
     * [infer] call will return with whatever tokens were generated up to the cancellation point.
     */
    override fun cancelInference() {
        cancellationFlag.set(true)
        nativeBridge.cancelInference()
    }

    /**
     * Reports the current health status of the provider.
     *
     * Delegates to [NativeBridge.health] to check if the native engine has a model loaded
     * and context initialized.
     *
     * @return [ProviderHealth.Healthy] if the native layer reports ready,
     *         [ProviderHealth.Degraded] if the model is loaded but native reports unhealthy,
     *         [ProviderHealth.Unavailable] if no model file exists or model is not loaded.
     */
    override suspend fun health(): ProviderHealth {
        if (!modelRepository.modelExists()) {
            return ProviderHealth.Unavailable(reason = "Model file not found on device")
        }

        if (!modelLoaded) {
            return ProviderHealth.Unavailable(reason = "Model not loaded into memory")
        }

        val nativeHealthy = withContext(Dispatchers.IO) {
            nativeBridge.health()
        }

        return if (nativeHealthy) {
            val config = modelRepository.loadConfig()
            ProviderHealth.Healthy(
                modelName = config.name,
                memoryUsageMb = 0L // Native layer does not expose memory metrics in MVP
            )
        } else {
            ProviderHealth.Degraded(reason = "Native engine reports unhealthy state")
        }
    }

    /**
     * Ensures the model is loaded, performing a lazy load if necessary.
     *
     * @throws IllegalStateException if loading fails.
     */
    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return
        loadModel().getOrThrow()
    }

    /**
     * Resets the idle-timeout timer.
     *
     * Cancels any previously scheduled unload job and starts a new coroutine that waits
     * for [AppSettingsRepository.modelIdleTimeoutMs] before calling [unloadModel].
     * This ensures the model stays loaded between rapid successive inference calls
     * but is reclaimed when the device is idle.
     */
    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = scope.launch {
            val timeoutMs = appSettingsRepository.modelIdleTimeoutMs.first()
            delay(timeoutMs)
            unloadModel()
        }
    }
}
