package com.sentinel.bridge.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing application settings persisted in DataStore Preferences.
 *
 * Settings control pipeline behavior, device thresholds, and recorder integration
 * parameters. Each setting exposes a [Flow] for reactive observation and a suspend
 * setter for updates. Defaults are tuned for the MVP configuration on Xiaomi HyperOS 2
 * devices with Qwen3-4B GGUF model.
 *
 * This repository shares the same [DataStore] instance as [FeatureFlagsRepository].
 * Key namespaces are disjoint, so no conflicts occur.
 */
@Singleton
class AppSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * The target transcription language used by the Xiaomi Recorder.
     *
     * Default: `"Hindi"` — primary language for MVP use case.
     */
    val preferredLanguage: Flow<String> = dataStore.data.map { prefs ->
        prefs[PREFERRED_LANGUAGE] ?: DEFAULT_PREFERRED_LANGUAGE
    }

    /**
     * Duration in milliseconds before the LLM model is unloaded after idle.
     *
     * Default: `300000L` (5 minutes) — balances memory reclamation with reload cost.
     */
    val modelIdleTimeoutMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[MODEL_IDLE_TIMEOUT_MS] ?: DEFAULT_MODEL_IDLE_TIMEOUT_MS
    }

    /**
     * Package name of the Xiaomi Recorder application.
     *
     * Default: `"com.miui.voiceassist"` — the HyperOS 2 recorder package.
     */
    val recorderPackage: Flow<String> = dataStore.data.map { prefs ->
        prefs[RECORDER_PACKAGE] ?: DEFAULT_RECORDER_PACKAGE
    }

    /**
     * Text pattern to match in the notification indicating transcription is complete.
     *
     * Default: `"Finished transcribing"` — the HyperOS 2 notification text.
     */
    val completionNotificationText: Flow<String> = dataStore.data.map { prefs ->
        prefs[COMPLETION_NOTIFICATION_TEXT] ?: DEFAULT_COMPLETION_NOTIFICATION_TEXT
    }

    /**
     * Timeout in milliseconds waiting for the transcription to complete.
     *
     * Default: `180000L` (3 minutes) — maximum wait for Xiaomi HyperAI transcription.
     */
    val transcriptionTimeoutMs: Flow<Long> = dataStore.data.map { prefs ->
        prefs[TRANSCRIPTION_TIMEOUT_MS] ?: DEFAULT_TRANSCRIPTION_TIMEOUT_MS
    }

    /**
     * Minimum free RAM in megabytes required before loading the model.
     *
     * Default: `2048` (2 GB) — Qwen3-4B requires substantial memory headroom.
     */
    val minFreeRamMb: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MIN_FREE_RAM_MB] ?: DEFAULT_MIN_FREE_RAM_MB
    }

    /**
     * Minimum free storage in megabytes required for pipeline operations.
     *
     * Default: `500` (500 MB) — covers model file and transcript storage.
     */
    val minFreeStorageMb: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MIN_FREE_STORAGE_MB] ?: DEFAULT_MIN_FREE_STORAGE_MB
    }

    /**
     * Maximum number of retries allowed per pipeline stage.
     *
     * Default: `3` — exponential backoff applies between retries.
     */
    val maxPipelineRetries: Flow<Int> = dataStore.data.map { prefs ->
        prefs[MAX_PIPELINE_RETRIES] ?: DEFAULT_MAX_PIPELINE_RETRIES
    }

    /**
     * Current pipeline version identifier.
     *
     * Default: `"1.0.0"` — included in pipeline output for traceability.
     */
    val pipelineVersion: Flow<String> = dataStore.data.map { prefs ->
        prefs[PIPELINE_VERSION] ?: DEFAULT_PIPELINE_VERSION
    }

    /**
     * Sets the preferred transcription language.
     *
     * @param language the language name (e.g., "Hindi", "English").
     */
    suspend fun setPreferredLanguage(language: String) {
        dataStore.edit { prefs -> prefs[PREFERRED_LANGUAGE] = language }
    }

    /**
     * Sets the model idle timeout duration.
     *
     * @param timeoutMs duration in milliseconds before unloading the idle model.
     */
    suspend fun setModelIdleTimeoutMs(timeoutMs: Long) {
        dataStore.edit { prefs -> prefs[MODEL_IDLE_TIMEOUT_MS] = timeoutMs }
    }

    /**
     * Sets the recorder application package name.
     *
     * @param packageName the fully qualified package name of the recorder app.
     */
    suspend fun setRecorderPackage(packageName: String) {
        dataStore.edit { prefs -> prefs[RECORDER_PACKAGE] = packageName }
    }

    /**
     * Sets the notification text pattern for transcription completion detection.
     *
     * @param text the text to match in the notification content.
     */
    suspend fun setCompletionNotificationText(text: String) {
        dataStore.edit { prefs -> prefs[COMPLETION_NOTIFICATION_TEXT] = text }
    }

    /**
     * Sets the transcription timeout duration.
     *
     * @param timeoutMs duration in milliseconds to wait for transcription.
     */
    suspend fun setTranscriptionTimeoutMs(timeoutMs: Long) {
        dataStore.edit { prefs -> prefs[TRANSCRIPTION_TIMEOUT_MS] = timeoutMs }
    }

    /**
     * Sets the minimum free RAM threshold.
     *
     * @param ramMb minimum free RAM in megabytes.
     */
    suspend fun setMinFreeRamMb(ramMb: Int) {
        dataStore.edit { prefs -> prefs[MIN_FREE_RAM_MB] = ramMb }
    }

    /**
     * Sets the minimum free storage threshold.
     *
     * @param storageMb minimum free storage in megabytes.
     */
    suspend fun setMinFreeStorageMb(storageMb: Int) {
        dataStore.edit { prefs -> prefs[MIN_FREE_STORAGE_MB] = storageMb }
    }

    /**
     * Sets the maximum pipeline retries per stage.
     *
     * @param retries maximum number of retry attempts.
     */
    suspend fun setMaxPipelineRetries(retries: Int) {
        dataStore.edit { prefs -> prefs[MAX_PIPELINE_RETRIES] = retries }
    }

    /**
     * Sets the pipeline version identifier.
     *
     * @param version the version string (e.g., "1.0.0").
     */
    suspend fun setPipelineVersion(version: String) {
        dataStore.edit { prefs -> prefs[PIPELINE_VERSION] = version }
    }

    companion object {
        /** Preference key for the target transcription language. */
        val PREFERRED_LANGUAGE = stringPreferencesKey("app_preferred_language")

        /** Preference key for the model idle timeout in milliseconds. */
        val MODEL_IDLE_TIMEOUT_MS = longPreferencesKey("app_model_idle_timeout_ms")

        /** Preference key for the Xiaomi Recorder package name. */
        val RECORDER_PACKAGE = stringPreferencesKey("app_recorder_package")

        /** Preference key for the transcription completion notification text. */
        val COMPLETION_NOTIFICATION_TEXT = stringPreferencesKey("app_completion_notification_text")

        /** Preference key for the transcription timeout in milliseconds. */
        val TRANSCRIPTION_TIMEOUT_MS = longPreferencesKey("app_transcription_timeout_ms")

        /** Preference key for minimum free RAM in megabytes. */
        val MIN_FREE_RAM_MB = intPreferencesKey("app_min_free_ram_mb")

        /** Preference key for minimum free storage in megabytes. */
        val MIN_FREE_STORAGE_MB = intPreferencesKey("app_min_free_storage_mb")

        /** Preference key for maximum pipeline retries per stage. */
        val MAX_PIPELINE_RETRIES = intPreferencesKey("app_max_pipeline_retries")

        /** Preference key for the current pipeline version identifier. */
        val PIPELINE_VERSION = stringPreferencesKey("app_pipeline_version")

        /** Default: Hindi — primary transcription language for MVP. */
        const val DEFAULT_PREFERRED_LANGUAGE = "Hindi"

        /** Default: 300000ms (5 minutes) — model idle timeout before unload. */
        const val DEFAULT_MODEL_IDLE_TIMEOUT_MS = 300_000L

        /** Default: Xiaomi HyperOS 2 recorder package. */
        const val DEFAULT_RECORDER_PACKAGE = "com.miui.voiceassist"

        /** Default: notification text indicating transcription completion. */
        const val DEFAULT_COMPLETION_NOTIFICATION_TEXT = "Finished transcribing"

        /** Default: 180000ms (3 minutes) — maximum transcription wait time. */
        const val DEFAULT_TRANSCRIPTION_TIMEOUT_MS = 180_000L

        /** Default: 2048 MB (2 GB) — minimum free RAM for model loading. */
        const val DEFAULT_MIN_FREE_RAM_MB = 2048

        /**
         * Default: 3000 MB — must cover the model download, not just pipeline scratch
         * space. The Q4_K_M GGUF is roughly 2.5 GB, so the previous 500 MB threshold
         * let the Device Check pass on a device that would then run out of room
         * partway through the download.
         */
        const val DEFAULT_MIN_FREE_STORAGE_MB = 3000

        /** Default: 3 — maximum retries per pipeline stage. */
        const val DEFAULT_MAX_PIPELINE_RETRIES = 3

        /** Default: "1.0.0" — initial pipeline version. */
        const val DEFAULT_PIPELINE_VERSION = "1.0.0"
    }
}
