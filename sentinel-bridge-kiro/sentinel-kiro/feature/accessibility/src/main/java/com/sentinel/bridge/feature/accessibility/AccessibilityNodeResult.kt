package com.sentinel.bridge.feature.accessibility

/**
 * Represents the bounding rectangle of an accessibility node on screen.
 *
 * @property left Left edge coordinate in pixels.
 * @property top Top edge coordinate in pixels.
 * @property right Right edge coordinate in pixels.
 * @property bottom Bottom edge coordinate in pixels.
 */
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Immutable snapshot of an accessibility node's properties.
 *
 * This is the gateway's unit of communication — callers never interact with
 * platform `AccessibilityNodeInfo` directly.
 *
 * @property text The node's visible text content, if any.
 * @property contentDescription The node's content description for accessibility, if any.
 * @property className Fully-qualified class name of the underlying view (e.g. "android.widget.Button").
 * @property viewId The resource ID of the view (e.g. "com.miui.player:id/btn_play").
 * @property bounds Screen bounds of the node, or null if unavailable.
 * @property isClickable Whether the node supports click actions.
 * @property isScrollable Whether the node supports scroll actions.
 * @property childCount Number of direct child nodes.
 */
data class AccessibilityNodeResult(
    val text: String?,
    val contentDescription: String?,
    val className: String?,
    val viewId: String?,
    val bounds: NodeBounds?,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val childCount: Int
)
