package com.sentinel.bridge.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * System accessibility service that provides node-tree access for UI automation.
 *
 * This service is the system entry point — Android instantiates it when the user
 * enables it in Settings. It holds a [RealAccessibilityGateway] reference and
 * supplies the live service instance so the gateway can query the node tree on demand.
 *
 * Events are **not** consumed reactively. The automation layer polls the node tree
 * via [AccessibilityGateway] methods; therefore [onAccessibilityEvent] is a no-op.
 *
 * Lifecycle:
 * - [onServiceConnected]: creates the gateway and exposes it via [companion object].
 * - [onUnbind]: clears the gateway reference so other components stop using it.
 */
class SentinelAccessibilityService : AccessibilityService() {

    /**
     * Static reference to the gateway, accessible by other components (e.g. command handlers).
     *
     * This is non-null only while the service is connected. Callers must null-check
     * before use and handle the disconnected case gracefully.
     */
    companion object {
        var gateway: RealAccessibilityGateway? = null
            private set
    }

    /**
     * Called by the system after the service is successfully bound.
     *
     * Creates the [RealAccessibilityGateway] backed by this service instance and
     * publishes it via the [companion object] for other components to consume.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        val realGateway = RealAccessibilityGateway(this)
        gateway = realGateway
    }

    /**
     * No-op. Events are not consumed reactively — the automation layer polls
     * the node tree via [AccessibilityGateway] query methods.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty — events are polled via node tree queries, not event-driven.
    }

    /**
     * No-op. No feedback to interrupt.
     */
    override fun onInterrupt() {
        // Intentionally empty.
    }

    /**
     * Clears the gateway reference when the service is unbound, preventing
     * stale access from other components.
     */
    override fun onUnbind(intent: Intent?): Boolean {
        gateway = null
        return super.onUnbind(intent)
    }
}
