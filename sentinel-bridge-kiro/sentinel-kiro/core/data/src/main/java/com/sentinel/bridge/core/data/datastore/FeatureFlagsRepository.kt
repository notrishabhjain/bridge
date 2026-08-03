package com.sentinel.bridge.core.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing feature flags persisted in DataStore Preferences.
 *
 * Each flag exposes a [Flow] for reactive observation and a suspend setter
 * for toggling. Defaults are chosen for the MVP configuration:
 * - Calls pipeline enabled (primary trigger)
 * - Notification-based trigger disabled (future feature)
 * - Cloud sync disabled (no network in MVP)
 * - Debug logging disabled
 * - Auto-retry enabled (exponential backoff on transient failures)
 * - Setup wizard not yet completed
 */
@Singleton
class FeatureFlagsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {

    /**
     * Whether the call-based pipeline trigger is active.
     *
     * Default: `true` — calls are the primary event source in MVP.
     */
    val enableCalls: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_CALLS] ?: DEFAULT_ENABLE_CALLS
    }

    /**
     * Whether the notification-based pipeline trigger is active.
     *
     * Default: `false` — this is a future feature beyond MVP scope.
     */
    val enableNotifications: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_NOTIFICATIONS] ?: DEFAULT_ENABLE_NOTIFICATIONS
    }

    /**
     * Whether cloud synchronization is enabled.
     *
     * Default: `false` — MVP operates entirely on-device with no network.
     */
    val enableCloud: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_CLOUD] ?: DEFAULT_ENABLE_CLOUD
    }

    /**
     * Whether verbose debug logging is active.
     *
     * Default: `false` — enable only during development or troubleshooting.
     */
    val enableDebugLogs: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_DEBUG_LOGS] ?: DEFAULT_ENABLE_DEBUG_LOGS
    }

    /**
     * Whether automatic retry with exponential backoff is enabled for pipeline stages.
     *
     * Default: `true` — transient failures are retried before reporting failure.
     */
    val enableAutoRetry: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[ENABLE_AUTO_RETRY] ?: DEFAULT_ENABLE_AUTO_RETRY
    }

    /**
     * Whether the setup wizard has been completed successfully.
     *
     * Default: `false` — set to `true` after all setup steps pass.
     * When `false`, the app launches [SetupWizardActivity] instead of the main flow.
     */
    val setupComplete: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SETUP_COMPLETE] ?: DEFAULT_SETUP_COMPLETE
    }

    /**
     * Sets the call-based pipeline trigger flag.
     *
     * @param enabled `true` to enable calls as a pipeline trigger, `false` to disable.
     */
    suspend fun setEnableCalls(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_CALLS] = enabled }
    }

    /**
     * Sets the notification-based pipeline trigger flag.
     *
     * @param enabled `true` to enable notification triggers, `false` to disable.
     */
    suspend fun setEnableNotifications(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_NOTIFICATIONS] = enabled }
    }

    /**
     * Sets the cloud synchronization flag.
     *
     * @param enabled `true` to enable cloud sync, `false` to disable.
     */
    suspend fun setEnableCloud(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_CLOUD] = enabled }
    }

    /**
     * Sets the debug logging flag.
     *
     * @param enabled `true` to enable verbose debug logs, `false` to disable.
     */
    suspend fun setEnableDebugLogs(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_DEBUG_LOGS] = enabled }
    }

    /**
     * Sets the automatic retry flag.
     *
     * @param enabled `true` to enable auto-retry with exponential backoff, `false` to disable.
     */
    suspend fun setEnableAutoRetry(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[ENABLE_AUTO_RETRY] = enabled }
    }

    /**
     * Sets the setup completion flag.
     *
     * @param enabled `true` after the setup wizard completes successfully.
     */
    suspend fun setSetupComplete(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[SETUP_COMPLETE] = enabled }
    }

    companion object {
        /** Preference key for the calls pipeline trigger flag. */
        val ENABLE_CALLS = booleanPreferencesKey("enable_calls")

        /** Preference key for the notification pipeline trigger flag. */
        val ENABLE_NOTIFICATIONS = booleanPreferencesKey("enable_notifications")

        /** Preference key for the cloud synchronization flag. */
        val ENABLE_CLOUD = booleanPreferencesKey("enable_cloud")

        /** Preference key for the debug logging flag. */
        val ENABLE_DEBUG_LOGS = booleanPreferencesKey("enable_debug_logs")

        /** Preference key for the auto-retry flag. */
        val ENABLE_AUTO_RETRY = booleanPreferencesKey("enable_auto_retry")

        /** Preference key for the setup completion flag. */
        val SETUP_COMPLETE = booleanPreferencesKey("setup_complete")

        /** Default: calls trigger enabled (primary MVP pipeline trigger). */
        const val DEFAULT_ENABLE_CALLS = true

        /** Default: notification trigger disabled (future feature). */
        const val DEFAULT_ENABLE_NOTIFICATIONS = false

        /** Default: cloud sync disabled (no network in MVP). */
        const val DEFAULT_ENABLE_CLOUD = false

        /** Default: debug logging disabled. */
        const val DEFAULT_ENABLE_DEBUG_LOGS = false

        /** Default: auto-retry enabled. */
        const val DEFAULT_ENABLE_AUTO_RETRY = true

        /** Default: setup not yet completed. */
        const val DEFAULT_SETUP_COMPLETE = false
    }
}
