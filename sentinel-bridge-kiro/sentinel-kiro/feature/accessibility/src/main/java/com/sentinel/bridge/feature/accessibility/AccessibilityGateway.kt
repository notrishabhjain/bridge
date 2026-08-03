package com.sentinel.bridge.feature.accessibility

/**
 * Abstraction over the Android AccessibilityService node-tree APIs.
 *
 * Production implementation (`RealAccessibilityGateway`) delegates to the live
 * `AccessibilityService`. Test implementation (`FakeAccessibilityGateway`) provides
 * controllable stubs for deterministic unit tests.
 *
 * Node resolution follows the priority defined in the architecture doc:
 * text -> content description -> class hierarchy -> relative position -> coordinate (last resort).
 *
 * All waiting is done via coroutine suspension or Flow timeouts — never `Thread.sleep()`.
 */
interface AccessibilityGateway {

    /**
     * Launches an application by package name.
     *
     * @param packageName The target application's package name.
     * @return `true` if the app was successfully launched.
     */
    suspend fun launchApp(packageName: String): Boolean

    /**
     * Finds a node whose text matches the given value.
     *
     * @param text The exact or partial text to match.
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The matching node, or `null` if not found within the timeout.
     */
    suspend fun findNodeByText(text: String, timeoutMs: Long = 5000): AccessibilityNodeResult?

    /**
     * Finds a node whose content description matches the given value.
     *
     * @param description The exact or partial content description to match.
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The matching node, or `null` if not found within the timeout.
     */
    suspend fun findNodeByContentDescription(description: String, timeoutMs: Long = 5000): AccessibilityNodeResult?

    /**
     * Finds a node whose class name matches the given value.
     *
     * @param className Fully-qualified class name (e.g. "android.widget.TextView").
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The first matching node, or `null` if not found within the timeout.
     */
    suspend fun findNodeByClassName(className: String, timeoutMs: Long = 5000): AccessibilityNodeResult?

    /**
     * Finds a node by its resource view ID.
     *
     * @param viewId The resource ID string (e.g. "com.miui.player:id/btn_play").
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The matching node, or `null` if not found within the timeout.
     */
    suspend fun findNodeByViewId(viewId: String, timeoutMs: Long = 5000): AccessibilityNodeResult?

    /**
     * Performs a click action on the given node.
     *
     * @param node The target node to click.
     * @return `true` if the click was successfully performed.
     */
    suspend fun clickNode(node: AccessibilityNodeResult): Boolean

    /**
     * Scrolls forward in the currently focused scrollable container.
     *
     * @return `true` if the scroll action was performed.
     */
    suspend fun scrollForward(): Boolean

    /**
     * Scrolls backward in the currently focused scrollable container.
     *
     * @return `true` if the scroll action was performed.
     */
    suspend fun scrollBackward(): Boolean

    /**
     * Returns the direct children of the given node.
     *
     * @param node The parent node.
     * @return List of child nodes (empty if no children).
     */
    suspend fun getNodeChildren(node: AccessibilityNodeResult): List<AccessibilityNodeResult>

    /**
     * Returns the root node of the current active window.
     *
     * @return The root node, or `null` if the service is not connected.
     */
    suspend fun getRootNode(): AccessibilityNodeResult?

    /**
     * Finds all nodes whose class name matches the given value.
     *
     * @param className Fully-qualified class name to match.
     * @return All matching nodes in document order.
     */
    suspend fun findAllNodesByClassName(className: String): List<AccessibilityNodeResult>

    /**
     * Performs a global accessibility action (e.g. BACK, HOME, RECENTS).
     *
     * @param action The global action constant (from `AccessibilityService.GLOBAL_ACTION_*`).
     * @return `true` if the action was performed.
     */
    suspend fun performGlobalAction(action: Int): Boolean

    /**
     * Taps at exact screen coordinates. This is the last-resort fallback — prefer
     * node-based interactions whenever possible.
     *
     * @param x Horizontal coordinate in pixels.
     * @param y Vertical coordinate in pixels.
     * @return `true` if the tap gesture was dispatched.
     */
    suspend fun tapCoordinate(x: Float, y: Float): Boolean

    /**
     * Checks whether the accessibility service is currently connected and active.
     *
     * @return `true` if the service is connected and ready to process commands.
     */
    fun isServiceConnected(): Boolean
}
