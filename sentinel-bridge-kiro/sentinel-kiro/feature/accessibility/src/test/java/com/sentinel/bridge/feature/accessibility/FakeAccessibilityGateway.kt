package com.sentinel.bridge.feature.accessibility

/**
 * Test fake for [AccessibilityGateway] that provides controllable stubs for all methods.
 *
 * Configure return values via public properties before exercising the system under test,
 * then assert on call counts to verify interactions.
 *
 * Example usage:
 * ```kotlin
 * val fake = FakeAccessibilityGateway()
 * fake.nodesByText["Show Text"] = AccessibilityNodeResult(
 *     text = "Show Text", contentDescription = null,
 *     className = "android.widget.Button", viewId = null,
 *     bounds = NodeBounds(0, 0, 200, 50),
 *     isClickable = true, isScrollable = false, childCount = 0
 * )
 * // ... exercise HyperOS2RecorderStrategy ...
 * assertEquals(1, fake.findNodeByTextCallCount)
 * ```
 */
class FakeAccessibilityGateway : AccessibilityGateway {

    // ----- Configurable return values -----

    /** Controls the return value of [isServiceConnected]. */
    var isConnected: Boolean = true

    /** Controls the return value of [launchApp]. */
    var launchAppResult: Boolean = true

    /** Controls the return value of [clickNode]. */
    var clickResult: Boolean = true

    /** Controls the return value of [scrollForward] and [scrollBackward]. */
    var scrollResult: Boolean = true

    /** Controls the return value of [tapCoordinate]. */
    var tapResult: Boolean = true

    /** Controls the return value of [performGlobalAction]. */
    var globalActionResult: Boolean = true

    /** Lookup table for [findNodeByText]. Key = text value. */
    val nodesByText: MutableMap<String, AccessibilityNodeResult> = mutableMapOf()

    /** Lookup table for [findNodeByContentDescription]. Key = description value. */
    val nodesByDescription: MutableMap<String, AccessibilityNodeResult> = mutableMapOf()

    /** Lookup table for [findNodeByClassName]. Key = class name, value = list of matching nodes. */
    val nodesByClassName: MutableMap<String, List<AccessibilityNodeResult>> = mutableMapOf()

    /** Lookup table for [findNodeByViewId]. Key = view resource ID. */
    val nodesByViewId: MutableMap<String, AccessibilityNodeResult> = mutableMapOf()

    /** Controls the return value of [getRootNode]. */
    var rootNode: AccessibilityNodeResult? = null

    /** Maps a parent node to its children for [getNodeChildren]. */
    val childrenMap: MutableMap<AccessibilityNodeResult, List<AccessibilityNodeResult>> = mutableMapOf()

    // ----- Call tracking -----

    /** Number of times [launchApp] was called. */
    var launchAppCallCount: Int = 0
        private set

    /** Number of times [findNodeByText] was called. */
    var findNodeByTextCallCount: Int = 0
        private set

    /** Number of times [findNodeByContentDescription] was called. */
    var findNodeByContentDescriptionCallCount: Int = 0
        private set

    /** Number of times [findNodeByClassName] was called. */
    var findNodeByClassNameCallCount: Int = 0
        private set

    /** Number of times [findNodeByViewId] was called. */
    var findNodeByViewIdCallCount: Int = 0
        private set

    /** Number of times [clickNode] was called. */
    var clickNodeCallCount: Int = 0
        private set

    /** Number of times [scrollForward] was called. */
    var scrollForwardCallCount: Int = 0
        private set

    /** Number of times [scrollBackward] was called. */
    var scrollBackwardCallCount: Int = 0
        private set

    /** Number of times [getNodeChildren] was called. */
    var getNodeChildrenCallCount: Int = 0
        private set

    /** Number of times [getRootNode] was called. */
    var getRootNodeCallCount: Int = 0
        private set

    /** Number of times [findAllNodesByClassName] was called. */
    var findAllNodesByClassNameCallCount: Int = 0
        private set

    /** Number of times [performGlobalAction] was called. */
    var performGlobalActionCallCount: Int = 0
        private set

    /** Number of times [tapCoordinate] was called. */
    var tapCoordinateCallCount: Int = 0
        private set

    /** Number of times [isServiceConnected] was called. */
    var isServiceConnectedCallCount: Int = 0
        private set

    // ----- Argument capture -----

    /** The last package name passed to [launchApp]. */
    var lastLaunchAppPackage: String? = null
        private set

    /** The last text passed to [findNodeByText]. */
    var lastFindNodeByTextQuery: String? = null
        private set

    /** The last description passed to [findNodeByContentDescription]. */
    var lastFindNodeByContentDescriptionQuery: String? = null
        private set

    /** The last class name passed to [findNodeByClassName]. */
    var lastFindNodeByClassNameQuery: String? = null
        private set

    /** The last view ID passed to [findNodeByViewId]. */
    var lastFindNodeByViewIdQuery: String? = null
        private set

    /** The last node passed to [clickNode]. */
    var lastClickedNode: AccessibilityNodeResult? = null
        private set

    /** The last global action passed to [performGlobalAction]. */
    var lastGlobalAction: Int? = null
        private set

    /** The last coordinates passed to [tapCoordinate]. */
    var lastTapCoordinate: Pair<Float, Float>? = null
        private set

    // ----- Interface implementation -----

    override suspend fun launchApp(packageName: String): Boolean {
        launchAppCallCount++
        lastLaunchAppPackage = packageName
        return launchAppResult
    }

    override suspend fun findNodeByText(text: String, timeoutMs: Long): AccessibilityNodeResult? {
        findNodeByTextCallCount++
        lastFindNodeByTextQuery = text
        return nodesByText[text]
    }

    override suspend fun findNodeByContentDescription(
        description: String,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        findNodeByContentDescriptionCallCount++
        lastFindNodeByContentDescriptionQuery = description
        return nodesByDescription[description]
    }

    override suspend fun findNodeByClassName(
        className: String,
        timeoutMs: Long
    ): AccessibilityNodeResult? {
        findNodeByClassNameCallCount++
        lastFindNodeByClassNameQuery = className
        return nodesByClassName[className]?.firstOrNull()
    }

    override suspend fun findNodeByViewId(viewId: String, timeoutMs: Long): AccessibilityNodeResult? {
        findNodeByViewIdCallCount++
        lastFindNodeByViewIdQuery = viewId
        return nodesByViewId[viewId]
    }

    override suspend fun clickNode(node: AccessibilityNodeResult): Boolean {
        clickNodeCallCount++
        lastClickedNode = node
        return clickResult
    }

    override suspend fun scrollForward(): Boolean {
        scrollForwardCallCount++
        return scrollResult
    }

    override suspend fun scrollBackward(): Boolean {
        scrollBackwardCallCount++
        return scrollResult
    }

    override suspend fun getNodeChildren(node: AccessibilityNodeResult): List<AccessibilityNodeResult> {
        getNodeChildrenCallCount++
        return childrenMap[node] ?: emptyList()
    }

    override suspend fun getRootNode(): AccessibilityNodeResult? {
        getRootNodeCallCount++
        return rootNode
    }

    override suspend fun findAllNodesByClassName(className: String): List<AccessibilityNodeResult> {
        findAllNodesByClassNameCallCount++
        return nodesByClassName[className] ?: emptyList()
    }

    override suspend fun performGlobalAction(action: Int): Boolean {
        performGlobalActionCallCount++
        lastGlobalAction = action
        return globalActionResult
    }

    override suspend fun tapCoordinate(x: Float, y: Float): Boolean {
        tapCoordinateCallCount++
        lastTapCoordinate = x to y
        return tapResult
    }

    override fun isServiceConnected(): Boolean {
        isServiceConnectedCallCount++
        return isConnected
    }

    // ----- Test utilities -----

    /**
     * Resets all call counts, captured arguments, and configurable values to defaults.
     */
    fun reset() {
        isConnected = true
        launchAppResult = true
        clickResult = true
        scrollResult = true
        tapResult = true
        globalActionResult = true
        nodesByText.clear()
        nodesByDescription.clear()
        nodesByClassName.clear()
        nodesByViewId.clear()
        rootNode = null
        childrenMap.clear()

        launchAppCallCount = 0
        findNodeByTextCallCount = 0
        findNodeByContentDescriptionCallCount = 0
        findNodeByClassNameCallCount = 0
        findNodeByViewIdCallCount = 0
        clickNodeCallCount = 0
        scrollForwardCallCount = 0
        scrollBackwardCallCount = 0
        getNodeChildrenCallCount = 0
        getRootNodeCallCount = 0
        findAllNodesByClassNameCallCount = 0
        performGlobalActionCallCount = 0
        tapCoordinateCallCount = 0
        isServiceConnectedCallCount = 0

        lastLaunchAppPackage = null
        lastFindNodeByTextQuery = null
        lastFindNodeByContentDescriptionQuery = null
        lastFindNodeByClassNameQuery = null
        lastFindNodeByViewIdQuery = null
        lastClickedNode = null
        lastGlobalAction = null
        lastTapCoordinate = null
    }
}
