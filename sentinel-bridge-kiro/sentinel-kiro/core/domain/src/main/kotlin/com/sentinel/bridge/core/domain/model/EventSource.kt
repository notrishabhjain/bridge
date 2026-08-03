package com.sentinel.bridge.core.domain.model

/**
 * Identifies the origin of a pipeline event.
 *
 * Each event source maps to a specific automation trigger and determines
 * which prompt template, rules, and actions apply during pipeline processing.
 */
enum class EventSource {
    /** Triggered after a phone call recording completes. */
    CALL,

    /** Triggered by an incoming notification matching configured patterns. */
    NOTIFICATION,

    /** Triggered manually by the user for ad-hoc recordings. */
    MANUAL
}
