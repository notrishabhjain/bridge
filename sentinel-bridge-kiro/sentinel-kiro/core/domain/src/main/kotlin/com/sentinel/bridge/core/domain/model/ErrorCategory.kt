package com.sentinel.bridge.core.domain.model

/**
 * Categories for classifying pipeline errors.
 *
 * Every [SentinelError] is assigned exactly one category, enabling
 * downstream consumers (logger, retry policy, MacroDroid intents)
 * to route or filter errors without inspecting free-text messages.
 */
enum class ErrorCategory {

    /** Failures during UI automation of the Recorder app (node not found, gesture rejected). */
    UI_AUTOMATION,

    /** Failures related to obtaining or reading the transcription text. */
    TRANSCRIPTION,

    /** Failures in the Accessibility Service layer (permission revoked, service disconnected). */
    ACCESSIBILITY,

    /** Failures in notification listening (permission revoked, unexpected notification format). */
    NOTIFICATION,

    /** Failures when loading the on-device LLM model (file missing, checksum mismatch, OOM). */
    MODEL_LOADING,

    /** Failures during LLM inference (native crash, timeout, cancellation). */
    INFERENCE,

    /** Failures when validating or repairing the JSON output from the model. */
    JSON_VALIDATION,

    /** Failures in file or database storage operations. */
    STORAGE,

    /** Catch-all for system-level failures not covered by other categories. */
    SYSTEM
}
