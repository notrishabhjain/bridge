package com.sentinel.bridge.feature.accessibility.strategies

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves the appropriate [RecorderAutomationStrategy] for the current device.
 *
 * Reads the device's HyperOS version via `getprop ro.mi.os.version.name` and selects the
 * matching strategy implementation. Currently only Xiaomi HyperOS 2.x is supported; all
 * other device configurations result in an [UnsupportedDeviceException].
 *
 * Detection behaviour:
 * - If `getprop` returns a value starting with "HyperOS 2" (case-insensitive), the
 *   [HyperOS2RecorderStrategy] is returned.
 * - If `getprop` returns null, an empty string, or any other value, an
 *   [UnsupportedDeviceException] is thrown.
 * - If the process fails for any reason (SecurityException, IOException, etc.), the OS
 *   version is treated as null and the device is considered unsupported.
 *
 * The [getHyperOsVersion] method is marked `internal` to allow unit tests to verify
 * detection logic without requiring an actual Android device.
 *
 * @property hyperOS2Strategy The Hilt-injected strategy instance for HyperOS 2.x devices.
 */
@Singleton
class StrategyResolver @Inject constructor(
    private val hyperOS2Strategy: HyperOS2RecorderStrategy
) {

    /**
     * Resolves the [RecorderAutomationStrategy] for the current device.
     *
     * @return [HyperOS2RecorderStrategy] if the device runs Xiaomi HyperOS 2.x.
     * @throws UnsupportedDeviceException if the device is not running a supported OS version.
     */
    fun resolve(): RecorderAutomationStrategy {
        val osVersion = getHyperOsVersion()
        if (osVersion != null && osVersion.startsWith("HyperOS 2", ignoreCase = true)) {
            return hyperOS2Strategy
        }
        throw UnsupportedDeviceException(
            "Unsupported device: expected Xiaomi HyperOS 2.x, found: ${osVersion ?: "unknown"}"
        )
    }

    /**
     * Reads the HyperOS version string from the system property `ro.mi.os.version.name`.
     *
     * Uses [ProcessBuilder] to execute `getprop ro.mi.os.version.name` and reads the first
     * line of stdout. Returns `null` if the process fails, produces no output, or throws
     * any exception.
     *
     * @return The trimmed version string (e.g., "HyperOS 2.0.1"), or `null` on failure.
     */
    internal fun getHyperOsVersion(): String? {
        return try {
            ProcessBuilder("getprop", "ro.mi.os.version.name")
                .start()
                .inputStream
                .bufferedReader()
                .readLine()
                ?.trim()
        } catch (_: Exception) {
            null
        }
    }
}
