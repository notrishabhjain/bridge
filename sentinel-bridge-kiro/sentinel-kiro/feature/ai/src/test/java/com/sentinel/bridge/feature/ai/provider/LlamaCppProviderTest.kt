package com.sentinel.bridge.feature.ai.provider

import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.domain.model.InferenceConfig
import com.sentinel.bridge.core.domain.model.ProviderHealth
import com.sentinel.bridge.native_.NativeBridge
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LlamaCppProviderTest {

    private lateinit var nativeBridge: NativeBridge
    private lateinit var modelRepository: ModelRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var provider: LlamaCppProvider

    private val testConfig = ModelConfig(
        name = "test-model",
        downloadUrl = "https://example.com/model.gguf",
        checksum = "abc123",
        version = "1.0.0",
        contextSize = 2048,
        threads = 4
    )

    @BeforeEach
    fun setUp() {
        nativeBridge = mockk(relaxed = true)
        modelRepository = mockk()
        appSettingsRepository = mockk()

        every { modelRepository.loadConfig() } returns testConfig
        every { modelRepository.getModelPath() } returns "/data/models/test-model.gguf"
        every { modelRepository.modelExists() } returns true
        every { appSettingsRepository.modelIdleTimeoutMs } returns flowOf(300_000L)

        provider = LlamaCppProvider(nativeBridge, modelRepository, appSettingsRepository)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadModel tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadModel - checksum passes, native load returns true → Result.success")
    fun loadModel_checksumPassesAndNativeLoadSucceeds_returnsSuccess() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true

        val result = provider.loadModel()

        assertTrue(result.isSuccess)
        verify(exactly = 1) { nativeBridge.loadModel("/data/models/test-model.gguf", 2048, 4) }
    }

    @Test
    @DisplayName("loadModel - checksum fails → Result.failure")
    fun loadModel_checksumFails_returnsFailure() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns false

        val result = provider.loadModel()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertInstanceOf(IllegalStateException::class.java, exception)
        assertTrue(exception!!.message!!.contains("checksum"))
        verify(exactly = 0) { nativeBridge.loadModel(any(), any(), any()) }
    }

    @Test
    @DisplayName("loadModel - native load returns false → Result.failure")
    fun loadModel_nativeLoadReturnsFalse_returnsFailure() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns false

        val result = provider.loadModel()

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertInstanceOf(IllegalStateException::class.java, exception)
        assertTrue(exception!!.message!!.contains("NativeBridge.loadModel returned false"))
    }

    @Test
    @DisplayName("loadModel - already loaded, doesn't reload → only 1 native call")
    fun loadModel_alreadyLoaded_doesNotReload() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true

        // First load
        provider.loadModel()
        // Second load — should skip
        provider.loadModel()

        verify(exactly = 1) { nativeBridge.loadModel(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // infer tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("infer - model loaded, returns generated text")
    fun infer_modelLoaded_returnsGeneratedText() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true
        every {
            nativeBridge.infer(any(), any(), any(), any(), any(), any())
        } returns "Generated response"

        // Pre-load
        provider.loadModel()

        val config = InferenceConfig(
            temperature = 0.7f,
            maxTokens = 256,
            topP = 0.9f,
            topK = 40,
            repeatPenalty = 1.1f,
            contextSize = 2048,
            threads = 4
        )

        val result = provider.infer("Hello", config)

        assertEquals("Generated response", result)
        verify(exactly = 1) {
            nativeBridge.infer("Hello", 256, 0.7f, 0.9f, 40, 1.1f)
        }
    }

    @Test
    @DisplayName("infer - model not loaded, lazy loads then infers")
    fun infer_modelNotLoaded_lazyLoadsThenInfers() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true
        every {
            nativeBridge.infer(any(), any(), any(), any(), any(), any())
        } returns "Lazy loaded response"

        val config = InferenceConfig(
            temperature = 0.5f,
            maxTokens = 128,
            topP = 0.95f,
            topK = 50,
            repeatPenalty = 1.0f,
            contextSize = 2048,
            threads = 4
        )

        val result = provider.infer("Prompt", config)

        assertEquals("Lazy loaded response", result)
        // Verify model was loaded as part of lazy init
        verify(exactly = 1) { nativeBridge.loadModel("/data/models/test-model.gguf", 2048, 4) }
        verify(exactly = 1) {
            nativeBridge.infer("Prompt", 128, 0.5f, 0.95f, 50, 1.0f)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // unloadModel tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("unloadModel - calls native unload, resets state")
    fun unloadModel_callsNativeUnloadAndResetsState() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true

        // Load first
        provider.loadModel()
        // Then unload
        provider.unloadModel()

        verify(exactly = 1) { nativeBridge.unloadModel() }

        // After unload, loading again should call native again (state was reset)
        provider.loadModel()
        verify(exactly = 2) { nativeBridge.loadModel(any(), any(), any()) }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // cancelInference tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelInference - calls native cancelInference")
    fun cancelInference_callsNativeCancelInference() {
        provider.cancelInference()

        verify(exactly = 1) { nativeBridge.cancelInference() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // health tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("health - model loaded, native returns true → Healthy")
    fun health_modelLoadedAndNativeHealthy_returnsHealthy() = runTest {
        coEvery { modelRepository.verifyChecksum() } returns true
        every { nativeBridge.loadModel(any(), any(), any()) } returns true
        every { nativeBridge.health() } returns true

        provider.loadModel()
        val health = provider.health()

        assertInstanceOf(ProviderHealth.Healthy::class.java, health)
        val healthy = health as ProviderHealth.Healthy
        assertEquals("test-model", healthy.modelName)
    }

    @Test
    @DisplayName("health - model not loaded → Unavailable")
    fun health_modelNotLoaded_returnsUnavailable() = runTest {
        every { modelRepository.modelExists() } returns true

        val health = provider.health()

        assertInstanceOf(ProviderHealth.Unavailable::class.java, health)
        val unavailable = health as ProviderHealth.Unavailable
        assertTrue(unavailable.reason.contains("not loaded"))
    }

    @Test
    @DisplayName("health - model file doesn't exist → Unavailable")
    fun health_modelFileDoesNotExist_returnsUnavailable() = runTest {
        every { modelRepository.modelExists() } returns false

        val health = provider.health()

        assertInstanceOf(ProviderHealth.Unavailable::class.java, health)
        val unavailable = health as ProviderHealth.Unavailable
        assertTrue(unavailable.reason.contains("not found"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // isAvailable tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("isAvailable - returns modelRepository.modelExists()")
    fun isAvailable_delegatesToModelRepository() {
        every { modelRepository.modelExists() } returns true
        assertTrue(provider.isAvailable)

        every { modelRepository.modelExists() } returns false
        assertFalse(provider.isAvailable)
    }
}
