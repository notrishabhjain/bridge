package com.sentinel.bridge.feature.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Production implementation of [AccessibilityGateway] backed by a live [AccessibilityService].
 *
 * This gateway queries the accessibility node tree on demand using polling with coroutine
 * suspension. All waiting is done via [withTimeoutOrNull] + [delay] — never `Thread.sleep()`.
 *
 * Polling interval is fixed at [POLL_INTERVAL_MS] (200ms), which balances responsiveness
 * against CPU usage on mid-range Xiaomi devices.
 *
 * @param service The live [AccessibilityService] instance provided by [SentinelAccessibilityService].
 */
class RealAccessibilityGateway(
    private val service: AccessibilityService
) : AccessibilityGateway {

    companion object {
        /** Polling interval in milliseconds for node appearance checks. */
        private const val POLL_INTERVAL_MS = 200L

        /** Duration of a tap gesture in milliseconds. */
        private const val TAP_DURATION_MS = 50L
    }

    // ──────────────────────────────────────────────────────────────────────────
    // App launch
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Launches an application by resolving its launch intent from the package manager.
     *
     * @param packageName The target application's package name.
     * @return `true` if the launch intent was found and started successfully.
     */
    override suspend fun launchApp(packageName: String): Boolean {
        val context = service.applicationContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Node finding (polling-based)
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Polls [rootInActiveWindow] for a node whose text matches [text].
     *
     * Uses [AccessibilityNodeInfo.findAccessibilityNodeInfosByText] which performs
     * a case-insensitive containment match internally.
     *
     * @param text The exact or partial text to match.
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The first matching node snapshot, or `null` if not found within the timeout.
     */
    override suspend fun findNodeByText(text: String, timeoutMs: Long): AccessibilityNodeResult? {
        return pollForNode(timeoutMs) { root ->
            root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
        }
    }

    /**
     * Polls [rootInActiveWindow] for a node whose content description matches [description].
     *
     * Traverses the entire node tree since the platform API does not provide a direct
     * content-description search method.
     *
     * @param description The exact or partial content description to match.
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The first matching node snapshot, or `null` if not found within the timeout.
     */
    override suspend fun findNodeByContentDescription(
        description: String,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        return pollForNode(timeoutMs) { root ->
            findNodeInTree(root) { node ->
                node.contentDescription?.toString()?.contains(description, ignoreCase = true) == true
            }
        }
    }

    /**
     * Polls [rootInActiveWindow] for a node whose class name matches [className].
     *
     * @param className Fully-qualified class name (e.g. "android.widget.TextView").
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The first matching node snapshot, or `null` if not found within the timeout.
     */
    override suspend fun findNodeByClassName(
        className: String,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        return pollForNode(timeoutMs) { root ->
            findNodeInTree(root) { node ->
                node.className?.toString() == className
            }
        }
    }

    /**
     * Polls [rootInActiveWindow] for a node with the given resource view ID.
     *
     * Uses [AccessibilityNodeInfo.findAccessibilityNodeInfosByViewId] for direct
     * view-ID lookup.
     *
     * @param viewId The resource ID string (e.g. "com.miui.player:id/btn_play").
     * @param timeoutMs Maximum time in milliseconds to wait for the node to appear.
     * @return The first matching node snapshot, or `null` if not found within the timeout.
     */
    override suspend fun findNodeByViewId(viewId: String, timeoutMs: Long): AccessibilityNodeResult? {
        return pollForNode(timeoutMs) { root ->
            root.findAccessibilityNodeInfosByViewId(viewId)?.firstOrNull()
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Actions
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Performs a click action on the node matching the given [node] snapshot.
     *
     * Re-locates the live [AccessibilityNodeInfo] by matching its [viewId], [text],
     * or [contentDescription] from the snapshot, then performs [AccessibilityNodeInfo.ACTION_CLICK].
     * If the located node is not clickable, traverses up the parent chain to find
     * the nearest clickable ancestor.
     *
     * @param node The target node snapshot to click.
     * @return `true` if the click was successfully performed.
     */
    override suspend fun clickNode(node: AccessibilityNodeResult): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val liveNode = reconstructNode(root, node) ?: return false
        val clickableNode = findClickableNode(liveNode) ?: return false
        return clickableNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /**
     * Scrolls forward in the first scrollable container found in the active window.
     *
     * @return `true` if the scroll action was performed.
     */
    override suspend fun scrollForward(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val scrollable = findNodeInTree(root) { it.isScrollable } ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * Scrolls backward in the first scrollable container found in the active window.
     *
     * @return `true` if the scroll action was performed.
     */
    override suspend fun scrollBackward(): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val scrollable = findNodeInTree(root) { it.isScrollable } ?: return false
        return scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    /**
     * Returns the direct children of the given [node] by reconstructing the live node
     * and iterating its children.
     *
     * @param node The parent node snapshot.
     * @return List of child node snapshots (empty if no children or node not found).
     */
    override suspend fun getNodeChildren(node: AccessibilityNodeResult): List<AccessibilityNodeResult> {
        val root = service.rootInActiveWindow ?: return emptyList()
        val liveNode = reconstructNode(root, node) ?: return emptyList()
        val children = mutableListOf<AccessibilityNodeResult>()
        for (i in 0 until liveNode.childCount) {
            val child = liveNode.getChild(i)
            if (child != null) {
                children.add(child.toResult())
            }
        }
        return children
    }

    /**
     * Returns the root node of the current active window as a snapshot.
     *
     * @return The root node snapshot, or `null` if the service is not connected.
     */
    override suspend fun getRootNode(): AccessibilityNodeResult? {
        return service.rootInActiveWindow?.toResult()
    }

    /**
     * Finds all nodes in the tree whose class name matches [className].
     *
     * @param className Fully-qualified class name to match.
     * @return All matching node snapshots in document order.
     */
    override suspend fun findAllNodesByClassName(className: String): List<AccessibilityNodeResult> {
        val root = service.rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<AccessibilityNodeResult>()
        collectNodesInTree(root, results) { node ->
            node.className?.toString() == className
        }
        return results
    }

    /**
     * Performs a global accessibility action (e.g. BACK, HOME, RECENTS).
     *
     * @param action The global action constant (from [AccessibilityService.GLOBAL_ACTION_BACK], etc.).
     * @return `true` if the action was performed.
     */
    override suspend fun performGlobalAction(action: Int): Boolean {
        return service.performGlobalAction(action)
    }

    /**
     * Dispatches a tap gesture at exact screen coordinates using the [GestureDescription] API.
     *
     * This is a last-resort fallback — prefer node-based interactions whenever possible.
     * Requires API 24+ (guaranteed by minSdk 26).
     *
     * @param x Horizontal coordinate in pixels.
     * @param y Vertical coordinate in pixels.
     * @return `true` if the tap gesture was dispatched successfully.
     */
    override suspend fun tapCoordinate(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()

        return suspendCancellableCoroutine { continuation ->
            val callback = object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(true)
                    }
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (continuation.isActive) {
                        continuation.resume(false)
                    }
                }
            }

            val dispatched = service.dispatchGesture(gesture, callback, null)
            if (!dispatched) {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        }
    }

    /**
     * Checks whether the accessibility service is currently connected by verifying
     * that [rootInActiveWindow] is accessible.
     *
     * @return `true` if the service is connected and the node tree is available.
     */
    override fun isServiceConnected(): Boolean {
        return try {
            service.rootInActiveWindow != null
        } catch (_: Exception) {
            false
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Polls the active window's root node at [POLL_INTERVAL_MS] intervals until
     * [finder] returns a non-null [AccessibilityNodeInfo], or [timeoutMs] elapses.
     *
     * @param timeoutMs Maximum wait time in milliseconds.
     * @param finder Lambda that receives the root node and attempts to locate the target.
     * @return The found node converted to [AccessibilityNodeResult], or `null` on timeout.
     */
    private suspend fun pollForNode(
        timeoutMs: Long,
        finder: (AccessibilityNodeInfo) -> AccessibilityNodeInfo?
    ): AccessibilityNodeResult? {
        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val root = service.rootInActiveWindow
                if (root != null) {
                    val found = finder(root)
                    if (found != null) {
                        return@withTimeoutOrNull found.toResult()
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    /**
     * Traverses the node tree depth-first and returns the first node matching [predicate].
     *
     * @param root The root node to start traversal from.
     * @param predicate Condition to match against each node.
     * @return The first matching [AccessibilityNodeInfo], or `null` if none found.
     */
    private fun findNodeInTree(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeInTree(child, predicate)
            if (found != null) return found
        }
        return null
    }

    /**
     * Traverses the node tree depth-first, collecting all nodes matching [predicate]
     * into [results] in document order.
     *
     * @param root The root node to start traversal from.
     * @param results Mutable list to collect matching node snapshots.
     * @param predicate Condition to match against each node.
     */
    private fun collectNodesInTree(
        root: AccessibilityNodeInfo,
        results: MutableList<AccessibilityNodeResult>,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ) {
        if (predicate(root)) {
            results.add(root.toResult())
        }
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            collectNodesInTree(child, results, predicate)
        }
    }

    /**
     * Reconstructs a live [AccessibilityNodeInfo] from an [AccessibilityNodeResult] snapshot.
     *
     * Matches by viewId first (most reliable), then text, then content description.
     * Returns the first node that matches any of the available identifiers.
     *
     * @param root The root node of the current active window.
     * @param snapshot The node snapshot to re-locate.
     * @return The live node, or `null` if it can no longer be found in the tree.
     */
    private fun reconstructNode(
        root: AccessibilityNodeInfo,
        snapshot: AccessibilityNodeResult
    ): AccessibilityNodeInfo? {
        // Try viewId first — most specific
        snapshot.viewId?.let { viewId ->
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) return nodes.first()
        }

        // Try text match
        snapshot.text?.let { text ->
            val nodes = root.findAccessibilityNodeInfosByText(text)
            if (!nodes.isNullOrEmpty()) return nodes.first()
        }

        // Fall back to content description traversal
        snapshot.contentDescription?.let { desc ->
            val found = findNodeInTree(root) { node ->
                node.contentDescription?.toString() == desc
            }
            if (found != null) return found
        }

        return null
    }

    /**
     * Finds the nearest clickable node in the ancestor chain starting from [node].
     *
     * If [node] itself is clickable, returns it directly. Otherwise, traverses up
     * through parents until a clickable ancestor is found.
     *
     * @param node The starting node.
     * @return The clickable node, or `null` if none found in the ancestor chain.
     */
    private fun findClickableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) return current
            current = current.parent
        }
        return null
    }
}

// ──────────────────────────────────────────────────────────────────────────────
// Extension
// ──────────────────────────────────────────────────────────────────────────────

/**
 * Converts a platform [AccessibilityNodeInfo] to an immutable [AccessibilityNodeResult] snapshot.
 *
 * Extracts all relevant properties (text, content description, class name, view ID,
 * bounds, clickability, scrollability, and child count) into a data class that can
 * be safely held across suspension points without risking stale node references.
 *
 * @return An [AccessibilityNodeResult] snapshot of this node's current state.
 */
internal fun AccessibilityNodeInfo.toResult(): AccessibilityNodeResult {
    val rect = Rect()
    getBoundsInScreen(rect)
    return AccessibilityNodeResult(
        text = text?.toString(),
        contentDescription = contentDescription?.toString(),
        className = className?.toString(),
        viewId = viewIdResourceName,
        bounds = NodeBounds(
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom
        ),
        isClickable = isClickable,
        isScrollable = isScrollable,
        childCount = childCount
    )
}
