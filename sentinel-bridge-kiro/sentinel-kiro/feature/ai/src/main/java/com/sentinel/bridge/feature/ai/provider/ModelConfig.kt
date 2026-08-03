package com.sentinel.bridge.feature.ai.provider

/**
 * Configuration for the on-device GGUF model used by the AI inference layer.
 *
 * Loaded from `assets/model_config.json` by [ModelRepository]. All fields are
 * required and validated at parse time.
 *
 * @param name Model filename (without path) used to derive the storage path.
 * @param downloadUrl URL from which the model can be downloaded during initial setup.
 * @param checksum Expected SHA-256 hex string for integrity verification after download.
 * @param version Semantic version of the model configuration.
 * @param contextSize Maximum context window size in tokens passed to the native layer.
 * @param threads Number of CPU threads allocated for inference computation.
 */
data class ModelConfig(
    val name: String,
    val downloadUrl: String,
    val checksum: String,
    val version: String,
    val contextSize: Int,
    val threads: Int
)
