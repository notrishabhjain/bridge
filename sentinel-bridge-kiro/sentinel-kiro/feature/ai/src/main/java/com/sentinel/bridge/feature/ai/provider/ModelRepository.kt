package com.sentinel.bridge.feature.ai.provider

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository responsible for model file management on device.
 *
 * Handles loading model configuration from `assets/model_config.json`, resolving
 * the expected filesystem path for the GGUF model file, verifying file existence,
 * and computing/comparing SHA-256 checksums to ensure model integrity.
 *
 * This class is the single source of truth for model location and validity checks
 * used by [LlamaCppProvider] before loading the native inference engine.
 *
 * @param context Application context used for asset access and external file directory resolution.
 */
@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    /**
     * Lazily loaded and cached model configuration parsed from `assets/model_config.json`.
     */
    private val cachedConfig: ModelConfig by lazy { parseConfig() }

    /**
     * Loads the model configuration from the bundled asset file.
     *
     * The configuration is parsed once and cached for the lifetime of the application
     * process. Subsequent calls return the cached instance without re-reading the asset.
     *
     * @return Parsed [ModelConfig] containing model name, URL, checksum, version, and inference parameters.
     * @throws org.json.JSONException if the JSON structure is malformed or missing required fields.
     */
    fun loadConfig(): ModelConfig = cachedConfig

    /**
     * Resolves the expected absolute filesystem path where the model file should reside.
     *
     * The path is constructed as `<externalFilesDir>/models/<name>.gguf` where `<name>`
     * comes from the model configuration.
     *
     * @return Absolute path string for the model file on device storage.
     */
    fun getModelPath(): String {
        val modelsDir = context.getExternalFilesDir("models")
        val config = loadConfig()
        return File(modelsDir, "${config.name}.gguf").absolutePath
    }

    /**
     * Checks whether the model file exists at the expected path.
     *
     * @return `true` if the model file exists on disk, `false` otherwise.
     */
    fun modelExists(): Boolean {
        return File(getModelPath()).exists()
    }

    /**
     * Verifies the integrity of the downloaded model file by comparing its SHA-256
     * checksum against the expected value from the configuration.
     *
     * The file read and hash computation are performed on [Dispatchers.IO] to avoid
     * blocking the calling coroutine context.
     *
     * @return `true` if the computed checksum matches the expected checksum, `false`
     *         if the file does not exist or the checksums do not match.
     */
    suspend fun verifyChecksum(): Boolean {
        val modelPath = getModelPath()
        val file = File(modelPath)
        if (!file.exists()) return false
        val computed = computeChecksum(modelPath)
        return computed.equals(loadConfig().checksum, ignoreCase = true)
    }

    /**
     * Computes the SHA-256 hex digest of the file at the given path.
     *
     * Reads the file in 8KB chunks on [Dispatchers.IO] to keep memory usage bounded
     * regardless of model file size.
     *
     * @param filePath Absolute path to the file to hash.
     * @return Lowercase hexadecimal string representation of the SHA-256 digest.
     * @throws java.io.FileNotFoundException if the file does not exist.
     * @throws java.io.IOException if reading the file fails.
     */
    suspend fun computeChecksum(filePath: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        FileInputStream(File(filePath)).use { fis ->
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Parses the model configuration JSON from the bundled assets.
     *
     * Uses the built-in Android [org.json.JSONObject] parser — no external JSON
     * library is required.
     *
     * @return Parsed [ModelConfig] instance.
     * @throws org.json.JSONException if the JSON is malformed or missing required keys.
     */
    private fun parseConfig(): ModelConfig {
        val jsonString = context.assets.open("model_config.json").bufferedReader().use { it.readText() }
        val json = JSONObject(jsonString)
        return ModelConfig(
            name = json.getString("name"),
            downloadUrl = json.getString("downloadUrl"),
            checksum = json.getString("checksum"),
            version = json.getString("version"),
            contextSize = json.getInt("contextSize"),
            threads = json.getInt("threads")
        )
    }
}
