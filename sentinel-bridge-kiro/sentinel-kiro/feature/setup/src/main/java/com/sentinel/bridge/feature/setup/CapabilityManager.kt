package com.sentinel.bridge.feature.setup

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.StatFs
import android.provider.Settings
import com.sentinel.bridge.core.data.datastore.AppSettingsRepository
import com.sentinel.bridge.core.data.db.dao.CapabilityProfileDao
import com.sentinel.bridge.core.data.db.entity.CapabilityProfileEntity
import com.sentinel.bridge.feature.accessibility.SentinelAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of comparing the current Recorder accessibility node tree against a stored
 * [CapabilityProfileEntity].
 *
 * Used by [CapabilityManager.validateCapabilityProfile] to determine whether the device's
 * Recorder UI still matches the previously recorded capability profile. A mismatch typically
 * indicates a system update changed the Recorder UI, requiring a new profile recording.
 */
sealed class ProfileMatchResult {

    /**
     * The current node tree matches the stored profile (≥80% of stored nodes found).
     */
    object Match : ProfileMatchResult()

    /**
     * The current node tree does not sufficiently match the stored profile.
     *
     * @property reason Human-readable explanation of the mismatch.
     * @property missingNodes Number of stored nodes not found in the current tree.
     * @property totalNodes Total number of nodes in the stored profile.
     */
    data class Mismatch(
        val reason: String,
        val missingNodes: Int,
        val totalNodes: Int
    ) : ProfileMatchResult()

    /**
     * No capability profile has been recorded yet. The setup wizard should be run first.
     */
    object NoStoredProfile : ProfileMatchResult()

    /**
     * The accessibility service is not connected, so node-tree comparison cannot be performed.
     */
    object ServiceNotConnected : ProfileMatchResult()
}

/**
 * Represents the availability state of a single device capability.
 */
enum class CapabilityState {
    /** The capability is available and meets requirements. */
    AVAILABLE,

    /** The capability is not available or does not meet requirements. */
    UNAVAILABLE,

    /** The capability state has not been checked yet. */
    UNKNOWN
}

/**
 * Comprehensive report of all capability checks performed during startup or on-demand.
 *
 * @property accessibilityEnabled Whether the Sentinel accessibility service is enabled.
 * @property notificationListenerEnabled Whether the Sentinel notification listener is enabled.
 * @property recorderInstalled Whether the Xiaomi Recorder app is installed on the device.
 * @property modelValid Whether the AI model file exists in the expected directory.
 * @property sufficientRam Whether available RAM meets the configured minimum threshold.
 * @property sufficientStorage Whether available storage meets the configured minimum threshold.
 * @property allPassed Whether all capability checks passed successfully.
 */
data class CapabilityReport(
    val accessibilityEnabled: Boolean,
    val notificationListenerEnabled: Boolean,
    val recorderInstalled: Boolean,
    val modelValid: Boolean,
    val sufficientRam: Boolean,
    val sufficientStorage: Boolean,
    val allPassed: Boolean
)

/**
 * Monitors device capabilities required for the Sentinel AI Bridge pipeline.
 *
 * Exposes each capability as a [StateFlow] for reactive observation by the UI and
 * orchestration layers. The [checkAllCapabilities] method performs a full pre-flight
 * check and returns a [CapabilityReport] summarizing the device readiness.
 *
 * Capabilities monitored:
 * - Accessibility service permission
 * - Notification listener permission
 * - Xiaomi Recorder app installed
 * - AI model file presence
 * - Available RAM (configurable threshold via [AppSettingsRepository])
 * - Available storage (configurable threshold via [AppSettingsRepository])
 *
 * Thresholds for RAM and storage are read from [AppSettingsRepository] and can be
 * updated at runtime through the settings UI.
 */
@Singleton
class CapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appSettingsRepository: AppSettingsRepository,
    private val capabilityProfileDao: CapabilityProfileDao
) {

    private val _accessibilityState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state of the accessibility service permission. */
    val accessibilityState: StateFlow<CapabilityState> = _accessibilityState.asStateFlow()

    private val _notificationListenerState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state of the notification listener permission. */
    val notificationListenerState: StateFlow<CapabilityState> = _notificationListenerState.asStateFlow()

    private val _recorderState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state indicating whether the Xiaomi Recorder app is installed. */
    val recorderState: StateFlow<CapabilityState> = _recorderState.asStateFlow()

    private val _modelState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state indicating whether the AI model file exists. */
    val modelState: StateFlow<CapabilityState> = _modelState.asStateFlow()

    private val _ramState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state indicating whether sufficient RAM is available. */
    val ramState: StateFlow<CapabilityState> = _ramState.asStateFlow()

    private val _storageState = MutableStateFlow(CapabilityState.UNKNOWN)

    /** Reactive state indicating whether sufficient storage is available. */
    val storageState: StateFlow<CapabilityState> = _storageState.asStateFlow()

    /**
     * Performs all capability checks and returns a comprehensive [CapabilityReport].
     *
     * Each individual [StateFlow] is updated with the result of its corresponding check.
     * The report's [CapabilityReport.allPassed] field is `true` only when every
     * capability is [CapabilityState.AVAILABLE].
     *
     * @return A [CapabilityReport] summarizing the current device readiness.
     */
    suspend fun checkAllCapabilities(): CapabilityReport {
        val accessibility = checkAccessibility()
        val notificationListener = checkNotificationListener()
        val recorder = checkRecorderInstalled()
        val model = checkModelValid()
        val ram = checkRam()
        val storage = checkStorage()

        _accessibilityState.value = if (accessibility) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE
        _notificationListenerState.value = if (notificationListener) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE
        _recorderState.value = if (recorder) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE
        _modelState.value = if (model) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE
        _ramState.value = if (ram) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE
        _storageState.value = if (storage) CapabilityState.AVAILABLE else CapabilityState.UNAVAILABLE

        val allPassed = accessibility && notificationListener && recorder && model && ram && storage

        return CapabilityReport(
            accessibilityEnabled = accessibility,
            notificationListenerEnabled = notificationListener,
            recorderInstalled = recorder,
            modelValid = model,
            sufficientRam = ram,
            sufficientStorage = storage,
            allPassed = allPassed
        )
    }

    /**
     * Checks whether the Sentinel accessibility service is enabled in system settings.
     *
     * Reads [Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] and checks if the
     * fully qualified component name of [SentinelAccessibilityService] is present.
     *
     * @return `true` if the accessibility service is enabled; `false` otherwise.
     */
    private fun checkAccessibility(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val expectedComponent = ComponentName(
            context.packageName,
            "$ACCESSIBILITY_SERVICE_CLASS"
        ).flattenToString()

        return enabledServices.split(SERVICES_SEPARATOR).any { componentString ->
            ComponentName.unflattenFromString(componentString)?.flattenToString() == expectedComponent
        }
    }

    /**
     * Checks whether the Sentinel notification listener service is enabled.
     *
     * Reads the enabled notification listeners from [Settings.Secure] and checks if
     * our [SentinelNotificationListener] component is listed.
     *
     * @return `true` if the notification listener is enabled; `false` otherwise.
     */
    private fun checkNotificationListener(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            ENABLED_NOTIFICATION_LISTENERS
        ) ?: return false

        val expectedComponent = ComponentName(
            context.packageName,
            "$NOTIFICATION_LISTENER_CLASS"
        ).flattenToString()

        return enabledListeners.split(SERVICES_SEPARATOR).any { componentString ->
            ComponentName.unflattenFromString(componentString)?.flattenToString() == expectedComponent
        }
    }

    /**
     * Checks whether the Xiaomi Recorder app is installed on the device.
     *
     * Reads the recorder package name from [AppSettingsRepository] and attempts
     * to resolve it via [android.content.pm.PackageManager].
     *
     * @return `true` if the recorder package is installed; `false` otherwise.
     */
    @Suppress("DEPRECATION")
    private suspend fun checkRecorderInstalled(): Boolean {
        val recorderPackage = appSettingsRepository.recorderPackage.first()
        return try {
            context.packageManager.getPackageInfo(recorderPackage, 0)
            true
        } catch (_: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Checks whether the AI model file exists in the expected directory.
     *
     * Looks for any file in [Context.getExternalFilesDir] under the "models" subdirectory.
     * Full checksum verification is deferred to Tasks 42-43.
     *
     * @return `true` if at least one model file exists; `false` otherwise.
     */
    private fun checkModelValid(): Boolean {
        val modelsDir = context.getExternalFilesDir(MODELS_DIRECTORY)
        if (modelsDir == null || !modelsDir.exists()) return false
        val modelFiles = modelsDir.listFiles()
        return modelFiles != null && modelFiles.isNotEmpty()
    }

    /**
     * Checks whether available RAM meets the configured minimum threshold.
     *
     * Uses [ActivityManager.getMemoryInfo] to read available memory and compares
     * against the configurable threshold from [AppSettingsRepository.minFreeRamMb].
     *
     * @return `true` if available RAM exceeds the threshold; `false` otherwise.
     */
    private suspend fun checkRam(): Boolean {
        val minFreeRamMb = appSettingsRepository.minFreeRamMb.first()
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val availableMb = memoryInfo.availMem / BYTES_PER_MB
        return availableMb >= minFreeRamMb
    }

    /**
     * Checks whether available storage meets the configured minimum threshold.
     *
     * Uses [StatFs] on the external files directory to determine available bytes
     * and compares against the configurable threshold from [AppSettingsRepository.minFreeStorageMb].
     *
     * @return `true` if available storage exceeds the threshold; `false` otherwise.
     */
    private suspend fun checkStorage(): Boolean {
        val minFreeStorageMb = appSettingsRepository.minFreeStorageMb.first()
        val externalDir = context.getExternalFilesDir(null) ?: return false
        val statFs = StatFs(externalDir.absolutePath)
        val availableBytes = statFs.availableBlocksLong * statFs.blockSizeLong
        val availableMb = availableBytes / BYTES_PER_MB
        return availableMb >= minFreeStorageMb
    }

    /**
     * Records the current device capability profile by launching the Xiaomi Recorder app,
     * probing its UI for accessibility nodes, and persisting the result to Room.
     *
     * This method performs the following steps:
     * 1. Obtains the live [AccessibilityGateway] from [SentinelAccessibilityService].
     * 2. Launches the Recorder app using the configured package name.
     * 3. Waits for the app to load, then probes the UI tree for all `TextView` nodes.
     * 4. Serializes the discovered nodes as a JSON array string.
     * 5. Reads the Recorder version and HyperOS version from the system.
     * 6. Inserts a [CapabilityProfileEntity] into Room and returns the persisted entity.
     *
     * Returns `null` if the accessibility service is not connected, the Recorder
     * cannot be launched, or the root node is unavailable after launch.
     *
     * @return The persisted [CapabilityProfileEntity], or `null` if recording failed.
     */
    suspend fun recordCapabilityProfile(): CapabilityProfileEntity? {
        val gateway = SentinelAccessibilityService.gateway ?: return null

        val recorderPackage = appSettingsRepository.recorderPackage.first()

        // Launch the Recorder app
        val launched = gateway.launchApp(recorderPackage)
        if (!launched) return null

        // Wait for the app to fully load
        delay(APP_LOAD_DELAY_MS)

        // Probe for expected nodes
        val rootNode = gateway.getRootNode() ?: return null
        val allTextViews = gateway.findAllNodesByClassName("android.widget.TextView")

        // Build node descriptions as a JSON array
        val nodeDescriptions = allTextViews.map { node ->
            buildString {
                append("{\"text\":\"")
                append(escapeJson(node.text ?: ""))
                append("\",\"contentDescription\":\"")
                append(escapeJson(node.contentDescription ?: ""))
                append("\",\"className\":\"")
                append(escapeJson(node.className ?: ""))
                append("\"}")
            }
        }
        val nodesJson = "[${nodeDescriptions.joinToString(",")}]"

        // Get Recorder version
        val recorderVersion = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(recorderPackage, 0).versionName ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        // Get HyperOS version via system property
        val hyperOsVersion = try {
            ProcessBuilder("getprop", "ro.mi.os.version.name")
                .start().inputStream.bufferedReader().readLine()?.trim() ?: "unknown"
        } catch (_: Exception) {
            "unknown"
        }

        // Build and persist the profile entity
        val profile = CapabilityProfileEntity(
            id = 0, // auto-generated by Room
            version = 1,
            recorderPackage = recorderPackage,
            recorderVersion = recorderVersion,
            hyperOsVersion = hyperOsVersion,
            availableNodes = nodesJson,
            recordedAt = Instant.now().toEpochMilli()
        )

        capabilityProfileDao.insert(profile)
        return capabilityProfileDao.getLatest()
    }

    /**
     * Validates the current device state against the stored capability profile.
     *
     * Performs the following validation sequence:
     * 1. Loads the latest [CapabilityProfileEntity] from Room.
     * 2. If no profile exists, returns [ProfileMatchResult.NoStoredProfile].
     * 3. Checks that the accessibility service gateway is connected.
     * 4. If not connected, returns [ProfileMatchResult.ServiceNotConnected].
     * 5. Launches the Recorder app and waits for it to load.
     * 6. Probes the current UI tree for all `TextView` nodes.
     * 7. Parses the stored profile's [CapabilityProfileEntity.availableNodes] JSON.
     * 8. Compares stored nodes against the current tree by text content.
     * 9. If ≥80% of stored nodes are found → [ProfileMatchResult.Match].
     * 10. Otherwise → [ProfileMatchResult.Mismatch], and broadcasts `CAPABILITY_MISMATCH`.
     *
     * The 80% threshold accounts for minor UI text changes (e.g., date/time labels)
     * while still detecting significant structural changes from system updates.
     *
     * @return A [ProfileMatchResult] describing the validation outcome.
     */
    suspend fun validateCapabilityProfile(): ProfileMatchResult {
        // Step 1: Load the latest stored profile
        val storedProfile = capabilityProfileDao.getLatest()
            ?: return ProfileMatchResult.NoStoredProfile

        // Step 2: Check accessibility gateway availability
        val gateway = SentinelAccessibilityService.gateway
            ?: return ProfileMatchResult.ServiceNotConnected

        // Step 3: Launch the Recorder app
        val recorderPackage = appSettingsRepository.recorderPackage.first()
        val launched = gateway.launchApp(recorderPackage)
        if (!launched) {
            return ProfileMatchResult.Mismatch(
                reason = "Failed to launch Recorder app ($recorderPackage)",
                missingNodes = 0,
                totalNodes = 0
            )
        }

        // Step 4: Wait for the app to fully load
        delay(APP_LOAD_DELAY_MS)

        // Step 5: Probe current UI nodes
        val currentNodes = gateway.findAllNodesByClassName("android.widget.TextView")

        // Step 6: Parse stored nodes from the profile JSON
        val storedNodeTexts = parseStoredNodeTexts(storedProfile.availableNodes)
        val totalNodes = storedNodeTexts.size

        if (totalNodes == 0) {
            // Empty stored profile is considered a match (nothing to compare)
            return ProfileMatchResult.Match
        }

        // Step 7: Build set of current node texts for efficient lookup
        val currentNodeTexts = currentNodes.mapNotNull { it.text }.toSet()

        // Step 8: Count how many stored nodes are present in the current tree
        val matchedCount = storedNodeTexts.count { storedText ->
            currentNodeTexts.contains(storedText)
        }
        val missingNodes = totalNodes - matchedCount
        val matchRatio = matchedCount.toDouble() / totalNodes.toDouble()

        // Step 9: Determine result based on 80% threshold
        val result = if (matchRatio > MATCH_THRESHOLD) {
            ProfileMatchResult.Match
        } else {
            ProfileMatchResult.Mismatch(
                reason = "Node match ratio ${String.format("%.1f", matchRatio * 100)}% " +
                    "is below the ${(MATCH_THRESHOLD * 100).toInt()}% threshold. " +
                    "$missingNodes of $totalNodes stored nodes not found in current UI.",
                missingNodes = missingNodes,
                totalNodes = totalNodes
            )
        }

        // Step 10: Broadcast mismatch intent if validation failed
        if (result is ProfileMatchResult.Mismatch) {
            val intent = Intent(CAPABILITY_MISMATCH_ACTION)
            intent.putExtra("reason", result.reason)
            context.sendBroadcast(intent)
        }

        return result
    }

    /**
     * Parses the stored node texts from the [CapabilityProfileEntity.availableNodes] JSON.
     *
     * Expects a JSON array of objects with a `"text"` field. Non-blank text values are
     * collected for comparison.
     *
     * @param nodesJson The JSON string from the stored profile.
     * @return List of non-blank text values from stored nodes.
     */
    private fun parseStoredNodeTexts(nodesJson: String): List<String> {
        return try {
            val jsonArray = JSONArray(nodesJson)
            val texts = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                val nodeObj = jsonArray.getJSONObject(i)
                val text = nodeObj.optString("text", "")
                if (text.isNotBlank()) {
                    texts.add(text)
                }
            }
            texts
        } catch (_: Exception) {
            emptyList()
        }
    }

    companion object {
        /** Fully qualified class name for the Sentinel accessibility service. */
        private const val ACCESSIBILITY_SERVICE_CLASS =
            "com.sentinel.bridge.feature.accessibility.SentinelAccessibilityService"

        /** Fully qualified class name for the Sentinel notification listener. */
        private const val NOTIFICATION_LISTENER_CLASS =
            "com.sentinel.bridge.feature.notification.SentinelNotificationListener"

        /** Setting key for enabled notification listeners. */
        private const val ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners"

        /** Separator used in the enabled accessibility services and notification listeners settings. */
        private const val SERVICES_SEPARATOR = ":"

        /** Subdirectory name where AI model files are stored. */
        private const val MODELS_DIRECTORY = "models"

        /** Bytes per megabyte for memory/storage calculations. */
        private const val BYTES_PER_MB = 1_048_576L

        /** Delay in milliseconds to wait for the Recorder app to fully load after launch. */
        private const val APP_LOAD_DELAY_MS = 2000L

        /**
         * Minimum ratio of stored nodes that must be found in the current tree
         * for the profile to be considered a match.
         *
         * A threshold of 0.80 (80%) allows for minor UI variations (dynamic labels,
         * timestamps) while detecting significant structural changes.
         */
        private const val MATCH_THRESHOLD = 0.80

        /** Broadcast action sent when the current device state does not match the stored profile. */
        const val CAPABILITY_MISMATCH_ACTION = "com.sentinel.bridge.CAPABILITY_MISMATCH"
    }

    /**
     * Escapes special characters in a string for safe JSON embedding.
     *
     * Handles backslash, double quote, newline, carriage return, and tab.
     *
     * @param value The raw string to escape.
     * @return The JSON-safe escaped string.
     */
    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
