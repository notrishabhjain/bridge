package com.sentinel.bridge.feature.accessibility.strategies

/**
 * Thrown when the current device does not meet the required OS/manufacturer criteria
 * for Sentinel Bridge automation.
 *
 * This exception indicates that the device is not running a supported Xiaomi HyperOS version
 * (currently only HyperOS 2.x is supported). The pipeline must not proceed on unsupported
 * devices to avoid unpredictable UI automation behaviour.
 *
 * @param message Human-readable description including the detected OS version (or "unknown"
 *   if detection failed).
 */
class UnsupportedDeviceException(message: String) : RuntimeException(message)
