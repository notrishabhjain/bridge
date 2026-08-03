package com.sentinel.bridge.feature.ai.provider

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Path
import java.security.MessageDigest

@OptIn(ExperimentalCoroutinesApi::class)
class ModelRepositoryTest {

    private lateinit var context: Context
    private lateinit var assetManager: AssetManager
    private lateinit var repository: ModelRepository

    @TempDir
    lateinit var tempDir: Path

    private val configJson = """
        {
            "name": "test-model",
            "downloadUrl": "https://example.com/model.gguf",
            "checksum": "PLACEHOLDER",
            "version": "1.0.0",
            "contextSize": 2048,
            "threads": 4
        }
    """.trimIndent()

    private fun createRepositoryWithChecksum(checksum: String): ModelRepository {
        val json = configJson.replace("PLACEHOLDER", checksum)
        val inputStream = ByteArrayInputStream(json.toByteArray())

        val ctx: Context = mockk(relaxed = true)
        val assets: AssetManager = mockk()
        every { ctx.assets } returns assets
        every { assets.open("model_config.json") } returns inputStream
        every { ctx.getExternalFilesDir("models") } returns tempDir.toFile()

        return ModelRepository(ctx)
    }

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        assetManager = mockk()

        every { context.assets } returns assetManager
        every { assetManager.open("model_config.json") } returns
            ByteArrayInputStream(configJson.replace("PLACEHOLDER", "abc123").toByteArray())
        every { context.getExternalFilesDir("models") } returns tempDir.toFile()

        repository = ModelRepository(context)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyChecksum tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyChecksum - file exists with matching checksum → returns true")
    fun verifyChecksum_fileExistsWithMatchingChecksum_returnsTrue() = runTest {
        // Create a temp file with known content
        val content = "hello world".toByteArray()
        val expectedChecksum = computeSha256Hex(content)

        val repo = createRepositoryWithChecksum(expectedChecksum)

        // Create the model file at the expected path
        val modelFile = File(tempDir.toFile(), "test-model.gguf")
        modelFile.writeBytes(content)

        val result = repo.verifyChecksum()

        assertTrue(result)
    }

    @Test
    @DisplayName("verifyChecksum - file exists with mismatching checksum → returns false")
    fun verifyChecksum_fileExistsWithMismatchingChecksum_returnsFalse() = runTest {
        val repo = createRepositoryWithChecksum("0000000000000000000000000000000000000000000000000000000000000000")

        // Create the model file with different content
        val modelFile = File(tempDir.toFile(), "test-model.gguf")
        modelFile.writeBytes("some other content".toByteArray())

        val result = repo.verifyChecksum()

        assertFalse(result)
    }

    @Test
    @DisplayName("verifyChecksum - file does not exist → returns false")
    fun verifyChecksum_fileDoesNotExist_returnsFalse() = runTest {
        val repo = createRepositoryWithChecksum("abc123")

        // Don't create the model file — it shouldn't exist
        val result = repo.verifyChecksum()

        assertFalse(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // computeChecksum tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("computeChecksum - returns correct SHA-256 hex string for known content")
    fun computeChecksum_returnsCorrectSha256HexString() = runTest {
        val content = "The quick brown fox jumps over the lazy dog".toByteArray()
        val expectedHex = computeSha256Hex(content)

        // Create a temporary file with known content
        val tempFile = File(tempDir.toFile(), "checksum-test.bin")
        tempFile.writeBytes(content)

        val result = repository.computeChecksum(tempFile.absolutePath)

        assertEquals(expectedHex, result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // modelExists tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("modelExists - file exists → returns true")
    fun modelExists_fileExists_returnsTrue() {
        // Create the expected model file
        val modelFile = File(tempDir.toFile(), "test-model.gguf")
        modelFile.writeBytes("model data".toByteArray())

        val result = repository.modelExists()

        assertTrue(result)
    }

    @Test
    @DisplayName("modelExists - file does not exist → returns false")
    fun modelExists_fileDoesNotExist_returnsFalse() {
        // Don't create the model file
        val result = repository.modelExists()

        assertFalse(result)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // loadConfig tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("loadConfig - returns cached ModelConfig with correct values")
    fun loadConfig_returnsCachedModelConfig() {
        val config = repository.loadConfig()

        assertEquals("test-model", config.name)
        assertEquals("https://example.com/model.gguf", config.downloadUrl)
        assertEquals("abc123", config.checksum)
        assertEquals("1.0.0", config.version)
        assertEquals(2048, config.contextSize)
        assertEquals(4, config.threads)

        // Second call returns same instance (cached)
        val config2 = repository.loadConfig()
        assertTrue(config === config2)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getModelPath tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getModelPath - returns correct path")
    fun getModelPath_returnsCorrectPath() {
        val path = repository.getModelPath()

        val expected = File(tempDir.toFile(), "test-model.gguf").absolutePath
        assertEquals(expected, path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper
    // ─────────────────────────────────────────────────────────────────────────

    private fun computeSha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
