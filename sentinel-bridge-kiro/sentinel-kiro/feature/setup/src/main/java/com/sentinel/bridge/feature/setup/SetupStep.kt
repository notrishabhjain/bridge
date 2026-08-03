package com.sentinel.bridge.feature.setup

/**
 * Sequential steps in the setup wizard flow.
 *
 * Each step represents a distinct capability check or configuration action
 * that must complete before the Sentinel AI Bridge pipeline can operate.
 * Steps are executed in ordinal order; the wizard advances only when the
 * current step completes successfully.
 *
 * @property title Human-readable title displayed in the wizard UI.
 * @property description Explanation of what this step does and why it's needed.
 */
enum class SetupStep(val title: String, val description: String) {

    /**
     * Verify that the Sentinel Accessibility Service is enabled in system settings.
     * Required for driving the Xiaomi Recorder UI.
     */
    ACCESSIBILITY(
        title = "Accessibility Service",
        description = "Enable the Sentinel accessibility service to automate the Recorder UI."
    ),

    /**
     * Verify that the Sentinel Notification Listener is enabled in system settings.
     * Required for detecting the "Finished transcribing" notification.
     */
    NOTIFICATION_LISTENER(
        title = "Notification Listener",
        description = "Enable the notification listener to detect transcription completion."
    ),

    /**
     * Verify that the target device runs Xiaomi HyperOS 2.x and that the
     * Recorder app is installed.
     */
    DEVICE_CHECK(
        title = "Device Check",
        description = "Verify the device runs Xiaomi HyperOS 2 and the Recorder app is installed."
    ),

    /**
     * Open the Recorder app and inspect its accessibility tree to build
     * a [CapabilityProfileEntity] for future validation.
     */
    RECORDER_INSPECTION(
        title = "Recorder Inspection",
        description = "Open the Recorder to record its UI structure for future validation."
    ),

    /**
     * Download the AI model (GGUF) from the configured URL using DownloadManager.
     * This is the only step that uses network, behind explicit user action.
     */
    MODEL_DOWNLOAD(
        title = "Model Download",
        description = "Download the AI model file from GitHub Releases."
    ),

    /**
     * Verify the SHA-256 checksum of the downloaded model against the expected
     * value from `assets/model_config.json`.
     */
    CHECKSUM_VERIFY(
        title = "Checksum Verification",
        description = "Verify the downloaded model's integrity via SHA-256 checksum."
    ),

    /**
     * Terminal state indicating all setup steps passed successfully.
     * The wizard saves the completion flag and capability profile, then finishes.
     */
    COMPLETE(
        title = "Setup Complete",
        description = "All checks passed. Sentinel AI Bridge is ready to operate."
    )
}

/**
 * Status of an individual setup step.
 */
enum class StepStatus {
    /** Step has not started yet. */
    PENDING,

    /** Step is currently executing. */
    IN_PROGRESS,

    /** Step completed successfully. */
    COMPLETE,

    /** Step failed. The user may retry. */
    FAILED
}
