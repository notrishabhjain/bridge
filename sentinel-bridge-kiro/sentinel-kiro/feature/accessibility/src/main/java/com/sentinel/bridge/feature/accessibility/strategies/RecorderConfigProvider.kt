package com.sentinel.bridge.feature.accessibility.strategies

import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provides a [RecorderConfig] instance populated from persisted DataStore settings.
 *
 * Values that are stored in [AppSettingsRepository] are read reactively via their
 * [kotlinx.coroutines.flow.Flow] and collected into a snapshot. Values that have no
 * DataStore backing yet (e.g., [RecorderConfig.showTextButtonText],
 * [RecorderConfig.transcriptNodeClassName], fallback coordinates) retain their
 * compile-time defaults from [RecorderConfig].
 *
 * Usage:
 * ```kotlin
 * val config = recorderConfigProvider.loadConfig()
 * strategy.run(config)
 * ```
 *
 * @property appSettingsRepository DataStore-backed repository supplying persisted
 *     configuration values.
 */
@Singleton
class RecorderConfigProvider @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) {

    /**
     * Loads a [RecorderConfig] by reading current values from DataStore.
     *
     * This suspend function collects the latest emission from each relevant
     * [AppSettingsRepository] flow. Fields without a DataStore backing use the
     * [RecorderConfig] default values.
     *
     * @return a fully populated [RecorderConfig] reflecting the current persisted state.
     */
    suspend fun loadConfig(): RecorderConfig {
        val recorderPackage = appSettingsRepository.recorderPackage.first()
        val preferredLanguage = appSettingsRepository.preferredLanguage.first()
        val completionNotificationText = appSettingsRepository.completionNotificationText.first()
        val transcriptionTimeoutMs = appSettingsRepository.transcriptionTimeoutMs.first()

        return RecorderConfig(
            recorderPackage = recorderPackage,
            preferredLanguage = preferredLanguage,
            completionNotificationText = completionNotificationText,
            showTextButtonText = RecorderConfig().showTextButtonText,
            transcriptNodeClassName = RecorderConfig().transcriptNodeClassName,
            fallbackShowTextX = RecorderConfig().fallbackShowTextX,
            fallbackShowTextY = RecorderConfig().fallbackShowTextY,
            timeoutMs = transcriptionTimeoutMs
        )
    }
}
